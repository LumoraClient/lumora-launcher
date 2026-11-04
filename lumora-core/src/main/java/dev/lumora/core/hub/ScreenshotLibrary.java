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
 * The game's screenshots ({@code <game>/screenshots}): listing with search, filter and sort, favourites (kept in
 * {@code config/lumora/screenshots.json}), renaming and deleting. Only files directly in the folder with an image
 * extension count; names are checked so nothing outside the folder can be touched.
 */
final class ScreenshotLibrary {
    /** One image file. */
    record Shot(Path file, String name, long size, long modified) {
    }

    enum Sort { NEWEST, OLDEST, NAME, SIZE }

    private static final Pattern IMAGE = Pattern.compile("(?i).+\\.(png|jpe?g)");
    /** Allowed new names (without extension): letters incl. umlauts, digits, space and - _ . ( ) */
    private static final Pattern NAME = Pattern.compile("[\\p{L}\\p{N} _.()\\-]{1,100}");

    private final Path dir;
    private final Path favoritesFile;
    private final Set<String> favorites = new LinkedHashSet<>();
    private List<Shot> all = List.of();

    ScreenshotLibrary(Path dir, Path favoritesFile) {
        this.dir = dir;
        this.favoritesFile = favoritesFile;
        loadFavorites();
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
        String q = search == null ? "" : search.strip().toLowerCase(Locale.ROOT);
        List<Shot> out = new ArrayList<>();
        for (Shot s : all) {
            if (favoritesOnly && !isFavorite(s)) {
                continue;
            }
            if (!q.isEmpty() && !s.name().toLowerCase(Locale.ROOT).contains(q)) {
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
            saveFavorites();
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

    /** Renames inside the folder; returns the new shot. Favourite status moves along. */
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
        if (favorites.remove(s.name())) {
            favorites.add(target);
            saveFavorites();
        }
        refresh();
        return new Shot(to, target, s.size(), s.modified());
    }

    /** Deletes the file (after the view asked); the favourite entry goes too. */
    void delete(Shot s, java.util.function.Predicate<Path> trash) throws IOException {
        Path f = inside(s.name());
        if (trash == null || !trash.test(f)) {
            Files.deleteIfExists(f);
        }
        if (favorites.remove(s.name())) {
            saveFavorites();
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

    private void loadFavorites() {
        favorites.clear();
        if (favoritesFile == null || !Files.isRegularFile(favoritesFile)) {
            return;
        }
        try {
            JsonElement root = new JsonParser().parse(new String(Files.readAllBytes(favoritesFile), StandardCharsets.UTF_8));
            if (root.isJsonObject() && root.getAsJsonObject().get("favorites") instanceof JsonArray a) {
                for (JsonElement e : a) {
                    if (e.isJsonPrimitive()) {
                        favorites.add(e.getAsString());
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            // broken file: start without favourites, replaced on the next change
        }
    }

    private void saveFavorites() {
        if (favoritesFile == null) {
            return;
        }
        JsonObject root = new JsonObject();
        JsonArray a = new JsonArray();
        for (String f : favorites) {
            a.add(new com.google.gson.JsonPrimitive(f));
        }
        root.add("favorites", a);
        try {
            Files.createDirectories(favoritesFile.getParent());
            Path tmp = favoritesFile.resolveSibling(favoritesFile.getFileName() + ".tmp");
            Files.write(tmp, new Gson().toJson(root).getBytes(StandardCharsets.UTF_8));
            Files.move(tmp, favoritesFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            // favourites are a convenience: keep them in memory
        }
    }
}
