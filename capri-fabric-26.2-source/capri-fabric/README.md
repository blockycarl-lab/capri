# Capri (Fabric, Minecraft 26.2, client-side)

Capri rides along while you play. Every ~45 seconds (or when you press **H**) she looks at a
screenshot of your game and says something in her own voice, in a neon speech bubble with a
typewriter effect and blip sounds. You can talk back. She only talks: no powers, no chips.

Client-side only: nothing is sent to the game server. `@c ...` messages and `/capri ...`
commands are handled and cancelled on your machine.

## Build the jar (you need to do this, I couldn't compile it for you)

1. Install **JDK 25**.
2. Easiest: open this folder in **IntelliJ IDEA** (File > Open), let it import the Gradle project,
   then run the Gradle task **build**.
   Or install **Gradle 9.5.1** and run `gradle build` in this folder.
3. The jar appears at `build/libs/capri-1.0.0.jar`.

## Install

- Fabric Loader 0.19.3 or newer for Minecraft 26.2, Java 25.
- Fabric API for 26.2 (0.154.0+26.2 or newer).
- Put `capri-1.0.0.jar` in `.minecraft/mods`.

## First run

1. Get an API key (Anthropic, or any OpenAI-compatible provider with vision).
2. In a world, run `/capri key <your key>` (or edit `.minecraft/config/capri.json`).
   Default is Anthropic with `claude-haiku-5-5`. For OpenAI-style: `/capri provider openai`,
   `/capri model gpt-4o-mini` (default). `/capri url <endpoint>` for other compatible APIs.
3. Play. She comments on her own. Talk to her by typing `@c what's that?` in chat.

## Controls

| Input | Does |
| --- | --- |
| `H` | She looks at the screen and reacts now |
| `J` | Toggle automatic commentary |
| `@c <text>` in chat | Talk to her (also `@capri <text>`), never sent to the server |
| `/capri say <text>` / `look` | Same as above |
| `/capri interval <s>` / `auto <bool>` / `vision <bool>` | Tune how often she looks, turn screenshots off |
| `/capri corner top_left` etc. | Move the bubble |
| `/capri clear` / `status` | Forget the chat / show settings |

Keys are rebindable under Options > Controls > Capri. All settings also live in `config/capri.json`
(`extraPersona` lets you append lines to her prompt, `language` changes her language).

## Things to know

- **Privacy and cost:** each look sends one downscaled screenshot (960 px wide JPEG) to your chosen
  API, and your API key sits in plain text in `capri.json`. Screenshots include chat and player
  names. Raise the interval to spend less. Auto mode stops itself after 3 failed requests.
- **Servers:** the mod is passive (reads your own screen, shows a bubble). Check your server's rules
  yourself, including Hypixel's.
- **Text-only models** (e.g. DeepSeek): run `/capri vision false`.
- **Assets:** the chip icon, blip sounds and mod icon were copied from the Capri NeoForge jar you
  uploaded (marked All Rights Reserved by Ikeda). Replace them in `src/main/resources/assets/capri/`
  if you don't have the right to reuse them.
- **Not compiled or run:** I wrote this against the 26.1.2 Fabric docs and mods already running on
  26.2, but could not build it. If `gradle build` shows errors, paste them to me. The most likely
  spots are `CapriSound.java` (cosmetic: delete the file and its two `CapriSound.play` calls in
  `CapriHud.java`) and the `blit(...)` call in `CapriHud.java`.
