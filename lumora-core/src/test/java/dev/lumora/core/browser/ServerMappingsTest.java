package dev.lumora.core.browser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Server mappings (LumoraClient/Multiplayer-Mapping): parsing, checks, address matching, game modes and the cache, offline. */
class ServerMappingsTest {
    static final String SHA_B = "b".repeat(40);
    static final String SHA_C = "c".repeat(40);

    static final String INDEX = """
            {"format":1,"servers":{
              "hypixel":{"name":"Hypixel Network","addresses":["mc.hypixel.net","hypixel.net"],
                "categories":["MINIGAMES","PVP"],
                "icon":{"file":"servers/hypixel/icon.png","sha":"%s","source":"x"},
                "background":{"file":"servers/hypixel/background.png","sha":"%s"},
                "gamemodes":[
                  {"name":"SkyBlock","color":"#3B5BDB"},
                  {"name":"Bed Wars Solo","color":"red"},
                  {"name":"Old Mode","color":"#FFFFFF","versions":"1.8<1.12.2"},
                  {"name":"New Mode","versions":"1.8<1.12.2,1.20<*"},
                  {"color":"#123456"}
                ],
                "from":{"norisk":"hypixel","labymod":"hypixel"}},
              "evil":{"name":"Evil","addresses":["evil.net"],
                "icon":{"file":"servers/hypixel/icon.png","sha":"%s"},
                "background":{"file":"../../other/repo/bg.png","sha":"%s"}},
              "Bad Name":{"name":"Bad","addresses":["bad.net"]},
              "noaddress":{"name":"No address","addresses":[]},
              "noname":{"addresses":["a.net"]}
            }}""".formatted(SHA_B, SHA_C, SHA_B, SHA_C);

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
    void theListKeepsValidServersAndOnlyTheirOwnPictures() {
        Map<String, ServerMappings.Mapping> all = ServerMappings.parseIndex(INDEX);
        assertEquals(List.of("hypixel", "evil"), List.copyOf(all.keySet()), "bad ids, no name or no address are left out");
        ServerMappings.Mapping h = all.get("hypixel");
        assertEquals("Hypixel Network", h.name());
        assertEquals(List.of("mc.hypixel.net", "hypixel.net"), h.addresses());
        assertEquals(List.of("MINIGAMES", "PVP"), h.categories());
        assertEquals("servers/hypixel/icon.png", h.icon());
        assertEquals(SHA_B, h.iconSha());
        assertEquals(SHA_C, h.backgroundSha());
        assertEquals(List.of("SkyBlock", "Bed Wars Solo", "Old Mode", "New Mode"),
                h.modes().stream().map(ServerMappings.Mode::name).toList(), "in list order, nameless left out");
        assertEquals(0x3B5BDB, h.modes().get(0).color());
        assertEquals(-1, h.modes().get(1).color(), "invalid colour");
        ServerMappings.Mapping evil = all.get("evil");
        assertNull(evil.icon(), "no picture of another server");
        assertNull(evil.background(), "no paths out of the folder");
        assertTrue(ServerMappings.parseIndex("[]").isEmpty());
        assertTrue(ServerMappings.parseIndex("{\"servers\":[]}").isEmpty());
    }

    @Test
    void versionRangesHideModesOfOtherVersions() {
        assertTrue(ServerMappings.supports("", "1.21.11"));
        assertTrue(ServerMappings.supports("1.8<1.12.2", "1.8.9"));
        assertFalse(ServerMappings.supports("1.8<1.12.2", "1.21.11"));
        assertTrue(ServerMappings.supports("1.8<1.12.2,1.20<*", "26.3"));
        assertTrue(ServerMappings.supports("*<*", "1.8.9"));
        assertTrue(ServerMappings.supports("broken", "1.8.9"), "a broken range hides nothing");
        assertTrue(ServerMappings.compare("1.12", "1.8.9") > 0);
        assertEquals(0, ServerMappings.compare("1.21", "1.21.0"));
        ServerMappings.Mapping h = ServerMappings.parseIndex(INDEX).get("hypixel");
        assertEquals(List.of("SkyBlock", "Bed Wars Solo", "New Mode"),
                ServerMappings.modesFor(h, "1.21.11").stream().map(ServerMappings.Mode::name).toList());
        assertEquals(4, ServerMappings.modesFor(h, "1.8.9").size());
    }

    @Test
    void onlyFilesOfTheMappingRepositoryAreDownloaded() {
        assertTrue(ServerMappings.allowed(ServerMappings.INDEX_URL));
        assertTrue(ServerMappings.allowed(ServerMappings.BASE_URL + "servers/hypixel/icon.png"));
        assertFalse(ServerMappings.allowed("https://raw.githubusercontent.com/NoRiskClient/nrc-server-mappings/main/x.png"));
        assertFalse(ServerMappings.allowed("https://raw.githubusercontent.com/LumoraClient/Multiplayer-Mapping/dev/servers.json"));
        assertFalse(ServerMappings.allowed(ServerMappings.BASE_URL + "../../other/main/x"));
        assertFalse(ServerMappings.allowed("http://raw.githubusercontent.com/LumoraClient/Multiplayer-Mapping/main/servers.json"));
        assertFalse(ServerMappings.allowed("https://example.org/LumoraClient/Multiplayer-Mapping/main/servers.json"));
        assertFalse(ServerMappings.allowed(ServerMappings.INDEX_URL + "?x=1"));
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
    void theListIsCachedAndLoadedAgain(@TempDir Path config) throws Exception {
        Path dir = config.resolve("multiplayer-mapping");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("servers.json"), INDEX);
        ServerMappings a = new ServerMappings(dir);
        a.writeChecked(1234L);
        ServerMappings b = new ServerMappings(dir);
        assertEquals(1234L, b.loadIndex());
        assertEquals("Hypixel Network", b.find("MC.Hypixel.net:25565").name());
        assertEquals("hypixel", b.find("alpha.hypixel.net").dir());
        assertNull(b.find("unknown.example"));
        assertNull(b.image(new ServerMappings.Mapping("x", "X", List.of("x.de"), List.of(), "servers/x/icon.png", "../bad",
                null, null, List.of()), false), "an invalid hash never becomes a file name");
    }

    @Test
    void theCachesOfTheOldSourcesAreRemoved(@TempDir Path config) throws Exception {
        Files.createDirectories(config.resolve("server-mappings/images"));
        Files.writeString(config.resolve("server-mappings/images/a.png"), "x");
        Files.createDirectories(config.resolve("server-gamemodes"));
        Files.writeString(config.resolve("server-gamemodes/index.json"), "{}");
        Files.writeString(config.resolve("keep.json"), "{}");
        new ServerMappings(config.resolve("multiplayer-mapping")).removeOldCaches();
        assertFalse(Files.exists(config.resolve("server-mappings")));
        assertFalse(Files.exists(config.resolve("server-gamemodes")));
        assertTrue(Files.exists(config.resolve("keep.json")), "nothing else is touched");
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
        assertEquals("alpha.de", ServerBrowserView.displayName("alpha.de", "alpha.de", (String) null));
    }

    @Test
    void chipsStopWithACountWhenTheyDoNotFit() {
        BrowserViewTest.FakeGraphics g = new BrowserViewTest.FakeGraphics();
        List<ServerMappings.Mode> modes = List.of(new ServerMappings.Mode("SkyBlock", 0x3B5BDB, ""),
                new ServerMappings.Mode("Bed Wars", 0xFFFFFF, ""), new ServerMappings.Mode("Housing", -1, ""));
        ServerBrowserView.chips(g, modes, 0, 0, 400);
        assertEquals(List.of("SkyBlock", "Bed Wars", "Housing"), g.texts.stream().map(BrowserViewTest.FakeGraphics.Text::text).toList());
        g.texts.clear();
        ServerBrowserView.chips(g, modes, 0, 0, 120); // room for one chip and the count
        assertEquals(List.of("SkyBlock", "+2"), g.texts.stream().map(BrowserViewTest.FakeGraphics.Text::text).toList());
        assertTrue(ServerBrowserView.light(0xFFFFFF));
        assertFalse(ServerBrowserView.light(0x3B5BDB));
    }

    private static ServerMappings.Mapping mapping(String dir, String name, List<String> addresses) {
        return new ServerMappings.Mapping(dir, name, addresses, List.of(), null, null, null, null, List.of());
    }
}
