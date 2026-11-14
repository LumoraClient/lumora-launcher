package dev.lumora.launcher.core.update;

import com.sun.net.httpserver.HttpServer;
import dev.lumora.launcher.core.download.Downloader;
import dev.lumora.launcher.core.util.Hashing;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LauncherUpdateTest {
    @TempDir
    Path tmp;
    HttpServer server;
    String base;
    final Map<String, byte[]> files = new HashMap<>();
    KeyPair keys;
    final byte[] exe = "MZ new launcher".getBytes(StandardCharsets.UTF_8);

    @BeforeEach
    void start() throws Exception {
        keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        base = "http://127.0.0.1:" + server.getAddress().getPort();
        server.createContext("/", ex -> {
            byte[] b = files.get(ex.getRequestURI().getPath());
            if (b == null) {
                ex.sendResponseHeaders(404, -1);
            } else {
                ex.sendResponseHeaders(200, b.length);
                ex.getResponseBody().write(b);
            }
            ex.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    void publish(String tag, byte[] exeBytes, byte[] sumsFor, java.security.PrivateKey signer) throws Exception {
        String sums = Hashing.hash(sumsFor, Hashing.Algorithm.SHA256) + "  LumoraClient.exe\n" + "0".repeat(64) + "  other.zip\n";
        Signature s = Signature.getInstance("Ed25519");
        s.initSign(signer);
        s.update(sums.getBytes(StandardCharsets.UTF_8));
        files.put("/dl/LumoraClient.exe", exeBytes);
        files.put("/dl/SHA256SUMS.txt", sums.getBytes(StandardCharsets.UTF_8));
        files.put("/dl/SHA256SUMS.txt.sig", Base64.getEncoder().encode(s.sign()));
        files.put("/api/releases/latest", ("""
                {"tag_name":"%s","name":"Lumora %s","body":"Neu: Self-Update","html_url":"https://github.com/LumoraClient/lumora-launcher/releases/tag/%s",
                 "draft":false,"prerelease":false,"assets":[
                  {"name":"LumoraClient.exe","size":%d,"browser_download_url":"%s/dl/LumoraClient.exe"},
                  {"name":"SHA256SUMS.txt","size":1,"browser_download_url":"%s/dl/SHA256SUMS.txt"},
                  {"name":"SHA256SUMS.txt.sig","size":1,"browser_download_url":"%s/dl/SHA256SUMS.txt.sig"}]}
                """).formatted(tag, tag, tag, exeBytes.length, base, base, base).getBytes(StandardCharsets.UTF_8));
    }

    LauncherUpdate updater() {
        Downloader d = new Downloader(HttpClient.newHttpClient(), Set.of(), true, 1, Duration.ofMillis(1), 1);
        return new LauncherUpdate(d, base + "/api", keys.getPublic());
    }

    @Test
    void preReleaseChannelOffersTheNewestIncludingPreReleases() throws Exception {
        publish("v0.2.0", exe, exe, keys.getPrivate());
        String stable = new String(files.get("/api/releases/latest"), StandardCharsets.UTF_8);
        String pre = stable.replace("v0.2.0", "v0.3.0-beta.1").replace("\"prerelease\":false", "\"prerelease\":true");
        String draft = stable.replace("v0.2.0", "v0.4.0").replace("\"draft\":false", "\"draft\":true");
        files.put("/api/releases", ("[" + draft + "," + pre + "," + stable + "]").getBytes(StandardCharsets.UTF_8));
        assertEquals("0.2.0", updater().check("0.1.0").orElseThrow().version(), "stable channel: no pre-release");
        assertEquals("0.3.0-beta.1", updater().check("0.1.0", true).orElseThrow().version(), "drafts never count");
        assertTrue(updater().check("0.3.0", true).isEmpty(), "0.3.0 is newer than its beta");
    }

    @Test
    void installsOnlyANewerSignedRelease() throws Exception {
        publish("v0.2.0", exe, exe, keys.getPrivate());
        Optional<LauncherUpdate.Release> r = updater().check("0.1.0");
        assertTrue(r.isPresent());
        assertEquals("0.2.0", r.get().version());
        assertTrue(updater().check("0.2.0").isEmpty(), "same version: no update");
        Path downloaded = updater().download(r.get(), tmp.resolve("dl"));
        assertArrayEquals(exe, Files.readAllBytes(downloaded));

        // replace the portable EXE the user started
        Path portable = tmp.resolve("Spiele/LumoraClient.exe");
        Files.createDirectories(portable.getParent());
        Files.writeString(portable, "MZ old launcher");
        LauncherUpdate.install(downloaded, portable);
        assertArrayEquals(exe, Files.readAllBytes(portable));
        assertFalse(Files.exists(portable.resolveSibling("LumoraClient.exe.new")));
    }

    @Test
    void refusesWrongSignatureAndTamperedExe() throws Exception {
        KeyPair attacker = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        publish("v0.3.0", exe, exe, attacker.getPrivate());
        LauncherUpdate.Release r = updater().check("0.1.0").orElseThrow();
        IOException e = assertThrows(IOException.class, () -> updater().download(r, tmp.resolve("a")));
        assertTrue(e.getMessage().contains("Signatur"));

        // signed list, but the EXE on the server is not the one in it
        publish("v0.3.0", "MZ evil".getBytes(StandardCharsets.UTF_8), exe, keys.getPrivate());
        LauncherUpdate.Release r2 = updater().check("0.1.0").orElseThrow();
        assertThrows(IOException.class, () -> updater().download(r2, tmp.resolve("b")));
        try (var s = Files.list(tmp.resolve("b"))) {
            assertTrue(s.noneMatch(p -> p.toString().endsWith(".exe")), "nothing unverified stays");
        }
    }

    @Test
    void noReleaseOrIncompleteRelease() throws Exception {
        assertTrue(updater().check("0.1.0").isEmpty(), "404 = nothing published yet");
        files.put("/api/releases/latest", "{\"tag_name\":\"v9.0.0\",\"assets\":[]}".getBytes(StandardCharsets.UTF_8));
        assertTrue(updater().check("0.1.0").isEmpty(), "missing signature assets: not offered");
        files.put("/api/releases/latest", "{\"tag_name\":\"v9.0.0\",\"prerelease\":true}".getBytes(StandardCharsets.UTF_8));
        assertTrue(updater().check("0.1.0").isEmpty());
    }

    @Test
    void versionsAndSums() {
        assertTrue(LauncherUpdate.compare("0.10.0", "0.9.9") > 0);
        assertTrue(LauncherUpdate.compare("1.0.0-beta", "1.0.0") < 0);
        assertEquals(0, LauncherUpdate.compare("0.2", "0.2.0"));
        assertEquals("a".repeat(64), LauncherUpdate.sha256Of("A".repeat(64) + " *LumoraClient.exe\n", "LumoraClient.exe"));
    }

    @Test
    void bundledKeyLoads() throws IOException {
        assertEquals("EdDSA", LauncherUpdate.bundledKey().getAlgorithm());
    }
}
