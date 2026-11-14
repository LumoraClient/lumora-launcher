package dev.lumora.client.platform;

import dev.lumora.loader.api.LoadedJar;
import dev.lumora.loader.api.LumoraLoader;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * What the client used to ask the mod loader, answered by the Lumora Loader: folders, versions and whether
 * something is loaded. Without a mod loader there are no other mods, so Sodium, ViaFabricPlus or Mod Menu are
 * never "loaded" and the integrations for them stay off.
 */
public final class LoaderEnv {
    public static final String CLIENT_ID = "lumora";

    private LoaderEnv() {
    }

    public static Path gameDir() {
        return LumoraLoader.gameDir();
    }

    /** Parent of Lumora's settings and addons (shared by all versions or only this instance). */
    public static Path lumoraDir() {
        return LumoraLoader.lumoraDir();
    }

    /** This instance's own folder (resources unpacked from its jars). */
    public static Path instanceDir() {
        return LumoraLoader.instanceDir();
    }

    public static Path configDir() {
        return LumoraLoader.configDir();
    }

    public static boolean isLoaded(String id) {
        return LumoraLoader.isLoaded(id);
    }

    public static Optional<String> version(String id) {
        if ("minecraft".equals(id)) {
            return LumoraLoader.minecraftVersion().isEmpty() ? Optional.empty() : Optional.of(LumoraLoader.minecraftVersion());
        }
        if ("lumora-loader".equals(id)) {
            return Optional.of(LumoraLoader.VERSION);
        }
        return LumoraLoader.component(id).map(LoadedJar::version);
    }

    /** "id version" of everything that runs (diagnostics). */
    public static List<String> loadedList() {
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        out.add("lumora-loader " + LumoraLoader.VERSION);
        version("minecraft").ifPresent(v -> out.add("minecraft " + v));
        for (LoadedJar j : LumoraLoader.components()) {
            out.add(j.id() + " " + j.version());
        }
        return out;
    }
}
