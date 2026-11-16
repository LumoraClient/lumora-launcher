package dev.lumora.core.perf;

import dev.lumora.api.addon.AddonContext;
import dev.lumora.api.addon.LumoraAddon;
import dev.lumora.api.config.BooleanSetting;
import dev.lumora.api.config.SettingsGroup;
import dev.lumora.api.event.client.ClientStoppingEvent;
import dev.lumora.core.Platform;

import java.util.EnumMap;
import java.util.Map;

/**
 * Built-in module "Performance": the switches of Lumora's performance engine. Every optimization the running Minecraft
 * version supports gets its own switch (all on by default); the others stay off and are listed with the reason under
 * "Status". Switching the module off turns every optimization off.
 * <p>
 * The startup optimization works before the module is loaded: the Lumora Loader reads this module's saved settings
 * ({@code config/lumora/addons.json} and {@code config/lumora/addons/lumora-performance.json}) at the next start.
 */
public final class PerformanceModule implements LumoraAddon {
    public static final String ID = "lumora-performance";
    static final String K = "lumora.performance.";

    private final Platform platform;
    private final Map<Optimization, BooleanSetting> switches = new EnumMap<>(Optimization.class);
    private boolean enabled;

    public PerformanceModule(Platform platform) {
        this.platform = platform;
        PerformanceEngine.declareSupported(platform.optimizations());
    }

    @Override
    public void onInitialize(AddonContext context) {
        SettingsGroup s = context.configuration().settings();
        for (Optimization o : Optimization.values()) {
            if (!PerformanceEngine.isSupported(o)) {
                continue;
            }
            BooleanSetting b = s.toggle(o.key(), true).name(K + o.key()).description(K + o.key() + ".description")
                    .section(K + "section");
            b.onChange(v -> apply(o));
            switches.put(o, b);
        }
        s.button("status", () -> context.notifications().info(context.translations().translate(K + "status.title"), status(context)))
                .name(K + "status").section(K + "section.status");
    }

    @Override
    public void onEnable(AddonContext context) {
        enabled = true;
        for (Optimization o : Optimization.values()) {
            apply(o);
        }
        context.events().subscribe(ClientStoppingEvent.class, e -> PerformanceEngine.allOff());
    }

    @Override
    public void onDisable(AddonContext context) {
        enabled = false;
        PerformanceEngine.allOff();
    }

    private void apply(Optimization o) {
        BooleanSetting b = switches.get(o);
        boolean on = enabled && b != null && b.get();
        PerformanceEngine.set(o, on);
    }

    /** One line per optimization: on, off or why not available; the culling numbers of the last pass. */
    String status(AddonContext context) {
        var tr = context.translations();
        StringBuilder sb = new StringBuilder();
        for (Optimization o : Optimization.values()) {
            String state;
            if (!PerformanceEngine.isSupported(o)) {
                String note = platform.optimizationNote(o);
                state = tr.translate(note != null ? note : K + "unsupported");
            } else {
                state = tr.translate(PerformanceEngine.isActive(o) ? K + "on" : K + "off");
            }
            sb.append(tr.translate(K + o.key())).append(": ").append(state).append('\n');
        }
        OcclusionCuller c = PerformanceEngine.culler();
        if (c != null && PerformanceEngine.entityCulling) {
            sb.append(tr.translate(K + "status.culling", c.lastHidden(), c.lastChecked(),
                    String.format(java.util.Locale.ROOT, "%.2f", c.lastPassNanos() / 1e6)));
        }
        return sb.toString().strip();
    }
}
