package dev.lumora.launcher.core.platform;

import dev.lumora.launcher.core.download.Downloader;
import dev.lumora.launcher.core.platform.PlatformModel.SearchQuery;
import dev.lumora.launcher.core.testutil.LocalServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModrinthProviderTest {
    static Downloader downloader() {
        return new Downloader(HttpClient.newHttpClient(), Set.of(), true, 1, Duration.ofMillis(1), 2);
    }

    @Test
    void facetsCombineFiltersWithAndAndValuesWithOr() {
        SearchQuery q = new SearchQuery("sodium", ProjectType.MOD, "1.21.4", List.of("fabric", "quilt"), List.of("optimization", "x\"]]"),
                PlatformModel.Environment.CLIENT, true, PlatformModel.Sort.DOWNLOADS, 0, 20);
        assertEquals("[[\"project_type:mod\"],[\"versions:1.21.4\"],[\"categories:fabric\",\"categories:quilt\"],"
                + "[\"categories:optimization\",\"categories:x\"],[\"client_side:required\",\"client_side:optional\"],[\"open_source:true\"]]",
                ModrinthProvider.facets(q), "nothing but slugs reaches the facet string");
        SearchQuery bad = new SearchQuery("", null, "1.21\"],[\"x", List.of(), List.of(), null, false, null, -5, 500);
        assertEquals("[]", ModrinthProvider.facets(bad));
        assertEquals(0, bad.offset());
        assertEquals(100, bad.limit());
    }

    @Test
    void searchProjectAndCacheAgainstALocalApi() throws IOException {
        AtomicReference<String> lastQuery = new AtomicReference<>();
        try (LocalServer s = new LocalServer()) {
            s.on("/v2/search", (ex, n) -> {
                lastQuery.set(URLDecoder.decode(ex.getRequestURI().getRawQuery(), StandardCharsets.UTF_8));
                return LocalServer.Response.json(200, """
                        {"hits":[{"project_id":"AANobbMI","slug":"sodium","title":"Sodium","description":"Fast","author":"jellysquid3",
                          "downloads":100,"follows":5,"icon_url":"https://cdn.modrinth.com/i.png","categories":["fabric","optimization"],
                          "display_categories":["optimization","fabric"],"versions":["1.21.4"],"date_modified":"2026-11-01T10:00:00Z",
                          "license":"LGPL-3.0","project_type":"mod","gallery":["https://cdn.modrinth.com/g.png"],"client_side":"required","server_side":"unsupported"}],
                         "offset":0,"limit":20,"total_hits":1}""");
            });
            s.on("/v2/project/sodium", (ex, n) -> LocalServer.Response.json(200, """
                    {"id":"AANobbMI","slug":"sodium","title":"Sodium","description":"Fast","body":"# Sodium","project_type":"mod",
                     "categories":["optimization"],"loaders":["fabric","quilt"],"game_versions":["1.21.4"],"downloads":100,"followers":5,
                     "license":{"id":"LGPL-3.0","name":"GNU LGPL 3","url":"https://x"},"source_url":"https://github.com/x","issues_url":"http://insecure",
                     "gallery":[{"url":"https://cdn.modrinth.com/b.png","featured":false,"ordering":2},{"url":"https://cdn.modrinth.com/a.png","featured":true,"title":"A","ordering":1}],
                     "donation_urls":[{"id":"x","platform":"Ko-fi","url":"https://ko-fi.com/x"}],"client_side":"required","server_side":"unsupported"}"""));
            ModrinthProvider p = new ModrinthProvider(downloader(), s.uri("/v2").toString());
            SearchQuery q = new SearchQuery("so dium", ProjectType.MOD, "1.21.4", List.of("fabric"), List.of(), null, false, null, 0, 20);
            PlatformModel.SearchPage page = p.search(q);
            assertEquals(1, page.total());
            PlatformModel.ProjectSummary hit = page.hits().getFirst();
            assertEquals(List.of("optimization"), hit.categories(), "loaders are not shown as categories");
            assertEquals(List.of("fabric"), hit.loaders());
            assertEquals(PlatformModel.Side.UNSUPPORTED, hit.server());
            assertTrue(lastQuery.get().contains("query=so dium"));
            p.search(q);
            assertEquals(1, s.count("/v2/search"), "the same search within minutes comes from the cache");

            PlatformModel.Project project = p.project("sodium");
            assertEquals("https://cdn.modrinth.com/a.png", project.gallery().getFirst().url(), "gallery in Modrinth's order");
            assertEquals("GNU LGPL 3", project.licenseName());
            assertEquals(Set.of("source"), project.links().keySet(), "only https links");
            assertEquals(List.of("https://ko-fi.com/x"), project.donations());
            assertEquals("https://modrinth.com/mod/sodium", project.pageUrl());
            assertThrows(IllegalArgumentException.class, () -> p.project("../admin"));
        }
    }

    @Test
    void repositoryTranslatesLumoraCategories() {
        SearchQuery q = new SearchQuery("", ProjectType.MOD, null, List.of(), List.of(), null, false, null, 0, 20);
        SearchQuery t = ModRepository.translate(Platform.MODRINTH, q, List.of("biomes"));
        assertEquals(List.of("worldgen"), t.categories());
        assertEquals("biome", t.text(), "a single category without its own slug narrows by keyword");
        SearchQuery two = ModRepository.translate(Platform.MODRINTH, q, List.of("performance-does-not-exist", "optimization", "transport"));
        assertEquals(List.of("optimization", "transportation"), two.categories());
    }

    @Test
    void cacheExpiresAndDoesNotKeepFailures() throws IOException {
        AtomicLong now = new AtomicLong(0);
        ApiCache c = new ApiCache(2, now::get);
        int[] calls = {0};
        c.get("a", Duration.ofSeconds(10), () -> "v" + ++calls[0]);
        assertEquals("v1", c.get("a", Duration.ofSeconds(10), () -> "v" + ++calls[0]));
        now.set(11_000);
        assertEquals("v2", c.get("a", Duration.ofSeconds(10), () -> "v" + ++calls[0]));
        assertThrows(IOException.class, () -> c.get("b", Duration.ofSeconds(10), () -> {
            throw new IOException("down");
        }));
        assertEquals("ok", c.get("b", Duration.ofSeconds(10), () -> "ok"), "a failure is asked again");
        c.get("c", Duration.ofSeconds(10), () -> "x");
        assertEquals(2, c.size(), "least recently used entries go first");
    }
}
