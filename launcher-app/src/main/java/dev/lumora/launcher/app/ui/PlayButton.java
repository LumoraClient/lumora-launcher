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

/** Large accent play button with a gradient, play glyph and an optional progress fill. */
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
        setFont(Theme.font(Font.BOLD, 20f));
        setForeground(Color.WHITE);
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
            g.setColor(Theme.alpha(Color.WHITE, 45));
            g.fillRoundRect(0, 0, (int) (w * Math.min(1, progress)), h - 4, Theme.arc(16), Theme.arc(16));
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
        Color top = !enabled ? Theme.SURFACE_2 : pressed ? Theme.ACCENT_PRESSED : hover ? Theme.ACCENT_HOVER : Theme.ACCENT;
        Color bottom = !enabled ? Theme.SURFACE_2 : Theme.mix(top, Theme.mix(Theme.ACCENT, new Color(0xD946EF), 0.35), 0.55).darker();
        int arc = Theme.arc(16);
        if (enabled) {
            g.setColor(Theme.alpha(Theme.ACCENT, hover ? 80 : 50));
            g.fillRoundRect(2, 6, w - 4, h - 4, arc + 2, arc + 2);
        }
        g.setPaint(new GradientPaint(0, 0, top, w, h, bottom));
        g.fillRoundRect(0, 0, w, h - 4, arc, arc);
        if (squareSide == 1) {
            g.fillRect(0, 0, arc, h - 4);
        } else if (squareSide == 2) {
            g.fillRect(w - arc, 0, arc, h - 4);
        }
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
