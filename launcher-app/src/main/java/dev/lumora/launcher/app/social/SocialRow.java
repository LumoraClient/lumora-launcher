package dev.lumora.launcher.app.social;

import dev.lumora.launcher.app.ui.Icons;
import dev.lumora.launcher.app.ui.Theme;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * One row of the social lists (friend, group, request, member, role): avatar, title, subtitle, and on the right a
 * counter, a time or small round action buttons. Rounded highlight when selected or hovered.
 */
final class SocialRow extends JComponent {
    interface Avatar {
        void paint(Graphics2D g, int x, int y, int size);
    }

    private record Action(Icons.Kind icon, String tip, Color color, Runnable run, Rectangle[] hit) {
    }

    private final Avatar avatar;
    private final String title;
    private final String subtitle;
    private final Color subtitleColor;
    private final Runnable onClick;
    private final List<Action> actions = new ArrayList<>();
    private int avatarSize = 40;
    private int badge;
    private String trailing;
    private boolean selected;
    private boolean hover;
    private Color titleColor = Theme.TEXT;
    private String strong;

    SocialRow(Avatar avatar, String title, String subtitle, Color subtitleColor, Runnable onClick) {
        this.avatar = avatar;
        this.title = title;
        this.subtitle = subtitle;
        this.subtitleColor = subtitleColor;
        this.onClick = onClick;
        setAlignmentX(Component.LEFT_ALIGNMENT);
        setPreferredSize(new Dimension(260, 58));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 58));
        setMinimumSize(new Dimension(120, 58));
        if (onClick != null) {
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }
        MouseAdapter m = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() != MouseEvent.BUTTON1) {
                    return;
                }
                for (Action a : actions) {
                    if (a.hit[0] != null && a.hit[0].contains(e.getPoint())) {
                        a.run.run();
                        return;
                    }
                }
                if (SocialRow.this.onClick != null) {
                    SocialRow.this.onClick.run();
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
                setToolTipText(null);
                repaint();
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                String tip = null;
                for (Action a : actions) {
                    if (a.hit[0] != null && a.hit[0].contains(e.getPoint())) {
                        tip = a.tip;
                    }
                }
                setToolTipText(tip);
            }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
    }

    SocialRow height(int h, int avatarSize) {
        this.avatarSize = avatarSize;
        setPreferredSize(new Dimension(260, h));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
        setMinimumSize(new Dimension(120, h));
        return this;
    }

    SocialRow badge(int count) {
        this.badge = count;
        return this;
    }

    SocialRow trailing(String text) {
        this.trailing = text;
        return this;
    }

    SocialRow selected(boolean s) {
        this.selected = s;
        return this;
    }

    /** Bold tail after the subtitle, e.g. the server in "spielt auf <b>hypixel.net</b>". */
    SocialRow strong(String text) {
        this.strong = text;
        return this;
    }

    SocialRow titleColor(Color c) {
        this.titleColor = c;
        return this;
    }

    SocialRow action(Icons.Kind icon, String tip, Color color, Runnable run) {
        actions.add(new Action(icon, tip, color, run, new Rectangle[1]));
        return this;
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        SocialPaint.quality(g);
        int w = getWidth();
        int h = getHeight();
        if (selected || (hover && onClick != null)) {
            g.setColor(selected ? SocialPaint.SELECTED : Theme.alpha(Theme.SURFACE_2, 170));
            Theme.fillRound(g, 0, 2, w, h - 4, 14, 14);
            if (selected) {
                g.setColor(Theme.ACCENT);
                Theme.fillRound(g, 0, 14, 3, h - 28, 3, 3);
            }
        }
        int ax = 12;
        int ay = (h - avatarSize) / 2;
        if (avatar != null) {
            avatar.paint(g, ax, ay, avatarSize);
        }
        int right = w - 10;
        for (int i = actions.size() - 1; i >= 0; i--) {
            Action a = actions.get(i);
            int s = 30;
            Rectangle r = new Rectangle(right - s, (h - s) / 2, s, s);
            a.hit[0] = r;
            g.setColor(Theme.alpha(a.color, 40));
            g.fillOval(r.x, r.y, s, s);
            Icons.of(a.icon, 16, a.color).paintIcon(this, g, r.x + 7, r.y + 7);
            right -= s + 6;
        }
        if (badge > 0) {
            String t = badge > 99 ? "99+" : Integer.toString(badge);
            g.setFont(Theme.font(Font.BOLD, 11.5f));
            FontMetrics bm = g.getFontMetrics();
            int bw = Math.max(22, bm.stringWidth(t) + 12);
            g.setColor(Theme.ACCENT);
            Theme.fillRound(g, right - bw, (h - 22) / 2, bw, 22, 10, 10);
            g.setColor(Color.WHITE);
            g.drawString(t, right - bw + (bw - bm.stringWidth(t)) / 2, h / 2 + 4);
            right -= bw + 8;
        } else if (trailing != null) {
            g.setFont(Theme.font(Font.PLAIN, 11.5f));
            FontMetrics tm = g.getFontMetrics();
            g.setColor(Theme.MUTED);
            g.drawString(trailing, right - tm.stringWidth(trailing), h / 2 - 6);
            right -= tm.stringWidth(trailing) + 8;
        }
        int tx = avatar != null ? ax + avatarSize + 12 : ax;
        int maxW = Math.max(20, right - tx - 4);
        g.setFont(Theme.font(Font.BOLD, 14.5f));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(titleColor);
        boolean two = subtitle != null && !subtitle.isEmpty();
        g.drawString(SocialPaint.ellipsize(title, fm, maxW), tx, two ? h / 2 - 3 : h / 2 + 5);
        if (two) {
            g.setFont(Theme.font(Font.PLAIN, 12.5f));
            g.setColor(subtitleColor);
            String sub = SocialPaint.ellipsize(subtitle, g.getFontMetrics(), maxW);
            g.drawString(sub, tx, h / 2 + 15);
            if (strong != null && sub.equals(subtitle)) {
                int sx = tx + g.getFontMetrics().stringWidth(sub + " ");
                g.setFont(Theme.font(Font.BOLD, 12.5f));
                g.setColor(Theme.TEXT);
                g.drawString(SocialPaint.ellipsize(strong, g.getFontMetrics(), Math.max(10, tx + maxW - sx)), sx, h / 2 + 15);
            }
        }
        g.dispose();
    }
}
