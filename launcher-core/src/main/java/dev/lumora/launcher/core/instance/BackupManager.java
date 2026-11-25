package dev.lumora.launcher.core.instance;

import dev.lumora.launcher.core.InstanceLayout;
import dev.lumora.launcher.core.LauncherPaths;
import dev.lumora.launcher.core.i18n.Lang;
import dev.lumora.launcher.core.pack.SafeZip;
import dev.lumora.launcher.core.util.FileUtil;
import dev.lumora.launcher.core.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Whole-instance backups as zip files below {@code <data>/backups/<instance>/}: everything that makes the instance
 * (mods, configs, resource and shader packs, worlds, options, Lumora settings, instance.json), without logs, crash
 * reports, locks and caches. Restoring first backs up the current state, unpacks into a side folder (safe paths only)
 * and then swaps the folders, so a failure leaves the instance as it was. Instances in the shared standard folder are
 * not backed up here (that folder belongs to Minecraft and every version).
 */
public final class BackupManager {
    public static final int KEEP = 10;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSS").withZone(ZoneOffset.UTC);
    /** Never part of a backup. */
    static final Pattern SKIP = Pattern.compile(
            "(.*/)?(logs|crash-reports|\\.natives)(/.*)?|.*\\.lumora-running[^/]*\\.lock|(.*/)?\\.lumora/(staging|backups|client|packs|addon-assets)(/.*)?"
                    + "|(.*/)?\\.lumora/status[^/]*\\.json|.*\\.lumora-part");

    public record Backup(Path file, Instant created, long size) {
    }

    private final LauncherPaths paths;

    public BackupManager(LauncherPaths paths) {
        this.paths = paths;
    }

    /** The folder that is backed up: the own instance folder, or the own folder of a built-in instance. */
    static Path root(Instance i, InstanceLayout layout) throws IOException {
        if (!i.builtIn) {
            return i.dir;
        }
        if (layout.standard()) {
            throw new IOException(Lang.t("backup.error.standard"));
        }
        return layout.gameDir();
    }

    public Path folder(Instance i) {
        return paths.root().resolve("backups").resolve(i.key().replace(':', '_'));
    }

    public List<Backup> list(Instance i) {
        List<Backup> out = new ArrayList<>();
        Path dir = folder(i);
        if (!Files.isDirectory(dir)) {
            return out;
        }
        try (var s = Files.list(dir)) {
            for (Path f : s.filter(p -> p.getFileName().toString().endsWith(".zip")).toList()) {
                out.add(new Backup(f, Files.getLastModifiedTime(f).toInstant(), Files.size(f)));
            }
        } catch (IOException e) {
            Log.warn("Could not list backups: " + e.getMessage());
        }
        out.sort(Comparator.comparing(Backup::created).reversed());
        return out;
    }

    /**
     * Writes a new backup and keeps the newest {@link #KEEP}. {@code progress} gets (files done, files total).
     */
    public Backup create(Instance i, InstanceLayout layout, BiConsumer<Integer, Integer> progress) throws IOException {
        Path root = root(i, layout).toAbsolutePath().normalize();
        List<Path> files = new ArrayList<>();
        try (var walk = Files.walk(root)) {
            for (Path p : walk.toList()) {
                if (Files.isRegularFile(p, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                        && !SKIP.matcher(root.relativize(p).toString().replace(java.io.File.separatorChar, '/')).matches()) {
                    files.add(p);
                }
            }
        }
        Path dir = folder(i);
        Files.createDirectories(dir);
        // a unique name: a backup taken right before a restore must never replace the one being restored
        String stamp = STAMP.format(Instant.now());
        Path target = dir.resolve(stamp + ".zip");
        for (int n = 2; Files.exists(target); n++) {
            target = dir.resolve(stamp + "-" + n + ".zip");
        }
        Path tmp = target.resolveSibling(target.getFileName() + ".part");
        try (OutputStream os = Files.newOutputStream(tmp); ZipOutputStream zip = new ZipOutputStream(os)) {
            int n = 0;
            for (Path f : files) {
                String rel = root.relativize(f).toString().replace(java.io.File.separatorChar, '/');
                zip.putNextEntry(new ZipEntry(rel));
                try {
                    Files.copy(f, zip);
                } catch (IOException e) {
                    // a file locked by the running game: left out, noted in the log
                    Log.warn("Backup: skipped " + rel + " (" + e.getMessage() + ")");
                }
                zip.closeEntry();
                if (progress != null) {
                    progress.accept(++n, files.size());
                }
            }
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(tmp);
            throw e;
        }
        FileUtil.moveReplacing(tmp, target);
        prune(i);
        Log.info("Backup of " + i.key() + ": " + target.getFileName() + " (" + files.size() + " files)");
        return new Backup(target, Instant.now(), Files.size(target));
    }

    private void prune(Instance i) {
        List<Backup> all = list(i);
        for (int k = KEEP; k < all.size(); k++) {
            try {
                Files.deleteIfExists(all.get(k).file());
            } catch (IOException e) {
                Log.warn("Could not prune backup: " + e.getMessage());
            }
        }
    }

    /**
     * Replaces the instance with the backup. The current state is backed up first (so a restore can be undone); the
     * caller makes sure the instance is not running.
     */
    public void restore(Instance i, InstanceLayout layout, Backup backup) throws IOException {
        Path root = root(i, layout).toAbsolutePath().normalize();
        if (!backup.file().toAbsolutePath().normalize().startsWith(folder(i).toAbsolutePath().normalize())) {
            throw new IOException("backup outside the backup folder refused");
        }
        create(i, layout, null);
        Path side = root.resolveSibling(root.getFileName() + ".restoring");
        Path old = root.resolveSibling(root.getFileName() + ".before-restore");
        FileUtil.deleteTree(side);
        FileUtil.deleteTree(old);
        try (ZipFile zip = new ZipFile(backup.file().toFile())) {
            SafeZip.extract(zip, "", side, rel -> true);
        } catch (IOException | RuntimeException e) {
            FileUtil.deleteTree(side);
            throw e;
        }
        Files.move(root, old);
        try {
            Files.move(side, root);
        } catch (IOException e) {
            Files.move(old, root);
            FileUtil.deleteTree(side);
            throw e;
        }
        FileUtil.deleteTree(old);
        Log.info("Restored " + i.key() + " from " + backup.file().getFileName());
    }

    public void delete(Instance i, Backup b) throws IOException {
        if (!b.file().toAbsolutePath().normalize().startsWith(folder(i).toAbsolutePath().normalize())) {
            throw new IOException("backup outside the backup folder refused");
        }
        Files.deleteIfExists(b.file());
    }
}
