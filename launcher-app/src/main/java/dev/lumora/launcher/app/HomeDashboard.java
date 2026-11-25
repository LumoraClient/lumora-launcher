package dev.lumora.launcher.app;

import dev.lumora.launcher.app.pages.NewsPage;
import dev.lumora.launcher.app.ui.Card;
import dev.lumora.launcher.app.ui.Icons;
import dev.lumora.launcher.app.ui.Theme;
import dev.lumora.launcher.app.ui.Ui;
import dev.lumora.launcher.app.ui.VersionArt;
import dev.lumora.launcher.core.i18n.Lang;
import dev.lumora.launcher.core.launch.LaunchCommandBuilder;
import dev.lumora.launcher.core.library.InstanceLibrary;
import dev.lumora.launcher.core.news.NewsFeed;
import dev.lumora.launcher.core.profile.Flavor;
import dev.lumora.launcher.core.profile.ProfileCatalog;
import dev.lumora.launcher.core.stats.PlayStats;
import dev.lumora.launcher.core.util.Log;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
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
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * The start page below the banner: "Recently played" (the version played last, its newest screenshot, when and how
 * long, continue in the world of that session), "Downloads" (the running game installation with its progress, or
 * whether the selected version is installed and component updates) and the newest news.
 */
final class HomeDashboard {

    interface Host {
        AppContext ctx();

        Flavor flavor();

        /** Selects the profile and starts it (quickPlay may be null). */
        void play(String profileId, LaunchCommandBuilder.QuickPlay quickPlay);

        void showPage(String id);

        void openFolder(Path dir);

        /** "Check files and start" for the selected version. */
        void repair();

        /** Lists the component updates (dialog). */
        void checkUpdates();

        /** The launcher's "more" menu (logs, diagnostics, crashes, performance, update check). */
        JPopupMenu moreMenu();
    }

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Lang.locale());
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern(Lang.t("format.date_time"), Lang.locale());
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern(Lang.t("format.date_medium"), Lang.locale());

    private final Host host;
    private final NewsPage news;
    private final ExecutorService io;

    // recently played
    private final Cover cover = new Cover();
    private final JLabel recentTitle = Ui.label(" ", 20, Font.BOLD, Theme.TEXT);
    private final JLabel recentWhen = info(Icons.Kind.CLOCK);
    private final JLabel recentTime = info(Icons.Kind.TIMER);
    private final JLabel recentWorld = info(Icons.Kind.CUBE);
    private final JButton continueButton;
    private String recentProfile;
    private LaunchCommandBuilder.QuickPlay recentWorldPlay;
    private int recentGeneration;

    // downloads
    private final JLabel dlTitle = Ui.label(" ", 15.5f, Font.BOLD, Theme.TEXT);
    private final JLabel dlDetail = Ui.label(" ", 13, Font.PLAIN, Theme.MUTED);
    private final JLabel dlUpdates = Ui.label(" ", 12.5f, Font.BOLD, Theme.ACCENT_HOVER);
    private final Bar dlBar = new Bar();
    private boolean downloading;
    private int updates = -1;

    // news
    private final JPanel newsList = Ui.column();

    HomeDashboard(Host host, NewsPage news, ExecutorService io) {
        this.host = host;
        this.news = news;
        this.io = io;
        continueButton = outlineButton(Lang.t("home.recent.continue"), Icons.Kind.PLAY);
        continueButton.addActionListener(e -> {
            if (recentProfile != null) {
                host.play(recentProfile, recentWorldPlay);
            } else {
                host.play(host.ctx().selectedProfile().id, null);
            }
        });
        news.onUpdate(this::refreshNews);
    }

    /** The two columns: recently played and downloads on the left, news on the right. */
    JComponent build() {
        JPanel grid = new JPanel(new GridBagLayout());
        grid.setOpaque(false);
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 0.58;
        grid.add(recentCard(), c);
        c.gridy = 1;
        c.insets = new Insets(16, 0, 0, 0);
        grid.add(downloadsCard(), c);
        c.gridx = 1;
        c.gridy = 0;
        c.gridheight = 2;
        c.weightx = 0.42;
        c.weighty = 1;
        c.insets = new Insets(0, 16, 0, 0);
        grid.add(newsCard(), c);
        refreshRecent();
        refreshNews();
        idle();
        return grid;
    }

    // ------------------------------------------------------------------ cards

    private static Card card() {
        // narrow preferred width: GridBagLayout falls back to the minimum sizes (also the heights) as soon as the
        // preferred width does not fit; the column weights share the width instead
        Card card = new Card(new BorderLayout(0, 12)) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                return new Dimension(Math.min(d.width, 240), d.height);
            }
        }.calm();
        card.radius(Theme.arc(16));
        card.padding(14, 20, 14, 20);
        return card;
    }

    /** Card heading: icon, title and an optional "View all →" link. */
    private JComponent header(Icons.Kind icon, String title, Runnable all) {
        JPanel h = new JPanel(new BorderLayout());
        h.setOpaque(false);
        JLabel t = Ui.label(title, 18, Font.BOLD, Theme.TEXT);
        // like the design: an orange flame for "Recently played", the other headings in the text colour
        t.setIcon(icon == Icons.Kind.FLAME ? Icons.of(icon, 24, new Color(0xFF6A2A)) : Icons.of(icon, 22, Theme.TEXT));
        t.setIconTextGap(12);
        h.add(t, BorderLayout.WEST);
        if (all != null) {
            JLabel link = Ui.label(Lang.t("home.view_all") + "  →", 13.5f, Font.BOLD, Theme.ACCENT_HOVER);
            link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            link.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    all.run();
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    link.setForeground(Theme.TEXT);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    link.setForeground(Theme.ACCENT_HOVER);
                }
            });
            h.add(link, BorderLayout.EAST);
        }
        JPanel wrap = new JPanel(new BorderLayout(0, 8));
        wrap.setOpaque(false);
        wrap.add(h, BorderLayout.CENTER);
        JComponent line = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(Theme.alpha(Theme.BORDER, 160));
                g.fillRect(0, 0, getWidth(), 1);
            }
        };
        line.setPreferredSize(new Dimension(10, 1));
        wrap.add(line, BorderLayout.SOUTH);
        return wrap;
    }

    private JComponent recentCard() {
        Card card = card();
        card.add(header(Icons.Kind.FLAME, Lang.t("home.recent.title"), () -> host.showPage("library")), BorderLayout.NORTH);
        JPanel body = new JPanel(new BorderLayout(22, 0));
        body.setOpaque(false);
        body.add(cover, BorderLayout.WEST);
        JPanel text = Ui.column();
        JPanel title = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        title.setOpaque(false);
        title.add(new GrassBlock(30));
        title.add(Box.createHorizontalStrut(12));
        title.add(recentTitle);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        text.add(title);
        text.add(Box.createVerticalStrut(12));
        for (JLabel l : List.of(recentWhen, recentTime, recentWorld)) {
            text.add(l);
            text.add(Box.createVerticalStrut(6));
        }
        text.add(Box.createVerticalGlue());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        actions.setOpaque(false);
        actions.add(continueButton);
        actions.add(Box.createHorizontalStrut(12));
        JButton more = squareButton(Icons.Kind.MORE, Lang.t("common.more"));
        more.addActionListener(e -> recentMenu().show(more, 0, more.getHeight() + 4));
        actions.add(more);
        actions.setAlignmentX(Component.LEFT_ALIGNMENT);
        text.add(actions);
        body.add(text, BorderLayout.CENTER);
        card.add(body, BorderLayout.CENTER);
        return card;
    }

    /** "…" of the recently played card: folders, file check and the launcher's more menu. */
    private JPopupMenu recentMenu() {
        JPopupMenu m = new JPopupMenu();
        String id = recentProfile != null ? recentProfile : host.ctx().selectedProfile().id;
        Path game = host.ctx().gameDir(host.flavor().instanceId(id));
        m.add(item(Lang.t("home.open_game_dir"), Icons.Kind.FOLDER, () -> host.openFolder(game)));
        m.add(item(Lang.t("home.recent.screenshots"), Icons.Kind.IMAGE, () -> host.openFolder(game.resolve("screenshots"))));
        m.add(item(Lang.t("versions.repair"), Icons.Kind.REFRESH, host::repair));
        m.add(item(Lang.t("settings.title"), Icons.Kind.GEAR, () -> host.showPage("settings")));
        m.addSeparator();
        for (Component c : host.moreMenu().getComponents()) {
            m.add(c);
        }
        return m;
    }

    private static javax.swing.JMenuItem item(String text, Icons.Kind icon, Runnable action) {
        javax.swing.JMenuItem mi = new javax.swing.JMenuItem(text, Icons.of(icon, 14, Theme.TEXT));
        mi.addActionListener(e -> action.run());
        return mi;
    }

    private JComponent downloadsCard() {
        Card card = card();
        card.add(header(Icons.Kind.DOWNLOAD, Lang.t("home.downloads.title"), null), BorderLayout.NORTH);
        JPanel row = new JPanel(new BorderLayout(16, 0));
        row.setOpaque(false);
        row.add(new GrassBlock(40), BorderLayout.WEST);
        JPanel text = Ui.column();
        text.add(dlTitle);
        text.add(Box.createVerticalStrut(4));
        text.add(dlDetail);
        text.add(Box.createVerticalStrut(10));
        dlBar.setAlignmentX(Component.LEFT_ALIGNMENT);
        text.add(dlBar);
        dlUpdates.setAlignmentX(Component.LEFT_ALIGNMENT);
        dlUpdates.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        dlUpdates.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                host.checkUpdates();
            }
        });
        text.add(dlUpdates);
        row.add(text, BorderLayout.CENTER);
        card.add(row, BorderLayout.CENTER);
        return card;
    }

    private JComponent newsCard() {
        Card card = card();
        card.add(header(Icons.Kind.NEWS, Lang.t("nav.news"), () -> host.showPage("news")), BorderLayout.NORTH);
        newsList.setOpaque(false);
        JPanel holder = new JPanel(new BorderLayout());
        holder.setOpaque(false);
        holder.add(newsList, BorderLayout.NORTH);
        card.add(holder, BorderLayout.CENTER);
        return card;
    }

    // ------------------------------------------------------------------ recently played

    /** Reloads the recently played card (after a game ended, when the version changes). */
    void refreshRecent() {
        int gen = ++recentGeneration;
        AppContext ctx = host.ctx();
        Flavor flavor = host.flavor();
        io.submit(() -> {
            ProfileCatalog.Profile best = null;
            PlayStats.Entry bestStats = null;
            for (ProfileCatalog.Profile p : ctx.catalog.profiles()) {
                PlayStats.Entry s = ctx.play.stats().get(p.id);
                if (s.lastPlayed > 0 && (bestStats == null || s.lastPlayed > bestStats.lastPlayed)) {
                    best = p;
                    bestStats = s;
                }
            }
            ProfileCatalog.Profile shown = best != null ? best : ctx.selectedProfile();
            Path game = ctx.gameDir(flavor.instanceId(shown.id));
            BufferedImage img = null;
            InstanceLibrary.World world = null;
            try {
                List<InstanceLibrary.Screenshot> shots = new ArrayList<>(InstanceLibrary.screenshots(game));
                shots.sort(Comparator.comparing(InstanceLibrary.Screenshot::taken, Comparator.nullsLast(Comparator.reverseOrder())));
                for (InstanceLibrary.Screenshot s : shots.subList(0, Math.min(3, shots.size()))) {
                    img = ImageIO.read(s.file().toFile());
                    if (img != null) {
                        break;
                    }
                }
                if (bestStats != null) {
                    Instant started = Instant.ofEpochMilli(bestStats.lastPlayed);
                    // the world played in that session: saved after its start
                    world = InstanceLibrary.worlds(game).stream()
                            .filter(w -> w.lastPlayed() != null && !w.lastPlayed().isBefore(started.minusSeconds(60)))
                            .max(Comparator.comparing(InstanceLibrary.World::lastPlayed)).orElse(null);
                }
            } catch (Exception e) {
                Log.info("Recently played: " + e.getMessage());
            }
            if (img == null) {
                img = VersionArt.of(dev.lumora.launcher.app.ui.VersionGrid.family(shown.minecraftVersion));
            }
            ProfileCatalog.Profile fp = shown;
            PlayStats.Entry fs = bestStats;
            BufferedImage fi = img;
            InstanceLibrary.World fw = world;
            SwingUtilities.invokeLater(() -> {
                if (gen != recentGeneration) {
                    return;
                }
                recentProfile = fs != null ? fp.id : null;
                cover.image = fi;
                cover.repaint();
                recentTitle.setText("Minecraft " + fp.minecraftVersion);
                if (fs == null) {
                    recentWhen.setText(Lang.t("home.recent.never"));
                    recentTime.setVisible(false);
                    recentWorld.setVisible(false);
                    recentWorldPlay = null;
                    continueButton.setText(Lang.t("play.start_game"));
                    continueButton.setToolTipText(null);
                    return;
                }
                recentWhen.setText(when(Instant.ofEpochMilli(fs.lastPlayed)));
                recentTime.setText(Lang.t("home.recent.played", PlayStats.format(fs.playTime())));
                recentTime.setVisible(true);
                recentWorldPlay = null;
                if (fw != null) {
                    try {
                        recentWorldPlay = new LaunchCommandBuilder.QuickPlay(LaunchCommandBuilder.QuickPlay.Kind.SINGLEPLAYER, fw.folder());
                    } catch (IllegalArgumentException e) {
                        recentWorldPlay = null;
                    }
                }
                recentWorld.setVisible(recentWorldPlay != null);
                if (recentWorldPlay != null) {
                    recentWorld.setText(fw.name());
                }
                continueButton.setText(Lang.t("home.recent.continue"));
                continueButton.setToolTipText(recentWorldPlay != null ? Lang.t("home.recent.continue.world", fw.name())
                        : Lang.t("home.recent.continue.version", fp.minecraftVersion));
            });
        });
    }

    /** "Today, 14:32", "Yesterday, 09:10" or the date. */
    static String when(Instant t) {
        ZonedDateTime z = t.atZone(ZoneId.systemDefault());
        LocalDate today = LocalDate.now();
        if (z.toLocalDate().equals(today)) {
            return Lang.t("home.recent.today", TIME.format(z));
        }
        if (z.toLocalDate().equals(today.minusDays(1))) {
            return Lang.t("home.recent.yesterday", TIME.format(z));
        }
        return DATE_TIME.format(z);
    }

    // ------------------------------------------------------------------ downloads

    /** Game installation running: what is loaded and how far. */
    void download(String detail, double fraction) {
        downloading = true;
        dlTitle.setText(installTitle());
        String pct = fraction >= 0 ? Math.round(fraction * 100) + " %" : "";
        String d = detail == null || detail.isBlank() ? Lang.t("play.installing") : detail;
        dlDetail.setText(pct.isEmpty() ? d : d + "  –  " + pct);
        dlBar.value = fraction;
        dlBar.setVisible(true);
        dlBar.repaint();
        dlUpdates.setVisible(false);
    }

    /** No installation running: whether the selected version is installed, and component updates. */
    void idle() {
        downloading = false;
        String mc = host.ctx().selectedInstance().minecraftVersion;
        dlTitle.setText(installTitle());
        boolean installed = Files.isRegularFile(host.ctx().paths.versions().resolve(mc).resolve(mc + ".jar"));
        dlDetail.setText(installed ? Lang.t("home.downloads.installed") : Lang.t("home.downloads.not_installed"));
        dlDetail.setIcon(installed ? Icons.of(Icons.Kind.CHECK, 14, Theme.OK) : Icons.of(Icons.Kind.DOWNLOAD, 14, Theme.MUTED));
        dlBar.setVisible(false);
        showUpdates();
    }

    /** "Minecraft 26.3" for a built-in instance, "My Pack · Fabric 1.21.4" for an own one. */
    private String installTitle() {
        dev.lumora.launcher.core.instance.Instance i = host.ctx().selectedInstance();
        return i.builtIn ? "Minecraft " + i.minecraftVersion : i.name + " · " + i.kind().label() + " " + i.minecraftVersion;
    }

    /** Result of the background component update check. */
    void updates(int count) {
        updates = count;
        if (!downloading) {
            showUpdates();
        }
    }

    private void showUpdates() {
        dlUpdates.setVisible(updates > 0);
        if (updates > 0) {
            dlUpdates.setText(Lang.plural("home.stat.updates", updates) + "  →");
            dlUpdates.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        }
    }

    boolean downloading() {
        return downloading;
    }

    // ------------------------------------------------------------------ news

    void refreshNews() {
        newsList.removeAll();
        List<NewsFeed.Item> items = news.latest(2);
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                newsList.add(Box.createVerticalStrut(14));
            }
            newsList.add(new NewsRow(items.get(i)));
        }
        if (items.isEmpty()) {
            newsList.add(Ui.label(Lang.t("news.empty"), 13.5f, Font.PLAIN, Theme.MUTED));
        }
        newsList.revalidate();
        newsList.repaint();
    }

    // ------------------------------------------------------------------ widgets

    private static JLabel info(Icons.Kind icon) {
        JLabel l = Ui.label(" ", 14, Font.PLAIN, Theme.mix(Theme.MUTED, Theme.TEXT, 0.35));
        l.setIcon(Icons.of(icon, 16, Theme.MUTED));
        l.setIconTextGap(9);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private static final int ACTION_HEIGHT = 44;

    /** Outlined accent button (like "Continue playing"). */
    private static JButton outlineButton(String text, Icons.Kind icon) {
        JButton b = new JButton(text, Icons.of(icon, 16, Theme.ACCENT_HOVER)) {
            private boolean hover;

            {
                addMouseListener(new MouseAdapter() {
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
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int arc = Theme.arc(12);
                g.setColor(Theme.alpha(Theme.ACCENT, hover ? 70 : 34));
                Theme.fillRound(g, 1, 1, getWidth() - 2, getHeight() - 2, arc, arc);
                g.setColor(hover ? Theme.ACCENT_HOVER : Theme.ACCENT);
                g.setStroke(new java.awt.BasicStroke(2f));
                Theme.drawRound(g, 1, 1, getWidth() - 3, getHeight() - 3, arc, arc);
                g.dispose();
                super.paintComponent(g0);
            }

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(Math.max(200, super.getPreferredSize().width + 36), ACTION_HEIGHT);
            }
        };
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setOpaque(false);
        b.setForeground(Theme.TEXT);
        b.setFont(Theme.font(Font.BOLD, 15f));
        b.setIconTextGap(12);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private static JButton squareButton(Icons.Kind icon, String tip) {
        JButton b = new JButton(Icons.of(icon, 20, Theme.TEXT));
        b.setToolTipText(tip);
        b.setFocusable(false);
        b.setPreferredSize(new Dimension(ACTION_HEIGHT, ACTION_HEIGHT));
        b.setBackground(Theme.alpha(Theme.SURFACE_2, 230));
        b.putClientProperty("JButton.arc", Theme.arc(12));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private static void quality(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }

    /** Draws {@code img} scaled to cover the rectangle (centre crop). */
    private static void cover(Graphics2D g, Image img, int x, int y, int w, int h) {
        int iw = img.getWidth(null);
        int ih = img.getHeight(null);
        if (iw <= 0 || ih <= 0) {
            return;
        }
        double s = Math.max((double) w / iw, (double) h / ih);
        int dw = (int) Math.ceil(iw * s);
        int dh = (int) Math.ceil(ih * s);
        g.drawImage(img, x + (w - dw) / 2, y + (h - dh) / 2, dw, dh, null);
    }

    /** Rounded picture of the recently played card. */
    private static final class Cover extends JComponent {
        BufferedImage image;

        /** 260 x 146, narrower when the card is narrow (the text and buttons next to it keep their room). */
        @Override
        public Dimension getPreferredSize() {
            int avail = getParent() == null ? 0 : getParent().getWidth();
            int w = avail <= 0 ? 260 : Math.max(150, Math.min(260, avail - 300));
            return new Dimension(w, 146);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            quality(g);
            int w = getWidth();
            int h = getHeight();
            java.awt.Shape clip0 = g.getClip();
            g.clip(Theme.roundShape(0, 0, w, h, Theme.arc(14), Theme.arc(14)));
            g.setPaint(new GradientPaint(0, 0, Theme.SURFACE_2, w, h, Theme.BG));
            g.fillRect(0, 0, w, h);
            if (image != null) {
                cover(g, image, 0, 0, w, h);
            }
            g.setClip(clip0);
            g.setColor(Theme.alpha(Theme.BORDER, 200));
            Theme.drawRound(g, 0, 0, w - 1, h - 1, Theme.arc(14), Theme.arc(14));
            g.dispose();
        }
    }

    /** Progress bar of the downloads card (accent, rounded). */
    private static final class Bar extends JComponent {
        double value;

        Bar() {
            setPreferredSize(new Dimension(300, 8));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 8));
            setVisible(false);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            g.setColor(Theme.alpha(Theme.TEXT, 22));
            Theme.fillRound(g, 0, 0, w, h, h, h);
            if (value >= 0) {
                int fw = (int) Math.round(w * Math.min(1, value));
                if (fw > 0) {
                    g.setPaint(new GradientPaint(0, 0, Theme.ACCENT, w, 0, Theme.ACCENT_HOVER));
                    Theme.fillRound(g, 0, 0, Math.max(h, fw), h, h, h);
                }
            } else {
                // unknown progress: a moving segment
                double t = (System.nanoTime() / 1e9) % 1.6 / 1.6;
                int sw = w / 4;
                g.setColor(Theme.ACCENT);
                Theme.fillRound(g, (int) ((w + sw) * t) - sw, 0, sw, h, h, h);
                repaint(80);
            }
            g.dispose();
        }
    }

    /** Small voxel grass block (own drawing): green top, earth sides. */
    private static final class GrassBlock extends JComponent {
        private final int size;

        GrassBlock(int size) {
            this.size = size;
            setPreferredSize(new Dimension(size, size));
            setMinimumSize(new Dimension(size, size));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            double s = size;
            g.translate((getWidth() - size) / 2, (getHeight() - size) / 2);
            g.setColor(new Color(0x5FA83A));
            g.fill(shape(s, 0.5, 0.04, 0.95, 0.27, 0.5, 0.50, 0.05, 0.27));
            g.setColor(new Color(0x7A5230));
            g.fill(shape(s, 0.05, 0.27, 0.5, 0.50, 0.5, 0.96, 0.05, 0.73));
            g.setColor(new Color(0x5E3E24));
            g.fill(shape(s, 0.95, 0.27, 0.5, 0.50, 0.5, 0.96, 0.95, 0.73));
            // grass edge on the sides
            g.setColor(new Color(0x4C8C2C));
            g.fill(shape(s, 0.05, 0.27, 0.5, 0.50, 0.5, 0.60, 0.05, 0.37));
            g.setColor(new Color(0x3E7524));
            g.fill(shape(s, 0.95, 0.27, 0.5, 0.50, 0.5, 0.60, 0.95, 0.37));
            g.dispose();
        }

        private static java.awt.geom.Path2D shape(double s, double... xy) {
            java.awt.geom.Path2D p = new java.awt.geom.Path2D.Double();
            p.moveTo(s * xy[0], s * xy[1]);
            for (int i = 2; i < xy.length; i += 2) {
                p.lineTo(s * xy[i], s * xy[i + 1]);
            }
            p.closePath();
            return p;
        }
    }

    /** One news entry: picture, source chip, title, short text and date; opens the article. */
    private final class NewsRow extends JComponent {
        private final NewsFeed.Item it;
        private boolean hover;

        NewsRow(NewsFeed.Item it) {
            this.it = it;
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setPreferredSize(new Dimension(380, 128));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 128));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText(it.title());
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    news.openItem(it);
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
            int arc = Theme.arc(14);
            g.setColor(Theme.alpha(Theme.TEXT, hover ? 14 : 6));
            Theme.fillRound(g, 0, 0, w, h, arc, arc);
            // picture
            int pw = Math.min(170, w * 2 / 5);
            int ph = h - 16;
            java.awt.Shape clip0 = g.getClip();
            g.clip(Theme.roundShape(8, 8, pw, ph, Theme.arc(12), Theme.arc(12)));
            Image img = news.picture(it.image(), this::repaint);
            if (img != null) {
                cover(g, img, 8, 8, pw, ph);
            } else {
                Color a = it.source() == NewsFeed.Source.LUMORA ? Theme.ACCENT_DEEP : new Color(0x1E3A23);
                g.setPaint(new GradientPaint(8, 8, a, 8 + pw, 8 + ph, Theme.SURFACE_2));
                g.fillRect(8, 8, pw, ph);
            }
            g.setClip(clip0);
            // chip
            int x = pw + 24;
            int y = 12;
            String chip = NewsPage.sourceLabel(it.source());
            g.setFont(Theme.font(Font.BOLD, 12f));
            FontMetrics cm = g.getFontMetrics();
            int cw = cm.stringWidth(chip) + 18;
            g.setColor(it.source() == NewsFeed.Source.LUMORA ? Theme.ACCENT : new Color(0x3F7A3A));
            Theme.fillRound(g, x, y, cw, 22, Theme.arc(8), Theme.arc(8));
            g.setColor(Theme.onAccent());
            g.drawString(chip, x + 9, y + 15);
            // title and text
            int max = w - x - 12;
            g.setFont(Theme.font(Font.BOLD, 15.5f));
            g.setColor(Theme.TEXT);
            FontMetrics tm = g.getFontMetrics();
            g.drawString(fit(tm, it.title(), max), x, y + 42);
            g.setFont(Theme.font(Font.PLAIN, 13f));
            g.setColor(Theme.mix(Theme.MUTED, Theme.TEXT, 0.3));
            FontMetrics bm = g.getFontMetrics();
            List<String> lines = wrap(bm, it.text() == null ? "" : it.text(), max, 2);
            int ly = y + 62;
            for (String l : lines) {
                g.drawString(l, x, ly);
                ly += 18;
            }
            if (it.date() != null) {
                Icons.of(Icons.Kind.CALENDAR, 14, Theme.MUTED).paintIcon(this, g, x, h - 26);
                g.setFont(Theme.font(Font.PLAIN, 12.5f));
                g.setColor(Theme.MUTED);
                g.drawString(DATE.format(it.date()), x + 21, h - 14);
            }
            g.dispose();
        }
    }

    private static String fit(FontMetrics fm, String text, int max) {
        if (text == null) {
            return "";
        }
        if (fm.stringWidth(text) <= max) {
            return text;
        }
        String t = text;
        while (t.length() > 1 && fm.stringWidth(t + "…") > max) {
            t = t.substring(0, t.length() - 1);
        }
        return t + "…";
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
                if (out.size() == maxLines) {
                    break;
                }
            }
        }
        if (out.size() < maxLines && !line.isEmpty()) {
            out.add(line.toString());
        }
        if (out.size() == maxLines) {
            String joined = String.join(" ", out);
            if (joined.length() < text.trim().length()) {
                out.set(maxLines - 1, fit(fm, out.get(maxLines - 1) + " …", max));
            }
        }
        return out;
    }
}
