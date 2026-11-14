package dev.lumora.launcher.app.ui;

import javax.swing.JButton;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Large accent play button: a slowly flowing blue gradient with a light sweep and a breathing glow, play glyph and an
 * optional progress fill with moving stripes. Animated by {@link Motion} only while it is visible.
 */
public final class PlayButton extends JButton {
    private double progress = -1;
    /** Square right corners: an {@link Arrow} sits directly next to it (split button). */
    private boolean split;

    public PlayButton(String text) {
        super(text);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setPreferredSize(new Dimension(240, 56));
        setFont(Theme.display(24f, Font.BOLD, 20f));
        setForeground(Color.WHITE);
    }

    @Override
    public void addNotify() {
        super.addNotify();
        Motion.loop(this);
    }

    @Override
    public void removeNotify() {
        Motion.stop(this);
        super.removeNotify();
    }

    /** Right corners square, for a split button with {@link Arrow}. */
    public PlayButton split() {
        split = true;
        return this;
    }

    /** 0..1 shows a fill inside the button (download/install), negative hides it. */
    public void setProgress(double p) {
        this.progress = p;
        repaint();
    }

    private static boolean playText(String t) {
        return "Spielen".equals(t) || "Spiel starten".equals(t);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        int w = getWidth();
        int h = getHeight();
        boolean enabled = isEnabled();
        boolean pressed = getModel().isPressed();
        boolean hover = getModel().isRollover();
        fill(g, w, h, enabled, pressed, hover, split ? 0 : 2);
        if (progress >= 0) {
            int pw = (int) (w * Math.min(1, progress));
            java.awt.Shape clip = g.getClip();
            g.clip(Theme.roundShape(0, 0, pw, h - 4, Theme.arc(16), Theme.arc(16)));
            g.setColor(Theme.alpha(Color.WHITE, 40));
            g.fillRect(0, 0, pw, h - 4);
            // moving stripes: the download is alive even when the percentage hardly changes
            int off = (int) (Motion.seconds() * 28) % 24;
            g.setColor(Theme.alpha(Color.WHITE, 26));
            for (int sx = -h + off - 24; sx < pw; sx += 24) {
                g.fillPolygon(new int[]{sx, sx + 12, sx + 12 + h, sx + h}, new int[]{h, h, 0, 0}, 4);
            }
            g.setClip(clip);
        }
        String text = getText();
        g.setFont(getFont());
        FontMetrics fm = g.getFontMetrics();
        int iconSize = 18;
        boolean showIcon = enabled && playText(text);
        int total = fm.stringWidth(text) + (showIcon ? iconSize + 10 : 0);
        int x = (w - total) / 2;
        int cy = (h - 4) / 2;
        Color fg = enabled ? Theme.onAccent() : Theme.MUTED;
        if (showIcon) {
            Icons.of(Icons.Kind.PLAY, iconSize, fg).paintIcon(this, g, x, cy - iconSize / 2);
            x += iconSize + 10;
        }
        g.setColor(fg);
        g.drawString(text, x, cy - fm.getHeight() / 2 + fm.getAscent());
        g.dispose();
    }

    /**
     * Accent gradient body (shared by the play button and its arrow).
     *
     * @param squareSide 0 = round, 1 = square left side (arrow), 2 = square right side (split play button)
     */
    static void fill(Graphics2D g, int w, int h, boolean enabled, boolean pressed, boolean hover, int squareSide) {
        int arc = Theme.arc(16);
        double t = Motion.seconds();
        java.awt.Shape body = squareSide == 0
                ? Theme.roundShape(0, 0, w, h - 4, arc, arc)
                : unionSquare(w, h - 4, arc, squareSide);
        if (!enabled) {
            g.setColor(Theme.SURFACE_2);
            g.fill(body);
            return;
        }
        if (Theme.pixelStyle) {
            // Minecraft-like button: a darker bottom edge below the body instead of the glow
            g.setColor(Theme.ACCENT_DEEP);
            if (squareSide == 0) {
                Theme.fillRound(g, 0, 4, w, h - 4, arc, arc);
            } else {
                g.fill(unionSquare(w, h - 4, arc, squareSide, 4));
            }
        }
        // breathing glow under the button
        double breath = 0.5 + 0.5 * Math.sin(t * 2.1);
        if (!Theme.pixelStyle) {
            g.setColor(Theme.alpha(Theme.ACCENT, (int) ((hover ? 85 : 45) + breath * 30)));
            Theme.fillRound(g, 2, 6, w - 4, h - 4, arc + 2, arc + 2);
        }
        // flowing gradient: accent -> ice blue -> accent, drifting slowly to the right
        g.setPaint(Theme.flow(0, 0, w * 1.4f + 1, h * 0.6f, t * 0.12, 255));
        g.fill(body);
        if (hover || pressed) {
            g.setColor(pressed ? Theme.alpha(Color.BLACK, 50) : Theme.alpha(Color.WHITE, 26));
            g.fill(body);
        }
        // light sweep every few seconds
        double cycle = (t % 3.6) / 3.6;
        float sx = (float) (-0.4 * w + cycle * 1.8 * w);
        java.awt.Shape clip = g.getClip();
        g.clip(body);
        g.setPaint(new java.awt.LinearGradientPaint(sx, 0, sx + w * 0.35f + 1, h * 0.5f,
                new float[]{0f, 0.5f, 1f}, new Color[]{Theme.alpha(Color.WHITE, 0), Theme.alpha(Color.WHITE, 55), Theme.alpha(Color.WHITE, 0)}));
        g.fillRect(0, 0, w, h);
        // top highlight
        g.setPaint(new GradientPaint(0, 0, Theme.alpha(Color.WHITE, 40), 0, h * 0.5f, Theme.alpha(Color.WHITE, 0)));
        g.fillRect(0, 0, w, h / 2);
        g.setClip(clip);
    }

    /** Round rectangle with one square side (split button halves). */
    private static java.awt.Shape unionSquare(int w, int h, int arc, int squareSide) {
        return unionSquare(w, h, arc, squareSide, 0);
    }

    private static java.awt.Shape unionSquare(int w, int h, int arc, int squareSide, int dy) {
        java.awt.geom.Area a = new java.awt.geom.Area(Theme.roundShape(0, dy, w, h, arc, arc));
        // the square side covers the rounded (or stepped) corners there
        int edge = Math.max(arc, 6);
        a.add(new java.awt.geom.Area(squareSide == 1 ? new java.awt.Rectangle(0, dy, edge, h) : new java.awt.Rectangle(w - edge, dy, edge, h)));
        return a;
    }

    /** Small accent button with a down arrow that opens a menu; placed right next to a split {@link PlayButton}. */
    public static final class Arrow extends JButton {
        public Arrow() {
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(46, 56));
            setToolTipText("Weitere Startoptionen");
        }

        @Override
        public void addNotify() {
            super.addNotify();
            Motion.loop(this);
        }

        @Override
        public void removeNotify() {
            Motion.stop(this);
            super.removeNotify();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            fill(g, w, h, isEnabled(), getModel().isPressed(), getModel().isRollover(), 1);
            g.setColor(Theme.alpha(Color.BLACK, 60));
            g.fillRect(0, 6, 1, h - 16);
            Icons.of(Icons.Kind.CHEVRON_DOWN, 18, isEnabled() ? Theme.onAccent() : Theme.MUTED)
                    .paintIcon(this, g, (w - 18) / 2, (h - 4 - 18) / 2);
            g.dispose();
        }
    }
}
