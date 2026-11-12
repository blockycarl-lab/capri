package dev.capri;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Plays Capri's UI blips straight from sounds.json without registering anything in the game registry,
 * which keeps the mod fully client-side. Delete this file (and its two call sites) if you want no sound.
 */
public final class CapriSound {
    private static final Map<String, SoundEvent> CACHE = new HashMap<>();

    private CapriSound() {}

    public static void play(String name, float pitch) {
        try {
            SoundEvent ev = CACHE.computeIfAbsent(name,
                    n -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(CapriMod.ID, n)));
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(ev, pitch, 0.5f));
        } catch (Throwable ignored) {
            // sound is cosmetic; never let it break the HUD
        }
    }
}
