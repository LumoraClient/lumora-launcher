package dev.lumora.launcher.app.ui;

import dev.lumora.launcher.core.util.Log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * "Mit Windows starten": a value in the current user's Run key (HKCU\Software\Microsoft\Windows\CurrentVersion\Run,
 * no admin rights, only this user) that starts the installed LumoraLauncher.exe with {@code --tray}. Only available
 * when the launcher runs from its EXE (jpackage sets {@code jpackage.app-path}). Changed only by the user's switch.
 */
public final class Autostart {
    static final String KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";
    static final String VALUE = "LumoraLauncher";

    private Autostart() {
    }

    /** The launcher EXE, or null when not started from it (IDE, Gradle run, other OS). */
    public static Path exe() {
        String p = System.getProperty("jpackage.app-path");
        if (p == null || !System.getProperty("os.name", "").toLowerCase().contains("win")) {
            return null;
        }
        Path exe = Path.of(p);
        return Files.isRegularFile(exe) ? exe : null;
    }

    public static boolean supported() {
        return exe() != null;
    }

    /** reg.exe arguments, for tests. */
    static List<String> addCommand(Path exe) {
        return List.of("reg", "add", KEY, "/v", VALUE, "/t", "REG_SZ", "/d", "\"" + exe + "\" --tray", "/f");
    }

    static List<String> deleteCommand() {
        return List.of("reg", "delete", KEY, "/v", VALUE, "/f");
    }

    static List<String> queryCommand() {
        return List.of("reg", "query", KEY, "/v", VALUE);
    }

    public static boolean enabled() {
        if (!supported()) {
            return false;
        }
        Result r = run(queryCommand());
        return r.code == 0 && r.output.contains(exe().toString());
    }

    /** @return true when the change worked */
    public static boolean set(boolean on) {
        Path exe = exe();
        if (exe == null) {
            return false;
        }
        Result r = run(on ? addCommand(exe) : deleteCommand());
        if (r.code == 0) {
            return true;
        }
        Log.warn("Autostart " + (on ? "on" : "off") + " failed: " + r.output.strip());
        // switching off a value that is not there (localized error text) counts as done
        return !on && !enabled();
    }

    private record Result(int code, String output) {
    }

    private static Result run(List<String> cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!p.waitFor(10, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return new Result(-1, "timeout");
            }
            return new Result(p.exitValue(), out);
        } catch (IOException e) {
            return new Result(-1, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(-1, "interrupted");
        }
    }
}
