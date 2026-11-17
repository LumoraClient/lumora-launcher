package dev.lumora.client.diagnostics;

import dev.lumora.api.addon.AddonInfo;
import dev.lumora.api.addon.AddonState;
import dev.lumora.api.hud.WidgetInfo;
import dev.lumora.client.api.LumoraRuntime;
import dev.lumora.core.LumoraCore;
import dev.lumora.core.hud.Bounds;
import dev.lumora.core.hud.HudEditorModel;
import dev.lumora.core.hud.WidgetEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger LOG = LoggerFactory.getLogger("Lumora");
    /** Widgets that must exist when their mod is installed. */
    private static final Map<String, String> EXPECTED = Map.of("lumoravoice", "lumoravoice:voice", "labyvoice", "labyvoice:voice",
            "lumora-session-timer", "session-timer:timer");
    private static final String LUMORA_VOICE = "dev.lumora.voice.lumora.LumoraVoiceAddon";
    private static boolean lumoraVoiceSwitched;

    private ApiSmoke() {
    }

    /** LumoraVoice's addon class: from its jar in addons/ (own class loader) or, for the old Fabric mod, the game. */
    static Class<?> voiceClass() throws ClassNotFoundException {
        java.util.Optional<ClassLoader> jar = core().jarAddons().classLoader("lumoravoice");
        return jar.isPresent() ? Class.forName(LUMORA_VOICE, true, jar.get()) : Class.forName(LUMORA_VOICE);
    }

    /** Whether LumoraVoice is there, as addon jar or Fabric mod. */
    static boolean voiceLoaded() {
        return core().jarAddons().classLoader("lumoravoice").isPresent() || dev.lumora.client.platform.LoaderEnv.isLoaded("lumoravoice");
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

    /** Picks up the test widget and holds it over the left middle zone (the screenshot shows the drag overlay). */
    static void dragInEditor(HudEditorModel model, int width, int height) {
        Optional<WidgetEntry> found = core().widgetRegistry().entry(testWidget());
        if (found.isEmpty() || !found.get().bounds().valid) {
            return;
        }
        Bounds b = found.get().bounds();
        double cx = b.x + b.width / 2.0;
        double cy = b.y + b.height / 2.0;
        model.press(cx, cy, 0, false);
        model.drag(cx + 5, cy + 5, false);
        model.drag(width * 0.2, height * 0.55, false);
        LOG.info("LUMORA_SMOKE_DRAG zone={} magnets={} guides={}/{}", model.dragZone(), model.magnets().size(), model.guidesX(), model.guidesY());
    }

    /** Position of the test widget before {@link #trashInEditor} (x, y fractions), or null. */
    private static float[] beforeTrash;

    /** Picks up the test widget and holds it over the bin in the middle (the screenshot shows the red bin). */
    static void trashInEditor(HudEditorModel model, int width, int height) {
        Optional<WidgetEntry> found = core().widgetRegistry().entry(testWidget());
        if (found.isEmpty() || !found.get().bounds().valid || !found.get().isEnabled()) {
            LOG.warn("LUMORA_SMOKE_TRASH: test widget not in the HUD, skipped");
            return;
        }
        WidgetEntry e = found.get();
        beforeTrash = new float[]{e.x(), e.y()};
        Bounds b = e.bounds();
        double cx = b.x + b.width / 2.0;
        double cy = b.y + b.height / 2.0;
        model.press(cx, cy, 0, false);
        model.drag(cx + 5, cy + 5, false);
        model.drag(width / 2.0, height / 2.0, false);
        LOG.info("LUMORA_SMOKE_TRASH over={}", model.overTrash());
    }

    /**
     * Drops the held widget on the bin: it must leave the HUD; then a click in the side list ({@code add}) puts it back
     * where it was.
     */
    static void trashCheck(HudEditorModel model, List<String> problems) {
        Optional<WidgetEntry> found = core().widgetRegistry().entry(testWidget());
        if (found.isEmpty() || beforeTrash == null) {
            return;
        }
        WidgetEntry e = found.get();
        boolean over = model.overTrash();
        model.release();
        boolean removed = !e.isEnabled();
        model.add(e);
        boolean back = e.isEnabled() && Math.abs(e.x() - beforeTrash[0]) < 1e-4 && Math.abs(e.y() - beforeTrash[1]) < 1e-4;
        model.save();
        LOG.info("LUMORA_SMOKE_TRASH over={} removed={} backAtOldPlace={} x={} y={}", over, removed, back, fmt(e.x()), fmt(e.y()));
        if (!over || !removed) {
            problems.add("hud editor: the widget dropped on the bin is still in the HUD (over=" + over + ")");
        }
        if (!back) {
            problems.add("hud editor: adding the widget again did not put it back where it was");
        }
        beforeTrash = null;
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
            // a built-in module for newer Minecraft versions (elytra trails on 1.8.9) is locked on purpose
            if (a.state() == AddonState.FAILED && !(a.descriptor().builtIn()
                    && dev.lumora.core.addon.VersionSupport.check(a.descriptor(), core.minecraftVersion()) != null)) {
                problems.add("addon " + a.id() + " failed: " + a.error().orElse("?"));
            }
        }
        long builtIn = core.widgets().stream().filter(w -> w.addonId().equals("lumora")).count();
        // the armor pieces are widgets of their own module "Armor HUD" (lumora-armor)
        if (builtIn != 8) {
            problems.add("expected 8 built-in widgets, found " + builtIn);
        }
        if (core.addon("lumora-armor").map(AddonInfo::state).orElse(null) == AddonState.ENABLED
                && core.widgets().stream().filter(w -> w.addonId().equals("lumora-armor")).count() != 5) {
            problems.add("Armor HUD: expected 5 widgets (helmet, chestplate, leggings, boots, held item)");
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
            Object lumoraVoice = voiceClass().getMethod("smokeSetEnabled", boolean.class).invoke(null, on);
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
                voiceClass().getMethod("smokeRestore", Boolean.class).invoke(null, previous);
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
            return (Boolean) voiceClass().getMethod("smokeOpenSettings", int.class).invoke(null, tab);
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
        // Lumora API 1.7: a line above and below the name of every preview player
        core().nameTags().scoped("smoke", tracker, () -> { }).register(new dev.lumora.api.render.NameTags.Decorator() {
            @Override
            public java.util.Optional<dev.lumora.api.render.NameTags.Icon> icon(java.util.UUID id, dev.lumora.api.render.NameTags.Place place) {
                return java.util.Optional.empty();
            }

            @Override
            public java.util.Optional<dev.lumora.api.render.NameTags.Lines> lines(java.util.UUID id) {
                return states.containsKey(id)
                        ? java.util.Optional.of(new dev.lumora.api.render.NameTags.Lines("💵 1.234 €", "💀 250 €"))
                        : java.util.Optional.empty();
            }
        });
        return tracker;
    }

    private static String fmt(float v) {
        return String.format(Locale.ROOT, "%.3f", v);
    }

    // ------------------------------------------------------------------ Hidden Armor

    private static java.util.Map<String, String> armorSettingsBefore;
    private static boolean armorWantedBefore;

    /**
     * Hidden Armor on for the preview players: helmet 0 %, chestplate 40 %, leggings 100 %, boots 0 %, for everyone
     * (the second preview player was just hit and shows all of it). The previous state comes back in
     * {@link #hiddenArmorDone}.
     */
    static void hiddenArmorPreview() {
        var manager = core().addonManager();
        armorWantedBefore = manager.record(dev.lumora.core.extras.HiddenArmorModule.ID).map(r -> r.wanted()).orElse(false);
        manager.setWanted(dev.lumora.core.extras.HiddenArmorModule.ID, true);
        armorSettingsBefore = new java.util.LinkedHashMap<>();
        manager.record(dev.lumora.core.extras.HiddenArmorModule.ID).filter(r -> r.configuration() != null).ifPresent(r -> {
            var s = r.configuration().settings();
            for (var setting : s.all()) {
                armorSettingsBefore.put(setting.key(), setting.encode());
            }
            for (String[] kv : new String[][]{{"same_for_all", "false"}, {"helmet", "0"}, {"chestplate", "40"},
                    {"leggings", "100"}, {"boots", "0"}, {"applies_to", "everyone"}, {"show_on_hit", "true"}}) {
                s.get(kv[0]).ifPresent(x -> x.decode(kv[1]));
            }
        });
    }

    /** ", hidden armor" when armor pieces were drawn hidden or translucent; puts the module back as it was. */
    static String hiddenArmorDone(java.util.List<String> problems) {
        var m = dev.lumora.core.extras.HiddenArmorModule.active();
        int changed = m == null ? 0 : m.changedCount();
        LOG.info("LUMORA_HIDDEN_ARMOR pieces drawn hidden or translucent: {}", changed);
        var manager = core().addonManager();
        manager.record(dev.lumora.core.extras.HiddenArmorModule.ID).filter(r -> r.configuration() != null).ifPresent(r -> {
            var s = r.configuration().settings();
            if (armorSettingsBefore != null) {
                armorSettingsBefore.forEach((k, v) -> s.get(k).ifPresent(x -> x.decode(v)));
            }
        });
        manager.setWanted(dev.lumora.core.extras.HiddenArmorModule.ID, armorWantedBefore);
        if (changed == 0) {
            problems.add("hidden armor drew no armor piece hidden or translucent");
            return "";
        }
        return ", hidden armor";
    }
}
