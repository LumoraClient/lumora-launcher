package dev.lumora.launcher.core.content;

import dev.lumora.launcher.core.download.Downloader;
import dev.lumora.launcher.core.i18n.Lang;
import dev.lumora.launcher.core.instance.InstanceKind;
import dev.lumora.launcher.core.platform.ModPlatformProvider;
import dev.lumora.launcher.core.platform.PlatformModel.ProjectFile;
import dev.lumora.launcher.core.platform.PlatformModel.ProjectSummary;
import dev.lumora.launcher.core.platform.PlatformModel.ProjectVersion;
import dev.lumora.launcher.core.platform.ProjectType;
import dev.lumora.launcher.core.util.FileUtil;
import dev.lumora.launcher.core.util.Hashing;
import dev.lumora.launcher.core.util.Log;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Installs platform content into one game folder, in two steps the interface shows in between:
 * <ol>
 *   <li>{@link #plan}: compatibility check, dependency tree, what gets installed or replaced, size. Nothing is written.</li>
 *   <li>{@link #execute}: download into a staging folder with the platform's SHA-512, back up files that get replaced,
 *       move the new files into place, verify them again and record their origin. Any failure (or a cancel) rolls back:
 *       new files are removed, backups go back, the staging folder is deleted.</li>
 * </ol>
 * Installing a mod that is already there replaces it (an update or reinstall), never adds a second copy.
 */
public final class ContentInstaller {
    private static final String STAGING = ".lumora/staging";
    public static final String BACKUPS = ".lumora/backups";
    private static final int KEEP_BACKUPS = 10;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC);

    /** The instance to install into. */
    public record Target(InstanceKind kind, String minecraftVersion, Path gameDir) {
    }

    /** One file to put into the game folder. */
    public record Step(String projectId, String title, String iconUrl, ProjectVersion version, ProjectFile file, ProjectType type,
                       String key, String replacesKey, List<String> requiredBy) {
    }

    /**
     * @param tree     dependency tree for the dialog
     * @param issues   compatibility findings; any BLOCK means {@link #execute} refuses
     * @param steps    files in install order (the chosen project first)
     */
    public record Plan(Target target, DependencyResolver.Node tree, List<CompatibilityChecker.Issue> issues, List<Step> steps,
                       long downloadBytes) {
        public boolean blocked() {
            return CompatibilityChecker.blocked(issues);
        }
    }

    public record Result(List<String> installed, List<String> replaced, Path backup) {
    }

    private final Downloader downloader;

    public ContentInstaller(Downloader downloader) {
        this.downloader = downloader;
    }

    // ---------------------------------------------------------------- plan

    /**
     * What is installed in the game folder: manifest entries and, for files the launcher did not install, the
     * platform's answer to their SHA-1 (so a Fabric API dropped in by hand counts as installed).
     */
    public static DependencyResolver.Installed installed(ModPlatformProvider provider, Path gameDir, ContentManifest manifest) {
        Map<String, String> projectToKey = new LinkedHashMap<>();
        Map<String, ProjectVersion> versions = new LinkedHashMap<>();
        Set<String> modIds = new HashSet<>();
        Map<Path, String> unknown = new LinkedHashMap<>();
        for (ProjectType type : List.of(ProjectType.MOD, ProjectType.RESOURCE_PACK, ProjectType.SHADER)) {
            for (InstalledContent.Item item : InstalledContent.scan(gameDir, type, manifest)) {
                if (item.mod() != null) {
                    modIds.addAll(item.mod().ids());
                }
                ContentManifest.Entry o = item.origin();
                if (o != null && o.projectId != null && provider.platform().id().equals(o.platform)) {
                    projectToKey.put(o.projectId, item.key());
                } else {
                    unknown.put(item.file(), item.key());
                }
            }
        }
        try {
            // files the launcher did not install: the platform's own way to recognise them (hash, fingerprint)
            Map<Path, ProjectVersion> known = provider.identify(new ArrayList<>(unknown.keySet()));
            for (Map.Entry<Path, ProjectVersion> e : known.entrySet()) {
                projectToKey.putIfAbsent(e.getValue().projectId(), unknown.get(e.getKey()));
                versions.put(e.getValue().projectId(), e.getValue());
            }
        } catch (IOException | RuntimeException e) {
            Log.warn("Installed files could not be identified: " + e.getMessage());
        }
        return new DependencyResolver.Installed(projectToKey, versions, modIds);
    }

    /**
     * Plans installing {@code version} of {@code project} as {@code type}, with the optional dependencies the player
     * ticked. Network: dependency versions and titles.
     */
    public Plan plan(ModPlatformProvider provider, Target target, ProjectSummary project, ProjectType type, ProjectVersion version,
                     Set<String> chosenOptional) throws IOException {
        ContentManifest manifest = ContentManifest.load(target.gameDir());
        DependencyResolver.Installed installed = installed(provider, target.gameDir(), manifest);
        DependencyResolver resolver = new DependencyResolver(provider, target.kind(), target.minecraftVersion());
        DependencyResolver.Result r = resolver.resolve(project, type, version, installed, chosenOptional == null ? Set.of() : chosenOptional);
        List<CompatibilityChecker.Issue> issues = new ArrayList<>(r.issues());
        List<Step> steps = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        long bytes = 0;
        Map<String, List<String>> requiredBy = new HashMap<>();
        collectParents(r.root(), requiredBy);
        for (DependencyResolver.Node n : r.install()) {
            if (n.version() == null) {
                continue;
            }
            ProjectType stepType = n == r.root() ? type : ProjectType.MOD;
            ProjectFile file = n.version().primaryFile();
            if (file == null) {
                continue;
            }
            if (file.url() == null) {
                // the author does not allow downloads through other apps (CurseForge): never fetched another way
                issues.add(new CompatibilityChecker.Issue(CompatibilityChecker.Severity.BLOCK, "no_download",
                        Lang.t("content.error.no_download", n.title()), Lang.t("content.error.no_download.hint")));
                continue;
            }
            String name = safeFileName(file.fileName(), stepType);
            if (name == null) {
                issues.add(new CompatibilityChecker.Issue(CompatibilityChecker.Severity.BLOCK, "file_type",
                        Lang.t("content.error.file_type", file.fileName()), ""));
                continue;
            }
            String key = stepType.folder() + "/" + name;
            if (!keys.add(key)) {
                continue;
            }
            String replaces = installed.projectToKey().get(n.projectId());
            Path existing = target.gameDir().resolve(key);
            if (replaces == null && (Files.exists(existing) || Files.exists(target.gameDir().resolve(key + InstalledContent.DISABLED)))) {
                // a file with that name from somewhere else: it is backed up and replaced
                issues.add(new CompatibilityChecker.Issue(CompatibilityChecker.Severity.WARN, "replace_file",
                        Lang.t("content.replace_file", name), Lang.t("content.replace_file.hint")));
                replaces = key;
            }
            if (replaces != null && replaces.equals(key) && Files.isRegularFile(existing)
                    && Hashing.matches(existing, expected(file))) {
                continue; // exactly this file is already there
            }
            steps.add(new Step(n.projectId(), n.title(), n.iconUrl(), n.version(), file, stepType, key, replaces,
                    requiredBy.getOrDefault(n.projectId(), List.of())));
            bytes += Math.max(0, file.size());
        }
        if (steps.isEmpty() && !CompatibilityChecker.blocked(issues)) {
            issues.add(new CompatibilityChecker.Issue(CompatibilityChecker.Severity.WARN, "up_to_date",
                    Lang.t("content.already_installed", r.root().title()), ""));
        }
        return new Plan(target, r.root(), issues, steps, bytes);
    }

    private static void collectParents(DependencyResolver.Node node, Map<String, List<String>> into) {
        for (DependencyResolver.Node c : node.children()) {
            into.computeIfAbsent(c.projectId(), k -> new ArrayList<>()).add(node.projectId());
            collectParents(c, into);
        }
    }

    /** The file name as it will be stored: only safe characters, and the extension the type expects. Null if wrong type. */
    static String safeFileName(String name, ProjectType type) {
        if (name == null) {
            return null;
        }
        String safe = name.replaceAll("[^A-Za-z0-9_.+\\-\\[\\]() ]", "_").strip();
        String lower = safe.toLowerCase(Locale.ROOT);
        boolean ok = type == ProjectType.MOD ? lower.endsWith(".jar") : lower.endsWith(".zip");
        if (!ok || safe.startsWith(".") || safe.length() > 200) {
            return null;
        }
        return safe;
    }

    /** The strongest hash the platform published for a file (Modrinth SHA-512, CurseForge SHA-1). */
    public static Hashing.Expected expected(ProjectFile f) {
        return f.sha512() != null ? Hashing.Expected.sha512(f.sha512()) : Hashing.Expected.sha1(f.sha1());
    }

    // ---------------------------------------------------------------- execute

    /**
     * Carries out a plan. {@code progress} gets download progress; interrupting the thread cancels (with rollback).
     *
     * @throws IOException with a readable message; the game folder is then as before
     */
    public Result execute(Plan plan, String platformId, Downloader.Progress progress) throws IOException {
        if (plan.blocked()) {
            throw new IOException(Lang.t("content.error.blocked"));
        }
        Path game = plan.target().gameDir();
        String stamp = STAMP.format(Instant.now());
        Path staging = FileUtil.resolveInside(game, STAGING + "/" + stamp);
        Path backup = FileUtil.resolveInside(game, BACKUPS + "/" + stamp);
        List<Downloader.Task> tasks = new ArrayList<>();
        for (Step s : plan.steps()) {
            tasks.add(new Downloader.Task(URI.create(s.file().url()), FileUtil.resolveInside(staging, s.key()),
                    expected(s.file()), s.file().size()));
        }
        List<Path> placed = new ArrayList<>();
        Map<Path, Path> backedUp = new LinkedHashMap<>();
        List<String> installedKeys = new ArrayList<>();
        List<String> replacedKeys = new ArrayList<>();
        try {
            downloader.downloadAll(tasks, progress, Downloader.Verify.FULL);
            if (Thread.currentThread().isInterrupted()) {
                throw new java.io.InterruptedIOException(Lang.t("content.cancelled"));
            }
            for (Step s : plan.steps()) {
                if (s.replacesKey() != null) {
                    for (String variant : List.of(s.replacesKey(), s.replacesKey() + InstalledContent.DISABLED)) {
                        Path old = FileUtil.resolveInside(game, variant);
                        if (Files.isRegularFile(old)) {
                            Path to = FileUtil.resolveInside(backup, variant);
                            Files.createDirectories(to.getParent());
                            Files.move(old, to);
                            backedUp.put(to, old);
                        }
                    }
                    replacedKeys.add(s.replacesKey());
                }
                Path dest = FileUtil.resolveInside(game, s.key());
                Files.createDirectories(dest.getParent());
                FileUtil.moveReplacing(FileUtil.resolveInside(staging, s.key()), dest);
                placed.add(dest);
                installedKeys.add(s.key());
            }
            // validation: what lies in the folder now is exactly what the platform published
            for (Step s : plan.steps()) {
                if (!Hashing.matches(FileUtil.resolveInside(game, s.key()), expected(s.file()))) {
                    throw new IOException(Lang.t("content.error.verify", s.file().fileName()));
                }
            }
            ContentManifest manifest = ContentManifest.load(game);
            for (Step s : plan.steps()) {
                if (s.replacesKey() != null) {
                    manifest.remove(s.replacesKey());
                }
                ContentManifest.Entry e = new ContentManifest.Entry();
                e.platform = platformId;
                e.projectId = s.version().projectId();
                e.versionId = s.version().id();
                e.versionNumber = s.version().number();
                e.title = s.title();
                e.iconUrl = s.iconUrl();
                e.sha1 = s.file().sha1();
                e.sha512 = s.file().sha512();
                e.url = s.file().url();
                e.requiredBy = new ArrayList<>(s.requiredBy());
                e.installed = Instant.now().toString();
                manifest.put(s.key(), e);
            }
            manifest.prune(game);
            manifest.save();
            Log.info("Installed " + installedKeys + " into " + game + (replacedKeys.isEmpty() ? "" : ", replaced " + replacedKeys));
        } catch (IOException | RuntimeException e) {
            rollback(placed, backedUp);
            Log.warn("Installation rolled back: " + e.getMessage());
            throw e instanceof IOException io ? io : new IOException(e.getMessage(), e);
        } finally {
            FileUtil.deleteTree(staging);
            deleteIfEmpty(game.resolve(STAGING));
        }
        pruneBackups(game.resolve(BACKUPS));
        return new Result(installedKeys, replacedKeys, Files.isDirectory(backup) ? backup : null);
    }

    private static void rollback(List<Path> placed, Map<Path, Path> backedUp) {
        for (Path p : placed) {
            try {
                Files.deleteIfExists(p);
            } catch (IOException e) {
                Log.warn("Rollback could not remove " + p + ": " + e.getMessage());
            }
        }
        for (Map.Entry<Path, Path> e : backedUp.entrySet()) {
            try {
                FileUtil.moveReplacing(e.getKey(), e.getValue());
            } catch (IOException ex) {
                Log.warn("Rollback could not restore " + e.getValue() + ": " + ex.getMessage());
            }
        }
    }

    /** Keeps the newest {@link #KEEP_BACKUPS} backup folders. */
    static void pruneBackups(Path backups) {
        if (!Files.isDirectory(backups)) {
            return;
        }
        try (var dirs = Files.list(backups)) {
            List<Path> all = dirs.filter(Files::isDirectory).sorted(Comparator.comparing(Path::getFileName).reversed()).toList();
            for (int i = KEEP_BACKUPS; i < all.size(); i++) {
                FileUtil.deleteTree(all.get(i));
            }
        } catch (IOException e) {
            Log.warn("Could not prune backups: " + e.getMessage());
        }
    }

    private static void deleteIfEmpty(Path dir) {
        try (var s = Files.list(dir)) {
            if (s.findAny().isEmpty()) {
                Files.deleteIfExists(dir);
            }
        } catch (IOException e) {
            // not there or not empty
        }
    }
}
