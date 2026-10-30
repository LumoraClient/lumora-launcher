package dev.lumora.launcher.core.news;

import dev.lumora.launcher.core.servers.ServerDirectory;
import dev.lumora.launcher.core.servers.ServerHistory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NewsAndServersTest {
    @TempDir
    Path tmp;

    /** Shape of launchercontent.mojang.com/v2/news.json (trimmed). */
    static final String NEWS = """
            {"version":1,"entries":[
             {"title":"Bedrock only","tag":"news","category":"Minecraft for Windows","date":"2026-10-26","text":"x",
              "newsType":["News page","Bedrock"],"readMoreLink":"https://www.minecraft.net/a"},
             {"title":"Java Realms news","tag":"Java Realms","category":"Minecraft: Java Edition","date":"2026-10-20","text":"Realms!",
              "newsPageImage":{"title":"a.jpg","url":"/v2/images/realms 772x350.jpg"},"newsType":["Java","News page"],
              "readMoreLink":"https://www.minecraft.net/en-us/article/realms","id":"realms-1"},
             {"title":"Evil link","category":"Minecraft: Java Edition","date":"2026-10-22","text":"y","newsType":["Java"],
              "playPageImage":{"url":"/v2/images/../../etc"},"readMoreLink":"https://evil.example/phish"}
            ]}""";

    /** Shape of launchercontent.mojang.com/v2/javaPatchNotes.json (trimmed). */
    static final String NOTES = """
            {"version":1,"entries":[
             {"title":"Minecraft 26.3","version":"26.3","type":"release","image":{"url":"/v2/images/263.jpg"},
              "contentPath":"javaPatchNotes/26-3.json","id":"26-3","date":"2026-09-30T10:00:00.000Z"},
             {"title":"Minecraft 26.4 Snapshot 6","version":"26.4-snapshot-6","type":"snapshot",
              "contentPath":"javaPatchNotes/26-4-snapshot-6.json","id":"26-4-snapshot-6","date":"2026-10-27T13:40:29.000Z"},
             {"title":"Bad path","version":"x","type":"release","contentPath":"../../secret.json","id":"bad","date":"2026-01-01"}
            ]}""";

    @Test
    void minecraftNewsKeepsJavaEntriesAndSafeLinksOnly() {
        List<NewsFeed.Item> l = NewsFeed.parseNews(NEWS);
        assertEquals(2, l.size());
        assertEquals("Evil link", l.get(0).title()); // newest first
        assertNull(l.get(0).link(), "links only to minecraft.net");
        assertNull(l.get(0).image(), "no path traversal in image urls");
        NewsFeed.Item realms = l.get(1);
        assertEquals("realms-1", realms.id());
        assertEquals(LocalDate.of(2026, 10, 20), realms.date());
        assertEquals("https://launchercontent.mojang.com/v2/images/realms%20772x350.jpg", realms.image());
        assertEquals("https://www.minecraft.net/en-us/article/realms", realms.link());
    }

    @Test
    void patchNotesAreSortedAndContentPathsChecked() {
        List<NewsFeed.Change> l = NewsFeed.parsePatchNotes(NOTES);
        assertEquals("26-4-snapshot-6", l.get(0).id());
        assertTrue(l.get(0).snapshot());
        assertEquals("https://launchercontent.mojang.com/v2/javaPatchNotes/26-4-snapshot-6.json", l.get(0).contentUrl());
        assertEquals(LocalDate.of(2026, 9, 30), l.get(1).date());
        assertFalse(l.get(1).snapshot());
        assertNull(l.get(2).contentUrl(), "no traversal out of javaPatchNotes/");
    }

    @Test
    void bundledLumoraNewsAndChangelogLoad() {
        assertFalse(NewsFeed.lumoraNews().isEmpty());
        assertTrue(NewsFeed.lumoraNews().get(0).image().startsWith("bundled:"));
        assertFalse(NewsFeed.lumoraChangelog().isEmpty());
        assertFalse(NewsFeed.lumoraChangelog().get(0).items().isEmpty());
    }

    @Test
    void imagesOnlyFromTheContentHost() {
        NewsFeed f = new NewsFeed(tmp);
        assertNull(f.image("https://evil.example/x.jpg"));
        assertNull(f.image("http://launchercontent.mojang.com/v2/images/a.jpg"));
    }

    @Test
    void bundledServersHaveValidAddressesAndCustomOnesAreAdded() {
        List<ServerDirectory.Entry> b = ServerDirectory.bundled();
        assertTrue(b.size() >= 10);
        assertTrue(b.stream().allMatch(e -> ServerDirectory.validAddress(e.address())));
        assertTrue(b.stream().anyMatch(e -> e.address().equals("mc.hypixel.net")));
        assertFalse(ServerDirectory.validAddress("-quickPlayMultiplayer x"));
        assertFalse(ServerDirectory.validAddress("a b"));
        assertTrue(ServerDirectory.validAddress("play.example.de:25566"));
        ServerDirectory d = new ServerDirectory(tmp);
        List<ServerDirectory.Entry> all = d.all(List.of("play.example.de", "MC.HYPIXEL.NET", "bad address"));
        assertEquals(b.size() + 1, all.size());
        assertTrue(all.get(all.size() - 1).custom());
        d.rate("mc.hypixel.net", 4);
        assertEquals(4, new ServerDirectory(tmp).rating("MC.hypixel.net"), "ratings are saved");
    }

    @Test
    void historySparklineAndWeekTrendFromOwnMeasurements() {
        ServerHistory h = new ServerHistory(tmp);
        long now = 1_000_000_000L;
        long day = 24 * 3600;
        h.record("a.net", 100, now - 7 * day);
        h.record("a.net", 120, now - 7 * day + 300); // < 10 min later: ignored
        h.record("a.net", 200, now - 3600);
        h.record("a.net", 300, now - 60);
        assertEquals(100.0, h.weekChange("a.net", 200, now), 0.01);
        assertNull(h.weekChange("b.net", 5, now));
        int[] s = h.sparkline("a.net", now, day, 24);
        assertEquals(24, s.length);
        assertEquals(300, s[23]);
        assertEquals(200, s[22]);
        assertEquals(-1, s[0]);
        h.save();
        ServerHistory again = new ServerHistory(tmp);
        assertArrayEquals(s, again.sparkline("a.net", now, day, 24));
        assertTrue(again.trackedFor("a.net", now) >= 7 * day);
    }
}
