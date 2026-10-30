package dev.lumora.launcher.core.storage;

import dev.lumora.launcher.core.LauncherPaths;
import dev.lumora.launcher.core.util.Log;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileStore;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * How much disk space the launcher uses, by category (Java, downloads, libraries, worlds, mods ...). Categories that
 * the launcher can download again may be cleared; player data (worlds, screenshots, own mods) is only shown.
 * Every delete stays inside the data directory and never follows links.
 */
public final class StorageUsage {
    /** Launcher logs and compressed game logs older than this count as unused. */
    static final Duration OLD_LOGS = Duration.ofDays(14);

    public enum Kind {
        RUNTIMES("Java-Laufzeiten", "Werden bei Bedarf für die Versionen heruntergeladen, die sie brauchen", true),
        CACHE("Download-Cache", "Zwischengespeicherte Mods, Bilder und Neuigkeiten, die bei Bedarf neu geladen werden", true),
        LIBRARIES("Bibliotheken", "Gemeinsame Bibliotheken aller Versionen und Loader", true),
        ASSETS("Assets", "Texturen, Sounds und Sprachen von Minecraft", true),
        VERSIONS("Spielversionen", "Minecraft- und Fabric-Dateien der installierten Versionen", true),
        LOGS("Logs", "Launcher- und Spielprotokolle", true),
        CRASHES("Absturzberichte", "Crash-Reports aller Instanzen", true),
        WORLDS("Welten", "Einzelspielerwelten aller Instanzen", false),
        MODS("Mods", "In deinen Instanzen installierte Mod-JARs", false),
        PACKS("Ressourcen- und Shaderpakete", "Pakete in deinen Instanzen", false),
        SCREENSHOTS("Screenshots", "Bildschirmfotos aller Instanzen", false),
        CONFIGS("Mod-Konfigurationen", "Einstellungen der Mods in deinen Instanzen", false),
        OTHER("Sonstiges", "Einstellungen, Konten, Diagnose und übrige Spieldaten", false);

        public final String label;
        public final String description;
        /** The launcher can restore it (download again), so "Leeren" is offered. */
        public final boolean clearable;

        Kind(String label, String description, boolean clearable) {
            this.label = label;
            this.description = description;
            this.clearable = clearable;
        }
    }

    public record Category(Kind kind, long bytes, long files, List<Path> dirs) {
    }

    public record Report(List<Category> categories, long total, long diskFree, long diskTotal, long unusedBytes, long unusedFiles) {
        public Category get(Kind k) {
            return categories.stream().filter(c -> c.kind == k).findFirst().orElseThrow();
        }
    }

    private final LauncherPaths paths;

    public StorageUsage(LauncherPaths paths) {
        this.paths = paths;
    }

    /** Walks the data directory once. Can take a few seconds (assets are many small files): call off the UI thread. */
    public Report scan() {
        Path root = paths.root();
        List<Category> out = new ArrayList<>();
        long known = 0;
        for (Kind k : Kind.values()) {
            if (k == Kind.OTHER) {
                continue;
            }
            List<Path> dirs = dirs(k);
            long[] size = new long[2];
            for (Path d : dirs) {
                long[] s = size(d);
                size[0] += s[0];
                size[1] += s[1];
            }
            known += size[0];
            out.add(new Category(k, size[0], size[1], dirs));
        }
        long[] all = size(root);
        out.add(new Category(Kind.OTHER, Math.max(0, all[0] - known), Math.max(0, all[1] - out.stream().mapToLong(Category::files).sum()),
                List.of(root)));
        long free = -1;
        long total = -1;
        try {
            FileStore store = Files.getFileStore(Files.isDirectory(root) ? root : root.getParent());
            free = store.getUsableSpace();
            total = store.getTotalSpace();
        } catch (IOException | RuntimeException e) {
            Log.warn("Disk space unknown: " + e.getMessage());
        }
        long[] unused = new long[2];
        for (Path p : unusedFiles()) {
            try {
                unused[0] += Files.size(p);
                unused[1]++;
            } catch (IOException ignored) {
                // vanished meanwhile
            }
        }
        return new Report(List.copyOf(out), all[0], free, total, unused[0], unused[1]);
    }

    /** The folders of a category (existing or not). */
    public List<Path> dirs(Kind k) {
        Path root = paths.root();
        return switch (k) {
            case RUNTIMES -> List.of(paths.runtimes());
            case CACHE -> List.of(root.resolve("cache"));
            case LIBRARIES -> List.of(paths.libraries());
            case ASSETS -> List.of(paths.assets());
            case VERSIONS -> List.of(paths.versions());
            case LOGS -> concat(List.of(paths.logs()), instanceDirs("logs"));
            case CRASHES -> instanceDirs("crash-reports");
            case WORLDS -> instanceDirs("saves");
            case MODS -> instanceDirs("mods");
            case PACKS -> concat(instanceDirs("resourcepacks"), instanceDirs("shaderpacks"));
            case SCREENSHOTS -> instanceDirs("screenshots");
            case CONFIGS -> instanceDirs("config");
            case OTHER -> List.of(root);
        };
    }

    /**
     * Deletes the contents of a clearable category (the folders themselves stay). Only for categories the launcher
     * can restore; returns the freed bytes.
     */
    public long clear(Kind k) throws IOException {
        if (!k.clearable) {
            throw new IllegalArgumentException(k.label + " wird nicht vom Launcher verwaltet");
        }
        long freed = 0;
        for (Path d : dirs(k)) {
            freed += deleteContents(d);
        }
        Log.info("Storage: cleared " + k + " (" + freed + " bytes)");
        return freed;
    }

    /**
     * Files nobody needs any more: the download cache (mods are copied into the instances), extracted native
     * libraries (extracted again at the next start) and logs older than {@link #OLD_LOGS}.
     */
    public List<Path> unusedFiles() {
        List<Path> out = new ArrayList<>();
        collect(paths.root().resolve("cache").resolve("mods"), p -> true, out);
        for (Path inst : instances()) {
            collect(inst.resolve(".natives"), p -> true, out);
            collect(inst.resolve("logs"), p -> p.getFileName().toString().endsWith(".log.gz") && old(p), out);
        }
        collect(paths.logs(), p -> old(p) && !p.getFileName().toString().equals("latest.log"), out);
        return out;
    }

    /** Deletes {@link #unusedFiles()}; returns the freed bytes. */
    public long clearUnused() {
        long freed = 0;
        for (Path p : unusedFiles()) {
            try {
                long s = Files.size(p);
                if (Files.deleteIfExists(p)) {
                    freed += s;
                }
            } catch (IOException e) {
                Log.warn("Could not delete " + p + ": " + e.getMessage());
            }
        }
        Log.info("Storage: removed unused files (" + freed + " bytes)");
        return freed;
    }

    private List<Path> instances() {
        Path dir = paths.root().resolve("instances");
        List<Path> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return out;
        }
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            for (Path p : ds) {
                if (Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) {
                    out.add(p);
                }
            }
        } catch (IOException e) {
            Log.warn("Could not list instances: " + e.getMessage());
        }
        return out;
    }

    private List<Path> instanceDirs(String sub) {
        return instances().stream().map(i -> i.resolve(sub)).toList();
    }

    private static List<Path> concat(List<Path> a, List<Path> b) {
        List<Path> out = new ArrayList<>(a);
        out.addAll(b);
        return out;
    }

    private static boolean old(Path p) {
        try {
            return Files.getLastModifiedTime(p).toInstant().isBefore(Instant.now().minus(OLD_LOGS));
        } catch (IOException e) {
            return false;
        }
    }

    private static void collect(Path dir, java.util.function.Predicate<Path> filter, List<Path> out) {
        if (!Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (attrs.isRegularFile() && filter.test(file)) {
                        out.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            Log.warn("Could not scan " + dir + ": " + e.getMessage());
        }
    }

    /** {bytes, files} below a folder, without following links. */
    static long[] size(Path dir) {
        long[] s = new long[2];
        if (!Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) {
            return s;
        }
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (attrs.isRegularFile()) {
                        s[0] += attrs.size();
                        s[1]++;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            Log.warn("Could not measure " + dir + ": " + e.getMessage());
        }
        return s;
    }

    /** Deletes everything below dir (not dir itself), never following links out of it. */
    private long deleteContents(Path dir) throws IOException {
        if (!Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) {
            return 0;
        }
        Path root = paths.root().toAbsolutePath().normalize();
        Path target = dir.toAbsolutePath().normalize();
        if (!target.startsWith(root) || target.equals(root)) {
            throw new IOException("Ordner liegt nicht im Lumora-Verzeichnis: " + dir);
        }
        long[] freed = {0};
        Path realRoot = root.toRealPath();
        Files.walkFileTree(target, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes attrs) throws IOException {
                // a junction or link that leads out of the data directory is never entered
                if (!d.toRealPath().startsWith(realRoot)) {
                    throw new IOException("Verknüpfung aus dem Lumora-Verzeichnis heraus: " + d);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                long s = attrs.isRegularFile() ? attrs.size() : 0;
                Files.delete(file);
                freed[0] += s;
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                if (!d.equals(target)) {
                    Files.delete(d);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return freed[0];
    }

    /** "9.00 GB", "710.1 MB", "993 kB", "0 B" (decimal units like Windows Explorer's "GB" in the launcher mockups). */
    public static String format(long bytes) {
        if (bytes < 1000) {
            return bytes + " B";
        }
        if (bytes < 1_000_000) {
            return Math.round(bytes / 1000.0) + " kB";
        }
        if (bytes < 1_000_000_000) {
            return String.format(java.util.Locale.ROOT, "%.1f MB", bytes / 1e6);
        }
        return String.format(java.util.Locale.ROOT, "%.2f GB", bytes / 1e9);
    }
}
