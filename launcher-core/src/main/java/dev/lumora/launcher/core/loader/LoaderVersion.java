package dev.lumora.launcher.core.loader;

/**
 * A loader version for one Minecraft version.
 *
 * @param version     the loader's own version id as it is installed (Forge: the full Maven version, e.g.
 *                    "1.20.1-47.4.28"; NeoForge: "21.1.209")
 * @param label       short form for the UI ("47.4.28")
 * @param stable      not a beta, alpha or snapshot build
 * @param recommended the version the loader project recommends for this Minecraft version
 */
public record LoaderVersion(String version, String label, boolean stable, boolean recommended) {
    public LoaderVersion {
        if (version == null || version.isBlank() || !version.matches("[A-Za-z0-9._+-]{1,80}")) {
            throw new IllegalArgumentException("invalid loader version: " + version);
        }
        if (label == null || label.isBlank()) {
            label = version;
        }
    }
}
