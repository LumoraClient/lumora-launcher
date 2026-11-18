package dev.lumora.core.browser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Server mappings (NoRiskClient/nrc-server-mappings): parsing, checks, address matching and the cache, offline. */
class ServerMappingsTest {
    static final String SHA_A = "a".repeat(40);
    static final String SHA_B = "b".repeat(40);
    static final String SHA_C = "c".repeat(40);

    @Test
    void addressesAreNormalisedAndMatchedExactlyOrByDomain() {
        assertEquals("play.example.net", ServerMappings.host(" Play.Example.NET:25565 "));
        assertEquals("example.net", ServerMappings.host("example.net."));
        assertEquals("[::1]", ServerMappings.host("[::1]:25565"));
        ServerMappings.Mapping hypixel = mapping("hypixel", "Hypixel", List.of("hypixel.net"));
        ServerMappings.Mapping alpha = mapping("alpha", "Alpha", List.of("play.alpha.de", "alpha.de"));
        Map<String, ServerMappings.Mapping> all = Map.of("hypixel", hypixel, "alpha", alpha);
        assertEquals(hypixel, ServerMappings.match(all, "hypixel.net"));
        assertEquals(hypixel, ServerMappings.match(all, "mc.hypixel.net"), "a sub-domain of a mapped domain");
        assertEquals(alpha, ServerMappings.match(all, "play.alpha.de"));
        assertNull(ServerMappings.match(all, "nothypixel.net"), "only whole domain parts");
        assertNull(ServerMappings.match(all, "example.org"));
    }

    @Test
    void theTreeKeepsOnlyServerFoldersWithValidNamesAndHashes() {
        String tree = """
                {"sha":"x","tree":[
                 {"path":"servers/alpha/manifest.json","type":"blob","sha":"%s"},
                 {"path":"servers/alpha/icon.png","type":"blob","sha":"%s"},
                 {"path":"servers/alpha/bg.png","type":"blob","sha":"%s"},
                 {"path":"servers/alpha","type":"tree","sha":"%s"},
                 {"path":"servers/../evil/manifest.json","type":"blob","sha":"%s"},
                 {"path":"servers/Bad Name/manifest.json","type":"blob","sha":"%s"},
                 {"path":"servers/beta/manifest.json","type":"blob","sha":"not-a-sha"},
                 {"path":"servers/beta/sub/icon.png","type":"blob","sha":"%s"},
                 {"path":"servers/beta/run.sh","type":"blob","sha":"%s"},
                 {"path":"docs/manifest.md","type":"blob","sha":"%s"}
                ]}""".formatted(SHA_A, SHA_B, SHA_C, SHA_A, SHA_A, SHA_A, SHA_A, SHA_A, SHA_A);
        Map<String, Map<String, ServerMappings.Blob>> folders = ServerMappings.parseTree(tree);
        assertEquals(List.of("alpha"), List.copyOf(folders.keySet()));
        assertEquals(List.of("manifest.json", "icon.png", "bg.png"), List.copyOf(folders.get("alpha").keySet()));
        assertEquals(SHA_B, folders.get("alpha").get("icon.png").sha());
    }

    @Test
    void manifestsNeedNameAndAddressAndPointOnlyToFilesOfTheirFolder() {
        Map<String, ServerMappings.Blob> files = new LinkedHashMap<>();
        files.put("icon.png", new ServerMappings.Blob("icon.png", SHA_B));
        files.put("bg.png", new ServerMappings.Blob("bg.png", SHA_C));
        ServerMappings.Mapping m = ServerMappings.parseManifest("alpha", """
                {"server-address":["play.alpha.de","alpha.de"],"pretty-name":"AlphaSMP","categorys":["PVP","SURVIVAL"],
                 "assets":{"icon":"./icon.png","background":"./bg.png"},"socials":{"discord":"https://example.invalid"}}""", files);
        assertEquals("AlphaSMP", m.name());
        assertEquals(List.of("play.alpha.de", "alpha.de"), m.addresses());
        assertEquals(List.of("PVP", "SURVIVAL"), m.categories());
        assertEquals(SHA_B, m.iconSha());
        assertEquals(SHA_C, m.backgroundSha());
        ServerMappings.Mapping outside = ServerMappings.parseManifest("alpha", """
                {"server-address":"alpha.de","pretty-name":"Alpha","categorys":[],
                 "assets":{"icon":"../other/icon.png","background":"./missing.png"}}""", files);
        assertEquals(List.of("alpha.de"), outside.addresses(), "a single address as string");
        assertNull(outside.icon(), "no paths out of the folder");
        assertNull(outside.background(), "only files that are in the folder");
        assertNull(ServerMappings.parseManifest("x", "{\"pretty-name\":\"No address\"}", files));
        assertNull(ServerMappings.parseManifest("x", "{\"server-address\":[\"a.de\"]}", files));
    }

    @Test
    void downloadsAreCheckedByGitBlobHashAndPngSignature() {
        // git hash-object of "hello\n"
        assertEquals("ce013625030ba8dba906f756967f9e9ca394464a", ServerMappings.blobSha("hello\n".getBytes(StandardCharsets.UTF_8)));
        assertTrue(ServerMappings.isPng(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0}));
        assertFalse(ServerMappings.isPng("<html>".getBytes(StandardCharsets.UTF_8)));
        assertFalse(ServerMappings.isPng(null));
    }

    @Test
    void theIndexIsCachedAndLoadedAgain(@TempDir Path dir) throws Exception {
        ServerMappings a = new ServerMappings(dir);
        ServerMappings.Mapping alpha = new ServerMappings.Mapping("alpha", "AlphaSMP", List.of("alpha.de"), List.of("PVP"),
                "icon.png", SHA_B, "bg.png", SHA_C);
        a.saveIndex(1234L, Map.of("alpha", alpha), Map.of("alpha", SHA_A));
        ServerMappings b = new ServerMappings(dir);
        assertEquals(1234L, b.loadIndex());
        assertEquals(alpha, b.mappings().get("alpha"));
        assertEquals(alpha, b.find("mc.alpha.de:25565"));
        assertNull(b.find("unknown.example"));
        assertNull(b.image(new ServerMappings.Mapping("x", "X", List.of("x.de"), List.of(), "icon.png", "../bad", null, null), false),
                "an invalid hash never becomes a file name");
    }

    @Test
    void namesAndCategoriesForTheList() {
        assertEquals("PvP", ServerMappings.category("PVP"));
        assertEquals("KitPvP", ServerMappings.category("KITPVP"));
        assertEquals("Survival", ServerMappings.category("SURVIVAL"));
        assertEquals("UHC", ServerMappings.category("uhc"));
        ServerMappings.Mapping m = mapping("alpha", "AlphaSMP", List.of("alpha.de"));
        assertEquals("AlphaSMP", ServerBrowserView.displayName("alpha.de", "alpha.de", m), "the name was only the address");
        assertEquals("AlphaSMP", ServerBrowserView.displayName("Minecraft-Server", "alpha.de", m));
        assertEquals("Mein Server", ServerBrowserView.displayName("Mein Server", "alpha.de", m), "an own name stays");
        assertEquals("alpha.de", ServerBrowserView.displayName("alpha.de", "alpha.de", null));
    }

    private static ServerMappings.Mapping mapping(String dir, String name, List<String> addresses) {
        return new ServerMappings.Mapping(dir, name, addresses, List.of(), null, null, null, null);
    }
}
