package com.symbiotevoice;

import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.AudioChannel;
import de.maxhenkel.voicechat.api.audiochannel.AudioPlayer;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * Listens to each player's mic through Simple Voice Chat and answers through
 * speech-to-text -> AI -> text-to-speech -> deep voice. Also speaks on its own (see ChatterHandler).
 */
@ForgeVoicechatPlugin
public class SymbioteVoicePlugin implements VoicechatPlugin {
    private static final int FRAME = 960; // 20 ms at 48 kHz

    static volatile SymbioteVoicePlugin INSTANCE;

    private volatile VoicechatServerApi serverApi;
    private final Map<UUID, Utterance> active = new ConcurrentHashMap<>();
    private final Map<UUID, Long> nextAllowedMs = new ConcurrentHashMap<>();
    private final Map<UUID, List<String[]>> history = new ConcurrentHashMap<>();
    private final Map<UUID, List<String>> recentLines = new ConcurrentHashMap<>();
    private final ExecutorService pool = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "symbiotevoice-worker");
        t.setDaemon(true);
        return t;
    });

    public SymbioteVoicePlugin() {
        INSTANCE = this;
    }

    private static final class Utterance {
        final OpusDecoder decoder;
        final SymbioteState.Info info;
        short[] buf = new short[48000 * 4];
        int len = 0;

        Utterance(OpusDecoder decoder, SymbioteState.Info info) {
            this.decoder = decoder;
            this.info = info;
        }

        void append(short[] frame) {
            if (len + frame.length > buf.length) buf = java.util.Arrays.copyOf(buf, buf.length * 2);
            System.arraycopy(frame, 0, buf, len, frame.length);
            len += frame.length;
        }

        double seconds() { return len / 48000.0; }
    }

    @Override
    public String getPluginId() {
        return "symbiotevoice";
    }

    @Override
    public void initialize(de.maxhenkel.voicechat.api.VoicechatApi api) {}

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(VoicechatServerStartedEvent.class, e -> serverApi = e.getVoicechat());
        registration.registerEvent(MicrophonePacketEvent.class, this::onMic);
    }

    /** True when the player has voice chat, isn't mid-sentence, and the symbiote isn't busy or cooling down. */
    boolean isReady(ServerPlayer sp) {
        VoicechatServerApi api = serverApi;
        UUID id = sp.getUUID();
        return api != null
                && api.getConnectionOf(id) != null
                && !active.containsKey(id)
                && System.currentTimeMillis() >= nextAllowedMs.getOrDefault(id, 0L);
    }

    // ---------- you talk, it answers ----------

    private void onMic(MicrophonePacketEvent event) {
        VoicechatServerApi api = serverApi;
        VoicechatConnection con = event.getSenderConnection();
        if (api == null || con == null || con.getPlayer() == null) return;
        if (!(con.getPlayer().getPlayer() instanceof ServerPlayer sp)) return;

        UUID id = sp.getUUID();
        byte[] data = event.getPacket().getOpusEncodedData();
        Utterance u = active.get(id);

        // empty packet = the player stopped talking
        if (data == null || data.length == 0) {
            if (u != null) {
                active.remove(id);
                finish(sp, u);
            }
            return;
        }

        if (u == null) {
            long now = System.currentTimeMillis();
            if (now < nextAllowedMs.getOrDefault(id, 0L)) return;
            SymbioteState.Info info = SymbioteState.read(sp);
            if (VoiceConfig.REQUIRE_BOND.get() && !info.bonded()) return;
            u = new Utterance(api.createDecoder(), info);
            active.put(id, u);
        }

        u.append(u.decoder.decode(data));
        if (u.seconds() >= VoiceConfig.MAX_UTTERANCE_SECONDS.get()) {
            active.remove(id);
            finish(sp, u);
        }
    }

    private void finish(ServerPlayer sp, Utterance u) {
        try { u.decoder.close(); } catch (Throwable ignored) {}
        if (u.seconds() < 0.6) return; // clicks and coughs

        UUID id = sp.getUUID();
        nextAllowedMs.put(id, System.currentTimeMillis() + 120_000L); // busy until the pipeline finishes
        short[] pcm = java.util.Arrays.copyOf(u.buf, u.len);
        MinecraftServer server = sp.getServer();

        pool.execute(() -> {
            try {
                String heard = AiClient.transcribe(Wav.from48kTo16kWav(pcm));
                if (heard.length() < 2) { cooldown(id); return; }

                List<String[]> hist = history.computeIfAbsent(id, k -> new ArrayList<>());
                List<String[]> snapshot;
                synchronized (hist) { snapshot = new ArrayList<>(hist); }

                String reply = AiClient.chat(StrainVoice.systemPrompt(u.info), snapshot, heard);
                if (reply.isBlank()) { cooldown(id); return; }

                int keep = VoiceConfig.HISTORY_TURNS.get() * 2;
                synchronized (hist) {
                    hist.add(new String[]{"user", heard});
                    hist.add(new String[]{"assistant", reply});
                    while (hist.size() > keep) hist.remove(0);
                }
                voiceAndPlay(server, id, u.info, reply);
            } catch (Throwable t) {
                SymbioteVoiceMod.LOGGER.warn("Symbiote voice pipeline failed: {}", t.toString());
                cooldown(id);
            }
        });
    }

    // ---------- it speaks up on its own ----------

    void speakSpontaneous(ServerPlayer sp, SymbioteState.Info info, String situation) {
        UUID id = sp.getUUID();
        MinecraftServer server = sp.getServer();
        nextAllowedMs.put(id, System.currentTimeMillis() + 120_000L);

        pool.execute(() -> {
            try {
                List<String> recent = recentLines.computeIfAbsent(id, k -> new ArrayList<>());
                String avoid;
                synchronized (recent) { avoid = String.join(" | ", recent); }

                String prompt = "Situation: " + situation + "\n"
                        + "Say one short thought to your host, unprompted, in your own voice. "
                        + "React to the situation or to how you feel."
                        + (avoid.isEmpty() ? "" : " Do not repeat or echo these recent lines: " + avoid);

                String reply = AiClient.chat(StrainVoice.systemPrompt(info), List.of(), prompt);
                if (reply.isBlank()) { cooldown(id); return; }

                synchronized (recent) {
                    recent.add(reply);
                    while (recent.size() > 6) recent.remove(0);
                }
                voiceAndPlay(server, id, info, reply);
            } catch (Throwable t) {
                SymbioteVoiceMod.LOGGER.warn("Symbiote spontaneous line failed: {}", t.toString());
                // back off for a while so a broken setup doesn't spam the log
                nextAllowedMs.put(id, System.currentTimeMillis() + 60_000L);
            }
        });
    }

    // ---------- shared tail: text -> voice -> playback ----------

    private void voiceAndPlay(MinecraftServer server, UUID id, SymbioteState.Info info, String reply) throws Exception {
        StrainVoice.Preset preset = StrainVoice.preset(info.strain());
        AiClient.Audio tts = AiClient.speak(reply, preset.ttsVoice, preset.ttsSpeed);
        short[] voiced = VoiceFx.process(tts.pcm(), tts.sampleRate(), preset,
                StrainVoice.depthMultiplier(info.stage()));
        if (voiced.length == 0) { cooldown(id); return; }

        long durationMs = voiced.length * 1000L / 48000L;
        nextAllowedMs.put(id, System.currentTimeMillis() + durationMs + VoiceConfig.COOLDOWN_SECONDS.get() * 1000L);
        play(server, id, voiced);
    }

    private void cooldown(UUID id) {
        nextAllowedMs.put(id, System.currentTimeMillis() + VoiceConfig.COOLDOWN_SECONDS.get() * 1000L);
    }

    private void play(MinecraftServer server, UUID playerId, short[] pcm48) {
        server.execute(() -> {
            VoicechatServerApi api = serverApi;
            ServerPlayer sp = server.getPlayerList().getPlayer(playerId);
            if (api == null || sp == null) return;

            AudioChannel channel;
            VoicechatConnection con = api.getConnectionOf(playerId);
            if (VoiceConfig.HOST_ONLY.get() && con != null) {
                channel = api.createStaticAudioChannel(UUID.randomUUID(), api.fromServerLevel(sp.serverLevel()), con);
            } else {
                channel = api.createEntityAudioChannel(UUID.randomUUID(), api.fromEntity(sp));
            }
            if (channel == null) return;

            OpusEncoder encoder = api.createEncoder(); // TODO: close when playback ends
            int[] pos = {0};
            Supplier<short[]> frames = () -> {
                if (pos[0] >= pcm48.length) return null;
                short[] f = new short[FRAME];
                int n = Math.min(FRAME, pcm48.length - pos[0]);
                System.arraycopy(pcm48, pos[0], f, 0, n);
                pos[0] += FRAME;
                return f;
            };
            AudioPlayer player = api.createAudioPlayer(channel, encoder, frames);
            player.startPlaying();
        });
    }
}
