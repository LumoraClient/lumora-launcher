package dev.lumora.core.connection;

import dev.lumora.api.connection.ConnectionPhase;
import dev.lumora.api.connection.DisconnectKind;
import dev.lumora.api.connection.ServerConnection;
import dev.lumora.api.event.LumoraEvent;
import dev.lumora.api.event.connection.ConnectionPhaseEvent;
import dev.lumora.api.event.connection.ServerConnectingEvent;
import dev.lumora.api.event.connection.ServerDisconnectedEvent;
import dev.lumora.api.event.connection.ServerJoinedEvent;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Follows every server connection from "connect" to "disconnected" and turns what Minecraft does into phases, events
 * and a reason. The version adapter reports three things: a connect screen appeared ({@link #begin}), a disconnect
 * screen appeared ({@link #disconnected}) and the world was joined or left; everything in between is read from the
 * attempt on each client tick ({@link #tick}). Phases only move forward. A stuck phase before the world (no progress
 * for {@code timeoutMillis}) is cancelled and reported as {@link DisconnectKind#TIMEOUT}.
 * <p>
 * Runs on the client thread only; not thread-safe.
 */
public final class ConnectionTracker {
    /** Leaving the world without a disconnect screen within this time means the player left on purpose. */
    static final long LEAVE_GRACE_MILLIS = 400;

    /** What a finished connection leaves behind (for the disconnect screen and reconnects). */
    public record Ended(ServerConnection connection, ConnectionPhase lastPhase, DisconnectKind kind, String message,
                        long reconnectIn, int attempt) {
    }

    private static volatile ConnectionTracker instance;

    private final Consumer<LumoraEvent> events;
    private final LongSupplier clock;
    private ReconnectPolicy policy = new ReconnectPolicy(false, 5, 3);
    private long timeoutMillis = 30_000;

    private ConnectionAttempt attempt;
    private String name = "";
    private String address = "";
    private String remote = "";
    private ConnectionPhase phase;
    private long started;
    private long phaseSince;
    private long joined;
    private long leaveAt;
    private final Map<ConnectionPhase, Long> durations = new EnumMap<>(ConnectionPhase.class);
    private int attemptNo;
    private boolean reconnecting;
    /** The last end was guessed (world left without a disconnect screen), not reported. */
    private boolean guessedLeave;
    private Ended last;

    public ConnectionTracker(Consumer<LumoraEvent> events, LongSupplier clock) {
        this.events = events;
        this.clock = clock;
    }

    /** The tracker of the running client (set by the core). */
    public static ConnectionTracker get() {
        return instance;
    }

    public void installGlobal() {
        instance = this;
    }

    public void configure(ReconnectPolicy policy, int timeoutSeconds) {
        this.policy = policy;
        this.timeoutMillis = timeoutSeconds <= 0 ? 0 : Math.max(5, timeoutSeconds) * 1000L;
    }

    public ReconnectPolicy policy() {
        return policy;
    }

    // ---- reports from the version adapter

    /** Minecraft opened its connect screen. A pending {@link #markReconnect()} makes it the next automatic attempt. */
    public void begin(ConnectionAttempt a) {
        if (phase != null && phase != ConnectionPhase.DISCONNECTED && attempt != null && attempt != a && !joinedNow()) {
            // a new attempt replaced a running one (transfer to another server, or a second click)
            finish(DisconnectKind.USER, "", -1);
        }
        attempt = a;
        attemptNo = reconnecting ? attemptNo + 1 : 0;
        reconnecting = false;
        name = "";
        address = "";
        remote = "";
        durations.clear();
        started = clock.getAsLong();
        phaseSince = started;
        joined = 0;
        leaveAt = 0;
        phase = ConnectionPhase.CONNECTING;
        readTarget();
        events.accept(new ServerConnectingEvent(snapshot(), attemptNo));
    }

    /** The next {@link #begin} is a reconnect (attempt counter goes up instead of starting at 0). */
    public void markReconnect() {
        reconnecting = true;
    }

    /** The world was reached ({@code multiplayer}: on a server, not singleplayer or LAN host). */
    public void worldJoined(boolean multiplayer) {
        leaveAt = 0;
        if (!multiplayer || phase == null || phase == ConnectionPhase.DISCONNECTED || phase == ConnectionPhase.PLAYING) {
            return;
        }
        readTarget();
        move(ConnectionPhase.PLAYING);
        joined = clock.getAsLong();
        attempt = null; // the connect screen is gone; nothing to read anymore
        attemptNo = 0;
        events.accept(new ServerJoinedEvent(snapshot(), joined - started));
    }

    /** The world was left. Without a disconnect screen right after, the player left on purpose. */
    public void worldLeft() {
        if (phase == ConnectionPhase.PLAYING) {
            leaveAt = clock.getAsLong();
        }
    }

    /**
     * Minecraft shows its disconnect screen.
     *
     * @param keys translation keys in the reason (with arguments), {@code text} the reason as plain text
     * @return what ended (also kept as {@link #last()}); null if no connection was known (e.g. a mod's own screen)
     */
    public Ended disconnected(List<String> keys, String text) {
        if (phase == null) {
            return null;
        }
        DisconnectKind kind = DisconnectClassifier.classify(keys, text);
        if (phase == ConnectionPhase.DISCONNECTED) {
            boolean lateReason = guessedLeave && last != null && last.kind() == DisconnectKind.USER && kind != DisconnectKind.USER
                    && clock.getAsLong() - phaseSince < 3000;
            if (!lateReason) {
                return null; // already reported (timeout, cancel) or not a tracked server: Minecraft's screen
            }
            phase = last.lastPhase(); // the "left on purpose" guess was wrong: report the real reason
        }
        return finish(kind, text == null ? "" : text, policy.delayMillis(kind, attemptNo));
    }

    /** Called every client tick: reads the attempt, moves phases, watches for a stuck phase and a silent leave. */
    public void tick() {
        long now = clock.getAsLong();
        if (leaveAt > 0 && now - leaveAt > LEAVE_GRACE_MILLIS) {
            leaveAt = 0;
            finish(DisconnectKind.USER, "", -1);
            guessedLeave = true;
            return;
        }
        if (attempt == null || phase == null || phase.ordinal() >= ConnectionPhase.PLAYING.ordinal()) {
            return;
        }
        readTarget();
        ConnectionPhase next = phaseOf(attempt.stage(), attempt.statusKey());
        if (next != null && next.ordinal() > phase.ordinal()) {
            move(next);
        }
        String r = attempt.remoteAddress();
        if (r != null && !r.isEmpty()) {
            remote = r;
        }
        // stuck before the world: configuration can wait for resource pack prompts, so it has no timeout
        boolean watched = timeoutMillis > 0 && phase.ordinal() < ConnectionPhase.CONFIGURING.ordinal();
        if (watched && now - phaseSince > timeoutMillis) {
            ConnectionAttempt a = attempt;
            Ended e = finish(DisconnectKind.TIMEOUT, "", policy.delayMillis(DisconnectKind.TIMEOUT, attemptNo));
            a.cancel(); // back to the screen before connecting, then the module shows why
            onTimeout.accept(e);
        }
    }

    private Consumer<Ended> onTimeout = e -> { };

    /** What to do after a timeout cancelled the attempt (the module shows its disconnect screen). */
    public void onTimeout(Consumer<Ended> handler) {
        this.onTimeout = handler == null ? e -> { } : handler;
    }

    /** Cancels a running attempt (smoke test, or another part of Lumora that needs the connect screen gone). */
    public void cancelActive() {
        ConnectionAttempt a = attempt;
        if (a != null && active()) {
            cancelled();
            a.cancel();
        }
    }

    /** The user cancelled on the connecting screen. */
    public void cancelled() {
        if (phase != null && phase != ConnectionPhase.DISCONNECTED) {
            finish(DisconnectKind.USER, "", -1);
        }
    }

    /** Phase from Minecraft's connection state; null = no change visible. */
    static ConnectionPhase phaseOf(String stage, String statusKey) {
        String s = stage == null ? "" : stage;
        String k = statusKey == null ? "" : statusKey;
        return switch (s) {
            case "login" -> switch (k) {
                case "connect.authorizing" -> ConnectionPhase.AUTHENTICATING;
                case "connect.encrypting" -> ConnectionPhase.ENCRYPTING;
                // 1.20.1 and older have no configuration: "joining" already in the login listener
                case "connect.joining" -> ConnectionPhase.JOINING;
                default -> ConnectionPhase.LOGIN;
            };
            case "configuration" -> ConnectionPhase.CONFIGURING;
            case "play" -> ConnectionPhase.JOINING;
            default -> ConnectionPhase.CONNECTING;
        };
    }

    private void move(ConnectionPhase next) {
        long now = clock.getAsLong();
        ConnectionPhase previous = phase;
        long took = now - phaseSince;
        durations.put(previous, took);
        phase = next;
        phaseSince = now;
        events.accept(new ConnectionPhaseEvent(snapshot(), previous, took));
    }

    private Ended finish(DisconnectKind kind, String message, long reconnectIn) {
        guessedLeave = false;
        ConnectionPhase lastPhase = phase;
        long now = clock.getAsLong();
        if (lastPhase != null && lastPhase != ConnectionPhase.DISCONNECTED) {
            durations.put(lastPhase, now - phaseSince);
        }
        phase = ConnectionPhase.DISCONNECTED;
        phaseSince = now;
        attempt = null;
        leaveAt = 0;
        ServerConnection c = snapshot();
        last = new Ended(c, lastPhase, kind, message, reconnectIn, attemptNo);
        events.accept(new ServerDisconnectedEvent(c, lastPhase, kind, message, reconnectIn));
        return last;
    }

    private void readTarget() {
        if (attempt == null) {
            return;
        }
        String n = attempt.serverName();
        String a = attempt.serverAddress();
        if (n != null && !n.isEmpty()) {
            name = n;
        }
        if (a != null && !a.isEmpty()) {
            address = a;
        }
    }

    private boolean joinedNow() {
        return phase == ConnectionPhase.PLAYING;
    }

    // ---- reading

    /** The current (or last) connection; null before the first one. */
    public ServerConnection snapshot() {
        if (phase == null) {
            return null;
        }
        return new ServerConnection(name.isEmpty() ? address : name, address, phase, started, joined);
    }

    public ConnectionPhase phase() {
        return phase;
    }

    /** How long each finished phase took (ms) and how long the current one runs. */
    public long millis(ConnectionPhase p) {
        if (p == phase && phase != ConnectionPhase.DISCONNECTED) {
            return clock.getAsLong() - phaseSince;
        }
        Long d = durations.get(p);
        return d == null ? -1 : d;
    }

    /** Whether the phase was passed through (durations known) or is the current one. */
    public boolean reached(ConnectionPhase p) {
        return durations.containsKey(p) || p == phase;
    }

    public long elapsedMillis() {
        return phase == null ? 0 : clock.getAsLong() - started;
    }

    public String remoteAddress() {
        return remote;
    }

    public int attemptNumber() {
        return attemptNo;
    }

    /** The last finished connection (disconnect screen, reconnect target); null if none. */
    public Ended last() {
        return last;
    }

    public boolean active() {
        return phase != null && phase != ConnectionPhase.DISCONNECTED;
    }
}
