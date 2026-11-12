package dev.capri;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Client-side /capri commands. They are never sent to the server. */
public final class CapriCommands {
    private CapriCommands() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommands.literal("capri")
                        .then(ClientCommands.literal("say")
                                .then(ClientCommands.argument("text", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            CapriMod.ask(Minecraft.getInstance(), StringArgumentType.getString(ctx, "text"));
                                            return 1;
                                        })))
                        .then(ClientCommands.literal("look")
                                .executes(ctx -> {
                                    CapriMod.ask(Minecraft.getInstance(), null);
                                    return 1;
                                }))
                        .then(ClientCommands.literal("key")
                                .then(ClientCommands.argument("apiKey", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            CapriMod.config.apiKey = StringArgumentType.getString(ctx, "apiKey").trim();
                                            CapriMod.config.save();
                                            ctx.getSource().sendFeedback(Component.literal("Capri: API key saved."));
                                            return 1;
                                        })))
                        .then(ClientCommands.literal("provider")
                                .then(ClientCommands.argument("name", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String v = StringArgumentType.getString(ctx, "name").toLowerCase();
                                            if (!v.equals("anthropic") && !v.equals("openai")) {
                                                ctx.getSource().sendFeedback(Component.literal("Capri: provider must be anthropic or openai."));
                                                return 0;
                                            }
                                            CapriMod.config.provider = v;
                                            CapriMod.config.save();
                                            ctx.getSource().sendFeedback(Component.literal("Capri: provider = " + v
                                                    + ", model = " + CapriMod.config.resolvedModel()));
                                            return 1;
                                        })))
                        .then(ClientCommands.literal("model")
                                .then(ClientCommands.argument("name", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            CapriMod.config.model = StringArgumentType.getString(ctx, "name").trim();
                                            CapriMod.config.save();
                                            ctx.getSource().sendFeedback(Component.literal("Capri: model = " + CapriMod.config.resolvedModel()));
                                            return 1;
                                        })))
                        .then(ClientCommands.literal("url")
                                .then(ClientCommands.argument("endpoint", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            CapriMod.config.endpoint = StringArgumentType.getString(ctx, "endpoint").trim();
                                            CapriMod.config.save();
                                            ctx.getSource().sendFeedback(Component.literal("Capri: endpoint = " + CapriMod.config.resolvedEndpoint()));
                                            return 1;
                                        })))
                        .then(ClientCommands.literal("interval")
                                .then(ClientCommands.argument("seconds", IntegerArgumentType.integer(10, 3600))
                                        .executes(ctx -> {
                                            CapriMod.config.intervalSeconds = IntegerArgumentType.getInteger(ctx, "seconds");
                                            CapriMod.config.save();
                                            ctx.getSource().sendFeedback(Component.literal("Capri: she looks every "
                                                    + CapriMod.config.intervalSeconds + "s."));
                                            return 1;
                                        })))
                        .then(ClientCommands.literal("auto")
                                .then(ClientCommands.argument("on", BoolArgumentType.bool())
                                        .executes(ctx -> {
                                            CapriMod.config.autoLook = BoolArgumentType.getBool(ctx, "on");
                                            CapriMod.config.save();
                                            ctx.getSource().sendFeedback(Component.literal("Capri: auto commentary "
                                                    + (CapriMod.config.autoLook ? "on" : "off")));
                                            return 1;
                                        })))
                        .then(ClientCommands.literal("vision")
                                .then(ClientCommands.argument("on", BoolArgumentType.bool())
                                        .executes(ctx -> {
                                            CapriMod.config.vision = BoolArgumentType.getBool(ctx, "on");
                                            CapriMod.config.save();
                                            ctx.getSource().sendFeedback(Component.literal("Capri: screenshots "
                                                    + (CapriMod.config.vision ? "on" : "off")));
                                            return 1;
                                        })))
                        .then(ClientCommands.literal("corner")
                                .then(ClientCommands.argument("where", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String v = StringArgumentType.getString(ctx, "where").toLowerCase();
                                            if (!v.matches("(top|bottom)_(left|right)")) {
                                                ctx.getSource().sendFeedback(Component.literal(
                                                        "Capri: use top_left, top_right, bottom_left or bottom_right."));
                                                return 0;
                                            }
                                            CapriMod.config.corner = v;
                                            CapriMod.config.save();
                                            return 1;
                                        })))
                        .then(ClientCommands.literal("clear")
                                .executes(ctx -> {
                                    CapriMod.BRAIN.clear();
                                    ctx.getSource().sendFeedback(Component.literal("Capri: forgot the conversation."));
                                    return 1;
                                }))
                        .then(ClientCommands.literal("status")
                                .executes(ctx -> {
                                    CapriConfig c = CapriMod.config;
                                    ctx.getSource().sendFeedback(Component.literal(
                                            "Capri: " + c.provider + " / " + c.resolvedModel()
                                                    + " | key " + (c.hasKey() ? "set" : "MISSING")
                                                    + " | auto " + (c.autoLook ? "on" : "off") + " every " + c.intervalSeconds + "s"
                                                    + " | screenshots " + (c.vision ? "on" : "off")));
                                    return 1;
                                }))
                        .executes(ctx -> {
                            ctx.getSource().sendFeedback(Component.literal(
                                    "Capri: /capri say <text> | look | key <key> | provider <anthropic|openai> | model <name> | "
                                            + "interval <s> | auto <true|false> | vision <true|false> | corner <where> | clear | status. "
                                            + "Or type: @c <text> in chat."));
                            return 1;
                        })));
    }
}
