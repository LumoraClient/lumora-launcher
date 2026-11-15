package dev.lumora.launcher.app.ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
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
 * Side navigation: the logo on top, then narrow entries with an icon and the page name below it. The selected entry
 * gets a flat, slightly lighter pill that glides between entries; entries can carry a counter (unread chats), an online
 * count with a green dot or a small red dot (news not opened yet).
 */
public final class NavRail extends JPanel {
    /** Width of the rail. */
    public static final int RAIL_WIDTH = 92;
    private static final int PAD = 8;
    private static final int ICON = 22;

    private final List<Item> items = new ArrayList<>();
    private final Consumer<String> onSelect;
    private String selected;
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
        setPreferredSize(new Dimension(RAIL_WIDTH, 10));
        setMinimumSize(new Dimension(RAIL_WIDTH, 10));
        JComponent head = new JComponent() {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                int w = getWidth();
                int size = Math.min(52, getHeight() - 8);
                if (logo != null && size > 24) {
                    // soft accent glow behind the logo
                    g.setPaint(new java.awt.RadialGradientPaint(new java.awt.geom.Point2D.Float(w / 2f, size / 2f + 2), size * 0.9f,
                            new float[]{0f, 1f}, new Color[]{Theme.alpha(Theme.ACCENT, 70), Theme.alpha(Theme.ACCENT, 0)}));
                    g.fillOval(w / 2 - size, -size / 2, size * 2, size * 2);
                    g.drawImage(logo, (w - size) / 2, 2, size, size, null);
                }
                g.dispose();
            }
        };
        head.setPreferredSize(new Dimension(RAIL_WIDTH - 2 * PAD, 60));
        head.setMinimumSize(new Dimension(RAIL_WIDTH - 2 * PAD, 40));
        gbc.insets = new java.awt.Insets(0, 0, 12, 0);
        add(head, gbc);
        gbc.gridy++;
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
        line.setPreferredSize(new Dimension(10, 13));
        gbc.insets = new java.awt.Insets(2, 0, 2, 0);
        add(line, gbc);
        gbc.gridy++;
    }

    public void addGlue() {
        java.awt.GridBagConstraints g = (java.awt.GridBagConstraints) gbc.clone();
        g.weighty = 1;
        add(Box.createVerticalGlue(), g);
        gbc.gridy++;
    }

    /** Red counter on an entry (unread messages, requests); 0 hides it. */
    public void setBadge(String id, int count) {
        setBadge(id, count, false);
    }

    /** @param online green counter (friends online) instead of a red one */
    public void setBadge(String id, int count, boolean online) {
        for (Item it : items) {
            if (it.id.equals(id) && (it.badge != count || it.online != online)) {
                it.badge = count;
                it.online = online;
                it.repaint();
            }
        }
    }

    /** Small red dot on an entry (e.g. the bell: news not opened yet). */
    public void setDot(String id, boolean on) {
        for (Item it : items) {
            if (it.id.equals(id) && it.dot != on) {
                it.dot = on;
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
                // first paint or layout change (window resized): no glide
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
        private boolean dot;

        Item(String id, String label, Icons.Kind icon) {
            this.id = id;
            this.label = label;
            this.icon = icon;
            setPreferredSize(new Dimension(RAIL_WIDTH - 2 * PAD, 58));
            // GridBagLayout drops every item when the minimum sizes do not fit: items may shrink a little
            setMinimumSize(new Dimension(RAIL_WIDTH - 2 * PAD, 48));
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

        private void paintDot(Graphics2D g, int x, int y) {
            g.setColor(Theme.SURFACE);
            g.fillOval(x - 2, y - 2, 12, 12);
            g.setColor(Theme.FAIL);
            g.fillOval(x, y, 8, 8);
        }

        /** The name, shortened with "…" if it is wider than the entry. */
        private String fit(FontMetrics fm, String text, int max) {
            if (fm.stringWidth(text) <= max) {
                return text;
            }
            String t = text;
            while (t.length() > 1 && fm.stringWidth(t + "…") > max) {
                t = t.substring(0, t.length() - 1);
            }
            return t + "…";
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
            g.setFont(Theme.font(sel ? Font.BOLD : Font.PLAIN, 10.5f));
            FontMetrics fm = g.getFontMetrics();
            int block = ICON + 5 + fm.getAscent();
            int iy = (h - block) / 2;
            int ix = (w - ICON) / 2;
            Icons.filled(icon, ICON, ic).paintIcon(this, g, ix, iy);
            if (dot) {
                paintDot(g, ix + ICON - 5, iy - 2);
            }
            if (badge > 0) {
                // small counter at the top right of the icon
                String t = badge > 99 ? "99+" : Integer.toString(badge);
                Font f = g.getFont();
                g.setFont(Theme.font(Font.BOLD, 9.5f));
                FontMetrics bm = g.getFontMetrics();
                int bw = Math.max(16, bm.stringWidth(t) + 8);
                int bx = Math.min(w - bw - 2, ix + ICON - 6);
                int by = Math.max(1, iy - 7);
                g.setColor(online ? Theme.OK : Theme.ACCENT);
                Theme.fillRound(g, bx, by, bw, 16, Theme.arc(16), Theme.arc(16));
                g.setColor(online ? new Color(0x0D1424) : Theme.onAccent());
                g.drawString(t, bx + (bw - bm.stringWidth(t)) / 2, by + 12);
                g.setFont(f);
            }
            String name = fit(fm, label, w - 6);
            g.setColor(sel ? Theme.TEXT : hover ? Theme.TEXT : Theme.mix(Theme.MUTED, Theme.TEXT, 0.15));
            g.drawString(name, (w - fm.stringWidth(name)) / 2, iy + ICON + 5 + fm.getAscent());
            g.dispose();
        }
    }
}
