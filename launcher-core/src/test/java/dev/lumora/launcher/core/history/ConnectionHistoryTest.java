package dev.lumora.launcher.core.history;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectionHistoryTest {
    @TempDir
    Path tmp;

    private static final Instant T0 = Instant.parse("2026-11-01T10:00:00Z");

    @Test
    void readsTheServerFromMinecraftLogLines() {
        // modern (plain and log4j XML layout) and 1.8.9
        assertEquals("mc.hypixel.net", ConnectionHistory.serverFromLog("[20:01:02] [Render thread/INFO]: Connecting to mc.hypixel.net, 25565"));
        assertEquals("play.example.org:25570", ConnectionHistory.serverFromLog("<log4j:Message><![CDATA[Connecting to Play.Example.org, 25570]]></log4j:Message>"));
        assertEquals("127.0.0.1", ConnectionHistory.serverFromLog("[Server Connector #1/INFO]: Connecting to 127.0.0.1, 25565"));
        assertNull(ConnectionHistory.serverFromLog("[Render thread/INFO]: Setting user: Steve"));
        assertNull(ConnectionHistory.serverFromLog("Connecting to <script>, 25565"));
        assertNull(ConnectionHistory.serverFromLog("Connecting to host, 999999"));
    }

    @Test
    void normalisesAddresses() {
        assertEquals("example.net", ConnectionHistory.normalise("Example.NET:25565"));
        assertEquals("example.net:1234", ConnectionHistory.normalise("example.net:1234"));
        assertEquals("[::1]:25570", ConnectionHistory.normalise("[::1]:25570"));
        assertEquals("[::1]", ConnectionHistory.normalise("[::1]"));
        assertNull(ConnectionHistory.normalise("example.net:abc"));
        assertNull(ConnectionHistory.normalise("exa mple.net"));
        assertNull(ConnectionHistory.normalise(""));
    }

    @Test
    void recordsEventsNewestFirstAndKeepsThemAcrossRestarts() throws IOException {
        Path file = tmp.resolve("config").resolve("connections.json");
        ConnectionHistory h = new ConnectionHistory(file);
        h.record(ConnectionHistory.Kind.SIGN_IN, "u1", "Steve", "", T0);
        h.record(ConnectionHistory.Kind.GAME_START, "u1", "Steve", "1.21.11", T0.plusSeconds(60));
        h.recordServer("mc.hypixel.net:25565", "Steve", "1.21.11", T0.plusSeconds(90));
        h.recordServer("MC.Hypixel.net", "Steve", "1.8.9", T0.plusSeconds(600));
        h.recordServer("play.example.org", "Alex", "26.3", T0.plusSeconds(300));

        ConnectionHistory again = new ConnectionHistory(file);
        List<ConnectionHistory.Event> events = again.events();
        assertEquals(2, events.size());
        assertEquals(ConnectionHistory.Kind.GAME_START, events.get(0).kind());
        assertEquals("1.21.11", events.get(0).detail());
        List<ConnectionHistory.Server> servers = again.servers();
        assertEquals(2, servers.size());
        assertEquals("mc.hypixel.net", servers.get(0).address());
        assertEquals(2, servers.get(0).visits());
        assertEquals("1.8.9", servers.get(0).version());
        assertEquals(T0.plusSeconds(90), servers.get(0).first());
        assertEquals(T0, again.span("u1")[0]);
        assertEquals(T0.plusSeconds(60), again.span("u1")[1]);
        assertNull(again.span("nobody"));
        // no tokens or anything secret in the file
        String json = Files.readString(file);
        assertFalse(json.toLowerCase().contains("token"));
    }

    @Test
    void mergesRenewalsAndCapsTheList() {
        ConnectionHistory h = new ConnectionHistory(tmp.resolve("c.json"));
        h.record(ConnectionHistory.Kind.REFRESH, "u1", "Steve", "", T0);
        h.record(ConnectionHistory.Kind.REFRESH, "u1", "Steve", "", T0.plus(Duration.ofHours(1)));
        assertEquals(1, h.events().size());
        assertEquals(T0.plus(Duration.ofHours(1)), h.events().get(0).time());
        h.record(ConnectionHistory.Kind.REFRESH, "u1", "Steve", "", T0.plus(Duration.ofHours(8)));
        assertEquals(2, h.events().size());
        for (int i = 0; i < ConnectionHistory.MAX_EVENTS + 20; i++) {
            h.record(ConnectionHistory.Kind.GAME_START, "u2", "Alex", "26.3", T0.plusSeconds(100_000 + i));
        }
        assertEquals(ConnectionHistory.MAX_EVENTS, h.events().size());
    }

    @Test
    void switchedOffRecordsNothingAndClearingWorks() {
        ConnectionHistory h = new ConnectionHistory(tmp.resolve("c.json"));
        h.recordServer("a.example", "Steve", "1.21.11", T0);
        h.setEnabled(false);
        h.record(ConnectionHistory.Kind.SIGN_IN, "u1", "Steve", "", T0);
        h.recordServer("b.example", "Steve", "1.21.11", T0);
        assertTrue(h.events().isEmpty());
        assertEquals(1, h.servers().size());
        h.removeServer("A.example:25565");
        assertTrue(h.servers().isEmpty());
        h.setEnabled(true);
        h.record(ConnectionHistory.Kind.SIGN_IN, "u1", "Steve", "", T0);
        h.clearEvents();
        assertTrue(h.events().isEmpty());
    }

    @Test
    void ignoresABrokenFile() throws IOException {
        Path file = tmp.resolve("c.json");
        Files.writeString(file, "{ kaputt");
        ConnectionHistory h = new ConnectionHistory(file);
        assertTrue(h.events().isEmpty());
        h.record(ConnectionHistory.Kind.SIGN_IN, "u1", "Steve", "", T0);
        assertEquals(1, new ConnectionHistory(file).events().size());
    }
}
