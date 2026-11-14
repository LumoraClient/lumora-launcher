package dev.lumora.launcher.app.ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Side navigation: logo with name and slogan on top, then entries with icon and label. Two widths: with text, or
 * icons only (labels as tooltips; switch at the bottom of the rail or in Einstellungen → Erscheinungsbild). The
 * selected entry gets a flat, slightly lighter pill that glides between entries; entries can carry a counter (unread
 * chats, new news) or an online count with a green dot.
 */
public final class NavRail extends JPanel {
    /** Width with text. */
    public static final int RAIL_WIDTH = 224;
    /** Width with icons only. */
    public static final int ICON_WIDTH = 84;
    private static final int PAD = 10;

    private final List<Item> items = new ArrayList<>();
    private final Consumer<String> onSelect;
    private final JComponent head;
    private String selected;
    private boolean iconsOnly;
    private Consumer<Boolean> onModeChange;
    /** Sliding selection pill (y and height glide to the selected entry). */
    private final Motion.Tween pillY = new Motion.Tween(-1, 280);
    private final Motion.Tween pillH = new Motion.Tween(0, 280);

    private final java.awt.GridBagConstraints gbc = new java.awt.GridBagConstraints();

    public NavRail(Image logo, Consumer<String> onSelect) {
        this.onSelect = onSelect;
        setLayout(new java.awt.GridBagLayout());
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        gbc.anchor = java.awt.GridBagConstraints.NORTH;
        setBackground(Theme.SURFACE);
        setBorder(BorderFactory.createEmptyBorder(16, PAD, 14, PAD));
        head = new JComponent() {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                int w = getWidth();
                int size = iconsOnly ? Math.min(52, getHeight() - 8) : Math.min(72, getHeight() - 52);
                if (logo != null && size > 24) {
                    // soft accent glow behind the logo
                    g.setPaint(new java.awt.RadialGradientPaint(new java.awt.geom.Point2D.Float(w / 2f, size / 2f + 2), size * 0.9f,
                            new float[]{0f, 1f}, new Color[]{Theme.alpha(Theme.ACCENT, 70), Theme.alpha(Theme.ACCENT, 0)}));
                    g.fillOval(w / 2 - size, -size / 2, size * 2, size * 2);
                    g.drawImage(logo, (w - size) / 2, 2, size, size, null);
                }
                if (!iconsOnly) {
                    int ty = (size > 24 ? size + 2 : 0) + 30;
                    g.setFont(Theme.display(24f, Font.BOLD | Font.ITALIC, 25f));
                    FontMetrics fm = g.getFontMetrics();
                    String name = "LUMORA";
                    int x = (w - fm.stringWidth(name)) / 2;
                    // wordmark: white with the accent fading in on the last letters
                    g.setPaint(new GradientPaint(x, 0, Color.WHITE, x + fm.stringWidth(name), 0, Theme.ACCENT_HOVER));
                    g.drawString(name, x, ty);
                    g.setFont(Theme.font(Font.PLAIN, 11.5f));
                    fm = g.getFontMetrics();
                    String slogan = "Play. Optimize. Connect.";
                    g.setColor(Theme.MUTED);
                    g.drawString(slogan, (w - fm.stringWidth(slogan)) / 2, ty + 18);
                }
                g.dispose();
            }
        };
        gbc.insets = new java.awt.Insets(0, 0, 16, 0);
        add(head, gbc);
        gbc.gridy++;
        applySizes();
    }

    /** Text or icons only. Lays the rail out again; the window keeps its size. */
    public void setIconsOnly(boolean on) {
        if (on == iconsOnly) {
            return;
        }
        iconsOnly = on;
        applySizes();
        revalidate();
        repaint();
        if (getParent() != null) {
            getParent().revalidate();
        }
    }

    public boolean iconsOnly() {
        return iconsOnly;
    }

    private void applySizes() {
        int w = iconsOnly ? ICON_WIDTH : RAIL_WIDTH;
        setPreferredSize(new Dimension(w, 10));
        setMinimumSize(new Dimension(w, 10));
        head.setPreferredSize(new Dimension(w - 2 * PAD, iconsOnly ? 60 : 132));
        head.setMinimumSize(new Dimension(w - 2 * PAD, iconsOnly ? 40 : 60));
        for (Item it : items) {
            it.applySize();
        }
        // first paint after the change places the pill without a glide
        pillY.jump(-1);
    }

    public void addItem(String id, String label, Icons.Kind icon) {
        Item it = new Item(id, label, icon);
        items.add(it);
        gbc.insets = new java.awt.Insets(0, 0, 4, 0);
        add(it, gbc);
        gbc.gridy++;
        if (selected == null) {
            select(id);
        }
    }

    /** Thin line between groups. */
    public void addSeparator() {
        JComponent line = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(Theme.BORDER);
                g.fillRect(8, getHeight() / 2, getWidth() - 16, 1);
            }
        };
        line.setPreferredSize(new Dimension(10, 17));
        gbc.insets = new java.awt.Insets(4, 0, 4, 0);
        add(line, gbc);
        gbc.gridy++;
    }

    public void addGlue() {
        java.awt.GridBagConstraints g = (java.awt.GridBagConstraints) gbc.clone();
        g.weighty = 1;
        add(Box.createVerticalGlue(), g);
        gbc.gridy++;
    }

    /** Button at the bottom that switches between text and icons only; {@code onChange} gets the new mode. */
    public void addModeToggle(Consumer<Boolean> onChange) {
        this.onModeChange = onChange;
        JComponent t = new JComponent() {
            private boolean hover;

            {
                setPreferredSize(new Dimension(10, 32));
                setMinimumSize(new Dimension(10, 28));
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        setIconsOnly(!iconsOnly);
                        if (onModeChange != null) {
                            onModeChange.accept(iconsOnly);
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
                        repaint();
                    }
                });
            }

            @Override
            public String getToolTipText() {
                return iconsOnly ? "Seitenleiste mit Text" : null;
            }

            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
                int h = getHeight();
                if (hover) {
                    g.setColor(Theme.alpha(Theme.TEXT, 10));
                    Theme.fillRound(g, 0, 0, getWidth(), h, Theme.arc(10), Theme.arc(10));
                }
                Color c = hover ? Theme.TEXT : Theme.MUTED;
                g.setColor(c);
                g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                // double chevron: « collapses, » expands
                int cx = iconsOnly ? getWidth() / 2 - 4 : 18;
                int cy = h / 2;
                int d = iconsOnly ? 1 : -1;
                for (int k = 0; k < 2; k++) {
                    int x = cx + k * 6;
                    g.drawLine(x - 2 * d, cy - 5, x + 2 * d, cy);
                    g.drawLine(x + 2 * d, cy, x - 2 * d, cy + 5);
                }
                if (!iconsOnly) {
                    g.setFont(Theme.font(Font.PLAIN, 13f));
                    FontMetrics fm = g.getFontMetrics();
                    g.drawString("Nur Icons", 48, (h - fm.getHeight()) / 2 + fm.getAscent());
                }
                g.dispose();
            }
        };
        t.setToolTipText("");
        gbc.insets = new java.awt.Insets(6, 0, 0, 0);
        add(t, gbc);
        gbc.gridy++;
    }

    /** Red counter on an entry (unread messages, requests, new news); 0 hides it. */
    public void setBadge(String id, int count) {
        setBadge(id, count, false);
    }

    /** @param online green dot + number (friends online) instead of a red counter */
    public void setBadge(String id, int count, boolean online) {
        for (Item it : items) {
            if (it.id.equals(id) && (it.badge != count || it.online != online)) {
                it.badge = count;
                it.online = online;
                it.repaint();
            }
        }
    }

    public void select(String id) {
        selected = id;
        Item target = items.stream().filter(i -> i.id.equals(id)).findFirst().orElse(null);
        if (target != null && target.getHeight() > 0) {
            if (pillY.value() < 0) {
                pillY.jump(target.getY());
                pillH.jump(target.getHeight());
            } else {
                pillY.set(target.getY());
                pillH.set(target.getHeight());
                Motion.during(this, pillY.millis() + 40);
            }
        }
        for (Item it : items) {
            it.repaint();
        }
        onSelect.accept(id);
    }

    public String selected() {
        return selected;
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();
        g.setColor(Theme.BORDER);
        g.fillRect(w - 1, 0, 1, h);
        Item sel = items.stream().filter(i -> i.id.equals(selected)).findFirst().orElse(null);
        if (sel != null && sel.getHeight() > 0) {
            if (pillY.value() < 0 || (pillY.value() == pillY.target() && pillY.target() != sel.getY())) {
                // first paint or layout change (window resized, mode switched): no glide
                pillY.jump(sel.getY());
                pillH.jump(sel.getHeight());
            }
            int x = sel.getX();
            int y = (int) Math.round(pillY.value());
            int pw = sel.getWidth();
            int ph = (int) Math.round(pillH.value());
            int arc = Theme.arc(12);
            if (Theme.pixelStyle) {
                // like the selected entry of the game's Lumora hub: accent tint and an accent bar on the left
                g.setColor(Theme.alpha(Theme.ACCENT, 46));
                Theme.fillRound(g, x, y, pw, ph, arc, arc);
                g.setColor(Theme.ACCENT);
                g.fillRect(x, y + 6, 4, Math.max(0, ph - 12));
            } else {
                g.setColor(Theme.alpha(Theme.TEXT, 20));
                Theme.fillRound(g, x, y, pw, ph, arc, arc);
            }
        }
        g.dispose();
    }

    private final class Item extends JComponent {
        private final String id;
        private final String label;
        private final Icons.Kind icon;
        private boolean hover;
        private int badge;
        private boolean online;

        Item(String id, String label, Icons.Kind icon) {
            this.id = id;
            this.label = label;
            this.icon = icon;
            applySize();
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    select(Item.this.id);
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

        void applySize() {
            int w = (iconsOnly ? ICON_WIDTH : RAIL_WIDTH) - 2 * PAD;
            setPreferredSize(new Dimension(w, iconsOnly ? 52 : 44));
            // GridBagLayout drops every item when the minimum sizes do not fit: items may shrink a little
            setMinimumSize(new Dimension(w, iconsOnly ? 38 : 34));
            setToolTipText(iconsOnly ? label : null);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
            boolean sel = id.equals(selected);
            int w = getWidth();
            int h = getHeight();
            int arc = Theme.arc(12);
            if (!sel && hover) {
                // the selected entry's pill is painted by the rail (it slides between entries)
                g.setColor(Theme.alpha(Theme.TEXT, 10));
                Theme.fillRound(g, 0, 0, w, h, arc, arc);
            }
            Color ic = sel ? Theme.ACCENT_HOVER : hover ? Theme.TEXT : Theme.MUTED;
            if (iconsOnly) {
                int s = 24;
                int ix = (w - s) / 2;
                int iy = (h - s) / 2;
                Icons.of(icon, s, ic).paintIcon(this, g, ix, iy);
                if (badge > 0) {
                    // small counter at the top right of the icon
                    String t = badge > 99 ? "99+" : Integer.toString(badge);
                    g.setFont(Theme.font(Font.BOLD, 9.5f));
                    FontMetrics bm = g.getFontMetrics();
                    int bw = Math.max(16, bm.stringWidth(t) + 8);
                    int bx = Math.min(w - bw - 2, ix + s - 6);
                    int by = Math.max(2, iy - 7);
                    g.setColor(online ? Theme.OK : Theme.ACCENT);
                    Theme.fillRound(g, bx, by, bw, 16, Theme.arc(16), Theme.arc(16));
                    g.setColor(online ? new Color(0x0D1424) : Theme.onAccent());
                    g.drawString(t, bx + (bw - bm.stringWidth(t)) / 2, by + 12);
                }
                g.dispose();
                return;
            }
            Icons.of(icon, 20, ic).paintIcon(this, g, 14, (h - 20) / 2);
            g.setFont(Theme.display(16f, sel ? Font.BOLD : Font.PLAIN, 14.5f));
            FontMetrics fm = g.getFontMetrics();
            g.setColor(sel ? Theme.TEXT : hover ? Theme.TEXT : Theme.mix(Theme.MUTED, Theme.TEXT, 0.3));
            g.drawString(label, 48, (h - fm.getHeight()) / 2 + fm.getAscent());
            if (badge > 0) {
                String t = badge > 99 ? "99+" : Integer.toString(badge);
                g.setFont(Theme.font(Font.BOLD, 11f));
                FontMetrics bm = g.getFontMetrics();
                int bw = Math.max(20, bm.stringWidth(t) + 12) + (online ? 8 : 0);
                int bx = w - bw - 8;
                int by = (h - 20) / 2;
                g.setColor(online ? Theme.SURFACE_2 : Theme.ACCENT);
                Theme.fillRound(g, bx, by, bw, 20, Theme.arc(20), Theme.arc(20));
                int tx = bx + (bw - bm.stringWidth(t)) / 2;
                if (online) {
                    tx = bx + 7;
                    g.setColor(Theme.OK);
                    g.fillOval(bx + bw - 12, by + 7, 6, 6);
                }
                g.setColor(online ? Theme.TEXT : Theme.onAccent());
                g.drawString(t, tx, by + 14);
            }
            g.dispose();
        }
    }
}
