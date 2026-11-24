package dev.lumora.core.cosmetics;

import java.util.Locale;

/** How a cosmetic's texture is drawn: opaque with cut-out holes, see-through, or glowing (full light). */
public enum RenderMode {
    CUTOUT,
    TRANSLUCENT,
    EMISSIVE;

    public static RenderMode of(String name) {
        for (RenderMode m : values()) {
            if (m.name().equalsIgnoreCase(name == null ? "" : name.trim())) {
                return m;
            }
        }
        return CUTOUT;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
