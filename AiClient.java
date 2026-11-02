package com.symbiotevoice;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/** HTTP clients: speech-to-text, chat (Ollama or Claude), text-to-speech (Piper or OpenAI-style). Never logs keys. */
final class AiClient {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    record Audio(short[] pcm, int sampleRate) {}

    // ---------- speech to text ----------

    static String transcribe(byte[] wav) throws Exception {
        String boundary = "----symvoice" + System.nanoTime();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        field(body, boundary, "model", VoiceConfig.STT_MODEL.get());
        field(body, boundary, "response_format", "json");
        write(body, "--" + boundary + "\r\n");
        write(body, "Content-Disposition: form-data; name=\"file\"; filename=\"speech.wav\"\r\n");
        write(body, "Content-Type: audio/wav\r\n\r\n");
        body.write(wav);
        write(body, "\r\n--" + boundary + "--\r\n");

        HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(trimSlash(VoiceConfig.STT_BASE_URL.get()) + "/audio/transcriptions"))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()));
        auth(rb, VoiceConfig.openaiKey());
        HttpResponse<byte[]> resp = send(rb.build(), "speech-to-text");
        JsonObject o = JsonParser.parseString(new String(resp.body(), StandardCharsets.UTF_8)).getAsJsonObject();
        return o.has("text") ? o.get("text").getAsString().trim() : "";
    }

    // ---------- chat ----------

    /** history: {role, text} pairs, oldest first, alternating user/assistant. */
    static String chat(String system, List<String[]> history, String userText) throws Exception {
        String raw = VoiceConfig.useOllama() ? chatOllama(system, history, userText)
                                             : chatAnthropic(system, history, userText);
        return clean(raw);
    }

    private static String chatOllama(String system, List<String[]> history, String userText) throws Exception {
        JsonArray messages = new JsonArray();
        messages.add(msg("system", system));
        for (String[] h : history) messages.add(msg(h[0], h[1]));
        messages.add(msg("user", userText));

        JsonObject payload = new JsonObject();
        payload.addProperty("model", VoiceConfig.OLLAMA_MODEL.get());
        payload.addProperty("max_tokens", VoiceConfig.MAX_TOKENS.get());
        payload.addProperty("stream", false);
        payload.add("messages", messages);

        HttpRequest req = HttpRequest.newBuilder(URI.create(trimSlash(VoiceConfig.OLLAMA_URL.get()) + "/v1/chat/completions"))
                .timeout(Duration.ofSeconds(90)) // first call loads the model
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();
        HttpResponse<byte[]> resp = send(req, "Ollama chat");
        JsonObject o = JsonParser.parseString(new String(resp.body(), StandardCharsets.UTF_8)).getAsJsonObject();
        return o.getAsJsonArray("choices").get(0).getAsJsonObject()
                .getAsJsonObject("message").get("content").getAsString();
    }

    private static String chatAnthropic(String system, List<String[]> history, String userText) throws Exception {
        String key = VoiceConfig.anthropicKey();
        if (key.isEmpty()) throw new IllegalStateException("provider=anthropic but no anthropicKey / ANTHROPIC_API_KEY set");
        JsonArray messages = new JsonArray();
        for (String[] h : history) messages.add(msg(h[0], h[1]));
        messages.add(msg("user", userText));

        JsonObject payload = new JsonObject();
        payload.addProperty("model", VoiceConfig.ANTHROPIC_MODEL.get());
        payload.addProperty("max_tokens", VoiceConfig.MAX_TOKENS.get());
        payload.addProperty("system", system);
        payload.add("messages", messages);

        HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.anthropic.com/v1/messages"))
                .timeout(Duration.ofSeconds(30))
                .header("x-api-key", key)
                .header("anthropic-version", "2023-06-01")
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();
        HttpResponse<byte[]> resp = send(req, "Claude chat");
        JsonObject o = JsonParser.parseString(new String(resp.body(), StandardCharsets.UTF_8)).getAsJsonObject();
        StringBuilder sb = new StringBuilder();
        for (var el : o.getAsJsonArray("content")) {
            JsonObject block = el.getAsJsonObject();
            if (block.has("text")) sb.append(block.get("text").getAsString());
        }
        return sb.toString();
    }

    /** Small local models love asterisks, quotes, emoji and <think> blocks. Strip them. */
    static String clean(String s) {
        s = s.replaceAll("(?s)<think>.*?</think>", " ");
        s = s.replaceAll("\\*[^*]*\\*", " ");
        s = s.replaceAll("[\"“”`_#]", "");
        s = s.replaceAll("[\\x{10000}-\\x{10FFFF}]", ""); // emoji and other non-BMP
        s = s.replaceAll("[\\p{Cntrl}&&[^\\n]]", " ");
        s = s.replaceAll("\\s+", " ").trim();
        if (s.length() > 300) {
            int cut = s.lastIndexOf('.', 300);
            s = s.substring(0, cut > 40 ? cut + 1 : 300);
        }
        return s;
    }

    // ---------- text to speech ----------

    static Audio speak(String text, String voice, double speed) throws Exception {
        String override = VoiceConfig.TTS_VOICE_OVERRIDE.get().trim();
        if (!override.isEmpty()) voice = override;

        HttpRequest.Builder rb;
        if (VoiceConfig.usePiper()) {
            JsonObject payload = new JsonObject();
            payload.addProperty("text", text);
            if (!override.isEmpty()) payload.addProperty("voice", override);
            rb = HttpRequest.newBuilder(URI.create(trimSlash(VoiceConfig.TTS_BASE_URL.get())))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString()));
        } else {
            JsonObject payload = new JsonObject();
            payload.addProperty("model", VoiceConfig.TTS_MODEL.get());
            payload.addProperty("input", text);
            payload.addProperty("voice", voice);
            payload.addProperty("speed", speed);
            payload.addProperty("response_format", "wav");
            rb = HttpRequest.newBuilder(URI.create(trimSlash(VoiceConfig.TTS_BASE_URL.get()) + "/audio/speech"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString()));
        }
        rb.timeout(Duration.ofSeconds(60));
        auth(rb, VoiceConfig.openaiKey());
        HttpResponse<byte[]> resp = send(rb.build(), "text-to-speech");
        return parseWav(resp.body());
    }

    static Audio parseWav(byte[] b) {
        if (b.length < 44 || !new String(b, 0, 4, StandardCharsets.US_ASCII).equals("RIFF")) {
            throw new IllegalStateException("text-to-speech did not return WAV audio");
        }
        ByteBuffer bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN);
        int rate = 22050, ch = 1, bits = 16, dataStart = -1, dataLen = 0;
        int pos = 12;
        while (pos + 8 <= b.length) {
            String id = new String(b, pos, 4, StandardCharsets.US_ASCII);
            long size = bb.getInt(pos + 4) & 0xFFFFFFFFL;
            int body = pos + 8;
            if (id.equals("fmt ")) {
                ch = bb.getShort(body + 2);
                rate = bb.getInt(body + 4);
                bits = bb.getShort(body + 14);
            } else if (id.equals("data")) {
                dataStart = body;
                dataLen = (int) Math.min(size, (long) b.length - body);
                break;
            }
            long next = body + size + (size & 1);
            if (next > b.length) break;
            pos = (int) next;
        }
        if (dataStart < 0 || bits != 16 || ch < 1) throw new IllegalStateException("unsupported WAV format from text-to-speech");
        int frames = dataLen / (2 * ch);
        short[] pcm = new short[frames];
        for (int i = 0; i < frames; i++) pcm[i] = bb.getShort(dataStart + i * 2 * ch);
        return new Audio(pcm, rate);
    }

    // ---------- helpers ----------

    private static HttpResponse<byte[]> send(HttpRequest req, String what) throws Exception {
        HttpResponse<byte[]> resp;
        try {
            resp = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());
        } catch (java.net.ConnectException e) {
            throw new IllegalStateException(what + ": can't connect to " + req.uri().getHost() + ":" + req.uri().getPort()
                    + " - is that program running?");
        }
        if (resp.statusCode() / 100 != 2) {
            String snippet = new String(resp.body(), StandardCharsets.UTF_8);
            if (snippet.length() > 300) snippet = snippet.substring(0, 300);
            throw new IllegalStateException(what + " failed: HTTP " + resp.statusCode() + " " + snippet);
        }
        return resp;
    }

    private static void auth(HttpRequest.Builder rb, String key) {
        if (!key.isEmpty()) rb.header("Authorization", "Bearer " + key);
    }

    private static JsonObject msg(String role, String text) {
        JsonObject m = new JsonObject();
        m.addProperty("role", role);
        m.addProperty("content", text);
        return m;
    }

    private static String trimSlash(String s) {
        s = s.trim();
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private static void field(ByteArrayOutputStream o, String boundary, String name, String value) throws Exception {
        write(o, "--" + boundary + "\r\n");
        write(o, "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n");
        write(o, value + "\r\n");
    }

    private static void write(ByteArrayOutputStream o, String s) throws Exception {
        o.write(s.getBytes(StandardCharsets.UTF_8));
    }

    private AiClient() {}
}
