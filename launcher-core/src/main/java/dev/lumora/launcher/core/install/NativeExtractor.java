package dev.lumora.launcher.core.install;

import dev.lumora.launcher.core.mojang.ResolvedVersion;
import dev.lumora.launcher.core.util.FileUtil;
import dev.lumora.launcher.core.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Extracts native jars (LWJGL, jinput, ... up to Minecraft 1.18.2) into the profile's natives directory, the way
 * the official launcher does: the directory is emptied first (files a running game of the profile holds stay if they
 * are identical, for "multiple instances"), {@code extract.exclude} prefixes (META-INF/) are
 * skipped, and every entry path is checked so a jar cannot write outside the directory.
 */
public final class NativeExtractor {
    private NativeExtractor() {
    }

    public static void extract(List<ResolvedVersion.NativeLibrary> natives, Path librariesDir, Path target) throws IOException {
        if (natives.isEmpty()) {
            return;
        }
        boolean inUse = false;
        try {
            clear(target);
        } catch (java.nio.file.FileSystemException e) {
            // a running game of this profile ("multiple instances") has these libraries loaded: identical files stay
            inUse = true;
            Log.info("Natives in use by a running game, keeping identical files: " + e.getFile());
        }
        int files = 0;
        int kept = 0;
        for (ResolvedVersion.NativeLibrary n : natives) {
            Path jar = FileUtil.resolveInside(librariesDir, n.path());
            try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(jar))) {
                ZipEntry e;
                while ((e = zip.getNextEntry()) != null) {
                    if (e.isDirectory() || excluded(e.getName(), n.excludes())) {
                        continue;
                    }
                    Path out = FileUtil.resolveInside(target, e.getName());
                    Files.createDirectories(out.getParent());
                    if (inUse && Files.isRegularFile(out)) {
                        byte[] data = zip.readAllBytes();
                        if (java.util.Arrays.equals(data, Files.readAllBytes(out))) {
                            kept++;
                            continue;
                        }
                        copy(new java.io.ByteArrayInputStream(data), out);
                    } else {
                        copy(zip, out);
                    }
                    files++;
                }
            }
        }
        Log.info("Extracted " + files + " native files from " + natives.size() + " jars into " + target
                + (kept > 0 ? " (" + kept + " in use kept)" : ""));
    }

    static boolean excluded(String name, List<String> excludes) {
        for (String ex : excludes) {
            if (name.startsWith(ex)) {
                return true;
            }
        }
        return false;
    }

    private static void copy(InputStream in, Path out) throws IOException {
        Path tmp = out.resolveSibling(out.getFileName() + ".tmp");
        Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
        Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING);
    }

    private static void clear(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            Files.createDirectories(dir);
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path p : walk.sorted((a, b) -> b.getNameCount() - a.getNameCount()).toList()) {
                if (!p.equals(dir)) {
                    Files.deleteIfExists(p);
                }
            }
        }
    }
}
