package dev.lumora.loader.api;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What the game side may ask the Lumora Loader: folders, the started components and their versions. Replaces what a
 * mod loader used to answer. Filled once by the loader before any entry point runs.
 */
public final class LumoraLoader {
    /** Version of the loader itself. */
    public static final String VERSION = "1.0.0";

    private static volatile Path gameDir = Path.of(".");
    private static volatile String minecraftVersion = "";
    private static volatile List<LoadedJar> components = List.of();
    private static final Map<String, FileSystem> ROOTS = new ConcurrentHashMap<>();

    private LumoraLoader() {
    }

    /** Called by the loader's start code; later calls are ignored (the values never change while the game runs). */
    public static synchronized void init(Path game, String minecraft, List<LoadedJar> started) {
        if (!components.isEmpty()) {
            return;
        }
        gameDir = game.toAbsolutePath().normalize();
        minecraftVersion = minecraft == null ? "" : minecraft;
        components = List.copyOf(started);
    }

    /** {@code .minecraft} of this instance (Minecraft's {@code --gameDir}). */
    public static Path gameDir() {
        return gameDir;
    }

    /** {@code .minecraft/config}. */
    public static Path configDir() {
        return gameDir.resolve("config");
    }

    /** The Minecraft version the launcher started (empty when unknown). */
    public static String minecraftVersion() {
        return minecraftVersion;
    }

    public static List<LoadedJar> components() {
        return components;
    }

    public static Optional<LoadedJar> component(String id) {
        for (LoadedJar c : components) {
            if (c.id().equals(id)) {
                return Optional.of(c);
            }
        }
        return Optional.empty();
    }

    /**
     * Whether something with this id runs. Without a mod loader only the loader's own components exist, so e.g.
     * "sodium" or "viafabricplus" are never loaded.
     */
    public static boolean isLoaded(String id) {
        return "lumora-loader".equals(id) || component(id).isPresent();
    }

    /** Root directory of a component's jar (read-only zip file system), e.g. for its built-in resource packs. */
    public static Optional<Path> root(String id) {
        Optional<LoadedJar> c = component(id);
        if (c.isEmpty()) {
            return Optional.empty();
        }
        try {
            FileSystem fs = ROOTS.computeIfAbsent(id, k -> open(c.get().file()));
            return Optional.of(fs.getPath("/"));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static FileSystem open(Path jar) {
        URI uri = URI.create("jar:" + jar.toUri());
        try {
            return FileSystems.newFileSystem(uri, Map.of());
        } catch (FileSystemAlreadyExistsException e) {
            return FileSystems.getFileSystem(uri);
        } catch (IOException e) {
            throw new IllegalStateException("cannot open " + jar + ": " + e.getMessage(), e);
        }
    }
}
