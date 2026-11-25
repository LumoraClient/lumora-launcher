package dev.lumora.launcher.app.ui;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a project description (Markdown, often with embedded HTML) into simple, safe HTML for a read-only text pane:
 * everything is escaped first, then only headings, emphasis, code, lists, quotes, rules and https links are rebuilt.
 * Raw HTML tags of the source are dropped (their text stays), images become their alt text, so the pane never loads
 * anything from the network by itself.
 */
public final class Markdown {
    private static final Pattern LINK = Pattern.compile("\\[([^\\]]{0,300})]\\((https://[^)\\s]{1,500})\\)");
    private static final Pattern IMAGE = Pattern.compile("!\\[([^\\]]{0,200})]\\([^)]{0,600}\\)");
    private static final Pattern TAG = Pattern.compile("<[^>]{0,2000}>");
    private static final Pattern HTML_IMG_ALT = Pattern.compile("<img[^>]*alt=\"([^\"]{0,200})\"[^>]*>", Pattern.CASE_INSENSITIVE);

    private Markdown() {
    }

    public static String toHtml(String md) {
        if (md == null || md.isBlank()) {
            return "";
        }
        String src = md.replace("\r", "");
        // HTML in the source: line breaks and paragraphs keep their meaning, everything else goes
        src = HTML_IMG_ALT.matcher(src).replaceAll(m -> m.group(1).isBlank() ? "" : "\n_" + Matcher.quoteReplacement(m.group(1)) + "_\n");
        src = src.replaceAll("(?i)<br\\s*/?>", "\n").replaceAll("(?i)</p>|</h[1-6]>|</div>|</li>", "\n");
        src = TAG.matcher(src).replaceAll("");
        src = src.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"");
        StringBuilder out = new StringBuilder();
        String[] lines = src.split("\n", -1);
        boolean inCode = false;
        String list = null;
        StringBuilder para = new StringBuilder();
        for (String raw : lines) {
            String line = raw.stripTrailing();
            if (line.strip().startsWith("```")) {
                flush(out, para);
                list = closeList(out, list);
                out.append(inCode ? "</pre>" : "<pre>");
                inCode = !inCode;
                continue;
            }
            if (inCode) {
                out.append(escape(line)).append('\n');
                continue;
            }
            String t = line.strip();
            if (t.isEmpty()) {
                flush(out, para);
                list = closeList(out, list);
                continue;
            }
            Matcher h = Pattern.compile("^(#{1,6})\\s+(.*)$").matcher(t);
            if (h.matches()) {
                flush(out, para);
                list = closeList(out, list);
                int level = Math.min(4, h.group(1).length() + 1);
                out.append("<h").append(level).append('>').append(inline(h.group(2).replaceAll("#+$", "").strip())).append("</h").append(level).append('>');
                continue;
            }
            if (t.matches("^(-{3,}|\\*{3,}|_{3,})$")) {
                flush(out, para);
                list = closeList(out, list);
                out.append("<hr>");
                continue;
            }
            Matcher ul = Pattern.compile("^[-*+]\\s+(.*)$").matcher(t);
            Matcher ol = Pattern.compile("^\\d{1,3}[.)]\\s+(.*)$").matcher(t);
            if (ul.matches() || ol.matches()) {
                flush(out, para);
                String want = ul.matches() ? "ul" : "ol";
                if (!want.equals(list)) {
                    list = closeList(out, list);
                    out.append('<').append(want).append('>');
                    list = want;
                }
                out.append("<li>").append(inline(ul.matches() ? ul.group(1) : ol.group(1))).append("</li>");
                continue;
            }
            if (t.startsWith(">")) {
                flush(out, para);
                list = closeList(out, list);
                out.append("<blockquote>").append(inline(t.replaceFirst("^>+\\s?", ""))).append("</blockquote>");
                continue;
            }
            if (para.length() > 0) {
                para.append(' ');
            }
            para.append(t);
        }
        flush(out, para);
        closeList(out, list);
        if (inCode) {
            out.append("</pre>");
        }
        return out.toString();
    }

    private static void flush(StringBuilder out, StringBuilder para) {
        if (para.length() > 0) {
            out.append("<p>").append(inline(para.toString())).append("</p>");
            para.setLength(0);
        }
    }

    private static String closeList(StringBuilder out, String list) {
        if (list != null) {
            out.append("</").append(list).append('>');
        }
        return null;
    }

    /** Inline formatting on escaped text: images to alt text, https links, code, bold, italic. */
    static String inline(String s) {
        String t = IMAGE.matcher(s).replaceAll(m -> m.group(1).isBlank() ? "" : Matcher.quoteReplacement("_" + m.group(1) + "_"));
        StringBuilder out = new StringBuilder();
        Matcher m = LINK.matcher(t);
        int last = 0;
        while (m.find()) {
            out.append(format(escape(t.substring(last, m.start()))));
            out.append("<a href=\"").append(escape(m.group(2))).append("\">").append(format(escape(m.group(1)))).append("</a>");
            last = m.end();
        }
        out.append(format(escape(t.substring(last))));
        return out.toString();
    }

    private static String format(String escaped) {
        return escaped.replaceAll("`([^`]{1,200})`", "<code>$1</code>")
                .replaceAll("\\*\\*([^*]{1,500})\\*\\*", "<b>$1</b>")
                .replaceAll("__([^_]{1,500})__", "<b>$1</b>")
                .replaceAll("(?<![*\\w])\\*([^*]{1,500})\\*(?!\\*)", "<i>$1</i>")
                .replaceAll("(?<![_\\w])_([^_]{1,500})_(?![_\\w])", "<i>$1</i>")
                .replaceAll("~~([^~]{1,500})~~", "<s>$1</s>");
    }

    static String escape(String s) {
        StringBuilder b = new StringBuilder(s.length() + 16);
        for (char c : s.toCharArray()) {
            switch (c) {
                case '<' -> b.append("&lt;");
                case '>' -> b.append("&gt;");
                case '&' -> b.append("&amp;");
                case '"' -> b.append("&quot;");
                default -> {
                    if (c >= 0x20 || c == '\t') {
                        b.append(c);
                    }
                }
            }
        }
        return b.toString();
    }

    /** A style sheet in the launcher colours for {@code javax.swing.text.html.HTMLEditorKit}. */
    public static String css() {
        String text = Theme.hex(Theme.TEXT);
        String muted = Theme.hex(Theme.MUTED);
        String accent = Theme.hex(Theme.ACCENT_HOVER);
        String surface = Theme.hex(Theme.SURFACE_2);
        return ("body{color:%s;font-family:sans-serif;font-size:11pt;margin:0}h2{font-size:15pt;margin:14px 0 6px 0}"
                + "h3{font-size:13pt;margin:12px 0 4px 0}h4{font-size:11.5pt;margin:10px 0 4px 0}p{margin:0 0 8px 0}"
                + "a{color:%s}code{font-family:monospace;background:%s}pre{font-family:monospace;background:%s;padding:6px}"
                + "blockquote{color:%s;margin:4px 0 8px 12px}li{margin:2px 0}")
                .formatted(text, accent, surface, surface, muted).toLowerCase(Locale.ROOT);
    }
}
