package dev.lumora.core.hub;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The game's screenshots ({@code <game>/screenshots}): listing with search, filter and sort, favourites, own albums and
 * where each screenshot was taken (server, account, players nearby; written by {@link ScreenshotWatcher}), all kept in
 * {@code config/lumora/screenshots.json}; renaming and deleting. The automatic albums group the screenshots by server,
 * player and account. Only files directly in the folder with an image
 * extension count; names are checked so nothing outside the folder can be touched.
 */
final class ScreenshotLibrary {
    /** One image file. */
    record Shot(Path file, String name, long size, long modified) {
    }

    enum Sort { NEWEST, OLDEST, NAME, SIZE }

    /** Where a screenshot was taken: server address ("" in singleplayer), own account, players nearby. */
    record Meta(String server, String account, List<String> players) {
    }

    /** Kind of an album: own, or automatic by server, player or account. */
    enum Kind { OWN, SERVER, PLAYER, ACCOUNT }

    /** An album: its shots newest first (the first is the cover). */
    record Album(Kind kind, String name, List<Shot> shots) {
    }

    private static final java.util.Map<Path, ScreenshotLibrary> SHARED = new java.util.HashMap<>();

    private static final Pattern IMAGE = Pattern.compile("(?i).+\\.(png|jpe?g)");
    /** Allowed new names (without extension): letters incl. umlauts, digits, space and - _ . ( ) */
    private static final Pattern NAME = Pattern.compile("[\\p{L}\\p{N} _.()\\-]{1,100}");

    private final Path dir;
    private final Path favoritesFile;
    private final Set<String> favorites = new LinkedHashSet<>();
    /** Own albums in the order they were made: name to file names. */
    private final java.util.Map<String, List<String>> albums = new java.util.LinkedHashMap<>();
    private final java.util.Map<String, Meta> meta = new java.util.HashMap<>();
    private List<Shot> all = List.of();

    ScreenshotLibrary(Path dir, Path favoritesFile) {
        this.dir = dir;
        this.favoritesFile = favoritesFile;
        load();
    }

    /** One library per folder (screenshot manager and watcher share it: one writer of the file). Client thread. */
    static synchronized ScreenshotLibrary shared(Path dir, Path dataFile) {
        return SHARED.computeIfAbsent(dir.toAbsolutePath().normalize(), d -> new ScreenshotLibrary(dir, dataFile));
    }

    Path directory() {
        return dir;
    }

    /** Reads the folder again. */
    void refresh() {
        List<Shot> out = new ArrayList<>();
        if (Files.isDirectory(dir)) {
            try (DirectoryStream<Path> s = Files.newDirectoryStream(dir)) {
                for (Path p : s) {
                    String n = p.getFileName().toString();
                    if (IMAGE.matcher(n).matches() && Files.isRegularFile(p)) {
                        try {
                            out.add(new Shot(p, n, Files.size(p), Files.getLastModifiedTime(p).toMillis()));
                        } catch (IOException e) {
                            // vanished meanwhile
                        }
                    }
                }
            } catch (IOException e) {
                // unreadable folder: empty list
            }
        }
        all = List.copyOf(out);
    }

    List<Shot> all() {
        return all;
    }

    /** Shots matching the search (file name), the favourites filter, in the given order. */
    List<Shot> view(String search, boolean favoritesOnly, Sort sort) {
        return view(search, favoritesOnly, sort, null);
    }

    /**
     * Shots of {@code album} (null: all) matching the search (file name, server, account and players) and the
     * favourites filter, in the given order.
     */
    List<Shot> view(String search, boolean favoritesOnly, Sort sort, Album album) {
        String q = search == null ? "" : search.strip().toLowerCase(Locale.ROOT);
        Set<String> in = null;
        if (album != null) {
            in = new java.util.HashSet<>();
            for (Shot s : album.shots()) {
                in.add(s.name());
            }
        }
        List<Shot> out = new ArrayList<>();
        for (Shot s : all) {
            if (in != null && !in.contains(s.name())) {
                continue;
            }
            if (favoritesOnly && !isFavorite(s)) {
                continue;
            }
            if (!q.isEmpty() && !matches(s, q)) {
                continue;
            }
            out.add(s);
        }
        Comparator<Shot> c = switch (sort) {
            case NEWEST -> Comparator.comparingLong(Shot::modified).reversed();
            case OLDEST -> Comparator.comparingLong(Shot::modified);
            case NAME -> Comparator.comparing(s -> s.name().toLowerCase(Locale.ROOT));
            case SIZE -> Comparator.comparingLong(Shot::size).reversed();
        };
        out.sort(c.thenComparing(Shot::name));
        return out;
    }

    private boolean matches(Shot s, String q) {
        if (s.name().toLowerCase(Locale.ROOT).contains(q)) {
            return true;
        }
        Meta m = meta.get(s.name());
        if (m == null) {
            return false;
        }
        if (m.server().toLowerCase(Locale.ROOT).contains(q) || m.account().toLowerCase(Locale.ROOT).contains(q)) {
            return true;
        }
        for (String p : m.players()) {
            if (p.toLowerCase(Locale.ROOT).contains(q)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ where a screenshot was taken

    /** Where the shot was taken, or null (taken before Lumora watched, or outside the game). */
    Meta meta(Shot s) {
        return meta.get(s.name());
    }

    boolean hasMeta(String fileName) {
        return meta.containsKey(fileName);
    }

    /** Remembers where a new screenshot was taken (watcher). */
    void record(String fileName, Meta m) {
        if (fileName != null && m != null && IMAGE.matcher(fileName).matches()) {
            meta.put(fileName, new Meta(clean(m.server()), clean(m.account()), List.copyOf(m.players())));
            save();
        }
    }

    private static String clean(String s) {
        return s == null ? "" : s.strip();
    }

    // ------------------------------------------------------------------ albums

    /** Names of the own albums in the order they were made. */
    List<String> albumNames() {
        return List.copyOf(albums.keySet());
    }

    /** Problem with an album name (null if fine): "name" (empty, too long) or "exists". */
    String checkAlbumName(String name, String except) {
        String n = name == null ? "" : name.strip();
        if (n.isEmpty() || n.length() > 40 || n.chars().anyMatch(Character::isISOControl)) {
            return "name";
        }
        for (String a : albums.keySet()) {
            if (a.equalsIgnoreCase(n) && !a.equals(except)) {
                return "exists";
            }
        }
        return null;
    }

    /** Makes an empty album; returns its name, or null if the name is not allowed. */
    String createAlbum(String name) {
        if (checkAlbumName(name, null) != null) {
            return null;
        }
        String n = name.strip();
        albums.put(n, new ArrayList<>());
        save();
        return n;
    }

    boolean renameAlbum(String from, String to) {
        if (!albums.containsKey(from) || checkAlbumName(to, from) != null) {
            return false;
        }
        java.util.Map<String, List<String>> copy = new java.util.LinkedHashMap<>();
        for (java.util.Map.Entry<String, List<String>> e : albums.entrySet()) {
            copy.put(e.getKey().equals(from) ? to.strip() : e.getKey(), e.getValue());
        }
        albums.clear();
        albums.putAll(copy);
        save();
        return true;
    }

    /** Removes the album; the screenshots stay. */
    void deleteAlbum(String name) {
        if (albums.remove(name) != null) {
            save();
        }
    }

    /** Adds the shots to the album; returns how many were new in it. */
    int addToAlbum(String name, List<Shot> shots) {
        List<String> l = albums.get(name);
        if (l == null) {
            return 0;
        }
        int added = 0;
        for (Shot s : shots) {
            if (!l.contains(s.name())) {
                l.add(s.name());
                added++;
            }
        }
        if (added > 0) {
            save();
        }
        return added;
    }

    int removeFromAlbum(String name, List<Shot> shots) {
        List<String> l = albums.get(name);
        if (l == null) {
            return 0;
        }
        int removed = 0;
        for (Shot s : shots) {
            if (l.remove(s.name())) {
                removed++;
            }
        }
        if (removed > 0) {
            save();
        }
        return removed;
    }

    /** The own album of that name with its existing shots, or null. */
    Album album(String name) {
        List<String> l = albums.get(name);
        if (l == null) {
            return null;
        }
        Set<String> names = new java.util.HashSet<>(l);
        List<Shot> shots = new ArrayList<>();
        for (Shot s : all) {
            if (names.contains(s.name())) {
                shots.add(s);
            }
        }
        shots.sort(Comparator.comparingLong(Shot::modified).reversed().thenComparing(Shot::name));
        return new Album(Kind.OWN, name, shots);
    }

    List<Album> ownAlbums() {
        List<Album> out = new ArrayList<>();
        for (String n : albums.keySet()) {
            out.add(album(n));
        }
        return out;
    }

    /**
     * The automatic albums of a kind: one per server ("" = singleplayer), player nearby or account, the largest first.
     */
    List<Album> autoAlbums(Kind kind) {
        java.util.Map<String, List<Shot>> groups = new java.util.LinkedHashMap<>();
        List<Shot> newest = new ArrayList<>(all);
        newest.sort(Comparator.comparingLong(Shot::modified).reversed().thenComparing(Shot::name));
        for (Shot s : newest) {
            Meta m = meta.get(s.name());
            if (m == null) {
                continue;
            }
            List<String> keys = switch (kind) {
                case SERVER -> List.of(m.server());
                case ACCOUNT -> m.account().isEmpty() ? List.of() : List.of(m.account());
                case PLAYER -> m.players();
                case OWN -> List.of();
            };
            for (String k : keys) {
                groups.computeIfAbsent(k, x -> new ArrayList<>()).add(s);
            }
        }
        List<Album> out = new ArrayList<>();
        for (java.util.Map.Entry<String, List<Shot>> e : groups.entrySet()) {
            out.add(new Album(kind, e.getKey(), List.copyOf(e.getValue())));
        }
        out.sort(Comparator.comparingInt((Album a) -> -a.shots().size()).thenComparing(a -> a.name().toLowerCase(Locale.ROOT)));
        return out;
    }

    long totalBytes() {
        long n = 0;
        for (Shot s : all) {
            n += s.size();
        }
        return n;
    }

    int favoriteCount() {
        int n = 0;
        for (Shot s : all) {
            if (isFavorite(s)) {
                n++;
            }
        }
        return n;
    }

    boolean isFavorite(Shot s) {
        return favorites.contains(s.name());
    }

    void setFavorite(Shot s, boolean on) {
        boolean changed = on ? favorites.add(s.name()) : favorites.remove(s.name());
        if (changed) {
            save();
        }
    }

    /** Problem with a new name (null if fine). {@code base} is without extension. */
    String checkName(Shot s, String base) {
        String b = base == null ? "" : base.strip();
        if (!NAME.matcher(b).matches() || b.startsWith(".") || b.endsWith(".")) {
            return "name";
        }
        String target = b + extension(s.name());
        if (!target.equals(s.name()) && Files.exists(dir.resolve(target))) {
            return "exists";
        }
        return null;
    }

    /** Renames inside the folder; returns the new shot. Favourite, albums and where it was taken move along. */
    Shot rename(Shot s, String base) throws IOException {
        String problem = checkName(s, base);
        if (problem != null) {
            throw new IOException(problem);
        }
        String target = base.strip() + extension(s.name());
        if (target.equals(s.name())) {
            return s;
        }
        Path to = inside(target);
        Files.move(inside(s.name()), to, StandardCopyOption.ATOMIC_MOVE);
        boolean changed = false;
        if (favorites.remove(s.name())) {
            favorites.add(target);
            changed = true;
        }
        Meta m = meta.remove(s.name());
        if (m != null) {
            meta.put(target, m);
            changed = true;
        }
        for (List<String> l : albums.values()) {
            int i = l.indexOf(s.name());
            if (i >= 0) {
                l.set(i, target);
                changed = true;
            }
        }
        if (changed) {
            save();
        }
        refresh();
        return new Shot(to, target, s.size(), s.modified());
    }

    /** Deletes the file (after the view asked); its favourite, album and place entries go too. */
    void delete(Shot s, java.util.function.Predicate<Path> trash) throws IOException {
        Path f = inside(s.name());
        if (trash == null || !trash.test(f)) {
            Files.deleteIfExists(f);
        }
        boolean changed = favorites.remove(s.name());
        changed |= meta.remove(s.name()) != null;
        for (List<String> l : albums.values()) {
            changed |= l.remove(s.name());
        }
        if (changed) {
            save();
        }
        refresh();
    }

    /** A file name of this folder as path; throws for anything that would leave the folder. */
    Path inside(String name) throws IOException {
        Path p = dir.resolve(name).normalize();
        if (!p.getParent().equals(dir.normalize()) || name.contains("/") || name.contains("\\")) {
            throw new IOException("outside of the screenshots folder: " + name);
        }
        return p;
    }

    static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot);
    }

    static String baseName(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    private void load() {
        favorites.clear();
        albums.clear();
        meta.clear();
        if (favoritesFile == null || !Files.isRegularFile(favoritesFile)) {
            return;
        }
        try {
            JsonElement root = new JsonParser().parse(new String(Files.readAllBytes(favoritesFile), StandardCharsets.UTF_8));
            if (!root.isJsonObject()) {
                return;
            }
            JsonObject o = root.getAsJsonObject();
            if (o.get("favorites") instanceof JsonArray a) {
                favorites.addAll(strings(a));
            }
            if (o.get("albums") instanceof JsonObject al) {
                for (java.util.Map.Entry<String, JsonElement> e : al.entrySet()) {
                    if (checkAlbumName(e.getKey(), null) == null && e.getValue() instanceof JsonArray a) {
                        albums.put(e.getKey().strip(), new ArrayList<>(strings(a)));
                    }
                }
            }
            if (o.get("meta") instanceof JsonObject mo) {
                for (java.util.Map.Entry<String, JsonElement> e : mo.entrySet()) {
                    if (e.getValue() instanceof JsonObject m) {
                        String server = m.get("server") != null && m.get("server").isJsonPrimitive() ? m.get("server").getAsString() : "";
                        String account = m.get("account") != null && m.get("account").isJsonPrimitive() ? m.get("account").getAsString() : "";
                        List<String> players = m.get("players") instanceof JsonArray pa ? strings(pa) : List.of();
                        meta.put(e.getKey(), new Meta(server, account, players));
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            // broken file: start empty, replaced on the next change
        }
    }

    private static List<String> strings(JsonArray a) {
        List<String> out = new ArrayList<>();
        for (JsonElement e : a) {
            if (e.isJsonPrimitive()) {
                out.add(e.getAsString());
            }
        }
        return List.copyOf(out);
    }

    private static JsonArray array(java.util.Collection<String> values) {
        JsonArray a = new JsonArray();
        for (String v : values) {
            a.add(new com.google.gson.JsonPrimitive(v));
        }
        return a;
    }

    private void save() {
        if (favoritesFile == null) {
            return;
        }
        JsonObject root = new JsonObject();
        root.add("favorites", array(favorites));
        JsonObject al = new JsonObject();
        for (java.util.Map.Entry<String, List<String>> e : albums.entrySet()) {
            al.add(e.getKey(), array(e.getValue()));
        }
        root.add("albums", al);
        JsonObject mo = new JsonObject();
        for (java.util.Map.Entry<String, Meta> e : meta.entrySet()) {
            JsonObject m = new JsonObject();
            m.addProperty("server", e.getValue().server());
            m.addProperty("account", e.getValue().account());
            m.add("players", array(e.getValue().players()));
            mo.add(e.getKey(), m);
        }
        root.add("meta", mo);
        try {
            Files.createDirectories(favoritesFile.getParent());
            Path tmp = favoritesFile.resolveSibling(favoritesFile.getFileName() + ".tmp");
            Files.write(tmp, new Gson().toJson(root).getBytes(StandardCharsets.UTF_8));
            Files.move(tmp, favoritesFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            // a convenience: keep it in memory
        }
    }
}
