package dev.lumora.launcher.core.crash;

import dev.lumora.launcher.core.i18n.Lang;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * "Analyze" on the crash page: reads the crash report, the end of the game output and a JVM crash log (hs_err) from
 * the same time, recognises the cause from known signatures and says in plain words what happened, why, what helps -
 * and which fix the launcher can do itself ({@link Fix}). Works offline: nothing leaves the computer.
 */
public final class CrashDiagnosis {
    /** What the launcher can do about it with one button. */
    public enum Action {
        /** Raise the maximum memory to {@link Fix#memoryMb}. */
        MORE_MEMORY,
        /** Lower the maximum memory to {@link Fix#memoryMb} (the computer ran out of memory). */
        LESS_MEMORY,
        /** Set the render backend of 26.2+ to {@link Fix#backend}. */
        GRAPHICS_BACKEND,
        /** Check and reinstall the game files, Lumora and Java, then start. */
        REPAIR,
        /** Disable the suspected mod or extension ({@link Fix#suspect}). */
        DISABLE_SUSPECT,
        /** Sign in again. */
        SIGN_IN,
        /** Open the library (world backups). */
        OPEN_LIBRARY,
        /** Nothing the launcher can do by itself. */
        NONE
    }

    public record Fix(Action action, int memoryMb, String backend, CrashReport.Suspect suspect) {
        static Fix of(Action a) {
            return new Fix(a, 0, null, null);
        }
    }

    /**
     * @param key      which known cause matched (lang keys {@code crash.analysis.<key>.*})
     * @param what     what happened, in plain words
     * @param why      the likely cause
     * @param steps    what helps, in order
     * @param evidence the line of the report that gave it away (may be empty)
     * @param fix      what the fix button does
     */
    public record Result(String key, String what, String why, List<String> steps, String evidence, Fix fix) {
    }

    /** Settings the advice depends on. */
    public record Context(String minecraftVersion, int maxMemoryMb, long totalRamMb, String graphicsBackend) {
    }

    private static final List<String> GPU_DRIVERS = List.of("nvoglv64.dll", "nvoglv32.dll", "atio6axx.dll", "atig6pxx.dll",
            "atioglxx.dll", "amdvlk64.dll", "ig9icd64.dll", "ig8icd64.dll", "ig7icd64.dll", "igxelpicd64.dll", "ig75icd64.dll",
            "nvwgf2umx.dll", "libnvidia", "radeonsi", "iris_dri");

    private CrashDiagnosis() {
    }

    /** Reads everything that belongs to the crash (call off the UI thread) and diagnoses it. */
    public static Result analyze(CrashReport report, Path gameDir, Context ctx) {
        return diagnose(report, collect(report, gameDir), ctx);
    }

    /** The crash report, the end of the game output logs and a hs_err log written within two minutes of the crash. */
    static String collect(CrashReport report, Path gameDir) {
        StringBuilder sb = new StringBuilder();
        append(sb, report.file(), CrashReports.MAX_READ);
        Path logs = gameDir.resolve("logs");
        if (Files.isDirectory(logs)) {
            try (Stream<Path> s = Files.list(logs)) {
                s.filter(p -> p.getFileName().toString().startsWith("lumora-game-output")).forEach(p -> tail(sb, p));
            } catch (IOException ignored) {
                // best effort
            }
        }
        try (Stream<Path> s = Files.list(gameDir)) {
            s.filter(p -> p.getFileName().toString().startsWith("hs_err_pid") && p.getFileName().toString().endsWith(".log"))
                    .filter(p -> near(p, report))
                    .forEach(p -> append(sb, p, 64 * 1024));
        } catch (IOException ignored) {
            // best effort
        }
        return sb.toString();
    }

    private static boolean near(Path p, CrashReport r) {
        try {
            return Duration.between(Files.getLastModifiedTime(p).toInstant(), r.time()).abs().toMinutes() <= 2;
        } catch (IOException e) {
            return false;
        }
    }

    private static void append(StringBuilder sb, Path file, int max) {
        try (var in = Files.newInputStream(file)) {
            sb.append(new String(in.readNBytes(max), StandardCharsets.UTF_8)).append('\n');
        } catch (IOException ignored) {
            // best effort
        }
    }

    private static void tail(StringBuilder sb, Path file) {
        try {
            byte[] all = Files.readAllBytes(file);
            int from = Math.max(0, all.length - 128 * 1024);
            sb.append(new String(all, from, all.length - from, StandardCharsets.UTF_8)).append('\n');
        } catch (IOException ignored) {
            // best effort
        }
    }

    /** The diagnosis of {@code text} (crash report, logs) for {@code report}. */
    public static Result diagnose(CrashReport report, String text, Context ctx) {
        Optional<CrashReport.Suspect> suspect = report.suspects().stream().findFirst();
        boolean ownCode = suspect.isPresent() && !suspect.get().reason().contains("lumora.mixins.json");

        // the computer itself ran out of memory: less for Minecraft leaves more for the rest
        String ev = find(text, "native memory allocation", "insufficient memory for the java runtime", "could not reserve enough space");
        if (ev != null) {
            int less = roundDown(Math.max(2048, Math.min(ctx.maxMemoryMb() - 1024, ctx.totalRamMb() > 0 ? ctx.totalRamMb() / 2 : 4096)));
            Fix fix = less < ctx.maxMemoryMb() ? new Fix(Action.LESS_MEMORY, less, null, null) : Fix.of(Action.NONE);
            return result("system_memory", ev, fix, gb(less));
        }
        ev = find(text, "java.lang.outofmemoryerror");
        if (ev != null) {
            long cap = ctx.totalRamMb() > 0 ? ctx.totalRamMb() * 6 / 10 : 8192;
            int more = roundDown((int) Math.min(Math.max(ctx.maxMemoryMb() + 2048, 4096), Math.max(2048, cap)));
            Fix fix = more > ctx.maxMemoryMb() ? new Fix(Action.MORE_MEMORY, more, null, null) : Fix.of(Action.NONE);
            return result(fix.action() == Action.NONE ? "memory_full" : "memory", ev, fix, gb(more));
        }
        // graphics driver / render backend
        boolean switchable = dev.lumora.launcher.core.launch.GraphicsBackend.supported(ctx.minecraftVersion());
        ev = find(text, "vk_error", "failed to create vulkan", "vulkan error", "vkcreatedevice", "vulkan device lost");
        if (ev != null && switchable && !"opengl".equals(ctx.graphicsBackend())) {
            return result("vulkan", ev, new Fix(Action.GRAPHICS_BACKEND, 0, "opengl", null));
        }
        ev = find(text, GPU_DRIVERS.toArray(String[]::new));
        if (ev == null) {
            ev = find(text, "glfw error 65542", "glfw error 65543", "glfw error 65544", "pixel format not accelerated",
                    "wgl: the driver does not appear to support opengl", "no opengl context", "couldn't set pixel format");
        }
        if (ev != null) {
            if (switchable && !"vulkan".equals(ctx.graphicsBackend())) {
                return result("driver_switch", ev, new Fix(Action.GRAPHICS_BACKEND, 0, "vulkan", null));
            }
            return result("driver", ev, Fix.of(Action.NONE));
        }
        // wrong Java
        ev = find(text, "unsupportedclassversionerror", "has been compiled by a more recent version of the java runtime");
        if (ev != null) {
            return result("java", ev, Fix.of(Action.REPAIR));
        }
        // sign-in
        ev = find(text, "invalid session", "failed to verify username", "not authenticated with minecraft.net");
        if (ev != null) {
            return result("session", ev, Fix.of(Action.SIGN_IN));
        }
        // damaged or mismatched files
        ev = find(text, "zipexception", "invalid loc header", "zip end header not found", "error in opening zip file",
                "zip file is empty", "invalid cen header");
        if (ev != null) {
            return result("files", ev, Fix.of(Action.REPAIR));
        }
        ev = find(text, "nosuchmethoderror", "nosuchfielderror", "noclassdeffounderror", "classnotfoundexception",
                "incompatibleclasschangeerror", "abstractmethoderror");
        if (ev != null) {
            if (ownCode) {
                return result("mod_mismatch", ev, new Fix(Action.DISABLE_SUSPECT, 0, null, suspect.get()), suspect.get().name());
            }
            return result("mismatch", ev, Fix.of(Action.REPAIR));
        }
        // a mod could not hook into the game
        ev = find(text, "mixintransformererror", "mixin apply failed", "invalidinjectionexception", "injectionerror");
        if (ev == null && suspect.isPresent() && suspect.get().reason().contains(".mixins.json")) {
            ev = suspect.get().reason();
        }
        if (ev != null) {
            if (ownCode) {
                return result("mod_mixin", ev, new Fix(Action.DISABLE_SUSPECT, 0, null, suspect.get()), suspect.get().name());
            }
            return result("lumora_mixin", ev, Fix.of(Action.REPAIR));
        }
        // a mod that shows up in the stack trace
        if (ownCode) {
            return result("mod", suspect.get().reason(), new Fix(Action.DISABLE_SUSPECT, 0, null, suspect.get()), suspect.get().name());
        }
        // the world
        ev = find(text, "exception reading", "level.dat", "chunk file at", "failed to load level", "exception ticking world",
                "ticking entity", "ticking block entity");
        if (ev != null) {
            return result("world", ev, Fix.of(Action.OPEN_LIBRARY));
        }
        // resource packs and shaders
        ev = find(text, "resource reload", "failed to load shader", "shader compilation", "couldn't load resource pack",
                "failed to load texture");
        if (ev != null) {
            return result("resources", ev, Fix.of(Action.REPAIR));
        }
        // the game ended without a report (native crash, killed, ...)
        if (report.source() == CrashReport.Source.LAUNCHER) {
            return result("exit", report.exitCode() == null ? "" : "exit code " + report.exitCode(), Fix.of(Action.REPAIR));
        }
        return result("unknown", report.exception() == null ? "" : report.exception(), Fix.of(Action.REPAIR));
    }

    private static Result result(String key, String evidence, Fix fix, Object... args) {
        String p = "crash.analysis." + key + ".";
        List<String> steps = new ArrayList<>();
        for (String s : Lang.t(p + "steps", args).split("\n")) {
            if (!s.isBlank()) {
                steps.add(s.strip());
            }
        }
        return new Result(key, Lang.t(p + "what", args), Lang.t(p + "why", args), List.copyOf(steps),
                evidence == null ? "" : evidence.strip(), fix);
    }

    /** The first line of {@code text} that contains one of the needles (case-insensitive), cut to 200 characters. */
    static String find(String text, String... needles) {
        for (String line : text.split("\n")) {
            String l = line.toLowerCase(Locale.ROOT);
            for (String n : needles) {
                if (l.contains(n)) {
                    String s = line.strip();
                    return s.length() > 200 ? s.substring(0, 197) + "..." : s;
                }
            }
        }
        return null;
    }

    private static int roundDown(long mb) {
        return (int) Math.max(1024, mb / 512 * 512);
    }

    private static String gb(int mb) {
        return String.format(Locale.ROOT, "%.1f GB", mb / 1024.0);
    }
}
