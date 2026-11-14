package dev.lumora.launcher.app.pages;

import dev.lumora.launcher.app.ui.Card;
import dev.lumora.launcher.app.ui.Icons;
import dev.lumora.launcher.app.ui.Theme;
import dev.lumora.launcher.app.ui.Ui;
import dev.lumora.launcher.core.library.InstanceLibrary;
import dev.lumora.launcher.core.launch.LaunchCommandBuilder;
import dev.lumora.launcher.core.library.ServerPing;
import dev.lumora.launcher.core.library.WorldBackup;
import dev.lumora.launcher.core.profile.ProfileCatalog;
import dev.lumora.launcher.core.stats.BenchmarkHistory;
import dev.lumora.launcher.core.stats.PlayStats;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

/**
 * "Bibliothek" page of the selected version: play time, worlds (with their icons), the server list with live
 * status (players, ping, version), a screenshot gallery and the installed resource packs. Read-only.
 */
public final class LibraryPage extends JPanel {
    private static final int SHOTS = 12;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm", Locale.GERMANY)
            .withZone(ZoneId.systemDefault());

    private final PageHost host;
    private final ExecutorService io;
    private final JPanel versionBar = Ui.row(0);
    private final JPanel content = Ui.column();
    private int generation;

    private record Data(java.util.List<BenchmarkHistory.Run> benchmarks, PlayStats.Entry stats, List<InstanceLibrary.World> worlds, List<InstanceLibrary.Server> servers,
                        List<InstanceLibrary.Screenshot> shots, List<InstanceLibrary.ResourcePack> packs) {
    }

    public LibraryPage(PageHost host, ExecutorService io) {
        super(new BorderLayout(0, 14));
        this.host = host;
        this.io = io;
        setOpaque(false);
        JPanel head = Ui.column();
        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);
        titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleRow.add(Ui.pageTitle("Bibliothek", "Deine Welten, Server, Screenshots und Ressourcenpakete – getrennt nach Version."), BorderLayout.WEST);
        JPanel aw = new JPanel(new BorderLayout());
        aw.setOpaque(false);
        JButton reload = new JButton("Aktualisieren", Icons.of(Icons.Kind.REFRESH, 16, Theme.TEXT));
        reload.setFocusable(false);
        reload.addActionListener(e -> refresh());
        aw.add(reload, BorderLayout.SOUTH);
        titleRow.add(aw, BorderLayout.EAST);
        head.add(titleRow);
        head.add(Box.createVerticalStrut(14));
        head.add(versionBar);
        add(head, BorderLayout.NORTH);
        content.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(Ui.scroll(content), BorderLayout.CENTER);
    }

    private ProfileCatalog.Profile profile() {
        return host.ctx().selectedProfile();
    }

    public void refresh() {
        versionBar.removeAll();
        List<ProfileCatalog.Profile> ps = host.ctx().catalog.profiles();
        versionBar.add(Ui.segmented(ps.stream().map(p -> p.id).toList(),
                ps.stream().map(p -> "Minecraft " + p.minecraftVersion).toList(), profile().id, id -> {
                    host.selectProfile(id);
                    refresh();
                }));
        versionBar.revalidate();
        int gen = ++generation;
        ProfileCatalog.Profile p = profile();
        Path instance = host.ctx().gameDir(p.id);
        io.submit(() -> {
            Data d = new Data(BenchmarkHistory.load(host.ctx().paths.diagnostics(), p.id), host.ctx().play.stats().get(p.id), InstanceLibrary.worlds(instance), InstanceLibrary.servers(instance),
                    InstanceLibrary.screenshots(instance), InstanceLibrary.resourcePacks(instance));
            SwingUtilities.invokeLater(() -> {
                if (gen == generation) {
                    render(p, instance, d, gen);
                }
            });
        });
    }

    private void render(ProfileCatalog.Profile p, Path instance, Data d, int gen) {
        content.removeAll();
        // --- stats
        JPanel stats = new JPanel(new GridLayout(1, 4, 14, 0));
        stats.setOpaque(false);
        stats.setAlignmentX(Component.LEFT_ALIGNMENT);
        stats.add(stat(Icons.Kind.CLOCK, "Spielzeit", PlayStats.format(d.stats().playTime())));
        stats.add(stat(Icons.Kind.PLAY, "Starts", Long.toString(d.stats().launches)));
        stats.add(stat(Icons.Kind.REFRESH, "Zuletzt gespielt", PlayStats.ago(d.stats().lastPlayedInstant(), Instant.now())));
        stats.add(stat(Icons.Kind.CUBE, "Welten", Integer.toString(d.worlds().size())));
        stats.setMaximumSize(new Dimension(Integer.MAX_VALUE, 84));
        content.add(stats);
        content.add(Box.createVerticalStrut(22));

        // --- performance (F9 benchmark)
        content.add(header("Leistung", d.benchmarks().isEmpty() ? null : d.benchmarks().size() + " Benchmark-Läufe mit F9", null));
        if (d.benchmarks().isEmpty()) {
            content.add(empty("Noch keine Messungen. Drücke im Spiel F9: Lumora misst 30 Sekunden lang die Bildrate (Durchschnitt, 1 %-Lows, "
                    + "Frametimes) und zeigt den Verlauf hier als Diagramm."));
        } else {
            Card chartCard = new Card(new BorderLayout(0, 8));
            chartCard.padding(14, 16, 12, 16);
            chartCard.setAlignmentX(Component.LEFT_ALIGNMENT);
            dev.lumora.launcher.app.ui.LineChart chart = new dev.lumora.launcher.app.ui.LineChart();
            java.util.List<BenchmarkHistory.Run> runs = d.benchmarks();
            DateTimeFormatter shortDate = DateTimeFormatter.ofPattern("dd.MM. HH:mm", Locale.GERMANY).withZone(ZoneId.systemDefault());
            chart.setData(runs.stream().map(r -> shortDate.format(r.time())).toList(), java.util.List.of(
                    new dev.lumora.launcher.app.ui.LineChart.Series("Ø FPS", Theme.ACCENT, runs.stream().mapToDouble(BenchmarkHistory.Run::avgFps).toArray()),
                    new dev.lumora.launcher.app.ui.LineChart.Series("1 %-Low", new Color(0xF2A541), runs.stream().mapToDouble(BenchmarkHistory.Run::onePercentLowFps).toArray())), "FPS");
            chartCard.add(chart, BorderLayout.CENTER);
            BenchmarkHistory.Run last = runs.getLast();
            String trend = "";
            if (runs.size() > 1) {
                double prev = runs.get(runs.size() - 2).avgFps();
                double diff = prev > 0 ? (last.avgFps() - prev) / prev * 100 : 0;
                trend = String.format(Locale.GERMANY, "  ·  %+.0f %% zum Lauf davor", diff);
            }
            chartCard.add(Ui.label(String.format(Locale.GERMANY, "Letzter Lauf %s: Ø %.0f FPS, 1 %%-Low %.0f FPS, p99 %.1f ms%s%s",
                    DATE.format(last.time()), last.avgFps(), last.onePercentLowFps(), last.p99Ms(), trend,
                    last.renderer() != null ? "  ·  " + last.renderer() : ""), 12.5f, Font.PLAIN, Theme.MUTED), BorderLayout.SOUTH);
            content.add(chartCard);
        }
        content.add(Box.createVerticalStrut(22));

        // --- worlds
        content.add(header("Welten", d.worlds().isEmpty() ? null : d.worlds().size() + " gespeichert", instance.resolve("saves")));
        if (d.worlds().isEmpty()) {
            content.add(empty("Noch keine Welten in Minecraft " + p.minecraftVersion + ". Starte das Spiel und lege im Einzelspieler eine an."));
        }
        for (InstanceLibrary.World w : d.worlds()) {
            content.add(worldRow(w));
            content.add(Box.createVerticalStrut(8));
        }
        content.add(Box.createVerticalStrut(14));

        // --- servers
        content.add(header("Server", d.servers().isEmpty() ? null : "Live-Status wie im Mehrspieler-Menü", null));
        if (d.servers().isEmpty()) {
            content.add(empty("Keine Server gespeichert. Server, die du im Spiel unter „Mehrspieler“ hinzufügst, erscheinen hier mit Spielerzahl und Ping."));
        }
        for (InstanceLibrary.Server s : d.servers()) {
            content.add(serverRow(s, gen));
            content.add(Box.createVerticalStrut(8));
        }
        content.add(Box.createVerticalStrut(14));

        // --- screenshots
        content.add(header("Screenshots", d.shots().isEmpty() ? null
                : d.shots().size() + " gesamt" + (d.shots().size() > SHOTS ? ", die neuesten " + SHOTS : ""), instance.resolve("screenshots")));
        if (d.shots().isEmpty()) {
            content.add(empty("Noch keine Screenshots. Im Spiel mit F2 aufnehmen."));
        } else {
            JPanel grid = new JPanel(new GridLayout(0, 4, 12, 12));
            grid.setOpaque(false);
            grid.setAlignmentX(Component.LEFT_ALIGNMENT);
            for (InstanceLibrary.Screenshot s : d.shots().subList(0, Math.min(SHOTS, d.shots().size()))) {
                grid.add(shotTile(s, gen));
            }
            content.add(grid);
        }
        content.add(Box.createVerticalStrut(22));

        // --- resource packs
        content.add(header("Ressourcenpakete", d.packs().isEmpty() ? null : d.packs().size() + " installiert", instance.resolve("resourcepacks")));
        if (d.packs().isEmpty()) {
            content.add(empty("Keine eigenen Ressourcenpakete. Das eingebaute „Lumora Theme“ ist immer dabei."));
        } else {
            JPanel chips = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 8));
            chips.setOpaque(false);
            chips.setAlignmentX(Component.LEFT_ALIGNMENT);
            for (InstanceLibrary.ResourcePack r : d.packs()) {
                chips.add(Ui.chip(r.name() + (r.folder() ? "  (Ordner)" : "")));
            }
            content.add(chips);
        }
        content.revalidate();
        content.repaint();
    }

    private JComponent header(String title, String hint, Path folder) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(Ui.sectionTitle(title, hint), BorderLayout.WEST);
        if (folder != null) {
            JLabel open = Ui.label("Ordner öffnen", 12.5f, Font.BOLD, Theme.ACCENT_HOVER);
            open.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            open.addMouseListener(click(() -> host.openFolder(folder)));
            open.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
            row.add(open, BorderLayout.EAST);
        }
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        return row;
    }

    private static JComponent empty(String text) {
        Card c = new Card(new BorderLayout());
        c.padding(14, 16, 14, 16);
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        c.add(Ui.wrapText(text, 13f, Theme.MUTED), BorderLayout.CENTER);
        return c;
    }

    private static JComponent stat(Icons.Kind icon, String title, String value) {
        Card c = new Card(new BorderLayout(12, 0));
        c.padding(14, 16, 14, 16);
        JLabel i = new JLabel(Icons.of(icon, 22, Theme.ACCENT_HOVER));
        i.setVerticalAlignment(JLabel.TOP);
        c.add(i, BorderLayout.WEST);
        JPanel t = Ui.column();
        t.add(Ui.label(title.toUpperCase(Locale.GERMANY), 11, Font.BOLD, Theme.MUTED));
        t.add(Box.createVerticalStrut(4));
        t.add(Ui.label(value, 15, Font.BOLD, Theme.TEXT));
        c.add(t, BorderLayout.CENTER);
        return c;
    }

    private JComponent worldRow(InstanceLibrary.World w) {
        Card c = new Card(new BorderLayout(14, 0));
        c.padding(10, 12, 10, 16);
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        ImageIcon icon = Ui.icon(w.icon(), 48, 48, false);
        c.add(icon != null ? Ui.imageTile(icon, 48, 48) : Ui.letterTile(w.name(), 48, new Color(0x3DD68C)), BorderLayout.WEST);
        JPanel t = Ui.column();
        JPanel head = Ui.row(8);
        head.add(Ui.label(w.name(), 15, Font.BOLD, Theme.TEXT));
        head.add(Ui.chip(switch (w.mode()) {
            case SURVIVAL -> "Überleben";
            case CREATIVE -> "Kreativ";
            case ADVENTURE -> "Abenteuer";
            case SPECTATOR -> "Zuschauer";
            case UNKNOWN -> "Welt";
        }));
        if (w.hardcore()) {
            head.add(Ui.chip("Hardcore", Theme.FAIL, new Color(0xFF9EA1)));
        }
        head.setMaximumSize(new Dimension(Integer.MAX_VALUE, head.getPreferredSize().height));
        t.add(head);
        t.add(Box.createVerticalStrut(3));
        t.add(Ui.label("Zuletzt gespielt " + (w.lastPlayed() != null ? DATE.format(w.lastPlayed())
                + " (" + PlayStats.ago(w.lastPlayed(), Instant.now()) + ")" : "unbekannt") + "  ·  Ordner „" + w.folder() + "“",
                12.5f, Font.PLAIN, Theme.MUTED));
        c.add(t, BorderLayout.CENTER);
        JButton open = new JButton(Icons.of(Icons.Kind.FOLDER, 16, Theme.TEXT));
        open.setToolTipText("Weltordner öffnen");
        open.setFocusable(false);
        open.addActionListener(e -> host.openFolder(w.dir()));
        JButton backup = new JButton("Backup", Icons.of(Icons.Kind.ARCHIVE, 15, Theme.TEXT));
        backup.setToolTipText("Welt als ZIP sichern (Ordner „backups“ dieser Version)");
        backup.setFocusable(false);
        backup.addActionListener(e -> backup(w, backup));
        JButton play = new JButton("Spielen", Icons.of(Icons.Kind.PLAY, 15, Color.WHITE));
        if (!Theme.pixelStyle) {
            play.putClientProperty("JButton.buttonType", "roundRect");
        }
        play.setBackground(Theme.ACCENT_PRESSED);
        play.setForeground(Color.WHITE);
        play.setToolTipText("Minecraft starten und diese Welt direkt öffnen");
        play.setFocusable(false);
        play.addActionListener(e -> host.launch(new LaunchCommandBuilder.QuickPlay(LaunchCommandBuilder.QuickPlay.Kind.SINGLEPLAYER, w.folder()), false));
        JPanel ow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 6, 0));
        ow.setOpaque(false);
        ow.add(open);
        ow.add(backup);
        ow.add(play);
        JPanel owWrap = new JPanel(new java.awt.GridBagLayout());
        owWrap.setOpaque(false);
        owWrap.add(ow);
        ow = owWrap;
        c.add(ow, BorderLayout.EAST);
        return c;
    }

    private JComponent serverRow(InstanceLibrary.Server s, int gen) {
        Card c = new Card(new BorderLayout(14, 0));
        c.padding(10, 12, 10, 16);
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel pic = new JPanel(new BorderLayout());
        pic.setOpaque(false);
        pic.add(Ui.letterTile(s.name(), 48, Theme.ACCENT), BorderLayout.CENTER);
        c.add(pic, BorderLayout.WEST);
        JPanel t = Ui.column();
        t.add(Ui.label(s.name(), 15, Font.BOLD, Theme.TEXT));
        t.add(Box.createVerticalStrut(3));
        JLabel motd = Ui.label(s.address(), 12.5f, Font.PLAIN, Theme.MUTED);
        t.add(motd);
        c.add(t, BorderLayout.CENTER);
        JLabel status = Ui.label("● prüfe…", 13, Font.BOLD, Theme.MUTED);
        status.setHorizontalAlignment(JLabel.RIGHT);
        JButton join = new JButton("Beitreten", Icons.of(Icons.Kind.PLAY, 15, Color.WHITE));
        if (!Theme.pixelStyle) {
            join.putClientProperty("JButton.buttonType", "roundRect");
        }
        join.setBackground(Theme.ACCENT_PRESSED);
        join.setForeground(Color.WHITE);
        join.setToolTipText("Minecraft starten und direkt mit " + s.address() + " verbinden");
        join.setFocusable(false);
        join.addActionListener(e -> host.launch(new LaunchCommandBuilder.QuickPlay(LaunchCommandBuilder.QuickPlay.Kind.MULTIPLAYER, s.address()), false));
        JPanel sw = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 12, 0));
        sw.setOpaque(false);
        sw.add(status);
        sw.add(join);
        JPanel swWrap = new JPanel(new java.awt.GridBagLayout());
        swWrap.setOpaque(false);
        swWrap.add(sw);
        sw = swWrap;
        c.add(sw, BorderLayout.EAST);
        io.submit(() -> {
            ServerPing.Status st;
            String err;
            try {
                st = ServerPing.ping(s.address());
                err = null;
            } catch (IOException | RuntimeException e) {
                st = null;
                err = e.getMessage();
            }
            ServerPing.Status result = st;
            String error = err;
            SwingUtilities.invokeLater(() -> {
                if (gen != generation) {
                    return;
                }
                if (result == null) {
                    status.setText("● nicht erreichbar");
                    status.setForeground(Theme.FAIL);
                    status.setToolTipText(error);
                    return;
                }
                status.setText("● " + (result.online() >= 0 ? result.online() + " / " + result.max() + " Spieler" : "online")
                        + "  ·  " + result.pingMs() + " ms");
                status.setForeground(result.pingMs() < 150 ? Theme.OK : Theme.WARN);
                String m = result.motd().replaceAll("\\s+", " ");
                motd.setText(s.address() + "  ·  " + result.version() + (m.isBlank() ? "" : "  ·  " + (m.length() > 70 ? m.substring(0, 67) + "…" : m)));
                ImageIcon fav = Ui.icon(result.favicon(), 48);
                if (fav != null) {
                    pic.removeAll();
                    pic.add(Ui.imageTile(fav, 48, 48), BorderLayout.CENTER);
                    pic.revalidate();
                    pic.repaint();
                }
            });
        });
        return c;
    }

    private void backup(InstanceLibrary.World w, JButton button) {
        if (host.gameRunning()) {
            host.error("Backup nicht möglich", "Minecraft läuft gerade. Beende das Spiel, damit die Welt vollständig gespeichert ist.");
            return;
        }
        button.setEnabled(false);
        button.setText("Sichere…");
        Path backups = WorldBackup.backupsDir(host.ctx().gameDir(profile().id));
        io.submit(() -> {
            try {
                Path zip = WorldBackup.backup(w.dir(), backups, Instant.now());
                SwingUtilities.invokeLater(() -> {
                    button.setText("Gesichert ✓");
                    javax.swing.JOptionPane.showMessageDialog(host.frame(), "„" + w.name() + "“ gesichert:\n" + zip.getFileName()
                            + " (" + Ui.size(zip.toFile().length()) + ")\n\nOrdner: " + backups, "Backup erstellt",
                            javax.swing.JOptionPane.INFORMATION_MESSAGE);
                });
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> {
                    button.setEnabled(true);
                    button.setText("Backup");
                    host.error("Backup fehlgeschlagen", e.getMessage());
                });
            }
        });
    }

    private JComponent shotTile(InstanceLibrary.Screenshot s, int gen) {
        int w = 220;
        int h = 124;
        JPanel tile = new JPanel(new BorderLayout());
        tile.setOpaque(false);
        tile.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        tile.setToolTipText(s.file().getFileName() + "  ·  " + DATE.format(s.taken()) + "  ·  " + Ui.size(s.size()));
        tile.add(Ui.imageTile(null, w, h), BorderLayout.CENTER);
        tile.addMouseListener(click(() -> {
            try {
                Desktop.getDesktop().open(s.file().toFile());
            } catch (IOException | UnsupportedOperationException e) {
                host.error("Screenshot kann nicht geöffnet werden", s.file() + "\n" + e.getMessage());
            }
        }));
        JLabel date = Ui.label(DATE.format(s.taken()), 11.5f, Font.PLAIN, Theme.MUTED);
        date.setBorder(BorderFactory.createEmptyBorder(4, 2, 0, 0));
        tile.add(date, BorderLayout.SOUTH);
        io.submit(() -> {
            ImageIcon thumb = Ui.icon(s.file(), w, h, false);
            SwingUtilities.invokeLater(() -> {
                if (gen == generation && thumb != null) {
                    tile.remove(0);
                    tile.add(Ui.imageTile(thumb, w, h), BorderLayout.CENTER, 0);
                    tile.revalidate();
                    tile.repaint();
                }
            });
        });
        return tile;
    }

    private static MouseAdapter click(Runnable r) {
        return new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                r.run();
            }
        };
    }
}
