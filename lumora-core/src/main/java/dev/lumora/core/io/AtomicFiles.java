package dev.lumora.core.io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Crash-safe text files: write to a temporary file, then move it over the old one. */
public final class AtomicFiles {
    private AtomicFiles() {
    }

    public static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, content, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Moves a broken file aside ({@code name.corrupt-<time>}) and returns the backup, or {@code null}. */
    public static Path backup(Path file, String reason) {
        Path backup = file.resolveSibling(file.getFileName() + "." + reason + "-" + System.currentTimeMillis());
        try {
            Files.move(file, backup, StandardCopyOption.REPLACE_EXISTING);
            return backup;
        } catch (IOException e) {
            return null;
        }
    }
}
