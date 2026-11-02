package dev.lumora.launcher.core.profile;

import java.util.Locale;

/**
 * What the launcher starts for a profile's Minecraft version. {@link #LUMORA} is the profile as defined (Fabric with the
 * Lumora mods); {@link #VANILLA} is plain Minecraft; the others are a mod loader without any Lumora mods
 * ({@link #loaderId()} names the loader in {@code dev.lumora.launcher.core.loader.ModLoaders}). Every flavor except
 * Lumora gets its own game folder, so worlds, options and mods never mix.
 */
public enum Flavor {
    LUMORA("lumora", "Lumora Client", null),
    VANILLA("vanilla", "Vanilla", null),
    FABRIC("fabric", "Fabric", "fabric"),
    QUILT("quilt", "Quilt", "quilt"),
    FORGE("forge", "Forge", "forge"),
    NEOFORGE("neoforge", "NeoForge", "neoforge");

    private final String id;
    private final String label;
    private final String loaderId;

    Flavor(String id, String label, String loaderId) {
        this.id = id;
        this.label = label;
        this.loaderId = loaderId;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    /** The mod loader this flavor starts, null for Lumora (its own Fabric setup) and Vanilla. */
    public String loaderId() {
        return loaderId;
    }

    /** Instance folder name: the profile id for Lumora, else "&lt;profile&gt;-&lt;flavor&gt;". */
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

    /** The flavor of a mod loader id, or null. */
    public static Flavor ofLoader(String loaderId) {
        for (Flavor f : values()) {
            if (f.loaderId != null && f.loaderId.equals(loaderId)) {
                return f;
            }
        }
        return null;
    }
}
