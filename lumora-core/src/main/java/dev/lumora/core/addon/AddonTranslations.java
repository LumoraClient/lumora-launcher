package dev.lumora.core.addon;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.lumora.api.i18n.Translations;
import dev.lumora.core.io.Json;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.IllegalFormatException;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Translations of an addon from the {@code addons/} folder. Its jar is not part of Minecraft's resource packs, so the
 * game does not know its language files; this looks keys up in the game first (resource packs can still override)
 * and then in the jar's own {@code assets/<namespace>/lang/<language>.json} (or {@code .lang} up to 1.12), falling
 * back to {@code en_us}. Files are read once per language.
 */
final class AddonTranslations implements Translations {
    static final String FALLBACK = "en_us";
    private static final int MAX_FILE_BYTES = 1024 * 1024;

    private final Translations game;
    private final Path jar;
    private final Map<String, Map<String, String>> byLanguage = new ConcurrentHashMap<>();

    AddonTranslations(Translations game, Path jar) {
        this.game = game;
        this.jar = jar;
    }

    @Override
    public String translate(String key, Object... args) {
        if (game.has(key)) {
            return game.translate(key, args);
        }
        String pattern = own(key);
        if (pattern == null) {
            return game.translate(key, args);
        }
        if (args == null || args.length == 0) {
            return pattern;
        }
        try {
            return String.format(Locale.ROOT, pattern, args);
        } catch (IllegalFormatException e) {
            return pattern;
        }
    }

    @Override
    public boolean has(String key) {
        return game.has(key) || own(key) != null;
    }

    @Override
    public String language() {
        return game.language();
    }

    private String own(String key) {
        String lang = language() == null ? FALLBACK : language().toLowerCase(Locale.ROOT);
        String v = strings(lang).get(key);
        return v != null || lang.equals(FALLBACK) ? v : strings(FALLBACK).get(key);
    }

    private Map<String, String> strings(String lang) {
        return byLanguage.computeIfAbsent(lang, l -> load(jar, l));
    }

    /** All {@code assets/<ns>/lang/<lang>.json|.lang} entries of the jar, merged (file name case-insensitive). */
    static Map<String, String> load(Path jar, String lang) {
        Map<String, String> out = new HashMap<>();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                String name = e.getName().toLowerCase(Locale.ROOT);
                if (e.isDirectory() || !name.startsWith("assets/") || e.getSize() > MAX_FILE_BYTES) {
                    continue;
                }
                boolean json = name.endsWith("/lang/" + lang + ".json");
                boolean legacy = name.endsWith("/lang/" + lang + ".lang");
                if (!json && !legacy) {
                    continue;
                }
                String text;
                try (InputStream in = zip.getInputStream(e)) {
                    text = new String(in.readNBytes(MAX_FILE_BYTES), StandardCharsets.UTF_8);
                }
                if (json) {
                    parseJson(text, out);
                } else {
                    parseLang(text, out);
                }
            }
        } catch (IOException | RuntimeException e) {
            // no translations from this jar; keys are shown as they are
        }
        return out;
    }

    static void parseJson(String text, Map<String, String> out) {
        JsonElement root = Json.parse(text);
        if (root instanceof JsonObject o) {
            for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                if (e.getValue() != null && e.getValue().isJsonPrimitive()) {
                    out.put(e.getKey(), e.getValue().getAsString());
                }
            }
        }
    }

    /** {@code key=value} lines, {@code #} comments (Minecraft up to 1.12). */
    static void parseLang(String text, Map<String, String> out) {
        for (String line : text.split("\r?\n")) {
            if (line.isEmpty() || line.charAt(0) == '#') {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq > 0) {
                out.put(line.substring(0, eq).strip(), line.substring(eq + 1));
            }
        }
    }
}
