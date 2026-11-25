package dev.lumora.core.cosmetics.ui;

import dev.lumora.api.render.HudGraphics;
import dev.lumora.api.screen.AddonView;

/** What the cosmetics views need from the client. */
public interface UiHost {
    /** Translated text of a full language key. */
    String tr(String key, Object... args);

    String clipboard();

    void close(AddonView view);

    void show(AddonView view);

    /** Draws the own player as preview; {@code false} outside a world or where the version cannot. */
    boolean drawPreview(HudGraphics g, int x, int y, int width, int height, float yaw, float pitch, float zoom);

    /** {@code true} while playing in a world (emotes can be played). */
    boolean inWorld();

    /** Number of emote wheel slots per page (setting). */
    int wheelSlots();

    /** Number of emote wheel pages (setting). */
    default int wheelPages() {
        return 1;
    }

    /** {@code true} while the emote wheel key is physically held (also with the wheel open). */
    default boolean wheelKeyDown() {
        return false;
    }

    /** Opens the module's settings in the hub. */
    void openSettings();
}
