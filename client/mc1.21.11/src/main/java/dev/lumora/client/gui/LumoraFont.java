package dev.lumora.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.PackRepository;

/** Switches the built-in "Lumora Schrift" resource pack (Inter) on or off and reloads the resources. */
public final class LumoraFont {
    public static final String PACK_ID = "lumora:lumora_font";

    private LumoraFont() {
    }

    public static void apply(Minecraft mc, boolean on) {
        PackRepository repo = mc.getResourcePackRepository();
        if (!repo.isAvailable(PACK_ID)) {
            return;
        }
        boolean selected = repo.getSelectedIds().contains(PACK_ID);
        if (on == selected) {
            return;
        }
        if (on) {
            repo.addPack(PACK_ID);
        } else {
            repo.removePack(PACK_ID);
        }
        // writes options.txt and reloads the resources, like the resource pack screen does
        mc.options.updateResourcePacks(repo);
    }
}
