package dev.lumora.api.addon;

/** Lifecycle of an addon. */
public enum AddonState {
    /** Found, metadata read and validated. */
    LOADED,
    /** All required dependencies are present in matching versions. */
    RESOLVED,
    /** Instance created and {@code onInitialize} done. */
    INITIALIZED,
    /** {@code onEnable} is running. */
    ENABLING,
    ENABLED,
    /** Switched off (by the user or because a dependency was switched off). */
    DISABLED,
    /** Disabled and instance released; enabling creates a new instance. Classes stay loaded until restart. */
    UNLOADED,
    /** Invalid metadata, missing dependency or an exception in a lifecycle method; see {@link AddonInfo#error()}. */
    FAILED
}
