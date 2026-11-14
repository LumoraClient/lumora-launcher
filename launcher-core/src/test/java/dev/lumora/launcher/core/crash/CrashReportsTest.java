package dev.lumora.launcher.core.crash;

import dev.lumora.launcher.core.LauncherPaths;
import dev.lumora.launcher.core.library.WorldBackup;
import dev.lumora.launcher.core.util.Redactor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrashReportsTest {
    @TempDir
    Path tmp;

    @AfterEach
    void clear() {
        Redactor.clearSecrets();
    }

    static final String MC_REPORT = """
            ---- Minecraft Crash Report ----
            // Who set us up the TNT?

            Time: 2026-10-18 14:02:11
            Description: Rendering screen

            java.lang.NullPointerException: Cannot invoke "Object.toString()" because "value" is null
            \tat knot//com.example.zoomify.client.ZoomRenderer.render(ZoomRenderer.java:42)
            \tat knot//com.example.zoomify.client.ZoomRenderer.tick(ZoomRenderer.java:12)
            \tat knot//net.minecraft.client.renderer.GameRenderer.render(GameRenderer.java:900)
            \tat knot//net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer.draw(SodiumWorldRenderer.java:200)

            A detailed walkthrough of the error, its code path and all known details is as follows:
            """;

    static void mod(Path jar, String json, String... classes) throws IOException {
        Files.createDirectories(jar.getParent());
        try (OutputStream out = Files.newOutputStream(jar); ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("fabric.mod.json"));
            zip.write(json.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            for (String c : classes) {
                zip.putNextEntry(new ZipEntry(c));
                zip.closeEntry();
            }
        }
    }

    @Test
    void parsesHeadlineOfAMinecraftReport() {
        CrashReportParser.Parsed p = CrashReportParser.parse(MC_REPORT);
        assertEquals("Rendering screen", p.description());
        assertTrue(p.exception().startsWith("java.lang.NullPointerException: Cannot invoke"));
        assertNotNull(p.time());
    }

    @Test
    void topStackFramesPointToTheCulprit() throws IOException {
        Path mods = tmp.resolve("instances/1.21.11/mods");
        mod(mods.resolve("zoomify.jar"), "{\"id\":\"zoomify\",\"name\":\"Zoomify\"}", "com/example/zoomify/client/ZoomRenderer.class");
        mod(mods.resolve("sodium.jar"), "{\"id\":\"sodium\",\"name\":\"Sodium\",\"mixins\":[\"sodium.mixins.json\"]}",
                "net/caffeinemc/mods/sodium/client/render/SodiumWorldRenderer.class");
        List<CrashReport.Suspect> s = CulpritFinder.find(MC_REPORT, mods);
        assertEquals("zoomify", s.getFirst().modId(), s.toString());
        assertTrue(s.getFirst().reason().contains("Stacktrace"));
    }

    @Test
    void mixinConfigInTheReportIsAStrongSignal() throws IOException {
        Path mods = tmp.resolve("mods");
        mod(mods.resolve("a.jar"), "{\"id\":\"badmixins\",\"name\":\"Bad Mixins\",\"mixins\":[{\"config\":\"badmixins.client.json\"}]}");
        String report = "Description: Initializing game\n\norg.spongepowered.asm.mixin.transformer.throwables.MixinTransformerError: "
                + "An unexpected critical error was encountered\nCaused by: Mixin [badmixins.client.json:FooMixin] from mod badmixins failed";
        List<CrashReport.Suspect> s = CulpritFinder.find(report, mods);
        assertEquals("badmixins", s.getFirst().modId());
        assertTrue(s.getFirst().reason().contains("badmixins.client.json"));
    }

    @Test
    void listsMinecraftReportsAndLauncherRecordsNewestFirst() throws IOException {
        LauncherPaths paths = new LauncherPaths(tmp);
        Path inst = paths.instance("1.21.11");
        Files.createDirectories(inst.resolve("crash-reports"));
        Files.writeString(inst.resolve("crash-reports/crash-2026-10-18_14.02.11-client.txt"), MC_REPORT);
        Path log = inst.resolve("logs/lumora-game-output.log");
        Files.createDirectories(log.getParent());
        Files.writeString(log, "[main/INFO]: starting\nException in thread \"main\" java.lang.IllegalStateException: boom\n");
        Redactor.registerSecret("super-secret-token-value");
        Files.writeString(log, "token super-secret-token-value\n", java.nio.file.StandardOpenOption.APPEND);
        Path rec = CrashReports.recordExit(inst, "1.21.11", 1, log, Instant.parse("2027-01-01T10:00:00Z"));
        assertFalse(Files.readString(rec).contains("super-secret-token-value"), "launcher record is redacted");

        List<CrashReport> all = CrashReports.list(paths::instance, List.of("26.1.2", "1.21.11"));
        assertEquals(2, all.size());
        assertEquals(CrashReport.Source.LAUNCHER, all.getFirst().source());
        assertEquals(1, all.getFirst().exitCode());
        assertTrue(all.getFirst().exception().contains("IllegalStateException"), all.getFirst().exception());
        assertEquals("Rendering screen", all.get(1).description());
    }

    @Test
    void shareTextHidesUserFolderAndName() {
        String home = System.getProperty("user.home");
        String t = CrashReports.anonymize("at " + home + "\\AppData\\x and user " + System.getProperty("user.name"));
        assertFalse(t.contains(home));
        assertTrue(t.contains("%USERPROFILE%"));
    }

    @Test
    void diagnosticsZipContainsReportLogsAndSummary() throws IOException {
        LauncherPaths paths = new LauncherPaths(tmp);
        Path inst = paths.instance("26.1.2");
        Files.createDirectories(inst.resolve("crash-reports"));
        Files.createDirectories(inst.resolve("logs"));
        Files.writeString(inst.resolve("crash-reports/crash-1.txt"), MC_REPORT);
        Files.writeString(inst.resolve("logs/latest.log"), "game log");
        CrashReport r = CrashReports.list(paths::instance, List.of("26.1.2")).getFirst();
        Path zip = tmp.resolve("diag.zip");
        CrashReports.writeDiagnosticsZip(paths, paths.instance("26.1.2"), r, Map.of("Mods", "Sodium 0.9.2"), zip);
        try (ZipFile z = new ZipFile(zip.toFile())) {
            assertNotNull(z.getEntry("crash/crash-1.txt"));
            assertNotNull(z.getEntry("game/latest.log"));
            assertTrue(new String(z.getInputStream(z.getEntry("summary.txt")).readAllBytes(), StandardCharsets.UTF_8).contains("Sodium 0.9.2"));
        }
    }

    @Test
    void worldBackupZipsWithoutSessionLock() throws IOException {
        Path world = tmp.resolve("saves/Meine Welt");
        Files.createDirectories(world.resolve("region"));
        Files.writeString(world.resolve("level.dat"), "x");
        Files.writeString(world.resolve("session.lock"), "x");
        Files.writeString(world.resolve("region/r.0.0.mca"), "data");
        Path zip = WorldBackup.backup(world, tmp.resolve("backups"), Instant.parse("2026-10-18T12:00:00Z"));
        try (ZipFile z = new ZipFile(zip.toFile())) {
            assertNotNull(z.getEntry("Meine Welt/region/r.0.0.mca"));
            assertEquals(null, z.getEntry("Meine Welt/session.lock"));
        }
        assertThrows(IOException.class, () -> WorldBackup.backup(tmp.resolve("saves"), tmp.resolve("backups"), Instant.now()));
    }
}
