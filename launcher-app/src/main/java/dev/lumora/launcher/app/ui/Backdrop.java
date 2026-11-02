package dev.lumora.launcher.app.ui;

import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;

/**
 * Window background: deep navy with soft blue light from the top right and ice blue from the bottom left, plus a very
 * fine dot grid. Rendered once per window size (no per-frame cost); pages and the navigation lie on top of it.
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
        g.setPaint(new GradientPaint(0, 0, Theme.mix(Theme.BG, Theme.ACCENT_DEEP, 0.10), 0, h, Theme.BG));
        g.fillRect(0, 0, w, h);
        float r = Math.max(w, h) * 0.55f;
        g.setPaint(new RadialGradientPaint(new Point2D.Float(w * 0.92f, -h * 0.05f), r, new float[]{0f, 1f},
                new Color[]{Theme.alpha(Theme.ACCENT, 46), Theme.alpha(Theme.ACCENT, 0)}));
        g.fillRect(0, 0, w, h);
        g.setPaint(new RadialGradientPaint(new Point2D.Float(w * 0.18f, h * 1.05f), r * 0.8f, new float[]{0f, 1f},
                new Color[]{Theme.alpha(Theme.ACCENT_2, 26), Theme.alpha(Theme.ACCENT_2, 0)}));
        g.fillRect(0, 0, w, h);
        g.setColor(new Color(255, 255, 255, 7));
        for (int y = 12; y < h; y += 24) {
            for (int x = 12; x < w; x += 24) {
                g.fillRect(x, y, 1, 1);
            }
        }
        g.dispose();
        return img;
    }
}
