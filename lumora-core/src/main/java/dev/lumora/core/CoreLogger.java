package dev.lumora.core;

/** Logging bridge (SLF4J in the game, a list in tests). */
public interface CoreLogger {
    void info(String message);

    void warn(String message);

    void warn(String message, Throwable error);

    void error(String message, Throwable error);
}
