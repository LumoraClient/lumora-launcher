package dev.lumora.launcher.app.pages;

import dev.lumora.launcher.app.ui.Card;
import dev.lumora.launcher.app.ui.Icons;
import dev.lumora.launcher.app.ui.Theme;
import dev.lumora.launcher.app.ui.Ui;
import dev.lumora.launcher.core.launch.LaunchCommandBuilder;
import dev.lumora.launcher.core.library.ServerPing;
import dev.lumora.launcher.core.servers.ServerDirectory;
import dev.lumora.launcher.core.servers.ServerHistory;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.text.NumberFormat;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * "Server" page: well-known public servers and the player's own, ranked by the players online right now. Everything
 * live comes from the servers themselves (Minecraft Server List Ping: players, icon, version); the curve and the weekly
 * trend come from this launcher's own measurements ({@link ServerHistory}); ratings are the player's own.
 */
public final class ServersPage extends JPanel {
    private static final long REFRESH_MS = 3 * 60_000;
    private static final int[] MODE_COLORS = {0x3B82F6, 0xEC4899, 0xF5B342, 0x3DD68C, 0x22C3A6, 0xA78BFA, 0xF2555A, 0xFBBF24};

    private enum State { LOADING, ONLINE, OFFLINE }

    private static final class Live {
        volatile State state = State.LOADING;
        volatile int online;
        volatile int max;
        volatile String version = "";
        volatile long ping;
        volatile Image icon;
    }

    private final PageHost host;
    private final ServerDirectory directory;
    private final ServerHistory history;
    private final ExecutorService pinger = Executors.newFixedThreadPool(6, r -> {
        Thread t = new Thread(r, "server-list-ping");
        t.setDaemon(true);
        return t;
    });
    private final Map<String, Live> live = new ConcurrentHashMap<>();
    private final JPanel list = Ui.column();
    private final JTextField search = new JTextField();
    private final JComboBox<String> mode = new JComboBox<>();
    private final JComboBox<String> language = new JComboBox<>();
    private final JLabel updated = Ui.label(" ", 12.5f, Font.PLAIN, Theme.MUTED);
    private final Timer auto;
    private long lastRefresh;
    private boolean shown;

    public ServersPage(PageHost host) {
        super(new BorderLayout(0, 14));
        this.host = host;
        setOpaque(false);
        java.nio.file.Path dir = host.ctx().paths.root().resolve("servers");
        this.directory = new ServerDirectory(dir);
        this.history = new ServerHistory(dir);

        JPanel head = Ui.column();
        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);
        titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleRow.add(Ui.pageTitle("Server", "Beliebte Server und deine eigenen, sortiert nach Spielern online. Zahlen kommen live vom jeweiligen Server."),
                BorderLayout.WEST);
        head.add(titleRow);
        head.add(Box.createVerticalStrut(14));
        head.add(toolbar());
        add(head, BorderLayout.NORTH);

        Card table = new Card(new BorderLayout()).fill(Theme.SURFACE).padding(6, 6, 6, 6);
        table.add(new HeaderRow(), BorderLayout.NORTH);
        table.add(Ui.scroll(list), BorderLayout.CENTER);
        add(table, BorderLayout.CENTER);
        auto = new Timer((int) REFRESH_MS, e -> {
            if (shown) {
                refresh();
            }
        });
        auto.start();
        rebuild();
    }

    /** Called by the window when the page becomes visible or hidden. */
    public void setShown(boolean s) {
        shown = s;
        if (s && System.currentTimeMillis() - lastRefresh > REFRESH_MS - 5000) {
            refresh();
        }
    }

    private JComponent toolbar() {
        JPanel bar = new JPanel(new BorderLayout(10, 0));
        bar.setOpaque(false);
        bar.setAlignmentX(Component.LEFT_ALIGNMENT);
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        search.putClientProperty("JTextField.placeholderText", "Server suchen…");
        search.putClientProperty("JTextField.leadingIcon", Icons.of(Icons.Kind.SEARCH, 16, Theme.MUTED));
        search.setPreferredSize(new Dimension(260, 36));
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                rebuild();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                rebuild();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                rebuild();
            }
        });
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);
        left.setBorder(BorderFactory.createEmptyBorder(0, -8, 0, 0));
        left.add(search);
        TreeSet<String> modes = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        TreeSet<String> langs = new TreeSet<>();
        for (ServerDirectory.Entry e : ServerDirectory.bundled()) {
            modes.addAll(e.modes());
            langs.addAll(e.languages());
        }
        mode.addItem("Alle Spielmodi");
        modes.forEach(mode::addItem);
        language.addItem("Alle Sprachen");
        langs.forEach(language::addItem);
        mode.addActionListener(e -> rebuild());
        language.addActionListener(e -> rebuild());
        mode.setPreferredSize(new Dimension(170, 36));
        language.setPreferredSize(new Dimension(140, 36));
        left.add(mode);
        left.add(language);
        bar.add(left, BorderLayout.CENTER);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        right.add(updated);
        JButton add = new JButton("Eigener Server…", Icons.of(Icons.Kind.PLUS, 16, Theme.TEXT));
        add.setFocusable(false);
        add.addActionListener(e -> addServer());
        right.add(add);
        JButton reload = new JButton(Icons.of(Icons.Kind.REFRESH, 16, Theme.TEXT));
        reload.setToolTipText("Jetzt aktualisieren");
        reload.setFocusable(false);
        reload.addActionListener(e -> refresh());
        right.add(reload);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private void addServer() {
        String a = JOptionPane.showInputDialog(host.frame(), "Adresse des Servers (z. B. play.example.de oder play.example.de:25566):",
                "Eigener Server", JOptionPane.PLAIN_MESSAGE);
        if (a == null || a.isBlank()) {
            return;
        }
        String addr = a.strip();
        if (!ServerDirectory.validAddress(addr)) {
            host.error("Ungültige Adresse", "„" + addr + "“ ist keine gültige Server-Adresse.");
            return;
        }
        if (!host.ctx().config.customServers.contains(addr)) {
            host.ctx().config.customServers.add(addr);
            host.saveConfig();
        }
        rebuild();
        ping(addr, new AtomicInteger(1));
    }

    private List<ServerDirectory.Entry> entries() {
        return directory.all(host.ctx().config.customServers);
    }

    /** Pings every server (6 at a time); rows update as answers arrive. */
    public void refresh() {
        lastRefresh = System.currentTimeMillis();
        List<ServerDirectory.Entry> all = entries();
        AtomicInteger left = new AtomicInteger(all.size());
        updated.setText("Aktualisiere…");
        for (ServerDirectory.Entry e : all) {
            ping(e.address(), left);
        }
    }

    private void ping(String address, AtomicInteger left) {
        pinger.execute(() -> {
            Live l = live.computeIfAbsent(address.toLowerCase(Locale.ROOT), k -> new Live());
            try {
                ServerPing.Status s = ServerPing.ping(address);
                l.online = s.online();
                l.max = s.max();
                l.version = s.version() == null ? "" : s.version();
                l.ping = s.pingMs();
                if (s.favicon() != null && l.icon == null) {
                    BufferedImage img = ImageIO.read(new ByteArrayInputStream(s.favicon()));
                    if (img != null) {
                        l.icon = img;
                    }
                }
                l.state = State.ONLINE;
                history.record(address, s.online(), System.currentTimeMillis() / 1000);
            } catch (IOException | RuntimeException e) {
                l.state = State.OFFLINE;
            }
            if (left.decrementAndGet() <= 0) {
                history.save();
                SwingUtilities.invokeLater(() -> updated.setText("Aktualisiert " + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))));
            }
            SwingUtilities.invokeLater(this::rebuild);
        });
    }

    private Live live(String address) {
        return live.computeIfAbsent(address.toLowerCase(Locale.ROOT), k -> new Live());
    }

    private void rebuild() {
        String q = search.getText().strip().toLowerCase(Locale.ROOT);
        String m = mode.getSelectedIndex() <= 0 ? null : (String) mode.getSelectedItem();
        String lang = language.getSelectedIndex() <= 0 ? null : (String) language.getSelectedItem();
        List<ServerDirectory.Entry> shownList = new ArrayList<>();
        for (ServerDirectory.Entry e : entries()) {
            if (!q.isEmpty() && !e.name().toLowerCase(Locale.ROOT).contains(q) && !e.address().toLowerCase(Locale.ROOT).contains(q)) {
                continue;
            }
            if (m != null && e.modes().stream().noneMatch(x -> x.equalsIgnoreCase(m))) {
                continue;
            }
            if (lang != null && !e.languages().contains(lang)) {
                continue;
            }
            shownList.add(e);
        }
        shownList.sort(Comparator.<ServerDirectory.Entry>comparingInt(e -> {
            Live l = live(e.address());
            return l.state == State.ONLINE ? -l.online : l.state == State.LOADING ? 1 : 2;
        }).thenComparing(e -> e.name().toLowerCase(Locale.ROOT)));
        list.removeAll();
        int rank = 0;
        for (ServerDirectory.Entry e : shownList) {
            rank++;
            list.add(new ServerRow(e, rank));
        }
        if (shownList.isEmpty()) {
            JLabel none = Ui.label("Keine Server für diesen Filter.", 14, Font.PLAIN, Theme.MUTED);
            none.setBorder(BorderFactory.createEmptyBorder(20, 16, 20, 16));
            list.add(none);
        }
        list.revalidate();
        list.repaint();
    }

    // ------------------------------------------------------------------ rows

    /** Column x positions for a row width; narrow windows drop the modes and the curve. */
    private record Cols(int rank, int icon, int name, int modes, int rating, int lang, int spark, int online, int week, int play,
                        boolean showModes, boolean showSpark) {
        static Cols of(int w) {
            boolean modes = w >= 980;
            boolean spark = w >= 860;
            int play = w - 46;
            int week = play - 64;
            int online = week - 92;
            int spark0 = spark ? online - 118 : online;
            int lang = spark0 - 70;
            int rating = lang - 128;
            int modes0 = modes ? rating - 230 : rating;
            return new Cols(10, 44, 92, modes0, rating, lang, spark0, online, week, play, modes, spark);
        }
    }

    private static void quality(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    }

    private static final class HeaderRow extends JComponent {
        HeaderRow() {
            setPreferredSize(new Dimension(600, 30));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            quality(g);
            Cols c = Cols.of(getWidth());
            g.setFont(Theme.font(Font.BOLD, 11.5f));
            g.setColor(Theme.MUTED);
            int y = 19;
            g.drawString("#", c.rank + 8, y);
            g.drawString("SERVER", c.name, y);
            if (c.showModes) {
                g.drawString("SPIELMODI", c.modes, y);
            }
            g.drawString("BEWERTUNG", c.rating, y);
            g.drawString("SPR.", c.lang, y);
            if (c.showSpark) {
                g.drawString("24 STD.", c.spark, y);
            }
            FontMetrics fm = g.getFontMetrics();
            g.drawString("ONLINE", c.week - 12 - fm.stringWidth("ONLINE"), y);
            g.drawString("WOCHE", c.week + 4, y);
            g.setColor(Theme.BORDER);
            g.fillRect(0, getHeight() - 1, getWidth(), 1);
            g.dispose();
        }
    }

    private final class ServerRow extends JComponent {
        private final ServerDirectory.Entry e;
        private final int rank;
        private boolean hover;
        private int hoverStar;
        private Rectangle starsBox = new Rectangle();
        private Rectangle playBox = new Rectangle();

        ServerRow(ServerDirectory.Entry e, int rank) {
            this.e = e;
            this.rank = rank;
            setPreferredSize(new Dimension(600, 56));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
            setAlignmentX(Component.LEFT_ALIGNMENT);
            MouseAdapter m = new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent ev) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent ev) {
                    hover = false;
                    hoverStar = 0;
                    repaint();
                }

                @Override
                public void mouseMoved(MouseEvent ev) {
                    int hs = starsBox.contains(ev.getPoint()) ? 1 + Math.min(4, (ev.getX() - starsBox.x) / 16) : 0;
                    boolean overPlay = playBox.contains(ev.getPoint());
                    setCursor(hs > 0 || overPlay ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
                    setToolTipText(overPlay ? playTip() : hs > 0 ? hs + " Sterne vergeben (nur für dich)" : null);
                    if (hs != hoverStar) {
                        hoverStar = hs;
                        repaint();
                    }
                }

                @Override
                public void mouseClicked(MouseEvent ev) {
                    if (ev.getButton() != MouseEvent.BUTTON1) {
                        return;
                    }
                    if (starsBox.contains(ev.getPoint())) {
                        int s = 1 + Math.min(4, (ev.getX() - starsBox.x) / 16);
                        directory.rate(e.address(), directory.rating(e.address()) == s ? 0 : s);
                        repaint();
                    } else if (playBox.contains(ev.getPoint())) {
                        play();
                    }
                }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
            if (e.custom()) {
                JPopupMenu menu = new JPopupMenu();
                JMenuItem remove = new JMenuItem("Aus der Liste entfernen");
                remove.addActionListener(a -> {
                    host.ctx().config.customServers.remove(e.address());
                    host.saveConfig();
                    rebuild();
                });
                menu.add(remove);
                setComponentPopupMenu(menu);
            }
        }

        private String playTip() {
            Live l = live(e.address());
            String profile = host.ctx().selectedProfile().displayName;
            return "<html>Mit " + profile + " beitreten" + (l.version.isEmpty() ? "" : "<br>Server meldet: " + esc(l.version))
                    + (l.state == State.ONLINE ? "<br>Ping " + l.ping + " ms" : "") + "</html>";
        }

        private void play() {
            if (host.gameRunning()) {
                host.error("Minecraft läuft bereits", "Beende das laufende Spiel, um einem anderen Server beizutreten.");
                return;
            }
            host.launch(new LaunchCommandBuilder.QuickPlay(LaunchCommandBuilder.QuickPlay.Kind.MULTIPLAYER, e.address()), false);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            quality(g);
            int w = getWidth();
            int h = getHeight();
            Cols c = Cols.of(w);
            Live l = live(e.address());
            if (hover) {
                g.setColor(Theme.alpha(Theme.SURFACE_2, 200));
                g.fillRoundRect(0, 2, w, h - 4, 12, 12);
            }
            g.setColor(Theme.alpha(Theme.BORDER, 120));
            g.fillRect(8, h - 1, w - 16, 1);
            int cy = h / 2;
            // rank
            g.setFont(Theme.font(Font.BOLD, 15f));
            g.setColor(rank <= 3 ? new Color(0xF5A623) : Theme.MUTED);
            String r = Integer.toString(rank);
            g.drawString(r, c.rank + 14 - g.getFontMetrics().stringWidth(r) / 2, cy + 5);
            // icon
            int is = 38;
            int ix = c.icon;
            int iy = cy - is / 2;
            if (l.icon != null) {
                java.awt.Shape clip = g.getClip();
                g.clip(new java.awt.geom.RoundRectangle2D.Double(ix, iy, is, is, 10, 10));
                g.drawImage(l.icon, ix, iy, is, is, null);
                g.setClip(clip);
            } else {
                g.setColor(Theme.SURFACE_2);
                g.fillRoundRect(ix, iy, is, is, 10, 10);
                g.setColor(Theme.MUTED);
                g.setFont(Theme.font(Font.BOLD, 16f));
                String ch = e.name().substring(0, 1).toUpperCase(Locale.ROOT);
                g.drawString(ch, ix + (is - g.getFontMetrics().stringWidth(ch)) / 2, cy + 6);
            }
            // name + address
            int nameW = c.modes - c.name - 14;
            g.setFont(Theme.font(Font.BOLD, 15.5f));
            g.setColor(Theme.TEXT);
            g.drawString(fit(g, e.name(), nameW), c.name, cy - 2);
            g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            g.setColor(Theme.MUTED);
            g.drawString(fit(g, e.address() + (e.custom() ? "  · eigener Server" : ""), nameW), c.name, cy + 15);
            // modes
            if (c.showModes) {
                int x = c.modes;
                g.setFont(Theme.font(Font.PLAIN, 12.5f));
                int shownModes = 0;
                for (String mo : e.modes()) {
                    int ww = g.getFontMetrics().stringWidth(mo) + 18;
                    if (shownModes == 2 || x + ww > c.rating - 34) {
                        break;
                    }
                    g.setColor(new Color(MODE_COLORS[Math.floorMod(mo.toLowerCase(Locale.ROOT).hashCode(), MODE_COLORS.length)]));
                    g.fillOval(x, cy - 4, 7, 7);
                    g.setColor(Theme.TEXT);
                    g.drawString(mo, x + 11, cy + 4);
                    x += ww;
                    shownModes++;
                }
                if (e.modes().size() > shownModes) {
                    g.setColor(Theme.MUTED);
                    g.drawString("+" + (e.modes().size() - shownModes), x, cy + 4);
                }
            }
            // rating (own)
            int own = directory.rating(e.address());
            int stars = hoverStar > 0 ? hoverStar : own;
            starsBox = new Rectangle(c.rating, cy - 14, 80, 16);
            for (int i = 0; i < 5; i++) {
                star(g, c.rating + i * 16 + 7, cy - 6, 6.5, i < stars ? (hoverStar > 0 ? Theme.ACCENT_HOVER : new Color(0xF5B342)) : Theme.alpha(Theme.MUTED, 90));
            }
            g.setFont(Theme.font(Font.PLAIN, 11.5f));
            g.setColor(Theme.MUTED);
            g.drawString(own > 0 ? "Deine Bewertung" : "Bewerten", c.rating, cy + 16);
            // language
            g.setFont(Theme.font(Font.PLAIN, 12.5f));
            g.setColor(Theme.MUTED);
            g.drawString(e.languages().isEmpty() ? "–" : String.join(" · ", e.languages()), c.lang, cy + 4);
            // curve (own measurements, 24 h)
            long now = System.currentTimeMillis() / 1000;
            if (c.showSpark) {
                spark(g, history.sparkline(e.address(), now, 24 * 3600, 24), c.spark, cy - 12, 104, 24);
            }
            // online
            String on;
            Color oc;
            switch (l.state) {
                case ONLINE -> {
                    on = NumberFormat.getIntegerInstance(Locale.GERMANY).format(l.online);
                    oc = Theme.TEXT;
                }
                case OFFLINE -> {
                    on = "Offline";
                    oc = Theme.FAIL;
                }
                default -> {
                    on = "…";
                    oc = Theme.MUTED;
                }
            }
            g.setFont(Theme.font(Font.BOLD, l.state == State.ONLINE ? 17f : 13.5f));
            g.setColor(oc);
            g.drawString(on, c.week - 12 - g.getFontMetrics().stringWidth(on), cy + 6);
            // week
            Double wk = l.state == State.ONLINE ? history.weekChange(e.address(), l.online, now) : null;
            g.setFont(Theme.font(Font.PLAIN, 13f));
            if (wk == null) {
                g.setColor(Theme.MUTED);
                g.drawString("–", c.week + 14, cy + 5);
            } else {
                long p = Math.round(wk);
                g.setColor(p > 0 ? Theme.OK : p < 0 ? Theme.FAIL : Theme.MUTED);
                g.drawString((p > 0 ? "+" : "") + p + "%", c.week + 4, cy + 5);
            }
            // play
            playBox = new Rectangle(c.play, cy - 17, 34, 34);
            g.setColor(l.state == State.OFFLINE ? Theme.SURFACE_2 : Theme.ACCENT_PRESSED);
            g.fillRoundRect(playBox.x, playBox.y, 34, 34, 8, 8);
            Icons.of(Icons.Kind.PLAY, 18, Color.WHITE).paintIcon(this, g, playBox.x + 8, playBox.y + 8);
            g.dispose();
        }
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String fit(Graphics2D g, String s, int max) {
        FontMetrics fm = g.getFontMetrics();
        if (fm.stringWidth(s) <= max) {
            return s;
        }
        String t = s;
        while (t.length() > 1 && fm.stringWidth(t + "…") > max) {
            t = t.substring(0, t.length() - 1);
        }
        return t + "…";
    }

    private static void star(Graphics2D g, double cx, double cy, double r, Color c) {
        Path2D p = new Path2D.Double();
        for (int i = 0; i < 10; i++) {
            double a = -Math.PI / 2 + i * Math.PI / 5;
            double rr = i % 2 == 0 ? r : r * 0.45;
            double x = cx + Math.cos(a) * rr;
            double y = cy + Math.sin(a) * rr;
            if (i == 0) {
                p.moveTo(x, y);
            } else {
                p.lineTo(x, y);
            }
        }
        p.closePath();
        g.setColor(c);
        g.fill(p);
    }

    /** Line of the measured values (gaps skipped); "–" with fewer than two points. */
    private static void spark(Graphics2D g, int[] v, int x, int y, int w, int h) {
        int max = 1;
        int min = Integer.MAX_VALUE;
        int points = 0;
        for (int a : v) {
            if (a >= 0) {
                max = Math.max(max, a);
                min = Math.min(min, a);
                points++;
            }
        }
        if (points < 2) {
            g.setFont(Theme.font(Font.PLAIN, 11.5f));
            g.setColor(Theme.alpha(Theme.MUTED, 160));
            g.drawString(points == 1 ? "erste Messung" : "–", x, y + h / 2 + 4);
            return;
        }
        double range = Math.max(1, max - min);
        Path2D p = new Path2D.Double();
        boolean started = false;
        for (int i = 0; i < v.length; i++) {
            if (v[i] < 0) {
                continue;
            }
            double px = x + (double) i / (v.length - 1) * w;
            double py = y + h - (v[i] - min) / range * h;
            if (!started) {
                p.moveTo(px, py);
                started = true;
            } else {
                p.lineTo(px, py);
            }
        }
        g.setColor(new Color(0x7DA7D9));
        g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(p);
    }
}
