package dev.lumora.core.addon;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Makes the {@code assets/} of the addon jars in the {@code addons/} folder (textures, fonts, sounds, language files)
 * available to Minecraft. Their jars are not resource packs, so the version adapters register the folder prepared here
 * as an always-on built-in resource pack ("Lumora-Addons").
 * <p>
 * The assets of every jar with a {@code lumora-addon.json} are copied into one folder with a {@code pack.mcmeta}. A
 * marker with the names, sizes and dates of the jars avoids copying again when nothing changed. Only an addon's own
 * namespaces are taken: {@code minecraft}, {@code realms} and {@code lumora} cannot be replaced by an addon (a normal
 * resource pack can still do that). Paths are checked, sizes are limited, and when two addons ship the same file the
 * first jar (by file name) wins.
 */
public final class AddonAssets {
    static final String MARKER = ".lumora-addon-assets";
    static final Set<String> RESERVED = Set.of("minecraft", "realms", "lumora");
    static final long MAX_FILE_BYTES = 16L << 20;
    static final long MAX_TOTAL_BYTES = 128L << 20;
    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]{1,64}");
    private static final Pattern SEGMENT = Pattern.compile("[A-Za-z0-9_.\\-]{1,128}");
    private static final Pattern DESCRIPTION = Pattern.compile("\"description\"\\s*:\\s*(\"(?:[^\"\\\\]|\\\\.)*\"|\\{[^{}]*\\})");
    private static final String DEFAULT_META = "{\"pack\":{\"pack_format\":1,\"description\":\"Lumora-Addons\"}}";

    private AddonAssets() {
    }

    /**
     * Prepares the folder; returns it when at least one addon brings assets, otherwise null (nothing to register).
     *
     * @param addonsDir the game's {@code addons/} folder (may be missing)
     * @param target    where the assets go, owned by Lumora (e.g. {@code .lumora/addon-assets}); replaced when the jars changed
     * @param packMeta  the client's own {@code pack.mcmeta} (right pack format for this Minecraft version), or null
     * @param warn      receives problems (an unreadable jar is skipped, the others still count)
     */
    public static Path prepare(Path addonsDir, Path target, byte[] packMeta, Consumer<String> warn) {
        // jars replaced from the addon library go first, otherwise the old copy (first by name) would win the assets
        AddonLibrary.removePending(addonsDir);
        List<Path> jars = jars(addonsDir);
        String stamp = stamp(jars, packMeta);
        Path marker = target.resolve(MARKER);
        try {
            if (Files.isRegularFile(marker) && Files.readString(marker, StandardCharsets.UTF_8).equals(stamp)) {
                return Files.isDirectory(target.resolve("assets")) ? target : null;
            }
            deleteTree(target);
            Files.createDirectories(target);
            long[] total = {0};
            int copied = 0;
            for (Path jar : jars) {
                try {
                    copied += copy(jar, target, total, warn);
                } catch (IOException | RuntimeException e) {
                    warn.accept("Addon-Ressourcen aus " + jar.getFileName() + " übersprungen: " + e.getMessage());
                }
            }
            if (copied > 0) {
                Files.write(target.resolve("pack.mcmeta"), meta(packMeta));
            }
            Files.writeString(marker, stamp, StandardCharsets.UTF_8);
            return copied > 0 ? target : null;
        } catch (IOException e) {
            warn.accept("Addon-Ressourcen konnten nicht vorbereitet werden: " + e.getMessage());
            return Files.isDirectory(target.resolve("assets")) ? target : null;
        }
    }

    static List<Path> jars(Path dir) {
        List<Path> out = new ArrayList<>();
        if (dir == null || !Files.isDirectory(dir)) {
            return out;
        }
        try (DirectoryStream<Path> s = Files.newDirectoryStream(dir, "*.jar")) {
            for (Path p : s) {
                if (Files.isRegularFile(p)) {
                    out.add(p);
                }
            }
        } catch (IOException e) {
            return out;
        }
        out.sort(Comparator.comparing(p -> p.getFileName().toString()));
        return out;
    }

    private static String stamp(List<Path> jars, byte[] packMeta) {
        StringBuilder sb = new StringBuilder("v1 meta=").append(packMeta == null ? 0 : java.util.Arrays.hashCode(packMeta));
        for (Path j : jars) {
            try {
                sb.append('\n').append(j.getFileName()).append(' ').append(Files.size(j)).append(' ')
                        .append(Files.getLastModifiedTime(j).toMillis());
            } catch (IOException e) {
                sb.append('\n').append(j.getFileName()).append(" ?");
            }
        }
        return sb.toString();
    }

    /** Copies the allowed assets of one jar; returns how many files it added. */
    private static int copy(Path jar, Path target, long[] total, Consumer<String> warn) throws IOException {
        int copied = 0;
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            if (zip.getEntry(JarAddonLoader.DESCRIPTOR) == null) {
                // not a Lumora addon (the addon list explains why); its files are not used
                return 0;
            }
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                if (e.isDirectory() || !allowed(e.getName())) {
                    continue;
                }
                long size = e.getSize();
                if (size > MAX_FILE_BYTES || total[0] + Math.max(0, size) > MAX_TOTAL_BYTES) {
                    warn.accept("Addon-Ressource zu groß, übersprungen: " + jar.getFileName() + "!" + e.getName());
                    continue;
                }
                Path out = target.resolve(e.getName()).normalize();
                if (!out.startsWith(target) || Files.exists(out)) {
                    // outside the folder (never for allowed names) or already shipped by an earlier addon
                    continue;
                }
                Files.createDirectories(out.getParent());
                try (InputStream in = zip.getInputStream(e)) {
                    long n = Files.copy(limited(in, MAX_FILE_BYTES + 1), out, StandardCopyOption.REPLACE_EXISTING);
                    if (n > MAX_FILE_BYTES) {
                        Files.deleteIfExists(out);
                        warn.accept("Addon-Ressource zu groß, übersprungen: " + jar.getFileName() + "!" + e.getName());
                        continue;
                    }
                    total[0] += n;
                }
                copied++;
            }
        }
        return copied;
    }

    /** {@code assets/<namespace>/<path>} with a plain namespace that is not reserved and no odd path segments. */
    static boolean allowed(String name) {
        if (!name.startsWith("assets/") || name.contains("\\") || name.contains("//")) {
            return false;
        }
        String[] parts = name.split("/");
        if (parts.length < 3 || !NAMESPACE.matcher(parts[1]).matches() || RESERVED.contains(parts[1])) {
            return false;
        }
        for (int i = 2; i < parts.length; i++) {
            if (!SEGMENT.matcher(parts[i]).matches() || parts[i].equals(".") || parts[i].equals("..")) {
                return false;
            }
        }
        return true;
    }

    /** The client's pack.mcmeta (right format for this version) with the description "Lumora-Addons". */
    static byte[] meta(byte[] packMeta) {
        if (packMeta == null || packMeta.length == 0 || packMeta.length > 64 * 1024) {
            return DEFAULT_META.getBytes(StandardCharsets.UTF_8);
        }
        String json = new String(packMeta, StandardCharsets.UTF_8);
        var m = DESCRIPTION.matcher(json);
        return (m.find() ? m.replaceFirst("\"description\": \"Lumora-Addons\"") : json).getBytes(StandardCharsets.UTF_8);
    }

    private static InputStream limited(InputStream in, long max) {
        return new InputStream() {
            private long left = max;

            @Override
            public int read() throws IOException {
                if (left <= 0) {
                    return -1;
                }
                int b = in.read();
                if (b >= 0) {
                    left--;
                }
                return b;
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                if (left <= 0) {
                    return -1;
                }
                int n = in.read(b, off, (int) Math.min(len, left));
                if (n > 0) {
                    left -= n;
                }
                return n;
            }
        };
    }

    private static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> s = Files.walk(dir)) {
            for (Path p : s.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        }
    }
}
