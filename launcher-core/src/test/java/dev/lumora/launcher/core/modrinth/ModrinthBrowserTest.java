package dev.lumora.launcher.core.modrinth;

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
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModrinthBrowserTest {
    @TempDir
    Path tmp;
    HttpServer server;
    String base;
    final byte[] zoomJar = "zoom-jar-bytes".getBytes(StandardCharsets.UTF_8);
    final byte[] libJar = "lib-jar-bytes".getBytes(StandardCharsets.UTF_8);
    volatile boolean corruptLib;
    volatile String lastQuery;

    static String sha512(byte[] b) throws Exception {
        return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-512").digest(b));
    }

    String version(String project, String file, byte[] data, String deps) throws Exception {
        return """
                [{"id":"v-%1$s","project_id":"%1$s","version_number":"1.0","version_type":"release",
                  "files":[{"primary":true,"filename":"%2$s","url":"%3$s/files/%2$s","size":%4$d,"hashes":{"sha512":"%5$s"}}],
                  "dependencies":[%6$s]}]""".formatted(project, file, base, data.length, sha512(data), deps);
    }

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        base = "http://127.0.0.1:" + server.getAddress().getPort();
        Map<String, String> versions = Map.of(
                "zoom", version("zoom", "zoomify-2.0.jar", zoomJar,
                        "{\"project_id\":\"lib\",\"dependency_type\":\"required\"},{\"project_id\":\"P7dR8mSH\",\"dependency_type\":\"required\"},"
                                + "{\"project_id\":\"opt\",\"dependency_type\":\"optional\"}"),
                "lib", version("lib", "libby-1.0.jar", libJar, ""));
        server.createContext("/v2/", ex -> {
            String path = ex.getRequestURI().getPath();
            String body;
            if (path.equals("/v2/search")) {
                lastQuery = ex.getRequestURI().getQuery();
                body = "{\"hits\":[{\"project_id\":\"zoom\",\"slug\":\"zoomify\",\"title\":\"Zoomify\",\"description\":\"Zoom\",\"author\":\"x\","
                        + "\"downloads\":1234,\"icon_url\":null,\"license\":\"MIT\",\"categories\":[\"fabric\",\"utility\"]}],\"offset\":0,\"limit\":20,\"total_hits\":1}";
            } else {
                String project = path.substring("/v2/project/".length(), path.indexOf("/version"));
                body = versions.getOrDefault(project, "[]");
            }
            byte[] b = body.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        server.createContext("/files/", ex -> {
            byte[] b = ex.getRequestURI().getPath().endsWith("libby-1.0.jar") ? (corruptLib ? "evil".getBytes() : libJar) : zoomJar;
            ex.sendResponseHeaders(200, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    ModrinthBrowser browser() {
        return new ModrinthBrowser(new Downloader(HttpClient.newHttpClient(), Set.of(), true, 1, Duration.ofMillis(1), 2), base + "/v2");
    }

    @Test
    void searchesAndInstallsWithRequiredDependenciesButSkipsManagedOnes() throws IOException {
        ModrinthBrowser b = browser();
        ModrinthBrowser.Page page = b.search("zoom", "1.21.11", ModrinthBrowser.Kind.MOD, ModrinthBrowser.Sort.RELEVANCE, 0, 20);
        assertEquals("Zoomify", page.hits().getFirst().title());
        assertEquals("https://modrinth.com/mod/zoomify", page.hits().getFirst().pageUrl(ModrinthBrowser.Kind.MOD));

        Path instance = tmp.resolve("instances/1.21.11");
        ModrinthBrowser.Installed r = b.install("zoom", "1.21.11", ModrinthBrowser.Kind.MOD, instance, Set.of("P7dR8mSH"));
        assertEquals(java.util.List.of("zoomify-2.0.jar", "libby-1.0.jar"), r.files());
        assertEquals(java.util.List.of("P7dR8mSH"), r.skippedManaged(), "Fabric API comes from the lockfile");
        assertTrue(Files.isRegularFile(instance.resolve("mods/libby-1.0.jar")));
        assertEquals(Set.of("zoom", "lib"), ModrinthBrowser.installed(instance, ModrinthBrowser.Kind.MOD).keySet());
    }

    @Test
    void categoryFiltersBecomeFacets() throws IOException {
        browser().search("", "1.21.11", ModrinthBrowser.Kind.MOD, ModrinthBrowser.Sort.DOWNLOADS,
                java.util.List.of("optimization", "evil\"],[\"x"), 0, 20);
        assertTrue(lastQuery.contains("[\"categories:fabric\"]"));
        assertTrue(lastQuery.contains("[\"categories:optimization\"]"));
        assertTrue(lastQuery.contains("[\"categories:evilx\"]"), "only slug characters reach the facet string: " + lastQuery);
        assertFalse(lastQuery.contains("[\"x"));
    }

    @Test
    void refusesFilesWithWrongChecksum() {
        corruptLib = true;
        Path instance = tmp.resolve("instances/x");
        assertThrows(IOException.class, () -> browser().install("zoom", "1.21.11", ModrinthBrowser.Kind.MOD, instance, Set.of()));
        assertFalse(Files.exists(instance.resolve("mods/libby-1.0.jar")), "no unverified file stays behind");
    }

    @Test
    void noCompatibleVersionIsExplained() {
        IOException e = assertThrows(IOException.class,
                () -> browser().install("unknown", "1.21.11", ModrinthBrowser.Kind.MOD, tmp, Set.of()));
        assertTrue(e.getMessage().contains("1.21.11"));
    }

    @Test
    void sha512IsUsed() throws Exception {
        assertEquals(128, sha512(zoomJar).length());
        Hashing.Expected.sha512(sha512(zoomJar));
    }
}
