package dev.lumora.launcher.app.ui;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * Hero banner with a procedurally generated voxel dusk landscape in the accent colour: starry sky, a glowing horizon,
 * blocky forest on the left and a castle silhouette on the right (own artwork, no third-party assets). Rendered once
 * per size into a cached image; children (logo, title, buttons) are laid out on top.
 */
public final class HeroPanel extends JPanel {
    private BufferedImage cache;

    public HeroPanel(LayoutManager layout) {
        super(layout);
        setOpaque(false);
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
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        java.awt.Shape clip0 = g.getClip();
        g.clip(new java.awt.geom.RoundRectangle2D.Float(0, 0, w, h, Theme.arc(20), Theme.arc(20)));
        g.drawImage(cache, 0, 0, null);
        g.setClip(clip0);
        g.setColor(Theme.alpha(Theme.ACCENT_HOVER, 40));
        g.drawRoundRect(0, 0, w - 1, h - 1, Theme.arc(20), Theme.arc(20));
        g.dispose();
        super.paintComponent(g0);
    }

    static BufferedImage render(int w, int h) {
        return render(w, h, true);
    }

    /**
     * @param hero true: darker centre and fade into the page at the bottom (launcher banner); false: the plain scene
     *             (in-game menu background, generated once into the client mods)
     */
    public static BufferedImage render(int w, int h, boolean hero) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Random rnd = new Random(26_1_2);
        Color accent = Theme.ACCENT;
        Color deep = Theme.mix(new Color(0x0B0716), accent, 0.22);
        Color dusk = Theme.mix(new Color(0x2A1450), accent, 0.45);

        // sky: deep night on top, accent dusk towards the horizon
        g.setPaint(new GradientPaint(0, 0, deep, 0, h * 0.75f, dusk));
        g.fillRect(0, 0, w, h);
        // warm sunset glow on the right horizon, cool accent glow on the left
        g.setPaint(new RadialGradientPaint(new Point2D.Float(w * 0.82f, h * 0.62f), Math.max(w, h) * 0.45f,
                new float[]{0f, 0.4f, 1f},
                new Color[]{new Color(255, 170, 140, 150), Theme.alpha(Theme.mix(accent, new Color(0xFF7AB6), 0.5), 70), new Color(0, 0, 0, 0)}));
        g.fillRect(0, 0, w, h);
        g.setPaint(new RadialGradientPaint(new Point2D.Float(w * 0.18f, h * 0.35f), Math.max(w, h) * 0.4f,
                new float[]{0f, 1f}, new Color[]{Theme.alpha(accent, 90), new Color(0, 0, 0, 0)}));
        g.fillRect(0, 0, w, h);

        // stars (small squares, voxel style)
        for (int i = 0; i < w * h / 2200; i++) {
            int sx = rnd.nextInt(w);
            int sy = rnd.nextInt((int) (h * 0.55));
            int a = 60 + rnd.nextInt(170);
            int s = rnd.nextInt(10) == 0 ? 3 : 2;
            g.setColor(new Color(255, 255, 255, a));
            g.fillRect(sx, sy, s, s);
        }

        int block = Math.max(8, h / 34);
        // far hills
        Color far = Theme.mix(new Color(0x1C1030), accent, 0.30);
        hills(g, rnd, w, h, block, far, Theme.mix(far, accent, 0.35), 0.70, 0.08);
        // castle on the right
        castle(g, w, h, block, Theme.mix(new Color(0x120A20), accent, 0.12), Theme.alpha(new Color(0xFFC58A), 200));
        // near forest layer
        Color near = Theme.mix(new Color(0x0C0716), accent, 0.10);
        hills(g, rnd, w, h, block, near, Theme.mix(near, accent, 0.25), 0.84, 0.06);
        trees(g, rnd, w, h, block, near);

        if (!hero) {
            g.dispose();
            return img;
        }
        // centre vignette for the text, fade into the page at the bottom
        g.setPaint(new RadialGradientPaint(new Point2D.Float(w / 2f, h * 0.45f), Math.max(w, h) * 0.42f,
                new float[]{0f, 1f}, new Color[]{new Color(8, 5, 16, 150), new Color(8, 5, 16, 0)}));
        g.fillRect(0, 0, w, h);
        Color bg = Theme.BG;
        g.setPaint(new GradientPaint(0, h * 0.62f, Theme.alpha(bg, 0), 0, h, Theme.alpha(bg, 235)));
        g.fillRect(0, 0, w, h);
        g.dispose();
        return img;
    }

    private static void hills(Graphics2D g, Random rnd, int w, int h, int block, Color body, Color rim, double base, double amp) {
        double p1 = rnd.nextDouble() * 10;
        double p2 = rnd.nextDouble() * 10;
        for (int x = 0; x < w + block; x += block) {
            double t = x / (double) w;
            double n = Math.sin(t * Math.PI * 2.2 + p1) * 0.6 + Math.sin(t * Math.PI * 6.1 + p2) * 0.4;
            int top = (int) (h * (base - amp * n)) / block * block;
            g.setColor(body);
            g.fillRect(x, top, block, h - top);
            g.setColor(rim);
            g.fillRect(x, top, block, Math.max(2, block / 4));
        }
    }

    private static void castle(Graphics2D g, int w, int h, int b, Color stone, Color light) {
        int ground = (int) (h * 0.74) / b * b;
        int x0 = (int) (w * 0.74) / b * b;
        g.setColor(stone);
        // walls and towers (heights in blocks)
        int[][] parts = {{0, 7, 9}, {2, 5, 13}, {7, 4, 17}, {11, 6, 11}, {17, 3, 15}, {20, 8, 8}};
        for (int[] p : parts) {
            int px = x0 + p[0] * b;
            int top = ground - p[2] * b;
            g.fillRect(px, top, p[1] * b, ground - top + b);
            // battlements
            for (int i = 0; i < p[1]; i += 2) {
                g.fillRect(px + i * b, top - b, b, b);
            }
        }
        // spire on the main tower
        g.fillPolygon(new int[]{x0 + 7 * b, x0 + 9 * b, x0 + 11 * b}, new int[]{ground - 17 * b, ground - 23 * b, ground - 17 * b}, 3);
        // lit windows
        g.setColor(light);
        int[][] windows = {{8, 12}, {9, 9}, {3, 9}, {12, 7}, {18, 10}, {21, 5}, {13, 5}};
        for (int[] wnd : windows) {
            g.fillRect(x0 + wnd[0] * b + b / 4, ground - wnd[1] * b, Math.max(3, b / 2), Math.max(4, b * 3 / 4));
        }
    }

    private static void trees(Graphics2D g, Random rnd, int w, int h, int b, Color dark) {
        int ground = (int) (h * 0.80);
        for (int x = -b; x < w * 0.42; x += b * (2 + rnd.nextInt(3))) {
            int height = b * (4 + rnd.nextInt(5));
            int top = ground - height;
            g.setColor(dark.darker());
            g.fillRect(x + b, top + b * 2, Math.max(3, b / 2), height);
            g.setColor(dark);
            g.fillRect(x - b / 2, top, b * 3, b * 3);
            g.fillRect(x, top - b, b * 2, b);
        }
    }
}
