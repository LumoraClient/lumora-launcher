package dev.lumora.launcher.core.storage;

import dev.lumora.launcher.core.LauncherPaths;
import dev.lumora.launcher.core.util.Log;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Clean-up at launcher start (Einstellungen › Speicher): the download cache stays below its limit, and old game logs
 * of the own instances are deleted. The standard game folder (.minecraft) is the player's and is not touched.
 */
public final class Housekeeping {
    /** Minecraft's archived logs ("2026-11-14-1.log.gz"); latest.log and debug.log stay. */
    static final Pattern ARCHIVED_LOG = Pattern.compile("\\d{4}-\\d{2}-\\d{2}-\\d+\\.log(\\.gz)?");

    private Housekeeping() {
    }

    /**
     * Deletes the oldest files of the download cache until it is at most {@code maxBytes} (the cache only holds copies
     * of verified downloads; every instance keeps its own files). Returns the bytes freed; {@code maxBytes <= 0} = no limit.
     */
    public static long pruneCache(Path cache, long maxBytes) {
        if (maxBytes <= 0 || !Files.isDirectory(cache)) {
            return 0;
        }
        List<Entry> files = new ArrayList<>();
        long total = 0;
        try (Stream<Path> s = Files.walk(cache)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                BasicFileAttributes a = Files.readAttributes(p, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (a.isRegularFile()) {
                    files.add(new Entry(p, a.size(), a.lastModifiedTime().toInstant()));
                    total += a.size();
                }
            }
        } catch (IOException | RuntimeException e) {
            Log.warn("Could not measure the download cache: " + e.getMessage());
            return 0;
        }
        files.sort(Comparator.comparing(Entry::modified));
        long freed = 0;
        for (Entry f : files) {
            if (total - freed <= maxBytes) {
                break;
            }
            try {
                Files.deleteIfExists(f.path());
                freed += f.size();
            } catch (IOException e) {
                Log.warn("Could not delete " + f.path() + ": " + e.getMessage());
            }
        }
        if (freed > 0) {
            Log.info("Download cache over " + StorageUsage.format(maxBytes) + ": freed " + StorageUsage.format(freed));
        }
        return freed;
    }

    /** Deletes archived game logs older than {@code days} in {@code instances/<id>/logs}; returns the count. 0 days = keep. */
    public static int deleteOldLogs(LauncherPaths paths, int days, Instant now) {
        if (days <= 0) {
            return 0;
        }
        Instant cutoff = now.minus(Duration.ofDays(days));
        Path instances = paths.root().resolve("instances");
        if (!Files.isDirectory(instances)) {
            return 0;
        }
        int deleted = 0;
        try (Stream<Path> dirs = Files.list(instances)) {
            for (Path inst : (Iterable<Path>) dirs::iterator) {
                Path logs = inst.resolve("logs");
                if (!Files.isDirectory(logs, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                try (Stream<Path> s = Files.list(logs)) {
                    for (Path p : (Iterable<Path>) s::iterator) {
                        if (ARCHIVED_LOG.matcher(p.getFileName().toString()).matches() && Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)
                                && Files.getLastModifiedTime(p).toInstant().isBefore(cutoff)) {
                            Files.deleteIfExists(p);
                            deleted++;
                        }
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            Log.warn("Could not clean up old game logs: " + e.getMessage());
        }
        if (deleted > 0) {
            Log.info("Deleted " + deleted + " game logs older than " + days + " days");
        }
        return deleted;
    }

    private record Entry(Path path, long size, Instant modified) {
    }
}
