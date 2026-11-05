package dev.lumora.core.extras;

import dev.lumora.api.addon.AddonContext;
import dev.lumora.api.addon.LumoraAddon;
import dev.lumora.api.config.IntegerSetting;
import dev.lumora.api.event.client.ClientStoppingEvent;
import dev.lumora.api.event.client.ClientTickEvent;
import dev.lumora.api.input.Keys;
import dev.lumora.core.Platform;

/**
 * Built-in module "Fullbright": raises Minecraft's brightness option above its normal maximum while switched on, so
 * caves and nights are bright (client-side only, like a gamma setting). The player's own brightness is kept and put
 * back when the module or the key toggle turns it off and when the game closes.
 */
public final class FullbrightModule implements LumoraAddon {
    /** Vanilla's maximum ("Hell") is 1.0; above that Minecraft simply lights everything. */
    static final double MAX_VANILLA = 1.0;
    private final Platform platform;
    IntegerSetting level;
    private boolean on = true;
    private double original = Double.NaN;

    public FullbrightModule(Platform platform) {
        this.platform = platform;
    }

    @Override
    public void onInitialize(AddonContext context) {
        level = context.configuration().settings().slider("level", 10, 2, 15).name("lumora.extras.fullbright.level");
    }

    @Override
    public void onEnable(AddonContext context) {
        on = true;
        context.events().subscribe(ClientTickEvent.class, e -> tick());
        context.events().subscribe(ClientStoppingEvent.class, e -> restore());
        context.keybinds().register("toggle", "lumora.extras.fullbright.key", Keys.UNBOUND, () -> {
            on = !on;
            if (!on) {
                restore();
            }
        });
    }

    @Override
    public void onDisable(AddonContext context) {
        restore();
    }

    private void tick() {
        if (!on) {
            return;
        }
        GameExtras x = platform.extras();
        double now = x.gamma();
        if (Double.isNaN(original) && now <= MAX_VANILLA) {
            original = now; // the player's own brightness
        }
        double want = level.get();
        if (Math.abs(now - want) > 1e-6) {
            x.setGamma(want);
        }
    }

    private void restore() {
        GameExtras x = platform.extras();
        if (x.gamma() > MAX_VANILLA) {
            x.setGamma(Double.isNaN(original) ? 0.5 : original);
        }
        original = Double.NaN;
    }
}
