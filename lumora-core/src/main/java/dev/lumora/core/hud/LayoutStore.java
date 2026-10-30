package dev.lumora.core.hud;

import dev.lumora.core.io.Json;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.lumora.core.CoreLogger;
import dev.lumora.core.io.AtomicFiles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Reads and writes {@link HudLayout}. Robust by design: every widget entry is parsed on its own (a broken entry
 * is skipped, a broken value falls back to its default), a file that is no JSON at all is moved aside and the
 * defaults are used. Schema 1 is the old {@code config/lumora-hud.json} of the built-in modules.
 */
public final class LayoutStore {
    public static final int SCHEMA = 2;
    /** Built-in widgets of the Lumora client are registered by this addon id. */
    public static final String BUILT_IN_ADDON = "lumora";

    private LayoutStore() {
    }

    /** Loads the layout; migrates the legacy file if there is no new one yet. Never throws. */
    public static HudLayout load(Path file, Path legacy, CoreLogger log) {
        if (Files.isRegularFile(file)) {
            try {
                HudLayout l = parse(Files.readString(file, StandardCharsets.UTF_8), log);
                if (l != null) {
                    return l;
                }
            } catch (IOException | RuntimeException e) {
                log.warn("Could not read " + file + ": " + e.getMessage());
            }
            Path backup = AtomicFiles.backup(file, "corrupt");
            log.warn("HUD layout " + file + " is broken; using defaults (backup: " + backup + ")");
            return new HudLayout();
        }
        if (legacy != null && Files.isRegularFile(legacy)) {
            try {
                HudLayout l = migrateLegacy(Json.parse(Files.readString(legacy, StandardCharsets.UTF_8)).getAsJsonObject());
                log.info("Migrated " + legacy + " to the new HUD layout (" + l.active().widgets.size() + " widgets)");
                return l;
            } catch (IOException | RuntimeException e) {
                log.warn("Could not migrate " + legacy + ": " + e.getMessage());
            }
        }
        return new HudLayout();
    }

    /** Parses a layout; {@code null} if the text is not a JSON object. */
    public static HudLayout parse(String json, CoreLogger log) {
        JsonElement root;
        try {
            root = Json.parse(json);
        } catch (RuntimeException e) {
            return null;
        }
        if (!root.isJsonObject()) {
            return null;
        }
        JsonObject o = root.getAsJsonObject();
        int schema = integer(o.get("schema"), SCHEMA);
        if (schema == 1 || o.has("modules")) {
            return migrateLegacy(o);
        }
        if (schema > SCHEMA) {
            log.warn("HUD layout was written by a newer Lumora (schema " + schema + "); unknown fields are ignored");
        }
        HudLayout l = new HudLayout();
        l.grid = bool(o.get("grid"), false);
        l.gridSize = Math.max(2, Math.min(32, integer(o.get("gridSize"), 8)));
        l.snap = bool(o.get("snap"), true);
        if (o.get("profiles") instanceof JsonObject profiles) {
            for (Map.Entry<String, JsonElement> pe : profiles.entrySet()) {
                if (!HudLayout.validProfileId(pe.getKey()) || !pe.getValue().isJsonObject()) {
                    log.warn("Skipping invalid HUD profile " + pe.getKey());
                    continue;
                }
                l.profiles.put(pe.getKey(), profile(pe.getValue().getAsJsonObject(), pe.getKey(), log));
            }
        }
        l.profiles.computeIfAbsent(HudLayout.DEFAULT, k -> new HudProfile());
        String active = string(o.get("activeProfile"), HudLayout.DEFAULT);
        l.activeProfile = l.profiles.containsKey(active) ? active : HudLayout.DEFAULT;
        return l;
    }

    private static HudProfile profile(JsonObject o, String name, CoreLogger log) {
        HudProfile p = new HudProfile();
        if (!(o.get("widgets") instanceof JsonObject widgets)) {
            return p;
        }
        for (Map.Entry<String, JsonElement> we : widgets.entrySet()) {
            String id = we.getKey();
            if (!id.matches("[a-z0-9_-]{1,64}:[a-z0-9_-]{1,48}") || !we.getValue().isJsonObject()) {
                log.warn("Skipping invalid widget entry " + id + " in HUD profile " + name);
                continue;
            }
            p.widgets.put(id, widget(we.getValue().getAsJsonObject()));
        }
        return p;
    }

    private static WidgetState widget(JsonObject o) {
        WidgetState s = new WidgetState();
        s.enabled = bool(o.get("enabled"), false);
        s.shown = bool(o.get("shown"), true);
        s.x = fraction(o.get("x"), 0f);
        s.y = fraction(o.get("y"), 0f);
        s.scale = Math.max(0.25f, Math.min(4f, decimal(o.get("scale"), 1f)));
        s.layer = Math.max(-1000, Math.min(1000, integer(o.get("layer"), 0)));
        if (o.get("settings") instanceof JsonObject settings) {
            for (Map.Entry<String, JsonElement> e : settings.entrySet()) {
                if (e.getValue() instanceof JsonPrimitive prim) {
                    s.settings.put(e.getKey(), prim.getAsString());
                }
            }
        }
        return s;
    }

    /** Old format: {@code {"modules": {"fps": {enabled, x, y, scale}}, "background": true, "accent": true}}. */
    public static HudLayout migrateLegacy(JsonObject o) {
        HudLayout l = new HudLayout();
        boolean background = bool(o.get("background"), true);
        boolean accent = bool(o.get("accent"), true);
        if (o.get("modules") instanceof JsonObject modules) {
            for (Map.Entry<String, JsonElement> e : modules.entrySet()) {
                if (!e.getKey().matches("[a-z0-9_-]{1,48}") || !(e.getValue() instanceof JsonObject m)) {
                    continue;
                }
                WidgetState s = new WidgetState();
                s.enabled = bool(m.get("enabled"), false);
                s.x = fraction(m.get("x"), 0f);
                s.y = fraction(m.get("y"), 0f);
                s.scale = Math.max(0.25f, Math.min(4f, decimal(m.get("scale"), 1f)));
                s.settings.put("background", Boolean.toString(background));
                s.settings.put("accent", Boolean.toString(accent));
                l.active().widgets.put(BUILT_IN_ADDON + ":" + e.getKey(), s);
            }
        }
        return l;
    }

    public static String toJson(HudLayout l) {
        JsonObject o = new JsonObject();
        o.addProperty("schema", SCHEMA);
        o.addProperty("activeProfile", l.activeProfile);
        o.addProperty("grid", l.grid);
        o.addProperty("gridSize", l.gridSize);
        o.addProperty("snap", l.snap);
        JsonObject profiles = new JsonObject();
        for (Map.Entry<String, HudProfile> pe : l.profiles.entrySet()) {
            JsonObject widgets = new JsonObject();
            for (Map.Entry<String, WidgetState> we : pe.getValue().widgets.entrySet()) {
                WidgetState s = we.getValue();
                JsonObject w = new JsonObject();
                w.addProperty("enabled", s.enabled);
                w.addProperty("shown", s.shown);
                w.addProperty("x", round(s.x));
                w.addProperty("y", round(s.y));
                w.addProperty("scale", round(s.scale));
                w.addProperty("layer", s.layer);
                if (!s.settings.isEmpty()) {
                    JsonObject settings = new JsonObject();
                    for (Map.Entry<String, String> se : s.settings.entrySet()) {
                        settings.addProperty(se.getKey(), se.getValue());
                    }
                    w.add("settings", settings);
                }
                widgets.add(we.getKey(), w);
            }
            JsonObject p = new JsonObject();
            p.add("widgets", widgets);
            profiles.add(pe.getKey(), p);
        }
        o.add("profiles", profiles);
        return new GsonBuilder().setPrettyPrinting().create().toJson(o);
    }

    private static float round(float v) {
        return Math.round(v * 10000f) / 10000f;
    }

    private static boolean bool(JsonElement e, boolean fallback) {
        if (e instanceof JsonPrimitive p && p.isBoolean()) {
            return p.getAsBoolean();
        }
        return fallback;
    }

    private static int integer(JsonElement e, int fallback) {
        if (e instanceof JsonPrimitive p && p.isNumber()) {
            double d = p.getAsDouble();
            return Double.isFinite(d) ? (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, d)) : fallback;
        }
        return fallback;
    }

    private static float decimal(JsonElement e, float fallback) {
        if (e instanceof JsonPrimitive p && p.isNumber()) {
            float f = p.getAsFloat();
            return Float.isFinite(f) ? f : fallback;
        }
        return fallback;
    }

    private static float fraction(JsonElement e, float fallback) {
        return Math.max(0f, Math.min(1f, decimal(e, fallback)));
    }

    private static String string(JsonElement e, String fallback) {
        if (e instanceof JsonPrimitive p && p.isString()) {
            return p.getAsString();
        }
        return fallback;
    }
}
