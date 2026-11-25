package dev.lumora.launcher.core.platform;

import dev.lumora.launcher.core.download.Downloader;
import dev.lumora.launcher.core.pack.CurseForgePackImporter;
import dev.lumora.launcher.core.pack.MrpackImporter;
import dev.lumora.launcher.core.instance.InstanceKind;
import dev.lumora.launcher.core.testutil.LocalServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurseForgeProviderTest {
    @TempDir
    Path tmp;

    static Downloader downloader() {
        return new Downloader(HttpClient.newHttpClient(), Set.of(), true, 1, Duration.ofMillis(1), 2);
    }

    static final String MOD = """
            {"id":238222,"slug":"jei","name":"Just Enough Items","summary":"Items","downloadCount":5000,"thumbsUpCount":10,"classId":6,
             "logo":{"thumbnailUrl":"https://media.forgecdn.net/avatars/thumbnails/jei.png"},"authors":[{"name":"mezz"}],
             "categories":[{"name":"API and Library"}],"screenshots":[{"title":"Shot","url":"https://media.forgecdn.net/a.png","thumbnailUrl":"https://media.forgecdn.net/a_t.png"}],
             "latestFilesIndexes":[{"gameVersion":"1.20.1","fileId":1,"modLoader":1}],"links":{"websiteUrl":"https://www.curseforge.com/minecraft/mc-mods/jei","sourceUrl":"https://github.com/mezz/JustEnoughItems"},
             "dateModified":"2026-11-01T10:00:00Z"}""";

    static String file(long id, String name, String url, String... versions) {
        StringBuilder v = new StringBuilder();
        for (String s : versions) {
            v.append(v.isEmpty() ? "" : ",").append('"').append(s).append('"');
        }
        return """
                {"id":%d,"modId":238222,"displayName":"%s","fileName":"%s","releaseType":1,"fileDate":"2026-11-0%dT10:00:00Z","fileLength":1234,
                 "downloadUrl":%s,"gameVersions":[%s],"hashes":[{"value":"0123456789abcdef0123456789abcdef01234567","algo":1},{"value":"x","algo":2}],
                 "dependencies":[{"modId":1,"relationType":3},{"modId":2,"relationType":4}],"fileFingerprint":%d}"""
                .formatted(id, name, name, id % 9 + 1, url == null ? "null" : "\"" + url + "\"", v, 4242 + id);
    }

    @Test
    void searchProjectFilesAndHeaderAgainstALocalApi() throws IOException {
        AtomicReference<String> key = new AtomicReference<>();
        AtomicReference<String> query = new AtomicReference<>();
        try (LocalServer s = new LocalServer()) {
            s.on("/v1/mods/search", (ex, n) -> {
                key.set(ex.getRequestHeaders().getFirst("x-api-key"));
                query.set(ex.getRequestURI().getRawQuery());
                return LocalServer.Response.json(200, "{\"data\":[" + MOD + "],\"pagination\":{\"index\":0,\"pageSize\":20,\"resultCount\":1,\"totalCount\":1}}");
            });
            s.on("/v1/mods/238222/files", (ex, n) -> LocalServer.Response.json(200, "{\"data\":["
                    + file(2, "jei-forge-2.jar", "https://edge.forgecdn.net/files/2/jei-forge-2.jar", "1.20.1", "Forge", "Client")
                    + "," + file(3, "jei-fabric-3.jar", null, "1.20.1", "Fabric")
                    + "],\"pagination\":{\"index\":0,\"pageSize\":50,\"resultCount\":2,\"totalCount\":2}}"));
            s.on("/v1/mods/238222/description", (ex, n) -> LocalServer.Response.json(200, "{\"data\":\"<p>Hello</p>\"}"));
            s.on("/v1/mods/238222", (ex, n) -> LocalServer.Response.json(200, "{\"data\":" + MOD + "}"));
            s.on("/v1/categories", (ex, n) -> LocalServer.Response.json(200,
                    "{\"data\":[{\"id\":421,\"name\":\"API and Library\",\"classId\":6},{\"id\":412,\"name\":\"Technology\",\"classId\":6}]}"));
            CurseForgeProvider cf = new CurseForgeProvider(downloader(), s.uri("").toString(), () -> "test-key-123456");
            assertTrue(cf.available());
            PlatformModel.SearchPage page = cf.search(new PlatformModel.SearchQuery("jei", ProjectType.MOD, "1.20.1", List.of("forge"),
                    List.of("API and Library"), null, false, PlatformModel.Sort.DOWNLOADS, 0, 20));
            assertEquals("test-key-123456", key.get(), "the key travels in the x-api-key header");
            assertTrue(query.get().contains("classId=6") && query.get().contains("modLoaderType=1") && query.get().contains("sortField=6"));
            assertTrue(query.get().contains("categoryIds=%5B421%5D"), "category name resolved to its id: " + query.get());
            PlatformModel.ProjectSummary hit = page.hits().getFirst();
            assertEquals(Platform.CURSEFORGE, hit.platform());
            assertEquals("238222", hit.id());
            assertEquals("mezz", hit.author());
            assertEquals(List.of("forge"), hit.loaders());

            PlatformModel.Project p = cf.project("238222");
            assertEquals("<p>Hello</p>", p.body());
            assertEquals("https://media.forgecdn.net/a.png", p.gallery().getFirst().large());
            assertEquals("https://www.curseforge.com/minecraft/mc-mods/jei", p.pageUrl());

            List<PlatformModel.ProjectVersion> forge = cf.versions("238222", List.of("forge"), List.of("1.20.1"));
            assertEquals(1, forge.size(), "loader tags among the game versions filter the files");
            PlatformModel.ProjectVersion v = forge.getFirst();
            assertEquals(List.of("1.20.1"), v.gameVersions(), "loader and side tags are not game versions");
            assertEquals("0123456789abcdef0123456789abcdef01234567", v.primaryFile().sha1());
            assertNull(v.primaryFile().sha512());
            assertEquals(1, v.dependencies().size(), "tools are not dependencies");
            PlatformModel.ProjectVersion fabric = cf.versions("238222", List.of("fabric"), List.of("1.20.1")).getFirst();
            assertNull(fabric.primaryFile().url(), "no download address when the author does not allow it");
        }
    }

    @Test
    void withoutKeyNothingIsAskedAndARejectedKeyIsExplained() throws IOException {
        assertFalse(new CurseForgeProvider(downloader(), "http://127.0.0.1:1", () -> null).available());
        assertFalse(new CurseForgeProvider(downloader(), "http://127.0.0.1:1", () -> " ").available());
        try (LocalServer s = new LocalServer()) {
            s.on("/v1/mods/search", (ex, n) -> LocalServer.Response.json(403, "{}"));
            CurseForgeProvider cf = new CurseForgeProvider(downloader(), s.uri("").toString(), () -> "bad-key-123456");
            IOException e = assertThrows(IOException.class, () -> cf.search(new PlatformModel.SearchQuery("x", ProjectType.MOD, null,
                    List.of(), List.of(), null, false, null, 0, 20)));
            assertEquals(dev.lumora.launcher.core.i18n.Lang.t("curseforge.error.key"), e.getMessage(), "a 403 becomes the readable key message");
            assertFalse(e.getMessage().contains("bad-key"), "the key never appears in an error");
        }
    }

    @Test
    void fingerprintIgnoresWhitespaceAndIdentifiesFiles() throws IOException {
        byte[] a = "a b\r\n\tc".getBytes(StandardCharsets.US_ASCII);
        byte[] b = "abc".getBytes(StandardCharsets.US_ASCII);
        assertEquals(CurseForgeFingerprint.of(a), CurseForgeFingerprint.of(b));
        assertEquals(1540447798L, CurseForgeFingerprint.murmur2(new byte[0]), "MurmurHash2 seed 1 of nothing");
        Path jar = tmp.resolve("x.jar");
        Files.write(jar, b);
        long print = CurseForgeFingerprint.of(jar);
        assertEquals(CurseForgeFingerprint.of(b), print);
        try (LocalServer s = new LocalServer()) {
            s.on("/v1/fingerprints/432", (ex, n) -> LocalServer.Response.json(200,
                    ("{\"data\":{\"exactMatches\":[{\"id\":238222,\"file\":" + file(7, "x.jar", "https://edge.forgecdn.net/x.jar", "1.20.1", "Forge")
                            .replace("\"fileFingerprint\":4249", "\"fileFingerprint\":" + print) + "}]}}")));
            CurseForgeProvider cf = new CurseForgeProvider(downloader(), s.uri("").toString(), () -> "test-key-123456");
            Map<Path, PlatformModel.ProjectVersion> found = cf.identify(List.of(jar));
            assertEquals("7", found.get(jar).id());
        }
    }

    @Test
    void curseForgeModpacksResolveFilesAndListManualOnes() throws IOException {
        Path pack = tmp.resolve("pack.zip");
        try (OutputStream os = Files.newOutputStream(pack); ZipOutputStream z = new ZipOutputStream(os)) {
            z.putNextEntry(new ZipEntry("manifest.json"));
            z.write("""
                    {"minecraft":{"version":"1.20.1","modLoaders":[{"id":"forge-47.2.0","primary":true}]},"manifestType":"minecraftModpack",
                     "name":"CF Pack","version":"2.0","author":"me","files":[{"projectID":238222,"fileID":2,"required":true},
                     {"projectID":238222,"fileID":3,"required":true}],"overrides":"overrides"}""".getBytes(StandardCharsets.UTF_8));
            z.closeEntry();
            z.putNextEntry(new ZipEntry("overrides/config/a.toml"));
            z.write("x".getBytes(StandardCharsets.UTF_8));
            z.closeEntry();
        }
        assertTrue(CurseForgePackImporter.isCurseForgePack(pack));
        MrpackImporter.Preview noKey = CurseForgePackImporter.analyze(pack, null);
        assertFalse(noKey.importable(), "without a key the files cannot be resolved");
        try (LocalServer s = new LocalServer()) {
            s.on("/v1/mods/files", (ex, n) -> LocalServer.Response.json(200, "{\"data\":["
                    + file(2, "jei-forge-2.jar", "https://edge.forgecdn.net/files/2/jei-forge-2.jar", "1.20.1", "Forge")
                    + "," + file(3, "blocked.jar", null, "1.20.1", "Forge") + "]}"));
            s.on("/v1/mods", (ex, n) -> LocalServer.Response.json(200, "{\"data\":[" + MOD + "]}"));
            CurseForgeProvider cf = new CurseForgeProvider(downloader(), s.uri("").toString(), () -> "test-key-123456");
            MrpackImporter.Preview p = CurseForgePackImporter.analyze(pack, cf);
            assertTrue(p.importable(), p.problems().toString());
            assertEquals("curseforge", p.format());
            assertEquals(InstanceKind.FORGE, p.kind());
            assertEquals("1.20.1-47.2.0", p.loaderVersion(), "Forge's Maven version carries the Minecraft version");
            assertEquals(1, p.files().size());
            assertEquals("mods/jei-forge-2.jar", p.files().getFirst().path());
            assertEquals("curseforge", p.files().getFirst().platform());
            assertEquals(1, p.manual().size(), "the file without download permission is listed for a manual download");
            assertEquals(1, p.overrideFiles());
        }
    }
}
