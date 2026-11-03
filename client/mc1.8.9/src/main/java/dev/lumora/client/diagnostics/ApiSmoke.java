package dev.lumora.client.diagnostics;

import dev.lumora.api.addon.AddonInfo;
import dev.lumora.api.addon.AddonState;
import dev.lumora.api.hud.WidgetInfo;
import dev.lumora.client.api.LumoraRuntime;
import dev.lumora.core.LumoraCore;
import dev.lumora.core.hud.Bounds;
import dev.lumora.core.hud.HudEditorModel;
import dev.lumora.core.hud.WidgetEntry;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Smoke-test checks of the Lumora API in the running game: addons and widgets registered, a widget moved through
 * the real editor logic and saved (the next run logs the restored position: {@code LUMORA_SMOKE_LAYOUT}), and the
 * LabyVoice HUD switched on for the in-game screenshot.
 */
final class ApiSmoke {
    private static final Logger LOG = LogManager.getLogger("Lumora");
    /** Widgets that must exist when their mod is installed. */
    private static final Map<String, String> EXPECTED = Map.of("lumoravoice", "lumoravoice:voice", "labyvoice", "labyvoice:voice",
            "lumora-session-timer", "session-timer:timer");
    private static final String LUMORA_VOICE = "dev.lumora.voice.lumora.LumoraVoiceAddon";
    private static boolean lumoraVoiceSwitched;

    private ApiSmoke() {
    }

    static LumoraCore core() {
        return LumoraRuntime.core();
    }

    /** The widget used for the move test: LumoraVoice or LabyVoice if installed, else the FPS widget. */
    static String testWidget() {
        for (String id : List.of("lumoravoice:voice", "labyvoice:voice")) {
            if (core().widgetRegistry().entry(id).isPresent()) {
                return id;
            }
        }
        return "lumora:fps";
    }

    /** Logs the position as loaded from the layout file (compare with the last run's "saved" line). */
    static void logLoaded() {
        core().widgetRegistry().entry(testWidget()).ifPresent(e -> LOG.info("LUMORA_SMOKE_LAYOUT loaded {} x={} y={} scale={}",
                e.id(), fmt(e.x()), fmt(e.y()), fmt(e.scale())));
    }

    /** Selects the test widget in the editor, drags it to the other half of the screen with the editor logic and saves. */
    static void moveInEditor(HudEditorModel model) {
        Optional<WidgetEntry> found = core().widgetRegistry().entry(testWidget());
        if (found.isEmpty() || !found.get().bounds().valid) {
            LOG.warn("LUMORA_SMOKE_LAYOUT: test widget not laid out, move skipped");
            return;
        }
        WidgetEntry e = found.get();
        Bounds b = e.bounds();
        double cx = b.x + b.width / 2.0;
        double cy = b.y + b.height / 2.0;
        double dx = e.x() < 0.5f ? 160 : -160;
        model.press(cx, cy, 0, false);
        model.drag(cx + dx / 2, cy - 20, true);
        model.drag(cx + dx, cy - 40, true);
        model.release();
        model.save();
        LOG.info("LUMORA_SMOKE_LAYOUT saved {} x={} y={} scale={} (moved {} px)", e.id(), fmt(e.x()), fmt(e.y()), fmt(e.scale()), (int) dx);
    }

    /** Problems found, empty = OK. */
    static List<String> check() {
        List<String> problems = new ArrayList<>();
        LumoraCore core = core();
        for (Map.Entry<String, String> x : EXPECTED.entrySet()) {
            if (dev.lumora.client.platform.LoaderEnv.isLoaded(x.getKey()) && core.widgetRegistry().entry(x.getValue()).isEmpty()) {
                problems.add(x.getKey() + " is installed but widget " + x.getValue() + " is not registered");
            }
        }
        for (AddonInfo a : core.addons()) {
            if (a.state() == AddonState.FAILED) {
                problems.add("addon " + a.id() + " failed: " + a.error().orElse("?"));
            }
        }
        long builtIn = core.widgets().stream().filter(w -> w.addonId().equals("lumora")).count();
        if (builtIn != 9) {
            problems.add("expected 9 built-in widgets, found " + builtIn);
        }
        return problems;
    }

    static Map<String, Object> report() {
        Map<String, Object> out = new LinkedHashMap<>();
        LumoraCore core = core();
        out.put("apiVersion", core.apiVersion());
        out.put("profile", core.hudEditor().activeProfile());
        List<String> addons = new ArrayList<>();
        for (AddonInfo a : core.addons()) {
            addons.add(a.id() + " " + a.descriptor().version() + " " + a.state() + a.error().map(e -> " (" + e + ")").orElse(""));
        }
        out.put("addons", addons);
        List<String> widgets = new ArrayList<>();
        for (WidgetInfo w : core.widgets()) {
            widgets.add(w.id() + (w.isEnabled() ? " on" : " off") + " x=" + fmt(w.x()) + " y=" + fmt(w.y()) + " scale=" + fmt(w.scale())
                    + w.failure().map(f -> " FAILED " + f).orElse(""));
        }
        out.put("widgets", widgets);
        out.put("problems", check());
        return out;
    }

    /**
     * Switches LabyVoice's HUD on (VoiceChat enabled, nothing connects in singleplayer) via reflection - the
     * client does not depend on LabyVoice. Returns the previous value, or {@code null} if LabyVoice is absent.
     */
    static Boolean setLabyVoiceEnabled(boolean on) {
        try {
            Object lumoraVoice = Class.forName(LUMORA_VOICE).getMethod("smokeSetEnabled", boolean.class).invoke(null, on);
            if (lumoraVoice != null) {
                lumoraVoiceSwitched = true;
                return (Boolean) lumoraVoice;
            }
        } catch (ReflectiveOperationException | RuntimeException absent) {
            // no LumoraVoice: try LabyVoice
        }
        try {
            Object cfg = Class.forName("de.labyvoice.LabyVoice").getField("CONFIG").get(null);
            var field = cfg.getClass().getField("enabled");
            boolean previous = field.getBoolean(cfg);
            field.setBoolean(cfg, on);
            return previous;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /** Restores the LabyVoice setting and saves its config like LabyVoice itself does. */
    static void restoreLabyVoice(Boolean previous) {
        if (previous == null) {
            return;
        }
        if (lumoraVoiceSwitched) {
            try {
                Class.forName(LUMORA_VOICE).getMethod("smokeRestore", Boolean.class).invoke(null, previous);
            } catch (ReflectiveOperationException | RuntimeException e) {
                LOG.warn("Smoke test: could not restore the LumoraVoice setting", e);
            }
            return;
        }
        try {
            Object cfg = Class.forName("de.labyvoice.LabyVoice").getField("CONFIG").get(null);
            cfg.getClass().getField("enabled").setBoolean(cfg, previous);
            cfg.getClass().getMethod("save").invoke(cfg);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOG.warn("Smoke test: could not restore the LabyVoice setting", e);
        }
    }

    /** Opens the LumoraVoice settings on {@code tab} through the Lumora screen API; false without LumoraVoice. */
    static boolean openVoiceSettings(int tab) {
        try {
            return (Boolean) Class.forName(LUMORA_VOICE).getMethod("smokeOpenSettings", int.class).invoke(null, tab);
        } catch (ReflectiveOperationException | RuntimeException absent) {
            return false;
        }
    }

    /**
     * Name tag icons for the preview players: the LumoraVoice glyphs (idle, speaking, muted) through the real
     * NameTags registry and mixins, without a voice connection. Close the tracker to remove the decorator.
     */
    static dev.lumora.core.RegistrationTracker nameTagPreview(Map<java.util.UUID, Integer> states) {
        var tracker = new dev.lumora.core.RegistrationTracker();
        core().nameTags().scoped("smoke", tracker, () -> { }).register((id, place) -> {
            Integer state = states.get(id);
            if (state == null) {
                return java.util.Optional.empty();
            }
            String glyph = state == 2 ? "" : "";
            int color = state == 1 ? 0x65DD85 : 0x9CA5AD;
            return java.util.Optional.of(new dev.lumora.api.render.NameTags.Icon("lumoravoice:nametags", glyph, color, "*",
                    place == dev.lumora.api.render.NameTags.Place.WORLD && state == 1 ? "[Bauteam]" : ""));
        });
        return tracker;
    }

    private static String fmt(float v) {
        return String.format(Locale.ROOT, "%.3f", v);
    }
}
