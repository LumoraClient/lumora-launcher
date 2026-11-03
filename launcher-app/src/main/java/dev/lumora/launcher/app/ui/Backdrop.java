package dev.lumora.launcher.app.ui;

import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;

/**
 * Window background: plain deep navy with one very soft accent light from the top right. Rendered once per window size (no per-frame cost); pages and the navigation lie on top of it.
 */
public final class Backdrop extends JPanel {
    private BufferedImage cache;

    public Backdrop() {
        super(new BorderLayout());
        setOpaque(true);
        setBackground(Theme.BG);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        if (cache == null || cache.getWidth() != w || cache.getHeight() != h) {
            cache = render(w, h);
        }
        g0.drawImage(cache, 0, 0, null);
    }

    static BufferedImage render(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Theme.BG);
        g.fillRect(0, 0, w, h);
        // one very soft accent light at the top right, nothing else (minimal)
        float r = Math.max(w, h) * 0.6f;
        g.setPaint(new RadialGradientPaint(new Point2D.Float(w * 0.95f, -h * 0.1f), r, new float[]{0f, 1f},
                new Color[]{Theme.alpha(Theme.ACCENT, 20), Theme.alpha(Theme.ACCENT, 0)}));
        g.fillRect(0, 0, w, h);
        g.dispose();
        return img;
    }
}
