package dev.lumora.launcher.core.library;

import dev.lumora.launcher.core.util.FileUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Copies a world folder into a zip under instances/&lt;id&gt;/backups/ (Minecraft's session.lock is skipped).
 * The zip is written to a temporary file first, so a failed backup never leaves a broken archive behind.
 */
public final class WorldBackup {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss", Locale.ROOT)
            .withZone(ZoneId.systemDefault());

    private WorldBackup() {
    }

    public static Path backupsDir(Path instance) {
        return instance.resolve("backups");
    }

    public static Path backup(Path worldDir, Path backupsDir, Instant now) throws IOException {
        if (!Files.isRegularFile(worldDir.resolve("level.dat"))) {
            throw new IOException(worldDir.getFileName() + " ist keine Minecraft-Welt");
        }
        Files.createDirectories(backupsDir);
        String folder = worldDir.getFileName().toString();
        Path target = FileUtil.resolveInside(backupsDir, folder.replaceAll("[^\\w.\\- ]", "_") + "_" + TIME.format(now) + ".zip");
        Path tmp = target.resolveSibling(target.getFileName() + ".part");
        try (OutputStream out = Files.newOutputStream(tmp); ZipOutputStream zip = new ZipOutputStream(out)) {
            Files.walkFileTree(worldDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (file.getFileName().toString().equals("session.lock") || !attrs.isRegularFile()) {
                        return FileVisitResult.CONTINUE;
                    }
                    String rel = worldDir.relativize(file).toString().replace('\\', '/');
                    zip.putNextEntry(new ZipEntry(folder + "/" + rel));
                    Files.copy(file, zip);
                    zip.closeEntry();
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(tmp);
            throw e;
        }
        return Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
    }
}
