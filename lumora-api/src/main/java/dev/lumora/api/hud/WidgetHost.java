package dev.lumora.api.hud;

import dev.lumora.api.i18n.Translations;

/** Internal: what the Lumora core provides to a registered widget. Addons do not implement this. */
public interface WidgetHost {
    String fullId();

    String addonId();

    Translations translations();
}
