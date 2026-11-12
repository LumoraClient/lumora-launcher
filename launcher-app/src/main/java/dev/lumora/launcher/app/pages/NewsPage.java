package dev.lumora.launcher.app.pages;

import dev.lumora.launcher.app.ui.Card;
import dev.lumora.launcher.app.ui.Icons;
import dev.lumora.launcher.app.ui.Theme;
import dev.lumora.launcher.app.ui.Ui;
import dev.lumora.launcher.core.news.NewsFeed;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * "Neuigkeiten" page: News | Changelog, filtered by Alle | Lumora | Minecraft. Lumora items ship with the launcher;
 * Minecraft: Java Edition news and patch notes come from Mojang's launcher content service (see {@link NewsFeed}).
 */
public final class NewsPage extends JPanel {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d. MMMM yyyy", Locale.GERMANY);

    private final PageHost host;
    private final ExecutorService io;
    private final NewsFeed feed;
    private final JPanel content = Ui.column();
    private final Map<String, Image> images = new ConcurrentHashMap<>();
    /** Own loader: pictures must not wait behind the other pages' start-up work on the shared executor. */
    private final ExecutorService imageLoader = java.util.concurrent.Executors.newFixedThreadPool(3, r -> {
        Thread t = new Thread(r, "news-images");
        t.setDaemon(true);
        return t;
    });
    private final JCheckBox snapshots = new JCheckBox("Snapshots anzeigen");
    private String view = "news";
    private String filter = "all";
    private List<NewsFeed.Item> mcNews;
    private List<NewsFeed.Change> mcNotes;
    private boolean loading;

    public NewsPage(PageHost host, ExecutorService io) {
        super(new BorderLayout(0, 14));
        this.host = host;
        this.io = io;
        this.feed = new NewsFeed(host.ctx().paths.root().resolve("cache").resolve("news"));
        setOpaque(false);
        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        left.setOpaque(false);
        left.setBorder(BorderFactory.createEmptyBorder(0, -12, 0, 0));
        left.add(Ui.segmented(List.of("news", "changelog"), List.of("News", "Changelog"), view, v -> {
            view = v;
            snapshots.setVisible(v.equals("changelog"));
            rebuild();
        }));
        JComponent sep = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(Theme.BORDER);
                g.fillRect(getWidth() / 2, 4, 1, getHeight() - 8);
            }
        };
        sep.setPreferredSize(new Dimension(9, 30));
        left.add(sep);
        left.add(Ui.segmented(List.of("all", "lumora", "minecraft"), List.of("Alle", "Lumora", "Minecraft"), filter, f -> {
            filter = f;
            rebuild();
        }));
        snapshots.setOpaque(false);
        snapshots.setFocusable(false);
        snapshots.setSelected(host.ctx().config.showSnapshots);
        snapshots.setVisible(false);
        snapshots.addActionListener(e -> {
            host.ctx().config.showSnapshots = snapshots.isSelected();
            host.saveConfig();
            rebuild();
        });
        left.add(snapshots);
        bar.add(left, BorderLayout.WEST);
        add(bar, BorderLayout.NORTH);
        add(Ui.scroll(content), BorderLayout.CENTER);
        rebuild();
    }

    /** Loads the Minecraft feeds when the page is shown (once per launcher start; cached copies offline). */
    public void refresh() {
        if (mcNews != null || loading) {
            return;
        }
        loading = true;
        io.submit(() -> {
            List<NewsFeed.Item> n = feed.minecraftNews();
            List<NewsFeed.Change> c = feed.minecraftPatchNotes();
            SwingUtilities.invokeLater(() -> {
                mcNews = n;
                mcNotes = c;
                loading = false;
                rebuild();
            });
        });
    }

    private boolean wants(NewsFeed.Source s) {
        return filter.equals("all") || (filter.equals("lumora") ? s == NewsFeed.Source.LUMORA : s == NewsFeed.Source.MINECRAFT);
    }

    private void rebuild() {
        content.removeAll();
        if (view.equals("news")) {
            List<NewsFeed.Item> items = new ArrayList<>();
            if (wants(NewsFeed.Source.LUMORA)) {
                items.addAll(NewsFeed.lumoraNews());
            }
            if (wants(NewsFeed.Source.MINECRAFT) && mcNews != null) {
                items.addAll(mcNews.subList(0, Math.min(30, mcNews.size())));
            }
            items.sort(Comparator.comparing(NewsFeed.Item::date, Comparator.nullsLast(Comparator.reverseOrder())));
            if (!items.isEmpty()) {
                content.add(new Hero(items.get(0)));
                content.add(Box.createVerticalStrut(14));
                JPanel grid = new JPanel(new GridLayout(0, 3, 14, 14));
                grid.setOpaque(false);
                grid.setAlignmentX(Component.LEFT_ALIGNMENT);
                for (NewsFeed.Item it : items.subList(1, items.size())) {
                    grid.add(new NewsCard(it));
                }
                content.add(grid);
            }
            note(items.isEmpty());
        } else {
            List<NewsFeed.Change> changes = new ArrayList<>();
            if (wants(NewsFeed.Source.LUMORA)) {
                changes.addAll(NewsFeed.lumoraChangelog());
            }
            if (wants(NewsFeed.Source.MINECRAFT) && mcNotes != null) {
                int n = 0;
                for (NewsFeed.Change c : mcNotes) {
                    if (!c.snapshot() || snapshots.isSelected()) {
                        changes.add(c);
                        if (++n >= 40) {
                            break;
                        }
                    }
                }
            }
            changes.sort(Comparator.comparing(NewsFeed.Change::date, Comparator.nullsLast(Comparator.reverseOrder())));
            for (NewsFeed.Change c : changes) {
                content.add(changeRow(c));
                content.add(Box.createVerticalStrut(10));
            }
            note(changes.isEmpty());
        }
        content.revalidate();
        content.repaint();
    }

    private void note(boolean empty) {
        String t = null;
        if (!filter.equals("lumora") && mcNews == null) {
            t = loading ? "Lade Minecraft-Neuigkeiten…" : null;
        } else if (!filter.equals("lumora") && mcNews != null && mcNews.isEmpty() && empty) {
            t = "Minecraft-Neuigkeiten sind gerade nicht erreichbar.";
        }
        if (empty && t == null) {
            t = "Keine Einträge.";
        }
        if (t != null) {
            JLabel l = Ui.label(t, 13.5f, Font.PLAIN, Theme.MUTED);
            l.setBorder(BorderFactory.createEmptyBorder(14, 2, 4, 2));
            content.add(l);
        }
    }

    // ------------------------------------------------------------------ images

    private Image image(String url, Runnable loaded) {
        if (url == null) {
            return null;
        }
        Image i = images.get(url);
        if (i == null && images.putIfAbsent(url + "#loading", new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB)) == null) {
            imageLoader.submit(() -> {
                byte[] b = feed.image(url);
                if (b == null) {
                    dev.lumora.launcher.core.util.Log.info("News image " + url + " not available");
                }
                try {
                    BufferedImage img = b == null ? null : ImageIO.read(new ByteArrayInputStream(b));
                    if (img != null) {
                        images.put(url, img);
                        SwingUtilities.invokeLater(loaded);
                    }
                } catch (Exception e) {
                    dev.lumora.launcher.core.util.Log.info("News image " + url + " not shown: " + e);
                }
            });
        }
        return i;
    }

    /** Draws the image scaled to cover the rectangle (center crop), or a Lumora gradient placeholder. */
    private static void cover(Graphics2D g, Image img, int x, int y, int w, int h, NewsFeed.Source src) {
        if (img == null) {
            Color a = src == NewsFeed.Source.LUMORA ? new Color(0x0E4F5C) : new Color(0x1E3A23);
            g.setPaint(new GradientPaint(x, y, a, x + w, y + h, Theme.SURFACE_2));
            g.fillRect(x, y, w, h);
            return;
        }
        int iw = img.getWidth(null);
        int ih = img.getHeight(null);
        double s = Math.max((double) w / iw, (double) h / ih);
        int dw = (int) Math.ceil(iw * s);
        int dh = (int) Math.ceil(ih * s);
        g.drawImage(img, x + (w - dw) / 2, y + (h - dh) / 2, dw, dh, null);
    }

    private static void quality(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }

    private static int chip(Graphics2D g, String text, int x, int y) {
        g.setFont(Theme.font(Font.BOLD, 12f));
        FontMetrics fm = g.getFontMetrics();
        int w = fm.stringWidth(text) + 20;
        g.setColor(text.equals("Lumora") ? Theme.ACCENT_PRESSED : new Color(0x3F7A3A));
        g.fillRoundRect(x, y, w, 24, 24, 24);
        g.setColor(Color.WHITE);
        g.drawString(text, x + 10, y + 16);
        return w;
    }

    private static List<String> wrap(FontMetrics fm, String text, int max, int maxLines) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split("\\s+")) {
            String cand = line.isEmpty() ? word : line + " " + word;
            if (fm.stringWidth(cand) <= max) {
                line.setLength(0);
                line.append(cand);
            } else {
                if (!line.isEmpty()) {
                    out.add(line.toString());
                }
                line.setLength(0);
                line.append(word);
            }
            if (out.size() == maxLines) {
                break;
            }
        }
        if (out.size() < maxLines && !line.isEmpty()) {
            out.add(line.toString());
        }
        if (out.size() == maxLines && fm.stringWidth(String.join(" ", out)) < fm.stringWidth(text)) {
            String last = out.get(maxLines - 1);
            while (last.length() > 1 && fm.stringWidth(last + "…") > max) {
                last = last.substring(0, last.length() - 1);
            }
            out.set(maxLines - 1, last + "…");
        }
        return out;
    }

    private static String label(NewsFeed.Source s) {
        return s == NewsFeed.Source.LUMORA ? "Lumora" : "Minecraft";
    }

    private static String meta(NewsFeed.Item it) {
        String d = it.date() == null ? "" : dayText(it.date());
        String a = it.author() == null || it.author().isBlank() ? "" : it.author();
        return (d + (a.isEmpty() ? "" : "  ·  " + a)).toUpperCase(Locale.GERMANY);
    }

    private static String dayText(LocalDate d) {
        LocalDate today = LocalDate.now();
        return d.equals(today) ? "Heute" : d.equals(today.minusDays(1)) ? "Gestern" : DATE.format(d);
    }

    /** Opens the article of a news card over the launcher (Lumora: text, screenshots and the full changelog). */
    private void open(NewsFeed.Item it) {
        ArticleView.open(host.frame(), NewsFeed.article(it), this::image, host::browse);
    }

    private void open(NewsFeed.Change c) {
        if (c.source() == NewsFeed.Source.LUMORA) {
            ArticleView.open(host.frame(), NewsFeed.article(c), this::image, host::browse);
        } else if (c.contentUrl() != null) {
            showPatchNotes(c);
        }
    }

    /**
     * Deep link (also for {@code --ui-shot --pages news/…}): {@code news}, {@code changelog}, {@code article} (newest
     * news), {@code article=<id>} or {@code release=<version>} (article of a Lumora release).
     */
    public void closeArticle() {
        ArticleView.closeAll(host.frame());
    }

    public void show(String what) {
        closeArticle();
        if (what.equals("news") || what.equals("changelog")) {
            if (!view.equals(what)) {
                view = what;
                snapshots.setVisible(what.equals("changelog"));
                rebuild();
            }
            return;
        }
        if (what.startsWith("release=")) {
            String v = what.substring("release=".length());
            NewsFeed.lumoraChangelog().stream().filter(c -> c.version().equals(v)).findFirst().ifPresent(this::open);
            return;
        }
        List<NewsFeed.Item> items = new ArrayList<>(NewsFeed.lumoraNews());
        items.sort(Comparator.comparing(NewsFeed.Item::date, Comparator.nullsLast(Comparator.reverseOrder())));
        String id = what.startsWith("article=") ? what.substring("article=".length()) : null;
        items.stream().filter(i -> id == null || i.id().equals(id)).findFirst().ifPresent(this::open);
    }

    // ------------------------------------------------------------------ news cards

    private final class Hero extends JComponent {
        private final NewsFeed.Item it;

        Hero(NewsFeed.Item it) {
            this.it = it;
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setPreferredSize(new Dimension(800, 360));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 360));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    open(it);
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            quality(g);
            int w = getWidth();
            int h = getHeight();
            java.awt.Shape clip0 = g.getClip();
            g.clip(new RoundRectangle2D.Double(0, 0, w, h, 18, 18));
            cover(g, image(it.image(), this::repaint), 0, 0, w, h, it.source());
            g.setPaint(new GradientPaint(0, h * 0.25f, new Color(10, 12, 16, 0), 0, h, new Color(10, 12, 16, 235)));
            g.fillRect(0, 0, w, h);
            g.setPaint(new GradientPaint(0, 0, new Color(10, 12, 16, 150), w * 0.6f, 0, new Color(10, 12, 16, 0)));
            g.fillRect(0, 0, w, h);
            chip(g, label(it.source()), 22, 20);
            g.setFont(Theme.font(Font.BOLD, 12.5f));
            g.setColor(new Color(0xD7DCE3));
            g.drawString(meta(it), 24, h - 112);
            g.setFont(Theme.font(Font.BOLD, 27f));
            g.setColor(Color.WHITE);
            List<String> title = wrap(g.getFontMetrics(), it.title(), w - 60, 2);
            int y = h - 82 - (title.size() - 1) * 32;
            for (String l : title) {
                g.drawString(l, 24, y);
                y += 32;
            }
            g.setFont(Theme.font(Font.PLAIN, 14.5f));
            g.setColor(new Color(0xE3E7EC));
            List<String> text = wrap(g.getFontMetrics(), it.text(), w - 60, 2);
            y = h - 42;
            for (String l : text) {
                g.drawString(l, 24, y);
                y += 20;
            }
            g.setClip(clip0);
            g.setColor(Theme.BORDER);
            g.drawRoundRect(0, 0, w - 1, h - 1, 18, 18);
            g.dispose();
        }
    }

    private final class NewsCard extends JComponent {
        private final NewsFeed.Item it;
        private boolean hover;

        NewsCard(NewsFeed.Item it) {
            this.it = it;
            setPreferredSize(new Dimension(300, 290));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    open(it);
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            quality(g);
            int w = getWidth();
            int h = getHeight();
            java.awt.Shape clip0 = g.getClip();
            g.clip(new RoundRectangle2D.Double(0, 0, w, h, 16, 16));
            g.setColor(hover ? Theme.SURFACE_2 : Theme.SURFACE);
            g.fillRect(0, 0, w, h);
            int ih = (int) (w * 0.5);
            cover(g, image(it.image(), this::repaint), 0, 0, w, ih, it.source());
            chip(g, label(it.source()), 14, 12);
            g.setFont(Theme.font(Font.BOLD, 12f));
            g.setColor(Theme.MUTED);
            g.drawString(meta(it), 16, ih + 24);
            g.setFont(Theme.font(Font.BOLD, 16f));
            g.setColor(Theme.TEXT);
            int y = ih + 48;
            for (String l : wrap(g.getFontMetrics(), it.title(), w - 32, 2)) {
                g.drawString(l, 16, y);
                y += 21;
            }
            g.setFont(Theme.font(Font.PLAIN, 13f));
            g.setColor(Theme.MUTED);
            for (String l : wrap(g.getFontMetrics(), it.text(), w - 32, Math.max(1, (h - y - 8) / 18))) {
                g.drawString(l, 16, y + 2);
                y += 18;
            }
            g.setClip(clip0);
            g.setColor(hover ? Theme.ACCENT : Theme.BORDER);
            g.drawRoundRect(0, 0, w - 1, h - 1, 16, 16);
            g.dispose();
        }
    }

    // ------------------------------------------------------------------ changelog

    private JComponent changeRow(NewsFeed.Change c) {
        Card card = new Card(new BorderLayout(16, 0)).padding(14, 14, 14, 18);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        JComponent thumb = new JComponent() {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                quality(g);
                g.clip(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 12, 12));
                Image img = image(c.image(), this::repaint);
                if (img == null && c.source() == NewsFeed.Source.LUMORA) {
                    g.setPaint(new GradientPaint(0, 0, new Color(0x0E4F5C), getWidth(), getHeight(), Theme.SURFACE_2));
                    g.fillRect(0, 0, getWidth(), getHeight());
                    Icons.of(Icons.Kind.CUBE, 34, Theme.ACCENT_HOVER).paintIcon(this, g, (getWidth() - 34) / 2, (getHeight() - 34) / 2);
                } else {
                    cover(g, img, 0, 0, getWidth(), getHeight(), c.source());
                }
                g.dispose();
            }
        };
        thumb.setPreferredSize(new Dimension(76, 76));
        JPanel thumbTop = new JPanel(new BorderLayout());
        thumbTop.setOpaque(false);
        thumbTop.add(thumb, BorderLayout.NORTH);
        card.add(thumbTop, BorderLayout.WEST);
        JPanel text = Ui.column();
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        top.setOpaque(false);
        top.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.setBorder(BorderFactory.createEmptyBorder(0, -8, 0, 0));
        String kind = c.source() == NewsFeed.Source.LUMORA ? "Lumora " + c.version() : c.snapshot() ? "Snapshot" : "Release";
        top.add(Ui.chip(kind, c.source() == NewsFeed.Source.LUMORA ? Theme.ACCENT : c.snapshot() ? Theme.WARN : Theme.OK,
                c.source() == NewsFeed.Source.LUMORA ? Theme.ACCENT_TEXT : c.snapshot() ? new Color(0xFBE3B3) : new Color(0xC4F3DA)));
        if (c.date() != null) {
            top.add(Ui.label(dayText(c.date()), 12.5f, Font.PLAIN, Theme.MUTED));
        }
        text.add(top);
        text.add(Box.createVerticalStrut(6));
        text.add(Ui.label(c.title(), 16, Font.BOLD, Theme.TEXT));
        if (!c.items().isEmpty()) {
            text.add(Box.createVerticalStrut(6));
            for (String item : c.items()) {
                text.add(Ui.wrapText("•  " + item, 13, Theme.MUTED));
            }
        }
        boolean lumora = c.source() == NewsFeed.Source.LUMORA;
        if (lumora || c.contentUrl() != null) {
            text.add(Box.createVerticalStrut(6));
            text.add(Ui.label(lumora ? "Ganzen Changelog anzeigen" : "Patch Notes anzeigen", 13, Font.PLAIN, Theme.ACCENT_HOVER));
            card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            card.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    open(c);
                }
            });
        }
        card.add(text, BorderLayout.CENTER);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Short.MAX_VALUE));
        return card;
    }

    private void showPatchNotes(NewsFeed.Change c) {
        io.submit(() -> {
            String html;
            try {
                html = feed.patchNotesHtml(c);
            } catch (Exception e) {
                html = null;
            }
            dev.lumora.launcher.core.news.Article a = NewsFeed.patchNotesArticle(c, html == null ? null : sanitize(html));
            SwingUtilities.invokeLater(() -> ArticleView.open(host.frame(), a, this::image, host::browse));
        });
    }

    /** Only simple text markup from the patch notes; no images, scripts, frames, forms or styles. */
    static String sanitize(String html) {
        return html.replaceAll("(?is)<(script|style|iframe|object|embed|form)[^>]*>.*?</\\1>", "")
                .replaceAll("(?is)<(img|iframe|object|embed|input|link|meta|base)[^>]*>", "")
                .replaceAll("(?is)\\son\\w+\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)", "")
                .replaceAll("(?is)\\s(style|href|src)\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)", "");
    }

}
