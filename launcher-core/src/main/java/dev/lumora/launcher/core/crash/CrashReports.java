package dev.lumora.launcher.core.crash;

import dev.lumora.launcher.core.LauncherPaths;
import dev.lumora.launcher.core.diagnostics.CrashAnalyzer;
import dev.lumora.launcher.core.util.FileUtil;
import dev.lumora.launcher.core.util.Log;
import dev.lumora.launcher.core.util.Redactor;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Crash reports of all profiles: Minecraft's own (&lt;game folder&gt;/crash-reports/*.txt) and the launcher's
 * records for games that ended with an error code without writing one (&lt;game folder&gt;/crash-reports/
 * lumora-exit-*.txt, containing the redacted end of the game output). Also builds a shareable, redacted text
 * and a diagnostics zip for support requests.
 */
public final class CrashReports {
    public static final String LAUNCHER_PREFIX = "lumora-exit-";
    static final int MAX_READ = 512 * 1024;
    private static final int OUTPUT_TAIL_LINES = 300;
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss", Locale.ROOT)
            .withZone(ZoneId.systemDefault());
    private static final Pattern EXIT_CODE = Pattern.compile("^Exit code: (-?\\d+)$", Pattern.MULTILINE);

    private CrashReports() {
    }

    /**
     * All crashes of these profiles, newest first. Parsing (incl. culprit search) happens here - call off the UI thread.
     * Profiles sharing a game folder (the standard folder) list its reports once, under the first of them.
     *
     * @param gameDirs game folder of a profile (InstanceLayout#gameDir)
     */
    public static List<CrashReport> list(java.util.function.Function<String, Path> gameDirs, List<String> profileIds) {
        List<CrashReport> out = new ArrayList<>();
        java.util.Set<Path> seen = new java.util.HashSet<>();
        for (String id : profileIds) {
            Path game = gameDirs.apply(id);
            Path dir = game.resolve("crash-reports");
            if (!seen.add(dir.toAbsolutePath().normalize()) || !Files.isDirectory(dir)) {
                continue;
            }
            List<Path> files;
            try (Stream<Path> s = Files.list(dir)) {
                files = s.filter(p -> p.getFileName().toString().endsWith(".txt")).toList();
            } catch (IOException e) {
                continue;
            }
            Path mods = game.resolve("mods");
            List<CulpritFinder.Mod> index = null;
            for (Path f : files) {
                try {
                    if (index == null) {
                        index = CulpritFinder.index(mods);
                    }
                    out.add(read(id, f, index));
                } catch (IOException | RuntimeException e) {
                    Log.warn("Could not read crash report " + f + ": " + e.getMessage());
                }
            }
        }
        out.sort(Comparator.comparing(CrashReport::time).reversed());
        return out;
    }

    /** One report, e.g. right after a crash (call off the UI thread: the culprit search reads the mod jars). */
    public static CrashReport load(Path gameDir, String profileId, Path file) throws IOException {
        return read(profileId, file, CulpritFinder.index(gameDir.resolve("mods")));
    }

    static CrashReport read(String profileId, Path file, List<CulpritFinder.Mod> mods) throws IOException {
        String text = readHead(file);
        boolean launcher = file.getFileName().toString().startsWith(LAUNCHER_PREFIX);
        CrashReportParser.Parsed p = CrashReportParser.parse(text);
        Instant time = p.time() != null ? p.time() : Files.getLastModifiedTime(file).toInstant();
        Integer exitCode = null;
        Matcher m = EXIT_CODE.matcher(text);
        if (m.find()) {
            exitCode = Integer.parseInt(m.group(1));
        }
        String description = p.description() != null ? p.description()
                : launcher ? "Minecraft wurde ohne Absturzbericht beendet" : "Unbekannter Absturz";
        return new CrashReport(profileId, file, launcher ? CrashReport.Source.LAUNCHER : CrashReport.Source.MINECRAFT, time,
                description, p.exception(), exitCode, CulpritFinder.find(text, mods), CrashAnalyzer.analyze(text));
    }

    static String readHead(Path file) throws IOException {
        try (var in = Files.newInputStream(file)) {
            return new String(in.readNBytes(MAX_READ), StandardCharsets.UTF_8);
        }
    }

    /**
     * Called when the game ended with an error code. If Minecraft wrote no crash report, the launcher keeps its own
     * record (the redacted end of the game output) so the crash appears on the crash page. Returns that file.
     */
    public static Path recordExit(Path instance, String profileId, int exitCode, Path outputLog, Instant when) throws IOException {
        Path dir = instance.resolve("crash-reports");
        Files.createDirectories(dir);
        Path file = FileUtil.resolveInside(dir, LAUNCHER_PREFIX + FILE_TIME.format(when) + ".txt");
        List<String> tail = tail(outputLog, OUTPUT_TAIL_LINES);
        StringBuilder sb = new StringBuilder();
        sb.append("---- Lumora Launcher: Spielende mit Fehler ----\n");
        sb.append("Time: ").append(DateTimeFormatter.ISO_INSTANT.format(when)).append('\n');
        sb.append("Description: Minecraft wurde mit Fehlercode ").append(exitCode).append(" beendet (kein Absturzbericht)\n");
        sb.append("Profile: ").append(profileId).append('\n');
        sb.append("Exit code: ").append(exitCode).append("\n\n");
        sb.append("---- Letzte ").append(tail.size()).append(" Zeilen der Spielausgabe ----\n");
        tail.forEach(l -> sb.append(l).append('\n'));
        FileUtil.writeAtomically(file, Redactor.redact(sb.toString()).getBytes(StandardCharsets.UTF_8));
        return file;
    }

    static List<String> tail(Path file, int lines) {
        if (file == null || !Files.isRegularFile(file)) {
            return List.of();
        }
        try {
            byte[] all = Files.readAllBytes(file);
            int from = Math.max(0, all.length - 2 * 1024 * 1024);
            List<String> l = new String(all, from, all.length - from, StandardCharsets.UTF_8).lines().toList();
            return l.subList(Math.max(0, l.size() - lines), l.size());
        } catch (IOException e) {
            return List.of();
        }
    }

    /**
     * The report as text to paste into a support chat: tokens removed ({@link Redactor}), the Windows user folder
     * and account name replaced, capped in size.
     */
    public static String shareText(CrashReport r) throws IOException {
        return anonymize(readHead(r.file()));
    }

    static String anonymize(String text) {
        String t = Redactor.redact(text);
        String home = System.getProperty("user.home");
        if (home != null && home.length() > 3) {
            t = t.replace(home, "%USERPROFILE%").replace(home.replace('\\', '/'), "%USERPROFILE%");
        }
        String user = System.getProperty("user.name");
        if (user != null && user.length() >= 3) {
            t = t.replaceAll("(?i)(?<![\\w])" + Pattern.quote(user) + "(?![\\w])", "<user>");
        }
        return t;
    }

    /**
     * Zip for support: the crash report, the newest game log, the launcher log and a short summary (profile,
     * suspects, hints, mod list). Everything is anonymized; no account data, no config with IDs.
     */
    public static void writeDiagnosticsZip(LauncherPaths paths, Path gameDir, CrashReport r, Map<String, String> extra, Path target)
            throws IOException {
        Path instance = gameDir;
        try (OutputStream out = Files.newOutputStream(target); ZipOutputStream zip = new ZipOutputStream(out)) {
            entry(zip, "crash/" + r.file().getFileName(), anonymize(readHead(r.file())));
            for (String log : List.of("logs/latest.log", "logs/lumora-game-output.log")) {
                Path p = instance.resolve(log);
                if (Files.isRegularFile(p)) {
                    entry(zip, "game/" + p.getFileName(), anonymize(String.join("\n", tail(p, 3000))));
                }
            }
            Path launcherLog = paths.root().resolve("logs/launcher.log");
            if (Files.isRegularFile(launcherLog)) {
                entry(zip, "launcher/launcher.log", anonymize(String.join("\n", tail(launcherLog, 2000))));
            }
            StringBuilder s = new StringBuilder();
            s.append("Lumora Diagnosepaket\n\nProfil: ").append(r.profileId()).append("\nZeit: ").append(r.time())
                    .append("\nBeschreibung: ").append(r.description()).append("\nAusnahme: ").append(r.exception()).append('\n');
            r.suspects().forEach(x -> s.append("Verdacht: ").append(x.name()).append(" (").append(x.modId()).append(") - ").append(x.reason()).append('\n'));
            r.hints().forEach(h -> s.append("Hinweis: ").append(h.title()).append(" - ").append(h.advice()).append('\n'));
            extra.forEach((k, v) -> s.append('\n').append(k).append(":\n").append(v).append('\n'));
            entry(zip, "summary.txt", anonymize(s.toString()));
        }
    }

    private static void entry(ZipOutputStream zip, String name, String text) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(text.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
