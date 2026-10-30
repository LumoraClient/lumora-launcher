package dev.lumora.api.addon;

/** Logger that prefixes messages with the addon id; ends up in the game log. Never log access tokens or passwords. */
public interface AddonLogger {
    void info(String message);

    void warn(String message);

    void warn(String message, Throwable error);

    void error(String message, Throwable error);
}
