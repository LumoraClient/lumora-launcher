package dev.lumora.launcher.core.mods;

import dev.lumora.launcher.core.download.Downloader;
import dev.lumora.launcher.core.profile.ProfileCatalog;
import dev.lumora.launcher.core.testutil.LocalServer;
import dev.lumora.launcher.core.util.Hashing;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModInstallerTest {
    @TempDir
    Path tmp;

    static ProfileCatalog.Artifact modrinth(String id, String file, byte[] data, String url) {
        ProfileCatalog.Artifact a = new ProfileCatalog.Artifact();
        a.id = id;
        a.name = id;
        a.source = ProfileCatalog.Source.MODRINTH;
        a.fileName = file;
        a.url = url;
        a.size = data.length;
        a.sha512 = Hashing.hash(data, Hashing.Algorithm.SHA512);
        return a;
    }

    static ProfileCatalog.Artifact bundled(String file) {
        ProfileCatalog.Artifact a = new ProfileCatalog.Artifact();
        a.id = "lumora-client";
        a.source = ProfileCatalog.Source.BUNDLED;
        a.fileName = file;
        return a;
    }

    BundledMods bundledDir(byte[] jar, boolean tamper) throws IOException {
        Path dir = Files.createDirectories(tmp.resolve("bundled"));
        Files.write(dir.resolve("lumora-client-mc26.1.2.jar"), tamper ? "tampered".getBytes() : jar);
        Files.writeString(dir.resolve(BundledMods.INDEX), "{\"lumora-client-mc26.1.2.jar\":\""
                + Hashing.hash(jar, Hashing.Algorithm.SHA512) + "\"}");
        return new BundledMods(dir);
    }

    Downloader downloader() {
        return new Downloader(HttpClient.newHttpClient(), Set.of(), true, 2, Duration.ofMillis(1), 2);
    }

    @Test
    void installsSyncsAndReportsUnmanagedMods() throws Exception {
        byte[] a = DependencyCheckerTest.jar("{\"id\":\"a\",\"version\":\"1\"}", Map.of());
        byte[] b = DependencyCheckerTest.jar("{\"id\":\"b\",\"version\":\"1\"}", Map.of());
        byte[] own = DependencyCheckerTest.jar("{\"id\":\"lumora\",\"version\":\"0.1.0\"}", Map.of());
        try (LocalServer s = new LocalServer()
                .on("/a.jar", (ex, n) -> LocalServer.Response.ok(a))
                .on("/b.jar", (ex, n) -> LocalServer.Response.ok(b))) {
            Path mods = tmp.resolve("instance/mods");
            Files.createDirectories(mods);
            Files.write(mods.resolve("user-added.jar"), b);
            ModInstaller installer = new ModInstaller(downloader(), tmp.resolve("cache"), bundledDir(own, false));

            var first = installer.install(List.of(
                    modrinth("a", "a-1.jar", a, s.uri("/a.jar").toString()),
                    modrinth("b", "b-1.jar", b, s.uri("/b.jar").toString()),
                    bundled("lumora-client-mc26.1.2.jar")), mods, null);
            assertEquals(3, first.modFiles().size());
            assertEquals(List.of(mods.resolve("user-added.jar")), first.unmanagedFiles());

            // b is removed from the profile -> the launcher-managed copy disappears, the user jar stays
            var second = installer.install(List.of(modrinth("a", "a-1.jar", a, s.uri("/a.jar").toString()),
                    bundled("lumora-client-mc26.1.2.jar")), mods, null);
            assertEquals(2, second.modFiles().size());
            assertFalse(Files.exists(mods.resolve("b-1.jar")));
            assertTrue(Files.exists(mods.resolve("user-added.jar")));
            assertEquals(1, s.count("/a.jar"), "cached and verified artifacts are not downloaded again");
        }
    }

    @Test
    void tamperedBundledModIsRejected() throws Exception {
        byte[] own = DependencyCheckerTest.jar("{\"id\":\"lumora\",\"version\":\"0.1.0\"}", Map.of());
        ModInstaller installer = new ModInstaller(downloader(), tmp.resolve("cache"), bundledDir(own, true));
        IOException e = assertThrows(IOException.class,
                () -> installer.install(List.of(bundled("lumora-client-mc26.1.2.jar")), tmp.resolve("mods"), null));
        assertTrue(e.getMessage().contains("corrupt"));
    }

    @Test
    void missingBundleIndexIsExplained() {
        IOException e = assertThrows(IOException.class, () -> new BundledMods(tmp.resolve("nope")));
        assertTrue(e.getMessage().contains("reinstall"));
    }
}
