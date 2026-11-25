package dev.lumora.launcher.core.content;

import dev.lumora.launcher.core.instance.InstanceKind;
import dev.lumora.launcher.core.platform.ModPlatformProvider;
import dev.lumora.launcher.core.platform.ModRepository;
import dev.lumora.launcher.core.platform.Platform;
import dev.lumora.launcher.core.platform.PlatformModel.ProjectFile;
import dev.lumora.launcher.core.platform.PlatformModel.ProjectVersion;
import dev.lumora.launcher.core.platform.ProjectType;
import dev.lumora.launcher.core.util.Log;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Finds newer versions of what is installed in one game folder, for the instance's loader and Minecraft version:
 * Modrinth files by their SHA-1 (one request for all), CurseForge files by their recorded origin or fingerprint.
 * Pinned files ({@link ContentManifest.Entry#pinned}) and switched-off files are left out. Applying an update goes
 * through {@link ContentInstaller}, which backs up the replaced file and rolls back on failure.
 */
public final class ContentUpdates {
    /** One available update; {@code current} is the installed version text (may be null). */
    public record Update(InstalledContent.Item item, Platform platform, String current, ProjectVersion latest) {
        public String title() {
            return item.displayName();
        }
    }

    private ContentUpdates() {
    }

    /** Loader names the platforms use for a content type in this instance. */
    static List<String> loaders(ProjectType type, InstanceKind kind, String mc) {
        return switch (type) {
            case MOD -> kind.modLoaders(mc);
            case SHADER -> List.of("iris", "optifine", "vanilla");
            case RESOURCE_PACK -> List.of("minecraft");
            default -> List.of();
        };
    }

    public static List<Update> check(ModRepository repo, InstanceKind kind, String mc, Path gameDir) {
        List<Update> out = new ArrayList<>();
        ContentManifest manifest = ContentManifest.load(gameDir);
        for (ProjectType type : List.of(ProjectType.MOD, ProjectType.RESOURCE_PACK, ProjectType.SHADER)) {
            if (!CompatibilityChecker.supports(kind, type)) {
                continue;
            }
            List<InstalledContent.Item> items = InstalledContent.scan(gameDir, type, manifest).stream()
                    .filter(InstalledContent.Item::enabled)
                    .filter(i -> i.origin() == null || !i.origin().pinned)
                    .toList();
            List<String> loaders = loaders(type, kind, mc);
            out.addAll(modrinth(repo, items, loaders, mc));
            out.addAll(curseForge(repo, items, loaders, mc));
        }
        return out;
    }

    private static List<Update> modrinth(ModRepository repo, List<InstalledContent.Item> items, List<String> loaders, String mc) {
        List<Update> out = new ArrayList<>();
        ModPlatformProvider mr = repo.provider(Platform.MODRINTH).filter(ModPlatformProvider::available).orElse(null);
        if (mr == null) {
            return out;
        }
        Map<String, InstalledContent.Item> byHash = new LinkedHashMap<>();
        for (InstalledContent.Item i : items) {
            if (i.origin() != null && i.origin().platform != null && !"modrinth".equals(i.origin().platform)) {
                continue;
            }
            try {
                String sha1 = i.origin() != null && i.origin().sha1 != null ? i.origin().sha1 : InstalledContent.sha1(i.file());
                byHash.put(sha1.toLowerCase(Locale.ROOT), i);
            } catch (IOException e) {
                Log.warn("Could not hash " + i.fileName() + ": " + e.getMessage());
            }
        }
        if (byHash.isEmpty()) {
            return out;
        }
        try {
            Map<String, ProjectVersion> latest = mr.latestByHash(new ArrayList<>(byHash.keySet()), loaders, List.of(mc));
            for (Map.Entry<String, ProjectVersion> e : latest.entrySet()) {
                InstalledContent.Item item = byHash.get(e.getKey());
                ProjectFile f = e.getValue().primaryFile();
                if (item == null || f == null || e.getKey().equalsIgnoreCase(f.sha1())) {
                    continue;
                }
                if (e.getValue().files().stream().anyMatch(x -> e.getKey().equalsIgnoreCase(x.sha1()))) {
                    continue; // the installed file is part of the newest version
                }
                out.add(new Update(item, Platform.MODRINTH, item.version(), e.getValue()));
            }
        } catch (IOException | RuntimeException e) {
            Log.warn("Modrinth update check failed: " + e.getMessage());
        }
        return out;
    }

    private static List<Update> curseForge(ModRepository repo, List<InstalledContent.Item> items, List<String> loaders, String mc) {
        List<Update> out = new ArrayList<>();
        ModPlatformProvider cf = repo.provider(Platform.CURSEFORGE).filter(ModPlatformProvider::available).orElse(null);
        if (cf == null) {
            return out;
        }
        for (InstalledContent.Item i : items) {
            ContentManifest.Entry o = i.origin();
            if (o == null || !"curseforge".equals(o.platform) || o.projectId == null) {
                continue;
            }
            try {
                ProjectVersion best = DependencyResolver.best(cf.versions(o.projectId, loaders, List.of(mc)));
                if (best != null && !best.id().equals(o.versionId)) {
                    out.add(new Update(i, Platform.CURSEFORGE, i.version(), best));
                }
            } catch (IOException | RuntimeException e) {
                Log.warn("CurseForge update check for " + i.fileName() + " failed: " + e.getMessage());
            }
        }
        return out;
    }

    /** Pins or unpins a file (no update offers while pinned); files without an entry get one. */
    public static void setPinned(Path gameDir, InstalledContent.Item item, boolean pinned) throws IOException {
        ContentManifest m = ContentManifest.load(gameDir);
        ContentManifest.Entry e = m.get(item.key()).orElseGet(() -> {
            ContentManifest.Entry n = new ContentManifest.Entry();
            n.title = item.displayName();
            n.installed = Instant.now().toString();
            return n;
        });
        if (e.sha1 == null) {
            e.sha1 = InstalledContent.sha1(item.file());
        }
        e.pinned = pinned;
        m.put(item.key(), e);
        m.save();
    }
}
