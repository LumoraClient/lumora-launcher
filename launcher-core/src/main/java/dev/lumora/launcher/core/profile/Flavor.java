package dev.lumora.launcher.core.profile;

import java.util.Locale;

/**
 * What the launcher starts for a profile's Minecraft version. {@link #LUMORA} is the profile as defined (Fabric with the
 * Lumora mods); {@link #FABRIC} the same Fabric loader without any Lumora mods; {@link #VANILLA} plain Minecraft.
 * Fabric and Vanilla get their own game folder, so worlds, options and mods of the three never mix.
 */
public enum Flavor {
    LUMORA("lumora", "Lumora Client"),
    FABRIC("fabric", "Fabric"),
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

    /** Instance folder name: the profile id for Lumora, else "&lt;profile&gt;-fabric" / "&lt;profile&gt;-vanilla". */
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
