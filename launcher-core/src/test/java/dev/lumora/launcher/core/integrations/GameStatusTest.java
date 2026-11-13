package dev.lumora.launcher.core.integrations;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameStatusTest {
    @TempDir
    Path dir;

    @Test
    void serverLineWithTheTimeSinceJoining() throws Exception {
        Path f = GameStatus.file(dir);
        Files.createDirectories(f.getParent());
        Files.writeString(f, "{\"state\":\"multiplayer\",\"server\":\"eineip.de\",\"since\":1800000100,\"version\":\"26.3\"}");
        GameStatus s = GameStatus.read(f).orElseThrow();
        DiscordPresence.Activity a = s.activity("Lumora", "26.1.2", 1_800_000_000L, true);
        assertEquals("Lumora 26.3", a.details());
        assertEquals("auf eineip.de", a.state());
        assertEquals(1_800_000_100L, a.startEpochSeconds());
        // server hidden in the settings
        assertEquals("Mehrspieler", s.activity("Lumora", "26.3", 1, false).state());
    }

    @Test
    void menuAndSingleplayerCountFromTheirStart() throws Exception {
        Path f = GameStatus.file(dir);
        Files.createDirectories(f.getParent());
        Files.writeString(f, "{\"state\":\"menu\",\"server\":\"\",\"since\":1800000500,\"version\":\"26.3\"}");
        DiscordPresence.Activity menu = GameStatus.read(f).orElseThrow().activity("Lumora", "26.3", 1_800_000_000L, true);
        assertEquals("Im Hauptmenü", menu.state());
        assertEquals(1_800_000_000L, menu.startEpochSeconds(), "the menu counts from the game start");
        Files.writeString(f, "{\"state\":\"singleplayer\",\"server\":\"\",\"since\":1800000600,\"version\":\"\"}");
        DiscordPresence.Activity sp = GameStatus.read(f).orElseThrow().activity("Lumora", "26.2", 1_800_000_000L, true);
        assertEquals("Lumora 26.2", sp.details(), "the profile's version if the game sent none");
        assertEquals("Einzelspieler", sp.state());
        assertEquals(1_800_000_600L, sp.startEpochSeconds());
    }

    @Test
    void brokenOrMissingFilesAndOddServersAreIgnored() throws Exception {
        Path f = GameStatus.file(dir);
        assertTrue(GameStatus.read(f).isEmpty());
        Files.createDirectories(f.getParent());
        Files.writeString(f, "{\"state\":");
        assertTrue(GameStatus.read(f).isEmpty());
        Files.writeString(f, "{\"state\":\"flying\"}");
        assertTrue(GameStatus.read(f).isEmpty());
        Files.writeString(f, "{\"state\":\"multiplayer\",\"server\":\"evil text\nhere\",\"since\":null,\"version\":null}");
        GameStatus s = GameStatus.read(f).orElseThrow();
        assertEquals("", s.server());
        assertEquals("Mehrspieler", s.activity("Lumora", "26.3", 5, true).state());
        assertEquals(5, s.activity("Lumora", "26.3", 5, true).startEpochSeconds());
    }
}
