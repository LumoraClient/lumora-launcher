package dev.lumora.api.hud;

import java.util.Optional;

/** State of a registered widget in the active layout profile. Setters save the layout (debounced). */
public interface WidgetInfo {
    /** Global id {@code addon:widget}. */
    String id();

    String addonId();

    HudWidget widget();

    String category();

    /** Switched on (ticks and draws). */
    boolean isEnabled();

    void setEnabled(boolean enabled);

    /** Shown; a hidden widget stays enabled (keeps ticking) but is not drawn. */
    boolean isShown();

    void setShown(boolean shown);

    /** 0..1 of the free horizontal screen space. */
    float x();

    float y();

    float scale();

    int layer();

    /** Error if the widget was switched off because it threw while drawing or ticking. */
    Optional<String> failure();
}
