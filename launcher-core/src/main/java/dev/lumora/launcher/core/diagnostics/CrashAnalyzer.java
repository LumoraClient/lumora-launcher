package dev.lumora.launcher.core.diagnostics;
import dev.lumora.launcher.core.i18n.Lang;

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
            hints.add(new Hint(Lang.t("crash.hint.incompatible"),
                    Lang.t("crash.hint.incompatible.text")));
        }
        if (t.contains("java.lang.outofmemoryerror")) {
            hints.add(new Hint(Lang.t("crash.hint.memory"), Lang.t("crash.hint.memory.text")));
        }
        if (t.contains("mixin apply failed") || t.contains("mixintransformererror")) {
            hints.add(new Hint(Lang.t("crash.hint.mixin"), Lang.t("crash.hint.mixin.text")));
        }
        if (t.contains("glfw error 65542") || t.contains("wgl: the driver does not appear to support opengl")) {
            hints.add(new Hint(Lang.t("crash.hint.opengl"), Lang.t("crash.hint.opengl.text")));
        }
        if (t.contains("invalid session") || t.contains("failed to verify username")) {
            hints.add(new Hint(Lang.t("crash.hint.session"), Lang.t("crash.hint.session.text")));
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
