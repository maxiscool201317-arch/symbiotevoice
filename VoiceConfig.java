package com.symbiotevoice;

import net.minecraftforge.common.ForgeConfigSpec;

public final class VoiceConfig {
    public static final ForgeConfigSpec SPEC;

    // brain
    public static final ForgeConfigSpec.ConfigValue<String> CHAT_PROVIDER;
    public static final ForgeConfigSpec.ConfigValue<String> OLLAMA_URL;
    public static final ForgeConfigSpec.ConfigValue<String> OLLAMA_MODEL;
    public static final ForgeConfigSpec.ConfigValue<String> ANTHROPIC_KEY;
    public static final ForgeConfigSpec.ConfigValue<String> ANTHROPIC_MODEL;
    // ears and mouth
    public static final ForgeConfigSpec.ConfigValue<String> STT_BASE_URL;
    public static final ForgeConfigSpec.ConfigValue<String> STT_MODEL;
    public static final ForgeConfigSpec.ConfigValue<String> TTS_API;
    public static final ForgeConfigSpec.ConfigValue<String> TTS_BASE_URL;
    public static final ForgeConfigSpec.ConfigValue<String> TTS_MODEL;
    public static final ForgeConfigSpec.ConfigValue<String> TTS_VOICE_OVERRIDE;
    public static final ForgeConfigSpec.ConfigValue<String> OPENAI_KEY;
    // behaviour
    public static final ForgeConfigSpec.ConfigValue<String> DEFAULT_STRAIN;
    public static final ForgeConfigSpec.BooleanValue REQUIRE_BOND;
    public static final ForgeConfigSpec.BooleanValue HOST_ONLY;
    public static final ForgeConfigSpec.BooleanValue HIDE_WRITTEN_LINES;
    public static final ForgeConfigSpec.IntValue MAX_TOKENS;
    public static final ForgeConfigSpec.IntValue MAX_UTTERANCE_SECONDS;
    public static final ForgeConfigSpec.IntValue COOLDOWN_SECONDS;
    public static final ForgeConfigSpec.IntValue HISTORY_TURNS;
    public static final ForgeConfigSpec.BooleanValue CHATTER_ENABLED;
    public static final ForgeConfigSpec.BooleanValue CHATTER_REACT;
    public static final ForgeConfigSpec.IntValue CHATTER_MIN_SECONDS;
    public static final ForgeConfigSpec.IntValue CHATTER_MAX_SECONDS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("Which AI thinks for the symbiote. 'ollama' = free, runs on your PC. 'anthropic' = Claude, paid API key.")
                .push("brain");
        CHAT_PROVIDER = b.define("provider", "ollama");
        OLLAMA_URL = b.define("ollamaUrl", "http://localhost:11434");
        OLLAMA_MODEL = b.comment("Any model you have pulled in Ollama, e.g. llama3.2:3b or qwen2.5:3b")
                .define("ollamaModel", "llama3.2:3b");
        ANTHROPIC_KEY = b.comment("Only for provider=anthropic. NEVER share this file or ship it in a modpack export. Empty = env var ANTHROPIC_API_KEY.")
                .define("anthropicKey", "");
        ANTHROPIC_MODEL = b.define("anthropicModel", "claude-haiku-5-5");
        b.pop();

        b.comment("Speech to text (your mic -> words). Needs an OpenAI-compatible /audio/transcriptions server, e.g. whisper.cpp server or faster-whisper-server.")
                .push("listening");
        STT_BASE_URL = b.define("sttBaseUrl", "http://localhost:8001/v1");
        STT_MODEL = b.comment("Model name your server expects. Many local servers ignore it.")
                .define("sttModel", "whisper-1");
        b.pop();

        b.comment("Text to speech (words -> voice). 'piper' = Piper's built-in HTTP server (free, local). 'openai' = any OpenAI-compatible /audio/speech server.")
                .push("speaking");
        TTS_API = b.define("ttsApi", "piper");
        TTS_BASE_URL = b.comment("piper: http://localhost:5000   openai-style: http://localhost:8000/v1")
                .define("ttsBaseUrl", "http://localhost:5000");
        TTS_MODEL = b.comment("openai-style only").define("ttsModel", "tts-1");
        TTS_VOICE_OVERRIDE = b.comment("Empty = each strain uses its own voice name. Set a voice to force one for every strain.")
                .define("ttsVoiceOverride", "");
        OPENAI_KEY = b.comment("Only needed for paid OpenAI-style services. Empty = env var OPENAI_API_KEY, or none for local servers.")
                .define("openaiKey", "");
        b.pop();

        b.push("behaviour");
        REQUIRE_BOND = b.comment("Only answer players who are bonded to a symbiote.").define("requireBond", true);
        HOST_ONLY = b.comment("true = only the host hears the symbiote (inside their head). false = nearby players hear it too.")
                .define("hostOnly", true);
        HIDE_WRITTEN_LINES = b.comment("Hide the Symbiote mod's own written chat lines (this is a client-side filter, set it on each player's PC).")
                .define("hideWrittenLines", true);
        DEFAULT_STRAIN = b.comment("Strain to use if the Symbiote mod's data can't be read: guardian, predator, shadow, sculk, royal.")
                .define("defaultStrain", "guardian");
        MAX_TOKENS = b.defineInRange("maxReplyTokens", 120, 20, 400);
        MAX_UTTERANCE_SECONDS = b.defineInRange("maxUtteranceSeconds", 15, 3, 60);
        COOLDOWN_SECONDS = b.comment("Minimum seconds between replies per player.")
                .defineInRange("cooldownSeconds", 4, 0, 120);
        HISTORY_TURNS = b.comment("How many back-and-forth exchanges the symbiote remembers per player (resets on restart).")
                .defineInRange("historyTurns", 6, 0, 20);
        b.pop();

        b.comment("The symbiote speaking up on its own, without you talking first.").push("chatter");
        CHATTER_ENABLED = b.define("enabled", true);
        CHATTER_REACT = b.comment("Also react to danger: low health, hunger, nightfall, hostile mobs nearby.")
                .define("reactToEvents", true);
        CHATTER_MIN_SECONDS = b.comment("Random remarks come at a random time between min and max. Bigger stage = more talkative.")
                .defineInRange("minSeconds", 120, 20, 3600);
        CHATTER_MAX_SECONDS = b.defineInRange("maxSeconds", 300, 30, 7200);
        b.pop();

        SPEC = b.build();
    }

    public static boolean useOllama() {
        return !CHAT_PROVIDER.get().trim().equalsIgnoreCase("anthropic");
    }

    public static boolean usePiper() {
        return !TTS_API.get().trim().equalsIgnoreCase("openai");
    }

    public static String anthropicKey() {
        String k = ANTHROPIC_KEY.get().trim();
        return k.isEmpty() ? System.getenv().getOrDefault("ANTHROPIC_API_KEY", "").trim() : k;
    }

    public static String openaiKey() {
        String k = OPENAI_KEY.get().trim();
        return k.isEmpty() ? System.getenv().getOrDefault("OPENAI_API_KEY", "").trim() : k;
    }

    private VoiceConfig() {}
}
