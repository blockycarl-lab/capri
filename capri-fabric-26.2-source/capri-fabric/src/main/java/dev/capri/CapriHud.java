package dev.capri;

import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/** Neon cyberpunk speech bubble with a typewriter effect. */
public final class CapriHud {
    private static final Identifier CHIP = Identifier.fromNamespaceAndPath(CapriMod.ID, "textures/gui/chip.png");

    private static final int GREEN = 0xFF39FF14;
    private static final int PINK = 0xFFFF2D55;
    private static final int BG = 0xE6090912;
    private static final int WHITE = 0xFFF2F2F2;

    private static final int TEXT_W = 170;
    private static final int PAD = 6;
    private static final int ICON = 24;
    private static final int MARGIN = 6;
    private static final long CHAR_MS = 24;

    private String text = "";
    private String mood = "calm";
    private long shownAt;
    private long fullAt;
    private int lastBlip;
    private List<String> lines;
    private boolean thinking;

    public void setThinking(boolean on) { thinking = on; }

    public void say(String t, String m) {
        text = t;
        mood = m;
        shownAt = Util.getMillis();
        fullAt = 0;
        lastBlip = 0;
        lines = null;
        thinking = false;
        if (CapriMod.config.sounds) CapriSound.play("appear", 1.0f);
    }

    private static int accent(String mood) {
        return switch (mood) {
            case "happy" -> GREEN;
            case "mischief" -> PINK;
            case "worried" -> 0xFFFFD60A;
            case "angry" -> 0xFFFF0033;
            case "sad" -> 0xFF4DA3FF;
            default -> 0xFF8A5CFF; // calm
        };
    }

    public void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Font font = mc.font;
        long now = Util.getMillis();

        boolean showText = !text.isEmpty() && (fullAt == 0 || now - fullAt < 5000L + text.length() * 50L);

        if (thinking) {
            int dots = (int) ((now / 300) % 4);
            String label = "CAPRI is watching" + ".".repeat(dots);
            int w = PAD + ICON + 6 + font.width("CAPRI is watching...") + PAD;
            int h = ICON + PAD * 2;
            int[] xy = place(mc, w, h);
            panel(g, font, xy[0], xy[1], w, h, PINK);
            g.text(font, label, xy[0] + PAD + ICON + 6, xy[1] + (h - 8) / 2, GREEN, false);
            return;
        }
        if (!showText) return;

        if (lines == null) lines = wrap(font, text, TEXT_W);
        int total = text.length();
        int typed = Math.min(total, (int) ((now - shownAt) / CHAR_MS));
        if (typed >= total && fullAt == 0) fullAt = now;
        if (typed > lastBlip) {
            if (typed / 3 != lastBlip / 3 && CapriMod.config.sounds) {
                CapriSound.play("blip", 0.9f + (typed % 5) * 0.05f);
            }
            lastBlip = typed;
        }

        int textW = 60;
        for (String l : lines) textW = Math.max(textW, font.width(l));
        int w = PAD + ICON + 6 + textW + PAD;
        int h = PAD + 11 + lines.size() * 10 + PAD;
        int[] xy = place(mc, w, h);
        int x = xy[0];
        int y = xy[1];

        panel(g, font, x, y, w, h, accent(mood));
        int tx = x + PAD + ICON + 6;
        g.text(font, "CAPRI", tx, y + PAD, GREEN, false);

        int remaining = typed;
        int ly = y + PAD + 11;
        for (String line : lines) {
            if (remaining <= 0) break;
            int n = Math.min(line.length(), remaining);
            g.text(font, line.substring(0, n), tx, ly, WHITE, false);
            remaining -= line.length() + 1;
            ly += 10;
        }
    }

    private static void panel(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int accent) {
        g.fill(x, y, x + w, y + h, BG);
        g.outline(x, y, w, h, accent);
        g.fill(x, y, x + 3, y + h, accent);
        g.blit(RenderPipelines.GUI_TEXTURED, CHIP, x + PAD + 2, y + PAD, 0, 0, ICON, ICON, 48, 48, 48, 48);
    }

    private static int[] place(Minecraft mc, int w, int h) {
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        String c = CapriMod.config.corner == null ? "bottom_right" : CapriMod.config.corner;
        int x = c.endsWith("left") ? MARGIN : sw - w - MARGIN;
        int y = c.startsWith("top") ? MARGIN : sh - h - MARGIN;
        return new int[]{x, y};
    }

    private static List<String> wrap(Font font, String text, int maxW) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : text.split(" ")) {
            String test = cur.length() == 0 ? word : cur + " " + word;
            if (cur.length() > 0 && font.width(test) > maxW) {
                out.add(cur.toString());
                cur = new StringBuilder(word);
            } else {
                cur = new StringBuilder(test);
            }
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }
}
