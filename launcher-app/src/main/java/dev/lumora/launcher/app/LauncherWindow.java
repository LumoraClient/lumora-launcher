package dev.lumora.launcher.app;

import dev.lumora.launcher.app.ui.Avatar;
import dev.lumora.launcher.app.ui.Card;
import dev.lumora.launcher.app.ui.HeroPanel;
import dev.lumora.launcher.app.ui.Icons;
import dev.lumora.launcher.app.pages.AccountPage;
import dev.lumora.launcher.app.pages.BrowserPage;
import dev.lumora.launcher.app.pages.CrashPage;
import dev.lumora.launcher.app.pages.LibraryPage;
import dev.lumora.launcher.app.pages.PageHost;
import dev.lumora.launcher.app.ui.NavRail;
import dev.lumora.launcher.app.ui.PlayButton;
import dev.lumora.launcher.app.ui.Theme;
import dev.lumora.launcher.app.ui.ToggleSwitch;
import dev.lumora.launcher.app.ui.Tray;
import dev.lumora.launcher.app.ui.VersionGrid;
import dev.lumora.launcher.app.pages.NewsPage;
import dev.lumora.launcher.app.pages.ServersPage;
import dev.lumora.launcher.app.pages.SettingsPage;
import dev.lumora.launcher.app.ui.WrapText;
import dev.lumora.launcher.core.BuildInfo;
import dev.lumora.launcher.core.LauncherPaths;
import dev.lumora.launcher.core.PlayService;
import dev.lumora.launcher.core.auth.AuthException;
import dev.lumora.launcher.core.auth.MinecraftSession;
import dev.lumora.launcher.core.config.LauncherConfig;
import dev.lumora.launcher.core.diagnostics.CrashAnalyzer;
import dev.lumora.launcher.core.diagnostics.SystemCheck;
import dev.lumora.launcher.core.launch.GameProcess;
import dev.lumora.launcher.core.profile.ProfileCatalog;
import dev.lumora.launcher.core.crash.CrashReport;
import dev.lumora.launcher.core.crash.CrashReports;
import dev.lumora.launcher.core.launch.LaunchCommandBuilder;
import dev.lumora.launcher.core.stats.PlayStats;
import dev.lumora.launcher.core.update.UpdateChecker;
import dev.lumora.launcher.core.util.Log;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Main launcher window. Layout ideas taken from popular launchers: navigation rail (Modrinth App), hero banner
 * (Lunar Client), dashboard tiles and toggle cards (Feather), bottom play bar with profile picker and account
 * (official Minecraft Launcher), plain diagnostics/log views (Prism). Artwork is generated, no foreign assets.
 */
final class LauncherWindow implements PageHost {
    private final AppContext ctx;
    private final JFrame frame = new JFrame(BuildInfo.NAME);
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "launcher-worker");
        t.setDaemon(true);
        return t;
    });
    /** Background reads of the pages (mod lists, worlds, server pings, thumbnails). */
    private final ExecutorService pageIo = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "launcher-pages");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean busy = new AtomicBoolean();
    private LibraryPage libraryPage;
    private CrashPage crashPage;
    private final dev.lumora.launcher.app.social.SocialController social;
    private dev.lumora.launcher.app.social.SocialPage socialPage;
    private dev.lumora.launcher.app.pages.NewsPage newsPage;
    private dev.lumora.launcher.app.pages.ServersPage serversPage;
    private final Tray tray = new Tray(frame, icons(), this::quit);
    private boolean startedInTray;
    private boolean trayHintShown;
    private BrowserPage browserPage;
    private AccountPage accountPage;
    private dev.lumora.launcher.app.pages.PerformancePage performancePage;
    private final dev.lumora.launcher.app.ui.PageFade pageFade = new dev.lumora.launcher.app.ui.PageFade();
    private SettingsPage settingsPage;
    /** Play controls of the bottom bar; hidden on the start page, which has its own in the hero. */
    private JPanel barRight;
    private PlayButton heroPlay;
    private PlayButton.Arrow heroArrow;
    private final dev.lumora.launcher.core.integrations.DiscordPresence discord = new dev.lumora.launcher.core.integrations.DiscordPresence();
    private final long launcherStarted = java.time.Instant.now().getEpochSecond();
    private long gameStarted;

    private final CardLayout pages = new CardLayout();
    private final JPanel pageHost = new JPanel(pages);
    private NavRail nav;

    private PlayButton playButton;
    private JButton stopButton;
    private JProgressBar progress;
    private JLabel statusLabel;
    private JLabel accountName;
    private JLabel accountHint;
    private Avatar avatar;
    private JComboBox<ProfileCatalog.Profile> profileBox;
    /** "3 Std. gespielt" per profile, shown in the version menu. */
    private Map<String, String> playInfo = Map.of();
    private boolean syncingProfile;

    private JLabel statGpu;
    private JLabel statVersion;
    private JLabel heroOverline;
    private JPanel heroChips;
    private JLabel statRam;
    private JLabel statExtensions;
    private JLabel statUpdates;

    private final JTextArea logArea = new JTextArea();
    private final JPanel diagList = new JPanel();
    private volatile LoginFlow currentLogin;

    LauncherWindow(AppContext ctx) {
        this.ctx = ctx;
        this.social = new dev.lumora.launcher.app.social.SocialController(ctx);
        installLookAndFeel();
        build();
    }

    /** Must run before any Swing component is created (called from Main). */
    static void installLookAndFeel() {
        Theme.install();
    }

    void show() {
        show(false);
    }

    /** @param hidden started with --tray (autostart): only the tray icon, the window opens from there */
    void show(boolean hidden) {
        startedInTray = hidden && Tray.supported();
        updateTray();
        if (!startedInTray) {
            frame.setVisible(true);
        }
        if (!ctx.config.startPage.equals("home")) {
            showPage(ctx.config.startPage);
        }
        social.apply();
        updateDiscord();
        updateNewsBadge();
        if (ctx.configLoad.recoveredFromCorruption()) {
            JOptionPane.showMessageDialog(frame, "Die Einstellungsdatei war beschädigt und wurde auf Standardwerte zurückgesetzt.\n"
                    + "Sicherung: " + ctx.configLoad.backup(), BuildInfo.NAME, JOptionPane.WARNING_MESSAGE);
        }
        runSystemCheck();
        if (ctx.config.checkForUpdates) {
            worker.submit(this::backgroundUpdateCheck);
            checkLauncherUpdate(false);
        }
    }

    /** Documentation screenshot of the whole window (developer option --ui-shot; Swing thread). */
    void capture(java.nio.file.Path file) throws java.io.IOException {
        java.awt.Container root = frame.getRootPane();
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(root.getWidth(), root.getHeight(), java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        root.paint(g);
        g.dispose();
        java.nio.file.Files.createDirectories(file.getParent());
        javax.imageio.ImageIO.write(img, "png", file.toFile());
    }

    JFrame window() {
        return frame;
    }

    /** Used by documentation screenshots; "account/security" or "settings/Spiel" also open a tab or category. */
    @Override
    public void showPage(String id) {
        int slash = id.indexOf('/');
        nav.select(slash < 0 ? id : id.substring(0, slash));
        if (slash > 0 && id.startsWith("account/")) {
            accountPage.selectTab(id.substring(slash + 1));
        } else if (slash > 0 && id.startsWith("settings/")) {
            settingsPage.showCategory(id.substring(slash + 1));
        }
    }

    private void build() {
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                onClose();
            }

            @Override
            public void windowOpened(WindowEvent e) {
                applyTaskbarPin();
            }
        });
        frame.setIconImages(icons());
        frame.setMinimumSize(new Dimension(1180, 720));
        frame.getRootPane().putClientProperty("JRootPane.titleBarBackground", Theme.BG);
        frame.getRootPane().putClientProperty("JRootPane.titleBarForeground", Theme.MUTED);

        pageHost.setOpaque(false);
        pageHost.add(scroll((JComponent) homePage()), "home");
        libraryPage = new LibraryPage(this, pageIo);
        pageHost.add(libraryPage, "library");
        accountPage = new AccountPage(this, pageIo);
        pageHost.add(accountPage, "account");
        browserPage = new BrowserPage(this, pageIo);
        pageHost.add(browserPage, "browser");
        crashPage = new CrashPage(this, pageIo);
        pageHost.add(crashPage, "crashes");
        socialPage = new dev.lumora.launcher.app.social.SocialPage(this, social);
        pageHost.add(socialPage, "social");
        newsPage = new NewsPage(this, pageIo);
        pageHost.add(newsPage, "news");
        serversPage = new ServersPage(this);
        pageHost.add(serversPage, "servers");
        performancePage = new dev.lumora.launcher.app.pages.PerformancePage(this, pageIo);
        pageHost.add(performancePage, "performance");
        settingsPage = new SettingsPage(this, new SettingsPage.Hooks() {
            @Override
            public void socialChanged() {
                social.apply();
                socialPage.refresh();
                updateSocialBadge();
            }

            @Override
            public void discordChanged() {
                updateDiscord();
            }

            @Override
            public void checkLauncherUpdate() {
                LauncherWindow.this.checkLauncherUpdate(true);
            }

            @Override
            public void trayChanged() {
                updateTray();
            }

            @Override
            public void memoryChanged() {
                statRam.setText(String.format("%.1f GB für das Spiel", ctx.config.maxMemoryMb / 1024.0));
            }

            @Override
            public void navChanged() {
                nav.setIconsOnly(ctx.config.navIconsOnly);
            }

            @Override
            public boolean clientIdHelp() {
                return LoginFlow.showClientIdSetup(frame, ctx);
            }
        });
        pageHost.add(settingsPage, "settings");
        // reachable from Einstellungen → Fehlersuche (no own navigation item)
        pageHost.add(diagnosticsPage(), "diagnostics");
        pageHost.add(logsPage(), "logs");

        List<Image> ic = icons();
        nav = new NavRail(ic.isEmpty() ? null : ic.get(ic.size() - 1), id -> {
            pages.show(pageHost, id);
            pageFade.play();
            socialPage.setShown(id.equals("social"));
            serversPage.setShown(id.equals("servers"));
            if (barRight != null) {
                barRight.setVisible(!id.equals("home"));
            }
            if (id.equals("news")) {
                newsPage.refresh();
                markNewsSeen();
            } else if (id.equals("performance")) {
                performancePage.refresh();
            } else if (id.equals("library")) {
                libraryPage.refresh();
            } else if (id.equals("account")) {
                accountPage.refresh();
            } else if (id.equals("browser")) {
                browserPage.refresh();
            } else if (id.equals("crashes")) {
                crashPage.refresh();
            }
        });
        nav.addItem("home", "Startseite", Icons.Kind.HOME);
        nav.addItem("browser", "Entdecken", Icons.Kind.SEARCH);
        nav.addItem("performance", "Performance", Icons.Kind.CHART);
        nav.addItem("library", "Bibliothek", Icons.Kind.LIBRARY);
        nav.addItem("account", "Konto", Icons.Kind.USER);
        nav.addItem("settings", "Einstellungen", Icons.Kind.GEAR);
        nav.addSeparator();
        nav.addItem("social", "Freunde", Icons.Kind.USERS);
        nav.addItem("servers", "Server", Icons.Kind.GLOBE);
        nav.addItem("news", "News", Icons.Kind.NEWS);
        nav.addGlue();
        nav.addItem("crashes", "Abstürze", Icons.Kind.ALERT);
        nav.addModeToggle(on -> {
            ctx.config.navIconsOnly = on;
            saveConfigQuietly();
            settingsPage.syncNav();
        });
        nav.setIconsOnly(ctx.config.navIconsOnly);
        social.addListener(e -> {
            updateSocialBadge();
            notifySocial(e);
        });
        frame.addWindowFocusListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowGainedFocus(java.awt.event.WindowEvent e) {
                socialPage.windowActive(true);
            }

            @Override
            public void windowLostFocus(java.awt.event.WindowEvent e) {
                socialPage.windowActive(false);
            }
        });

        JPanel main = new JPanel(new BorderLayout());
        main.setOpaque(false);
        JPanel padded = new JPanel(new BorderLayout());
        padded.setOpaque(false);
        padded.setBorder(BorderFactory.createEmptyBorder(20, 26, 16, 26));
        pageHost.setOpaque(false);
        padded.add(pageFade.wrap(pageHost), BorderLayout.CENTER);
        main.add(padded, BorderLayout.CENTER);
        main.add(playBar(), BorderLayout.SOUTH);

        JPanel root = new dev.lumora.launcher.app.ui.Backdrop();
        root.add(nav, BorderLayout.WEST);
        root.add(main, BorderLayout.CENTER);
        frame.setContentPane(root);
        frame.setSize(1320, 840);
        frame.setLocationRelativeTo(null);

        Log.addListener(line -> SwingUtilities.invokeLater(() -> appendLog(line)));
        refreshAccount();
        selectProfile(ctx.selectedProfile().id);
        refreshPlayTimes();
    }

    // ================================================================ Home

    private Component homePage() {
        JPanel page = column();

        page.add(hero());

        page.add(Box.createVerticalStrut(22));
        page.add(sectionTitle("System", null));
        JPanel stats = new JPanel(new GridBagLayout());
        stats.setOpaque(false);
        statGpu = new JLabel("…");
        statVersion = new JLabel("…");
        statRam = new JLabel("…");
        statExtensions = new JLabel("…");
        statUpdates = new JLabel(" ");
        GridBagConstraints sc = new GridBagConstraints();
        sc.fill = GridBagConstraints.BOTH;
        sc.weighty = 1;
        Object[][] tiles4 = {
                {statTile(Icons.Kind.MONITOR, "Grafikkarte", statGpu, null), 1.6},
                {statTile(Icons.Kind.CHIP, "Minecraft", statVersion, null), 1.0},
                {statTile(Icons.Kind.MEMORY, "Arbeitsspeicher", statRam, null), 1.15},
                {statTile(Icons.Kind.PUZZLE, "Erweiterungen", statExtensions, statUpdates), 1.15}};
        for (int i = 0; i < tiles4.length; i++) {
            sc.gridx = i;
            sc.weightx = (Double) tiles4[i][1];
            sc.insets = new Insets(0, i == 0 ? 0 : 14, 0, 0);
            stats.add((Component) tiles4[i][0], sc);
        }
        stats.setAlignmentX(Component.LEFT_ALIGNMENT);
        stats.setMaximumSize(new Dimension(Integer.MAX_VALUE, 96));
        page.add(stats);
        page.add(Box.createVerticalGlue());
        updateExtensionStat();
        return page;
    }

    /**
     * Start page banner: logo, wordmark and slogan and the start controls (version, split play
     * button with more start options, game folder, settings, more).
     */
    private JComponent hero() {
        HeroPanel hero = new HeroPanel(null);
        hero.setLayout(new BoxLayout(hero, BoxLayout.Y_AXIS));
        hero.setBorder(BorderFactory.createEmptyBorder(26, 24, 22, 24));
        hero.setAlignmentX(Component.LEFT_ALIGNMENT);
        List<Image> ic = icons();
        Image logoImg = ic.isEmpty() ? null : ic.getLast();
        JComponent logo = new JComponent() {
            @Override
            protected void paintComponent(java.awt.Graphics g0) {
                java.awt.Graphics2D g = (java.awt.Graphics2D) g0.create();
                g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                int s = Math.min(getWidth(), getHeight());
                int x = (getWidth() - s) / 2;
                g.setPaint(new java.awt.RadialGradientPaint(new java.awt.geom.Point2D.Float(getWidth() / 2f, s / 2f), s * 0.75f,
                        new float[]{0f, 1f}, new Color[]{Theme.alpha(Theme.ACCENT_HOVER, 55), Theme.alpha(Theme.ACCENT, 0)}));
                g.fillOval(x - s / 2, -s / 2, s * 2, s * 2);
                if (logoImg != null) {
                    g.drawImage(logoImg, x, 0, s, s, null);
                }
                g.dispose();
            }
        };
        logo.setPreferredSize(new Dimension(104, 104));
        logo.setMaximumSize(new Dimension(104, 104));
        JComponent wordmark = new JComponent() {
            @Override
            protected void paintComponent(java.awt.Graphics g0) {
                java.awt.Graphics2D g = (java.awt.Graphics2D) g0.create();
                g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setFont(Theme.font(Font.BOLD | Font.ITALIC, 58f));
                java.awt.FontMetrics fm = g.getFontMetrics();
                String t = "LUMORA";
                int x = (getWidth() - fm.stringWidth(t)) / 2;
                int y = fm.getAscent() - 4;
                g.setColor(Theme.alpha(Color.BLACK, 120));
                g.drawString(t, x + 3, y + 4);
                g.setPaint(new java.awt.GradientPaint(x, 0, Color.WHITE, x + fm.stringWidth(t), 0, Theme.ACCENT_HOVER));
                g.drawString(t, x, y);
                g.dispose();
            }
        };
        wordmark.setPreferredSize(new Dimension(420, 66));
        wordmark.setMaximumSize(new Dimension(Integer.MAX_VALUE, 66));
        JLabel slogan = label("Dein All-in-One Minecraft Client", 18, Font.PLAIN, new Color(0xDCE7FA));
        slogan.setHorizontalAlignment(SwingConstants.CENTER);
        for (JComponent c : List.of(logo, wordmark, slogan)) {
            c.setAlignmentX(Component.CENTER_ALIGNMENT);
            hero.add(c);
        }
        hero.add(Box.createVerticalStrut(20));

        hero.add(Box.createVerticalStrut(6));

        // start controls
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        controls.setOpaque(false);
        JPanel split = new JPanel(new BorderLayout(0, 0));
        split.setOpaque(false);
        heroPlay = new PlayButton("Spiel starten").split();
        heroPlay.setPreferredSize(new Dimension(290, 56));
        heroPlay.addActionListener(e -> startPlay(false));
        heroArrow = new PlayButton.Arrow();
        heroArrow.setToolTipText("Version und Startart wählen");
        heroArrow.addActionListener(e -> showVersionMenu());
        split.add(heroPlay, BorderLayout.CENTER);
        split.add(heroArrow, BorderLayout.EAST);
        controls.add(split);
        controls.add(heroButton(Icons.Kind.FOLDER, "Spielordner öffnen",
                () -> open(ctx.paths.instance(currentFlavor().instanceId(ctx.selectedProfile().id)))));
        controls.add(heroButton(Icons.Kind.GEAR, "Einstellungen", () -> showPage("settings")));
        JButton[] more = new JButton[1];
        more[0] = heroButton(Icons.Kind.MORE, "Mehr", () -> moreMenu().show(more[0], 0, more[0].getHeight()));
        controls.add(more[0]);
        controls.setAlignmentX(Component.CENTER_ALIGNMENT);
        controls.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
        hero.add(controls);
        hero.add(Box.createVerticalStrut(14));

        JPanel info = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        info.setOpaque(false);
        heroOverline = label(" ", 12, Font.BOLD, Theme.ACCENT_TEXT);
        info.add(heroOverline);
        heroChips = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
        heroChips.setOpaque(false);
        info.add(heroChips);
        info.setAlignmentX(Component.CENTER_ALIGNMENT);
        info.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        hero.add(info);
        hero.setMaximumSize(new Dimension(Integer.MAX_VALUE, hero.getPreferredSize().height));
        return hero;
    }

    private JButton heroButton(Icons.Kind icon, String tip, Runnable action) {
        JButton b = new JButton(Icons.of(icon, 20, Theme.TEXT));
        b.setToolTipText(tip);
        b.setFocusable(false);
        b.setPreferredSize(new Dimension(52, 52));
        b.setBackground(Theme.alpha(Theme.SURFACE_2, 230));
        b.addActionListener(e -> action.run());
        return b;
    }

    /**
     * Version menu of the arrow next to "Spiel starten": start type (Lumora Client / Vanilla), every version
     * grouped by major version (newest first) and "Dateien prüfen und starten".
     */
    private void showVersionMenu() {
        javax.swing.JPopupMenu popup = new javax.swing.JPopupMenu();
        popup.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        popup.add(versionMenuBody(() -> popup.setVisible(false)));
        Dimension size = popup.getPreferredSize();
        int w = Math.max(size.width, 520);
        popup.setPopupSize(w, size.height);
        // right edge under the arrow
        popup.show(heroArrow, heroArrow.getWidth() - w, heroArrow.getHeight() + 6);
    }

    /** Content of the version menu; {@code close} hides the menu. */
    JPanel versionMenuBody(Runnable close) {
        JPanel body = column();
        body.setOpaque(true);
        body.setBackground(Theme.SURFACE);
        body.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));

        JLabel title = label("Version wählen", 15, Font.BOLD, Theme.TEXT);
        body.add(title);
        body.add(Box.createVerticalStrut(2));
        body.add(label(ctx.catalog.profiles().size() + " Versionen · jede mit eigenem Spielordner", 12, Font.PLAIN, Theme.MUTED));
        body.add(Box.createVerticalStrut(12));

        // start type: Lumora or plain Minecraft (selectable mod loaders are removed for now)
        JPanel flavors = new JPanel(new GridLayout(1, 2, 6, 0));
        flavors.setOpaque(false);
        flavors.setAlignmentX(Component.LEFT_ALIGNMENT);
        javax.swing.ButtonGroup fg = new javax.swing.ButtonGroup();
        for (dev.lumora.launcher.core.profile.Flavor fl : dev.lumora.launcher.core.profile.Flavor.values()) {
            Icons.Kind kind = switch (fl) {
                case LUMORA -> Icons.Kind.R_STAR;
                case VANILLA -> Icons.Kind.CUBE;
            };
            javax.swing.JToggleButton b = new javax.swing.JToggleButton(fl.label(), Icons.of(kind, 14, Theme.TEXT), fl == currentFlavor());
            b.setFont(Theme.font(Font.BOLD, 12.5f));
            b.setFocusable(false);
            b.setIconTextGap(7);
            b.setToolTipText(switch (fl) {
                case LUMORA -> "Lumora-Oberfläche, Performance-Mods und Addons";
                case VANILLA -> "Reines Minecraft ohne Mods, eigener Spielordner";
            });
            b.addActionListener(e -> setFlavor(fl));
            fg.add(b);
            flavors.add(b);
        }
        flavors.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        body.add(flavors);
        body.add(Box.createVerticalStrut(12));

        // versions by major version: the same cards as the old version grid (pixel art or newest screenshot)
        List<VersionGrid.Version> list = new ArrayList<>();
        for (ProfileCatalog.Profile p : ctx.catalog.profiles()) {
            boolean recommended = p.id.equals(ctx.catalog.defaultProfile());
            String played = playInfo.get(p.id);
            list.add(new VersionGrid.Version(p.id, p.minecraftVersion, "<html><b>Minecraft " + p.minecraftVersion + "</b>"
                    + (recommended ? "  ·  Empfohlene Version" : "") + "<br>Lumora Loader"
                    + (played == null ? "" : "<br>" + played) + "</html>"));
        }
        VersionGrid grid = new VersionGrid(list, id -> ctx.paths.instance(id).resolve("screenshots"), id -> {
            selectProfile(id);
            close.run();
        }, 104, 8);
        grid.setSelected(ctx.selectedProfile().id);
        grid.setPlayInfo(playInfo);
        grid.setPreferredSize(new Dimension(3 * 228 + 2 * 8, grid.getMaximumSize().height));
        body.add(grid);

        body.add(Box.createVerticalStrut(12));
        JButton repair = new JButton("Dateien prüfen und starten", Icons.of(Icons.Kind.CHECK, 14, Theme.TEXT));
        repair.setFocusable(false);
        repair.setAlignmentX(Component.LEFT_ALIGNMENT);
        repair.addActionListener(e -> {
            close.run();
            startPlay(true);
        });
        body.add(repair);
        return body;
    }

    private javax.swing.JPopupMenu moreMenu() {
        javax.swing.JPopupMenu m = new javax.swing.JPopupMenu();
        Object[][] items = {
                {"Logs", Icons.Kind.TERMINAL, "logs"},
                {"Diagnose", Icons.Kind.PULSE, "diagnostics"},
                {"Abstürze", Icons.Kind.ALERT, "crashes"},
                {"Performance", Icons.Kind.CHART, "performance"}};
        for (Object[] it : items) {
            javax.swing.JMenuItem mi = new javax.swing.JMenuItem((String) it[0], Icons.of((Icons.Kind) it[1], 14, Theme.TEXT));
            mi.addActionListener(e -> showPage((String) it[2]));
            m.add(mi);
        }
        m.addSeparator();
        javax.swing.JMenuItem upd = new javax.swing.JMenuItem("Nach Updates suchen", Icons.of(Icons.Kind.REFRESH, 14, Theme.TEXT));
        upd.addActionListener(e -> checkUpdates());
        m.add(upd);
        return m;
    }

    private dev.lumora.launcher.core.profile.Flavor currentFlavor() {
        return dev.lumora.launcher.core.profile.Flavor.of(ctx.config.selectedFlavor);
    }

    private void setFlavor(dev.lumora.launcher.core.profile.Flavor fl) {
        if (fl != currentFlavor()) {
            ctx.config.selectedFlavor = fl.id();
            saveConfigQuietly();
        }
        refreshVersionInfo(ctx.selectedProfile());
    }

    private void updateSocialBadge() {
        dev.lumora.social.SocialClient c = social.client();
        int unread = c == null || !ctx.config.unreadBadge ? 0 : c.unreadTotal() + c.incomingRequests();
        if (unread > 0) {
            nav.setBadge("social", unread);
        } else {
            long online = c == null ? 0 : c.friends().stream().filter(f -> f.presence.online()).count();
            nav.setBadge("social", (int) online, true);
        }
    }

    /** Lumora news the user has not opened yet. */
    private void updateNewsBadge() {
        pageIo.submit(() -> {
            try {
                List<String> ids = dev.lumora.launcher.core.news.NewsFeed.lumoraNews().stream().map(dev.lumora.launcher.core.news.NewsFeed.Item::id).toList();
                long unseen = ids.stream().filter(id -> !ctx.config.seenNews.contains(id)).count();
                SwingUtilities.invokeLater(() -> nav.setBadge("news", (int) unseen));
            } catch (RuntimeException e) {
                Log.warn("News badge: " + e.getMessage());
            }
        });
    }

    private void markNewsSeen() {
        pageIo.submit(() -> {
            try {
                List<String> ids = dev.lumora.launcher.core.news.NewsFeed.lumoraNews().stream().map(dev.lumora.launcher.core.news.NewsFeed.Item::id).toList();
                SwingUtilities.invokeLater(() -> {
                    if (!ctx.config.seenNews.containsAll(ids)) {
                        ctx.config.seenNews = new ArrayList<>(ids);
                        saveConfigQuietly();
                    }
                    nav.setBadge("news", 0);
                });
            } catch (RuntimeException e) {
                Log.warn("News seen: " + e.getMessage());
            }
        });
    }

    /** Discord shows "Im Launcher" or the running version (only with Rich Presence on and an application ID). */
    private void updateDiscord() {
        dev.lumora.launcher.core.integrations.DiscordPresence.Activity a;
        if (ctx.play.runningGame().isPresent()) {
            ProfileCatalog.Profile p = ctx.selectedProfile();
            a = new dev.lumora.launcher.core.integrations.DiscordPresence.Activity("Spielt Minecraft " + p.minecraftVersion,
                    currentFlavor().label(), gameStarted);
        } else {
            a = new dev.lumora.launcher.core.integrations.DiscordPresence.Activity("Im Launcher", "Lumora " + BuildInfo.VERSION, launcherStarted);
        }
        discord.update(ctx.config, a);
    }

    /** True if the profile ships the Lumora client mod (Lumora menus, HUD editor, addons). */
    private boolean hasLumoraUi(ProfileCatalog.Profile p) {
        return ctx.catalog.profileArtifact(p, "lumora-client").isPresent();
    }

    private void refreshVersionInfo(ProfileCatalog.Profile p) {
        dev.lumora.launcher.core.profile.Flavor fl = currentFlavor();
        if (heroOverline != null) {
            heroOverline.setText("MINECRAFT " + p.minecraftVersion + "  ·  " + switch (fl) {
                case LUMORA -> "LUMORA CLIENT";
                case VANILLA -> "VANILLA";
            });
        }
        if (heroChips != null) {
            heroChips.removeAll();
            if (fl == dev.lumora.launcher.core.profile.Flavor.LUMORA) {
                heroChips.add(chip("Lumora Loader"));
                if (hasLumoraUi(p)) {
                    heroChips.add(chip("Lumora-UI"));
                }
            } else {
                heroChips.add(chip("eigener Spielordner"));
            }
            heroChips.revalidate();
            heroChips.repaint();
        }
        if (statVersion != null) {
            statVersion.setText(p.minecraftVersion);
        }
    }

    @Override
    public void selectProfile(String id) {
        if (syncingProfile) {
            return;
        }
        syncingProfile = true;
        try {
            if (!id.equals(ctx.config.selectedProfile)) {
                ctx.config.selectedProfile = id;
                saveConfigQuietly();
            }
            ctx.catalog.profile(id).ifPresent(this::refreshVersionInfo);
            if (profileBox != null) {
                ctx.catalog.profile(id).ifPresent(profileBox::setSelectedItem);
            }
        } finally {
            syncingProfile = false;
        }
    }

    private Card statTile(Icons.Kind icon, String title, JLabel value, JLabel sub) {
        Card c = new Card(new BorderLayout(12, 0));
        c.padding(14, 16, 14, 16);
        JLabel i = new JLabel(Icons.of(icon, 22, Theme.ACCENT_HOVER));
        i.setVerticalAlignment(SwingConstants.TOP);
        c.add(i, BorderLayout.WEST);
        JPanel t = column();
        JLabel tl = label(title.toUpperCase(), 11, Font.BOLD, Theme.MUTED);
        tl.setAlignmentX(Component.LEFT_ALIGNMENT);
        t.add(tl);
        t.add(Box.createVerticalStrut(4));
        value.setFont(Theme.font(Font.BOLD, 15f));
        value.setForeground(Theme.TEXT);
        value.setAlignmentX(Component.LEFT_ALIGNMENT);
        t.add(value);
        if (sub != null) {
            sub.setFont(Theme.font(Font.PLAIN, 12f));
            sub.setForeground(Theme.MUTED);
            sub.setAlignmentX(Component.LEFT_ALIGNMENT);
            t.add(sub);
        }
        c.add(t, BorderLayout.CENTER);
        return c;
    }

    private void updateExtensionStat() {
        if (statExtensions != null) {
            int n = ctx.config.enabledExtensions.size();
            statExtensions.setText(n == 0 ? "keine aktiv" : n + " aktiv");
        }
    }

    // ================================================================ Play bar

    private Component playBar() {
        JPanel bar = new JPanel(new BorderLayout(18, 0)) {
            @Override
            protected void paintComponent(java.awt.Graphics g) {
                g.setColor(Theme.SURFACE);
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(Theme.BORDER);
                g.drawLine(0, 0, getWidth(), 0);
            }
        };
        bar.setBorder(BorderFactory.createEmptyBorder(14, 22, 14, 22));

        // account (left)
        JPanel account = new JPanel(new BorderLayout(10, 0));
        account.setOpaque(false);
        avatar = new Avatar(42);
        account.add(avatar, BorderLayout.WEST);
        JPanel accText = column();
        accountName = label("Nicht angemeldet", 14, Font.BOLD, Theme.TEXT);
        accountHint = label("Anmelden", 12, Font.PLAIN, Theme.ACCENT_HOVER);
        accountHint.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        accountHint.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (ctx.play.storedSession() != null) {
                    showPage("account");
                } else {
                    startLogin();
                }
            }
        });
        accText.add(accountName);
        accText.add(accountHint);
        account.add(accText, BorderLayout.CENTER);
        account.setPreferredSize(new Dimension(230, 46));
        bar.add(account, BorderLayout.WEST);

        // status (center)
        JPanel status = new JPanel(new GridBagLayout());
        status.setOpaque(false);
        statusLabel = label("Bereit", 13, Font.PLAIN, Theme.MUTED);
        progress = new JProgressBar(0, 1000);
        progress.setPreferredSize(new Dimension(10, 6));
        progress.setVisible(false);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        status.add(statusLabel, c);
        c.gridy = 1;
        c.insets = new Insets(6, 0, 0, 0);
        status.add(progress, c);
        bar.add(status, BorderLayout.CENTER);

        // profile + play (right)
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        right.setOpaque(false);
        barRight = right;
        right.setVisible(nav == null || !"home".equals(nav.selected()));
        profileBox = new JComboBox<>();
        profileBox.setMaximumRowCount(12);
        for (ProfileCatalog.Profile p : ctx.catalog.profiles()) {
            profileBox.addItem(p);
        }
        profileBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                if (value instanceof ProfileCatalog.Profile p) {
                    setText("Minecraft " + p.minecraftVersion);
                }
                return this;
            }
        });
        profileBox.setPreferredSize(new Dimension(170, 40));
        profileBox.addActionListener(e -> {
            ProfileCatalog.Profile sel = (ProfileCatalog.Profile) profileBox.getSelectedItem();
            if (sel != null && !syncingProfile) {
                selectProfile(sel.id);
            }
        });
        stopButton = new JButton("Beenden");
        stopButton.setVisible(false);
        stopButton.setPreferredSize(new Dimension(100, 40));
        stopButton.addActionListener(e -> ctx.play.runningGame().ifPresent(g -> worker.submit(() -> g.stop(java.time.Duration.ofSeconds(15)))));
        playButton = new PlayButton("Spielen");
        playButton.addActionListener(e -> startPlay(false));
        right.add(profileBox);
        right.add(stopButton);
        right.add(playButton);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private void refreshAccount() {
        MinecraftSession s = ctx.play.storedSession();
        if (s != null) {
            accountName.setText(s.username());
            accountHint.setText(ctx.accounts.list().size() > 1 ? "Konto wechseln" : "Konten verwalten");
            avatar.setPlayer(s.username(), s.uuid());
        } else {
            accountName.setText("Nicht angemeldet");
            accountHint.setText("Mit Microsoft anmelden");
            avatar.setPlayer(null, null);
        }
        if (social != null) {
            social.apply(); // new account: reconnect; same account after a new sign-in: retry a rejected login
        }
    }

    private void signOut() {
        if (JOptionPane.showConfirmDialog(frame, "Abmelden? Beim nächsten Start ist eine neue Anmeldung nötig.",
                BuildInfo.NAME, JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            ctx.play.signOut();
        } catch (IOException ex) {
            error("Abmelden fehlgeschlagen", ex.getMessage());
        }
        refreshAccount();
    }

    private void startLogin() {
        if (!busy.compareAndSet(false, true)) {
            return;
        }
        LoginFlow flow = new LoginFlow(frame, ctx);
        if (PlayService.effectiveClientId(ctx.config) == null && !LoginFlow.showClientIdSetup(frame, ctx)) {
            busy.set(false);
            return;
        }
        currentLogin = flow;
        statusLabel.setText("Anmeldung im Browser …");
        worker.submit(() -> {
            try {
                MinecraftSession s = ctx.play.signIn(ctx.config, flow.interactive(), flow.cancelled());
                SwingUtilities.invokeLater(() -> statusLabel.setText("Angemeldet als " + s.username()));
            } catch (AuthException e) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Bereit");
                    if (e.reason() != AuthException.Reason.CANCELLED) {
                        error("Anmeldung fehlgeschlagen", e.getMessage());
                    }
                });
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> error("Anmeldung fehlgeschlagen", e.getMessage()));
            } finally {
                busy.set(false);
                SwingUtilities.invokeLater(() -> {
                    flow.closeDialogs();
                    refreshAccount();
                    if (accountPage != null && accountPage.isShowing()) {
                        accountPage.refresh();
                    }
                });
            }
        });
    }

    @Override
    public void signIn() {
        startLogin();
    }

    @Override
    public void accountsChanged() {
        refreshAccount();
    }

    private void startPlay(boolean repair) {
        startPlay(repair, null);
    }

    @Override
    public void launch(LaunchCommandBuilder.QuickPlay quickPlay, boolean repair) {
        startPlay(repair, quickPlay);
    }

    @Override
    public boolean gameRunning() {
        return ctx.play.runningGame().isPresent();
    }

    private void startPlay(boolean repair, LaunchCommandBuilder.QuickPlay quickPlay) {
        if (ctx.play.runningGame().isPresent() && !ctx.config.multipleInstances) {
            JOptionPane.showMessageDialog(frame, "Minecraft läuft bereits. Mehrere Instanzen lassen sich unter "
                    + "Einstellungen → Spiel erlauben.", BuildInfo.NAME, JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (!busy.compareAndSet(false, true)) {
            return;
        }
        ProfileCatalog.Profile profile = ctx.selectedProfile();
        dev.lumora.launcher.core.profile.Flavor flavor = currentFlavor();
        LoginFlow flow = new LoginFlow(frame, ctx);
        // Ask for the client ID now (before the long install), but only if a sign-in may actually be needed.
        if (!flow.ensureClientId()) {
            busy.set(false);
            return;
        }
        currentLogin = flow;
        setPlay("Vorbereiten …", false);
        progress.setVisible(true);
        worker.submit(() -> {
            try {
                ctx.play.play(profile, flavor, ctx.config, PlayService.Mode.ACCOUNT, repair,
                        flow.interactive(), flow.cancelled(), null, quickPlay, playListener());
                SwingUtilities.invokeLater(() -> {
                    flow.closeDialogs();
                    refreshAccount();
                    switch (ctx.config.afterLaunch) {
                        case "hide" -> frame.setState(JFrame.ICONIFIED);
                        case "close" -> quit(true);
                        default -> {
                            // keep the launcher open
                        }
                    }
                });
            } catch (AuthException e) {
                SwingUtilities.invokeLater(() -> {
                    flow.closeDialogs();
                    resetPlayButton();
                    if (e.reason() != AuthException.Reason.CANCELLED) {
                        error("Anmeldung fehlgeschlagen", e.getMessage());
                    }
                });
            } catch (Exception e) {
                Log.error("Start failed", e);
                SwingUtilities.invokeLater(() -> {
                    flow.closeDialogs();
                    resetPlayButton();
                    error("Start fehlgeschlagen", e.getMessage() + "\n\nDetails: " + Log.file());
                });
            } finally {
                busy.set(false);
            }
        });
    }

    private PlayService.Listener playListener() {
        return new PlayService.Listener() {
            @Override
            public void state(PlayService.State state, String message) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText(message);
                    switch (state) {
                        case INSTALLING -> setPlay("Installiere …", false);
                        case LOGGING_IN -> setPlay("Anmeldung …", false);
                        case LAUNCHING -> setPlay("Starte …", false);
                        case PLAYING -> {
                            setPlay(ctx.config.multipleInstances ? "Spielen" : "Läuft", ctx.config.multipleInstances);
                            setButtonProgress(-1);
                            progress.setVisible(false);
                            stopButton.setVisible(true);
                            gameStarted = java.time.Instant.now().getEpochSecond();
                        }
                        case IDLE, ERROR -> resetPlayButton();
                    }
                    updateDiscord();
                });
            }

            @Override
            public void gameOutput(String line) {
                SwingUtilities.invokeLater(() -> appendLog("[game] " + line));
            }

            @Override
            public void gameExited(GameProcess.Exit exit, List<CrashAnalyzer.Hint> hints) {
                SwingUtilities.invokeLater(LauncherWindow.this::refreshPlayTimes);
                if (!exit.crashed()) {
                    return;
                }
                showCrashDialog(exit, hints);
            }

            @Override
            public void stage(String description) {
                SwingUtilities.invokeLater(() -> statusLabel.setText(description));
            }

            @Override
            public void progress(double fraction, String detail) {
                SwingUtilities.invokeLater(() -> {
                    if (fraction < 0) {
                        progress.setIndeterminate(true);
                        setButtonProgress(-1);
                    } else {
                        progress.setIndeterminate(false);
                        progress.setValue((int) (fraction * 1000));
                        setButtonProgress(fraction);
                    }
                    if (detail != null && !detail.isEmpty()) {
                        statusLabel.setText(detail);
                    }
                });
            }
        };
    }

    private void setPlay(String text, boolean enabled) {
        playButton.setText(text);
        playButton.setEnabled(enabled);
        if (heroPlay != null) {
            heroPlay.setText(text.equals("Spielen") ? "Spiel starten" : text);
            heroPlay.setEnabled(enabled);
            heroArrow.setEnabled(enabled);
        }
    }

    private void setButtonProgress(double p) {
        playButton.setProgress(p);
        if (heroPlay != null) {
            heroPlay.setProgress(p);
        }
    }

    private void resetPlayButton() {
        boolean running = ctx.play.runningGame().isPresent();
        // several instances: the play button stays usable while a game runs
        setPlay(running && !ctx.config.multipleInstances ? "Läuft" : "Spielen", !running || ctx.config.multipleInstances);
        stopButton.setVisible(running);
        setButtonProgress(-1);
        progress.setIndeterminate(false);
        progress.setVisible(false);
    }

    // ================================================================ PageHost

    @Override
    public AppContext ctx() {
        return ctx;
    }

    @Override
    public JFrame frame() {
        return frame;
    }

    @Override
    public void openFolder(Path dir) {
        open(dir);
    }

    @Override
    public void browse(String url) {
        if (url == null || !url.startsWith("https://")) {
            return;
        }
        try {
            Desktop.getDesktop().browse(java.net.URI.create(url));
        } catch (IOException | UnsupportedOperationException | IllegalArgumentException e) {
            error("Link kann nicht geöffnet werden", url + "\n" + e.getMessage());
        }
    }

    @Override
    public void saveConfig() {
        saveConfigQuietly();
    }

    @Override
    public void checkUpdates() {
        worker.submit(this::interactiveUpdateCheck);
    }

    @Override
    public void extensionsChanged() {
        updateExtensionStat();
    }

    /**
     * After a crash: short summary (what happened, likely culprit, hint) with the way to the crash page, a restart
     * and the report itself. Falls back to the plain text if the report cannot be read.
     */
    private void showCrashDialog(GameProcess.Exit exit, List<CrashAnalyzer.Hint> hints) {
        pageIo.submit(() -> {
            CrashReport report = null;
            if (exit.crashReport().isPresent()) {
                Path file = exit.crashReport().get();
                String profileId = file.getParent().getParent().getFileName().toString();
                try {
                    report = CrashReports.load(ctx.paths, profileId, file);
                } catch (IOException | RuntimeException e) {
                    Log.warn("Could not read crash report " + file + ": " + e.getMessage());
                }
            }
            CrashReport r = report;
            SwingUtilities.invokeLater(() -> {
                frame.setState(JFrame.NORMAL);
                StringBuilder sb = new StringBuilder();
                sb.append(r != null ? r.description() : "Minecraft wurde unerwartet beendet").append(" (Code ").append(exit.code()).append(")\n");
                if (r != null && r.exception() != null) {
                    sb.append('\n').append(r.exception()).append('\n');
                }
                if (r != null && !r.suspects().isEmpty()) {
                    CrashReport.Suspect s = r.suspects().getFirst();
                    sb.append("\nWahrscheinliche Ursache: ").append(s.name()).append("\n(").append(s.reason()).append(")\n");
                }
                for (CrashAnalyzer.Hint h : r != null ? r.hints() : hints) {
                    sb.append("\nTipp – ").append(h.title()).append(": ").append(h.advice());
                }
                JTextArea text = new JTextArea(sb.toString().strip());
                text.setEditable(false);
                text.setLineWrap(true);
                text.setWrapStyleWord(true);
                text.setColumns(56);
                text.setOpaque(false);
                Object[] options = {"Crash-Report ansehen", "Erneut starten", "Schließen"};
                int choice = JOptionPane.showOptionDialog(frame, text, "Minecraft ist abgestürzt", JOptionPane.DEFAULT_OPTION,
                        JOptionPane.ERROR_MESSAGE, null, options, options[0]);
                if (choice == 0) {
                    showPage("crashes");
                } else if (choice == 1) {
                    startPlay(false);
                }
            });
        });
    }

    /** Play time and last start under each version tile. */
    private void refreshPlayTimes() {
        Map<String, String> info = new java.util.HashMap<>();
        for (ProfileCatalog.Profile p : ctx.catalog.profiles()) {
            PlayStats.Entry s = ctx.play.stats().get(p.id);
            if (s.launches > 0) {
                info.put(p.id, PlayStats.format(s.playTime()) + " gespielt");
            }
        }
        playInfo = info;
    }

    /** Looks for a newer launcher on GitHub Releases; {@code interactive} also reports "up to date" and errors. */
    void checkLauncherUpdate(boolean interactive) {
        pageIo.submit(() -> {
            try {
                dev.lumora.launcher.core.update.LauncherUpdate u = new dev.lumora.launcher.core.update.LauncherUpdate();
                var r = u.check(BuildInfo.VERSION);
                SwingUtilities.invokeLater(() -> {
                    if (r.isPresent()) {
                        offerLauncherUpdate(u, r.get());
                    } else if (interactive) {
                        JOptionPane.showMessageDialog(frame, "Der Launcher ist aktuell (Version " + BuildInfo.VERSION + ").",
                                BuildInfo.NAME, JOptionPane.INFORMATION_MESSAGE);
                    }
                });
            } catch (IOException | RuntimeException e) {
                Log.info("Launcher update check: " + e.getMessage());
                if (interactive) {
                    SwingUtilities.invokeLater(() -> error("Update-Prüfung fehlgeschlagen", e.getMessage()));
                }
            }
        });
    }

    /** Set once the window's taskbar pin points to LumoraClient.exe (cleared again on quit). */
    private boolean taskbarPinned;

    /**
     * A pin of the launcher window starts LumoraClient.exe instead of the extracted program (setting
     * "Update auch beim direkten Start"). Only when that EXE exists: the starter's, the one it recorded, or the copy a
     * self-update put there.
     */
    private void applyTaskbarPin() {
        if (taskbarPinned || !ctx.config.portableSelfUpdate || !dev.lumora.launcher.core.integrations.WindowsTaskbar.supported()) {
            return;
        }
        var target = dev.lumora.launcher.core.update.LauncherUpdate.selfUpdateTarget(true);
        if (target.isEmpty() || !Files.isRegularFile(target.get().exe())) {
            return;
        }
        taskbarPinned = dev.lumora.launcher.core.integrations.WindowsTaskbar.pinAs(frame, target.get().exe(), BuildInfo.NAME);
        Log.info("Taskbar pin -> " + target.get().exe() + " (" + target.get().source() + "): " + (taskbarPinned ? "set" : "not set"));
    }

    private void offerLauncherUpdate(dev.lumora.launcher.core.update.LauncherUpdate u, dev.lumora.launcher.core.update.LauncherUpdate.Release r) {
        var target = dev.lumora.launcher.core.update.LauncherUpdate.selfUpdateTarget(ctx.config.portableSelfUpdate);
        var portable = target.map(dev.lumora.launcher.core.update.LauncherUpdate.Target::exe);
        boolean managedCopy = target.isPresent() && !target.get().starter();
        boolean directStartOff = target.isEmpty() && !ctx.config.portableSelfUpdate
                && dev.lumora.launcher.core.update.LauncherUpdate.startedFromPortableFolder();
        // release notes are Markdown: show them as plain text
        String notes = r.notes().strip()
                .replaceAll("(?m)^#{1,6}\\s*", "")
                .replace("**", "")
                .replace("`", "")
                .replaceAll("(?m)^\\|.*\\|\\s*$\\n?", "");
        if (notes.length() > 2000) {
            notes = notes.substring(0, 1998) + "…";
        }
        JTextArea text = new JTextArea("Lumora " + r.version() + " ist verfügbar (du hast " + BuildInfo.VERSION + ")."
                + (notes.isEmpty() ? "" : "\n\n" + notes)
                + (managedCopy ? "\n\nDas Update wird geladen, mit der Signatur geprüft und als " + portable.get()
                + " gespeichert, danach startet der Launcher neu. Hefte das Lumora-Symbol danach neu an."
                : portable.isPresent() ? "\n\nDas Update wird geladen, mit der Signatur geprüft und der Launcher danach neu gestartet."
                : directStartOff ? "\n\nDer Launcher wurde direkt gestartet, nicht über LumoraClient.exe, und „Update auch beim "
                + "direkten Start“ ist aus (Einstellungen). Lade die neue Version von der Release-Seite."
                : "\n\nDiese Installation aktualisiert sich nicht selbst: lade die neue Version von der Release-Seite."));
        text.setEditable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setOpaque(false);
        text.setCaretPosition(0);
        // fixed size with a scroll bar: long notes never push the buttons out of the window (any font size or scaling)
        JScrollPane notesPane = new JScrollPane(text, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        notesPane.setBorder(BorderFactory.createEmptyBorder());
        notesPane.setOpaque(false);
        notesPane.getViewport().setOpaque(false);
        notesPane.getVerticalScrollBar().setUnitIncrement(16);
        int maxHeight = Math.max(160, Math.min(320, frame.getGraphicsConfiguration().getBounds().height / 3));
        notesPane.setPreferredSize(new Dimension(520, maxHeight));
        Object[] options = portable.isPresent() ? new Object[]{"Jetzt aktualisieren", "Später"} : new Object[]{"Release-Seite öffnen", "Später"};
        int choice = JOptionPane.showOptionDialog(frame, notesPane, "Launcher-Update", JOptionPane.DEFAULT_OPTION,
                JOptionPane.INFORMATION_MESSAGE, null, options, options[0]);
        if (choice != 0) {
            return;
        }
        if (portable.isEmpty()) {
            if (r.pageUrl() != null) {
                browse(r.pageUrl());
            }
            return;
        }
        statusLabel.setText("Lade Lumora " + r.version() + " …");
        pageIo.submit(() -> {
            try {
                java.nio.file.Path file = u.download(r, ctx.paths.root().resolve("cache").resolve("launcher-update"));
                SwingUtilities.invokeLater(() -> {
                    if (ctx.play.runningGame().isPresent()) {
                        JOptionPane.showMessageDialog(frame, "Das Update ist geladen. Bitte erst Minecraft beenden, dann noch einmal "
                                + "„Jetzt aktualisieren“ wählen.", "Launcher-Update", JOptionPane.INFORMATION_MESSAGE);
                        statusLabel.setText("Bereit");
                        return;
                    }
                    try {
                        dev.lumora.launcher.core.update.LauncherUpdate.install(file, portable.get());
                        new ProcessBuilder(portable.get().toString()).start();
                        quit(true);
                    } catch (IOException ex) {
                        error("Launcher-Update fehlgeschlagen", ex.getMessage());
                    }
                });
            } catch (IOException | RuntimeException e) {
                Log.warn("Launcher update failed: " + e.getMessage());
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Bereit");
                    error("Launcher-Update fehlgeschlagen", e.getMessage());
                });
            }
        });
    }

    private void backgroundUpdateCheck() {
        List<UpdateChecker.Update> updates = new UpdateChecker(ctx.downloader).check(ctx.catalog, ctx.selectedProfile());
        SwingUtilities.invokeLater(() -> statUpdates.setText(updates.isEmpty() ? "alles aktuell" : updates.size() + " Update(s) verfügbar"));
    }

    private void interactiveUpdateCheck() {
        List<UpdateChecker.Update> updates = new UpdateChecker(ctx.downloader).check(ctx.catalog, ctx.selectedProfile());
        SwingUtilities.invokeLater(() -> {
            if (updates.isEmpty()) {
                JOptionPane.showMessageDialog(frame, "Alle Komponenten sind aktuell.", BuildInfo.NAME, JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            StringBuilder sb = new StringBuilder("Verfügbare Updates:\n\n");
            for (UpdateChecker.Update u : updates) {
                sb.append("• ").append(u.current().name).append(": ").append(u.current().version).append(" → ").append(u.candidate().version).append('\n');
            }
            sb.append("\nÜbernehmen? Die Dateien werden beim nächsten Start geladen, geprüft und auf Kompatibilität getestet.");
            if (JOptionPane.showConfirmDialog(frame, sb.toString(), BuildInfo.NAME, JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                try {
                    UpdateChecker.writeOverride(new LauncherPaths(ctx.defaultRoot).lockfileOverride(),
                            updates.stream().map(UpdateChecker.Update::candidate).toList());
                    JOptionPane.showMessageDialog(frame, "Updates vorgemerkt. Bitte den Launcher neu starten.", BuildInfo.NAME, JOptionPane.INFORMATION_MESSAGE);
                } catch (IOException ex) {
                    error("Update fehlgeschlagen", ex.getMessage());
                }
            }
        });
    }

    // ================================================================ Settings

    // ================================================================ Diagnostics & logs

    private Component diagnosticsPage() {
        JPanel page = column();
        page.add(pageTitle("Diagnose", "Systemprüfung, Reparatur und schneller Zugriff auf alle Ordner."));
        page.add(Box.createVerticalStrut(18));
        Card card = new Card(new BorderLayout());
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        diagList.setOpaque(false);
        diagList.setLayout(new BoxLayout(diagList, BoxLayout.Y_AXIS));
        diagList.add(label("Systemprüfung läuft …", 13, Font.PLAIN, Theme.MUTED));
        card.add(diagList, BorderLayout.CENTER);
        page.add(card);
        page.add(Box.createVerticalStrut(16));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        buttons.setOpaque(false);
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttons.add(actionButton("Erneut prüfen", Icons.Kind.REFRESH, this::runSystemCheck));
        buttons.add(Box.createHorizontalStrut(8));
        buttons.add(actionButton("Installation reparieren", Icons.Kind.CHECK, () -> startPlay(true)));
        buttons.add(Box.createHorizontalStrut(8));
        buttons.add(actionButton("Log-Ordner", Icons.Kind.FOLDER, () -> open(ctx.defaultRoot.resolve("logs"))));
        buttons.add(Box.createHorizontalStrut(8));
        buttons.add(actionButton("Spielordner", Icons.Kind.FOLDER, () -> open(ctx.paths.instance(ctx.selectedProfile().id))));
        buttons.add(Box.createHorizontalStrut(8));
        buttons.add(actionButton("Spiel-Diagnose", Icons.Kind.FOLDER, () -> open(ctx.paths.diagnostics())));
        page.add(buttons);
        page.add(Box.createVerticalGlue());
        return scroll(page);
    }

    private JButton actionButton(String text, Icons.Kind icon, Runnable action) {
        JButton b = new JButton(text, Icons.of(icon, 16, Theme.ACCENT_HOVER));
        b.setIconTextGap(8);
        b.setPreferredSize(new Dimension(b.getPreferredSize().width + 18, 38));
        b.addActionListener(e -> action.run());
        return b;
    }

    private void runSystemCheck() {
        worker.submit(() -> {
            SystemCheck.Report r = SystemCheck.run(ctx.os, ctx.paths);
            SwingUtilities.invokeLater(() -> {
                diagList.removeAll();
                boolean first = true;
                for (SystemCheck.Item it : r.items()) {
                    if (!first) {
                        diagList.add(Box.createVerticalStrut(10));
                    }
                    first = false;
                    diagList.add(diagRow(it));
                }
                diagList.revalidate();
                diagList.repaint();
                statGpu.setText(r.gpus().isEmpty() ? "unbekannt" : r.gpus().getFirst().replaceAll(" \\(Treiber.*", ""));
                statRam.setText(String.format("%.1f von %d GB", ctx.config.maxMemoryMb / 1024.0, Math.round(r.totalRamMb() / 1024.0)));
            });
        });
    }

    private JComponent diagRow(SystemCheck.Item it) {
        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        Color dot = switch (it.status()) {
            case OK -> Theme.OK;
            case WARN -> Theme.WARN;
            case FAIL -> Theme.FAIL;
        };
        JComponent d = new JComponent() {
            @Override
            protected void paintComponent(java.awt.Graphics g0) {
                java.awt.Graphics2D g = (java.awt.Graphics2D) g0.create();
                g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(Theme.alpha(dot, 60));
                g.fillOval(0, 4, 14, 14);
                g.setColor(dot);
                g.fillOval(3, 7, 8, 8);
                g.dispose();
            }
        };
        d.setPreferredSize(new Dimension(14, 22));
        row.add(d, BorderLayout.WEST);
        JLabel name = label(it.name(), 14, Font.BOLD, Theme.TEXT);
        name.setPreferredSize(new Dimension(190, 22));
        JLabel detail = label(it.detail(), 13, Font.PLAIN, Theme.MUTED);
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.add(name, BorderLayout.WEST);
        wrap.add(detail, BorderLayout.CENTER);
        row.add(wrap, BorderLayout.CENTER);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        return row;
    }

    private Component logsPage() {
        JPanel page = new JPanel(new BorderLayout(0, 14));
        page.setOpaque(false);
        page.add(pageTitle("Logs", "Launcher- und Spielausgabe live. Zugangstoken werden automatisch geschwärzt."), BorderLayout.NORTH);
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        logArea.setBackground(Theme.SURFACE);
        logArea.setForeground(new Color(0xC7CDD4));
        logArea.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        JScrollPane sp = new JScrollPane(logArea);
        Card card = new Card(new BorderLayout());
        card.padding(4, 4, 4, 4);
        card.add(sp, BorderLayout.CENTER);
        page.add(card, BorderLayout.CENTER);
        JLabel file = label("Logdatei: " + Log.file(), 12, Font.PLAIN, Theme.MUTED);
        page.add(file, BorderLayout.SOUTH);
        return page;
    }

    private void appendLog(String line) {
        logArea.append(line + "\n");
        if (logArea.getLineCount() > 5000) {
            try {
                logArea.replaceRange("", 0, logArea.getLineEndOffset(1000));
            } catch (javax.swing.text.BadLocationException ignored) {
                // cannot happen for valid line counts
            }
        }
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    // ================================================================ helpers

    private static JPanel column() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        return p;
    }

    private static JLabel label(String text, float size, int style, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.font(style, size));
        l.setForeground(color);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private static JComponent pageTitle(String title, String subtitle) {
        JPanel p = column();
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(label(title, 28, Font.BOLD, Theme.TEXT));
        p.add(Box.createVerticalStrut(4));
        p.add(label(subtitle, 14, Font.PLAIN, Theme.MUTED));
        return p;
    }

    private static JComponent sectionTitle(String title, String hint) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(label(title, 17, Font.BOLD, Theme.TEXT));
        if (hint != null) {
            p.add(Box.createHorizontalStrut(12));
            p.add(label(hint, 13, Font.PLAIN, Theme.MUTED));
        }
        p.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        return p;
    }

    private static JComponent chip(String text) {
        JLabel l = new JLabel(text) {
            @Override
            protected void paintComponent(java.awt.Graphics g0) {
                java.awt.Graphics2D g = (java.awt.Graphics2D) g0.create();
                g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(Theme.alpha(Theme.ACCENT, 50));
                g.fillRoundRect(0, 0, getWidth(), getHeight(), Theme.arc(getHeight()), Theme.arc(getHeight()));
                g.dispose();
                super.paintComponent(g0);
            }
        };
        l.setFont(Theme.font(Font.BOLD, 11.5f));
        l.setForeground(Theme.ACCENT_TEXT);
        l.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        return l;
    }

    private static JTextField field(String value, String placeholder) {
        JTextField f = new JTextField(value);
        f.putClientProperty("JTextField.placeholderText", placeholder);
        f.setPreferredSize(new Dimension(200, 36));
        return f;
    }

    private static JTextArea wrapText(String text, float size, Color color) {
        return new WrapText(text, size, color);
    }

    /** Page content that always fits the viewport width (only scrolls vertically). */
    private static final class WidthTrackingPanel extends JPanel implements javax.swing.Scrollable {
        WidthTrackingPanel() {
            super(new BorderLayout());
            setOpaque(false);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(java.awt.Rectangle r, int orientation, int direction) {
            return 18;
        }

        @Override
        public int getScrollableBlockIncrement(java.awt.Rectangle r, int orientation, int direction) {
            return r.height - 40;
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

    private static JScrollPane scroll(JComponent content) {
        JPanel holder = new WidthTrackingPanel();
        holder.add(content, BorderLayout.NORTH);
        JScrollPane sp = new JScrollPane(holder);
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        sp.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sp.getVerticalScrollBar().setUnitIncrement(18);
        return sp;
    }

    /** Window close button: to the tray when that is switched on, else quit. */
    private void onClose() {
        if ((ctx.config.minimizeToTray || startedInTray) && tray.active()) {
            frame.setVisible(false);
            if (!trayHintShown) {
                trayHintShown = true;
                tray.notify("Lumora läuft weiter", "Der Launcher ist im Infobereich. Beenden über das Lumora-Symbol.");
            }
            return;
        }
        quit();
    }

    /** Tray icon while "Im Tray bleiben" or chat notifications are on (or after an autostart). */
    private void updateTray() {
        boolean want = Tray.supported() && (ctx.config.minimizeToTray || ctx.config.socialNotifications || startedInTray);
        tray.setActive(want);
    }

    /** Windows notification for new messages and requests while the window is not in front. */
    private void notifySocial(dev.lumora.social.SocialEvent e) {
        dev.lumora.social.SocialClient c = social.client();
        String level = ctx.config.notificationLevel;
        if (c == null || "none".equals(level) || !ctx.config.socialNotifications || (frame.isVisible() && frame.isActive())) {
            return;
        }
        if ("messages".equals(level) && e.kind != dev.lumora.social.SocialEvent.Kind.MESSAGE) {
            return;
        }
        if (e.kind == dev.lumora.social.SocialEvent.Kind.MESSAGE && e.message != null) {
            dev.lumora.social.model.ConversationInfo ci = c.conversation(e.conv);
            if (ci != null && ci.muted) {
                return;
            }
            String who = c.nameOf(e.message.sender);
            dev.lumora.social.model.Group g = dev.lumora.social.Conv.isGroup(e.conv) ? c.group(dev.lumora.social.Conv.groupId(e.conv)) : null;
            String text = e.message.text.length() > 120 ? e.message.text.substring(0, 118) + "…" : e.message.text;
            tray.notify(g == null ? who : who + " · " + g.name, text.replace('\n', ' '));
        } else if (e.kind == dev.lumora.social.SocialEvent.Kind.NOTICE && e.text != null) {
            tray.notify("Lumora Social", e.text);
        }
    }

    /** Really ends the launcher (tray "Beenden" or closing without tray). */
    private void quit() {
        quit(false);
    }

    /** @param afterLaunch "Aktion nach dem Start: Schließen": no question, the game keeps running on its own */
    private void quit(boolean afterLaunch) {
        if (!afterLaunch && !frame.isVisible()) {
            frame.setVisible(true);
        }
        if (!afterLaunch && ctx.play.runningGame().isPresent()) {
            int r = JOptionPane.showConfirmDialog(frame, "Minecraft läuft noch. Launcher schließen? Das Spiel läuft weiter.",
                    BuildInfo.NAME, JOptionPane.YES_NO_OPTION);
            if (r != JOptionPane.YES_OPTION) {
                return;
            }
        }
        if (currentLogin != null) {
            currentLogin.cancel();
        }
        social.shutdown();
        discord.close();
        if (taskbarPinned) {
            dev.lumora.launcher.core.integrations.WindowsTaskbar.clear(frame);
        }
        tray.setActive(false);
        Log.close();
        frame.dispose();
        System.exit(0);
    }

    private void open(Path dir) {
        try {
            Files.createDirectories(dir);
            Desktop.getDesktop().open(dir.toFile());
        } catch (IOException | UnsupportedOperationException e) {
            error("Ordner kann nicht geöffnet werden", dir + "\n" + e.getMessage());
        }
    }

    @Override
    public void error(String title, String message) {
        JTextArea text = new JTextArea(message);
        text.setEditable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setColumns(60);
        text.setOpaque(false);
        JOptionPane.showMessageDialog(frame, text, title, JOptionPane.ERROR_MESSAGE);
    }

    private void saveConfigQuietly() {
        try {
            ctx.saveConfig();
        } catch (IOException e) {
            Log.warn("Could not save config", e);
        }
    }

    static List<Image> icons() {
        List<Image> out = new ArrayList<>();
        for (int s : new int[]{32, 64, 256}) {
            try (InputStream in = LauncherWindow.class.getResourceAsStream("/lumora/icon-" + s + ".png")) {
                if (in != null) {
                    out.add(ImageIO.read(in));
                }
            } catch (IOException ignored) {
                // icons are cosmetic
            }
        }
        return out;
    }
}
