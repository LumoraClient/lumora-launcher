package dev.lumora.api.addon;

/**
 * Entry point of an addon, named in {@code lumora-addon.json} ("entrypoint"). Needs a public no-argument
 * constructor. All methods run on the client thread.
 * <p>
 * Lifecycle: {@link #onInitialize} once after the instance was created (define settings here), then
 * {@link #onEnable} / {@link #onDisable} whenever the addon is switched on or off. Everything registered
 * through the context (listeners, widgets, keybinds, tasks) is removed automatically after {@link #onDisable},
 * even if it throws. A disabled addon must not keep working: do not start own threads or keep references that
 * outlive {@code onDisable}.
 */
public interface LumoraAddon {
    /** Called once; define addon settings with {@code context.configuration().settings()}. Registrations are not allowed yet. */
    default void onInitialize(AddonContext context) throws Exception {
    }

    /** Register listeners, widgets, keybinds and tasks here. An exception marks the addon as failed and undoes its registrations. */
    void onEnable(AddonContext context) throws Exception;

    /** Release own resources. Registrations made through the context are removed afterwards automatically. */
    default void onDisable(AddonContext context) throws Exception {
    }
}
