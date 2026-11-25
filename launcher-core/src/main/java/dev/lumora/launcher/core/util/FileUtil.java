package dev.lumora.launcher.core.util;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class FileUtil {
    private FileUtil() {
    }

    /** Writes to a sibling temp file and moves it into place so readers never see a half-written file. */
    public static void writeAtomically(Path target, byte[] data) throws IOException {
        Path parent = target.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Path tmp = Files.createTempFile(parent, target.getFileName().toString(), ".tmp");
        try {
            Files.write(tmp, data);
            moveReplacing(tmp, target);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    public static void moveReplacing(Path source, Path target) throws IOException {
        Files.createDirectories(target.toAbsolutePath().getParent());
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Deletes a folder tree; links are removed themselves, never followed. */
    public static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (var walk = Files.walk(dir)) {
            for (Path p : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        }
    }

    /**
     * Copies a folder tree into {@code target} (created; must not exist yet). Links are skipped, so a copy never
     * reaches outside the source. {@code skip} decides per relative path (forward slashes) what stays behind.
     */
    public static void copyTree(Path source, Path target, java.util.function.Predicate<String> skip) throws IOException {
        Path src = source.toAbsolutePath().normalize();
        Files.createDirectories(target);
        try (var walk = Files.walk(src)) {
            for (Path p : walk.toList()) {
                if (Files.isSymbolicLink(p)) {
                    continue;
                }
                String rel = src.relativize(p).toString().replace(java.io.File.separatorChar, '/');
                if (rel.isEmpty()) {
                    continue;
                }
                if (skip != null && skip.test(rel)) {
                    continue;
                }
                Path dest = resolveInside(target, rel);
                if (Files.isDirectory(p, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(dest.getParent());
                    Files.copy(p, dest, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
    }

    /** Total size of the regular files below {@code dir} (links not followed); 0 if it does not exist. */
    public static long treeSize(Path dir) {
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        try (var walk = Files.walk(dir)) {
            return walk.filter(p -> Files.isRegularFile(p, java.nio.file.LinkOption.NOFOLLOW_LINKS)).mapToLong(p -> {
                try {
                    return Files.size(p);
                } catch (IOException e) {
                    return 0;
                }
            }).sum();
        } catch (IOException e) {
            return 0;
        }
    }

    /** Rejects relative paths that would escape {@code base} (e.g. "../../x" from a remote manifest). */
    public static Path resolveInside(Path base, String relative) throws IOException {
        Path normalizedBase = base.toAbsolutePath().normalize();
        Path resolved = normalizedBase.resolve(relative).normalize();
        if (!resolved.startsWith(normalizedBase)) {
            throw new IOException("Refusing path outside of " + normalizedBase + ": " + relative);
        }
        return resolved;
    }
}
