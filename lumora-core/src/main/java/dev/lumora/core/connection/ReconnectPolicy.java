package dev.lumora.core.connection;

import dev.lumora.api.connection.DisconnectKind;

/**
 * When Lumora reconnects by itself: only for reasons where trying again can help ({@link DisconnectKind#retryable()}),
 * a limited number of times and with a growing pause (base delay, doubled per attempt, at most one minute). Servers
 * that throttle, are full or restart get at least their usual waiting time. Bans, whitelists, wrong versions, a second
 * login elsewhere and failed account checks never reconnect automatically (it would only repeat the error or kick the
 * other session).
 *
 * @param enabled     the setting "Automatisch neu verbinden"
 * @param baseSeconds delay before the first attempt
 * @param maxAttempts attempts in a row (reset once a world was reached)
 */
public record ReconnectPolicy(boolean enabled, int baseSeconds, int maxAttempts) {
    static final long MAX_DELAY_MILLIS = 60_000;

    /** Milliseconds to wait before attempt number {@code attempt} (0 = first retry), -1 for no automatic reconnect. */
    public long delayMillis(DisconnectKind kind, int attempt) {
        if (!enabled || kind == null || !kind.retryable() || attempt >= maxAttempts || attempt < 0) {
            return -1;
        }
        long delay = Math.max(1, baseSeconds) * 1000L << Math.min(attempt, 6);
        long minimum = switch (kind) {
            case THROTTLED -> 10_000;
            case FULL, SHUTDOWN -> 15_000;
            default -> 0;
        };
        return Math.min(MAX_DELAY_MILLIS, Math.max(delay, minimum));
    }
}
