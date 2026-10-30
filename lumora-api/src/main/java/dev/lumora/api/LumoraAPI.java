package dev.lumora.api;

/**
 * Global entry point. Addons normally get everything through their {@link dev.lumora.api.addon.AddonContext}
 * (which also cleans up after them); this accessor is for code that runs outside an addon lifecycle.
 */
public final class LumoraAPI {
    /** Version of this API. Addons declare the minimum they need in {@code lumora-addon.json} ("api"). */
    public static final String API_VERSION = "1.1.0";

    private static volatile Lumora instance;

    private LumoraAPI() {
    }

    /** The running client; throws {@link IllegalStateException} if Lumora is not (yet) loaded. */
    public static Lumora get() {
        Lumora l = instance;
        if (l == null) {
            throw new IllegalStateException("Lumora is not loaded");
        }
        return l;
    }

    public static boolean isAvailable() {
        return instance != null;
    }

    /** Internal: installed exactly once by the Lumora core during client start. */
    public static synchronized void install(Lumora lumora) {
        if (instance != null) {
            throw new IllegalStateException("Lumora is already installed");
        }
        instance = lumora;
    }
}
