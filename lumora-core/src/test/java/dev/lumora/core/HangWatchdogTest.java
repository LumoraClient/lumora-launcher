package dev.lumora.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HangWatchdogTest {
    @TempDir
    Path game;

    @Test
    void oneDumpPerHangAndReArmedByTheNextTick() throws Exception {
        AtomicLong now = new AtomicLong(1_000_000);
        CoreLogger log = new CoreLogger() {
            @Override
            public void info(String message) {
            }

            @Override
            public void warn(String message) {
            }

            @Override
            public void warn(String message, Throwable error) {
            }

            @Override
            public void error(String message, Throwable error) {
            }
        };
        HangWatchdog w = new HangWatchdog(() -> game, log, now::get);
        assertNull(w.check(), "no tick yet: nothing to watch");
        w.tick();
        now.addAndGet(HangWatchdog.HANG_MILLIS - 1);
        assertNull(w.check());
        now.addAndGet(2);
        Path dump = w.check();
        assertNotNull(dump);
        String text = Files.readString(dump);
        assertTrue(text.contains("Description: Spiel reagiert nicht"), text);
        assertTrue(text.contains("\"" + Thread.currentThread().getName() + "\""), "the client thread first");
        assertNull(w.check(), "only once per hang");
        w.tick();
        now.addAndGet(HangWatchdog.HANG_MILLIS + 1);
        assertNotNull(w.check(), "a new hang after the game ticked again");
    }
}
