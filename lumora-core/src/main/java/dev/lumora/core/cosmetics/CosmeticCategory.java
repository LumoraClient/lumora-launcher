package dev.lumora.core.cosmetics;

import java.util.Locale;

/**
 * Category of a cosmetic as the menu groups it. Masks, head and back accessories are "accessories"; auras, particle
 * effects and trails are "effects"; shoulder pets and floating companions are "companions". The finer kind is the
 * definition's {@code type}.
 */
public enum CosmeticCategory {
    WINGS,
    CAPES,
    HATS,
    ACCESSORIES,
    EFFECTS,
    COMPANIONS;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static CosmeticCategory of(String name) {
        for (CosmeticCategory c : values()) {
            if (c.id().equalsIgnoreCase(name == null ? "" : name.trim())) {
                return c;
            }
        }
        return ACCESSORIES;
    }
}
