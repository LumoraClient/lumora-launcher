package dev.lumora.launcher.core.download;

import dev.lumora.launcher.core.testutil.LocalServer;
import dev.lumora.launcher.core.util.Hashing;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DownloaderTest {
    private static final byte[] DATA = "hello lumora".getBytes(StandardCharsets.UTF_8);
    private static final Hashing.Expected SHA1 = Hashing.Expected.sha1(Hashing.hash(DATA, Hashing.Algorithm.SHA1));

    @TempDir
    Path tmp;

    private static Downloader downloader(int attempts) {
        return new Downloader(HttpClient.newHttpClient(), Set.of(), true, attempts, Duration.ofMillis(1), 4);
    }

    @Test
    void downloadsAndVerifies() throws Exception {
        try (LocalServer s = new LocalServer().on("/f", (ex, n) -> LocalServer.Response.ok(DATA))) {
            Path target = tmp.resolve("a/b/file.bin");
            assertTrue(downloader(3).download(new Downloader.Task(s.uri("/f"), target, SHA1, DATA.length), null));
            assertArrayEquals(DATA, Files.readAllBytes(target));
        }
    }

    @Test
    void existingValidFileIsNotDownloadedAgain() throws Exception {
        try (LocalServer s = new LocalServer().on("/f", (ex, n) -> LocalServer.Response.ok(DATA))) {
            Path target = tmp.resolve("file.bin");
            Files.write(target, DATA);
            assertFalse(downloader(3).download(new Downloader.Task(s.uri("/f"), target, SHA1, DATA.length), null));
            assertEquals(0, s.count("/f"));
        }
    }

    @Test
    void corruptExistingFileIsRepaired() throws Exception {
        try (LocalServer s = new LocalServer().on("/f", (ex, n) -> LocalServer.Response.ok(DATA))) {
            Path target = tmp.resolve("file.bin");
            Files.writeString(target, "corrupted!!!");
            assertTrue(downloader(3).download(new Downloader.Task(s.uri("/f"), target, SHA1, DATA.length), null));
            assertArrayEquals(DATA, Files.readAllBytes(target));
        }
    }

    @Test
    void quickModeTrustsMatchingSizeButFullModeDoesNot() throws Exception {
        try (LocalServer s = new LocalServer().on("/f", (ex, n) -> LocalServer.Response.ok(DATA))) {
            Path target = tmp.resolve("file.bin");
            byte[] sameSize = new byte[DATA.length];
            Files.write(target, sameSize);
            var task = new Downloader.Task(s.uri("/f"), target, SHA1, DATA.length);
            assertFalse(downloader(3).download(task, null, Downloader.Verify.QUICK));
            assertTrue(downloader(3).download(task, null, Downloader.Verify.FULL));
            assertArrayEquals(DATA, Files.readAllBytes(target));
        }
    }

    @Test
    void hashMismatchFailsAndLeavesNoFile() throws Exception {
        try (LocalServer s = new LocalServer().on("/f", (ex, n) -> LocalServer.Response.ok("tampered".getBytes()))) {
            Path target = tmp.resolve("file.bin");
            IOException e = assertThrows(IOException.class,
                    () -> downloader(2).download(new Downloader.Task(s.uri("/f"), target, SHA1, DATA.length), null));
            assertTrue(e.getMessage().contains("Checksum mismatch"), e.getMessage());
            assertFalse(Files.exists(target));
            try (Stream<Path> files = Files.list(tmp)) {
                assertEquals(0, files.count(), "no temp files may remain");
            }
            assertEquals(2, s.count("/f"));
        }
    }

    @Test
    void retriesTransientServerErrors() throws Exception {
        try (LocalServer s = new LocalServer().on("/f", (ex, n) -> n < 3
                ? new LocalServer.Response(503, new byte[0], "text/plain") : LocalServer.Response.ok(DATA))) {
            Path target = tmp.resolve("file.bin");
            assertTrue(downloader(4).download(new Downloader.Task(s.uri("/f"), target, SHA1, DATA.length), null));
            assertEquals(3, s.count("/f"));
        }
    }

    @Test
    void doesNotRetryNotFound() throws Exception {
        try (LocalServer s = new LocalServer().on("/f", (ex, n) -> new LocalServer.Response(404, new byte[0], "text/plain"))) {
            assertThrows(IOException.class, () -> downloader(4).download(
                    new Downloader.Task(s.uri("/f"), tmp.resolve("x"), SHA1, DATA.length), null));
            assertEquals(1, s.count("/f"));
        }
    }

    @Test
    void refusesUntrustedHostsAndPlainHttp() {
        Downloader d = new Downloader();
        assertThrows(IOException.class, () -> d.checkTrusted(URI.create("https://evil.example.com/x.jar")));
        assertThrows(IOException.class, () -> d.checkTrusted(URI.create("http://cdn.modrinth.com/x.jar")));
        assertThrows(IOException.class, () -> d.checkTrusted(URI.create("http://127.0.0.1:1234/x.jar")));
    }

    @Test
    void trustsOfficialHosts() throws IOException {
        Downloader d = new Downloader();
        d.checkTrusted(URI.create("https://cdn.modrinth.com/data/x.jar"));
        d.checkTrusted(URI.create("https://piston-data.mojang.com/v1/objects/abc/client.jar"));
        d.checkTrusted(URI.create("https://maven.fabricmc.net/net/fabricmc/fabric-loader/0.19.5/fabric-loader-0.19.5.jar"));
    }

    @Test
    void downloadAllReportsAllFailures() throws Exception {
        try (LocalServer s = new LocalServer()
                .on("/good", (ex, n) -> LocalServer.Response.ok(DATA))
                .on("/bad", (ex, n) -> LocalServer.Response.ok("nope".getBytes()))) {
            List<Downloader.Task> tasks = List.of(
                    new Downloader.Task(s.uri("/good"), tmp.resolve("g"), SHA1, DATA.length),
                    new Downloader.Task(s.uri("/bad"), tmp.resolve("b1"), SHA1, DATA.length),
                    new Downloader.Task(s.uri("/bad"), tmp.resolve("b2"), SHA1, DATA.length));
            Downloader.DownloadException e = assertThrows(Downloader.DownloadException.class,
                    () -> downloader(1).downloadAll(tasks, null));
            assertEquals(2, e.failed().size());
            assertTrue(Files.exists(tmp.resolve("g")), "successful downloads are kept for the next attempt");
        }
    }

    @Test
    void expectedHashValidation() {
        assertThrows(IllegalArgumentException.class, () -> Hashing.Expected.sha1("xyz"));
        assertThrows(IllegalArgumentException.class, () -> Hashing.Expected.sha512("ab"));
        assertThrows(IllegalArgumentException.class, () -> new Downloader.Task(URI.create("https://x"), tmp, null, 0));
    }

    @Test
    void fetchVerifiedChecksDocumentHash() throws Exception {
        try (LocalServer s = new LocalServer().on("/m.json", (ex, n) -> LocalServer.Response.ok(DATA))) {
            assertEquals("hello lumora", downloader(1).fetchVerified(s.uri("/m.json"), SHA1));
            assertThrows(IOException.class, () -> downloader(1).fetchVerified(s.uri("/m.json"),
                    Hashing.Expected.sha1("0000000000000000000000000000000000000000")));
        }
    }
}
