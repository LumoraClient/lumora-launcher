package dev.lumora.launcher.core.content;

import com.google.gson.JsonParseException;
import dev.lumora.launcher.core.util.FileUtil;
import dev.lumora.launcher.core.util.Json;
import dev.lumora.launcher.core.util.Log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Where installed files came from, per game folder: {@code <game>/.lumora/content.json}. Keys are paths relative to the
 * game folder ({@code mods/sodium-0.6.jar}); a disabled file keeps its key without {@code .disabled}. The file is a
 * convenience for the interface (names, icons, updates, pins): losing it never breaks the instance, files are then
 * identified again by their hash.
 */
public final class ContentManifest {
    public static final String FILE = ".lumora/content.json";
    private static final Pattern KEY = Pattern.compile("(mods|resourcepacks|shaderpacks|datapacks)/[^/\\\\]{1,200}");

    /** One installed file. */
    public static final class Entry {
        public String platform;
        public String projectId;
        public String versionId;
        public String versionNumber;
        public String title;
        public String iconUrl;
        public String sha1;
        public String sha512;
        /** Where the file came from (for exporting modpacks). */
        public String url;
        /** Keep this version: no update offers. */
        public boolean pinned;
        /** Installed as a dependency of these project ids (empty = chosen by the player). */
        public List<String> requiredBy = new java.util.ArrayList<>();
        public String installed;

        public boolean explicit() {
            return requiredBy == null || requiredBy.isEmpty();
        }
    }

    static final class Stored {
        int format = 1;
        Map<String, Entry> files = new LinkedHashMap<>();
    }

    private final Path file;
    private final Map<String, Entry> files;

    private ContentManifest(Path file, Map<String, Entry> files) {
        this.file = file;
        this.files = files;
    }

    public static ContentManifest load(Path gameDir) {
        Path f = gameDir.resolve(FILE);
        Map<String, Entry> files = new LinkedHashMap<>();
        if (Files.isRegularFile(f)) {
            try {
                Stored s = Json.GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), Stored.class);
                if (s != null && s.files != null) {
                    s.files.forEach((k, v) -> {
                        if (k != null && v != null && KEY.matcher(k).matches() && !k.contains("..")) {
                            if (v.requiredBy == null) {
                                v.requiredBy = new java.util.ArrayList<>();
                            }
                            files.put(k, v);
                        }
                    });
                }
            } catch (IOException | JsonParseException e) {
                Log.warn("Content list " + f + " unreadable, files are identified again: " + e.getMessage());
            }
        }
        return new ContentManifest(f, files);
    }

    /** Key of a file: its path below the game folder with forward slashes and without {@code .disabled}. */
    public static String key(String folder, String fileName) {
        String n = fileName.endsWith(InstalledContent.DISABLED) ? fileName.substring(0, fileName.length() - InstalledContent.DISABLED.length()) : fileName;
        return folder + "/" + n;
    }

    public synchronized Optional<Entry> get(String key) {
        return Optional.ofNullable(files.get(key));
    }

    public synchronized Map<String, Entry> all() {
        return Map.copyOf(files);
    }

    /** The key of the file that holds {@code projectId} of {@code platform}, if installed. */
    public synchronized Optional<String> keyOf(String platform, String projectId) {
        for (Map.Entry<String, Entry> e : files.entrySet()) {
            if (projectId != null && projectId.equals(e.getValue().projectId) && platform.equals(e.getValue().platform)) {
                return Optional.of(e.getKey());
            }
        }
        return Optional.empty();
    }

    public synchronized void put(String key, Entry entry) {
        if (!KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("invalid content key " + key);
        }
        files.put(key, entry);
    }

    public synchronized void remove(String key) {
        files.remove(key);
    }

    /** Drops entries whose file (enabled or disabled) no longer exists. */
    public synchronized void prune(Path gameDir) {
        files.keySet().removeIf(k -> !Files.exists(gameDir.resolve(k)) && !Files.exists(gameDir.resolve(k + InstalledContent.DISABLED)));
    }

    public synchronized void save() throws IOException {
        Stored s = new Stored();
        s.files = new LinkedHashMap<>(files);
        FileUtil.writeAtomically(file, Json.GSON.toJson(s).getBytes(StandardCharsets.UTF_8));
    }
}
