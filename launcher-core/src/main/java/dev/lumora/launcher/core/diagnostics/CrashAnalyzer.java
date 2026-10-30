package dev.lumora.launcher.core.diagnostics;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Turns common failure signatures in the game output / crash report into actionable hints. */
public final class CrashAnalyzer {
    public record Hint(String title, String advice) {
    }

    private CrashAnalyzer() {
    }

    public static List<Hint> analyze(Path outputLog, Optional<Path> crashReport) {
        StringBuilder text = new StringBuilder();
        readTail(outputLog, text);
        crashReport.ifPresent(p -> readTail(p, text));
        return analyze(text.toString());
    }

    public static List<Hint> analyze(String text) {
        String t = text.toLowerCase(Locale.ROOT);
        List<Hint> hints = new ArrayList<>();
        if (t.contains("incompatible mods found") || t.contains("incompatible mod set") || t.contains("net.fabricmc.loader.impl.formatterror")) {
            hints.add(new Hint("Inkompatible Mods",
                    "Fabric Loader hat eine ungültige Mod-Kombination erkannt. Entferne manuell hinzugefügte Mods aus dem "
                            + "mods-Ordner des Profils oder deaktiviere Erweiterungen."));
        }
        if (t.contains("java.lang.outofmemoryerror")) {
            hints.add(new Hint("Zu wenig Arbeitsspeicher", "Erhöhe den maximalen Arbeitsspeicher in den Launcher-Einstellungen."));
        }
        if (t.contains("mixin apply failed") || t.contains("mixintransformererror")) {
            hints.add(new Hint("Mixin-Fehler", "Eine Mod konnte nicht in das Spiel eingreifen. Meist eine nicht passende "
                    + "Zusatz-Mod; Erweiterungen deaktivieren und erneut starten."));
        }
        if (t.contains("glfw error 65542") || t.contains("wgl: the driver does not appear to support opengl")) {
            hints.add(new Hint("OpenGL nicht verfügbar", "Der Grafiktreiber stellt kein OpenGL bereit. Grafiktreiber installieren."));
        }
        if (t.contains("invalid session") || t.contains("failed to verify username")) {
            hints.add(new Hint("Sitzung ungültig", "Bitte im Launcher ab- und wieder anmelden."));
        }
        return hints;
    }

    private static void readTail(Path file, StringBuilder out) {
        try {
            if (file != null && Files.isRegularFile(file)) {
                byte[] all = Files.readAllBytes(file);
                int from = Math.max(0, all.length - 256 * 1024);
                out.append(new String(all, from, all.length - from, StandardCharsets.UTF_8)).append('\n');
            }
        } catch (IOException ignored) {
            // diagnostics are best-effort
        }
    }
}
