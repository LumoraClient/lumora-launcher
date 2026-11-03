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

    /** Lumora's font (the one chosen in the hub) on, or Minecraft's own font. */
    public static void apply(Minecraft mc, boolean on) {
        select(mc, on ? configured() : dev.lumora.core.theme.LumoraFonts.VANILLA);
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
        // Minecraft 1.16.5 has no Options#updateResourcePacks: write the selection like the resource pack screen does,
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

    /** Shows the font with this id (dev.lumora.core.theme.LumoraFonts; "vanilla": Minecraft's own) in one reload. */
    public static void select(Minecraft mc, String font) {
        PackRepository repo = mc.getResourcePackRepository();
        String wanted = dev.lumora.core.theme.LumoraFonts.get(font).packId();
        List<String> selected = new ArrayList<>(repo.getSelectedIds());
        boolean changed = false;
        for (String id : dev.lumora.core.theme.LumoraFonts.packIds()) {
            boolean on = id.equals(wanted);
            if (!repo.isAvailable(id) || selected.contains(id) == on) {
                continue;
            }
            if (on) {
                selected.add(id);
            } else {
                selected.remove(id);
            }
            changed = true;
        }
        if (!changed) {
            return;
        }
        repo.setSelected(selected);
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

    private static String configured() {
        dev.lumora.client.config.LumoraConfig c = dev.lumora.client.LumoraClient.config();
        return c == null ? dev.lumora.core.theme.LumoraFonts.DEFAULT : c.font;
    }
}
