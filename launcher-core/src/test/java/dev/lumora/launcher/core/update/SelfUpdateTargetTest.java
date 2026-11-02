package dev.lumora.launcher.core.update;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Where a self-update goes: through the starter, or started directly from the extracted folder (pinned icon). */
class SelfUpdateTargetTest {
    @TempDir
    Path dir;

    /** {@code <root>/portable/<hash>/LumoraLauncher/LumoraLauncher.exe} as the portable starter extracts it. */
    private Path extracted(boolean marker) throws IOException {
        Path hash = dir.resolve("LumoraClient").resolve("portable").resolve("8018a4f273f43536");
        Path app = hash.resolve("LumoraLauncher").resolve("LumoraLauncher.exe");
        Files.createDirectories(app.getParent());
        Files.write(app, new byte[]{1});
        if (marker) {
            Files.writeString(hash.resolve(LauncherUpdate.EXTRACTED_MARKER), "x");
        }
        return app;
    }

    private Path starter(String name) throws IOException {
        Path exe = dir.resolve("Downloads").resolve(name);
        Files.createDirectories(exe.getParent());
        Files.write(exe, new byte[]{1});
        return exe;
    }

    @Test
    void startedThroughTheStarter() throws IOException {
        Path exe = starter("LumoraClient.exe");
        Optional<LauncherUpdate.Target> t = LauncherUpdate.selfUpdateTarget(exe.toString(), extracted(true), false);
        assertEquals(exe, t.orElseThrow().exe());
        assertEquals(LauncherUpdate.Target.Source.STARTER, t.get().source());
        assertTrue(t.get().starter());
    }

    @Test
    void directStartUsesTheRecordedStarter() throws IOException {
        Path app = extracted(true);
        Path exe = starter("LumoraClient (1).exe");
        Files.writeString(dir.resolve("LumoraClient/portable").resolve(LauncherUpdate.STARTER_RECORD), exe + "\r\n", StandardCharsets.UTF_8);
        LauncherUpdate.Target t = LauncherUpdate.selfUpdateTarget(null, app, true).orElseThrow();
        assertEquals(exe, t.exe());
        assertEquals(LauncherUpdate.Target.Source.RECORDED, t.source());
    }

    @Test
    void directStartWithoutRecordUsesTheManagedCopy() throws IOException {
        LauncherUpdate.Target t = LauncherUpdate.selfUpdateTarget(null, extracted(true), true).orElseThrow();
        assertEquals(dir.resolve("LumoraClient").resolve(LauncherUpdate.MANAGED_EXE).toAbsolutePath(), t.exe().toAbsolutePath());
        assertEquals(LauncherUpdate.Target.Source.MANAGED_COPY, t.source());
        assertFalse(t.starter());
    }

    @Test
    void staleOrInvalidRecordFallsBackToTheManagedCopy() throws IOException {
        Path app = extracted(true);
        Path record = dir.resolve("LumoraClient/portable").resolve(LauncherUpdate.STARTER_RECORD);
        for (String content : new String[]{dir.resolve("gone.exe").toString(), "relative.exe", starter("notes.txt").toString(),
                starter("a.exe") + "\n" + starter("b.exe")}) {
            Files.writeString(record, content);
            assertEquals(LauncherUpdate.Target.Source.MANAGED_COPY,
                    LauncherUpdate.selfUpdateTarget(null, app, true).orElseThrow().source(), content);
        }
    }

    @Test
    void settingOffOrNotPortableMeansNoSelfUpdate() throws IOException {
        Path app = extracted(true);
        assertTrue(LauncherUpdate.selfUpdateTarget(null, app, false).isEmpty(), "setting off");
        assertTrue(LauncherUpdate.selfUpdateTarget("", app, false).isEmpty());
        Path noMarker = dir.resolve("other");
        Files.createDirectories(noMarker);
        assertTrue(LauncherUpdate.selfUpdateTarget(null, dir.resolve("Program Files/LumoraLauncher/LumoraLauncher.exe"), true).isEmpty(),
                "installed launcher");
        assertTrue(LauncherUpdate.selfUpdateTarget(null, null, true).isEmpty(), "IDE / Gradle run");
    }

    @Test
    void portableFolderNeedsHashNameAndMarker() throws IOException {
        assertTrue(LauncherUpdate.portableBase(extracted(false)).isEmpty(), "no marker");
        assertEquals(dir.resolve("LumoraClient/portable").toAbsolutePath(), LauncherUpdate.portableBase(extracted(true)).orElseThrow());
        Path wrongName = dir.resolve("p/NOTAHASH/LumoraLauncher/LumoraLauncher.exe");
        Files.createDirectories(wrongName.getParent());
        Files.writeString(dir.resolve("p/NOTAHASH").resolve(LauncherUpdate.EXTRACTED_MARKER), "x");
        assertTrue(LauncherUpdate.portableBase(wrongName).isEmpty());
    }
}
