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
