package dev.lumora.voice;

/**
 * Logging without Minecraft or SLF4J classes (1.8.9 and 26.x ship different logging stacks). The Lumora addon entry
 * routes it to the addon logger; tests and early startup fall back to {@link System.Logger}.
 */
public final class VoiceLog {
    public interface Sink {
        void info(String message);
        void warn(String message, Throwable error);
    }

    private static final System.Logger FALLBACK = System.getLogger("LumoraVoice");
    private static volatile Sink sink;

    private VoiceLog() {}

    public static void use(Sink target) { sink = target; }

    public static void info(String message) {
        Sink s = sink;
        if (s != null) s.info(message);
        else FALLBACK.log(System.Logger.Level.INFO, message);
    }

    public static void warn(String message, Throwable error) {
        Sink s = sink;
        if (s != null) s.warn(message, error);
        else FALLBACK.log(System.Logger.Level.WARNING, message, error);
    }

    public static void warn(String message) { warn(message, null); }
}
