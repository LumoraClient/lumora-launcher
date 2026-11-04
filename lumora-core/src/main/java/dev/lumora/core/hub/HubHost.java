package dev.lumora.core.hub;

import dev.lumora.api.config.SettingsGroup;
import dev.lumora.api.input.Keybind;

import java.nio.file.Path;
import java.util.List;

/**
 * What the Lumora hub ({@link HubView}) needs from the Minecraft version it runs in: the client settings
 * (config/lumora-client.json), the version's own screens and system information. Called on the client thread.
 */
public interface HubHost {
    /** Short line under the brand, e.g. "26.3 · Java 25". */
    String versionLine();

    /** A world is open (the hub is drawn over the game). */
    boolean inWorld();

    /** Whether the client setting exists in this version (field name in lumora-client.json). */
    boolean has(String option);

    /** Value of a client setting (Boolean, Integer, Double or String), or null if it does not exist. */
    Object get(String option);

    /** Changes a client setting, saves the file and applies the change (font, accent, theme). */
    void set(String option, Object value);

    /** Lumora's own font can be used (not on versions with bitmap fonts only). */
    boolean fontSupported();

    /** Name of the key bound to zoom. */
    String zoomKey();

    /** Opens the HUD editor; it returns to the hub. */
    void openHudEditor();

    /** Opens Lumora's settings view for a group of settings and keys ({@code hudEditor}: with the HUD editor button); it returns to the hub. */
    void openSettings(String title, String description, SettingsGroup settings, List<Keybind> keybinds, Runnable onClose,
                      boolean hudEditor);

    /**
     * Like {@link #openSettings(String, String, SettingsGroup, List, Runnable, boolean)} with sections below the own
     * settings (e.g. the HUD widgets of an addon, each after a divider). The version's view is created synchronously
     * inside the call and takes the sections over.
     */
    default void openSettings(String title, String description, SettingsGroup settings, List<Keybind> keybinds,
                              List<SettingsView.Section> sections, Runnable onClose, boolean hudEditor) {
        SettingsView.nextSections(sections);
        try {
            openSettings(title, description, settings, keybinds, onClose, hudEditor);
        } finally {
            SettingsView.nextSections(List.of());
        }
    }

    /** Minecraft's own title screen is used until the game restarts. */
    boolean classicMenuUntilRestart();

    /** Switches between Lumora's main menu and Minecraft's (until restart) and shows it (only without a world). */
    void showMainMenu(boolean lumora);

    /** Renderer line (backend, Sodium) and GPU name. */
    String renderer();

    String gpu();

    /** Protocol line (ViaFabricPlus target), or null without ViaFabricPlus. */
    String protocol();

    /** Opens ViaFabricPlus' protocol selection; false if it is not available. */
    boolean openProtocol();

    /** Starts the benchmark (the hub closes). */
    void startBenchmark();

    /** Writes the diagnostics file; returns its file name. */
    String writeDiagnostics();

    Path diagnosticsDirectory();

    /** Opens a folder in the system file manager. */
    void openFolder(Path dir) throws java.io.IOException;

    /** Closes the hub (returns to the screen it was opened from). */
    void close();
}
