package dev.lumora.client.render;

import dev.lumora.api.render.NameTags;
import dev.lumora.client.LumoraClient;
import dev.lumora.client.api.LumoraRuntime;

import java.util.Optional;
import java.util.UUID;

/**
 * Addon icons next to player names (Lumora API 1.1 {@link NameTags}) on Minecraft 1.8.9: this version has no
 * custom fonts, so the icon's plain-text fallback is appended in the nearest chat color.
 */
public final class NameTagIcons {
    private static final int[] COLORS = {0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};
    private static boolean logged;

    private NameTagIcons() {
    }

    public static Optional<NameTags.Icon> icon(UUID player, NameTags.Place place) {
        var core = LumoraRuntime.core();
        if (core == null || player == null || !core.nameTags().active()) {
            return Optional.empty();
        }
        return core.nameTags().icon(player, place);
    }

    /** Chat formatting code of the legacy color nearest to {@code rgb}. */
    static String code(int rgb) {
        int best = 15;
        long bestDistance = Long.MAX_VALUE;
        for (int i = 0; i < COLORS.length; i++) {
            int dr = ((rgb >> 16) & 255) - ((COLORS[i] >> 16) & 255);
            int dg = ((rgb >> 8) & 255) - ((COLORS[i] >> 8) & 255);
            int db = (rgb & 255) - (COLORS[i] & 255);
            long d = (long) dr * dr + (long) dg * dg + (long) db * db;
            if (d < bestDistance) {
                bestDistance = d;
                best = i;
            }
        }
        return "§" + "0123456789abcdef".charAt(best);
    }

    /** {@code name} with the fallback icon of {@code player} appended, or {@code name} unchanged. */
    public static String decorate(String name, UUID player, NameTags.Place place) {
        var found = icon(player, place);
        if (found.isEmpty() || found.get().fallback().isEmpty()) {
            return name;
        }
        if (!logged) {
            logged = true;
            LumoraClient.LOG.info("LUMORA_NAMETAG_ICON first icon drawn (fallback text, {})", place);
        }
        return name + " " + code(found.get().color()) + found.get().fallback() + "§r";
    }

    /** Whether an icon was drawn since start (smoke test). */
    public static boolean drawn() {
        return logged;
    }
}
