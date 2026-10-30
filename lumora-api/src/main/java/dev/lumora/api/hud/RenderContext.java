package dev.lumora.api.hud;

import dev.lumora.api.game.GameContext;
import dev.lumora.api.i18n.Translations;

/** Frame information for widgets. Valid only during the call that received it. */
public interface RenderContext {
    /** No live data available (editor opened from the main menu): draw sample content. */
    boolean preview();

    /** Drawn inside the HUD editor. */
    boolean editing();

    /** Fraction of the current tick (0..1) for smooth animations. */
    float partialTick();

    /** Screen size in GUI pixels (after the GUI scale). */
    int screenWidth();

    int screenHeight();

    /** Minecraft GUI scale (1..n). */
    int guiScale();

    /** Wall clock in milliseconds for animations; the same value for the whole frame. */
    long timeMillis();

    GameContext game();

    Translations translations();
}
