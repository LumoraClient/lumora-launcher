package dev.lumora.launcher.app;

import dev.lumora.launcher.app.ui.Avatar;
import dev.lumora.launcher.app.ui.Card;
import dev.lumora.launcher.app.ui.HeroPanel;
import dev.lumora.launcher.app.ui.Icons;
import dev.lumora.launcher.app.pages.AccountPage;
import dev.lumora.launcher.app.pages.DiscoverPage;
import dev.lumora.launcher.app.pages.InstancesPage;
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
import dev.lumora.launcher.core.i18n.Lang;
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
    private dev.lumora.launcher.core.util.SingleInstance instance;
    private boolean trayHintShown;
    private DiscoverPage browserPage;
    private InstancesPage instancesPage;
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
    /** What the running game reported last (Discord shows it); null without a running Lumora game. */
    private dev.lumora.launcher.core.integrations.GameStatus lastGameStatus;
    /** Looks every 5 s whether the game changed menu / world / server (for Discord). */
    private final javax.swing.Timer gameStatusTimer = new javax.swing.Timer(5000, e -> pollGameStatus());

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
    private JButton instanceButton;
    /** "3 Std. gespielt" per profile, shown in the version menu. */
    private Map<String, String> playInfo = Map.of();
    private boolean syncingProfile;

    private HomeDashboard dashboard;
    private JLabel heroOverline;
    private JPanel quickPlayBar;
    private HeroPanel heroPanel;
    private int quickPlayGeneration;

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
        gameStatusTimer.start();
        updateNewsBadge();
        if (ctx.configLoad.recoveredFromCorruption()) {
            JOptionPane.showMessageDialog(frame, Lang.t("config.corrupt", ctx.configLoad.backup()), BuildInfo.NAME, JOptionPane.WARNING_MESSAGE);
        }
        runSystemCheck();
        if (ctx.config.checkForUpdates) {
            worker.submit(this::backgroundUpdateCheck);
            checkLauncherUpdate(false);
        }
    }

    /** Documentation screenshot of the whole window (developer option --ui-shot; Swing thread). */
    void capture(java.nio.file.Path file) throws java.io.IOException {
        // an open dialog of the launcher (e.g. "Neue Instanz") instead of the window behind it
        java.awt.Container root = frame.getRootPane();
        for (java.awt.Window w : frame.getOwnedWindows()) {
            if (w.isShowing() && w instanceof javax.swing.JDialog d) {
                root = d.getRootPane();
            }
        }
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(root.getWidth(), root.getHeight(), java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        root.paint(g);
        // open popups (the instance choice) are own windows with FlatLaf: paint them at their place
        if (root == frame.getRootPane() && root.isShowing()) {
            java.awt.Point origin = root.getLocationOnScreen();
            for (java.awt.Window w : frame.getOwnedWindows()) {
                if (w.isShowing() && !(w instanceof java.awt.Dialog)) {
                    java.awt.Point p = w.getLocationOnScreen();
                    java.awt.Graphics2D pg = (java.awt.Graphics2D) g.create(p.x - origin.x, p.y - origin.y, w.getWidth(), w.getHeight());
                    w.paint(pg);
                    pg.dispose();
                }
            }
        }
        g.dispose();
        java.nio.file.Files.createDirectories(file.getParent());
        javax.imageio.ImageIO.write(img, "png", file.toFile());
    }

    JFrame window() {
        return frame;
    }

    /** Used by documentation screenshots; "account/security" or "settings/game" also open a tab or category. */
    @Override
    public void showPage(String id) {
        int hashAt = id.indexOf('#');
        if (hashAt > 0 && !id.startsWith("instances/")) {
            // "home#picker", "instances#create-versions": the page, then that popup or dialog (documentation screenshots)
            String action = id.substring(hashAt + 1);
            showPage(id.substring(0, hashAt));
            SwingUtilities.invokeLater(() -> pageAction(action));
            return;
        }
        int slash = id.indexOf('/');
        if (newsPage != null) {
            newsPage.closeArticle();
        }
        String navId = slash < 0 ? id : id.substring(0, slash);
        nav.select(navId.equals("project") ? "browser" : navId);
        if (slash > 0 && id.startsWith("news/")) {
            newsPage.show(id.substring(slash + 1));
        } else if (slash > 0 && id.startsWith("account/")) {
            accountPage.selectTab(id.substring(slash + 1));
        } else if (slash > 0 && id.startsWith("settings/")) {
            settingsPage.showCategory(id.substring(slash + 1));
        } else if (slash > 0 && id.startsWith("instances/")) {
            // "instances/custom:my-pack" or "instances/26.3": the page of that instance
            String rest = id.substring(slash + 1);
            int hash = rest.indexOf('#');
            ctx.instances.find(ctx.config, hash < 0 ? rest : rest.substring(0, hash)).ifPresent(instancesPage::open);
            if (hash > 0) {
                instancesPage.showSection(rest.substring(hash + 1));
            }
        } else if (slash > 0 && id.startsWith("project/")) {
            // "project/sodium": the detail page of a Modrinth project
            browserPage.openProject(id.substring(slash + 1), dev.lumora.launcher.core.platform.ProjectType.MOD);
        } else if (slash > 0 && id.startsWith("browser/")) {
            // "browser/mod", "browser/shader": Entdecken for that content type
            browserPage.show(dev.lumora.launcher.core.platform.ProjectType.of(id.substring(slash + 1)), null);
        }
    }

    private void pageAction(String action) {
        switch (action) {
            case "picker" -> showVersionMenu();
            case "picker-bar" -> dev.lumora.launcher.app.pages.InstancePicker.show(this, pageIo, instanceButton, true);
            case "create" -> instancesPage.showCreate(false);
            case "create-versions" -> instancesPage.showCreate(true);
            case "create-versions-fabric" -> instancesPage.showCreate(dev.lumora.launcher.core.instance.InstanceKind.FABRIC);
            default -> Log.warn("Unknown page action " + action);
        }
    }

    /** A second start of the launcher brings this window back (from the tray, minimized or behind other windows). */
    void attach(dev.lumora.launcher.core.util.SingleInstance i) {
        instance = i;
        if (i != null) {
            i.onRequest(r -> {
                if (r == dev.lumora.launcher.core.util.SingleInstance.Request.SHOW) {
                    SwingUtilities.invokeLater(this::bringToFront);
                }
            });
        }
    }

    private void bringToFront() {
        frame.setVisible(true);
        frame.setExtendedState(frame.getExtendedState() & ~java.awt.Frame.ICONIFIED);
        // Windows lets only the foreground app raise its windows; a short always-on-top gets past that
        frame.setAlwaysOnTop(true);
        frame.toFront();
        frame.requestFocus();
        frame.setAlwaysOnTop(false);
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
        newsPage = new NewsPage(this, pageIo);
        pageHost.add(scroll((JComponent) homePage()), "home");
        libraryPage = new LibraryPage(this, pageIo);
        pageHost.add(libraryPage, "library");
        accountPage = new AccountPage(this, pageIo);
        pageHost.add(accountPage, "account");
        browserPage = new DiscoverPage(this, pageIo);
        pageHost.add(browserPage, "browser");
        instancesPage = new InstancesPage(this, pageIo);
        pageHost.add(instancesPage, "instances");
        // files from the Explorer dropped anywhere in the window (pages with their own drop targets come first)
        dev.lumora.launcher.app.pages.ImportFlow.installWindowDrop(this, pageIo, pageHost);
        crashPage = new CrashPage(this, pageIo);
        pageHost.add(crashPage, "crashes");
        socialPage = new dev.lumora.launcher.app.social.SocialPage(this, social);
        pageHost.add(socialPage, "social");
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
            public void homeChanged() {
                refreshVersionInfo(ctx.selectedProfile());
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
                // shown on the performance page
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
            } else if (id.equals("instances")) {
                instancesPage.refresh();
            } else if (id.equals("crashes")) {
                crashPage.refresh();
            }
        });
        nav.addItem("home", Lang.t("nav.home_page"), Icons.Kind.HOME);
        nav.addItem("instances", Lang.t("nav.instances"), Icons.Kind.CUBE);
        nav.addItem("browser", Lang.t("nav.browser"), Icons.Kind.COMPASS);
        nav.addItem("performance", Lang.t("nav.performance"), Icons.Kind.CHART);
        nav.addItem("library", Lang.t("nav.library"), Icons.Kind.PUZZLE);
        nav.addItem("account", Lang.t("nav.account"), Icons.Kind.USER);
        nav.addItem("settings", Lang.t("nav.settings"), Icons.Kind.GEAR);
        nav.addSeparator();
        nav.addItem("social", Lang.t("nav.social"), Icons.Kind.USERS);
        nav.addItem("servers", Lang.t("nav.servers"), Icons.Kind.GLOBE);
        nav.addGlue();
        nav.addItem("news", Lang.t("nav.news"), Icons.Kind.BELL);
        nav.addItem("crashes", Lang.t("nav.crashes"), Icons.Kind.ALERT);
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
        page.add(Box.createVerticalStrut(16));
        dashboard = new HomeDashboard(new HomeDashboard.Host() {
            @Override
            public AppContext ctx() {
                return ctx;
            }

            @Override
            public dev.lumora.launcher.core.profile.Flavor flavor() {
                return currentFlavor();
            }

            @Override
            public void play(String profileId, LaunchCommandBuilder.QuickPlay quickPlay) {
                if (!profileId.equals(ctx.config.selectedProfile)) {
                    selectProfile(profileId);
                }
                startPlay(false, quickPlay);
            }

            @Override
            public void showPage(String id) {
                LauncherWindow.this.showPage(id);
            }

            @Override
            public void openFolder(Path dir) {
                open(dir);
            }

            @Override
            public void repair() {
                startPlay(true);
            }

            @Override
            public void checkUpdates() {
                LauncherWindow.this.checkUpdates();
            }

            @Override
            public javax.swing.JPopupMenu moreMenu() {
                return LauncherWindow.this.moreMenu();
            }
        }, newsPage, pageIo);
        page.add(dashboard.build());
        page.add(Box.createVerticalGlue());
        // Minecraft news for the news card (cached copies offline)
        newsPage.refresh();
        return page;
    }

    /**
     * Start page banner: logo, wordmark and slogan and the start controls (version, split play
     * button with more start options, game folder, settings, more).
     */
    private JComponent hero() {
        HeroPanel hero = new HeroPanel(null);
        hero.setLayout(new BoxLayout(hero, BoxLayout.Y_AXIS));
        hero.setBorder(BorderFactory.createEmptyBorder(18, 24, 14, 24));
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
        logo.setPreferredSize(new Dimension(64, 64));
        logo.setMaximumSize(new Dimension(64, 64));
        JComponent wordmark = new JComponent() {
            @Override
            protected void paintComponent(java.awt.Graphics g0) {
                java.awt.Graphics2D g = (java.awt.Graphics2D) g0.create();
                g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setFont(Theme.display(52f, Font.BOLD | Font.ITALIC, 48f));
                java.awt.FontMetrics fm = g.getFontMetrics();
                String t = "LUMORA";
                int x = (getWidth() - fm.stringWidth(t)) / 2;
                int y = fm.getAscent() - 4;
                if (Theme.pixelStyle) {
                    // like the logo of the game's Lumora main menu: white pixel letters on a stepped accent shadow
                    y = getHeight() - fm.getDescent() - 8;
                    g.setColor(Theme.alpha(Color.BLACK, 140));
                    g.drawString(t, x + 8, y + 8);
                    g.setColor(Theme.ACCENT_DEEP);
                    g.drawString(t, x + 4, y + 4);
                    g.setColor(Color.WHITE);
                    g.drawString(t, x, y);
                    g.dispose();
                    return;
                }
                g.setColor(Theme.alpha(Color.BLACK, 120));
                g.drawString(t, x + 3, y + 4);
                g.setPaint(new java.awt.GradientPaint(x, 0, Color.WHITE, x + fm.stringWidth(t), 0, Theme.ACCENT_HOVER));
                g.drawString(t, x, y);
                g.dispose();
            }
        };
        int wordmarkHeight = Theme.pixelStyle ? 70 : 58;
        wordmark.setPreferredSize(new Dimension(420, wordmarkHeight));
        wordmark.setMaximumSize(new Dimension(Integer.MAX_VALUE, wordmarkHeight));
        for (JComponent c : List.of(logo, wordmark)) {
            c.setAlignmentX(Component.CENTER_ALIGNMENT);
            hero.add(c);
        }
        hero.add(Box.createVerticalStrut(12));

        // start controls
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        controls.setOpaque(false);
        JPanel split = new JPanel(new BorderLayout(0, 0));
        split.setOpaque(false);
        heroPlay = new PlayButton(Lang.t("play.start_game")).split();
        heroPlay.setPreferredSize(new Dimension(290, 56));
        heroPlay.addActionListener(e -> startPlay(false));
        heroArrow = new PlayButton.Arrow();
        heroArrow.setToolTipText(Lang.t("play.choose_version"));
        heroArrow.addActionListener(e -> showVersionMenu());
        split.add(heroPlay, BorderLayout.CENTER);
        split.add(heroArrow, BorderLayout.EAST);
        controls.add(split);
        controls.setAlignmentX(Component.CENTER_ALIGNMENT);
        controls.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
        hero.add(controls);
        hero.add(Box.createVerticalStrut(14));

        JPanel info = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        info.setOpaque(false);
        heroOverline = label(" ", 12, Font.BOLD, Theme.ACCENT_TEXT);
        info.add(heroOverline);
        info.setAlignmentX(Component.CENTER_ALIGNMENT);
        info.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        hero.add(info);
        // Quick Play: the first servers of the selected version's server list, one click joins (filled by refreshQuickPlay)
        quickPlayBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 4)) {
            @Override
            protected void paintComponent(java.awt.Graphics g0) {
                java.awt.Graphics2D g = (java.awt.Graphics2D) g0.create();
                g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(Theme.alpha(Theme.BG, 190));
                Theme.fillRound(g, 0, 0, getWidth(), getHeight(), Theme.arc(10), Theme.arc(10));
                g.setColor(Theme.alpha(Theme.BORDER, 220));
                Theme.drawRound(g, 0, 0, getWidth() - 1, getHeight() - 1, Theme.arc(10), Theme.arc(10));
                g.dispose();
            }
        };
        quickPlayBar.setOpaque(false);
        quickPlayBar.setVisible(false);
        JPanel quickPlayRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        quickPlayRow.setOpaque(false);
        quickPlayRow.add(quickPlayBar);
        quickPlayRow.setAlignmentX(Component.CENTER_ALIGNMENT);
        hero.add(Box.createVerticalStrut(10));
        hero.add(quickPlayRow);
        heroPanel = hero;
        hero.setMaximumSize(new Dimension(Integer.MAX_VALUE, hero.getPreferredSize().height));
        return hero;
    }

    /**
     * Fills the Quick Play bar with the first five servers of the selected version's server list (its game folder's
     * servers.dat, with the favicons Minecraft stored); hidden without servers or when switched off in the settings.
     */
    private void refreshQuickPlay() {
        if (quickPlayBar == null) {
            return;
        }
        int gen = ++quickPlayGeneration;
        // the selected instance's server list (an own instance has its own servers.dat)
        java.nio.file.Path game = ctx.layout(ctx.selectedInstance()).gameDir();
        boolean on = ctx.config.quickPlay;
        pageIo.submit(() -> {
            List<dev.lumora.launcher.core.library.InstanceLibrary.Server> servers = on
                    ? dev.lumora.launcher.core.library.InstanceLibrary.servers(game).stream().limit(5).toList() : List.of();
            List<javax.swing.Icon> icons = new ArrayList<>();
            for (var s : servers) {
                javax.swing.Icon icon = Icons.of(Icons.Kind.SERVER, 18, Theme.MUTED);
                byte[] png = s.iconPng();
                if (png != null) {
                    try {
                        java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(png));
                        if (img != null) {
                            icon = new javax.swing.ImageIcon(img.getScaledInstance(20, 20, java.awt.Image.SCALE_SMOOTH));
                        }
                    } catch (IOException | RuntimeException ignored) {
                        // keep the server symbol
                    }
                }
                icons.add(icon);
            }
            SwingUtilities.invokeLater(() -> {
                if (gen != quickPlayGeneration) {
                    return;
                }
                quickPlayBar.removeAll();
                if (!servers.isEmpty()) {
                    JLabel title = label(Lang.t("home.quick_play"), 11, Font.BOLD, Theme.ACCENT_HOVER);
                    title.setFont(Theme.display(16f, Font.BOLD, 11f));
                    title.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 8));
                    quickPlayBar.add(title);
                    for (int i = 0; i < servers.size(); i++) {
                        var s = servers.get(i);
                        String name = s.name().length() > 24 ? s.name().substring(0, 23) + "…" : s.name();
                        JButton b = new JButton(name, icons.get(i));
                        b.putClientProperty("JButton.buttonType", "borderless");
                        b.setFont(Theme.font(Font.BOLD, 12.5f));
                        b.setForeground(Theme.TEXT);
                        b.setIconTextGap(7);
                        b.setFocusable(false);
                        b.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
                        b.setToolTipText(Lang.t("home.quick_play.tip", s.address()));
                        b.addActionListener(e -> launch(new LaunchCommandBuilder.QuickPlay(LaunchCommandBuilder.QuickPlay.Kind.MULTIPLAYER, s.address()), false));
                        quickPlayBar.add(b);
                    }
                }
                quickPlayBar.setVisible(!servers.isEmpty());
                heroPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, heroPanel.getPreferredSize().height));
                heroPanel.revalidate();
                heroPanel.repaint();
            });
        });
    }

    /** Instance choice of the arrow next to "Spiel starten" (see InstancePicker). */
    private void showVersionMenu() {
        dev.lumora.launcher.app.pages.InstancePicker.show(this, pageIo, heroArrow, false);
    }

    @Override
    public java.awt.Image versionArt(String family, Runnable loaded) {
        return newsPage == null ? null : newsPage.updateArt(family, loaded);
    }

    @Override
    public JComponent folderChooser(dev.lumora.launcher.core.instance.Instance builtIn) {
        if (builtIn == null || !builtIn.builtIn) {
            return null;
        }
        return instanceChooser(builtIn.id, builtIn.kind() == dev.lumora.launcher.core.instance.InstanceKind.LUMORA);
    }

    /**
     * Game folder of a built-in instance: the standard folder (.minecraft, shared with Vanilla and the other versions)
     * or an own folder, and (Lumora) whether its Lumora settings are shared by all versions.
     */
    private JPanel instanceChooser(String id, boolean lumora) {
        JPanel box = column();
        box.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel modes = new JPanel(new GridLayout(1, 2, 6, 0));
        modes.setOpaque(false);
        modes.setAlignmentX(Component.LEFT_ALIGNMENT);
        javax.swing.JToggleButton standard = new javax.swing.JToggleButton(Lang.t("instance.default"), Icons.of(Icons.Kind.FOLDER, 14, Theme.TEXT));
        javax.swing.JToggleButton own = new javax.swing.JToggleButton(Lang.t("instance.own"), Icons.of(Icons.Kind.ARCHIVE, 14, Theme.TEXT));
        standard.setToolTipText(Lang.t("instance.default.tip"));
        own.setToolTipText(Lang.t("instance.own.tip"));
        javax.swing.ButtonGroup g = new javax.swing.ButtonGroup();
        for (javax.swing.JToggleButton b : List.of(standard, own)) {
            b.setFont(Theme.font(Font.BOLD, 12.5f));
            b.setFocusable(false);
            b.setIconTextGap(7);
            g.add(b);
            modes.add(b);
        }
        modes.setMaximumSize(new Dimension(520, 34));
        box.add(modes);
        box.add(Box.createVerticalStrut(6));
        javax.swing.JCheckBox shared = new javax.swing.JCheckBox(Lang.t("instance.share_settings"));
        shared.setOpaque(false);
        shared.setFocusable(false);
        shared.setFont(Theme.font(Font.PLAIN, 12.5f));
        shared.setForeground(Theme.TEXT);
        shared.setAlignmentX(Component.LEFT_ALIGNMENT);
        shared.setVisible(lumora);
        box.add(shared);
        box.add(Box.createVerticalStrut(2));
        JLabel where = label("", 11.5f, Font.PLAIN, Theme.MUTED);
        where.setAlignmentX(Component.LEFT_ALIGNMENT);
        box.add(where);
        Runnable sync = () -> {
            dev.lumora.launcher.core.InstanceLayout l = ctx.layout(id);
            standard.setSelected(!l.own());
            own.setSelected(l.own());
            shared.setSelected(l.sharedSettings());
            where.setText(Lang.t("instance.game_dir", l.gameDir()));
            where.setToolTipText(Lang.t("instance.config_dir", l.configDir()));
        };
        standard.addActionListener(e -> {
            setInstanceChoice(id, false, shared.isSelected());
            sync.run();
        });
        own.addActionListener(e -> {
            setInstanceChoice(id, true, shared.isSelected());
            sync.run();
        });
        shared.addActionListener(e -> {
            setInstanceChoice(id, own.isSelected(), shared.isSelected());
            sync.run();
        });
        sync.run();
        return box;
    }

    /** Stores a built-in instance's game folder choice; its Lumora settings come along into a new, empty settings folder. */
    private void setInstanceChoice(String id, boolean own, boolean shared) {
        dev.lumora.launcher.core.InstanceLayout before = ctx.layout(id);
        if (before.own() == own && before.sharedSettings() == shared) {
            return;
        }
        if (ctx.play.runningGame().isPresent()) {
            error(Lang.t("instance.game_dir.title"), Lang.t("instance.game_running"));
            return;
        }
        ctx.config.instances.put(id, new dev.lumora.launcher.core.config.LauncherConfig.InstanceChoice(own, shared));
        saveConfigQuietly();
        dev.lumora.launcher.core.InstanceLayout after = ctx.layout(id);
        worker.submit(() -> {
            try {
                List<String> copied = dev.lumora.launcher.core.InstanceLayout.carrySettings(before, after);
                Log.info("Instance " + id + ": " + (own ? "own" : "standard") + (shared ? ", shared settings" : ", own settings")
                        + (copied.isEmpty() ? "" : ", copied " + copied));
            } catch (IOException e) {
                Log.warn("Could not carry the Lumora settings of " + id + ": " + e.getMessage());
            }
        });
        refreshVersionInfo(ctx.selectedProfile());
    }


    private javax.swing.JPopupMenu moreMenu() {
        javax.swing.JPopupMenu m = new javax.swing.JPopupMenu();
        Object[][] items = {
                {Lang.t("settings.debug.logs.title"), Icons.Kind.TERMINAL, "logs"},
                {Lang.t("settings.debug.diag.title"), Icons.Kind.PULSE, "diagnostics"},
                {Lang.t("nav.crashes"), Icons.Kind.ALERT, "crashes"},
                {Lang.t("nav.performance"), Icons.Kind.CHART, "performance"}};
        for (Object[] it : items) {
            javax.swing.JMenuItem mi = new javax.swing.JMenuItem((String) it[0], Icons.of((Icons.Kind) it[1], 14, Theme.TEXT));
            mi.addActionListener(e -> showPage((String) it[2]));
            m.add(mi);
        }
        m.addSeparator();
        javax.swing.JMenuItem upd = new javax.swing.JMenuItem(Lang.t("update.check_menu"), Icons.of(Icons.Kind.REFRESH, 14, Theme.TEXT));
        upd.addActionListener(e -> checkUpdates());
        m.add(upd);
        return m;
    }

    private dev.lumora.launcher.core.profile.Flavor currentFlavor() {
        return dev.lumora.launcher.core.profile.Flavor.of(ctx.config.selectedFlavor);
    }

    private void setFlavor(dev.lumora.launcher.core.profile.Flavor fl) {
        if (fl != currentFlavor() || ctx.config.selectedInstance != null) {
            ctx.config.selectedFlavor = fl.id();
            // the version menu chooses a built-in instance
            ctx.config.selectedInstance = null;
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
                SwingUtilities.invokeLater(() -> nav.setDot("news", unseen > 0));
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
                    nav.setDot("news", false);
                });
            } catch (RuntimeException e) {
                Log.warn("News seen: " + e.getMessage());
            }
        });
    }

    /**
     * Discord shows "Im Launcher" or what the game does: "Lumora 26.3" / "auf eineip.de" (from the status file the Lumora
     * game writes; older clients and Vanilla: only the version). Always on with Lumora's own application ID (AppIds).
     */
    private void updateDiscord() {
        dev.lumora.launcher.core.integrations.DiscordPresence.Activity a;
        java.util.Optional<dev.lumora.launcher.core.launch.GameProcess> running = ctx.play.runningGame();
        if (running.isPresent()) {
            ProfileCatalog.Profile p = ctx.selectedProfile();
            lastGameStatus = gameStatus(running.get());
            a = lastGameStatus != null
                    ? lastGameStatus.activity("Lumora", p.minecraftVersion, gameStarted, ctx.config.discordShowServer)
                    : new dev.lumora.launcher.core.integrations.DiscordPresence.Activity(Lang.t("discord.playing", p.minecraftVersion),
                    currentFlavor().label(), gameStarted);
        } else {
            lastGameStatus = null;
            a = new dev.lumora.launcher.core.integrations.DiscordPresence.Activity(Lang.t("discord.in_launcher"), "Lumora " + BuildInfo.VERSION, launcherStarted);
        }
        discord.update(withSmallPicture(a));
    }

    /** Small picture: the server's icon on a server (if the server may be shown), else the head of the signed-in account. */
    private dev.lumora.launcher.core.integrations.DiscordPresence.Activity withSmallPicture(dev.lumora.launcher.core.integrations.DiscordPresence.Activity a) {
        String server = lastGameStatus == null ? null : lastGameStatus.shownServer(ctx.config.discordShowServer);
        if (server != null) {
            return a.withSmall(dev.lumora.launcher.core.integrations.PresenceImages.server(server), server);
        }
        MinecraftSession s = ctx.play.storedSession();
        if (s == null) {
            return a;
        }
        String head = dev.lumora.launcher.core.integrations.PresenceImages.head(s.uuid());
        return head == null ? a : a.withSmall(head, Lang.t("discord.signed_in", s.username()));
    }

    private static dev.lumora.launcher.core.integrations.GameStatus gameStatus(dev.lumora.launcher.core.launch.GameProcess game) {
        java.nio.file.Path file = game.statusFile();
        return file == null ? null : dev.lumora.launcher.core.integrations.GameStatus.read(file).orElse(null);
    }

    /** Timer: updates Discord when the running game moved between menu, world and server. */
    private void pollGameStatus() {
        dev.lumora.launcher.core.integrations.GameStatus now = ctx.play.runningGame().map(LauncherWindow::gameStatus).orElse(null);
        if (!java.util.Objects.equals(now, lastGameStatus)) {
            updateDiscord();
        }
    }

    /** True if the profile ships the Lumora client mod (Lumora menus, HUD editor, addons). */
    private void refreshVersionInfo(ProfileCatalog.Profile p) {
        dev.lumora.launcher.core.profile.Flavor fl = currentFlavor();
        dev.lumora.launcher.core.instance.Instance own = ctx.selectedInstance();
        updateInstanceButton();
        if (heroOverline != null) {
            if (!own.builtIn) {
                // an own instance: its name, loader and version
                heroOverline.setText(own.name.toUpperCase(Lang.locale()) + "  ·  " + own.kind().label().toUpperCase(Lang.locale())
                        + "  ·  MINECRAFT " + own.minecraftVersion);
            } else {
                heroOverline.setText("MINECRAFT " + p.minecraftVersion + "  ·  " + switch (fl) {
                    case LUMORA -> "LUMORA CLIENT";
                    case VANILLA -> "VANILLA";
                });
            }
        }
        if (dashboard != null && !dashboard.downloading()) {
            dashboard.idle();
        }
        refreshQuickPlay();
    }

    /** Play bar: name of the selected instance (shortened), full name, loader and version as tooltip. */
    private void updateInstanceButton() {
        if (instanceButton == null) {
            return;
        }
        dev.lumora.launcher.core.instance.Instance i = ctx.selectedInstance();
        String n = i.name.length() > 22 ? i.name.substring(0, 21) + "…" : i.name;
        instanceButton.setText(n);
        instanceButton.setToolTipText(Lang.t("picker.tooltip", i.name, i.minecraftVersion, i.kind().label()));
    }

    @Override
    public void selectProfile(String id) {
        if (syncingProfile) {
            return;
        }
        syncingProfile = true;
        try {
            boolean changed = !id.equals(ctx.config.selectedProfile);
            if (changed) {
                // choosing another version picks its built-in instance (Lumora or Vanilla)
                ctx.config.selectedInstance = null;
            }
            ctx.config.selectedProfile = id;
            if (ctx.config.rememberPatch) {
                // "Ausgewählten Patch merken": this patch is picked again next time its family is chosen
                String family = ctx.catalog.profile(id).map(p -> VersionGrid.family(p.minecraftVersion)).orElse(null);
                if (family != null && !id.equals(ctx.config.lastPatch.put(family, id))) {
                    changed = true;
                }
            }
            if (changed) {
                saveConfigQuietly();
            }
            ctx.catalog.profile(id).ifPresent(this::refreshVersionInfo);
        } finally {
            syncingProfile = false;
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
        accountName = label(Lang.t("account.signed_out"), 14, Font.BOLD, Theme.TEXT);
        accountHint = label(Lang.t("account.sign_in"), 12, Font.PLAIN, Theme.ACCENT_HOVER);
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
        statusLabel = label(Lang.t("status.ready"), 13, Font.PLAIN, Theme.MUTED);
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
        // the selected instance; a click opens the instance choice above the bar
        instanceButton = new JButton(" ", Icons.of(Icons.Kind.CHEVRON_DOWN, 13, Theme.MUTED));
        instanceButton.setHorizontalTextPosition(javax.swing.SwingConstants.LEFT);
        instanceButton.setIconTextGap(10);
        instanceButton.setFont(Theme.font(Font.BOLD, 13f));
        instanceButton.setFocusable(false);
        instanceButton.setPreferredSize(new Dimension(200, 40));
        instanceButton.addActionListener(e -> dev.lumora.launcher.app.pages.InstancePicker.show(this, pageIo, instanceButton, true));
        updateInstanceButton();
        stopButton = new JButton(Lang.t("play.stop"));
        stopButton.setVisible(false);
        stopButton.setPreferredSize(new Dimension(100, 40));
        stopButton.addActionListener(e -> ctx.play.runningGame().ifPresent(g -> worker.submit(() -> g.stop(java.time.Duration.ofSeconds(15)))));
        playButton = new PlayButton(Lang.t("play.play"));
        playButton.addActionListener(e -> startPlay(false));
        right.add(instanceButton);
        right.add(stopButton);
        right.add(playButton);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private void refreshAccount() {
        MinecraftSession s = ctx.play.storedSession();
        if (s != null) {
            accountName.setText(s.username());
            accountHint.setText(ctx.accounts.list().size() > 1 ? Lang.t("account.switch") : Lang.t("account.manage"));
            avatar.setPlayer(s.username(), s.uuid());
        } else {
            accountName.setText(Lang.t("account.signed_out"));
            accountHint.setText(Lang.t("account.sign_in_microsoft"));
            avatar.setPlayer(null, null);
        }
        updateDiscord(); // the account head in Discord
        if (social != null) {
            social.apply(); // new account: reconnect; same account after a new sign-in: retry a rejected login
        }
    }

    private void signOut() {
        if (JOptionPane.showConfirmDialog(frame, Lang.t("account.sign_out.confirm"),
                BuildInfo.NAME, JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            ctx.play.signOut();
        } catch (IOException ex) {
            error(Lang.t("account.sign_out.failed"), ex.getMessage());
        }
        refreshAccount();
    }

    private void startLogin() {
        if (!busy.compareAndSet(false, true)) {
            return;
        }
        LoginFlow flow = new LoginFlow(frame, ctx);
        currentLogin = flow;
        statusLabel.setText(Lang.t("account.signing_in_browser"));
        worker.submit(() -> {
            try {
                MinecraftSession s = ctx.play.signIn(ctx.config, flow.interactive(), flow.cancelled());
                SwingUtilities.invokeLater(() -> statusLabel.setText(Lang.t("account.signed_in_as", s.username())));
            } catch (AuthException e) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText(Lang.t("status.ready"));
                    if (e.reason() != AuthException.Reason.CANCELLED) {
                        error(Lang.t("account.sign_in.failed"), e.getMessage());
                    }
                });
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> error(Lang.t("account.sign_in.failed"), e.getMessage()));
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
            JOptionPane.showMessageDialog(frame, Lang.t("play.already_running"), BuildInfo.NAME, JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (!busy.compareAndSet(false, true)) {
            return;
        }
        ProfileCatalog.Profile profile = ctx.selectedProfile();
        dev.lumora.launcher.core.profile.Flavor flavor = currentFlavor();
        dev.lumora.launcher.core.instance.Instance instance = ctx.selectedInstance();
        LoginFlow flow = new LoginFlow(frame, ctx);
        currentLogin = flow;
        setPlay(Lang.t("play.preparing"), false);
        progress.setVisible(true);
        worker.submit(() -> {
            try {
                if (instance.builtIn) {
                    ctx.play.play(profile, flavor, ctx.config, PlayService.Mode.ACCOUNT, repair,
                            flow.interactive(), flow.cancelled(), null, quickPlay, playListener());
                } else {
                    // an own instance: its loader, folder, Java and memory
                    ctx.play.playInstance(instance, ctx.layout(instance), ctx.catalog, ctx.config, PlayService.Mode.ACCOUNT, repair,
                            flow.interactive(), flow.cancelled(), quickPlay, playListener());
                }
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
                        error(Lang.t("account.sign_in.failed"), e.getMessage());
                    }
                });
            } catch (Exception e) {
                Log.error("Start failed", e);
                SwingUtilities.invokeLater(() -> {
                    flow.closeDialogs();
                    resetPlayButton();
                    error(Lang.t("play.failed"), Lang.t("play.failed_details", e.getMessage(), Log.file()));
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
                        case INSTALLING -> {
                            setPlay(Lang.t("play.installing"), false);
                            dashboard.download(message, -1);
                        }
                        case LOGGING_IN -> setPlay(Lang.t("play.signing_in"), false);
                        case LAUNCHING -> {
                            setPlay(Lang.t("play.launching"), false);
                            dashboard.idle();
                        }
                        case PLAYING -> {
                            setPlay(ctx.config.multipleInstances ? Lang.t("play.play") : Lang.t("play.running"), ctx.config.multipleInstances);
                            setButtonProgress(-1);
                            progress.setVisible(false);
                            stopButton.setVisible(true);
                            gameStarted = java.time.Instant.now().getEpochSecond();
                        }
                        case IDLE, ERROR -> {
                            resetPlayButton();
                            dashboard.idle();
                        }
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
                SwingUtilities.invokeLater(dashboard::refreshRecent);
                // servers added in the game appear in Quick Play
                SwingUtilities.invokeLater(LauncherWindow.this::refreshQuickPlay);
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
                    if (dashboard.downloading()) {
                        dashboard.download(detail != null && !detail.isEmpty() ? detail : statusLabel.getText(), fraction);
                    }
                });
            }
        };
    }

    private void setPlay(String text, boolean enabled) {
        playButton.setText(text);
        playButton.setEnabled(enabled);
        if (heroPlay != null) {
            heroPlay.setText(text.equals(Lang.t("play.play")) ? Lang.t("play.start_game") : text);
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
        setPlay(running && !ctx.config.multipleInstances ? Lang.t("play.running") : Lang.t("play.play"), !running || ctx.config.multipleInstances);
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
    public void selectInstance(dev.lumora.launcher.core.instance.Instance instance) {
        dev.lumora.launcher.core.instance.InstanceManager.select(ctx.config, instance);
        saveConfigQuietly();
        if (instance.builtIn) {
            selectProfile(instance.profileId);
        }
        refreshVersionInfo(ctx.selectedProfile());
    }

    @Override
    public void openInstance(dev.lumora.launcher.core.instance.Instance instance) {
        showPage("instances");
        instancesPage.open(instance);
    }

    @Override
    public void discover(dev.lumora.launcher.core.platform.ProjectType type, dev.lumora.launcher.core.instance.Instance target) {
        showPage("browser");
        browserPage.show(type, target);
    }

    @Override
    public void instancesChanged() {
        if (instancesPage != null && instancesPage.isShowing()) {
            instancesPage.refresh();
        }
        refreshVersionInfo(ctx.selectedProfile());
    }

    @Override
    public void browse(String url) {
        if (url == null || !url.startsWith("https://")) {
            return;
        }
        try {
            Desktop.getDesktop().browse(java.net.URI.create(url));
        } catch (IOException | UnsupportedOperationException | IllegalArgumentException e) {
            error(Lang.t("error.open_link"), url + "\n" + e.getMessage());
        }
    }

    @Override
    public void saveConfig() {
        saveConfigQuietly();
    }

    @Override
    public void settingsChanged() {
        if (settingsPage != null) {
            settingsPage.reload();
        }
    }

    @Override
    public void checkUpdates() {
        worker.submit(this::interactiveUpdateCheck);
    }

    @Override
    public void extensionsChanged() {
        // extensions are listed in the library and the settings
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
                Path game = file.getParent().getParent();
                // own instance: its folder name; the standard folder is shared, there it is the selected version
                String folder = game.getFileName().toString();
                String profileId = ctx.catalog.profile(folder).isPresent() || folder.endsWith("-vanilla") ? folder
                        : currentFlavor().instanceId(ctx.selectedProfile().id);
                try {
                    report = CrashReports.load(game, profileId, file);
                } catch (IOException | RuntimeException e) {
                    Log.warn("Could not read crash report " + file + ": " + e.getMessage());
                }
            }
            CrashReport r = report;
            SwingUtilities.invokeLater(() -> {
                frame.setState(JFrame.NORMAL);
                StringBuilder sb = new StringBuilder();
                sb.append(r != null ? r.description() : Lang.t("crash.dialog.exited")).append(" (Code ").append(exit.code()).append(")\n");
                if (r != null && r.exception() != null) {
                    sb.append('\n').append(r.exception()).append('\n');
                }
                if (r != null && !r.suspects().isEmpty()) {
                    CrashReport.Suspect s = r.suspects().getFirst();
                    sb.append("\n" + Lang.t("crash.dialog.cause")).append(" ").append(s.name()).append("\n(").append(s.reason()).append(")\n");
                }
                for (CrashAnalyzer.Hint h : r != null ? r.hints() : hints) {
                    sb.append("\n" + Lang.t("crash.dialog.tip")).append(" ").append(h.title()).append(": ").append(h.advice());
                }
                JTextArea text = new JTextArea(sb.toString().strip());
                text.setEditable(false);
                text.setLineWrap(true);
                text.setWrapStyleWord(true);
                text.setColumns(56);
                text.setOpaque(false);
                Object[] options = {Lang.t("crash.dialog.view"), Lang.t("crash.dialog.restart"), Lang.t("common.close")};
                int choice = JOptionPane.showOptionDialog(frame, text, Lang.t("crash.dialog.title"), JOptionPane.DEFAULT_OPTION,
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
                info.put(p.id, Lang.t("home.recent.played", PlayStats.format(s.playTime())));
            }
        }
        playInfo = info;
    }

    /** Looks for a newer launcher on GitHub Releases; {@code interactive} also reports "up to date" and errors. */
    void checkLauncherUpdate(boolean interactive) {
        pageIo.submit(() -> {
            try {
                dev.lumora.launcher.core.update.LauncherUpdate u = new dev.lumora.launcher.core.update.LauncherUpdate();
                var r = u.check(BuildInfo.VERSION, ctx.config.prereleases);
                SwingUtilities.invokeLater(() -> {
                    if (r.isPresent()) {
                        offerLauncherUpdate(u, r.get());
                    } else if (interactive) {
                        JOptionPane.showMessageDialog(frame, Lang.t("update.launcher.up_to_date", BuildInfo.VERSION),
                                BuildInfo.NAME, JOptionPane.INFORMATION_MESSAGE);
                    }
                });
            } catch (IOException | RuntimeException e) {
                Log.info("Launcher update check: " + e.getMessage());
                if (interactive) {
                    SwingUtilities.invokeLater(() -> error(Lang.t("update.check_failed"), e.getMessage()));
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
        JTextArea text = new JTextArea(Lang.t("update.launcher.available", r.version(), BuildInfo.VERSION)
                + (notes.isEmpty() ? "" : "\n\n" + notes)
                + (managedCopy ? "\n\n" + Lang.t("update.launcher.managed", portable.get())
                : portable.isPresent() ? "\n\n" + Lang.t("update.launcher.portable")
                : directStartOff ? "\n\n" + Lang.t("update.launcher.direct_off")
                : "\n\n" + Lang.t("update.launcher.manual")));
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
        Object[] options = portable.isPresent() ? new Object[]{Lang.t("update.launcher.now"), Lang.t("common.later")} : new Object[]{Lang.t("update.launcher.open_release"), Lang.t("common.later")};
        int choice = JOptionPane.showOptionDialog(frame, notesPane, Lang.t("update.launcher.title"), JOptionPane.DEFAULT_OPTION,
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
        statusLabel.setText(Lang.t("update.launcher.downloading", r.version()));
        pageIo.submit(() -> {
            try {
                java.nio.file.Path file = u.download(r, ctx.paths.root().resolve("cache").resolve("launcher-update"));
                SwingUtilities.invokeLater(() -> {
                    if (ctx.play.runningGame().isPresent()) {
                        JOptionPane.showMessageDialog(frame, Lang.t("update.launcher.quit_game"), Lang.t("update.launcher.title"), JOptionPane.INFORMATION_MESSAGE);
                        statusLabel.setText(Lang.t("status.ready"));
                        return;
                    }
                    try {
                        dev.lumora.launcher.core.update.LauncherUpdate.install(file, portable.get());
                        if (instance != null) {
                            instance.close(); // the new launcher must not find this one still running
                        }
                        new ProcessBuilder(portable.get().toString()).start();
                        quit(true);
                    } catch (IOException ex) {
                        error(Lang.t("update.launcher.failed"), ex.getMessage());
                    }
                });
            } catch (IOException | RuntimeException e) {
                Log.warn("Launcher update failed: " + e.getMessage());
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText(Lang.t("status.ready"));
                    error(Lang.t("update.launcher.failed"), e.getMessage());
                });
            }
        });
    }

    private void backgroundUpdateCheck() {
        List<UpdateChecker.Update> updates = new UpdateChecker(ctx.downloader).check(ctx.catalog, ctx.selectedProfile());
        SwingUtilities.invokeLater(() -> dashboard.updates(updates.size()));
    }

    private void interactiveUpdateCheck() {
        List<UpdateChecker.Update> updates = new UpdateChecker(ctx.downloader).check(ctx.catalog, ctx.selectedProfile());
        SwingUtilities.invokeLater(() -> {
            if (updates.isEmpty()) {
                JOptionPane.showMessageDialog(frame, Lang.t("update.components.none"), BuildInfo.NAME, JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            StringBuilder sb = new StringBuilder(Lang.t("update.components.list") + "\n\n");
            for (UpdateChecker.Update u : updates) {
                sb.append("• ").append(u.current().name).append(": ").append(u.current().version).append(" → ").append(u.candidate().version).append('\n');
            }
            sb.append("\n" + Lang.t("update.components.apply"));
            if (JOptionPane.showConfirmDialog(frame, sb.toString(), BuildInfo.NAME, JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                try {
                    UpdateChecker.writeOverride(new LauncherPaths(ctx.defaultRoot).lockfileOverride(),
                            updates.stream().map(UpdateChecker.Update::candidate).toList());
                    JOptionPane.showMessageDialog(frame, Lang.t("update.components.queued"), BuildInfo.NAME, JOptionPane.INFORMATION_MESSAGE);
                } catch (IOException ex) {
                    error(Lang.t("update.failed"), ex.getMessage());
                }
            }
        });
    }

    // ================================================================ Settings

    // ================================================================ Diagnostics & logs

    private Component diagnosticsPage() {
        JPanel page = column();
        page.add(pageTitle(Lang.t("settings.debug.diag.title"), Lang.t("diag.subtitle")));
        page.add(Box.createVerticalStrut(18));
        Card card = new Card(new BorderLayout());
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        diagList.setOpaque(false);
        diagList.setLayout(new BoxLayout(diagList, BoxLayout.Y_AXIS));
        diagList.add(label(Lang.t("diag.running"), 13, Font.PLAIN, Theme.MUTED));
        card.add(diagList, BorderLayout.CENTER);
        page.add(card);
        page.add(Box.createVerticalStrut(16));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        buttons.setOpaque(false);
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttons.add(actionButton(Lang.t("diag.check_again"), Icons.Kind.REFRESH, this::runSystemCheck));
        buttons.add(Box.createHorizontalStrut(8));
        buttons.add(actionButton(Lang.t("diag.repair"), Icons.Kind.CHECK, () -> startPlay(true)));
        buttons.add(Box.createHorizontalStrut(8));
        buttons.add(actionButton(Lang.t("diag.log_folder"), Icons.Kind.FOLDER, () -> open(ctx.defaultRoot.resolve("logs"))));
        buttons.add(Box.createHorizontalStrut(8));
        buttons.add(actionButton(Lang.t("instance.game_dir.title"), Icons.Kind.FOLDER, () -> open(ctx.gameDir(currentFlavor().instanceId(ctx.selectedProfile().id)))));
        buttons.add(Box.createHorizontalStrut(8));
        buttons.add(actionButton(Lang.t("diag.game_diagnostics"), Icons.Kind.FOLDER, () -> open(ctx.paths.diagnostics())));
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
        page.add(pageTitle(Lang.t("settings.debug.logs.title"), Lang.t("logs.subtitle")), BorderLayout.NORTH);
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
        JLabel t = label(Theme.pixelSafe(title), 28, Font.BOLD, Theme.TEXT);
        t.setFont(Theme.display(32f, Font.BOLD, 28f));
        p.add(t);
        p.add(Box.createVerticalStrut(4));
        p.add(label(subtitle, 14, Font.PLAIN, Theme.MUTED));
        return p;
    }

    private static JComponent sectionTitle(String title, String hint) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel t = label(Theme.pixelSafe(title), 17, Font.BOLD, Theme.TEXT);
        t.setFont(Theme.display(16f, Font.BOLD, 17f));
        p.add(t);
        if (hint != null) {
            p.add(Box.createHorizontalStrut(12));
            p.add(label(hint, 13, Font.PLAIN, Theme.MUTED));
        }
        p.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        return p;
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
                tray.notify(Lang.t("tray.still_running"), Lang.t("tray.still_running.text"));
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
            int r = JOptionPane.showConfirmDialog(frame, Lang.t("quit.game_running"),
                    BuildInfo.NAME, JOptionPane.YES_NO_OPTION);
            if (r != JOptionPane.YES_OPTION) {
                return;
            }
        }
        if (currentLogin != null) {
            currentLogin.cancel();
        }
        social.shutdown();
        gameStatusTimer.stop();
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
            error(Lang.t("error.open_folder"), dir + "\n" + e.getMessage());
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
