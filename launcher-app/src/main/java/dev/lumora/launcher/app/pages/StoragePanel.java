package dev.lumora.launcher.app.pages;

import dev.lumora.launcher.app.ui.Icons;
import dev.lumora.launcher.app.ui.Theme;
import dev.lumora.launcher.app.ui.Ui;
import dev.lumora.launcher.core.storage.StorageUsage;
import dev.lumora.launcher.core.util.Log;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * "Speicher": how much space the launcher uses, as a coloured bar and one row per category with folder and clear
 * buttons. Clearing only exists for data the launcher downloads again; worlds, screenshots and own mods are only shown.
 */
final class StoragePanel extends JPanel {
    private static final Map<StorageUsage.Kind, Color> COLORS = new EnumMap<>(StorageUsage.Kind.class);

    static {
        int[] c = {0xF07A4A, 0xA78BFA, 0x9CA3AF, 0x6B7280, 0x60A5FA, 0x8B95A5, 0xF472B6, 0x3B82F6, 0x818CF8, 0x14B8A6,
                0xEAB308, 0x22C55E, 0x4B5563};
        StorageUsage.Kind[] k = StorageUsage.Kind.values();
        for (int i = 0; i < k.length; i++) {
            COLORS.put(k[i], new Color(c[i % c.length]));
        }
    }

    private final PageHost host;
    private final ExecutorService io;
    private final StorageUsage usage;
    private StorageUsage.Report report;
    private String sort = "standard";
    private final JLabel title = Ui.label("Speicher wird berechnet …", 17, Font.BOLD, Theme.TEXT);
    private final JLabel subtitle = Ui.label(" ", 12.5f, Font.PLAIN, Theme.MUTED);
    private final Bar bar = new Bar();
    private final JLabel barHint = Ui.label("Fahr über den Balken, um zu sehen, wofür jede Farbe steht", 12, Font.PLAIN, Theme.MUTED);
    private final JPanel rows = Ui.column();
    private final JButton unused = new JButton("Ungenutzte Daten löschen", Icons.of(Icons.Kind.BROOM, 15, Theme.TEXT));

    StoragePanel(PageHost host, ExecutorService io) {
        super(new BorderLayout(0, 12));
        this.host = host;
        this.io = io;
        this.usage = new StorageUsage(host.ctx().paths);
        setOpaque(false);

        JPanel head = new JPanel(new BorderLayout(12, 10));
        head.setOpaque(false);
        JPanel text = Ui.column();
        text.add(title);
        text.add(Box.createVerticalStrut(3));
        text.add(subtitle);
        head.add(text, BorderLayout.NORTH);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setOpaque(false);
        actions.add(Ui.segmented(List.of("standard", "size", "name"), List.of("Standard", "Größe", "Name"), sort, s -> {
            sort = s;
            fill();
        }));
        unused.setFocusable(false);
        unused.addActionListener(e -> clearUnused());
        actions.add(unused);
        JButton refresh = new JButton(Icons.of(Icons.Kind.REFRESH, 15, Theme.TEXT));
        refresh.setToolTipText("Neu berechnen");
        refresh.setFocusable(false);
        refresh.addActionListener(e -> refresh());
        actions.add(refresh);
        head.add(actions, BorderLayout.CENTER);
        add(head, BorderLayout.NORTH);

        JPanel center = Ui.column();
        bar.setAlignmentX(Component.LEFT_ALIGNMENT);
        center.add(bar);
        center.add(Box.createVerticalStrut(6));
        center.add(barHint);
        center.add(Box.createVerticalStrut(10));
        rows.setAlignmentX(Component.LEFT_ALIGNMENT);
        center.add(rows);
        add(center, BorderLayout.CENTER);
    }

    /** Measures in the background (assets are many small files). */
    void refresh() {
        title.setText("Speicher wird berechnet …");
        io.submit(() -> {
            StorageUsage.Report r = usage.scan();
            SwingUtilities.invokeLater(() -> {
                report = r;
                fill();
            });
        });
    }

    private void fill() {
        if (report == null) {
            return;
        }
        title.setText(StorageUsage.format(report.total()) + " vom Launcher belegt");
        subtitle.setText(report.diskTotal() > 0
                ? StorageUsage.format(report.diskFree()) + " von " + StorageUsage.format(report.diskTotal()) + " frei auf diesem Laufwerk"
                : " ");
        unused.setEnabled(report.unusedBytes() > 0);
        unused.setToolTipText(report.unusedBytes() > 0 ? StorageUsage.format(report.unusedBytes()) + " in " + report.unusedFiles()
                + " Dateien: Download-Cache, entpackte native Bibliotheken und Logs älter als 14 Tage" : "Nichts zu löschen");
        List<StorageUsage.Category> cats = new ArrayList<>(report.categories());
        if (sort.equals("size")) {
            cats.sort(Comparator.comparingLong(StorageUsage.Category::bytes).reversed());
        } else if (sort.equals("name")) {
            cats.sort(Comparator.comparing(c -> c.kind().label));
        }
        bar.set(report);
        rows.removeAll();
        boolean first = true;
        for (StorageUsage.Category c : cats) {
            if (!first) {
                JComponent line = new JComponent() {
                    @Override
                    protected void paintComponent(Graphics g) {
                        g.setColor(Theme.BORDER);
                        g.fillRect(0, 0, getWidth(), 1);
                    }
                };
                line.setAlignmentX(Component.LEFT_ALIGNMENT);
                line.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
                line.setPreferredSize(new Dimension(10, 1));
                rows.add(line);
            }
            first = false;
            rows.add(row(c));
        }
        rows.revalidate();
        rows.repaint();
    }

    private JComponent row(StorageUsage.Category c) {
        JPanel r = new JPanel(new BorderLayout(12, 0));
        r.setOpaque(false);
        r.setAlignmentX(Component.LEFT_ALIGNMENT);
        r.setBorder(BorderFactory.createEmptyBorder(9, 2, 9, 0));
        JComponent dot = new JComponent() {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(COLORS.get(c.kind()));
                g.fillRoundRect(0, 6, 8, 8, Theme.arc(3), Theme.arc(3));
                g.dispose();
            }
        };
        dot.setPreferredSize(new Dimension(8, 20));
        JPanel west = new JPanel(new BorderLayout());
        west.setOpaque(false);
        west.add(dot, BorderLayout.NORTH);
        r.add(west, BorderLayout.WEST);
        JPanel text = Ui.column();
        text.add(Ui.label(c.kind().label, 14, Font.BOLD, Theme.TEXT));
        text.add(Box.createVerticalStrut(2));
        String desc = c.kind().description;
        text.add(Ui.label(desc, 12.5f, Font.PLAIN, Theme.MUTED));
        r.add(text, BorderLayout.CENTER);
        JPanel east = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        east.setOpaque(false);
        JLabel size = Ui.label(StorageUsage.format(c.bytes()), 13.5f, Font.BOLD, c.bytes() == 0 ? Theme.MUTED : Theme.TEXT);
        size.setPreferredSize(new Dimension(90, 30));
        size.setHorizontalAlignment(JLabel.RIGHT);
        east.add(size);
        JButton folder = iconButton(Icons.Kind.FOLDER, "Ordner öffnen");
        Path dir = c.dirs().size() == 1 ? c.dirs().getFirst() : host.ctx().paths.root().resolve("instances");
        folder.addActionListener(e -> host.openFolder(dir));
        east.add(folder);
        JButton clear = iconButton(Icons.Kind.BROOM, c.kind().clearable ? "Leeren" : "Spielerdaten werden nicht vom Launcher gelöscht");
        clear.setEnabled(c.kind().clearable && c.bytes() > 0);
        clear.setVisible(c.kind().clearable);
        clear.addActionListener(e -> clear(c));
        east.add(clear);
        if (!c.kind().clearable) {
            east.add(Box.createHorizontalStrut(36));
        }
        r.add(east, BorderLayout.EAST);
        r.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
        return r;
    }

    private static JButton iconButton(Icons.Kind icon, String tip) {
        JButton b = new JButton(Icons.of(icon, 15, Theme.TEXT));
        b.setToolTipText(tip);
        b.setFocusable(false);
        b.setPreferredSize(new Dimension(34, 30));
        return b;
    }

    private boolean gameBlocks() {
        if (host.gameRunning()) {
            JOptionPane.showMessageDialog(host.frame(), "Bitte zuerst Minecraft beenden – das Spiel nutzt diese Dateien gerade.",
                    "Speicher", JOptionPane.INFORMATION_MESSAGE);
            return true;
        }
        return false;
    }

    private void clear(StorageUsage.Category c) {
        if (gameBlocks()) {
            return;
        }
        if (JOptionPane.showConfirmDialog(host.frame(), c.kind().label + " leeren (" + StorageUsage.format(c.bytes()) + ")?\n"
                        + "Der Launcher lädt die Dateien beim nächsten Start wieder herunter, wenn sie gebraucht werden.\n"
                        + "Welten, Screenshots, eigene Mods und Einstellungen bleiben unberührt.", "Speicher freigeben",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        io.submit(() -> {
            try {
                usage.clear(c.kind());
            } catch (Exception e) {
                Log.warn("Clearing " + c.kind() + " failed: " + e.getMessage());
                SwingUtilities.invokeLater(() -> host.error("Speicher freigeben", e.getMessage()));
            }
            SwingUtilities.invokeLater(this::refresh);
        });
    }

    private void clearUnused() {
        if (report == null || gameBlocks()) {
            return;
        }
        if (JOptionPane.showConfirmDialog(host.frame(), StorageUsage.format(report.unusedBytes()) + " in " + report.unusedFiles()
                        + " Dateien löschen?\nDownload-Cache, entpackte native Bibliotheken und Logs älter als 14 Tage. "
                        + "Alles davon wird bei Bedarf neu angelegt.", "Ungenutzte Daten löschen",
                JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) {
            return;
        }
        io.submit(() -> {
            usage.clearUnused();
            SwingUtilities.invokeLater(this::refresh);
        });
    }

    /** Stacked bar, one segment per category; the tooltip names the segment under the mouse. */
    private static final class Bar extends JComponent {
        private StorageUsage.Report report;

        Bar() {
            setPreferredSize(new Dimension(10, 12));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 12));
            setToolTipText("");
            addMouseMotionListener(new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    repaint();
                }
            });
        }

        void set(StorageUsage.Report r) {
            this.report = r;
            repaint();
        }

        @Override
        public String getToolTipText(MouseEvent e) {
            StorageUsage.Category c = at(e.getX());
            return c == null ? null : c.kind().label + ": " + StorageUsage.format(c.bytes());
        }

        private StorageUsage.Category at(int x) {
            if (report == null || report.total() <= 0) {
                return null;
            }
            double pos = 0;
            for (StorageUsage.Category c : report.categories()) {
                double w = getWidth() * (double) c.bytes() / report.total();
                if (x >= pos && x < pos + w) {
                    return c;
                }
                pos += w;
            }
            return null;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            g.clip(new java.awt.geom.RoundRectangle2D.Float(0, 0, w, h, Theme.arc(6), Theme.arc(6)));
            g.setColor(Theme.SURFACE_2);
            g.fillRect(0, 0, w, h);
            if (report != null && report.total() > 0) {
                double pos = 0;
                for (StorageUsage.Category c : report.categories()) {
                    double cw = w * (double) c.bytes() / report.total();
                    g.setColor(COLORS.get(c.kind()));
                    g.fillRect((int) Math.round(pos), 0, (int) Math.ceil(cw), h);
                    pos += cw;
                    g.setColor(Theme.SURFACE);
                    g.fillRect((int) Math.round(pos), 0, 1, h);
                }
            }
            g.dispose();
        }
    }
}
