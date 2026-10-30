package dev.lumora.api.addon;

import dev.lumora.api.config.SettingsGroup;

/**
 * Addon-wide settings. Define them in {@link LumoraAddon#onInitialize}; stored values are applied right after
 * that, changes are saved automatically (debounced, off the render thread). The Lumora hub shows them under
 * "Addons".
 */
public interface AddonConfiguration {
    SettingsGroup settings();

    /** Requests a save now (still asynchronous). Normally not needed. */
    void save();
}
