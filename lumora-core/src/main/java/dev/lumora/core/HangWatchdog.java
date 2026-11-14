package dev.lumora.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Launcher setting "Thread-Dump bei Hänger" ({@code -Dlumora.hangDump=true}): when the game's client thread has not
 * ticked for {@link #HANG_MILLIS}, the stack traces of all threads go to {@code crash-reports/lumora-hang-<time>.txt}
 * (a few kilobytes of text, shown on the launcher's crash page). One file per hang; it re-arms once the game ticks
 * again. Without the property nothing runs. Version independent.
 */
final class HangWatchdog {
    static final String PROPERTY = "lumora.hangDump";
    static final long HANG_MILLIS = 30_000;
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss").withZone(ZoneId.systemDefault());

    private final Supplier<Path> gameDir;
    private final CoreLogger log;
    private final LongSupplier clock;
    private volatile long lastTick;
    private volatile Thread client;
    private volatile boolean dumped;

    HangWatchdog(Supplier<Path> gameDir, CoreLogger log, LongSupplier clock) {
        this.gameDir = gameDir;
        this.log = log;
        this.clock = clock;
        this.lastTick = clock.getAsLong();
    }

    /** The watchdog for this game (thread started), or null if the launcher did not ask for one. */
    static HangWatchdog fromSystemProperty(Supplier<Path> gameDir, CoreLogger log) {
        if (!Boolean.getBoolean(PROPERTY)) {
            return null;
        }
        HangWatchdog w = new HangWatchdog(gameDir, log, System::currentTimeMillis);
        Thread t = new Thread(w::watch, "Lumora hang watchdog");
        t.setDaemon(true);
        t.start();
        return w;
    }

    /** Called on every client tick (on the client thread). */
    void tick() {
        if (client == null) {
            client = Thread.currentThread();
        }
        lastTick = clock.getAsLong();
        dumped = false;
    }

    private void watch() {
        while (true) {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                return;
            }
            check();
        }
    }

    /** Writes the dump if the client thread hangs; returns the file written, or null. */
    Path check() {
        Thread c = client;
        long since = clock.getAsLong() - lastTick;
        if (c == null || dumped || since < HANG_MILLIS || !c.isAlive()) {
            return null;
        }
        dumped = true;
        try {
            Path dir = gameDir.get().resolve("crash-reports");
            Files.createDirectories(dir);
            Instant now = Instant.ofEpochMilli(clock.getAsLong());
            Path file = dir.resolve("lumora-hang-" + FILE_TIME.format(now) + ".txt");
            Files.writeString(file, dump(c, since, now), StandardCharsets.UTF_8);
            log.warn("Client thread hangs for " + since / 1000 + " s, thread dump: " + file);
            return file;
        } catch (IOException | RuntimeException e) {
            log.warn("Could not write the thread dump: " + e);
            return null;
        }
    }

    static String dump(Thread client, long since, Instant now) {
        StringBuilder s = new StringBuilder("---- Lumora Thread-Dump ----\n");
        s.append("Time: ").append(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault()).format(now)).append('\n');
        s.append("Description: Spiel reagiert nicht (Client-Thread hängt seit ").append(since / 1000).append(" s)\n\n");
        Map<Thread, StackTraceElement[]> all = Thread.getAllStackTraces();
        append(s, client, all.getOrDefault(client, client.getStackTrace()));
        for (Map.Entry<Thread, StackTraceElement[]> e : all.entrySet()) {
            if (e.getKey() != client) {
                append(s, e.getKey(), e.getValue());
            }
        }
        return s.toString();
    }

    private static void append(StringBuilder s, Thread t, StackTraceElement[] trace) {
        s.append('"').append(t.getName()).append('"').append(t.isDaemon() ? " daemon" : "").append(" state=").append(t.getState()).append('\n');
        for (StackTraceElement el : trace) {
            s.append("    at ").append(el).append('\n');
        }
        s.append('\n');
    }
}
