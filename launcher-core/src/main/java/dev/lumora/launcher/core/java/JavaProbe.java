package dev.lumora.launcher.core.java;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Determines the feature version of a java executable by asking it (no guessing from paths). */
public final class JavaProbe {
    private static final Pattern SPEC = Pattern.compile("java\\.specification\\.version\\s*=\\s*(\\d+)(?:\\.(\\d+))?");

    private JavaProbe() {
    }

    public static int majorVersion(Path javaExecutable) throws IOException {
        if (!Files.isRegularFile(javaExecutable)) {
            throw new IOException("Java nicht gefunden: " + javaExecutable);
        }
        Path exe = javaExecutable;
        // javaw.exe prints nothing to a console; use the java.exe next to it for probing.
        if (exe.getFileName().toString().equalsIgnoreCase("javaw.exe")) {
            Path console = exe.resolveSibling("java.exe");
            if (Files.isRegularFile(console)) {
                exe = console;
            }
        }
        Process p = new ProcessBuilder(exe.toString(), "-XshowSettings:properties", "-version")
                .redirectErrorStream(true).start();
        String out;
        try {
            out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!p.waitFor(20, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new IOException("Java-Prüfung hat zu lange gedauert: " + exe);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            p.destroyForcibly();
            throw new IOException("Java-Prüfung unterbrochen", e);
        }
        return parseMajor(out);
    }

    static int parseMajor(String settingsOutput) throws IOException {
        Matcher m = SPEC.matcher(settingsOutput);
        if (!m.find()) {
            throw new IOException("Java-Version konnte nicht ermittelt werden");
        }
        int first = Integer.parseInt(m.group(1));
        // Java 8 reports "1.8"
        return first == 1 && m.group(2) != null ? Integer.parseInt(m.group(2)) : first;
    }
}
