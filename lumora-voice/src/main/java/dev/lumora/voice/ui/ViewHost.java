package dev.lumora.voice.ui;

/** What a {@link View} needs from the screen that shows it. */
public interface ViewHost {
    /** Shows {@code view} on top of the current one; {@link #back()} returns to the current one. */
    void open(View view);

    /** Closes the current view (back to the previous view, or to the game/menu). */
    void back();

    boolean inWorld();

    String clipboard();

    /** Lumora HUD editor (position and size of the voice widget). */
    void openHudEditor();

    /** A short Lumora notification, e.g. after blocking someone or before a reset. */
    void notify(String message);

    /** Name of the key currently bound to push-to-talk (for the HUD and hints). */
    String pushToTalkKey();
}
