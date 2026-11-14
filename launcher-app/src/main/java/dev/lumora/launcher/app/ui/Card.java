package dev.lumora.launcher.app.ui;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.AWTEvent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.MouseEvent;

/**
 * Flat rounded surface with optional accent outline (selected state). Under the mouse the outline brightens a little; the change glides ({@link Motion.Tween}). One global mouse listener
 * finds the card under the pointer, so cards full of buttons and labels still react as a whole.
 */
public class Card extends JPanel {
    private static Card hovered;
    private static boolean listening;

    private Color fill = Theme.SURFACE;
    private Color outline = Theme.BORDER;
    private int radius = Theme.RADIUS;
    private boolean hoverGlow = true;
    private final Motion.Tween glow = new Motion.Tween(0, 220);

    public Card(LayoutManager layout) {
        super(layout);
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
        listen();
    }

    public Card fill(Color c) {
        this.fill = c;
        repaint();
        return this;
    }

    public Card outline(Color c) {
        this.outline = c;
        repaint();
        return this;
    }

    public Card radius(int r) {
        this.radius = r;
        return this;
    }

    /** No hover glow (large containers such as page sections). */
    public Card calm() {
        this.hoverGlow = false;
        return this;
    }

    public Card padding(int top, int left, int bottom, int right) {
        setBorder(BorderFactory.createEmptyBorder(top, left, bottom, right));
        return this;
    }

    private static synchronized void listen() {
        if (listening) {
            return;
        }
        listening = true;
        Toolkit.getDefaultToolkit().addAWTEventListener(e -> {
            if (!(e instanceof MouseEvent me) || !(me.getSource() instanceof Component src)) {
                return;
            }
            int id = me.getID();
            if (id != MouseEvent.MOUSE_MOVED && id != MouseEvent.MOUSE_ENTERED && id != MouseEvent.MOUSE_EXITED
                    && id != MouseEvent.MOUSE_DRAGGED) {
                return;
            }
            Card target = null;
            if (id != MouseEvent.MOUSE_EXITED || src.contains(me.getPoint())) {
                Component c = src;
                while (c != null) {
                    if (c instanceof Card card && card.hoverGlow) {
                        target = card;
                        break;
                    }
                    c = c.getParent();
                }
            }
            SwingUtilities.invokeLater(new HoverChange(target));
        }, AWTEvent.MOUSE_MOTION_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK);
    }

    private record HoverChange(Card target) implements Runnable {
        @Override
        public void run() {
            if (target == hovered) {
                return;
            }
            if (hovered != null && hovered.glow.set(0)) {
                Motion.during(hovered, hovered.glow.millis());
            }
            hovered = target;
            if (target != null && target.glow.set(1)) {
                Motion.during(target, target.glow.millis());
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();
        double hv = glow.value();
        g.setColor(fill);
        Theme.fillRound(g, 0, 0, w - 1, h - 1, radius, radius);
        if (outline != null) {
            // minimal hover: the outline only brightens a little
            boolean selected = outline == Theme.ACCENT;
            Color c = selected ? outline : Theme.mix(outline, Theme.mix(Theme.BORDER, Theme.TEXT, 0.35), hv);
            g.setColor(c);
            g.setStroke(new BasicStroke(selected ? 1.5f : 1f));
            Theme.drawRound(g, 0, 0, w - 1, h - 1, radius, radius);
        }
        g.dispose();
        super.paintComponent(g0);
    }
}
