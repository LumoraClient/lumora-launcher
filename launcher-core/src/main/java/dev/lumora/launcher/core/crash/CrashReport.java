package dev.lumora.launcher.core.crash;

import dev.lumora.launcher.core.diagnostics.CrashAnalyzer;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/**
 * One crash of one profile, for the launcher's crash page.
 *
 * @param file        Minecraft's crash report (crash-reports/*.txt) or the launcher's own exit record
 * @param source      where it comes from
 * @param description "Description:" line of the report, or a short text for launcher records
 * @param exception   first line of the causing exception, e.g. "java.lang.NullPointerException: ..."
 * @param exitCode    process exit code if known (launcher records), otherwise null
 * @param suspects    mods that most likely caused the crash, best match first
 * @param hints       known failure patterns with advice
 */
public record CrashReport(String profileId, Path file, Source source, Instant time, String description, String exception,
                          Integer exitCode, List<Suspect> suspects, List<CrashAnalyzer.Hint> hints) {

    public enum Source {
        /** Written by Minecraft (crash-reports/crash-*.txt). */
        MINECRAFT,
        /** Written by the launcher when the game ended with an error code but left no crash report. */
        LAUNCHER
    }

    /**
     * @param modId    Fabric mod id
     * @param name     display name
     * @param file     the jar in mods/ (null if not found there)
     * @param reason   why it is suspected (German, for the UI)
     * @param score    higher = more likely
     */
    public record Suspect(String modId, String name, Path file, String reason, int score) {
    }
}
