package dev.lumora.client.hud;

import dev.lumora.api.addon.AddonContext;
import dev.lumora.api.addon.LumoraAddon;
import dev.lumora.api.hud.HudWidget;

/**
 * Built-in addon "lumora": registers the Lumora HUD widgets through the same API as every other addon. Switching it
 * off in the hub removes them (their layout is kept).
 */
public final class BuiltInHudAddon implements LumoraAddon {
    @Override
    public void onEnable(AddonContext context) {
        for (HudWidget w : BuiltInWidgets.create()) {
            context.widgets().register(w);
        }
    }
}
