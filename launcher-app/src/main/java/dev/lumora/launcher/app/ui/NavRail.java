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
 * Side navigation: logo with name and slogan on top, then entries with icon and label. The selected entry gets an
 * accent gradient pill; entries can carry a counter (unread chats, new news) or an online count with a green dot.
 */
public final class NavRail extends JPanel {
    public static final int RAIL_WIDTH = 216;

    private final List<Item> items = new ArrayList<>();
    private final Consumer<String> onSelect;
    private String selected;

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
        setBorder(BorderFactory.createEmptyBorder(16, 12, 14, 12));
        setPreferredSize(new Dimension(RAIL_WIDTH, 10));
        JComponent head = new JComponent() {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                int w = getWidth();
                int size = Math.min(72, getHeight() - 52);
                if (logo != null && size > 24) {
                    // soft accent glow behind the logo
                    g.setPaint(new java.awt.RadialGradientPaint(new java.awt.geom.Point2D.Float(w / 2f, size / 2f + 2), size * 0.9f,
                            new float[]{0f, 1f}, new Color[]{Theme.alpha(Theme.ACCENT, 70), Theme.alpha(Theme.ACCENT, 0)}));
                    g.fillOval(w / 2 - size, -size / 2, size * 2, size * 2);
                    g.drawImage(logo, (w - size) / 2, 2, size, size, null);
                }
                int ty = (size > 24 ? size + 2 : 0) + 30;
                g.setFont(Theme.font(Font.BOLD | Font.ITALIC, 25f));
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
                g.dispose();
            }
        };
        head.setPreferredSize(new Dimension(RAIL_WIDTH - 24, 132));
        head.setMinimumSize(new Dimension(RAIL_WIDTH - 24, 60));
        gbc.insets = new java.awt.Insets(0, 0, 16, 0);
        add(head, gbc);
        gbc.gridy++;
    }

    public void addItem(String id, String label, Icons.Kind icon) {
        Item it = new Item(id, label, icon);
        items.add(it);
        gbc.insets = new java.awt.Insets(0, 0, 3, 0);
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
                g.fillRect(6, getHeight() / 2, getWidth() - 12, 1);
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
        for (Item it : items) {
            it.repaint();
        }
        onSelect.accept(id);
    }

    public String selected() {
        return selected;
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
            setPreferredSize(new Dimension(RAIL_WIDTH - 24, 42));
            // GridBagLayout drops every item when the minimum sizes do not fit: items may shrink a little
            setMinimumSize(new Dimension(RAIL_WIDTH - 24, 34));
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

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
            boolean sel = id.equals(selected);
            int w = getWidth();
            int h = getHeight();
            int arc = Theme.arc(12);
            if (sel) {
                g.setPaint(new GradientPaint(0, 0, Theme.alpha(Theme.ACCENT, 120), w, 0, Theme.alpha(Theme.ACCENT, 28)));
                g.fillRoundRect(0, 0, w, h, arc, arc);
                g.setColor(Theme.alpha(Theme.ACCENT_HOVER, 90));
                g.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
            } else if (hover) {
                g.setColor(Theme.alpha(Theme.SURFACE_2, 190));
                g.fillRoundRect(0, 0, w, h, arc, arc);
            }
            Color c = sel ? Theme.ACCENT_TEXT : hover ? Theme.TEXT : Theme.MUTED;
            Icons.of(icon, 19, sel ? Theme.ACCENT_TEXT : c).paintIcon(this, g, 14, (h - 19) / 2);
            g.setFont(Theme.font(sel ? Font.BOLD : Font.PLAIN, 14f));
            FontMetrics fm = g.getFontMetrics();
            g.setColor(sel ? Color.WHITE : hover ? Theme.TEXT : Theme.mix(Theme.MUTED, Theme.TEXT, 0.35));
            g.drawString(label, 46, (h - fm.getHeight()) / 2 + fm.getAscent());
            if (badge > 0) {
                String t = badge > 99 ? "99+" : Integer.toString(badge);
                g.setFont(Theme.font(Font.BOLD, 11f));
                FontMetrics bm = g.getFontMetrics();
                int bw = Math.max(20, bm.stringWidth(t) + 12) + (online ? 8 : 0);
                int bx = w - bw - 8;
                int by = (h - 20) / 2;
                g.setColor(online ? Theme.SURFACE_2 : Theme.ACCENT);
                g.fillRoundRect(bx, by, bw, 20, Theme.arc(20), Theme.arc(20));
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
