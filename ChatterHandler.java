package com.symbiotevoice;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Monster;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** Makes the symbiote speak up on its own: random remarks plus reactions to danger. Server side. */
public final class ChatterHandler {
    private final Random rng = new Random();
    private final Map<UUID, Long> nextRandom = new HashMap<>();
    private final Map<UUID, Map<String, Long>> lastTrigger = new HashMap<>();

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.side != LogicalSide.SERVER) return;
        if (!(e.player instanceof ServerPlayer sp)) return;
        if (!VoiceConfig.CHATTER_ENABLED.get()) return;

        long now = sp.serverLevel().getGameTime();
        if (now % 20 != 0) return; // once a second is plenty

        SymbioteVoicePlugin plugin = SymbioteVoicePlugin.INSTANCE;
        if (plugin == null || !plugin.isReady(sp)) return;

        SymbioteState.Info info = SymbioteState.read(sp);
        if (VoiceConfig.REQUIRE_BOND.get() && !info.bonded()) return;

        UUID id = sp.getUUID();
        Long due = nextRandom.get(id);
        if (due == null || due - now > 20L * VoiceConfig.CHATTER_MAX_SECONDS.get() * 3) {
            schedule(id, now, info);
            return;
        }

        String trigger = VoiceConfig.CHATTER_REACT.get() ? reaction(sp, id, now) : null;
        if (trigger == null && now < due) return;

        plugin.speakSpontaneous(sp, info, describe(sp, trigger));
        schedule(id, now, info);
    }

    private void schedule(UUID id, long now, SymbioteState.Info info) {
        int min = Math.min(VoiceConfig.CHATTER_MIN_SECONDS.get(), VoiceConfig.CHATTER_MAX_SECONDS.get());
        int max = Math.max(VoiceConfig.CHATTER_MIN_SECONDS.get(), VoiceConfig.CHATTER_MAX_SECONDS.get());
        double secs = min + rng.nextDouble() * (max - min);
        double mult = switch (info.stage().toUpperCase()) {
            case "ATTACHED" -> 1.5;
            case "INTEGRATED" -> 1.2;
            case "DOMINANT" -> 0.7;
            default -> 1.0;
        };
        nextRandom.put(id, now + (long) (secs * mult * 20));
    }

    /** Returns a trigger name if something noteworthy is happening and it hasn't been mentioned recently. */
    private String reaction(ServerPlayer sp, UUID id, long now) {
        if (sp.getHealth() <= 6 && ready(id, "low_health", now, 90)) return "low_health";

        List<Monster> mobs = sp.serverLevel().getEntitiesOfClass(Monster.class, sp.getBoundingBox().inflate(10));
        if (!mobs.isEmpty() && ready(id, "danger", now, 120)) return "danger";

        if (sp.getFoodData().getFoodLevel() <= 6 && ready(id, "hungry", now, 240)) return "hungry";

        long time = sp.serverLevel().getDayTime() % 24000L;
        if (time >= 13000 && time < 14000 && ready(id, "nightfall", now, 600)) return "nightfall";
        return null;
    }

    private boolean ready(UUID id, String key, long now, int cooldownSeconds) {
        Map<String, Long> m = lastTrigger.computeIfAbsent(id, k -> new HashMap<>());
        Long last = m.get(key);
        if (last != null && now - last < cooldownSeconds * 20L) return false;
        m.put(key, now);
        return true;
    }

    private String describe(ServerPlayer sp, String trigger) {
        var level = sp.serverLevel();
        StringBuilder sb = new StringBuilder();
        sb.append("host health ").append(Math.round(sp.getHealth())).append("/").append(Math.round(sp.getMaxHealth()));
        sb.append(", hunger ").append(sp.getFoodData().getFoodLevel()).append("/20");
        long time = level.getDayTime() % 24000L;
        sb.append(time >= 13000 && time < 23000 ? ", it is night" : ", it is day");
        if (level.isRaining()) sb.append(", raining");
        sb.append(", in the ").append(level.dimension().location().getPath().replace('_', ' '));
        if (!level.canSeeSky(sp.blockPosition()) && sp.getY() < 55) sb.append(", deep underground");

        List<Monster> mobs = level.getEntitiesOfClass(Monster.class, sp.getBoundingBox().inflate(14));
        if (!mobs.isEmpty()) {
            Monster nearest = mobs.get(0);
            double best = nearest.distanceToSqr(sp);
            for (Monster m : mobs) {
                double d = m.distanceToSqr(sp);
                if (d < best) { best = d; nearest = m; }
            }
            sb.append(", ").append(mobs.size()).append(" hostile creature(s) nearby, nearest is a ")
              .append(nearest.getType().getDescription().getString());
        }

        if (trigger != null) {
            sb.append(switch (trigger) {
                case "low_health" -> ". The host is badly hurt and might die.";
                case "danger" -> ". Something hostile is close.";
                case "hungry" -> ". The host is hungry, and so are you.";
                case "nightfall" -> ". Night has just fallen.";
                default -> ".";
            });
        }
        return sb.toString();
    }
}
