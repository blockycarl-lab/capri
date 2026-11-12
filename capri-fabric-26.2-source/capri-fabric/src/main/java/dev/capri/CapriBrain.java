package dev.capri;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Talks to the AI provider. Supports Anthropic Messages and OpenAI-compatible chat completions. */
public final class CapriBrain {
    public record Reply(String text, String mood) {}

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final Pattern MOOD = Pattern.compile("\\[mood:(\\w+)]");
    private static final Pattern ANY_TAG = Pattern.compile("\\[[^\\]]{1,40}]");
    private static final int MAX_HISTORY = 16; // entries, i.e. 8 exchanges

    private final AtomicBoolean busy = new AtomicBoolean(false);
    private final List<String[]> history = new ArrayList<>(); // {role, text}, main thread only
    private String persona;

    public boolean tryBegin() { return busy.compareAndSet(false, true); }
    public void end() { busy.set(false); }
    public boolean busy() { return busy.get(); }
    public void clear() { history.clear(); }

    private String system(CapriConfig cfg) {
        if (persona == null) {
            try (InputStream in = CapriBrain.class.getResourceAsStream("/assets/capri/persona.txt")) {
                persona = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                persona = "You are Capri, a playful kid-like AI on a chip in the player's head. Reply in {language}. "
                        + "1 to 3 short sentences. End with [mood:calm].";
            }
        }
        String s = persona.replace("{language}", cfg.language == null || cfg.language.isBlank() ? "English" : cfg.language);
        if (cfg.extraPersona != null && !cfg.extraPersona.isBlank()) s += "\n" + cfg.extraPersona;
        return s;
    }

    /**
     * Call only after tryBegin() returned true. Always calls end() before ok/fail, on the main thread.
     *
     * @param userText   what the host sent to the model this turn (situation + their words)
     * @param shortText  the short version stored in history
     * @param jpegBase64 screenshot or null
     */
    public void send(CapriConfig cfg, String userText, String shortText, String jpegBase64,
                     Consumer<Reply> ok, Consumer<String> fail) {
        final boolean anth = cfg.isAnthropic();
        final Minecraft mc = Minecraft.getInstance();
        try {
            String key = cfg.resolvedKey();
            JsonObject body = anth
                    ? anthropicBody(cfg, userText, jpegBase64)
                    : openAiBody(cfg, userText, jpegBase64);

            HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(cfg.resolvedEndpoint()))
                    .timeout(Duration.ofSeconds(45))
                    .header("Content-Type", "application/json");
            if (anth) {
                rb.header("x-api-key", key).header("anthropic-version", "2023-06-01");
            } else {
                rb.header("Authorization", "Bearer " + key);
            }
            rb.POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8));

            HTTP.sendAsync(rb.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                    .whenComplete((resp, err) -> {
                        Reply parsed = null;
                        String error = null;
                        try {
                            if (err != null) {
                                Throwable root = err.getCause() != null ? err.getCause() : err;
                                error = root.toString();
                            } else if (resp.statusCode() / 100 != 2) {
                                error = "HTTP " + resp.statusCode() + errorDetail(resp.body());
                            } else {
                                parsed = parse(anth, resp.body());
                            }
                        } catch (Exception e) {
                            error = "bad response: " + e;
                        }
                        final Reply r = parsed;
                        final String e = error;
                        mc.execute(() -> {
                            end();
                            if (r != null && !r.text().isEmpty()) {
                                remember(shortText, r.text());
                                ok.accept(r);
                            } else {
                                fail.accept(e != null ? e : "empty reply");
                            }
                        });
                    });
        } catch (Exception e) {
            end();
            fail.accept(e.toString());
        }
    }

    private void remember(String userShort, String reply) {
        history.add(new String[]{"user", userShort});
        history.add(new String[]{"assistant", reply});
        while (history.size() > MAX_HISTORY) history.remove(0);
    }

    // ---------- request bodies ----------

    private JsonObject anthropicBody(CapriConfig cfg, String text, String img) {
        JsonObject body = new JsonObject();
        body.addProperty("model", cfg.resolvedModel());
        body.addProperty("max_tokens", 220);
        body.addProperty("system", system(cfg));
        JsonArray msgs = new JsonArray();
        for (String[] h : history) msgs.add(textMsg(h[0], h[1]));

        JsonArray content = new JsonArray();
        if (img != null) {
            JsonObject src = new JsonObject();
            src.addProperty("type", "base64");
            src.addProperty("media_type", "image/jpeg");
            src.addProperty("data", img);
            JsonObject image = new JsonObject();
            image.addProperty("type", "image");
            image.add("source", src);
            content.add(image);
        }
        JsonObject t = new JsonObject();
        t.addProperty("type", "text");
        t.addProperty("text", text);
        content.add(t);

        JsonObject user = new JsonObject();
        user.addProperty("role", "user");
        user.add("content", content);
        msgs.add(user);
        body.add("messages", msgs);
        return body;
    }

    private JsonObject openAiBody(CapriConfig cfg, String text, String img) {
        JsonObject body = new JsonObject();
        body.addProperty("model", cfg.resolvedModel());
        body.addProperty("max_tokens", 220);
        JsonArray msgs = new JsonArray();
        msgs.add(textMsg("system", system(cfg)));
        for (String[] h : history) msgs.add(textMsg(h[0], h[1]));

        JsonArray content = new JsonArray();
        JsonObject t = new JsonObject();
        t.addProperty("type", "text");
        t.addProperty("text", text);
        content.add(t);
        if (img != null) {
            JsonObject url = new JsonObject();
            url.addProperty("url", "data:image/jpeg;base64," + img);
            JsonObject image = new JsonObject();
            image.addProperty("type", "image_url");
            image.add("image_url", url);
            content.add(image);
        }
        JsonObject user = new JsonObject();
        user.addProperty("role", "user");
        user.add("content", content);
        msgs.add(user);
        body.add("messages", msgs);
        return body;
    }

    private static JsonObject textMsg(String role, String text) {
        JsonObject m = new JsonObject();
        m.addProperty("role", role);
        m.addProperty("content", text);
        return m;
    }

    // ---------- responses ----------

    private static Reply parse(boolean anth, String body) {
        JsonObject o = JsonParser.parseString(body).getAsJsonObject();
        String text = "";
        if (anth) {
            for (JsonElement e : o.getAsJsonArray("content")) {
                JsonObject b = e.getAsJsonObject();
                if (b.has("type") && "text".equals(b.get("type").getAsString())) {
                    text = b.get("text").getAsString();
                    break;
                }
            }
        } else {
            text = o.getAsJsonArray("choices").get(0).getAsJsonObject()
                    .getAsJsonObject("message").get("content").getAsString();
        }
        return clean(text);
    }

    private static Reply clean(String raw) {
        String mood = "calm";
        Matcher m = MOOD.matcher(raw);
        if (m.find()) mood = m.group(1).toLowerCase();
        String t = ANY_TAG.matcher(raw).replaceAll("");
        t = t.replaceAll("\\s+", " ").trim();
        if (t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"")) t = t.substring(1, t.length() - 1).trim();
        if (t.length() > 220) t = t.substring(0, 217).trim() + "...";
        return new Reply(t, mood);
    }

    private static String errorDetail(String body) {
        try {
            JsonObject o = JsonParser.parseString(body).getAsJsonObject();
            if (o.has("error")) {
                JsonElement e = o.get("error");
                if (e.isJsonObject() && e.getAsJsonObject().has("message")) {
                    return ": " + e.getAsJsonObject().get("message").getAsString();
                }
                if (e.isJsonPrimitive()) return ": " + e.getAsString();
            }
        } catch (Exception ignored) {
            // not JSON
        }
        return "";
    }
}
