package dev.lumora.core.connection;

import dev.lumora.api.connection.ConnectionPhase;
import dev.lumora.api.connection.DisconnectKind;
import dev.lumora.api.event.LumoraEvent;
import dev.lumora.api.event.connection.ConnectionPhaseEvent;
import dev.lumora.api.event.connection.ServerConnectingEvent;
import dev.lumora.api.event.connection.ServerDisconnectedEvent;
import dev.lumora.api.event.connection.ServerJoinedEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectionTrackerTest {
    /** A connect screen whose state the test moves forward. */
    static final class FakeAttempt implements ConnectionAttempt {
        String name = "";
        String address = "";
        String stage = "";
        String status = "connect.connecting";
        String remote = "";
        int cancels;

        @Override public String serverName() { return name; }
        @Override public String serverAddress() { return address; }
        @Override public String stage() { return stage; }
        @Override public String statusKey() { return status; }
        @Override public String remoteAddress() { return remote; }
        @Override public void cancel() { cancels++; }
    }

    private final AtomicLong now = new AtomicLong(1_000_000);
    private final List<LumoraEvent> events = new ArrayList<>();
    private final ConnectionTracker tracker = new ConnectionTracker(events::add, now::get);

    private void advance(long ms) {
        now.addAndGet(ms);
        tracker.tick();
    }

    private <T> List<T> of(Class<T> type) {
        return events.stream().filter(type::isInstance).map(type::cast).toList();
    }

    @Test
    void followsAnOnlineModeJoinPhaseByPhase() {
        FakeAttempt a = new FakeAttempt();
        tracker.begin(a);
        a.name = "Lumora Test";
        a.address = "play.example.net";
        advance(120);
        a.stage = "login";
        a.status = "connect.connecting";
        a.remote = "203.0.113.5:25565";
        advance(40);
        a.status = "connect.authorizing";
        advance(300);
        a.status = "connect.encrypting";
        advance(30);
        a.stage = "configuration";
        a.status = "connect.joining";
        advance(500);
        a.stage = "play";
        advance(10);
        tracker.worldJoined(true);

        List<ConnectionPhase> phases = of(ConnectionPhaseEvent.class).stream().map(e -> e.connection().phase()).toList();
        assertEquals(List.of(ConnectionPhase.LOGIN, ConnectionPhase.AUTHENTICATING, ConnectionPhase.ENCRYPTING,
                ConnectionPhase.CONFIGURING, ConnectionPhase.JOINING, ConnectionPhase.PLAYING), phases);
        assertEquals(1, of(ServerConnectingEvent.class).size());
        ServerJoinedEvent joined = of(ServerJoinedEvent.class).getFirst();
        assertEquals("Lumora Test", joined.connection().name());
        assertEquals("play.example.net", joined.connection().address());
        assertEquals(1000, joined.connectMillis());
        assertEquals(30, tracker.millis(ConnectionPhase.AUTHENTICATING), "from the first tick that saw it to the next phase");
        assertEquals("203.0.113.5:25565", tracker.remoteAddress());
    }

    @Test
    void offlineServersSkipAuthenticationAndOldVersionsJoinFromLogin() {
        FakeAttempt a = new FakeAttempt();
        tracker.begin(a);
        a.stage = "login";
        advance(50);
        a.status = "connect.joining"; // 1.20.1: no configuration phase
        advance(50);
        assertEquals(ConnectionPhase.JOINING, tracker.phase());
        assertFalse(tracker.reached(ConnectionPhase.AUTHENTICATING));
        assertTrue(tracker.reached(ConnectionPhase.LOGIN));
    }

    @Test
    void phasesNeverGoBackwards() {
        FakeAttempt a = new FakeAttempt();
        tracker.begin(a);
        a.stage = "configuration";
        advance(10);
        a.stage = "login";
        advance(10);
        assertEquals(ConnectionPhase.CONFIGURING, tracker.phase());
    }

    @Test
    void refusedConnectionIsClassifiedAndReconnectsWithBackoff() {
        tracker.configure(new ReconnectPolicy(true, 5, 3), 30);
        FakeAttempt a = new FakeAttempt();
        a.address = "localhost:25599";
        tracker.begin(a);
        advance(200);
        ConnectionTracker.Ended e = tracker.disconnected(List.of("connect.failed", "disconnect.genericReason"),
                "Failed to connect to the server\nio.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: getsockopt");
        assertNotNull(e);
        assertEquals(DisconnectKind.REFUSED, e.kind());
        assertEquals(ConnectionPhase.CONNECTING, e.lastPhase());
        assertEquals(5000, e.reconnectIn());
        ServerDisconnectedEvent ev = of(ServerDisconnectedEvent.class).getFirst();
        assertEquals(DisconnectKind.REFUSED, ev.kind());

        // automatic attempt 2 waits twice as long, attempt 4 does not happen (3 attempts)
        tracker.markReconnect();
        tracker.begin(new FakeAttempt());
        assertEquals(1, tracker.attemptNumber());
        assertEquals(10_000, tracker.disconnected(List.of(), "Connection refused").reconnectIn());
        tracker.markReconnect();
        tracker.begin(new FakeAttempt());
        assertEquals(20_000, tracker.disconnected(List.of(), "Connection refused").reconnectIn());
        tracker.markReconnect();
        tracker.begin(new FakeAttempt());
        assertEquals(-1, tracker.disconnected(List.of(), "Connection refused").reconnectIn());
        // a manual connect starts counting again
        tracker.begin(new FakeAttempt());
        assertEquals(0, tracker.attemptNumber());
    }

    @Test
    void bansAndAuthFailuresNeverReconnectAutomatically() {
        tracker.configure(new ReconnectPolicy(true, 2, 5), 30);
        tracker.begin(new FakeAttempt());
        assertEquals(DisconnectKind.BANNED, tracker.disconnected(List.of("multiplayer.disconnect.banned.reason"), "You are banned").kind());
        assertEquals(-1, tracker.last().reconnectIn());
        tracker.begin(new FakeAttempt());
        ConnectionTracker.Ended auth = tracker.disconnected(List.of("disconnect.loginFailedInfo", "disconnect.loginFailedInfo.invalidSession"), "Failed to log in");
        assertEquals(DisconnectKind.AUTH, auth.kind());
        assertEquals(-1, auth.reconnectIn());
    }

    @Test
    void watchdogCancelsAHangingPhaseButNotConfiguration() {
        tracker.configure(new ReconnectPolicy(false, 5, 3), 10);
        List<ConnectionTracker.Ended> timeouts = new ArrayList<>();
        tracker.onTimeout(timeouts::add);
        FakeAttempt a = new FakeAttempt();
        tracker.begin(a);
        a.stage = "login";
        advance(10);
        advance(9_000);
        assertEquals(0, a.cancels);
        advance(1_500);
        assertEquals(1, a.cancels);
        assertEquals(DisconnectKind.TIMEOUT, timeouts.getFirst().kind());
        assertEquals(ConnectionPhase.LOGIN, timeouts.getFirst().lastPhase());

        FakeAttempt b = new FakeAttempt();
        tracker.begin(b);
        b.stage = "configuration"; // resource pack prompts may take long
        advance(100);
        advance(60_000);
        assertEquals(0, b.cancels);
    }

    @Test
    void kickWhilePlayingUsesTheRealReasonEvenAfterTheWorldWasLeft() {
        FakeAttempt a = new FakeAttempt();
        a.address = "mc.example.org";
        tracker.begin(a);
        a.stage = "play";
        advance(100);
        tracker.worldJoined(true);
        advance(60_000);
        // Minecraft leaves the world first, then opens its disconnect screen
        tracker.worldLeft();
        now.addAndGet(50);
        ConnectionTracker.Ended e = tracker.disconnected(List.of("multiplayer.disconnect.server_shutdown"), "Server closed");
        assertEquals(DisconnectKind.SHUTDOWN, e.kind());
        assertEquals(ConnectionPhase.PLAYING, e.lastPhase());
        assertTrue(e.connection().joined());
    }

    @Test
    void leavingWithoutDisconnectScreenIsTheUsersChoice() {
        FakeAttempt a = new FakeAttempt();
        tracker.begin(a);
        a.stage = "play";
        advance(10);
        tracker.worldJoined(true);
        tracker.worldLeft();
        advance(ConnectionTracker.LEAVE_GRACE_MILLIS + 10);
        assertEquals(DisconnectKind.USER, of(ServerDisconnectedEvent.class).getLast().kind());
        // a late disconnect screen still corrects it
        ConnectionTracker.Ended late = tracker.disconnected(List.of("multiplayer.disconnect.kicked"), "Kicked by an operator");
        assertNotNull(late);
        assertEquals(DisconnectKind.KICKED, late.kind());
    }

    @Test
    void unknownDisconnectScreensKeepMinecraftsScreen() {
        assertNull(tracker.disconnected(List.of(), "Something"));
        tracker.begin(new FakeAttempt());
        tracker.cancelled();
        assertNull(tracker.disconnected(List.of("disconnect.lost"), "Connection lost"), "already ended by the cancel");
    }

    @Test
    void singleplayerWorldsAreNoServerJoins() {
        tracker.worldJoined(false);
        assertTrue(of(ServerJoinedEvent.class).isEmpty());
    }

    @Test
    void classifierKnowsKeysAndServerTexts() {
        assertEquals(DisconnectKind.WHITELIST, DisconnectClassifier.classify(List.of("multiplayer.disconnect.not_whitelisted"), ""));
        assertEquals(DisconnectKind.THROTTLED, DisconnectClassifier.classify(List.of(), "Connection throttled! Please wait before reconnecting."));
        assertEquals(DisconnectKind.UNKNOWN_HOST, DisconnectClassifier.classify(List.of("disconnect.unknownHost"), "Unknown host"));
        assertEquals(DisconnectKind.TIMEOUT, DisconnectClassifier.classify(List.of("disconnect.timeout"), "Timed out"));
        assertEquals(DisconnectKind.OUTDATED, DisconnectClassifier.classify(List.of(), "Outdated client! Please use 1.21.4"));
        assertEquals(DisconnectKind.LOST, DisconnectClassifier.classify(List.of("disconnect.lost", "disconnect.endOfStream"), "End of stream"));
        assertEquals(DisconnectKind.BANNED, DisconnectClassifier.classify(List.of(), "§cDu wurdest vom Netzwerk gebannt"));
        assertEquals(DisconnectKind.OTHER, DisconnectClassifier.classify(List.of(), ""));
    }

    @Test
    void reconnectPolicyLimitsAndMinimums() {
        ReconnectPolicy p = new ReconnectPolicy(true, 3, 4);
        assertEquals(3000, p.delayMillis(DisconnectKind.LOST, 0));
        assertEquals(24_000, p.delayMillis(DisconnectKind.LOST, 3));
        assertEquals(-1, p.delayMillis(DisconnectKind.LOST, 4));
        assertEquals(10_000, p.delayMillis(DisconnectKind.THROTTLED, 0));
        assertEquals(15_000, p.delayMillis(DisconnectKind.FULL, 0));
        assertEquals(60_000, new ReconnectPolicy(true, 30, 10).delayMillis(DisconnectKind.TIMEOUT, 5));
        assertEquals(-1, new ReconnectPolicy(false, 3, 4).delayMillis(DisconnectKind.LOST, 0));
        assertEquals(-1, p.delayMillis(DisconnectKind.KICKED, 0));
    }
}
