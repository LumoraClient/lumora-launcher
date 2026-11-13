package dev.lumora.core;

import dev.lumora.api.game.ConnectionInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LauncherStatusTest {
    @TempDir
    Path dir;

    @Test
    void writesMenuWorldAndServerWithTheTimeTheyStarted() throws Exception {
        FakePlatform p = new FakePlatform(dir);
        Path file = dir.resolve(".lumora").resolve("status.json");
        LauncherStatus status = new LauncherStatus(file, p::game, "26.3");

        status.check(1000);
        assertEquals("{\"state\":\"menu\",\"server\":\"\",\"since\":1000,\"version\":\"26.3\"}", read(file));

        p.inWorld = true;
        p.connection = new ConnectionInfo(ConnectionInfo.Kind.MULTIPLAYER, "EineIP.DE:25565", 30);
        status.check(1010);
        assertEquals("{\"state\":\"multiplayer\",\"server\":\"eineip.de\",\"since\":1010,\"version\":\"26.3\"}", read(file));

        // same server: the time it started stays
        status.check(1500);
        assertTrue(read(file).contains("\"since\":1010"));

        p.connection = new ConnectionInfo(ConnectionInfo.Kind.MULTIPLAYER, "other.net:25566", 30);
        status.check(1600);
        assertTrue(read(file).contains("\"server\":\"other.net:25566\",\"since\":1600"));

        p.connection = new ConnectionInfo(ConnectionInfo.Kind.SINGLEPLAYER, "", -1);
        status.check(1700);
        assertTrue(read(file).startsWith("{\"state\":\"singleplayer\",\"server\":\"\",\"since\":1700"));
        assertFalse(Files.exists(file.resolveSibling("status.json.tmp")));
    }

    @Test
    void nothingWithoutTheLauncherProperty() {
        String before = System.getProperty(LauncherStatus.PROPERTY);
        System.clearProperty(LauncherStatus.PROPERTY);
        try {
            assertNull(LauncherStatus.fromSystemProperty(new FakePlatform(dir)::game, "26.3"));
        } finally {
            if (before != null) {
                System.setProperty(LauncherStatus.PROPERTY, before);
            }
        }
    }

    private static String read(Path f) throws Exception {
        return Files.readString(f, StandardCharsets.UTF_8);
    }
}
