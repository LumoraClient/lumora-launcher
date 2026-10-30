package dev.lumora.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.PackRepository;

/** Switches the built-in "Lumora Schrift" resource pack (Inter) on or off and reloads the resources. */
public final class LumoraFont {
    public static final String PACK_ID = "lumora:lumora_font";
    /** The built-in "Lumora Theme" pack (registered in LumoraClient). */
    public static final String THEME_PACK_ID = "lumora:lumora_theme";

    private LumoraFont() {
    }

    public static void apply(Minecraft mc, boolean on) {
        applyPack(mc, PACK_ID, on);
    }

    /** Switches one built-in resource pack on or off and reloads the resources (no-op if already in that state). */
    public static void applyPack(Minecraft mc, String packId, boolean on) {
        PackRepository repo = mc.getResourcePackRepository();
        if (!repo.isAvailable(packId)) {
            return;
        }
        boolean selected = repo.getSelectedIds().contains(packId);
        if (on == selected) {
            return;
        }
        if (on) {
            repo.addPack(packId);
        } else {
            repo.removePack(packId);
        }
        // writes options.txt and reloads the resources, like the resource pack screen does
        mc.options.updateResourcePacks(repo);
    }
}
