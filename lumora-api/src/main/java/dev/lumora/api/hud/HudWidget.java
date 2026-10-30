package dev.lumora.api.hud;

import dev.lumora.api.config.SettingsGroup;
import dev.lumora.api.i18n.Translations;
import dev.lumora.api.render.HudGraphics;

/**
 * Base class of a HUD widget. Register an instance with {@link WidgetRegistry#register}; the HUD editor lists it
 * automatically, and Lumora takes care of position, scale, layer, on/off state, per-profile storage and the
 * settings screen.
 * <p>
 * A widget draws itself at (0, 0) in its own coordinate space of {@link #width} x {@link #height} pixels; Lumora
 * moves and scales it. Keep {@link #render} cheap: no file or network access, no searching, no big allocations
 * per frame. Collect data in {@link #tick()} (20x per second) or event listeners and only draw it here.
 * <p>
 * Lifecycle (client thread): {@link #configure} once on registration, stored settings are applied,
 * {@link #initialize}, then {@link #onEnable}/{@link #onDisable} whenever the user switches it on or off, and
 * {@link #dispose} after it was unregistered (also when its addon is disabled).
 */
public abstract class HudWidget {
    private final String id;
    private volatile WidgetHost host;
    private volatile SettingsGroup settings;

    /** @param id local id, unique within the addon: {@code [a-z0-9_-]}, 1-48 characters */
    protected HudWidget(String id) {
        if (id == null || !id.matches("[a-z0-9_-]{1,48}")) {
            throw new IllegalArgumentException("invalid widget id: " + id);
        }
        this.id = id;
    }

    /** Local id. */
    public final String id() {
        return id;
    }

    /** Global id {@code addon:widget} (known after registration). */
    public final String fullId() {
        WidgetHost h = host;
        return h != null ? h.fullId() : id;
    }

    /** Id of the owning addon (known after registration). */
    public final String addonId() {
        WidgetHost h = host;
        return h != null ? h.addonId() : "";
    }

    /** This widget's settings; available from {@link #configure} on. */
    public final SettingsGroup settings() {
        SettingsGroup s = settings;
        if (s == null) {
            throw new IllegalStateException("widget " + id + " is not registered");
        }
        return s;
    }

    /** Internal: connects the widget to Lumora on registration. A widget instance can be registered once. */
    public final synchronized void attach(WidgetHost host, SettingsGroup settings) {
        if (this.host != null) {
            throw new IllegalStateException("widget " + id + " is already registered as " + this.host.fullId());
        }
        this.host = host;
        this.settings = settings;
    }

    public final boolean isAttached() {
        return host != null;
    }

    protected final Translations translations() {
        WidgetHost h = host;
        if (h == null) {
            throw new IllegalStateException("widget " + id + " is not registered");
        }
        return h.translations();
    }

    /** Translation key of the name shown in the editor (default {@code <addon>.widget.<id>}). */
    public String nameKey() {
        return addonId() + ".widget." + id;
    }

    /** Translation key of the description (default name key + ".description"; optional). */
    public String descriptionKey() {
        return nameKey() + ".description";
    }

    /** Category for grouping in the editor, see {@link WidgetCategory}. */
    public String category() {
        return WidgetCategory.GENERAL;
    }

    /** Where the widget goes when it is first added or reset. */
    public WidgetPlacement defaultPlacement() {
        return WidgetPlacement.at(0.01f, 0.01f);
    }

    public float minScale() {
        return 0.5f;
    }

    public float maxScale() {
        return 3.0f;
    }

    /** Default drawing order; higher layers are drawn on top. */
    public int defaultLayer() {
        return 0;
    }

    /** Describe the settings here ({@code settings.toggle(...)}, ...). Called once, before {@link #initialize}. */
    public void configure(SettingsGroup settings) {
    }

    /** Called once after registration, after stored settings were applied. */
    public void initialize() {
    }

    /** The user switched the widget on (also called on start if it is on). */
    public void onEnable() {
    }

    /** The user switched the widget off, or it is being removed. */
    public void onDisable() {
    }

    /** Once per client tick while enabled. */
    public void tick() {
    }

    /** Width in unscaled pixels for the current content. */
    public abstract int width(RenderContext context);

    /** Height in unscaled pixels for the current content. */
    public abstract int height(RenderContext context);

    /** Draws the widget at (0, 0). In the editor without a world {@link RenderContext#preview()} is true: show sample data. */
    public abstract void render(HudGraphics graphics, RenderContext context);

    /** Runtime condition (e.g. only on servers). Hidden widgets still show as a placeholder in the editor. */
    public boolean isVisible(RenderContext context) {
        return true;
    }

    /**
     * Mouse click on the widget while the chat is open (the cursor is free). Coordinates are local and unscaled.
     * Return {@code true} if handled.
     */
    public boolean mouseClicked(double x, double y, int button) {
        return false;
    }

    /** After the widget was unregistered: release resources. */
    public void dispose() {
    }
}
