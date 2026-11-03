package dev.lumora.client.gui;

import net.minecraft.client.MinecraftClient;

/**
 * Lumora font (Inter) and theme packs on Minecraft 1.12.2: not available. 1.12.2's font renderer only draws bitmap fonts
 * (TTF fonts arrived in 1.13) and Legacy Fabric registers no built-in resource packs; the Lumora look of vanilla buttons
 * comes from the skin mixins instead. Kept with the API of the newer modules, so the hub code is unchanged.
 */
public final class LumoraFont {
    public static final String PACK_ID = "lumora:lumora_font";
    public static final String THEME_PACK_ID = "lumora:lumora_theme";

    private LumoraFont() {
    }

    /** Whether the font switch exists in this version (false: the hub hides it). */
    public static boolean supported() {
        return false;
    }

    public static void apply(MinecraftClient mc, boolean on) {
    }

    public static void applyPack(MinecraftClient mc, String packId, boolean on) {
    }

    /** Fonts need TrueType support (Minecraft 1.13+): nothing to switch on this version. */
    public static void select(net.minecraft.client.MinecraftClient mc, String font) {
    }
}
