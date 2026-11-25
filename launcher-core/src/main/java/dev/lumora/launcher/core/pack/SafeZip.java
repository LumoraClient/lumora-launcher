package dev.lumora.launcher.core.pack;

import dev.lumora.launcher.core.util.FileUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Reading archives from outside (modpacks, imports) without trusting their paths or sizes:
 * <ul>
 *   <li>entry paths must be relative: no leading slash or backslash, no drive letter, no {@code ..} segment, no NUL
 *       (the rules of the .mrpack format, applied to every archive);</li>
 *   <li>every target is resolved inside the destination ({@link FileUtil#resolveInside});</li>
 *   <li>limits on the number of entries, the size of one entry and the total size (counted while unpacking, so a
 *       forged header does not help);</li>
 *   <li>files are written to a temporary name and moved into place when complete.</li>
 * </ul>
 */
public final class SafeZip {
    public static final int MAX_ENTRIES = 100_000;
    public static final long MAX_ENTRY_BYTES = 2L * 1024 * 1024 * 1024;
    public static final long MAX_TOTAL_BYTES = 8L * 1024 * 1024 * 1024;
    private static final Pattern DRIVE = Pattern.compile("^[A-Za-z]:.*");

    private SafeZip() {
    }

    /** The checked, normalised path of an entry (forward slashes), or an exception if it is unsafe. */
    public static String checkedName(String name) throws IOException {
        if (name == null || name.isEmpty() || name.indexOf('\0') >= 0) {
            throw new IOException("invalid archive entry name");
        }
        String n = name.replace('\\', '/');
        if (n.startsWith("/") || DRIVE.matcher(n).matches()) {
            throw new IOException("absolute path in archive refused: " + name);
        }
        for (String seg : n.split("/")) {
            if (seg.equals("..")) {
                throw new IOException("path with .. in archive refused: " + name);
            }
        }
        return n;
    }

    /** Names of all entries (checked), at most {@link #MAX_ENTRIES}. */
    public static List<String> list(ZipFile zip) throws IOException {
        List<String> out = new ArrayList<>();
        Enumeration<? extends ZipEntry> en = zip.entries();
        while (en.hasMoreElements()) {
            ZipEntry e = en.nextElement();
            if (out.size() >= MAX_ENTRIES) {
                throw new IOException("archive has too many entries");
            }
            out.add(checkedName(e.getName()));
        }
        return out;
    }

    /** Reads one small entry completely (at most {@code maxBytes}), or null if it does not exist. */
    public static byte[] read(ZipFile zip, String name, int maxBytes) throws IOException {
        ZipEntry e = zip.getEntry(name);
        if (e == null || e.isDirectory()) {
            return null;
        }
        try (InputStream in = zip.getInputStream(e)) {
            byte[] data = in.readNBytes(maxBytes + 1);
            if (data.length > maxBytes) {
                throw new IOException(name + " is larger than " + maxBytes + " bytes");
            }
            return data;
        }
    }

    /** What happened to one file while unpacking. */
    public record Extracted(String path, Path target, long size, boolean replaced) {
    }

    /**
     * Unpacks all files below {@code prefix} (e.g. {@code "overrides/"}, empty = everything) into {@code destination},
     * keeping their path below the prefix. Existing files are replaced only when {@code overwrite} accepts their
     * relative path; otherwise they are kept and left out of the result.
     */
    public static List<Extracted> extract(ZipFile zip, String prefix, Path destination, Predicate<String> overwrite) throws IOException {
        List<Extracted> out = new ArrayList<>();
        long total = 0;
        int count = 0;
        Enumeration<? extends ZipEntry> en = zip.entries();
        while (en.hasMoreElements()) {
            ZipEntry e = en.nextElement();
            if (++count > MAX_ENTRIES) {
                throw new IOException("archive has too many entries");
            }
            String name = checkedName(e.getName());
            if (!name.startsWith(prefix) || e.isDirectory()) {
                continue;
            }
            String rel = name.substring(prefix.length());
            if (rel.isEmpty() || rel.endsWith("/")) {
                continue;
            }
            Path target = FileUtil.resolveInside(destination, rel);
            boolean exists = Files.exists(target);
            if (exists && (overwrite == null || !overwrite.test(rel))) {
                continue;
            }
            if (Files.isDirectory(target)) {
                throw new IOException("archive file collides with a folder: " + rel);
            }
            Files.createDirectories(target.getParent());
            Path tmp = target.resolveSibling(target.getFileName() + ".lumora-part");
            long size = 0;
            try (InputStream in = zip.getInputStream(e); OutputStream os = Files.newOutputStream(tmp)) {
                byte[] buf = new byte[64 * 1024];
                int r;
                while ((r = in.read(buf)) > 0) {
                    size += r;
                    total += r;
                    if (size > MAX_ENTRY_BYTES || total > MAX_TOTAL_BYTES) {
                        throw new IOException("archive content too large: " + rel);
                    }
                    os.write(buf, 0, r);
                }
            } catch (IOException ex) {
                Files.deleteIfExists(tmp);
                throw ex;
            }
            FileUtil.moveReplacing(tmp, target);
            out.add(new Extracted(rel, target, size, exists));
        }
        return out;
    }

    /** File extension in lower case without the dot ("jar", "zip", "mrpack"), or "". */
    public static String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
