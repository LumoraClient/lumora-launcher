package dev.lumora.core.cosmetics;

import java.util.Locale;

/** How rare a cosmetic or emote is (only shown and filtered; Lumora has no shop). */
public enum Rarity {
    COMMON(0xFFB0B8C4),
    UNCOMMON(0xFF5FD068),
    RARE(0xFF4FA3FF),
    EPIC(0xFFB06CFF),
    LEGENDARY(0xFFFFB13B);

    /** Colour of the rarity stripe and label (ARGB). */
    public final int color;

    Rarity(int color) {
        this.color = color;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Rarity of(String name) {
        for (Rarity r : values()) {
            if (r.id().equalsIgnoreCase(name == null ? "" : name.trim())) {
                return r;
            }
        }
        return COMMON;
    }
}
