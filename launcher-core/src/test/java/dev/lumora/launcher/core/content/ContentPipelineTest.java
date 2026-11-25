package dev.lumora.launcher.core.content;

import dev.lumora.launcher.core.download.Downloader;
import dev.lumora.launcher.core.instance.InstanceKind;
import dev.lumora.launcher.core.platform.ModrinthProvider;
import dev.lumora.launcher.core.platform.PlatformModel.ProjectSummary;
import dev.lumora.launcher.core.platform.PlatformModel.ProjectVersion;
import dev.lumora.launcher.core.platform.ProjectType;
import dev.lumora.launcher.core.testutil.LocalServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Plan → install → update → rollback against a local stand-in for the Modrinth API. */
class ContentPipelineTest {
    @TempDir
    Path tmp;
    LocalServer server;
    final Map<String, String> versionLists = new ConcurrentHashMap<>();
    final Map<String, byte[]> files = new ConcurrentHashMap<>();
    volatile boolean corrupt;

    static String sha512(byte[] b) throws Exception {
        return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-512").digest(b));
    }

    static String sha1(byte[] b) throws Exception {
        return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-1").digest(b));
    }

    String version(String project, String id, String number, String file, byte[] data, String loaders, String mc, String deps) throws Exception {
        files.put(file, data);
        return """
                {"id":"%2$s","project_id":"%1$s","name":"%1$s %3$s","version_number":"%3$s","version_type":"release",
                 "date_published":"2026-11-0%7$sT10:00:00Z","loaders":[%5$s],"game_versions":["%6$s"],
                 "files":[{"primary":true,"filename":"%4$s","url":"%8$s/files/%4$s","size":%9$d,"hashes":{"sha512":"%10$s","sha1":"%11$s"}},
                          {"primary":false,"filename":"src.jar","url":"%8$s/files/src.jar","size":1,"file_type":"sources-jar","hashes":{"sha512":"00","sha1":"00"}}],
                 "dependencies":[%12$s]}""".formatted(project, id, number, file, loaders, mc, number.endsWith("2") ? "2" : "1",
                server.uri("").toString(), data.length, sha512(data), sha1(data), deps);
    }

    @BeforeEach
    void start() throws Exception {
        server = new LocalServer();
        versionLists.put("sodium", "[" + version("sodium", "s1", "0.6.1", "sodium-0.6.1.jar", "sodium-1".getBytes(), "\"fabric\"", "1.21.4",
                "{\"project_id\":\"fapi\",\"dependency_type\":\"required\"},{\"project_id\":\"extra\",\"dependency_type\":\"optional\"},"
                        + "{\"project_id\":\"optifine\",\"dependency_type\":\"incompatible\"},{\"file_name\":\"x.jar\",\"dependency_type\":\"embedded\"}") + "]");
        versionLists.put("fapi", "[" + version("fapi", "f2", "0.110.2", "fabric-api-0.110.2.jar", "fapi-2".getBytes(), "\"fabric\"", "1.21.4", "")
                + "," + version("fapi", "f1", "0.110.1", "fabric-api-0.110.1.jar", "fapi-1".getBytes(), "\"fabric\"", "1.21.4", "") + "]");
        versionLists.put("extra", "[" + version("extra", "e1", "1.0", "extra-1.0.jar", "extra".getBytes(), "\"fabric\"", "1.21.4", "") + "]");
        versionLists.put("forgeonly", "[" + version("forgeonly", "fo1", "1.0", "forgeonly.jar", "fo".getBytes(), "\"forge\"", "1.21.4", "") + "]");
        versionLists.put("needsmissing", "[" + version("needsmissing", "nm1", "1.0", "nm.jar", "nm".getBytes(), "\"fabric\"", "1.21.4",
                "{\"project_id\":\"forgeonly\",\"dependency_type\":\"required\"}") + "]");
        server.on("/v2/project/", (ex, n) -> {
            String path = ex.getRequestURI().getPath();
            String project = path.substring("/v2/project/".length(), path.indexOf("/version"));
            String query = ex.getRequestURI().getQuery();
            String body = versionLists.getOrDefault(project, "[]");
            if (query != null && query.contains("loaders") && !query.contains("forge") && project.equals("forgeonly")) {
                body = "[]"; // Modrinth filters by loader: no Fabric version of a Forge-only mod
            }
            return LocalServer.Response.json(200, body);
        });
        server.on("/v2/projects", (ex, n) -> LocalServer.Response.json(200,
                "[{\"id\":\"fapi\",\"slug\":\"fabric-api\",\"title\":\"Fabric API\",\"project_type\":\"mod\"},"
                        + "{\"id\":\"extra\",\"slug\":\"extra\",\"title\":\"Sodium Extra\",\"project_type\":\"mod\"}]"));
        server.on("/v2/version_files", (ex, n) -> LocalServer.Response.json(200, "{}"));
        server.on("/files/", (ex, n) -> {
            String name = ex.getRequestURI().getPath().substring("/files/".length());
            byte[] data = files.get(name);
            if (data == null) {
                return LocalServer.Response.json(404, "{}");
            }
            return LocalServer.Response.ok(corrupt && name.startsWith("fabric-api") ? "evil".getBytes() : data);
        });
    }

    @AfterEach
    void stop() {
        server.close();
    }

    Downloader downloader() {
        return new Downloader(HttpClient.newHttpClient(), Set.of(), true, 1, Duration.ofMillis(1), 2);
    }

    ModrinthProvider provider() {
        return new ModrinthProvider(downloader(), server.uri("/v2").toString());
    }

    ProjectSummary summary(String id, String title) {
        return new ProjectSummary(dev.lumora.launcher.core.platform.Platform.MODRINTH, id, id, title, "", "", 0, 0, null, List.of(),
                List.of("fabric"), List.of("1.21.4"), null, null, "MIT", ProjectType.MOD, null,
                dev.lumora.launcher.core.platform.PlatformModel.Side.REQUIRED, dev.lumora.launcher.core.platform.PlatformModel.Side.OPTIONAL);
    }

    ProjectVersion first(String project) throws IOException {
        return provider().versions(project, List.of("fabric"), List.of("1.21.4")).getFirst();
    }

    @Test
    void plansTheDependencyTreeAndInstallsWithManifest() throws Exception {
        Path game = tmp.resolve("game");
        ContentInstaller installer = new ContentInstaller(downloader());
        ContentInstaller.Target target = new ContentInstaller.Target(InstanceKind.FABRIC, "1.21.4", game);
        ProjectVersion sodium = first("sodium");
        assertEquals(1, sodium.files().size(), "source jars are never offered");
        ContentInstaller.Plan plan = installer.plan(provider(), target, summary("sodium", "Sodium"), ProjectType.MOD, sodium, Set.of());
        assertFalse(plan.blocked(), plan.issues().toString());
        assertEquals(List.of("mods/sodium-0.6.1.jar", "mods/fabric-api-0.110.2.jar"), plan.steps().stream().map(ContentInstaller.Step::key).toList(),
                "newest compatible release of the required dependency; optional one not installed");
        DependencyResolver.Node tree = plan.tree();
        assertEquals("Sodium", tree.title());
        assertEquals(List.of(DependencyResolver.Status.NEW, DependencyResolver.Status.OPTIONAL),
                tree.children().stream().map(DependencyResolver.Node::status).toList());
        assertEquals("Fabric API", tree.children().get(0).title());
        assertEquals("Sodium Extra", tree.children().get(1).title());

        ContentInstaller.Result r = installer.execute(plan, "modrinth", null);
        assertEquals(2, r.installed().size());
        assertTrue(Files.isRegularFile(game.resolve("mods/sodium-0.6.1.jar")));
        ContentManifest m = ContentManifest.load(game);
        assertEquals("sodium", m.get("mods/sodium-0.6.1.jar").orElseThrow().projectId);
        assertEquals(List.of("sodium"), m.get("mods/fabric-api-0.110.2.jar").orElseThrow().requiredBy, "remembered as a dependency");
        assertFalse(Files.exists(game.resolve(".lumora/staging")), "staging folder removed");

        // optional dependency ticked: only the new file is planned, the installed ones are recognised
        ContentInstaller.Plan again = installer.plan(provider(), target, summary("sodium", "Sodium"), ProjectType.MOD, sodium, Set.of("extra"));
        assertEquals(List.of("mods/extra-1.0.jar"), again.steps().stream().map(ContentInstaller.Step::key).toList());
    }

    @Test
    void anUpdateReplacesTheOldFileAndKeepsABackup() throws Exception {
        Path game = tmp.resolve("game");
        ContentInstaller installer = new ContentInstaller(downloader());
        ContentInstaller.Target target = new ContentInstaller.Target(InstanceKind.FABRIC, "1.21.4", game);
        List<ProjectVersion> fapi = provider().versions("fapi", List.of("fabric"), List.of("1.21.4"));
        ProjectVersion older = fapi.stream().filter(v -> v.number().equals("0.110.1")).findFirst().orElseThrow();
        installer.execute(installer.plan(provider(), target, summary("fapi", "Fabric API"), ProjectType.MOD, older, Set.of()), "modrinth", null);
        assertTrue(Files.exists(game.resolve("mods/fabric-api-0.110.1.jar")));

        ContentInstaller.Plan update = installer.plan(provider(), target, summary("fapi", "Fabric API"), ProjectType.MOD, fapi.getFirst(), Set.of());
        assertEquals(DependencyResolver.Status.UPDATE, update.tree().status());
        assertEquals("mods/fabric-api-0.110.1.jar", update.steps().getFirst().replacesKey());
        ContentInstaller.Result r = installer.execute(update, "modrinth", null);
        assertFalse(Files.exists(game.resolve("mods/fabric-api-0.110.1.jar")), "never two copies of one mod");
        assertTrue(Files.exists(game.resolve("mods/fabric-api-0.110.2.jar")));
        assertNotNull(r.backup());
        assertTrue(Files.exists(r.backup().resolve("mods/fabric-api-0.110.1.jar")), "the replaced file is backed up");
        assertTrue(ContentManifest.load(game).get("mods/fabric-api-0.110.1.jar").isEmpty());
    }

    @Test
    void aFailedDownloadRollsBackEverything() throws Exception {
        Path game = tmp.resolve("game");
        Files.createDirectories(game.resolve("mods"));
        ContentInstaller installer = new ContentInstaller(downloader());
        ContentInstaller.Target target = new ContentInstaller.Target(InstanceKind.FABRIC, "1.21.4", game);
        ContentInstaller.Plan plan = installer.plan(provider(), target, summary("sodium", "Sodium"), ProjectType.MOD, first("sodium"), Set.of());
        corrupt = true;
        assertThrows(IOException.class, () -> installer.execute(plan, "modrinth", null));
        try (var s = Files.list(game.resolve("mods"))) {
            assertEquals(0, s.count(), "nothing half-installed stays behind");
        }
        assertFalse(Files.exists(game.resolve(ContentManifest.FILE)));
    }

    @Test
    void incompatibleRequestsAreExplainedAndBlocked() throws Exception {
        ContentInstaller installer = new ContentInstaller(downloader());
        ProjectVersion sodium = first("sodium");
        // Lumora runs no third-party mods
        ContentInstaller.Plan lumora = installer.plan(provider(), new ContentInstaller.Target(InstanceKind.LUMORA, "1.21.4", tmp.resolve("a")),
                summary("sodium", "Sodium"), ProjectType.MOD, sodium, Set.of());
        assertTrue(lumora.blocked());
        assertEquals("mods_unsupported", lumora.issues().getFirst().code());
        // wrong loader and wrong Minecraft version
        ContentInstaller.Plan forge = installer.plan(provider(), new ContentInstaller.Target(InstanceKind.FORGE, "1.20.1", tmp.resolve("b")),
                summary("sodium", "Sodium"), ProjectType.MOD, sodium, Set.of());
        assertTrue(forge.blocked());
        assertTrue(forge.issues().stream().anyMatch(i -> i.code().equals("loader")));
        assertTrue(forge.issues().stream().anyMatch(i -> i.code().equals("mc_version")));
        assertFalse(forge.issues().getFirst().text().isBlank());
        // a required dependency without a Fabric version
        ContentInstaller.Plan missing = installer.plan(provider(), new ContentInstaller.Target(InstanceKind.FABRIC, "1.21.4", tmp.resolve("c")),
                summary("needsmissing", "Needs"), ProjectType.MOD, first("needsmissing"), Set.of());
        assertTrue(missing.blocked());
        assertTrue(missing.issues().stream().anyMatch(i -> i.code().equals("missing")));
        assertThrows(IOException.class, () -> installer.execute(missing, "modrinth", null));
    }

    @Test
    void fileNamesAreSanitisedAndTypeChecked() {
        assertEquals("a_b.jar", ContentInstaller.safeFileName("a/b.jar", ProjectType.MOD));
        assertEquals(null, ContentInstaller.safeFileName("pack.zip", ProjectType.MOD));
        assertEquals(null, ContentInstaller.safeFileName(".hidden.jar", ProjectType.MOD));
        assertEquals("pack.zip", ContentInstaller.safeFileName("pack.zip", ProjectType.RESOURCE_PACK));
    }

    @Test
    void installedContentSwitchesOffAndOn() throws Exception {
        Path game = tmp.resolve("game");
        Files.createDirectories(game.resolve("mods"));
        Files.write(game.resolve("mods/a.jar"), "x".getBytes(StandardCharsets.UTF_8));
        Files.write(game.resolve("mods/readme.txt"), "x".getBytes(StandardCharsets.UTF_8));
        List<InstalledContent.Item> items = InstalledContent.scan(game, ProjectType.MOD, ContentManifest.load(game));
        assertEquals(1, items.size(), "only jars count as mods");
        java.nio.file.Path off = InstalledContent.setEnabled(items.getFirst(), false);
        assertEquals("a.jar.disabled", off.getFileName().toString());
        InstalledContent.Item disabled = InstalledContent.scan(game, ProjectType.MOD, null).getFirst();
        assertFalse(disabled.enabled());
        assertEquals("mods/a.jar", disabled.key(), "the key ignores .disabled");
        InstalledContent.setEnabled(disabled, true);
        assertTrue(Files.exists(game.resolve("mods/a.jar")));
    }
}
