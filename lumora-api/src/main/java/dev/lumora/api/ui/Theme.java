package dev.lumora.api.ui;

import dev.lumora.api.Registration;

import java.util.List;

/**
 * The look the player chose in the Lumora hub ({@code AddonContext.theme()}): the accent colour and Lumora's surface
 * and text colours, read fresh on every call, so an addon follows a change at once. Use it for HUD widgets and
 * {@linkplain dev.lumora.api.screen.AddonView addon menus}, or let {@link UiKit} draw Lumora's controls with it.
 * <p>
 * {@link #addAccent} offers an own accent colour in the hub (permission {@code design}).
 *
 * @since 1.5.0
 */
public interface Theme {
    /** "lumora" (Lumora's menus) or "vanilla" (Minecraft's own menus and buttons). */
    String style();

    /** Id of the chosen accent, e.g. "purple", "blue" or {@code "<addon>:<id>"} for an addon's accent. */
    String accentId();

    int accent();

    /** Lighter accent for hovered elements. */
    int accentHover();

    /** Darker accent for pressed elements. */
    int accentDark();

    default int background() {
        return 0xFF070B14;
    }

    default int surface() {
        return 0xFF0D1424;
    }

    default int surface2() {
        return 0xFF16213A;
    }

    default int border() {
        return 0xFF22314F;
    }

    default int text() {
        return 0xFFE8EEFA;
    }

    default int muted() {
        return 0xFF8D9BB8;
    }

    default int ok() {
        return 0xFF3DD68C;
    }

    default int warning() {
        return 0xFFF5B342;
    }

    default int error() {
        return 0xFFF2555A;
    }

    /** The look of all HUD widgets the player set in the hub ("Widget-Design"); not active by default. @since 1.6.0 */
    default WidgetStyle widgetStyle() {
        return WidgetStyle.NONE;
    }

    /** An accent colour the player can choose. */
    record Accent(String id, String name, int color) {
    }

    /** All accent colours in the hub: Lumora's own, then those of addons. */
    List<Accent> accents();

    /**
     * Offers {@code color} as an accent in the hub (Themes page) under {@code name}; its id is {@code "<addon>:<id>"}.
     * Removed with the returned registration or when the addon is disabled (the hub then falls back to Lila; the
     * choice comes back when the addon is enabled again). Permission {@code design}.
     */
    Registration addAccent(String id, String name, int color);
}
