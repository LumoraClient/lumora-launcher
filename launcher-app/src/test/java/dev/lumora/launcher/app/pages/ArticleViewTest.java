package dev.lumora.launcher.app.pages;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ArticleViewTest {
    @Test
    void changelogMarkupBecomesSafeHtml() {
        assertEquals("<b>Kinobalken:</b> Option &lt;neu&gt; im Ordner <code>addons</code> &amp; mehr",
                ArticleView.inline("**Kinobalken:** Option <neu> im Ordner `addons` & mehr"));
        assertEquals("&lt;script&gt;alert(1)&lt;/script&gt;", ArticleView.inline("<script>alert(1)</script>"));
    }

    @Test
    void subItemsAreNestedUnderTheItemBefore() {
        assertEquals("<ul><li>a<ul><li>a1</li><li>a2</li></ul></li><li>b</li></ul>",
                ArticleView.list(List.of("a", "  a1", "  a2", "b")));
        assertEquals("<ul><li>a</li><li>b<ul><li>b1</li></ul></li></ul>", ArticleView.list(List.of("a", "b", "  b1")));
    }
}
