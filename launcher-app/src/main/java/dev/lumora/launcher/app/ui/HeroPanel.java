package dev.lumora.launcher.app.ui;

import javax.swing.JPanel;
import java.awt.AlphaComposite;
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
 * Hero banner with a procedurally generated voxel night scene in the accent colour (own artwork, no third-party
 * assets): snow-capped mountain ranges under a full moon with a pine forest in front. The scene is rendered once per
 * size into a cached image; on top of it an aurora of soft light drifts slowly and a few stars twinkle (animated while
 * visible, off with reduce motion). Children (logo, title, buttons) lie above.
 */
public final class HeroPanel extends JPanel {
    private BufferedImage cache;
    /** Low-resolution buffer for the aurora (scaled up smoothly: cheap to animate). */
    private BufferedImage veil;

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
            cache = render(w, h, true);
        }
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        java.awt.Shape clip0 = g.getClip();
        g.clip(new java.awt.geom.RoundRectangle2D.Float(0, 0, w, h, Theme.arc(20), Theme.arc(20)));
        g.drawImage(cache, 0, 0, null);
        int vw = Math.max(8, w / 6);
        int vh = Math.max(8, h / 6);
        if (veil == null || veil.getWidth() != vw || veil.getHeight() != vh) {
            veil = new BufferedImage(vw, vh, BufferedImage.TYPE_INT_ARGB);
        }
        Graphics2D vg = veil.createGraphics();
        vg.setComposite(AlphaComposite.Clear);
        vg.fillRect(0, 0, vw, vh);
        vg.setComposite(AlphaComposite.SrcOver);
        aurora(vg, vw, vh, Motion.seconds(), false);
        vg.dispose();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(veil, 0, 0, w, h, null);
        aurora(g, w, h, Motion.seconds(), true);
        g.setClip(clip0);
        g.setColor(Theme.alpha(Theme.ACCENT_HOVER, 40));
        Theme.drawRound(g, 0, 0, w - 1, h - 1, Theme.arc(20), Theme.arc(20));
        g.dispose();
        super.paintComponent(g0);
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

    /** Flowing light: two aurora veils drifting across the sky and twinkling stars. */
    private static void aurora(Graphics2D g, int w, int h, double t, boolean stars) {
        if (stars) {
            twinkle(g, w, h, t);
            return;
        }
        float r = Math.max(w, h) * 0.38f;
        float ax = (float) (w * (0.30 + 0.18 * Math.sin(t * 0.21)));
        float ay = (float) (h * (0.28 + 0.08 * Math.cos(t * 0.33)));
        g.setPaint(new RadialGradientPaint(new Point2D.Float(ax, ay), r, new float[]{0f, 1f},
                new Color[]{Theme.alpha(Theme.ACCENT, 58), new Color(0, 0, 0, 0)}));
        g.fillRect(0, 0, w, h);
        float bx = (float) (w * (0.68 + 0.16 * Math.cos(t * 0.17)));
        float by = (float) (h * (0.22 + 0.10 * Math.sin(t * 0.27)));
        g.setPaint(new RadialGradientPaint(new Point2D.Float(bx, by), r * 0.85f, new float[]{0f, 1f},
                new Color[]{Theme.alpha(Theme.ACCENT_2, 46), new Color(0, 0, 0, 0)}));
        g.fillRect(0, 0, w, h);
    }

    private static void twinkle(Graphics2D g, int w, int h, double t) {
        Random rnd = new Random(7);
        for (int i = 0; i < 26; i++) {
            int sx = rnd.nextInt(Math.max(1, w));
            int sy = rnd.nextInt(Math.max(1, (int) (h * 0.5)));
            double tw = 0.5 + 0.5 * Math.sin(t * (1.2 + rnd.nextDouble() * 2.2) + rnd.nextDouble() * 6.3);
            g.setColor(new Color(220, 238, 255, (int) (40 + 170 * tw * tw)));
            g.fillRect(sx, sy, 2, 2);
        }
    }

    static BufferedImage render(int w, int h) {
        return render(w, h, true);
    }

    /**
     * @param hero true: darker centre and fade into the page at the bottom (launcher banner); false: the plain scene
     */
    public static BufferedImage render(int w, int h, boolean hero) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Random rnd = new Random(26_1_2 + 2 * 7919L);
        Color accent = Theme.ACCENT;
        Color deep = Theme.mix(new Color(0x040A18), accent, 0.20);
        Color dusk = Theme.mix(new Color(0x0E2552), accent, 0.45);

        // sky: deep night on top, accent dusk towards the horizon
        g.setPaint(new GradientPaint(0, 0, deep, 0, h * 0.75f, dusk));
        g.fillRect(0, 0, w, h);
        // ice-blue glow on the right horizon, accent glow on the left
        g.setPaint(new RadialGradientPaint(new Point2D.Float(w * 0.82f, h * 0.62f), Math.max(w, h) * 0.45f,
                new float[]{0f, 0.4f, 1f},
                new Color[]{new Color(150, 225, 255, 130), Theme.alpha(Theme.mix(accent, new Color(0x22D3EE), 0.5), 80), new Color(0, 0, 0, 0)}));
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

        mountains(g, rnd, w, h, Math.max(8, h / 34));

        if (!hero) {
            g.dispose();
            return img;
        }
        // centre vignette for the text, fade into the page at the bottom
        g.setPaint(new RadialGradientPaint(new Point2D.Float(w / 2f, h * 0.45f), Math.max(w, h) * 0.42f,
                new float[]{0f, 1f}, new Color[]{new Color(4, 8, 20, 150), new Color(4, 8, 20, 0)}));
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

    /** Full moon as a blocky disc with a few darker craters and a soft glow. */
    private static void moon(Graphics2D g, int cx, int cy, int r, int b) {
        g.setPaint(new RadialGradientPaint(new Point2D.Float(cx, cy), r * 3.2f, new float[]{0f, 1f},
                new Color[]{new Color(200, 230, 255, 90), new Color(0, 0, 0, 0)}));
        g.fillRect(cx - r * 4, cy - r * 4, r * 8, r * 8);
        int step = Math.max(4, b / 2);
        for (int y = -r; y <= r; y += step) {
            for (int x = -r; x <= r; x += step) {
                if (x * x + y * y <= r * r) {
                    boolean crater = ((x / step) * 7 + (y / step) * 13) % 11 == 0;
                    g.setColor(crater ? new Color(0xC9D9EE) : new Color(0xEAF3FF));
                    g.fillRect(cx + x, cy + y, step, step);
                }
            }
        }
    }

    /** A range of blocky peaks; the highest get snow caps. */
    private static void peaks(Graphics2D g, Random rnd, int w, int h, int b, Color body, Color snow, double base, double height, int count) {
        double[] px = new double[count];
        double[] ph = new double[count];
        for (int i = 0; i < count; i++) {
            px[i] = (i + rnd.nextDouble() * 0.8) / count;
            ph[i] = 0.5 + rnd.nextDouble() * 0.5;
        }
        for (int x = 0; x < w + b; x += b) {
            double t = x / (double) w;
            double best = 0;
            for (int i = 0; i < count; i++) {
                best = Math.max(best, ph[i] * (1 - Math.abs(t - px[i]) * count * 1.1));
            }
            int top = (int) (h * (base - height * Math.max(0, best))) / b * b;
            g.setColor(body);
            g.fillRect(x, top, b, h - top);
            if (best > 0.55) {
                g.setColor(snow);
                g.fillRect(x, top, b, b * (best > 0.8 ? 2 : 1));
            }
        }
    }

    /** Moon, two mountain ranges, a hill line and pines (fewer in the middle, where the text is). */
    private static void mountains(Graphics2D g, Random rnd, int w, int h, int b) {
        Color accent = Theme.ACCENT;
        moon(g, (int) (w * 0.80), (int) (h * 0.24), Math.max(18, h / 9), b);
        Color far = Theme.mix(new Color(0x10213F), accent, 0.30);
        peaks(g, rnd, w, h, b, far, Theme.mix(far, Color.WHITE, 0.35), 0.66, 0.40, 6);
        Color mid = Theme.mix(new Color(0x0A162E), accent, 0.18);
        peaks(g, rnd, w, h, b, mid, Theme.mix(mid, Color.WHITE, 0.45), 0.78, 0.36, 4);
        Color near = Theme.mix(new Color(0x050B1A), accent, 0.10);
        hills(g, rnd, w, h, b, near, Theme.mix(near, accent, 0.25), 0.86, 0.04);
        int ground = (int) (h * 0.88);
        for (int x = -b; x < w + b; x += b * (2 + rnd.nextInt(4))) {
            if (x > w * 0.30 && x < w * 0.70 && rnd.nextInt(3) > 0) {
                continue;
            }
            int levels = 3 + rnd.nextInt(3);
            g.setColor(near);
            for (int l = 0; l < levels; l++) {
                int lw = (levels - l) * b;
                g.fillRect(x - lw / 2 + b / 2, ground - (l + 1) * b - b, lw, b);
            }
            g.fillRect(x + b / 4, ground - b, Math.max(3, b / 2), b * 2);
        }
    }
}
