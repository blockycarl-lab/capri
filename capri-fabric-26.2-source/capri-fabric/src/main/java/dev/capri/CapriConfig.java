package dev.capri;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Settings stored in .minecraft/config/capri.json. */
public final class CapriConfig {
    /** "anthropic" or "openai" (any OpenAI-compatible chat completions endpoint). */
    public String provider = "anthropic";
    public String apiKey = "";
    /** Blank = provider default. */
    public String model = "";
    /** Blank = provider default. */
    public String endpoint = "";
    public boolean autoLook = true;
    public int intervalSeconds = 45;
    /** Send a screenshot with each request. Turn off for text-only models. */
    public boolean vision = true;
    public int imageWidth = 960;
    public boolean echoToChat = true;
    public boolean sounds = true;
    /** bottom_right, bottom_left, top_right, top_left */
    public String corner = "bottom_right";
    public String language = "English";
    /** Extra lines appended to Capri's system prompt. */
    public String extraPersona = "";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("capri.json");
    }

    public static CapriConfig load() {
        try {
            Path p = path();
            if (Files.exists(p)) {
                CapriConfig c = GSON.fromJson(Files.readString(p, StandardCharsets.UTF_8), CapriConfig.class);
                if (c != null) {
                    c.intervalSeconds = Math.max(10, c.intervalSeconds);
                    return c;
                }
            }
        } catch (Exception e) {
            System.err.println("[capri] could not read config: " + e);
        }
        CapriConfig fresh = new CapriConfig();
        fresh.save();
        return fresh;
    }

    public void save() {
        try {
            Files.writeString(path(), GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("[capri] could not save config: " + e);
        }
    }

    public boolean isAnthropic() {
        return !"openai".equalsIgnoreCase(provider);
    }

    public String resolvedKey() {
        if (apiKey != null && !apiKey.isBlank()) return apiKey.trim();
        String env = System.getenv("CAPRI_API_KEY");
        return env == null ? "" : env.trim();
    }

    public boolean hasKey() {
        return !resolvedKey().isEmpty();
    }

    public String resolvedModel() {
        if (model != null && !model.isBlank()) return model.trim();
        return isAnthropic() ? "claude-haiku-5-5" : "gpt-4o-mini";
    }

    public String resolvedEndpoint() {
        if (endpoint != null && !endpoint.isBlank()) return endpoint.trim();
        return isAnthropic() ? "https://api.anthropic.com/v1/messages"
                             : "https://api.openai.com/v1/chat/completions";
    }
}
