package dev.lumora.launcher.core.news;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArticleTest {
    @Test
    void parsesReleasesWithSectionsBulletsSubBulletsAndParagraphs() {
        String md = """
                # Änderungen

                ## 0.3.30 (2026-11-11)

                ### Zoom
                - **Kinobalken:** neue Option
                  über zwei Zeilen.
                - Zweiter Punkt
                  - Unterpunkt

                Ein Absatz.

                ## 0.3.29 (2026-11-10)

                ### Party
                - Einladen
                """;
        List<Changelog.Release> r = Changelog.parse(md);
        assertEquals(2, r.size());
        Changelog.Release a = r.get(0);
        assertEquals("0.3.30", a.version());
        assertEquals(LocalDate.of(2026, 11, 11), a.date());
        assertEquals(List.of("Zoom"), a.sections());
        assertEquals(Article.Block.heading("Zoom"), a.blocks().get(0));
        assertEquals(List.of("**Kinobalken:** neue Option über zwei Zeilen.", "Zweiter Punkt", "  Unterpunkt"), a.blocks().get(1).items());
        assertEquals(Article.Block.paragraph("Ein Absatz."), a.blocks().get(2));
        assertEquals(3, a.blocks().size());
        assertEquals(List.of("Kinobalken", "Zweiter Punkt"), a.highlights(4, 90));
        assertEquals(2, a.changes());
        assertEquals("0.3.29", r.get(1).version());
        assertEquals(List.of("Einladen"), r.get(1).blocks().get(1).items());
    }

    @Test
    void longPlainBulletsAreShortenedAtAWordBoundary() {
        String s = Changelog.highlight("Lumora läuft weiter auf Fabric. Den Loader wählt aber nur noch der Launcher selbst", 40);
        assertTrue(s.endsWith(" …"), s);
        assertTrue(s.length() <= 42, s);
        assertEquals("Neue Option", Changelog.highlight("**Neue Option:** Text dahinter", 40));
        assertEquals("Ordner addons", Changelog.highlight("Ordner `addons`", 40));
    }

    @Test
    void theBundledChangelogListsEveryReleaseNewestFirst() {
        List<Changelog.Release> releases = NewsFeed.lumoraReleases();
        assertTrue(releases.size() >= 30, "releases: " + releases.size());
        Set<String> seen = new HashSet<>();
        LocalDate last = LocalDate.MAX;
        for (Changelog.Release r : releases) {
            assertTrue(seen.add(r.version()), "twice: " + r.version());
            assertNotNull(r.date(), r.version());
            assertFalse(r.date().isAfter(last), r.version());
            assertFalse(r.blocks().isEmpty(), r.version());
            last = r.date();
        }
        List<NewsFeed.Change> changes = NewsFeed.lumoraChangelog();
        assertEquals(releases.size(), changes.size());
        assertEquals(releases.get(0).version(), changes.get(0).version());
        assertFalse(changes.get(0).items().isEmpty());
    }

    @Test
    void aNewsArticleHasItsBodyScreenshotsAndTheFullChangelog() {
        NewsFeed.Item it = news("lumora-player-menu");
        Article a = NewsFeed.article(it);
        assertEquals(it.title(), a.title());
        assertEquals(it.text(), a.subtitle());
        assertEquals("Lumora", a.author());
        assertEquals("Lumora 0.3.29", a.badge());
        assertEquals("bundled:player-menu.png", a.hero());
        assertNull(a.link());
        assertTrue(a.blocks().contains(Article.Block.image("bundled:player-menu-category.png",
                "Kategorien öffnen ihre Einträge als äußeren Ring – hier die Einträge eines Addons.")));
        int section = a.blocks().indexOf(Article.Block.section("Changelog · Lumora 0.3.29"));
        assertTrue(section > 0);
        // the changelog part is the complete release
        assertEquals(NewsFeed.lumoraRelease("0.3.29").blocks(), a.blocks().subList(section + 1, a.blocks().size()));
    }

    @Test
    void anArticleAboutSeveralReleasesHasEachChangelog() {
        Article a = NewsFeed.article(news("lumora-hud-editor-anchors"));
        assertTrue(a.blocks().contains(Article.Block.section("Changelog · Lumora 0.3.27")));
        assertTrue(a.blocks().contains(Article.Block.section("Changelog · Lumora 0.3.28")));
        assertEquals("Lumora 0.3.28", a.badge());
    }

    @Test
    void oldNewsWithALongTextShowItAsTheFirstParagraph() {
        NewsFeed.Item it = news("lumora-attack-indicator");
        assertTrue(it.text().length() > NewsFeed.SUBTITLE_MAX);
        Article a = NewsFeed.article(it);
        assertNull(a.subtitle());
        assertEquals(Article.Block.paragraph(it.text()), a.blocks().get(0));
        assertTrue(a.blocks().contains(Article.Block.section("Changelog · Lumora 0.3.9")));
    }

    @Test
    void aChangelogEntryOpensTheNewsArticleOrTheRelease() {
        NewsFeed.Change c29 = change("0.3.29");
        assertEquals(news("lumora-player-menu").title(), c29.title());
        assertEquals("bundled:player-menu.png", c29.image());
        assertEquals("lumora-player-menu", NewsFeed.article(c29).id());

        NewsFeed.Change c24 = change("0.3.24");
        assertEquals("Fadenkreuz genau in der Mitte · Eigenes Fadenkreuz frei zeichnen", c24.title());
        Article a = NewsFeed.article(c24);
        assertEquals("Lumora 0.3.24", a.badge());
        assertEquals(Article.Block.section("Changelog · Lumora 0.3.24"), a.blocks().get(0));
        assertTrue(a.subtitle().startsWith("2 Änderungen – Im Spiel, Hub"), a.subtitle());
    }

    @Test
    void minecraftNewsKeepTheirLinkAndShowAShortTeaserAsSubtitle() {
        NewsFeed.Item it = new NewsFeed.Item(NewsFeed.Source.MINECRAFT, "x", "Titel", "Kurz.", LocalDate.of(2026, 9, 24), null,
                "https://www.minecraft.net/de-de/article/x", "Minecraft: Java Edition");
        Article a = NewsFeed.article(it);
        assertEquals("Kurz.", a.subtitle());
        assertEquals("https://www.minecraft.net/de-de/article/x", a.link());
        assertEquals("Minecraft: Java Edition", a.author());
        assertTrue(a.blocks().isEmpty());
    }

    @Test
    void everyPictureOfTheBundledNewsIsShipped(@TempDir Path cache) {
        NewsFeed feed = new NewsFeed(cache);
        List<String> missing = new ArrayList<>();
        for (NewsFeed.Item it : NewsFeed.lumoraNews()) {
            Article a = NewsFeed.article(it);
            List<String> urls = new ArrayList<>();
            urls.add(a.hero());
            a.blocks().stream().filter(b -> b.kind() == Article.Kind.IMAGE).forEach(b -> urls.add(b.image()));
            for (String u : urls) {
                if (u == null || feed.image(u) == null) {
                    missing.add(it.id() + ": " + u);
                }
            }
        }
        assertEquals(List.of(), missing);
    }

    @Test
    void everyVersionNamedByANewsItemIsInTheChangelog() {
        for (NewsFeed.Item it : NewsFeed.lumoraNews()) {
            Article a = NewsFeed.article(it);
            if (a.badge() != null) {
                assertNotNull(NewsFeed.lumoraRelease(a.badge().substring("Lumora ".length())), it.id());
            }
        }
    }

    private static NewsFeed.Item news(String id) {
        return NewsFeed.lumoraNews().stream().filter(i -> i.id().equals(id)).findFirst().orElseThrow();
    }

    private static NewsFeed.Change change(String version) {
        return NewsFeed.lumoraChangelog().stream().filter(c -> c.version().equals(version)).findFirst().orElseThrow();
    }
}
