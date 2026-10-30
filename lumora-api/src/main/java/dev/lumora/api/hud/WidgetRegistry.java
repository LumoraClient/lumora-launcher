package dev.lumora.api.hud;

import dev.lumora.api.Registration;

import java.util.List;
import java.util.Optional;

/** Registration of HUD widgets. Registered widgets show up in the HUD editor automatically. */
public interface WidgetRegistry {
    /**
     * Registers a widget under {@code <addon>:<widget id>}. Throws {@link IllegalArgumentException} if that id is
     * taken or the instance is already registered. Its stored layout and settings are applied immediately.
     */
    Registration register(HudWidget widget);

    /** All registered widgets in render order (lowest layer first). */
    List<WidgetInfo> all();

    List<WidgetInfo> byCategory(String category);

    Optional<WidgetInfo> find(String fullId);
}
