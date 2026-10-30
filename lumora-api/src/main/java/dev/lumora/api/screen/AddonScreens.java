package dev.lumora.api.screen;

/**
 * Opens addon menus (permission {@code screens}). Views stack: {@link #open} shows a view on top of the current
 * screen, {@link #back} or Escape returns to what was shown before.
 *
 * @since 1.1.0
 */
public interface AddonScreens {
    /** Shows {@code view} (client thread). */
    void open(AddonView view);

    /** Closes the topmost view this addon opened, if it is shown. */
    void back();

    /** Text in the system clipboard (empty if none). */
    String clipboard();

    /** Lumora hub settings of this addon: addon settings and key bindings. */
    void openAddonSettings();

    /**
     * The addon's main menu: the Lumora hub (Addons tab) shows an open button for it, so the menu is reachable even
     * when its key is unbound. {@code menu} creates a fresh view each time. Removed when the addon is disabled.
     */
    void registerMenu(java.util.function.Supplier<AddonView> menu);
}
