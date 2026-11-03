package dev.lumora.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.PackRepository;

/** Switches the built-in "Lumora Schrift" resource pack (Inter) on or off and reloads the resources. */
public final class LumoraFont {
    public static final String PACK_ID = "lumora:lumora_font";

    private LumoraFont() {
    }

    /** Lumora's font (the one chosen in the hub) on, or Minecraft's own font. */
    public static void apply(Minecraft mc, boolean on) {
        select(mc, on ? configured() : dev.lumora.core.theme.LumoraFonts.VANILLA);
    }

    /** Shows the font with this id (dev.lumora.core.theme.LumoraFonts; "vanilla": Minecraft's own) in one reload. */
    public static void select(Minecraft mc, String font) {
        PackRepository repo = mc.getResourcePackRepository();
        String wanted = dev.lumora.core.theme.LumoraFonts.get(font).packId();
        boolean changed = false;
        for (String id : dev.lumora.core.theme.LumoraFonts.packIds()) {
            boolean on = id.equals(wanted);
            if (!repo.isAvailable(id) || repo.getSelectedIds().contains(id) == on) {
                continue;
            }
            if (on) {
                repo.addPack(id);
            } else {
                repo.removePack(id);
            }
            changed = true;
        }
        if (changed) {
            // writes options.txt and reloads the resources, like the resource pack screen does
            mc.options.updateResourcePacks(repo);
        }
    }

    private static String configured() {
        dev.lumora.client.config.LumoraConfig c = dev.lumora.client.LumoraClient.config();
        return c == null ? dev.lumora.core.theme.LumoraFonts.DEFAULT : c.font;
    }
}
