package dev.lumora.api;

/**
 * Handle for something an addon registered (listener, widget, keybind, task). {@link #unregister()} removes it;
 * calling it again does nothing. Everything registered through an {@link dev.lumora.api.addon.AddonContext} is
 * also removed automatically when the addon is disabled.
 */
public interface Registration {
    /** Removes the registration. Idempotent and safe to call from the client thread at any time. */
    void unregister();

    /** {@code true} until {@link #unregister()} was called (or the owning addon was disabled). */
    boolean isActive();
}
