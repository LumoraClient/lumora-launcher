package dev.lumora.core.chat.emoji;

import com.google.gson.Gson;
import dev.lumora.api.render.TextureRef;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The emojis of the module "Emoji Chat": Twemoji graphics (CC-BY 4.0) in eight texture sheets of 16 x 16 cells and
 * a Minecraft bitmap font {@code lumora:emoji} with one private-use character per emoji (U+E000 + index), names,
 * aliases, tags and categories from gemoji (MIT). Built by {@code .t/EmojiAtlas.java}; see
 * {@code assets/lumora/emoji/NOTICE.txt}.
 */
public final class EmojiRegistry {
    public static final String FONT = "lumora:emoji";
    public static final String INDEX = "/assets/lumora/emoji/emojis.json";
    /** Categories in the order of the picker tabs (gemoji's order). */
    public static final List<String> CATEGORIES = List.of("smileys", "people", "nature", "food", "activities", "travel",
            "objects", "symbols", "flags");

    /** One emoji: its main name, all names that work as {@code :name:}, search words, category and atlas cell. */
    public record Emoji(String name, List<String> aliases, List<String> tags, String category, int index) {
        /** The font character in {@link #FONT}. */
        public String glyph() {
            return String.valueOf((char) (0xE000 + index));
        }

        public String shortcode() {
            return ":" + name + ":";
        }
    }

    /** JSON of {@code emojis.json} (field names as in the file; Gson 2.2.4 compatible plain classes). */
    static final class Index {
        int cell;
        int columns;
        List<Entry> emojis;
    }

    static final class Entry {
        String name;
        List<String> aliases;
        List<String> tags;
        String category;
    }

    private final int cell;
    private final int columns;
    private final List<Emoji> all;
    private final Map<String, Emoji> byName;
    private final Map<String, List<Emoji>> byCategory;
    /** One texture per sheet, made once (versions cache textures by this object). */
    private final TextureRef[] sheets;

    EmojiRegistry(int cell, int columns, List<Emoji> all) {
        this.cell = cell;
        this.columns = columns;
        this.all = List.copyOf(all);
        Map<String, Emoji> names = new HashMap<>();
        Map<String, List<Emoji>> cats = new LinkedHashMap<>();
        for (String c : CATEGORIES) {
            cats.put(c, new ArrayList<>());
        }
        for (Emoji e : all) {
            for (String a : e.aliases()) {
                names.putIfAbsent(a.toLowerCase(Locale.ROOT), e);
            }
            names.putIfAbsent(e.name().toLowerCase(Locale.ROOT), e);
            cats.computeIfAbsent(e.category(), k -> new ArrayList<>()).add(e);
        }
        this.byName = names;
        Map<String, List<Emoji>> frozen = new LinkedHashMap<>();
        cats.forEach((k, v) -> frozen.put(k, Collections.unmodifiableList(v)));
        this.byCategory = frozen;
        this.sheets = new TextureRef[(all.size() + columns * columns - 1) / (columns * columns)];
        for (int i = 0; i < sheets.length; i++) {
            sheets[i] = new TextureRef("lumora", "textures/font/emoji_" + i + ".png");
        }
    }

    /** Reads {@link #INDEX} from the classpath (the Lumora client jar). */
    public static EmojiRegistry load() throws IOException {
        try (InputStream in = EmojiRegistry.class.getResourceAsStream(INDEX)) {
            if (in == null) {
                throw new IOException(INDEX + " missing");
            }
            return read(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    static EmojiRegistry read(Reader reader) {
        Index index = new Gson().fromJson(reader, Index.class);
        if (index == null || index.emojis == null || index.cell <= 0 || index.columns <= 0) {
            throw new IllegalArgumentException("emoji index without emojis");
        }
        List<Emoji> list = new ArrayList<>();
        for (int i = 0; i < index.emojis.size(); i++) {
            Entry e = index.emojis.get(i);
            List<String> aliases = e.aliases == null || e.aliases.isEmpty() ? List.of(e.name) : List.copyOf(e.aliases);
            list.add(new Emoji(e.name, aliases, e.tags == null ? List.of() : List.copyOf(e.tags),
                    e.category == null ? "symbols" : e.category, i));
        }
        return new EmojiRegistry(index.cell, index.columns, list);
    }

    public List<Emoji> all() {
        return all;
    }

    /** The emoji for a name or alias (lower case), or null. */
    public Emoji byName(String name) {
        return name == null ? null : byName.get(name.toLowerCase(Locale.ROOT));
    }

    public List<Emoji> category(String category) {
        return byCategory.getOrDefault(category, List.of());
    }

    /**
     * Emojis whose names start with {@code query} first (shorter names first), then those whose names or search words
     * contain it, at most {@code limit}.
     */
    public List<Emoji> search(String query, int limit) {
        String q = query == null ? "" : query.toLowerCase(Locale.ROOT);
        if (q.isEmpty() || limit <= 0) {
            return List.of();
        }
        List<Emoji> starts = new ArrayList<>();
        List<Emoji> contains = new ArrayList<>();
        for (Emoji e : all) {
            String hit = null;
            for (String a : e.aliases()) {
                if (a.startsWith(q) && (hit == null || a.length() < hit.length())) {
                    hit = a;
                }
            }
            if (hit != null) {
                starts.add(e);
                continue;
            }
            boolean found = false;
            for (String a : e.aliases()) {
                found |= a.contains(q);
            }
            for (String t : e.tags()) {
                found |= t.startsWith(q);
            }
            if (found) {
                contains.add(e);
            }
        }
        starts.sort((a, b) -> Integer.compare(shortest(a, q), shortest(b, q)));
        List<Emoji> out = new ArrayList<>(starts);
        out.addAll(contains);
        return out.size() > limit ? List.copyOf(out.subList(0, limit)) : List.copyOf(out);
    }

    /** The alias of {@code e} that starts with {@code q} (the one a suggestion completes to), or its name. */
    public static String completion(Emoji e, String q) {
        String best = null;
        for (String a : e.aliases()) {
            if (a.startsWith(q) && (best == null || a.length() < best.length())) {
                best = a;
            }
        }
        return best == null ? e.name() : best;
    }

    private static int shortest(Emoji e, String q) {
        int best = Integer.MAX_VALUE;
        for (String a : e.aliases()) {
            if (a.startsWith(q)) {
                best = Math.min(best, a.length());
            }
        }
        return best;
    }

    /** Pixels per emoji in the texture sheets. */
    public int cell() {
        return cell;
    }

    /** The texture sheet of an emoji. */
    public TextureRef sheet(Emoji e) {
        return sheets[e.index() / (columns * columns)];
    }

    /** Left edge of the emoji in its sheet, in texture pixels. */
    public int u(Emoji e) {
        return e.index() % (columns * columns) % columns * cell;
    }

    /** Top edge of the emoji in its sheet, in texture pixels. */
    public int v(Emoji e) {
        return e.index() % (columns * columns) / columns * cell;
    }

    /** Width and height of a whole sheet in pixels. */
    public int sheetSize() {
        return columns * cell;
    }
}
