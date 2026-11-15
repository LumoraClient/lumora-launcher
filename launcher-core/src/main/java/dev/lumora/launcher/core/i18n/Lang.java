package dev.lumora.launcher.core.i18n;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Texts of the launcher, read from {@code /lumora/lang/<code>.json} (flat {@code "key": "text"} map). No text of the
 * launcher's interface is written in the code: everything goes through {@link #t}. {@code {0}}, {@code {1}}, ... in a
 * text are replaced by the arguments (no MessageFormat, so apostrophes need no escaping). A missing key shows the key
 * itself, so it is noticed instead of silently showing nothing. {@code en_us} is the base every other language falls
 * back to.
 */
public final class Lang {
    public static final String BASE = "en_us";

    private static volatile Map<String, String> texts = read(BASE);
    private static volatile Locale locale = Locale.US;

    private Lang() {
    }

    /** Switches the language ({@code de_de}, ...); keys the language lacks keep the English text. */
    public static synchronized void use(String code) {
        Map<String, String> merged = new HashMap<>(read(BASE));
        if (code != null && !code.equals(BASE)) {
            merged.putAll(read(code));
        }
        texts = merged;
        String tag = merged.getOrDefault("lang.locale", "en-US");
        locale = Locale.forLanguageTag(tag);
    }

    /** Locale of the current language (number and date formats). */
    public static Locale locale() {
        return locale;
    }

    /** Text of {@code key}; {@code {0}}, {@code {1}}, ... are filled with {@code args}. */
    public static String t(String key, Object... args) {
        String s = texts.get(key);
        if (s == null) {
            return key;
        }
        if (args.length == 0 || s.indexOf('{') < 0) {
            return s;
        }
        StringBuilder out = new StringBuilder(s.length() + 16);
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            int close = c == '{' ? s.indexOf('}', i) : -1;
            if (close > i + 1 && close - i <= 3 && isIndex(s, i + 1, close)) {
                int n = Integer.parseInt(s.substring(i + 1, close));
                out.append(n < args.length ? String.valueOf(args[n]) : s.substring(i, close + 1));
                i = close + 1;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    /** {@code key.one} for exactly 1, otherwise {@code key.other}; {@code {0}} is {@code n}. */
    public static String plural(String key, long n, Object... more) {
        Object[] args = new Object[more.length + 1];
        args[0] = n;
        System.arraycopy(more, 0, args, 1, more.length);
        return t(key + (n == 1 ? ".one" : ".other"), args);
    }

    /** True if the current language has a text for {@code key}. */
    public static boolean has(String key) {
        return texts.containsKey(key);
    }

    private static boolean isIndex(String s, int from, int to) {
        for (int i = from; i < to; i++) {
            if (!Character.isDigit(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static Map<String, String> read(String code) {
        Map<String, String> map = new HashMap<>();
        if (code == null || !code.matches("[a-z]{2}_[a-z]{2}")) {
            return map;
        }
        try (InputStream in = Lang.class.getResourceAsStream("/lumora/lang/" + code + ".json")) {
            if (in == null) {
                return map;
            }
            JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                if (e.getValue().isJsonPrimitive()) {
                    map.put(e.getKey(), e.getValue().getAsString());
                }
            }
        } catch (IOException | RuntimeException e) {
            System.err.println("[Lumora] language " + code + " could not be read: " + e.getMessage());
        }
        return map;
    }
}
