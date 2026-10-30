package dev.lumora.launcher.core.profile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileCatalogTest {
    @TempDir
    Path tmp;

    @Test
    void bundledCatalogIsValidAndConsistent() throws IOException {
        ProfileCatalog c = ProfileCatalog.loadBundled();
        assertEquals("26.1.2", c.defaultProfile());
        assertEquals(List.of("26.3", "26.2", "26.1.2", "26.1.1", "26.1", "1.21.11", "1.21.8", "1.21.4", "1.21", "1.20.6", "1.20.4",
                "1.20.1", "1.19.4", "1.18.2", "1.17.1", "1.16.5", "1.12.2", "1.8.9"), c.profiles().stream().map(p -> p.id).toList());
        // versions with a ported Lumora companion mod
        List<String> lumora = List.of("26.3", "26.2", "26.1.2", "26.1.1", "26.1", "1.21.11", "1.21.8", "1.21.4", "1.21", "1.20.6", "1.20.4", "1.20.1", "1.19.4", "1.18.2", "1.17.1", "1.16.5", "1.12.2", "1.8.9");
        for (ProfileCatalog.Profile p : c.profiles()) {
            assertEquals(p.id, p.minecraftVersion, "profile id is the Minecraft version");
            assertEquals("0.19.5", p.fabricLoaderVersion);
            boolean legacy = p.id.equals("1.8.9") || p.id.equals("1.12.2");
            assertEquals(legacy ? "legacy-fabric" : "fabric", p.loader, p.id);
            assertTrue(c.profileArtifact(p, legacy ? "legacy-fabric-api" : "fabric-api").isPresent(), p.id + " lacks its Fabric API");
            if (!legacy) {
                assertTrue(c.profileArtifact(p, "sodium").isPresent(), p.id + " lacks Sodium");
            }
            assertTrue(c.profileArtifact(p, "viafabricplus").isPresent() || c.profileArtifact(p, "viafabric").isPresent(),
                    p.id + " lacks ViaFabric(Plus)");
            assertEquals(lumora.contains(p.id), c.profileArtifact(p, "lumora-client").isPresent(), p.id + " Lumora mod");
            // the Lumora API is built for Java 21: versions whose Minecraft ships an older Java start with Java 21
            if (lumora.contains(p.id) && List.of("1.20.4", "1.20.1", "1.19.4", "1.18.2", "1.17.1", "1.16.5", "1.12.2", "1.8.9").contains(p.id)) {
                assertEquals("java-runtime-delta", p.javaComponent, p.id + " needs Java 21 for the Lumora API");
            }
            assertTrue(p.mods.stream().noneMatch(m -> m.contains("vulkan")), "VulkanMod profile was removed");
            for (ProfileCatalog.Artifact a : c.profileArtifacts(p)) {
                if (a.source == ProfileCatalog.Source.MODRINTH && !a.id.equals(a.key())) {
                    assertTrue(a.id.endsWith("-" + p.minecraftVersion), a.id + " belongs to another version");
                }
            }
        }
        assertEquals("lumora-client-mc1.21.8.jar", c.profileArtifact(c.profile("1.21.8").orElseThrow(), "lumora-client").orElseThrow().fileName);
        assertEquals(List.of("modmenu", "ferritecore", "lithium", "voicechat"), c.allExtensionIds());
        for (ProfileCatalog.Artifact a : c.artifacts()) {
            if (a.source == ProfileCatalog.Source.MODRINTH) {
                assertEquals(128, a.sha512.length(), a.id);
                assertTrue(a.url.startsWith("https://cdn.modrinth.com/"), a.id);
            }
        }
    }

    @Test
    void resolveModsAddsOnlySupportedEnabledExtensions() throws IOException {
        ProfileCatalog c = ProfileCatalog.loadBundled();
        var mods = c.resolveMods(c.profile("26.1.2").orElseThrow(), List.of("modmenu", "unknown"));
        assertEquals(List.of("fabric-api", "sodium", "viafabricplus", "lumora-client", "modmenu"), mods.stream().map(a -> a.id).toList());
        // extension switches are stored by key and pick the build of the selected version
        var old = c.resolveMods(c.profile("1.21.11").orElseThrow(), List.of("modmenu", "lithium"));
        assertEquals(List.of("fabric-api-1.21.11", "sodium-1.21.11", "viafabricplus-1.21.11", "lumora-client-1.21.11",
                "modmenu-1.21.11", "lithium-1.21.11"), old.stream().map(a -> a.id).toList());
    }

    private static String doc(String artifactJson, String profileMods) {
        return """
                {"schemaVersion":1,"defaultProfile":"p","profiles":[{"id":"p","displayName":"P","minecraftVersion":"26.1.2",
                 "fabricLoaderVersion":"0.19.5","mods":[%s],"extensions":[]}],
                 "artifacts":[%s]}
                """.formatted(profileMods, artifactJson);
    }

    private static final String GOOD = """
            {"id":"m","name":"M","version":"1","license":"MIT","source":"MODRINTH","fileName":"m-1.jar",
             "url":"https://cdn.modrinth.com/data/x/m-1.jar","size":10,"sha512":"%s"}""".formatted("a".repeat(128));

    @Test
    void acceptsValidDocument() throws IOException {
        ProfileCatalog c = ProfileCatalog.parse(doc(GOOD, "\"m\""));
        assertEquals("m-1.jar", c.artifact("m").fileName);
    }

    @Test
    void rejectsTwoBuildsOfTheSameModInOneProfile() {
        String second = GOOD.replace("\"id\":\"m\"", "\"id\":\"m2\",\"key\":\"m\"").replace("m-1.jar", "m-2.jar");
        IOException e = assertThrows(IOException.class, () -> ProfileCatalog.parse(doc(GOOD + "," + second, "\"m\",\"m2\"")));
        assertTrue(e.getMessage().contains("two artifacts with key m"), e.getMessage());
    }

    @Test
    void rejectsForeignDownloadHost() {
        String bad = GOOD.replace("https://cdn.modrinth.com/data/x/m-1.jar", "https://example.com/m-1.jar");
        IOException e = assertThrows(IOException.class, () -> ProfileCatalog.parse(doc(bad, "\"m\"")));
        assertTrue(e.getMessage().contains("cdn.modrinth.com"));
    }

    @Test
    void rejectsMalformedHashAndUnknownMods() {
        String bad = GOOD.replace("a".repeat(128), "abc");
        assertThrows(IOException.class, () -> ProfileCatalog.parse(doc(bad, "\"m\"")));
        assertThrows(IOException.class, () -> ProfileCatalog.parse(doc(GOOD, "\"missing\"")));
    }

    @Test
    void rejectsPathTraversalInFileName() {
        String bad = GOOD.replace("\"fileName\":\"m-1.jar\"", "\"fileName\":\"../evil.jar\"");
        assertTrue(bad.contains("../evil.jar"));
        assertThrows(IOException.class, () -> ProfileCatalog.parse(doc(bad, "\"m\"")));
    }

    @Test
    void validOverrideReplacesArtifactInvalidOverrideIsIgnored() throws IOException {
        Path override = tmp.resolve("profiles.override.json");
        Files.writeString(override, """
                {"schemaVersion":1,"artifacts":[{"id":"sodium-1.21.8","name":"Sodium","version":"9.9.9","license":"PolyForm-Shield-1.0.0",
                 "source":"MODRINTH","modrinthProjectId":"AANobbMI","fileName":"sodium-9.9.9.jar",
                 "url":"https://cdn.modrinth.com/data/AANobbMI/versions/x/sodium-9.9.9.jar","size":5,"sha512":"%s"}]}
                """.formatted("b".repeat(128)));
        ProfileCatalog updated = ProfileCatalog.load(override);
        assertEquals("9.9.9", updated.artifact("sodium-1.21.8").version);
        assertEquals("sodium", updated.artifact("sodium-1.21.8").key(), "key comes from the bundled lockfile");

        Files.writeString(override, """
                {"schemaVersion":1,"artifacts":[{"id":"sodium-1.21.8","source":"MODRINTH","fileName":"x.jar",
                 "url":"https://evil.example/x.jar","size":5,"sha512":"%s"}]}
                """.formatted("b".repeat(128)));
        assertEquals("mc1.21.8-0.7.3-fabric", ProfileCatalog.load(override).artifact("sodium-1.21.8").version);
    }
}
