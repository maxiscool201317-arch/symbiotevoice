# Symbiote Voice (Forge 1.20.1)

Talk into your mic (Simple Voice Chat) and your symbiote answers out loud in a deep voice that
differs per strain (guardian, predator, shadow, sculk, royal) and gets deeper as the bond stage rises.

Default setup is FREE and runs on your own PC. Paid Claude is an optional switch.

## Needs
- Forge 1.20.1 (47.x), Symbiote 1.1.3, Simple Voice Chat 2.5+
- Three free programs running on your PC while you play (see below)

## Build
1. Install JDK 17.
2. In this folder run once: `gradle wrapper --gradle-version 8.1.1`
3. Run `./gradlew build` (Windows: `gradlew.bat build`)
4. Put `build/libs/symbiotevoice-forge-1.20.1-1.0.0.jar` in your pack's `mods` folder.

## Free local setup (names and commands change, so follow each project's own README if one differs)
1. **The brain: Ollama** (ollama.com). Install it, then run `ollama pull llama3.2:3b`.
   Ollama listens on http://localhost:11434, which is the default in the config.
2. **The ears: whisper.cpp server** (github.com/ggml-org/whisper.cpp). Download a small English model
   (e.g. `ggml-base.en.bin`) and start its server on port 8001 with the inference path
   `/v1/audio/transcriptions`. Any server with an OpenAI-style `/v1/audio/transcriptions`
   (such as faster-whisper-server) also works; set `sttBaseUrl` to it.
3. **The mouth: Piper** (github.com/OHF-Voice/piper1-gpl or `pip install piper-tts`). Download a male English
   voice and run its HTTP server on port 5000. The default `ttsBaseUrl` expects http://localhost:5000.
   All strains share that one Piper voice; the strain differences come from the effects in `StrainVoice.java`.

Then start Minecraft once, edit `config/symbiotevoice-common.toml` if your ports differ.

## Switching to paid Claude (optional)
In the config set `provider = "anthropic"` and paste `anthropicKey` (or set env var ANTHROPIC_API_KEY).
For paid speech set `ttsApi = "openai"`, `ttsBaseUrl = "https://api.openai.com/v1"`,
`sttBaseUrl = "https://api.openai.com/v1"` and `openaiKey`.
**Never export a modpack with the config file inside; keys would go with it.**

## It speaks up on its own
Every couple of minutes (random, config `[chatter]`) the symbiote says something unprompted, written by the AI
from the situation: your health and hunger, day or night, rain, dimension, underground, hostile mobs near you,
its mood and your bond stage. It also reacts to low health, nearby enemies, hunger and nightfall.
Higher bond stages talk more often. Turn it off with `enabled = false` under `[chatter]`.
It never talks while you are mid-sentence, and it avoids repeating its last few lines.

## Hiding the mod's written lines
`hideWrittenLines = true` (default) hides the Symbiote mod's own chat whispers on your screen, so you only hear the voice.
It filters chat messages whose translation key starts with `symbiote.voice.`; this is client-side, so set it on each player's PC.
The symbiote still "thinks" those lines internally; only the text is hidden. If some still show, they are not using
that key and I need an example of one.

## Tuning the voices
Edit the numbers in `StrainVoice.java` (pitch ratios, growl, echo) and rebuild.

## Known limits
- Written without being compiled or run, and the local servers' exact flags are from memory: expect to fix a few things.
- Reads Symbiote's data by reflection; if that fails it acts as a bonded guardian (see `defaultStrain`).
- It does not voice the Symbiote mod's own chat whispers; its random remarks are written fresh by the AI instead.
- Small local models are less clever than Claude, and Minecraft will share your GPU with them.
