package dev.lumora.launcher.app.ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.RenderingHints;
import java.util.List;

/**
 * Minimal table in a rounded frame: a header row, then rows split by thin lines. The columns share the width by
 * fixed weights (every cell asks for no width of its own), so all rows line up. Each row can end with a small
 * component (for example a "⋮" menu button).
 */
public final class DataTable extends JPanel {
    private static final int TRAILING = 40;

    private final double[] weights;
    private final JPanel body = new JPanel();
    private int rows;

    public DataTable(List<String> columns, double... weights) {
        super(new BorderLayout());
        if (weights.length != columns.size()) {
            throw new IllegalArgumentException("one weight per column");
        }
        this.weights = weights;
        setOpaque(false);
        setAlignmentX(Component.LEFT_ALIGNMENT);
        setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
        JComponent[] head = new JComponent[columns.size()];
        for (int i = 0; i < head.length; i++) {
            head[i] = Ui.label(columns.get(i), 13, Font.BOLD, Theme.TEXT);
        }
        JPanel header = line(head, null, 12);
        header.setOpaque(true);
        header.setBackground(Theme.mix(Theme.SURFACE, Theme.SURFACE_2, 0.55));
        add(header, BorderLayout.NORTH);
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        add(body, BorderLayout.CENTER);
    }

    /** Adds a row; {@code trailing} (optional) sits at the right end. */
    public void addRow(List<? extends JComponent> cells, JComponent trailing) {
        if (cells.size() != weights.length) {
            throw new IllegalArgumentException("one cell per column");
        }
        body.add(divider());
        body.add(line(cells.toArray(new JComponent[0]), trailing, 10));
        rows++;
        revalidate();
    }

    /** Text shown instead of rows when there are none. */
    public void empty(String text) {
        body.add(divider());
        JLabel l = Ui.label(text, 13, Font.PLAIN, Theme.MUTED);
        l.setBorder(BorderFactory.createEmptyBorder(14, 20, 14, 20));
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.add(l, BorderLayout.WEST);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(p);
    }

    public int rowCount() {
        return rows;
    }

    public void clear() {
        body.removeAll();
        rows = 0;
        revalidate();
        repaint();
    }

    private JPanel line(JComponent[] cells, JComponent trailing, int vpad) {
        JPanel p = new JPanel(new GridBagLayout()) {
            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setBorder(BorderFactory.createEmptyBorder(vpad, 20, vpad, 10));
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;
        for (int i = 0; i < cells.length; i++) {
            c.gridx = i;
            c.weightx = weights[i];
            c.insets = new java.awt.Insets(0, 0, 0, 12);
            JPanel cell = new JPanel(new BorderLayout()) {
                @Override
                public Dimension getPreferredSize() {
                    // no own width: the weights alone divide the row, so columns line up across rows
                    return new Dimension(0, super.getPreferredSize().height);
                }

                @Override
                public Dimension getMinimumSize() {
                    return new Dimension(0, super.getMinimumSize().height);
                }
            };
            cell.setOpaque(false);
            cell.add(cells[i], BorderLayout.CENTER);
            p.add(cell, c);
        }
        c.gridx = cells.length;
        c.weightx = 0;
        c.insets = new java.awt.Insets(0, 0, 0, 0);
        JPanel end = new JPanel(new BorderLayout());
        end.setOpaque(false);
        end.setPreferredSize(new Dimension(TRAILING, 10));
        end.setMinimumSize(new Dimension(TRAILING, 10));
        if (trailing != null) {
            end.add(trailing, BorderLayout.CENTER);
        }
        p.add(end, c);
        return p;
    }

    private static JComponent divider() {
        JComponent d = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(Theme.BORDER);
                g.fillRect(0, 0, getWidth(), 1);
            }
        };
        d.setAlignmentX(Component.LEFT_ALIGNMENT);
        d.setPreferredSize(new Dimension(10, 1));
        d.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        return d;
    }

    /** A cell with a small muted line above the main text, like "(dieses Gerät)" over a name. */
    public static JComponent twoLine(String small, String main) {
        JPanel p = Ui.column();
        if (small != null && !small.isEmpty()) {
            p.add(Ui.label(small, 11.5f, Font.PLAIN, Theme.MUTED));
            p.add(Box.createVerticalStrut(2));
        }
        JLabel l = Ui.label(main, 13.5f, Font.PLAIN, Theme.TEXT);
        l.setToolTipText(main);
        p.add(l);
        return p;
    }

    /** An entry of a row menu; {@code danger} shows it in red. */
    public record Action(String label, Runnable run, boolean danger) {
    }

    /** Small "⋮" button that opens a menu with the given actions. */
    public static JComponent menu(List<Action> actions) {
        javax.swing.JButton b = new javax.swing.JButton(Icons.of(Icons.Kind.MORE, 16, Theme.TEXT));
        b.putClientProperty("JButton.buttonType", "borderless");
        b.setFocusable(false);
        b.setToolTipText("Aktionen");
        b.addActionListener(e -> {
            javax.swing.JPopupMenu m = new javax.swing.JPopupMenu();
            for (Action a : actions) {
                javax.swing.JMenuItem it = new javax.swing.JMenuItem(a.label());
                if (a.danger()) {
                    it.setForeground(Theme.FAIL);
                }
                it.addActionListener(x -> a.run().run());
                m.add(it);
            }
            m.show(b, b.getWidth() - m.getPreferredSize().width, b.getHeight());
        });
        JPanel w = new JPanel(new GridBagLayout());
        w.setOpaque(false);
        w.add(b);
        return w;
    }

    /** A plain text cell (cut with "…" when too narrow; the tooltip shows all of it). */
    public static JLabel text(String s, boolean muted) {
        JLabel l = Ui.label(s, 13.5f, Font.PLAIN, muted ? Theme.MUTED : Theme.TEXT);
        l.setToolTipText(s);
        return l;
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int arc = Theme.arc(10);
        g.setColor(Theme.SURFACE);
        Theme.fillRound(g, 0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
        g.dispose();
    }

    @Override
    protected void paintChildren(Graphics g0) {
        // clip the header background to the rounded frame, then draw the frame on top
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int arc = Theme.arc(10);
        g.clip(new java.awt.geom.RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 1, arc, arc));
        super.paintChildren(g);
        g.dispose();
        Graphics2D f = (Graphics2D) g0.create();
        f.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        f.setColor(Theme.BORDER);
        Theme.drawRound(f, 0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
        f.dispose();
    }
}
