package dev.lumora.core.browser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Game modes from LabyMod/server-media: parsing, version ranges, matching, cache and the chips, offline. */
class ServerGamemodesTest {
    static final String SHA = "d".repeat(40);

    static final String HYPIXEL = """
            {"server_name":"hypixel","nice_name":"Hypixel Network","direct_ip":"mc.hypixel.net",
             "server_wildcards":["%.hypixel.net","%.bad%.net","nodot"],
             "social":{"web":"https://hypixel.net"},
             "gamemodes":{
               "sb":{"name":"SkyBlock","color":"#3B5BDB","command":"/play sb"},
               "bw":{"name":"Bed Wars Solo","color":"red"},
               "old":{"name":"Old Mode","color":"#FFFFFF","versions":"1.8<1.12.2"},
               "new":{"name":"New Mode","color":"#000000","versions":"1.8<1.12.2,1.20<*"},
               "empty":{"color":"#123456"}
             }}""";

    @Test
    void theTreeKeepsOnlyServerManifests() {
        String tree = """
                {"tree":[
                 {"path":"minecraft_servers/hypixel/manifest.json","type":"blob","sha":"%s"},
                 {"path":"minecraft_servers/hypixel/icon.png","type":"blob","sha":"%s"},
                 {"path":"minecraft_servers/../x/manifest.json","type":"blob","sha":"%s"},
                 {"path":"minecraft_servers/Upper/manifest.json","type":"blob","sha":"%s"},
                 {"path":"docs/manifest.json","type":"blob","sha":"%s"}
                ],"truncated":false}""".formatted(SHA, SHA, SHA, SHA, SHA);
        assertEquals(Map.of("hypixel", SHA), ServerGamemodes.parseTree(tree));
    }

    @Test
    void manifestsGiveNameAddressDomainsAndModes() {
        ServerGamemodes.Server s = ServerGamemodes.parseManifest("hypixel", HYPIXEL);
        assertEquals("Hypixel Network", s.name());
        assertEquals("mc.hypixel.net", s.address());
        assertEquals(List.of("hypixel.net"), s.domains(), "only plain %.domain wildcards");
        assertEquals(List.of("SkyBlock", "Bed Wars Solo", "Old Mode", "New Mode"),
                s.modes().stream().map(ServerGamemodes.Mode::name).toList(), "in manifest order, nameless left out");
        assertEquals(0x3B5BDB, s.modes().get(0).color());
        assertEquals(-1, s.modes().get(1).color(), "invalid colour");
        assertNull(ServerGamemodes.parseManifest("x", "{\"nice_name\":\"No address\"}"));
    }

    @Test
    void versionRangesHideModesOfOtherVersions() {
        assertTrue(ServerGamemodes.supports("", "1.21.11"));
        assertTrue(ServerGamemodes.supports("1.8<1.12.2", "1.8.9"));
        assertFalse(ServerGamemodes.supports("1.8<1.12.2", "1.21.11"));
        assertTrue(ServerGamemodes.supports("1.8<1.12.2,1.20<*", "26.3"));
        assertTrue(ServerGamemodes.supports("*<*", "1.8.9"));
        assertTrue(ServerGamemodes.supports("broken", "1.8.9"), "a broken range hides nothing");
        assertTrue(ServerGamemodes.compare("1.12", "1.8.9") > 0);
        assertEquals(0, ServerGamemodes.compare("1.21", "1.21.0"));
        ServerGamemodes.Server s = ServerGamemodes.parseManifest("hypixel", HYPIXEL);
        assertEquals(List.of("SkyBlock", "Bed Wars Solo", "New Mode"),
                ServerGamemodes.modesFor(s, "1.21.11").stream().map(ServerGamemodes.Mode::name).toList());
        assertEquals(4, ServerGamemodes.modesFor(s, "1.8.9").size());
    }

    @Test
    void addressesMatchTheDirectAddressAndWildcardDomains(@TempDir Path dir) throws Exception {
        ServerGamemodes.Server s = ServerGamemodes.parseManifest("hypixel", HYPIXEL);
        Map<String, ServerGamemodes.Server> all = Map.of("hypixel", s);
        assertEquals(s, ServerGamemodes.match(all, "mc.hypixel.net"));
        assertEquals(s, ServerGamemodes.match(all, "hypixel.net"));
        assertEquals(s, ServerGamemodes.match(all, "alpha.hypixel.net"));
        assertNull(ServerGamemodes.match(all, "nothypixel.net"));
        // the cache keeps everything
        ServerGamemodes a = new ServerGamemodes(dir);
        a.saveIndex(42L, all, Map.of("hypixel", SHA));
        ServerGamemodes b = new ServerGamemodes(dir);
        assertEquals(42L, b.loadIndex());
        assertEquals(s, b.find("MC.Hypixel.net:25565"));
        assertNull(b.find("example.org"));
    }

    @Test
    void chipsStopWithACountWhenTheyDoNotFit() {
        BrowserViewTest.FakeGraphics g = new BrowserViewTest.FakeGraphics();
        List<ServerGamemodes.Mode> modes = List.of(new ServerGamemodes.Mode("SkyBlock", 0x3B5BDB, ""),
                new ServerGamemodes.Mode("Bed Wars", 0xFFFFFF, ""), new ServerGamemodes.Mode("Housing", -1, ""));
        ServerBrowserView.chips(g, modes, 0, 0, 400);
        assertEquals(List.of("SkyBlock", "Bed Wars", "Housing"), g.texts.stream().map(BrowserViewTest.FakeGraphics.Text::text).toList());
        g.texts.clear();
        ServerBrowserView.chips(g, modes, 0, 0, 120); // room for one chip and the count
        assertEquals(List.of("SkyBlock", "+2"), g.texts.stream().map(BrowserViewTest.FakeGraphics.Text::text).toList());
        assertTrue(ServerBrowserView.light(0xFFFFFF));
        assertFalse(ServerBrowserView.light(0x3B5BDB));
    }
}
