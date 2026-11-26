package dev.lumora.voice.ui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * UI colors of LumoraVoice: built into the client, they follow the look chosen in the Lumora hub ({@link #follow}:
 * accent colour, surfaces, borders, text); the defaults below are used outside the client (tests). {@code assets/lumoravoice/theme.json} documents them; the Lumora addon can pass a
 * resource-pack override through {@link #reload(Supplier)}. Colors: {@code "#RRGGBB"} or {@code "#AARRGGBB"}.
 */
public final class Theme {
    private static final Map<String, Integer> DEFAULTS = new HashMap<>();
    private static volatile Map<String, Integer> overrides = Map.of();
    /** The client's look (accent, surfaces, text), read fresh on every call; null outside the client (tests). */
    private static volatile dev.lumora.api.ui.Theme client;

    static {
        def("text", 0xFFE8EAED); def("text_muted", 0xFF98A1AD); def("text_subtle", 0xFF5F6874);
        def("accent", 0xFF18B4CC); def("accent_hover", 0xFF26C7E0); def("accent_dark", 0xFF1297AB); def("accent_text", 0xFFFFFFFF);
        def("ok", 0xFF3DD68C); def("error", 0xFFF2555A); def("warning", 0xFFF5B342);
        def("background", 0xFF111318); def("scrim_menu", 0x70111318); def("scrim_world", 0xB0111318);
        def("surface", 0xF0191C22); def("border", 0xFF2B3039); def("border_hover", 0xFF3A414C); def("divider", 0xFF2B3039);
        def("panel", 0xE6191C22); def("panel_hover", 0xF021252D); def("panel_selected", 0xFF21252D);
        def("focus_outline", 0xFF18B4CC); def("dialog", 0xF5191C22); def("input", 0xFF21252D);
        def("slider_track", 0xFF2B3039); def("slider_knob", 0xFFFFFFFF);
        def("toggle_on", 0xFF18B4CC); def("toggle_off", 0xFF2B3039); def("toggle_knob_on", 0xFFFFFFFF); def("toggle_knob_off", 0xFF98A1AD);
        def("scrollbar_track", 0x80191C22); def("scrollbar_thumb", 0xFF3A414C);
        def("meter_track", 0xFF21252D); def("meter_level", 0xFF5F6874);
        def("hud_background", 0xFF111318); def("hud_border", 0xFF2B3039); def("wave_idle", 0xFF3A414C);
        def("tooltip", 0xF5111318);
    }

    private Theme() {}

    private static void def(String key, int argb) { DEFAULTS.put(key, argb); }

    public static int color(String key) {
        // the client's look first (the jar's theme.json only documents the voice defaults), then the overrides
        Integer value = overrides.get(key);
        int fallback = value != null ? value : DEFAULTS.getOrDefault(key, 0xFFFF00FF);
        dev.lumora.api.ui.Theme t = client;
        if (t == null) {
            return fallback;
        }
        try {
            Integer c = fromClient(t, key);
            // the client gives opaque colours: the alpha of the voice defaults stays (translucent panels, scrims)
            return c == null ? fallback : (fallback & 0xFF000000) | (c & 0x00FFFFFF);
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    /**
     * Follows the look the player chose in the Lumora hub (accent colour, surfaces, borders, text), so the voice
     * windows match the hub and the rest of the client. {@code null} goes back to the voice defaults.
     */
    public static void follow(dev.lumora.api.ui.Theme theme) {
        client = theme;
    }

    /** The client colour for a voice colour key, or null to keep the voice default. */
    static Integer fromClient(dev.lumora.api.ui.Theme t, String key) {
        return switch (key) {
            case "text", "accent_text", "slider_knob", "toggle_knob_on" -> t.text();
            case "text_muted", "toggle_knob_off" -> t.muted();
            case "text_subtle" -> dim(t.muted());
            case "accent", "focus_outline", "toggle_on" -> t.accent();
            case "accent_hover" -> t.accentHover();
            case "accent_dark" -> t.accentDark();
            case "ok" -> t.ok();
            case "error" -> t.error();
            case "warning" -> t.warning();
            case "background", "scrim_menu", "scrim_world", "hud_background", "tooltip" -> t.background();
            case "surface", "panel", "dialog" -> t.surface();
            case "panel_hover", "panel_selected", "input", "meter_track" -> t.surface2();
            case "border", "divider", "slider_track", "toggle_off", "hud_border", "scrollbar_thumb" -> t.border();
            case "border_hover", "wave_idle" -> mix(t.border(), t.muted());
            case "scrollbar_track" -> t.surface();
            case "meter_level" -> t.muted();
            default -> null;
        };
    }

    private static int dim(int rgb) {
        return mix(rgb, 0xFF000000);
    }

    /** Halfway between two colours. */
    static int mix(int a, int b) {
        int r = (((a >> 16) & 255) + ((b >> 16) & 255)) / 2;
        int g = (((a >> 8) & 255) + ((b >> 8) & 255)) / 2;
        int bl = ((a & 255) + (b & 255)) / 2;
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }

    /** Reads overrides from {@code source} (a theme.json stream); {@code null} or a broken file means the defaults. */
    public static void reload(Supplier<InputStream> source) {
        var parsed = new HashMap<String, Integer>();
        try (InputStream in = source == null ? null : source.get()) {
            if (in != null) {
                JsonObject root = new JsonParser().parse(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                JsonObject colors = root.has("colors") ? root.getAsJsonObject("colors") : root;
                for (Map.Entry<String, JsonElement> entry : colors.entrySet()) {
                    Integer color = entry.getValue().isJsonPrimitive() ? parse(entry.getValue().getAsString()) : null;
                    if (color != null) parsed.put(entry.getKey(), color);
                }
            }
        } catch (Exception broken) {
            parsed.clear(); // broken theme: default colors
        }
        overrides = Map.copyOf(parsed);
    }

    static Integer parse(String raw) {
        String value = raw.trim();
        if (value.startsWith("#")) value = value.substring(1);
        else if (value.startsWith("0x") || value.startsWith("0X")) value = value.substring(2);
        if (value.length() != 6 && value.length() != 8) return null;
        try {
            long argb = Long.parseLong(value, 16);
            if (value.length() == 6) argb |= 0xFF000000L;
            return (int) argb;
        } catch (NumberFormatException invalid) { return null; }
    }
}
