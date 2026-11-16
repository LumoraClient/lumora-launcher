package dev.lumora.core.perf;

import dev.lumora.api.config.Setting;
import dev.lumora.core.LumoraCore;
import dev.lumora.core.hud.HudRenderer;
import dev.lumora.core.hud.WidgetEntry;
import dev.lumora.core.hud.WidgetState;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Performance bench only (smoke test with {@code -Dlumora.perfBench}): a busy HUD to measure "Smart HUD &amp; Menus"
 * and "Frame Optimization" on their own. Every widget is switched on and "Widget-Design" draws them with rounded
 * corners, a border and formatted labels; {@link #restore()} puts widgets and design back as they were.
 */
public final class HudBench {
    private static final String DESIGN = "lumora-widgetdesign";
    private final LumoraCore core;
    private final Map<String, WidgetState> savedStates = new LinkedHashMap<>();
    private final Map<String, String> savedDesign = new LinkedHashMap<>();
    private boolean designWasWanted;
    private boolean active;

    public HudBench(LumoraCore core) {
        this.core = core;
    }

    /** Switches every widget and the design on (remembering the previous state) and starts timing the HUD pass. */
    public void showAll() {
        if (active) {
            return;
        }
        active = true;
        savedStates.clear();
        for (WidgetEntry e : core.widgetRegistry().entries()) {
            savedStates.put(e.fullId(), e.state().copy());
            e.setEnabled(true);
            e.state().shown = true;
        }
        designWasWanted = core.addonManager().record(DESIGN).map(r -> r.wanted()).orElse(false);
        core.addonManager().setWanted(DESIGN, true);
        core.addonManager().record(DESIGN).ifPresent(r -> {
            var s = r.configuration().settings();
            for (Setting<?> setting : s.all()) {
                savedDesign.put(setting.key(), setting.encode());
            }
            set(s, "corners", "4");
            set(s, "border", "true");
            set(s, "format", "colon");
            set(s, "uppercase", "true");
        });
        HudRenderer.timing(true);
    }

    /** Clears what was measured (a new measurement begins). */
    public void restart() {
        HudRenderer.timing(true);
    }

    /** Average CPU time of one HUD pass in microseconds since the last {@link #restart()}. */
    public double micros() {
        return HudRenderer.timedMicros();
    }

    /** Widgets and design as before {@link #showAll()}; timing off. */
    public void restore() {
        HudRenderer.timing(false);
        if (!active) {
            return;
        }
        active = false;
        for (WidgetEntry e : List.copyOf(core.widgetRegistry().entries())) {
            WidgetState before = savedStates.get(e.fullId());
            if (before == null) {
                continue;
            }
            e.setEnabled(before.enabled);
            e.state().copyFrom(before);
        }
        core.addonManager().record(DESIGN).ifPresent(r -> {
            var s = r.configuration().settings();
            savedDesign.forEach((k, v) -> set(s, k, v));
        });
        core.addonManager().setWanted(DESIGN, designWasWanted);
        core.widgetRegistry().changed();
        core.widgetRegistry().saveNow();
    }

    private static void set(dev.lumora.api.config.SettingsGroup s, String key, String value) {
        s.get(key).ifPresent(setting -> setting.decode(value));
    }
}
