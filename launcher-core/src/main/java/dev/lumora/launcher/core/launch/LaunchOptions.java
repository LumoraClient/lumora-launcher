package dev.lumora.launcher.core.launch;

import dev.lumora.launcher.core.config.LauncherConfig;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Launcher settings that become part of the game command: garbage collector tuning, heap dump, JVM debugging,
 * fullscreen and environment variables. Pure functions, so they are easy to test.
 */
public final class LaunchOptions {
    /** G1 with short pauses: works on Java 8 to 25 and is what most Minecraft launchers ship. */
    static final List<String> PERFORMANCE_FLAGS = List.of("-XX:+UseG1GC", "-XX:+ParallelRefProcEnabled", "-XX:MaxGCPauseMillis=50");

    private LaunchOptions() {
    }

    /** Extra JVM arguments (after the user's own ones). */
    public static List<String> jvmArgs(LauncherConfig config, Path diagnosticsDir) {
        List<String> out = new ArrayList<>();
        if (config.performanceFlags && !choosesCollector(config.extraJvmArgs)) {
            out.addAll(PERFORMANCE_FLAGS);
        }
        if (config.heapDumpOnOom) {
            out.add("-XX:+HeapDumpOnOutOfMemoryError");
            out.add("-XX:HeapDumpPath=" + diagnosticsDir.toAbsolutePath());
        }
        if (!"off".equals(config.debugMode) && LauncherConfig.isValidDebugAddress(config.debugAddress)) {
            boolean listen = "listen".equals(config.debugMode);
            // listen: the game waits until a debugger connects; attach: the game connects to the running debugger
            out.add("-agentlib:jdwp=transport=dt_socket,server=" + (listen ? "y" : "n") + ",suspend=y,address="
                    + config.debugAddress.strip());
        }
        return out;
    }

    /** Extra game arguments. */
    public static List<String> gameArgs(LauncherConfig config) {
        return config.forceFullscreen ? List.of("--fullscreen") : List.of();
    }

    /** Environment variables for the game process; invalid entries are skipped. */
    public static Map<String, String> environment(LauncherConfig config) {
        Map<String, String> env = new LinkedHashMap<>();
        if (config.environmentVariables != null) {
            for (String e : config.environmentVariables) {
                if (LauncherConfig.isValidEnvironmentVariable(e)) {
                    int eq = e.indexOf('=');
                    env.put(e.substring(0, eq), e.substring(eq + 1));
                }
            }
        }
        return env;
    }

    /** Parses the settings text field: one KEY=VALUE per line or separated by commas. */
    public static List<String> parseEnvironment(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) {
            return out;
        }
        for (String part : text.split("[\\r\\n,]+")) {
            String p = part.strip();
            if (!p.isEmpty()) {
                out.add(p);
            }
        }
        return out;
    }

    private static boolean choosesCollector(List<String> args) {
        return args != null && args.stream().anyMatch(a -> a.startsWith("-XX:+Use") && a.endsWith("GC"));
    }
}
