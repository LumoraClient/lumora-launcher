package dev.lumora.launcher.core.loader;

import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import dev.lumora.launcher.core.config.LauncherConfig;
import dev.lumora.launcher.core.util.FileUtil;
import dev.lumora.launcher.core.util.Json;
import dev.lumora.launcher.core.util.Log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Selection, installed state, updates and removal of mod loaders, separately per loader and Minecraft version.
 * <ul>
 *   <li>A pinned version ({@link LauncherConfig#loaderVersions}) stays until the player changes it; without one the
 *       launcher follows the loader's recommended version and so updates by itself.</li>
 *   <li>Installed loaders are recognised by a small marker next to their launch profile in {@code versions/}.</li>
 *   <li>Version lists are cached on disk, so an installed loader still starts offline.</li>
 * </ul>
 */
public final class LoaderManager {
    /** Marker next to the launch profile. */
    static final String MARKER = ".lumora-loader.json";
    private static final long LIST_TTL_MILLIS = 30 * 60_000L;

    public record Installed(String loader, String minecraft, String version, String profileId, String installedAt) {
    }

    private final ModLoaders loaders;
    private final Path versionsDir;
    private final Path cacheDir;
    private final Map<String, List<LoaderVersion>> lists = new ConcurrentHashMap<>();
    private final Map<String, Long> listTimes = new ConcurrentHashMap<>();

    public LoaderManager(ModLoaders loaders, Path versionsDir) {
        this.loaders = loaders;
        this.versionsDir = versionsDir;
        this.cacheDir = versionsDir.resolve(".loader-cache");
    }

    public ModLoaders loaders() {
        return loaders;
    }

    public static String key(String loader, String mc) {
        return loader + "@" + mc;
    }

    /** Versions of {@code loader} for {@code mc} (newest first): network with a 30 minute cache, disk copy when offline. */
    public List<LoaderVersion> versions(String loader, String mc) throws IOException {
        String key = key(loader, mc);
        Long t = listTimes.get(key);
        if (t != null && System.currentTimeMillis() - t < LIST_TTL_MILLIS) {
            return lists.get(key);
        }
        ModLoader l = loaders.byId(loader).orElseThrow(() -> new IOException("Unbekannter Loader " + loader));
        Path cached = FileUtil.resolveInside(cacheDir, loader + "-" + mc + ".json");
        List<LoaderVersion> list;
        try {
            list = List.copyOf(l.versions(mc));
            FileUtil.writeAtomically(cached, Json.GSON.toJson(list).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            if (!Files.isRegularFile(cached)) {
                throw e;
            }
            Log.warn("Loader list " + key + " offline from cache (" + e.getMessage() + ")");
            try {
                list = List.copyOf(Json.GSON.fromJson(Files.readString(cached), new TypeToken<List<LoaderVersion>>() { }.getType()));
            } catch (JsonParseException | IllegalArgumentException | NullPointerException bad) {
                throw e;
            }
        }
        lists.put(key, list);
        listTimes.put(key, System.currentTimeMillis());
        return list;
    }

    /** The pinned version, or null when the loader follows its recommendation. */
    public static String pinned(LauncherConfig config, String loader, String mc) {
        return config.loaderVersions == null ? null : config.loaderVersions.get(key(loader, mc));
    }

    /**
     * The version to start: the pinned one, else the recommended one ({@code tested} for Fabric: the version Lumora
     * tested with the profile), else the newest installed one when the list is not reachable.
     */
    public String selected(LauncherConfig config, String loader, String mc, String tested) throws IOException {
        String pin = pinned(config, loader, mc);
        if (pin != null) {
            return pin;
        }
        if (tested != null) {
            return tested;
        }
        try {
            return recommended(loader, mc).map(LoaderVersion::version)
                    .orElseThrow(() -> new IOException(loaders.byId(loader).map(ModLoader::displayName).orElse(loader)
                            + " gibt es nicht für Minecraft " + mc));
        } catch (IOException e) {
            Optional<Installed> any = installed(loader, mc).stream().findFirst();
            if (any.isPresent()) {
                return any.get().version();
            }
            throw e;
        }
    }

    public Optional<LoaderVersion> recommended(String loader, String mc) throws IOException {
        List<LoaderVersion> list = versions(loader, mc);
        return list.stream().filter(LoaderVersion::recommended).findFirst().or(() -> list.stream().findFirst());
    }

    /** Installed versions of {@code loader} for {@code mc}, newest install first. Either argument may be null (= all). */
    public List<Installed> installed(String loader, String mc) {
        List<Installed> out = new ArrayList<>();
        if (!Files.isDirectory(versionsDir)) {
            return out;
        }
        try (var dirs = Files.list(versionsDir)) {
            for (Path d : dirs.toList()) {
                Path marker = d.resolve(MARKER);
                if (!Files.isRegularFile(marker)) {
                    continue;
                }
                try {
                    Installed i = Json.GSON.fromJson(Files.readString(marker), Installed.class);
                    if (i != null && i.loader() != null && (loader == null || loader.equals(i.loader()))
                            && (mc == null || mc.equals(i.minecraft())) && d.getFileName().toString().equals(i.profileId())) {
                        out.add(i);
                    }
                } catch (IOException | JsonParseException e) {
                    Log.warn("Unreadable loader marker " + marker + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            Log.warn("Could not list installed loaders: " + e.getMessage());
        }
        out.sort(Comparator.comparing((Installed i) -> i.installedAt() == null ? "" : i.installedAt()).reversed());
        return out;
    }

    public boolean isInstalled(String loader, String mc, String version) {
        return installed(loader, mc).stream().anyMatch(i -> i.version().equals(version));
    }

    /** Called by the client installer after a successful installation. */
    public void recordInstalled(String loader, String mc, String version) throws IOException {
        ModLoader l = loaders.byId(loader).orElseThrow();
        String id = l.profileId(mc, version);
        Installed i = new Installed(loader, mc, version, id, Instant.now().toString());
        FileUtil.writeAtomically(FileUtil.resolveInside(versionsDir, id + "/" + MARKER), Json.GSON.toJson(i).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * A newer recommended version than the one the player would start now; empty when pinned (the player chose a
     * version), when the selected one is the newest, or when the list cannot be loaded.
     */
    public Optional<LoaderVersion> update(LauncherConfig config, String loader, String mc) {
        if (pinned(config, loader, mc) != null) {
            return Optional.empty();
        }
        try {
            Optional<LoaderVersion> rec = recommended(loader, mc);
            List<Installed> have = installed(loader, mc);
            if (rec.isEmpty() || have.isEmpty() || have.stream().anyMatch(i -> i.version().equals(rec.get().version()))) {
                return Optional.empty();
            }
            return rec;
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /**
     * Removes one installed loader version (its launch profile and generated files below {@code versions/}). Shared
     * libraries stay; worlds and mods of the instance are never touched.
     */
    public void uninstall(String loader, String mc, String version) throws IOException {
        ModLoader l = loaders.byId(loader).orElseThrow(() -> new IOException("Unbekannter Loader " + loader));
        Path dir = FileUtil.resolveInside(versionsDir, l.profileId(mc, version));
        if (!Files.isRegularFile(dir.resolve(MARKER))) {
            throw new IOException(l.displayName() + " " + version + " ist nicht installiert");
        }
        ForgeInstaller.deleteTree(dir);
        Log.info("Removed loader " + loader + " " + version + " for " + mc);
    }
}
