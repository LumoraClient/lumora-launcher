package dev.lumora.core.cosmetics;

import java.util.Locale;

/** Category of an emote in the emote menu ("favorites" is a filter, not a category of its own). */
public enum EmoteCategory {
    DANCE,
    SOCIAL,
    FUNNY,
    ACTIONS,
    REACTIONS,
    SITTING,
    CELEBRATIONS,
    SPECIAL;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static EmoteCategory of(String name) {
        for (EmoteCategory c : values()) {
            if (c.id().equalsIgnoreCase(name == null ? "" : name.trim())) {
                return c;
            }
        }
        return SPECIAL;
    }
}
