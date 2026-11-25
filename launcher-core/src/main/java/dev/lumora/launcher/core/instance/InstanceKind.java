package dev.lumora.launcher.core.instance;

import java.util.List;
import java.util.Locale;

/**
 * What an instance starts. {@link #LUMORA} is Minecraft with the Lumora client and its addons (started by the Lumora
 * Loader, no mod loader needed); {@link #VANILLA} is plain Minecraft; the others start Minecraft with that mod loader
 * and the mods in the instance's {@code mods} folder.
 */
public enum InstanceKind {
    LUMORA("lumora", "Lumora", null),
    VANILLA("vanilla", "Vanilla", null),
    FABRIC("fabric", "Fabric", "fabric"),
    QUILT("quilt", "Quilt", "quilt"),
    FORGE("forge", "Forge", "forge"),
    NEOFORGE("neoforge", "NeoForge", "neoforge");

    private final String id;
    private final String label;
    private final String loaderId;

    InstanceKind(String id, String label, String loaderId) {
        this.id = id;
        this.label = label;
        this.loaderId = loaderId;
    }

    public String id() {
        return id;
    }

    /** Product name (not translated). */
    public String label() {
        return label;
    }

    /** Id of the mod loader in {@code dev.lumora.launcher.core.loader.ModLoaders}; null for Lumora and Vanilla. */
    public String loaderId() {
        return loaderId;
    }

    /** Runs third-party mods from the {@code mods} folder. */
    public boolean modded() {
        return loaderId != null;
    }

    /**
     * Loader names whose mods run on this kind, as platforms tag them (Modrinth "loaders"), best match first.
     * Quilt also loads most Fabric mods; NeoForge for Minecraft 1.20.1 still loads Forge mods.
     */
    public List<String> modLoaders(String minecraftVersion) {
        return switch (this) {
            case FABRIC -> List.of("fabric");
            case QUILT -> List.of("quilt", "fabric");
            case FORGE -> List.of("forge");
            case NEOFORGE -> "1.20.1".equals(minecraftVersion) ? List.of("neoforge", "forge") : List.of("neoforge");
            default -> List.of();
        };
    }

    public static InstanceKind of(String id) {
        if (id != null) {
            for (InstanceKind k : values()) {
                if (k.id.equals(id.toLowerCase(Locale.ROOT))) {
                    return k;
                }
            }
        }
        return null;
    }
}
