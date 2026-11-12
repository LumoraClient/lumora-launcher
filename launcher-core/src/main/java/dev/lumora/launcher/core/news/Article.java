package dev.lumora.launcher.core.news;

import java.time.LocalDate;
import java.util.List;

/**
 * A news article as the launcher shows it when a news card or a changelog entry is opened: title, subtitle, date,
 * author, a large picture and the body (paragraphs, headings, lists, screenshots and the full changelog of the
 * update). Read-only: there are no comments or reactions.
 *
 * <p>Text in {@link Block#text()} and {@link Block#items()} may use the simple inline markup of the changelog:
 * {@code **bold**} and {@code `code`}. {@link Kind#HTML} blocks carry already sanitized HTML (Minecraft patch notes).
 *
 * @param hero  picture above the body: {@code bundled:<name>.png} or an https URL on the content host, or null
 * @param link  "open in browser" link (minecraft.net only), or null
 * @param badge short label next to the date, e.g. "Lumora 0.3.29", or null
 */
public record Article(NewsFeed.Source source, String id, String title, String subtitle, LocalDate date, String author,
                      String badge, String hero, String link, List<Block> blocks) {

    public enum Kind {
        /** Section heading inside the body ({@code ### …} in the changelog). */
        HEADING,
        PARAGRAPH,
        /** Bullet list; an item starting with two spaces is a sub-item of the one before. */
        LIST,
        /** Screenshot with optional caption ({@code text}); {@code image} as for {@link Article#hero()}. */
        IMAGE,
        /** Divider that starts a part of the article, e.g. "Changelog · Lumora 0.3.29". */
        SECTION,
        /** Sanitized HTML (only simple text markup). */
        HTML
    }

    public record Block(Kind kind, String text, String image, List<String> items) {
        public static Block heading(String text) {
            return new Block(Kind.HEADING, text, null, List.of());
        }

        public static Block paragraph(String text) {
            return new Block(Kind.PARAGRAPH, text, null, List.of());
        }

        public static Block list(List<String> items) {
            return new Block(Kind.LIST, null, null, List.copyOf(items));
        }

        public static Block image(String image, String caption) {
            return new Block(Kind.IMAGE, caption, image, List.of());
        }

        public static Block section(String text) {
            return new Block(Kind.SECTION, text, null, List.of());
        }

        public static Block html(String html) {
            return new Block(Kind.HTML, html, null, List.of());
        }
    }
}
