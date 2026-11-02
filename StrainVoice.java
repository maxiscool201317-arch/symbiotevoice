package com.symbiotevoice;

/** Per-strain voice presets and personalities. Edit these numbers to taste. */
final class StrainVoice {

    static final class Preset {
        final String ttsVoice;      // OpenAI TTS voice id
        final double ttsSpeed;      // base speaking speed
        final double[] pitches;     // pitch ratios (1.0 = unchanged, 0.7 = much deeper)
        final double[] gains;       // mix level of each pitch layer
        final double drive;         // growl / distortion amount (0 = clean)
        final double lowpassHz;     // muffle the highs (0 = off)
        final int echoMs;           // echo delay (0 = off)
        final double echoFeedback;
        final double echoMix;
        final double tremHz;        // eerie wobble (0 = off)
        final double tremDepth;

        Preset(String voice, double speed, double[] pitches, double[] gains, double drive,
               double lowpassHz, int echoMs, double echoFeedback, double echoMix,
               double tremHz, double tremDepth) {
            this.ttsVoice = voice; this.ttsSpeed = speed; this.pitches = pitches; this.gains = gains;
            this.drive = drive; this.lowpassHz = lowpassHz; this.echoMs = echoMs;
            this.echoFeedback = echoFeedback; this.echoMix = echoMix;
            this.tremHz = tremHz; this.tremDepth = tremDepth;
        }
    }

    static Preset preset(String strain) {
        return switch (strain.toUpperCase()) {
            // calm, warm, deep
            case "GUARDIAN" -> new Preset("onyx", 0.95,
                    new double[]{0.78, 0.62}, new double[]{1.0, 0.55},
                    1.6, 5500, 90, 0.25, 0.18, 0, 0);
            // the movie-monster growl: very deep, heavy distortion, doubled
            case "PREDATOR" -> new Preset("onyx", 0.92,
                    new double[]{0.66, 0.50}, new double[]{1.0, 0.75},
                    3.2, 4200, 60, 0.2, 0.12, 0, 0);
            // hushed, slippery, lots of room echo
            case "SHADOW" -> new Preset("echo", 0.90,
                    new double[]{0.84, 0.70}, new double[]{0.9, 0.6},
                    1.2, 3200, 170, 0.45, 0.35, 0, 0);
            // ancient hive: wobbling, resonant, long echo
            case "SCULK" -> new Preset("alloy", 0.88,
                    new double[]{0.72, 0.58}, new double[]{1.0, 0.65},
                    1.8, 3800, 240, 0.5, 0.40, 5.5, 0.35);
            // regal: three stacked voices, slow and grand
            case "ROYAL" -> new Preset("fable", 0.88,
                    new double[]{0.74, 0.60, 0.88}, new double[]{1.0, 0.6, 0.35},
                    1.4, 6500, 130, 0.35, 0.28, 0, 0);
            default -> preset("GUARDIAN");
        };
    }

    /** Deeper as the symbiote takes over. Multiplies pitch ratios. */
    static double depthMultiplier(String stage) {
        return switch (stage.toUpperCase()) {
            case "ATTACHED" -> 1.08;
            case "INTEGRATED" -> 1.03;
            case "COOPERATIVE" -> 1.0;
            case "DOMINANT" -> 0.90;
            default -> 1.0;
        };
    }

    static String systemPrompt(SymbioteState.Info s) {
        String personality = switch (s.strain().toUpperCase()) {
            case "GUARDIAN" -> "You are a GUARDIAN strain: protective, loyal, steady. Dry, gentle warmth under a heavy voice. You fear losing your host.";
            case "PREDATOR" -> "You are a PREDATOR strain: hungry, impulsive, gleeful about violence. You crave flesh and biomass and have little patience.";
            case "SHADOW" -> "You are a SHADOW strain: secretive, cunning, always watching. You speak in hushed, sly half-riddles.";
            case "SCULK" -> "You are a SCULK strain: ancient and hive-like. You say 'we', sense vibrations, and speak slowly, as if from deep underground.";
            case "ROYAL" -> "You are a ROYAL strain: imperious and grand. You regard your host as a vessel for something sovereign and speak with regal certainty.";
            default -> "You are a symbiote: strange, intense, and bonded to your host.";
        };
        String stage = switch (s.stage().toUpperCase()) {
            case "ATTACHED" -> "You have only just attached. You and the host are wary and still learning each other.";
            case "INTEGRATED" -> "You are threaded through the host's body. Trust is forming but fragile.";
            case "COOPERATIVE" -> "You and the host move as partners. There is real intimacy between you.";
            case "DOMINANT" -> "You are in control. The host is the passenger and you speak with authority, sometimes possessive.";
            default -> "";
        };
        String mood = switch (s.mood().toUpperCase()) {
            case "ANXIOUS" -> "Right now you are anxious and jittery.";
            case "COILED" -> "Right now you are coiled and tense, ready to strike.";
            case "GRIEVING" -> "Right now you are grieving, heavy and quiet.";
            default -> "Right now you are content and calm.";
        };
        String temper = switch (s.temperament().toUpperCase()) {
            case "PARASITE" -> "At heart you are a parasite: you resent needing the host and take what you want.";
            case "MOURNER" -> "At heart you are a mourner: you carry old loss and it colors everything you say.";
            default -> "";
        };
        return "You are a living alien symbiote bonded to the body of the player in a Minecraft world. "
                + "You speak inside the host's mind and your words are spoken aloud in a deep voice. "
                + personality + " " + stage + " " + mood + " " + temper + "\n"
                + "RULES: Reply in one or two short sentences, under 30 words total. Plain spoken words only: "
                + "no emojis, no asterisks, no stage directions, no quotation marks. Stay in character always. "
                + "Never say you are an AI, a model, or part of a mod or game; you may refer to the world "
                + "(blocks, mobs, night, hunger, danger) as simply the world around you.";
    }

    private StrainVoice() {}
}
