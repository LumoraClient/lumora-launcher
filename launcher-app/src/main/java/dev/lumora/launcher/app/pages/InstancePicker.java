package dev.lumora.launcher.app.pages;

import dev.lumora.launcher.app.ui.Icons;
import dev.lumora.launcher.app.ui.Theme;
import dev.lumora.launcher.app.ui.Ui;
import dev.lumora.launcher.core.i18n.Lang;
import dev.lumora.launcher.core.instance.Instance;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * Instance choice of the start page (arrow next to the play button and the play bar): search, the selected instance on
 * top, then the player's instances and the Lumora versions on the list, then one group per origin ("Importiert aus
 * CurseForge"). A click selects; on hover a row shows "open" (gear) and "play". Below: "Instanz erstellen".
 */
public final class InstancePicker {
    private static final int WIDTH = 400;
    private static final int ROW_H = 52;

    private final PageHost host;
    private final ExecutorService io;
    private final JPopupMenu popup = new JPopupMenu();
    private final JTextField search = new JTextField();
    private final JPanel rows = Ui.column();

    private InstancePicker(PageHost host, ExecutorService io) {
        this.host = host;
        this.io = io;
    }

    /** Opens the picker below {@code anchor} (right edges aligned), or above it with {@code above}. */
    public static void show(PageHost host, ExecutorService io, Component anchor, boolean above) {
        new InstancePicker(host, io).open(anchor, above);
    }

    private void open(Component anchor, boolean above) {
        JPanel body = new JPanel(new BorderLayout(0, 10));
        body.setBackground(Theme.SURFACE);
        body.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        search.putClientProperty("JTextField.placeholderText", Lang.t("picker.search"));
        search.putClientProperty("JTextField.leadingIcon", Icons.of(Icons.Kind.SEARCH, 16, Theme.MUTED));
        search.putClientProperty("JTextField.showClearButton", true);
        search.setPreferredSize(new Dimension(WIDTH - 20, Ui.CONTROL_HEIGHT));
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                fill();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                fill();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                fill();
            }
        });
        body.add(search, BorderLayout.NORTH);
        rows.setOpaque(false);
        JPanel holder = new JPanel(new BorderLayout());
        holder.setOpaque(false);
        holder.add(rows, BorderLayout.NORTH);
        JScrollPane sp = new JScrollPane(holder);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.getViewport().setOpaque(false);
        sp.setOpaque(false);
        sp.getVerticalScrollBar().setUnitIncrement(18);
        sp.setPreferredSize(new Dimension(WIDTH - 20, 380));
        body.add(sp, BorderLayout.CENTER);
        JPanel south = new JPanel(new BorderLayout(8, 0));
        south.setOpaque(false);
        JButton create = Ui.primary(Lang.t("instances.create"), Icons.Kind.PLUS, () -> {
            popup.setVisible(false);
            CreateInstanceDialog.show(host, io, host::openInstance);
        });
        south.add(create, BorderLayout.CENTER);
        JButton repair = Ui.button("", Icons.Kind.CHECK, () -> {
            popup.setVisible(false);
            host.launch(null, true);
        });
        repair.setToolTipText(Lang.t("versions.repair"));
        south.add(repair, BorderLayout.EAST);
        body.add(south, BorderLayout.SOUTH);
        fill();
        popup.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        popup.add(body);
        popup.setFocusable(true);
        Dimension size = popup.getPreferredSize();
        popup.setPopupSize(WIDTH, size.height);
        int x = anchor.getWidth() - WIDTH;
        popup.show(anchor, x, above ? -size.height - 6 : anchor.getHeight() + 6);
        // type to search right away, but only in the active window (focusing a popup of an inactive window closes it)
        java.awt.Window win = SwingUtilities.getWindowAncestor(anchor);
        if (win != null && win.isActive()) {
            SwingUtilities.invokeLater(search::requestFocusInWindow);
        }
    }

    /** Fills the rows for the current search. */
    private void fill() {
        rows.removeAll();
        var ctx = host.ctx();
        String q = search.getText().strip().toLowerCase(Locale.ROOT);
        Instance selected = ctx.selectedInstance();
        List<Instance> all = new ArrayList<>(ctx.instances.list(ctx.config));
        all.removeIf(i -> !q.isEmpty() && !i.name.toLowerCase(Locale.ROOT).contains(q) && !i.minecraftVersion.contains(q)
                && !i.kind().label().toLowerCase(Locale.ROOT).contains(q));
        all.sort(Comparator.comparingLong((Instance i) -> InstanceUi.lastPlayedMillis(ctx, i)).reversed());
        boolean any = false;
        Instance top = all.stream().filter(i -> i.key().equals(selected.key())).findFirst().orElse(null);
        if (top != null) {
            rows.add(new Row(top, true));
            all.remove(top);
            any = true;
        }
        Map<String, List<Instance>> groups = new LinkedHashMap<>();
        List<Instance> plain = new ArrayList<>();
        for (Instance i : all) {
            String g = InstanceUi.importGroup(i);
            if (g == null) {
                plain.add(i);
            } else {
                groups.computeIfAbsent(g, k -> new ArrayList<>()).add(i);
            }
        }
        if (top != null && (!plain.isEmpty() || !groups.isEmpty())) {
            rows.add(separator());
        }
        for (Instance i : plain) {
            rows.add(new Row(i, false));
            any = true;
        }
        for (Map.Entry<String, List<Instance>> g : groups.entrySet()) {
            rows.add(groupTitle(Lang.t("picker.imported", g.getKey()).toUpperCase(Lang.locale()), g.getValue().size()));
            for (Instance i : g.getValue()) {
                rows.add(new Row(i, false));
                any = true;
            }
        }
        if (!any) {
            JComponent none = Ui.label(q.isEmpty() ? Lang.t("picker.empty") : Lang.t("instances.no_match", search.getText().strip()),
                    13, Font.PLAIN, Theme.MUTED);
            none.setBorder(BorderFactory.createEmptyBorder(10, 8, 10, 8));
            rows.add(none);
        }
        rows.revalidate();
        rows.repaint();
    }

    private static JComponent separator() {
        JComponent s = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(Theme.BORDER);
                g.fillRect(24, getHeight() / 2, getWidth() - 48, 1);
            }
        };
        s.setPreferredSize(new Dimension(WIDTH - 40, 13));
        s.setMaximumSize(new Dimension(Integer.MAX_VALUE, 13));
        s.setAlignmentX(Component.LEFT_ALIGNMENT);
        return s;
    }

    private static JComponent groupTitle(String text, int count) {
        JPanel p = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
        p.setOpaque(false);
        p.add(Ui.label(text, 11.5f, Font.BOLD, Theme.MUTED));
        p.add(Ui.label(String.valueOf(count), 11.5f, Font.PLAIN, Theme.MUTED));
        p.setBorder(BorderFactory.createEmptyBorder(10, 0, 4, 0));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, p.getPreferredSize().height));
        return p;
    }

    /** One instance: icon, name, "version - loader"; on hover gear (open) and play. */
    private final class Row extends JComponent {
        private final Instance instance;
        private final boolean selected;
        private final BufferedImage icon;
        private final Rectangle gear = new Rectangle();
        private final Rectangle play = new Rectangle();
        private boolean hover;
        private int hoverButton; // 0 none, 1 gear, 2 play

        Row(Instance instance, boolean selected) {
            this.instance = instance;
            this.selected = selected;
            this.icon = InstanceTile.loadIcon(instance.iconFile());
            setPreferredSize(new Dimension(WIDTH - 40, ROW_H));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, ROW_H));
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            MouseAdapter m = new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (!SwingUtilities.isLeftMouseButton(e)) {
                        return;
                    }
                    popup.setVisible(false);
                    if (gear.contains(e.getPoint())) {
                        host.openInstance(instance);
                    } else if (play.contains(e.getPoint())) {
                        host.selectInstance(instance);
                        host.launch(null, false);
                    } else {
                        host.selectInstance(instance);
                    }
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    hoverButton = 0;
                    repaint();
                }

                @Override
                public void mouseMoved(MouseEvent e) {
                    int b = gear.contains(e.getPoint()) ? 1 : play.contains(e.getPoint()) ? 2 : 0;
                    if (b != hoverButton) {
                        hoverButton = b;
                        setToolTipText(b == 1 ? Lang.t("picker.open") : b == 2 ? Lang.t("instances.play_tip", instance.name) : null);
                        repaint();
                    }
                }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            if (hover || selected) {
                g.setColor(hover ? Theme.SURFACE_2 : Theme.alpha(Theme.SURFACE_2, 150));
                Theme.fillRound(g, 0, 2, w, h - 4, Theme.arc(8), Theme.arc(8));
            }
            // icon
            int is = 32;
            int ix = 10;
            int iy = (h - is) / 2;
            if (icon != null) {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.drawImage(icon, ix, iy, is, is, null);
            } else {
                g.setColor(Theme.alpha(InstanceUi.kindColor(instance.kind()), 50));
                Theme.fillRound(g, ix, iy, is, is, Theme.arc(8), Theme.arc(8));
                InstanceTile.paintLoader(g, instance.kind(), ix + 4, iy + 4, is - 8);
            }
            // buttons on hover
            int bs = 30;
            play.setBounds(0, 0, 0, 0);
            gear.setBounds(0, 0, 0, 0);
            int textRight = w - 12;
            if (hover) {
                play.setBounds(w - bs - 10, (h - bs) / 2, bs, bs);
                gear.setBounds(play.x - bs - 8, play.y, bs, bs);
                g.setColor(hoverButton == 1 ? Theme.alpha(Theme.TEXT, 40) : Theme.alpha(Theme.TEXT, 20));
                Theme.fillRound(g, gear.x, gear.y, bs, bs, Theme.arc(6), Theme.arc(6));
                g.setColor(Theme.BORDER);
                g.setStroke(new BasicStroke(1f));
                Theme.drawRound(g, gear.x, gear.y, bs, bs, Theme.arc(6), Theme.arc(6));
                Icons.of(Icons.Kind.GEAR, 15, Theme.TEXT).paintIcon(this, g, gear.x + (bs - 15) / 2, gear.y + (bs - 15) / 2);
                g.setColor(hoverButton == 2 ? Theme.ACCENT_HOVER : Theme.ACCENT);
                Theme.fillRound(g, play.x, play.y, bs, bs, Theme.arc(6), Theme.arc(6));
                Icons.of(Icons.Kind.PLAY, 14, Color.WHITE).paintIcon(this, g, play.x + (bs - 14) / 2 + 1, play.y + (bs - 14) / 2);
                textRight = gear.x - 8;
            }
            // text
            int tx = ix + is + 12;
            g.setFont(Theme.font(Font.BOLD, 14f));
            FontMetrics fm = g.getFontMetrics();
            g.setColor(selected ? Theme.ACCENT_TEXT : Theme.TEXT);
            g.drawString(InstanceTile.ellipsize(instance.name, fm, textRight - tx), tx, h / 2 - 2);
            g.setFont(Theme.font(Font.PLAIN, 12.5f));
            g.setColor(Theme.MUTED);
            String line = instance.minecraftVersion + " - " + instance.kind().label();
            g.drawString(InstanceTile.ellipsize(line, g.getFontMetrics(), textRight - tx), tx, h / 2 + 15);
            g.dispose();
        }
    }
}
