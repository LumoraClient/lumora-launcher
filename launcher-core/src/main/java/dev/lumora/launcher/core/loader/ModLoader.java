package dev.lumora.launcher.core.loader;

import dev.lumora.launcher.core.install.ProgressListener;
import dev.lumora.launcher.core.mojang.VersionJson;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;

/**
 * One mod loader (Fabric, Quilt, Forge, NeoForge, ...). A loader only knows how to list its versions for a Minecraft
 * version and how to turn one of them into a launch profile (a version JSON that inherits from vanilla). Everything
 * else (vanilla files, assets, Java, starting the game) is shared and done by the client installer, so a new loader
 * is one class registered in {@link ModLoaders}.
 * <p>
 * Implementations only download from the hosts of the official download allow-list and verify every file with the
 * hash its publisher states.
 */
public interface ModLoader {
    /** Stable id used in settings and instance folder names ("fabric", "quilt", "forge", "neoforge"). */
    String id();

    String displayName();

    /** One sentence for the loader page. */
    String description();

    /** The project's own page for supporting its developers (shown next to the install button), null if none. */
    default URI supportUrl() {
        return null;
    }

    /** Quick check without network: whether the loader exists for this Minecraft version at all. */
    default boolean supports(String minecraftVersion) {
        return true;
    }

    /** Versions for {@code minecraftVersion}, newest first; empty if the loader does not support it. May use the network. */
    List<LoaderVersion> versions(String minecraftVersion) throws IOException;

    /** Folder and id of the launch profile below {@code versions/}. */
    String profileId(String minecraftVersion, String loaderVersion);

    /**
     * Installs the loader if needed and returns its launch profile. Idempotent: an existing installation is only
     * checked (and repaired with {@link Install#repair()}).
     */
    VersionJson install(Install request, ProgressListener progress) throws IOException;

    /**
     * Whether the profile expects the vanilla jar under its own id ({@code versions/<id>/<id>.jar}), like the official
     * launcher creates it. Forge and NeoForge exclude it from their module layer by that file name.
     */
    default boolean needsOwnClientJar() {
        return false;
    }

    /** Whether the loader runs Fabric mods (the launcher then checks their dependencies before the start). */
    default boolean fabricMods() {
        return false;
    }

    /**
     * @param vanilla    the vanilla version JSON (downloads, mappings)
     * @param vanillaJar the verified vanilla client jar
     * @param java       Java executable for installer tools (the version's own runtime)
     */
    record Install(String minecraftVersion, String loaderVersion, VersionJson vanilla, Path vanillaJar, Path java,
                   boolean repair) {
    }
}
