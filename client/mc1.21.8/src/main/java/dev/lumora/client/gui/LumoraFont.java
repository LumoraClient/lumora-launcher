package dev.lumora.client.gui;

import dev.lumora.client.platform.Packs;
import dev.lumora.core.theme.LumoraFonts;
import dev.lumora.core.theme.ThemeLayers;
import net.minecraft.client.Minecraft;

/**
 * Lumora's theme and fonts are built into the client (no resource packs to pick, see ThemeLayers): the hub switches
 * them, a resource reload shows the change.
 */
public final class LumoraFont {
    private LumoraFont() {
    }

    /** Lumora's font (the one chosen in the hub) on, or Minecraft's own font. */
    public static void apply(Minecraft mc, boolean on) {
        select(mc, on ? configured() : LumoraFonts.VANILLA);
    }

    /** Shows the font with this id (LumoraFonts; "vanilla": Minecraft's own) instead of the hub's choice, in one reload. */
    public static void select(Minecraft mc, String font) {
        ThemeLayers.overrideFont(font);
        reloadIfChanged(mc);
    }

    /** Shows theme and font as chosen in the hub; reloads the resources only if they changed. */
    public static void refresh(Minecraft mc) {
        ThemeLayers.overrideFont(null);
        reloadIfChanged(mc);
    }

    private static void reloadIfChanged(Minecraft mc) {
        if (Packs.changed()) {
            mc.reloadResourcePacks();
        }
    }

    private static String configured() {
        dev.lumora.client.config.LumoraConfig c = dev.lumora.client.LumoraClient.config();
        return c == null ? LumoraFonts.DEFAULT : c.font;
    }
}
