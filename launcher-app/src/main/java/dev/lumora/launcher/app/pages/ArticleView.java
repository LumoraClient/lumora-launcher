package dev.lumora.launcher.app.pages;

import dev.lumora.launcher.app.ui.Icons;
import dev.lumora.launcher.app.ui.Theme;
import dev.lumora.launcher.core.news.Article;
import dev.lumora.launcher.core.news.NewsFeed;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
import javax.swing.Timer;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Full-window article over the launcher, opened from a news card or a changelog entry: centered title and subtitle,
 * date and author, a large picture, then the text with headings, lists and screenshots and the complete changelog of
 * the update. Only for reading: no comments, no reactions. Esc, the ✕ or a click beside the article closes it.
 */
final class ArticleView extends JComponent {
    /** Image cache of the news page: returns the picture or null and calls {@code loaded} once it is there. */
    interface Images {
        Image get(String url, Runnable loaded);
    }

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d. MMMM yyyy", Locale.GERMANY);
    /** Width of the text column (like a news site). */
    private static final int COLUMN = 800;
    private static final int MARGIN = 18;

    private final JLayeredPane layer;
    private final JPanel card;
    private final Body body;
    private JScrollPane scroll;
    private final ComponentAdapter follow;
    private float alpha = Theme.reduceMotion ? 1f : 0f;

    private ArticleView(JFrame frame, Article a, Images images, Consumer<String> browse) {
        this.layer = frame.getLayeredPane();
        setLayout(null);
        setOpaque(false);

        card = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                quality(g);
                g.setColor(Theme.mix(Theme.BG, Theme.SURFACE, 0.35));
                g.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 18, 18));
                g.setColor(Theme.BORDER);
                g.draw(new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1, getHeight() - 1, 18, 18));
                g.dispose();
            }
        };
        card.setOpaque(false);

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        bar.setOpaque(false);
        bar.setBorder(BorderFactory.createEmptyBorder(12, 12, 4, 12));
        if (a.link() != null) {
            JButton web = new JButton("Im Browser öffnen", Icons.of(Icons.Kind.GLOBE, 15, Theme.TEXT));
            web.setFocusable(false);
            web.setToolTipText(a.link());
            web.addActionListener(e -> browse.accept(a.link()));
            bar.add(web);
        }
        JButton close = new JButton(Icons.of(Icons.Kind.X, 15, Theme.TEXT));
        close.setFocusable(false);
        close.setToolTipText("Schließen (Esc)");
        close.addActionListener(e -> close());
        bar.add(close);
        card.add(bar, BorderLayout.NORTH);

        body = new Body();
        build(a, images, browse);
        JScrollPane sp = new JScrollPane(body);
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        sp.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 4));
        sp.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sp.getVerticalScrollBar().setUnitIncrement(24);
        scroll = sp;
        card.add(sp, BorderLayout.CENTER);
        add(card);

        // the overlay takes every click; a click beside the article closes it
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!card.getBounds().contains(e.getPoint())) {
                    close();
                }
            }
        });
        addMouseWheelListener(e -> { });
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close-article");
        getActionMap().put("close-article", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                close();
            }
        });
        follow = new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                setBounds(0, 0, layer.getWidth(), layer.getHeight());
                revalidate();
            }
        };
    }

    /** Shows the article over the whole launcher window (replaces an article that is already open). */
    static void open(JFrame frame, Article a, Images images, Consumer<String> browse) {
        closeAll(frame);
        ArticleView v = new ArticleView(frame, a, images, browse);
        JLayeredPane layer = frame.getLayeredPane();
        v.setBounds(0, 0, layer.getWidth(), layer.getHeight());
        layer.add(v, JLayeredPane.MODAL_LAYER);
        layer.addComponentListener(v.follow);
        layer.revalidate();
        layer.repaint();
        v.body.requestFocusInWindow();
        javax.swing.SwingUtilities.invokeLater(() -> v.scroll.getViewport().setViewPosition(new java.awt.Point()));
        if (v.alpha < 1f) {
            long start = System.currentTimeMillis();
            Timer t = new Timer(15, null);
            t.addActionListener(e -> {
                v.alpha = Math.min(1f, (System.currentTimeMillis() - start) / 160f);
                v.repaint();
                if (v.alpha >= 1f) {
                    t.stop();
                }
            });
            t.start();
        }
    }

    /** Closes any open article (e.g. before switching pages). */
    static void closeAll(JFrame frame) {
        for (Component c : frame.getLayeredPane().getComponentsInLayer(JLayeredPane.MODAL_LAYER)) {
            if (c instanceof ArticleView v) {
                v.close();
            }
        }
    }

    private void close() {
        layer.removeComponentListener(follow);
        layer.remove(this);
        layer.revalidate();
        layer.repaint();
    }

    @Override
    public void doLayout() {
        card.setBounds(MARGIN, MARGIN, Math.max(0, getWidth() - 2 * MARGIN), Math.max(0, getHeight() - 2 * MARGIN));
    }

    @Override
    public void paint(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        g.setColor(new Color(3, 5, 10, 175));
        g.fillRect(0, 0, getWidth(), getHeight());
        super.paint(g);
        g.dispose();
    }

    // ------------------------------------------------------------------ content

    private void build(Article a, Images images, Consumer<String> browse) {
        body.add(new TextBlock(a.title(), Theme.font(Font.BOLD, 34f), Color.WHITE, true, 6, 12));
        if (a.subtitle() != null && !a.subtitle().isBlank()) {
            body.add(new TextBlock(a.subtitle(), Theme.font(Font.PLAIN, 16.5f), Theme.mix(Theme.TEXT, Theme.MUTED, 0.45), true, 5, 18));
        }
        List<Chip> chips = new ArrayList<>();
        if (a.date() != null) {
            chips.add(new Chip(Icons.Kind.CLOCK, DATE.format(a.date())));
        }
        if (a.author() != null && !a.author().isBlank()) {
            chips.add(new Chip(Icons.Kind.USER, a.author()));
        }
        if (a.badge() != null) {
            chips.add(new Chip(Icons.Kind.CUBE, a.badge()));
        }
        if (!chips.isEmpty()) {
            body.add(new ChipRow(chips));
        }
        if (a.hero() != null) {
            body.add(new ImageBlock(images, a.hero(), null, a.source(), 26));
        } else {
            body.add(new Spacer(8));
        }
        for (Article.Block b : a.blocks()) {
            switch (b.kind()) {
                case HEADING -> body.add(new TextBlock(b.text(), Theme.font(Font.BOLD, 20f), Color.WHITE, false, 4, 10));
                case PARAGRAPH -> body.add(new HtmlBlock("<p>" + inline(b.text()) + "</p>", 14));
                case LIST -> body.add(new HtmlBlock(list(b.items()), 14));
                case IMAGE -> body.add(new ImageBlock(images, b.image(), b.text(), a.source(), 22));
                case SECTION -> body.add(new SectionHeader(b.text()));
                case HTML -> body.add(new HtmlBlock(b.text(), 14));
            }
        }
        if (a.link() != null) {
            JButton more = new JButton("Ganzen Artikel auf minecraft.net lesen", Icons.of(Icons.Kind.GLOBE, 15, Theme.TEXT));
            more.setFocusable(false);
            more.addActionListener(e -> browse.accept(a.link()));
            body.add(new ButtonRow(more));
        }
        body.add(new Spacer(30));
    }

    /** Changelog markup to HTML: escaped text, {@code **bold**}, {@code `code`}. */
    static String inline(String s) {
        String e = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
        e = e.replaceAll("\\*\\*(.+?)\\*\\*", "<b>$1</b>");
        return e.replaceAll("`([^`]+)`", "<code>$1</code>");
    }

    /** Bullet list; items starting with two spaces are nested under the item before. */
    static String list(List<String> items) {
        StringBuilder sb = new StringBuilder("<ul>");
        boolean nested = false;
        boolean open = false;
        for (String item : items) {
            boolean sub = item.startsWith("  ") && open;
            if (sub && !nested) {
                sb.append("<ul>");
                nested = true;
            } else if (!sub && nested) {
                sb.append("</ul>");
                nested = false;
            }
            if (!sub && open) {
                sb.append("</li>");
            }
            sb.append("<li>").append(inline(item.strip()));
            if (sub) {
                sb.append("</li>");
            } else {
                open = true;
            }
        }
        if (nested) {
            sb.append("</ul>");
        }
        if (open) {
            sb.append("</li>");
        }
        return sb.append("</ul>").toString();
    }

    private static void quality(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }

    static List<String> wrap(FontMetrics fm, String text, int max) {
        List<String> out = new ArrayList<>();
        for (String para : text.split("\n")) {
            StringBuilder line = new StringBuilder();
            for (String word : para.split("\\s+")) {
                if (word.isEmpty()) {
                    continue;
                }
                String cand = line.isEmpty() ? word : line + " " + word;
                if (fm.stringWidth(cand) <= max || line.isEmpty()) {
                    line.setLength(0);
                    line.append(cand);
                } else {
                    out.add(line.toString());
                    line.setLength(0);
                    line.append(word);
                }
            }
            out.add(line.toString());
        }
        return out;
    }

    // ------------------------------------------------------------------ layout

    /** A block of the article column: its height for a given width and the space below it. */
    private interface Flow {
        int heightFor(int width);

        int gapAfter();
    }

    /** The scrolling column: blocks stacked at up to {@link #COLUMN} px wide, centered. */
    private static final class Body extends JPanel implements Scrollable {
        Body() {
            setOpaque(false);
            setFocusable(true);
            setLayout(new LayoutManager() {
                @Override
                public void addLayoutComponent(String name, Component comp) {
                }

                @Override
                public void removeLayoutComponent(Component comp) {
                }

                @Override
                public Dimension preferredLayoutSize(Container p) {
                    int w = p.getWidth() > 0 ? p.getWidth() : COLUMN + 80;
                    int y = 8;
                    int cw = column(w);
                    for (Component c : p.getComponents()) {
                        Flow f = (Flow) c;
                        y += f.heightFor(cw) + f.gapAfter();
                    }
                    return new Dimension(w, y);
                }

                @Override
                public Dimension minimumLayoutSize(Container p) {
                    return new Dimension(200, 100);
                }

                @Override
                public void layoutContainer(Container p) {
                    int cw = column(p.getWidth());
                    int x = (p.getWidth() - cw) / 2;
                    int y = 8;
                    for (Component c : p.getComponents()) {
                        Flow f = (Flow) c;
                        int h = f.heightFor(cw);
                        c.setBounds(x, y, cw, h);
                        y += h + f.gapAfter();
                    }
                }
            });
            addComponentListener(new ComponentAdapter() {
                private int lastWidth = -1;

                @Override
                public void componentResized(ComponentEvent e) {
                    if (getWidth() != lastWidth) {
                        lastWidth = getWidth();
                        revalidate();
                    }
                }
            });
        }

        static int column(int width) {
            return Math.max(260, Math.min(COLUMN, width - 64));
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle r, int orientation, int direction) {
            return 24;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle r, int orientation, int direction) {
            return Math.max(40, r.height - 60);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    private static final class Spacer extends JComponent implements Flow {
        private final int h;

        Spacer(int h) {
            this.h = h;
        }

        @Override
        public int heightFor(int width) {
            return h;
        }

        @Override
        public int gapAfter() {
            return 0;
        }
    }

    /** Wrapped text drawn with the launcher font (title, subtitle, headings). */
    private static final class TextBlock extends JComponent implements Flow {
        private final String text;
        private final Font font;
        private final Color color;
        private final boolean center;
        private final int lineGap;
        private final int gap;

        TextBlock(String text, Font font, Color color, boolean center, int lineGap, int gap) {
            this.text = text == null ? "" : text;
            this.font = font;
            this.color = color;
            this.center = center;
            this.lineGap = lineGap;
            this.gap = gap;
        }

        @Override
        public int heightFor(int width) {
            FontMetrics fm = getFontMetrics(font);
            int n = wrap(fm, text, width).size();
            return n * fm.getHeight() + (n - 1) * lineGap;
        }

        @Override
        public int gapAfter() {
            return gap;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            quality(g);
            g.setFont(font);
            g.setColor(color);
            FontMetrics fm = g.getFontMetrics();
            int y = fm.getAscent();
            for (String l : wrap(fm, text, getWidth())) {
                int x = center ? (getWidth() - fm.stringWidth(l)) / 2 : 0;
                g.drawString(l, x, y);
                y += fm.getHeight() + lineGap;
            }
            g.dispose();
        }
    }

    private record Chip(Icons.Kind icon, String text) {
    }

    /** Date, author and version as small framed chips, centered under the subtitle. */
    private static final class ChipRow extends JComponent implements Flow {
        private static final Font FONT = Theme.font(Font.BOLD, 13.5f);
        private final List<Chip> chips;

        ChipRow(List<Chip> chips) {
            this.chips = chips;
        }

        @Override
        public int heightFor(int width) {
            return 36;
        }

        @Override
        public int gapAfter() {
            return 26;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            quality(g);
            g.setFont(FONT);
            FontMetrics fm = g.getFontMetrics();
            int gap = 12;
            int total = -gap;
            for (Chip c : chips) {
                total += fm.stringWidth(c.text()) + 50 + gap;
            }
            int x = Math.max(0, (getWidth() - total) / 2);
            for (Chip c : chips) {
                int w = fm.stringWidth(c.text()) + 50;
                g.setColor(Theme.SURFACE_2);
                g.fillRoundRect(x, 0, w, 35, 10, 10);
                g.setColor(Theme.BORDER);
                g.drawRoundRect(x, 0, w - 1, 34, 10, 10);
                Icons.of(c.icon(), 15, Theme.TEXT).paintIcon(this, g, x + 14, 10);
                g.setColor(Theme.TEXT);
                g.drawString(c.text(), x + 36, (35 - fm.getHeight()) / 2 + fm.getAscent());
                x += w + gap;
            }
            g.dispose();
        }
    }

    /** Screenshot or header picture, as wide as the column, rounded, with an optional caption below. */
    private final class ImageBlock extends JComponent implements Flow {
        private static final Font CAPTION = Theme.font(Font.PLAIN, 13.5f);
        private final Images images;
        private final String url;
        private final String caption;
        private final NewsFeed.Source source;
        private final int gap;

        ImageBlock(Images images, String url, String caption, NewsFeed.Source source, int gap) {
            this.images = images;
            this.url = url;
            this.caption = caption;
            this.source = source;
            this.gap = gap;
        }

        private Image image() {
            return images.get(url, () -> {
                body.revalidate();
                body.repaint();
            });
        }

        /** Picture height: the image's aspect ratio (16:9 while it loads), at most 560 px. */
        private int pictureHeight(int width, Image img) {
            if (img == null || img.getWidth(null) <= 0) {
                return width * 9 / 16;
            }
            return Math.min(560, (int) Math.round((double) width * img.getHeight(null) / img.getWidth(null)));
        }

        @Override
        public int heightFor(int width) {
            int h = pictureHeight(width, image());
            if (caption != null) {
                FontMetrics fm = getFontMetrics(CAPTION);
                h += 10 + wrap(fm, caption, width - 20).size() * fm.getHeight();
            }
            return h;
        }

        @Override
        public int gapAfter() {
            return gap;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            quality(g);
            int w = getWidth();
            Image img = image();
            int ph = pictureHeight(w, img);
            java.awt.Shape clip0 = g.getClip();
            RoundRectangle2D shape = new RoundRectangle2D.Double(0, 0, w, ph, 14, 14);
            g.clip(shape);
            if (img == null) {
                Color a = source == NewsFeed.Source.LUMORA ? Theme.mix(Theme.ACCENT_DEEP, Theme.BG, 0.6) : new Color(0x1E3A23);
                g.setPaint(new GradientPaint(0, 0, a, w, ph, Theme.SURFACE_2));
                g.fillRect(0, 0, w, ph);
            } else {
                int iw = img.getWidth(null);
                int ih = img.getHeight(null);
                // fit inside (very tall pictures get bars), otherwise fill the width
                double s = Math.min((double) w / iw, (double) ph / ih);
                int dw = (int) Math.round(iw * s);
                int dh = (int) Math.round(ih * s);
                if (dw < w) {
                    g.setColor(Theme.BG);
                    g.fillRect(0, 0, w, ph);
                }
                g.drawImage(img, (w - dw) / 2, (ph - dh) / 2, dw, dh, null);
            }
            g.setClip(clip0);
            g.setColor(Theme.BORDER);
            g.draw(new RoundRectangle2D.Double(0.5, 0.5, w - 1, ph - 1, 14, 14));
            if (caption != null) {
                g.setFont(CAPTION);
                g.setColor(Theme.MUTED);
                FontMetrics fm = g.getFontMetrics();
                int y = ph + 10 + fm.getAscent();
                for (String l : wrap(fm, caption, w - 20)) {
                    g.drawString(l, (w - fm.stringWidth(l)) / 2, y);
                    y += fm.getHeight();
                }
            }
            g.dispose();
        }
    }

    /** "CHANGELOG · LUMORA 0.3.29" with a line, starts the changelog part of the article. */
    private static final class SectionHeader extends JComponent implements Flow {
        private static final Font FONT = Theme.font(Font.BOLD, 13f);
        private final String text;

        SectionHeader(String text) {
            this.text = text.toUpperCase(Locale.GERMANY);
        }

        @Override
        public int heightFor(int width) {
            return 46;
        }

        @Override
        public int gapAfter() {
            return 10;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            quality(g);
            g.setFont(FONT);
            FontMetrics fm = g.getFontMetrics();
            int y = 30;
            int x = 0;
            g.setColor(Theme.ACCENT_HOVER);
            for (char ch : text.toCharArray()) {
                g.drawString(String.valueOf(ch), x, y);
                x += fm.charWidth(ch) + 1;
            }
            g.setColor(Theme.BORDER);
            g.fillRect(x + 12, y - fm.getAscent() / 2 + 1, Math.max(0, getWidth() - x - 12), 1);
            g.dispose();
        }
    }

    private static final class ButtonRow extends JPanel implements Flow {
        ButtonRow(JButton b) {
            super(new FlowLayout(FlowLayout.CENTER, 0, 0));
            setOpaque(false);
            add(b);
        }

        @Override
        public int heightFor(int width) {
            return getPreferredSize().height + 4;
        }

        @Override
        public int gapAfter() {
            return 10;
        }
    }

    /** Paragraph, list or sanitized HTML in the launcher font; wraps to the column width. */
    private static final class HtmlBlock extends JEditorPane implements Flow {
        private final int gap;

        HtmlBlock(String html, int gap) {
            this.gap = gap;
            setEditorKit(new HTMLEditorKit());
            putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
            setFont(Theme.font(Font.PLAIN, 15f));
            setForeground(Theme.TEXT);
            setOpaque(false);
            setEditable(false);
            setFocusable(false);
            setBorder(BorderFactory.createEmptyBorder());
            setCursor(Cursor.getDefaultCursor());
            // setText must not scroll the article to this block
            ((javax.swing.text.DefaultCaret) getCaret()).setUpdatePolicy(javax.swing.text.DefaultCaret.NEVER_UPDATE);
            var css = ((HTMLDocument) getDocument()).getStyleSheet();
            css.addRule("body { margin: 0; color: " + Theme.hex(Theme.TEXT) + "; }");
            css.addRule("p { margin-top: 0; margin-bottom: 0; }");
            css.addRule("ul { margin-top: 0; margin-bottom: 0; margin-left: 20px; }");
            css.addRule("li { margin-bottom: 7px; }");
            css.addRule("b { color: #FFFFFF; }");
            css.addRule("code { font-family: Monospaced; color: " + Theme.hex(Theme.ACCENT_2) + "; }");
            css.addRule("h1 { font-size: 20pt; color: #FFFFFF; margin-top: 18px; margin-bottom: 8px; }");
            css.addRule("h2 { font-size: 17pt; color: #FFFFFF; margin-top: 16px; margin-bottom: 6px; }");
            css.addRule("h3 { font-size: 15pt; color: #FFFFFF; margin-top: 12px; margin-bottom: 4px; }");
            css.addRule("a { color: " + Theme.hex(Theme.ACCENT_HOVER) + "; }");
            setText("<html><body>" + html + "</body></html>");
        }

        @Override
        public int heightFor(int width) {
            setSize(width, Short.MAX_VALUE);
            return getPreferredSize().height;
        }

        @Override
        public int gapAfter() {
            return gap;
        }
    }
}
