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
        for (ProfileCatalog.Profile p : c.profiles()) {
            assertEquals(p.id, p.minecraftVersion, "profile id is the Minecraft version");
            // Lumora Loader: Minecraft plus the Lumora client only, no mod loader and no third-party mods
            assertEquals("lumora", p.loader, p.id);
            assertTrue(p.lumoraLoader(), p.id);
            assertEquals(1, p.mods.size(), p.id);
            assertEquals("lumora-client", c.profileArtifact(p, "lumora-client").orElseThrow().key(), p.id);
            assertEquals(ProfileCatalog.Source.BUNDLED, c.profileArtifact(p, "lumora-client").orElseThrow().source, p.id);
            assertTrue(p.extensions.isEmpty(), p.id + " has no extensions without a mod loader");
            for (ProfileCatalog.Artifact pack : c.resolvePacks(p, List.of())) {
                assertTrue(pack.requires == null || pack.requires.isEmpty(), p.id + ": " + pack.id + " needs a mod");
                assertTrue(pack.id.endsWith("-" + p.minecraftVersion) || pack.id.equals(pack.key()), pack.id + " belongs to another version");
            }
            // the Lumora API is built for Java 21: versions whose Minecraft ships an older Java start with Java 21
            if (List.of("1.20.4", "1.20.1", "1.19.4", "1.18.2", "1.17.1", "1.16.5", "1.12.2", "1.8.9").contains(p.id)) {
                assertEquals("java-runtime-delta", p.javaComponent, p.id + " needs Java 21 for the Lumora API");
            }
        }
        assertEquals("lumora-client-mc1.21.8.jar", c.profileArtifact(c.profile("1.21.8").orElseThrow(), "lumora-client").orElseThrow().fileName);
        assertTrue(c.allExtensionIds().isEmpty(), "no mod extensions without a mod loader");
        for (ProfileCatalog.Artifact a : c.artifacts()) {
            if (a.source == ProfileCatalog.Source.MODRINTH) {
                assertEquals(128, a.sha512.length(), a.id);
                assertTrue(a.url.startsWith("https://cdn.modrinth.com/"), a.id);
            }
        }
    }

    @Test
    void resolveModsReturnsOnlyTheLumoraJar() throws IOException {
        ProfileCatalog c = ProfileCatalog.loadBundled();
        // no mod loader: old extension switches (modmenu, lithium ...) from a config are ignored
        for (ProfileCatalog.Profile p : c.profiles()) {
            assertEquals(List.of("lumora-client"), c.resolveMods(p, List.of("modmenu", "lithium", "unknown")).stream().map(a -> a.key()).toList(), p.id);
        }
    }

    private static String doc(String artifactJson, String profileMods) {
        return """
                {"schemaVersion":1,"defaultProfile":"p","profiles":[{"id":"p","displayName":"P","minecraftVersion":"26.1.2",
                 "loader":"lumora","mods":[%s],"extensions":[]}],
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
                {"schemaVersion":1,"artifacts":[{"id":"better-leaves-1.21.8","name":"Better Leaves","version":"9.9.9","license":"MIT",
                 "source":"MODRINTH","modrinthProjectId":"uvpymuxq","fileName":"better-leaves-9.9.9.zip",
                 "url":"https://cdn.modrinth.com/data/uvpymuxq/versions/x/better-leaves-9.9.9.zip","size":5,"sha512":"%s"}]}
                """.formatted("b".repeat(128)));
        ProfileCatalog updated = ProfileCatalog.load(override);
        assertEquals("9.9.9", updated.artifact("better-leaves-1.21.8").version);
        assertEquals("better-leaves", updated.artifact("better-leaves-1.21.8").key(), "key comes from the bundled lockfile");

        Files.writeString(override, """
                {"schemaVersion":1,"artifacts":[{"id":"better-leaves-1.21.8","source":"MODRINTH","fileName":"x.zip",
                 "url":"https://evil.example/x.zip","size":5,"sha512":"%s"}]}
                """.formatted("b".repeat(128)));
        assertEquals("9.6", ProfileCatalog.load(override).artifact("better-leaves-1.21.8").version);
    }
}
