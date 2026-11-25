package dev.lumora.launcher.core.loader;

import dev.lumora.launcher.core.i18n.Lang;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
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
        ModLoader l = loaders.byId(loader).orElseThrow(() -> new IOException(Lang.t("loader.error.unknown", loader)));
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
     * A newer recommended version than {@code current}; empty when {@code current} is the recommended or a newer one, or
     * when the list cannot be loaded. The instance decides whether it follows (not pinned) or keeps its version.
     */
    public Optional<LoaderVersion> update(String loader, String mc, String current) {
        try {
            Optional<LoaderVersion> rec = recommended(loader, mc);
            if (rec.isEmpty() || current == null || rec.get().version().equals(current)) {
                return Optional.empty();
            }
            List<LoaderVersion> list = versions(loader, mc);
            int recIndex = list.indexOf(rec.get());
            int curIndex = -1;
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).version().equals(current)) {
                    curIndex = i;
                }
            }
            // lists are newest first: only offer the recommendation if it is newer than the current version
            return curIndex >= 0 && recIndex >= curIndex ? Optional.empty() : rec;
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /**
     * Removes one installed loader version (its launch profile and generated files below {@code versions/}). Shared
     * libraries stay; worlds and mods of the instance are never touched.
     */
    public void uninstall(String loader, String mc, String version) throws IOException {
        ModLoader l = loaders.byId(loader).orElseThrow(() -> new IOException(Lang.t("loader.error.unknown", loader)));
        Path dir = FileUtil.resolveInside(versionsDir, l.profileId(mc, version));
        if (!Files.isRegularFile(dir.resolve(MARKER))) {
            throw new IOException(Lang.t("loader.error.not_installed", l.displayName() + " " + version));
        }
        ForgeInstaller.deleteTree(dir);
        Log.info("Removed loader " + loader + " " + version + " for " + mc);
    }
}
