package dev.lumora.core.connection;

import dev.lumora.core.connection.WorldLoadAttempt.Stage;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Times the stages of one world load. Stages only move forward: Minecraft sometimes shows an earlier screen again
 * for a moment (for example the progress screen between two steps), that does not reset a stage that is already done.
 */
final class WorldLoadTracker {
    private final LongSupplier clock;
    private final long start;
    private final Map<Stage, Long> entered = new EnumMap<>(Stage.class);
    private Stage current;
    private long finished = -1;

    WorldLoadTracker(LongSupplier clock) {
        this.clock = clock;
        this.start = clock.getAsLong();
    }

    /** Called every frame with what the adapter reports; returns the stage now shown. */
    Stage update(Stage reported) {
        if (finished < 0 && reported != null && (current == null || reported.ordinal() > current.ordinal())) {
            current = reported;
            entered.put(reported, clock.getAsLong());
        }
        return current;
    }

    Stage current() {
        return current;
    }

    /** Whether the stage was really passed through (not skipped). */
    boolean reached(Stage s) {
        return entered.containsKey(s);
    }

    /** How long the stage took (still running: so far), -1 if it was not reached. */
    long millis(Stage s) {
        Long in = entered.get(s);
        if (in == null) {
            return -1;
        }
        long out = finished >= 0 ? finished : clock.getAsLong();
        for (Stage later : Stage.values()) {
            if (later.ordinal() > s.ordinal() && entered.containsKey(later)) {
                out = entered.get(later);
                break;
            }
        }
        return Math.max(0, out - in);
    }

    /** Time since the load started (frozen once finished). */
    long elapsedMillis() {
        return (finished >= 0 ? finished : clock.getAsLong()) - start;
    }

    /** The world is open (or the load ended otherwise): stop the clocks. */
    void finish() {
        if (finished < 0) {
            finished = clock.getAsLong();
        }
    }

    /** "read 120 ms, server 900 ms, ..." for the log (no world name). */
    String summary() {
        StringBuilder sb = new StringBuilder();
        for (Stage s : Stage.values()) {
            if (reached(s)) {
                sb.append(sb.isEmpty() ? "" : ", ").append(s.name().toLowerCase(java.util.Locale.ROOT)).append(' ')
                        .append(millis(s)).append(" ms");
            }
        }
        return sb.append(sb.isEmpty() ? "" : ", ").append("total ").append(elapsedMillis()).append(" ms").toString();
    }
}
