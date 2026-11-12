package dev.lumora.launcher.core.news;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads Lumora's {@code CHANGELOG.md} (shipped with the launcher) into releases: {@code ## 0.3.29 (2026-11-11)} starts
 * a release, {@code ### Spielermenü} a section, {@code - } a bullet (two more spaces: a sub-bullet, further indented
 * lines continue the bullet), other lines are paragraphs. Inline markup ({@code **bold**}, {@code `code`}) is kept.
 */
public final class Changelog {
    /** One release with its section names and its body as article blocks. */
    public record Release(String version, LocalDate date, List<String> sections, List<Article.Block> blocks) {
        /**
         * Short lines for the changelog list: the bold lead of each top-level bullet ("Kinobalken"), else the bullet
         * itself, shortened to about {@code maxChars}.
         */
        public List<String> highlights(int max, int maxChars) {
            List<String> out = new ArrayList<>();
            for (Article.Block b : blocks) {
                if (b.kind() != Article.Kind.LIST) {
                    continue;
                }
                for (String item : b.items()) {
                    if (item.startsWith("  ")) {
                        continue;
                    }
                    out.add(highlight(item, maxChars));
                    if (out.size() == max) {
                        return out;
                    }
                }
            }
            return out;
        }

        /** Number of top-level bullets (all changes of the release). */
        public int changes() {
            int n = 0;
            for (Article.Block b : blocks) {
                if (b.kind() == Article.Kind.LIST) {
                    n += (int) b.items().stream().filter(i -> !i.startsWith("  ")).count();
                }
            }
            return n;
        }
    }

    private static final Pattern RELEASE = Pattern.compile("^##\\s+(\\d[^\\s(]*)\\s*(?:\\((\\d{4}-\\d{2}-\\d{2})\\))?\\s*$");
    private static final Pattern LEAD = Pattern.compile("^\\*\\*(.+?)\\*\\*\\s*(.*)$");

    private Changelog() {
    }

    public static List<Release> parse(String markdown) {
        List<Release> out = new ArrayList<>();
        Builder cur = null;
        for (String raw : markdown.replace("\r\n", "\n").split("\n", -1)) {
            String line = stripEnd(raw);
            Matcher m = RELEASE.matcher(line);
            if (m.matches()) {
                if (cur != null) {
                    out.add(cur.build());
                }
                cur = new Builder(m.group(1), date(m.group(2)));
                continue;
            }
            if (cur == null || line.startsWith("# ")) {
                continue;
            }
            cur.line(line);
        }
        if (cur != null) {
            out.add(cur.build());
        }
        return out;
    }

    /** "**Kinobalken:** neue Option …" → "Kinobalken"; plain bullets are shortened at a word boundary. */
    static String highlight(String item, int maxChars) {
        Matcher m = LEAD.matcher(item.strip());
        String s;
        if (m.matches()) {
            s = m.group(1).strip();
            if (s.endsWith(":")) {
                s = s.substring(0, s.length() - 1);
            }
        } else {
            s = item.strip().replace("**", "").replace("`", "");
        }
        if (s.length() > maxChars) {
            int cut = s.lastIndexOf(' ', maxChars);
            s = s.substring(0, cut > maxChars / 2 ? cut : maxChars).replaceAll("[,;:–-]+$", "").strip() + " …";
        }
        return s;
    }

    private static String stripEnd(String s) {
        int e = s.length();
        while (e > 0 && Character.isWhitespace(s.charAt(e - 1))) {
            e--;
        }
        return s.substring(0, e);
    }

    private static LocalDate date(String s) {
        try {
            return s == null ? null : LocalDate.parse(s);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static final class Builder {
        final String version;
        final LocalDate date;
        final List<String> sections = new ArrayList<>();
        final List<Article.Block> blocks = new ArrayList<>();
        List<String> items;
        StringBuilder paragraph;

        Builder(String version, LocalDate date) {
            this.version = version;
            this.date = date;
        }

        void line(String line) {
            String t = line.strip();
            if (t.isEmpty()) {
                flushParagraph();
                return;
            }
            if (line.startsWith("### ")) {
                flush();
                String h = t.substring(4).strip();
                sections.add(h);
                blocks.add(Article.Block.heading(h));
                return;
            }
            int indent = line.length() - line.stripLeading().length();
            if (t.startsWith("- ") || t.startsWith("* ")) {
                flushParagraph();
                if (items == null) {
                    items = new ArrayList<>();
                }
                String text = t.substring(2).strip();
                items.add(indent >= 2 && !items.isEmpty() ? "  " + text : text);
                return;
            }
            if (items != null && indent >= 2 && !items.isEmpty()) {
                int last = items.size() - 1;
                items.set(last, items.get(last) + " " + t);
                return;
            }
            flushList();
            if (paragraph == null) {
                paragraph = new StringBuilder(t);
            } else {
                paragraph.append(' ').append(t);
            }
        }

        void flushList() {
            if (items != null) {
                blocks.add(Article.Block.list(items));
                items = null;
            }
        }

        void flushParagraph() {
            if (paragraph != null) {
                blocks.add(Article.Block.paragraph(paragraph.toString()));
                paragraph = null;
            }
        }

        void flush() {
            flushList();
            flushParagraph();
        }

        Release build() {
            flush();
            return new Release(version, date, List.copyOf(sections), List.copyOf(blocks));
        }
    }
}
