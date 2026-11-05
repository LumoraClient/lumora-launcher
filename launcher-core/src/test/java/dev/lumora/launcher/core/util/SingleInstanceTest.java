package dev.lumora.launcher.core.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** One launcher per Lumora folder: a second start reaches the first one instead of opening another window. */
class SingleInstanceTest {
    @TempDir
    Path dir;

    private static void await(List<?> list, int size) throws InterruptedException {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (list.size() < size && System.nanoTime() < end) {
            Thread.sleep(10);
        }
    }

    @Test
    void aSecondStartAsksTheFirstToShowItsWindow() throws Exception {
        try (SingleInstance first = SingleInstance.claim(dir).orElseThrow()) {
            assertTrue(SingleInstance.claim(dir).isEmpty(), "the lock is taken");
            List<SingleInstance.Request> got = new CopyOnWriteArrayList<>();
            first.onRequest(got::add);
            assertTrue(SingleInstance.signal(dir, SingleInstance.Request.SHOW, Duration.ofSeconds(2)));
            assertTrue(SingleInstance.signal(dir, SingleInstance.Request.PING, Duration.ofSeconds(2)));
            await(got, 2);
            assertEquals(List.of(SingleInstance.Request.SHOW, SingleInstance.Request.PING), got);
        }
    }

    @Test
    void aShowBeforeTheWindowExistsIsDeliveredLater() throws Exception {
        try (SingleInstance first = SingleInstance.claim(dir).orElseThrow()) {
            assertTrue(SingleInstance.signal(dir, SingleInstance.Request.SHOW, Duration.ofSeconds(2)));
            List<SingleInstance.Request> got = new CopyOnWriteArrayList<>();
            first.onRequest(got::add);
            assertEquals(List.of(SingleInstance.Request.SHOW), got);
        }
    }

    @Test
    void closingFreesTheLockAndRemovesThePortFile() throws Exception {
        SingleInstance first = SingleInstance.claim(dir).orElseThrow();
        assertTrue(Files.isRegularFile(dir.resolve(SingleInstance.PORT_FILE)));
        first.close();
        assertFalse(Files.exists(dir.resolve(SingleInstance.PORT_FILE)));
        long start = System.nanoTime();
        assertFalse(SingleInstance.signal(dir, SingleInstance.Request.SHOW, Duration.ofSeconds(5)), "nobody listens");
        assertTrue(System.nanoTime() - start < TimeUnit.SECONDS.toNanos(2), "a free lock ends the waiting at once");
        SingleInstance second = SingleInstance.claim(dir).orElseThrow();
        assertTrue(Files.isRegularFile(dir.resolve(SingleInstance.PORT_FILE)));
        second.close();
    }

    @Test
    void requestsWithoutTheTokenAreRefused() throws Exception {
        try (SingleInstance first = SingleInstance.claim(dir).orElseThrow()) {
            List<SingleInstance.Request> got = new CopyOnWriteArrayList<>();
            first.onRequest(got::add);
            int port = Integer.parseInt(Files.readString(dir.resolve(SingleInstance.PORT_FILE)).split(" ")[0]);
            try (Socket s = new Socket(InetAddress.getLoopbackAddress(), port)) {
                OutputStream out = s.getOutputStream();
                out.write("wrong SHOW\n".getBytes(StandardCharsets.US_ASCII));
                out.flush();
                assertEquals('N', s.getInputStream().read());
            }
            assertTrue(SingleInstance.signal(dir, SingleInstance.Request.PING, Duration.ofSeconds(2)));
            await(got, 1);
            assertEquals(List.of(SingleInstance.Request.PING), got, "only the request with the token");
        }
    }
}
