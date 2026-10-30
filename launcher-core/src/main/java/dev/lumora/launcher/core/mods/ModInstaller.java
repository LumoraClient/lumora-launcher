package dev.lumora.launcher.core.mods;

import dev.lumora.launcher.core.download.Downloader;
import dev.lumora.launcher.core.profile.ProfileCatalog;
import dev.lumora.launcher.core.util.FileUtil;
import dev.lumora.launcher.core.util.Hashing;
import dev.lumora.launcher.core.util.Json;
import dev.lumora.launcher.core.util.Log;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Puts exactly the profile's mods into {@code <instance>/mods}.
 * <ul>
 *   <li>Modrinth artifacts are downloaded into a shared cache and verified (SHA-512) before use.</li>
 *   <li>Bundled artifacts (our own companion mod) come from the launcher's install folder and are
 *       verified against bundled-mods.json written at build time.</li>
 *   <li>Files previously placed by the launcher but no longer wanted are removed. Jars the user dropped
 *       in manually are left alone but reported by the dependency check.</li>
 * </ul>
 */
public final class ModInstaller {
    public static final String MANAGED_LIST = ".lumora-managed.json";

    static final class Managed {
        List<String> files = new ArrayList<>();
    }

    public record Result(List<Path> modFiles, List<Path> unmanagedFiles) {
    }

    private final Downloader downloader;
    private final Path cacheDir;
    private final BundledMods bundled;

    public ModInstaller(Downloader downloader, Path cacheDir, BundledMods bundled) {
        this.downloader = downloader;
        this.cacheDir = cacheDir;
        this.bundled = bundled;
    }

    public Result install(List<ProfileCatalog.Artifact> artifacts, Path modsDir, Downloader.Progress progress) throws IOException {
        Files.createDirectories(modsDir);
        List<Downloader.Task> tasks = new ArrayList<>();
        Map<String, Path> sources = new LinkedHashMap<>();
        for (ProfileCatalog.Artifact a : artifacts) {
            switch (a.source) {
                case MODRINTH -> {
                    Path cached = FileUtil.resolveInside(cacheDir, a.id + "/" + a.fileName);
                    tasks.add(new Downloader.Task(URI.create(a.url), cached, a.expectedHash(), a.size));
                    sources.put(a.fileName, cached);
                }
                case BUNDLED -> sources.put(a.fileName, bundled.verified(a.fileName));
            }
        }
        downloader.downloadAll(tasks, progress);

        Path managedFile = modsDir.resolve(MANAGED_LIST);
        Managed previous = Files.isRegularFile(managedFile) ? safeRead(managedFile) : new Managed();
        Set<String> wanted = new HashSet<>(sources.keySet());
        for (String old : previous.files) {
            if (!wanted.contains(old)) {
                Path p = FileUtil.resolveInside(modsDir, old);
                if (Files.deleteIfExists(p)) {
                    Log.info("Removed mod no longer part of the profile: " + old);
                }
            }
        }
        List<Path> result = new ArrayList<>();
        for (Map.Entry<String, Path> e : sources.entrySet()) {
            Path target = FileUtil.resolveInside(modsDir, e.getKey());
            String expected = Hashing.hash(e.getValue(), Hashing.Algorithm.SHA512);
            if (!Files.isRegularFile(target) || !Hashing.hash(target, Hashing.Algorithm.SHA512).equals(expected)) {
                Path tmp = target.resolveSibling(e.getKey() + ".tmp");
                Files.copy(e.getValue(), tmp, StandardCopyOption.REPLACE_EXISTING);
                FileUtil.moveReplacing(tmp, target);
            }
            result.add(target);
        }
        Managed now = new Managed();
        now.files = new ArrayList<>(sources.keySet());
        Json.write(managedFile, now);

        List<Path> unmanaged = new ArrayList<>();
        try (Stream<Path> s = Files.list(modsDir)) {
            s.filter(p -> p.getFileName().toString().endsWith(".jar"))
                    .filter(p -> !wanted.contains(p.getFileName().toString()))
                    .forEach(unmanaged::add);
        }
        return new Result(List.copyOf(result), List.copyOf(unmanaged));
    }

    private static Managed safeRead(Path file) {
        try {
            Managed m = Json.read(file, Managed.class);
            if (m.files == null) {
                m.files = new ArrayList<>();
            }
            m.files.removeIf(f -> f == null || f.contains("/") || f.contains("\\") || f.contains(".."));
            return m;
        } catch (IOException e) {
            Log.warn("Ignoring unreadable " + file + ": " + e.getMessage());
            return new Managed();
        }
    }
}
