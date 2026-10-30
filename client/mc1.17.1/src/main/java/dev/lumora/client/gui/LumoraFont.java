package dev.lumora.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;

import java.util.ArrayList;
import java.util.List;

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
        List<String> selected = new ArrayList<>(repo.getSelectedIds());
        if (on == selected.contains(packId)) {
            return;
        }
        if (on) {
            selected.add(packId);
        } else {
            selected.remove(packId);
        }
        repo.setSelected(selected);
        // Minecraft 1.17.1 has no Options#updateResourcePacks: write the selection like the resource pack screen does,
        // then save options.txt and reload the resources
        mc.options.resourcePacks.clear();
        mc.options.incompatibleResourcePacks.clear();
        for (Pack pack : repo.getSelectedPacks()) {
            if (!pack.isFixedPosition()) {
                mc.options.resourcePacks.add(pack.getId());
                if (!pack.getCompatibility().isCompatible()) {
                    mc.options.incompatibleResourcePacks.add(pack.getId());
                }
            }
        }
        mc.options.save();
        mc.reloadResourcePacks();
    }
}
