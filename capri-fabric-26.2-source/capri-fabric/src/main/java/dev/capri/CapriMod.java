package dev.capri;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Capri: a client-side companion that watches your screen and talks to you.
 * Nothing here talks to the game server: chat to her with "@c <text>" (never sent) or /capri say <text>.
 */
public final class CapriMod implements ClientModInitializer {
    public static final String ID = "capri";

    public static CapriConfig config;
    public static final CapriBrain BRAIN = new CapriBrain();
    public static final CapriHud HUD = new CapriHud();

    private static long nextAuto;
    private static int failures;

    private KeyMapping lookKey;
    private KeyMapping toggleKey;

    @Override
    public void onInitializeClient() {
        config = CapriConfig.load();

        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath(ID, "bubble"),
                HUD::render);

        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(ID, "main"));
        lookKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.capri.look", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, category));
        toggleKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.capri.toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, category));

        ClientTickEvents.END_CLIENT_TICK.register(this::tick);

        // "@c hello" or "@capri hello" is intercepted and never sent to the server.
        ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
            String m = message.trim();
            String lower = m.toLowerCase();
            String payload = null;
            if (lower.startsWith("@capri ")) payload = m.substring(7);
            else if (lower.startsWith("@c ")) payload = m.substring(3);
            if (payload == null) return true;
            ask(Minecraft.getInstance(), payload.trim());
            return false;
        });

        CapriCommands.register();
    }

    private void tick(Minecraft mc) {
        while (lookKey.consumeClick()) ask(mc, null);
        while (toggleKey.consumeClick()) {
            config.autoLook = !config.autoLook;
            config.save();
            failures = 0;
            nextAuto = System.currentTimeMillis() + config.intervalSeconds * 1000L;
            chat(mc, "Auto commentary " + (config.autoLook ? "on" : "off") + ".", ChatFormatting.GRAY);
        }
        if (mc.player == null || mc.level == null) return;
        if (config.autoLook && !BRAIN.busy() && !mc.isPaused() && System.currentTimeMillis() >= nextAuto) {
            ask(mc, null);
        }
    }

    /** userText == null means "look at the screen and react on your own". */
    public static void ask(Minecraft mc, String userText) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;
        if (!config.hasKey()) {
            chat(mc, "Capri has no API key yet. Run: /capri key <your key>  (or edit config/capri.json)", ChatFormatting.RED);
            return;
        }
        if (!BRAIN.tryBegin()) return; // already working on a reply

        boolean auto = userText == null;
        nextAuto = System.currentTimeMillis() + config.intervalSeconds * 1000L;
        HUD.setThinking(true);

        String situation = describe(mc);
        String sent = auto
                ? "SITUATION: " + situation + "\nThe host said nothing. React to the screenshot in one short line. Don't repeat earlier lines."
                : "SITUATION: " + situation + "\nThe host says: " + userText;
        String shortText = auto ? "(host said nothing; you commented on the screen)" : userText;

        Runnable[] fire = new Runnable[1];
        String[] image = new String[1];
        fire[0] = () -> BRAIN.send(config, sent, shortText, image[0],
                reply -> onReply(mc, reply),
                err -> onError(mc, err, auto));

        if (config.vision) {
            ScreenGrab.capture(config.imageWidth,
                    b64 -> mc.execute(() -> { image[0] = b64; fire[0].run(); }),
                    err -> mc.execute(() -> { image[0] = null; fire[0].run(); })); // fall back to text only
        } else {
            fire[0].run();
        }
    }

    private static void onReply(Minecraft mc, CapriBrain.Reply reply) {
        failures = 0;
        HUD.say(reply.text(), reply.mood());
        if (config.echoToChat) {
            capriLine(mc, reply.text());
        }
    }

    private static void onError(Minecraft mc, String err, boolean auto) {
        HUD.setThinking(false);
        failures++;
        chat(mc, "Capri error: " + err, ChatFormatting.RED);
        if (auto && failures >= 3) {
            config.autoLook = false;
            config.save();
            chat(mc, "Auto commentary turned off after 3 failures. Fix the problem, then press J or /capri auto on.", ChatFormatting.RED);
        }
    }

    private static String describe(Minecraft mc) {
        LocalPlayer p = mc.player;
        String held = p.getMainHandItem().isEmpty() ? "nothing" : p.getMainHandItem().getHoverName().getString();
        return String.format("Host health %.0f/%.0f, food %d/20, holding %s, %s.",
                p.getHealth(), p.getMaxHealth(), p.getFoodData().getFoodLevel(), held,
                "playing");
    }

    /** Local-only chat line (never sent to the server). */
    public static void chat(Minecraft mc, String msg, ChatFormatting color) {
        if (mc.player == null) return;
        mc.player.sendSystemMessage(Component.literal(msg).withStyle(color));
    }

    private static void capriLine(Minecraft mc, String capriLine) {
        if (mc.player == null) return;
        mc.player.sendSystemMessage(Component.literal("[Capri] ").withStyle(ChatFormatting.GREEN)
                .append(Component.literal(capriLine).withStyle(ChatFormatting.WHITE)));
    }
}
