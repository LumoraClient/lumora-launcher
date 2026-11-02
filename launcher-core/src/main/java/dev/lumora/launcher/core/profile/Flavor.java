package dev.lumora.launcher.core.profile;

import java.util.Locale;

/**
 * What the launcher starts for a profile's Minecraft version. {@link #LUMORA} is the profile as defined (the Lumora
 * client with its mods and addons; Fabric runs underneath and is never chosen by the player); {@link #VANILLA} is
 * plain Minecraft in its own game folder, so worlds and options of the two never mix.
 * <p>
 * Selectable mod loaders (Fabric, Quilt, Forge, NeoForge) are removed for now; a stored id of one of them falls back
 * to {@link #LUMORA}. Their old instance folders ("&lt;profile&gt;-fabric" ...) are left untouched on disk.
 */
public enum Flavor {
    LUMORA("lumora", "Lumora Client"),
    VANILLA("vanilla", "Vanilla");

    private final String id;
    private final String label;

    Flavor(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    /** Instance folder name: the profile id for Lumora, else "&lt;profile&gt;-vanilla". */
    public String instanceId(String profileId) {
        return this == LUMORA ? profileId : profileId + "-" + id;
    }

    /** Unknown or null ids fall back to {@link #LUMORA}. */
    public static Flavor of(String id) {
        if (id != null) {
            for (Flavor f : values()) {
                if (f.id.equals(id.toLowerCase(Locale.ROOT))) {
                    return f;
                }
            }
        }
        return LUMORA;
    }
}
