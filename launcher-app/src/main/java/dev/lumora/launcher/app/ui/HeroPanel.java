package dev.lumora.launcher.app.ui;

import dev.lumora.launcher.core.i18n.Lang;

import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.Point;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * Hero banner with procedurally generated voxel night scenes in the accent colour (own artwork, no third-party
 * assets): a castle by the forest, a dragon over the castle, mountains under the moon, floating islands and a
 * lighthouse by the sea. The scenes change every few seconds with a cross-fade (not with reduce motion); the dots at
 * the bottom right pick one. Each scene is rendered once per size into a cached image; on top of it an aurora of soft
 * light drifts slowly and a few stars twinkle. Children (logo, title, buttons) lie above.
 */
public final class HeroPanel extends JPanel {
    public static final int SCENES = 5;
    private static final int DOT = 10;
    private static final int DOT_GAP = 16;
    private static final int FADE_MS = 900;

    private final BufferedImage[] cache = new BufferedImage[SCENES];
    /** Low-resolution buffer for the aurora (scaled up smoothly: cheap to animate). */
    private BufferedImage veil;
    private int scene;
    private int previous = -1;
    private long fadeStart;
    private final Timer auto = new Timer(9000, e -> {
        if (isShowing()) {
            show((scene + 1) % SCENES);
        }
    });

    public HeroPanel(LayoutManager layout) {
        super(layout);
        setOpaque(false);
        auto.setRepeats(true);
        MouseAdapter dots = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int d = dotAt(e.getPoint());
                if (d >= 0) {
                    show(d);
                    auto.restart();
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                setCursor(dotAt(e.getPoint()) >= 0 ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
            }
        };
        addMouseListener(dots);
        addMouseMotionListener(dots);
        setToolTipText("");
    }

    /** Shows scene {@code index} (cross-fade from the current one). */
    public void show(int index) {
        if (index == scene || index < 0 || index >= SCENES) {
            return;
        }
        previous = scene;
        scene = index;
        fadeStart = System.nanoTime();
        Motion.during(this, FADE_MS + 50);
        repaint();
    }

    public int scene() {
        return scene;
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        int d = dotAt(e.getPoint());
        return d < 0 ? null : Lang.t("home.hero.slide", d + 1, SCENES);
    }

    /** Index of the dot under {@code p}, or -1. */
    private int dotAt(Point p) {
        int y = dotY();
        if (Math.abs(p.y - y) > 10) {
            return -1;
        }
        for (int i = 0; i < SCENES; i++) {
            if (Math.abs(p.x - dotX(i)) <= DOT_GAP / 2) {
                return i;
            }
        }
        return -1;
    }

    private int dotX(int i) {
        return getWidth() - 30 - (SCENES - 1 - i) * (DOT + DOT_GAP - 6);
    }

    private int dotY() {
        return getHeight() - 30;
    }

    private BufferedImage scene(int i, int w, int h) {
        if (cache[i] == null || cache[i].getWidth() != w || cache[i].getHeight() != h) {
            cache[i] = render(w, h, true, i);
        }
        return cache[i];
    }

    @Override
    protected void paintComponent(Graphics g0) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        java.awt.Shape clip0 = g.getClip();
        g.clip(new java.awt.geom.RoundRectangle2D.Float(0, 0, w, h, Theme.arc(20), Theme.arc(20)));
        double fade = previous < 0 ? 1 : Math.min(1, (System.nanoTime() - fadeStart) / (FADE_MS * 1e6));
        if (fade < 1 && !Theme.reduceMotion) {
            g.drawImage(scene(previous, w, h), 0, 0, null);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) Motion.easeInOut(fade)));
            g.drawImage(scene(scene, w, h), 0, 0, null);
            g.setComposite(AlphaComposite.SrcOver);
        } else {
            previous = -1;
            g.drawImage(scene(scene, w, h), 0, 0, null);
        }
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
        // carousel dots (bottom right)
        for (int i = 0; i < SCENES; i++) {
            int x = dotX(i) - DOT / 2;
            int y = dotY() - DOT / 2;
            if (i == scene) {
                g.setColor(Theme.alpha(Theme.ACCENT_HOVER, 90));
                g.fillOval(x - 3, y - 3, DOT + 6, DOT + 6);
                g.setColor(Theme.ACCENT_HOVER);
            } else {
                g.setColor(new Color(255, 255, 255, 90));
            }
            g.fillOval(x, y, DOT, DOT);
        }
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
        if (!Theme.reduceMotion) {
            auto.start();
        }
    }

    @Override
    public void removeNotify() {
        Motion.stop(this);
        auto.stop();
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
        return render(w, h, true, 0);
    }

    public static BufferedImage render(int w, int h, boolean hero) {
        return render(w, h, hero, 0);
    }

    /**
     * @param hero  true: darker centre and fade into the page at the bottom (launcher banner); false: the plain scene
     * @param scene 0 castle, 1 dragon over the castle, 2 mountains and moon, 3 floating islands, 4 lighthouse
     */
    public static BufferedImage render(int w, int h, boolean hero, int scene) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Random rnd = new Random(26_1_2 + scene * 7919L);
        Color accent = Theme.ACCENT;
        Color deep = Theme.mix(new Color(0x040A18), accent, 0.20);
        Color dusk = Theme.mix(new Color(0x0E2552), accent, 0.45);

        // sky: deep night on top, accent dusk towards the horizon
        g.setPaint(new GradientPaint(0, 0, deep, 0, h * 0.75f, dusk));
        g.fillRect(0, 0, w, h);
        // ice-blue glow on the right horizon, accent glow on the left
        g.setPaint(new RadialGradientPaint(new Point2D.Float(w * 0.82f, h * 0.62f), Math.max(w, h) * 0.45f,
                new float[]{0f, 0.4f, 1f},
                new Color[]{new Color(150, 225, 255, scene == 1 ? 70 : 130), Theme.alpha(Theme.mix(accent, new Color(0x22D3EE), 0.5), 80), new Color(0, 0, 0, 0)}));
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
        switch (scene) {
            case 2 -> mountains(g, rnd, w, h, block);
            case 3 -> islands(g, rnd, w, h, block);
            case 4 -> lighthouse(g, rnd, w, h, block);
            default -> {
                Color far = Theme.mix(new Color(0x0B1A36), accent, 0.30);
                hills(g, rnd, w, h, block, far, Theme.mix(far, accent, 0.35), 0.70, 0.08);
                castle(g, w, h, block, Theme.mix(new Color(0x07122A), accent, 0.12), Theme.alpha(new Color(0xBFE8FF), 210));
                if (scene == 1) {
                    dragon(g, w, h);
                }
                Color near = Theme.mix(new Color(0x050B1A), accent, 0.10);
                hills(g, rnd, w, h, block, near, Theme.mix(near, accent, 0.25), 0.84, 0.06);
                trees(g, rnd, w, h, block, near);
            }
        }

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

    // ------------------------------------------------------------------ dragon (scene 1)

    /**
     * A winged dragon facing left over the castle: drawn on a small grid and scaled up without smoothing (pixel
     * look), with a light rim on the upper edges, a soft shadow and glowing eyes.
     */
    private static void dragon(Graphics2D g, int w, int h) {
        int lw = 64;
        int lh = 40;
        BufferedImage s = new BufferedImage(lw, lh, BufferedImage.TYPE_INT_ARGB);
        Graphics2D d = s.createGraphics();
        Color body = Theme.mix(new Color(0x180E30), Theme.ACCENT, 0.28);
        Color shade = Theme.mix(body, Color.BLACK, 0.40);
        Color wing = Theme.mix(body, Theme.ACCENT_HOVER, 0.22);
        Color bone = Theme.mix(body, Color.BLACK, 0.20);
        // far wing (behind the body)
        d.setColor(shade);
        d.fillPolygon(new int[]{34, 38, 44, 52, 63, 57, 61, 53, 55, 46, 44}, new int[]{18, 8, 2, 0, 3, 7, 12, 12, 18, 17, 22}, 11);
        // tail
        d.setColor(body);
        d.setStroke(new BasicStroke(3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        d.drawPolyline(new int[]{45, 53, 58, 62, 61, 57}, new int[]{23, 26, 25, 28, 33, 35}, 6);
        d.fillPolygon(new int[]{56, 59, 53}, new int[]{33, 39, 37}, 3);
        // legs
        d.fillPolygon(new int[]{27, 30, 29, 31, 26, 26}, new int[]{25, 25, 31, 33, 33, 31}, 6);
        d.fillPolygon(new int[]{38, 42, 42, 44, 39, 39}, new int[]{25, 24, 30, 33, 33, 30}, 6);
        // body
        d.fillPolygon(new int[]{22, 27, 36, 44, 48, 45, 36, 27, 22}, new int[]{20, 16, 15, 18, 22, 26, 28, 27, 24}, 9);
        // neck and head
        d.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        d.drawPolyline(new int[]{26, 20, 16}, new int[]{20, 15, 11}, 3);
        d.fillPolygon(new int[]{5, 9, 15, 19, 19, 15, 9, 5}, new int[]{11, 8, 7, 9, 12, 14, 14, 13}, 8);
        d.fillPolygon(new int[]{7, 14, 13, 7}, new int[]{15, 15, 17, 16}, 4);
        // horns and back spikes
        d.setColor(bone);
        d.fillPolygon(new int[]{14, 18, 17}, new int[]{8, 3, 8}, 3);
        d.fillPolygon(new int[]{16, 21, 19}, new int[]{8, 4, 9}, 3);
        int[][] spikes = {{21, 14, 22, 10, 24, 15}, {27, 17, 29, 13, 31, 16}, {33, 16, 35, 12, 37, 16}, {40, 17, 42, 14, 43, 19}};
        for (int[] sp : spikes) {
            d.fillPolygon(new int[]{sp[0], sp[2], sp[4]}, new int[]{sp[1], sp[3], sp[5]}, 3);
        }
        // near wing: membrane with finger bones
        d.setColor(wing);
        d.fillPolygon(new int[]{30, 28, 25, 30, 38, 44, 42, 46, 39, 40, 34}, new int[]{19, 10, 3, 1, 2, 8, 12, 15, 16, 21, 20}, 11);
        d.setColor(bone);
        d.setStroke(new BasicStroke(1f));
        d.drawLine(31, 18, 33, 3);
        d.drawLine(31, 18, 41, 7);
        d.drawLine(31, 18, 44, 14);
        d.drawPolyline(new int[]{28, 25, 30, 38}, new int[]{10, 3, 1, 2}, 4);
        d.dispose();
        // rim light on every upper edge
        Color rim = Theme.mix(body, Theme.ACCENT_HOVER, 0.55);
        for (int y = lh - 1; y >= 0; y--) {
            for (int x = 0; x < lw; x++) {
                if ((s.getRGB(x, y) >>> 24) > 0 && (y == 0 || (s.getRGB(x, y - 1) >>> 24) == 0)) {
                    s.setRGB(x, y, rim.getRGB());
                }
            }
        }
        int px = Math.max(2, (int) Math.min(w * 0.40 / lw, h * 0.88 / lh));
        int dw = lw * px;
        int x0 = w - dw - (int) (w * 0.03);
        int y0 = (int) (h * 0.05);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        // soft shadow: the silhouette in black, offset
        BufferedImage shadow = new BufferedImage(lw, lh, BufferedImage.TYPE_INT_ARGB);
        Graphics2D sg = shadow.createGraphics();
        sg.drawImage(s, 0, 0, null);
        sg.setComposite(AlphaComposite.SrcIn);
        sg.setColor(new Color(0, 0, 0, 110));
        sg.fillRect(0, 0, lw, lh);
        sg.dispose();
        g.drawImage(shadow, x0 + px, y0 + px, dw, lh * px, null);
        g.drawImage(s, x0, y0, dw, lh * px, null);
        // glowing eye
        int ex = x0 + 11 * px;
        int ey = y0 + 10 * px;
        Color eye = Theme.mix(Theme.ACCENT_HOVER, Color.WHITE, 0.55);
        g.setPaint(new RadialGradientPaint(new Point2D.Float(ex + px, ey + px / 2f), px * 3.5f, new float[]{0f, 1f},
                new Color[]{Theme.alpha(eye, 140), new Color(0, 0, 0, 0)}));
        g.fillRect(ex - px * 4, ey - px * 4, px * 9, px * 9);
        g.setColor(eye);
        g.fillRect(ex, ey, px * 2, px);
    }

    // ------------------------------------------------------------------ mountains (scene 2)

    private static void moon(Graphics2D g, int cx, int cy, int r, int b) {
        g.setPaint(new RadialGradientPaint(new Point2D.Float(cx, cy), r * 3.2f, new float[]{0f, 1f},
                new Color[]{new Color(200, 230, 255, 90), new Color(0, 0, 0, 0)}));
        g.fillRect(cx - r * 4, cy - r * 4, r * 8, r * 8);
        // blocky disc
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

    private static void mountains(Graphics2D g, Random rnd, int w, int h, int b) {
        Color accent = Theme.ACCENT;
        moon(g, (int) (w * 0.80), (int) (h * 0.24), Math.max(18, h / 9), b);
        Color far = Theme.mix(new Color(0x10213F), accent, 0.30);
        peaks(g, rnd, w, h, b, far, Theme.mix(far, Color.WHITE, 0.35), 0.66, 0.40, 6);
        Color mid = Theme.mix(new Color(0x0A162E), accent, 0.18);
        peaks(g, rnd, w, h, b, mid, Theme.mix(mid, Color.WHITE, 0.45), 0.78, 0.36, 4);
        Color near = Theme.mix(new Color(0x050B1A), accent, 0.10);
        hills(g, rnd, w, h, b, near, Theme.mix(near, accent, 0.25), 0.86, 0.04);
        // pines
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

    // ------------------------------------------------------------------ floating islands (scene 3)

    private static void island(Graphics2D g, Random rnd, int cx, int top, int size, int b, Color grass, Color earth, Color fall) {
        int x0 = cx - size * b / 2;
        // grass layer and stepped earth body that narrows downwards
        g.setColor(grass);
        g.fillRect(x0, top, size * b, b);
        g.setColor(earth);
        int rows = Math.max(2, size / 2 + 1);
        for (int r = 1; r <= rows; r++) {
            int inset = r * size / (rows + 1) / 2 + rnd.nextInt(2);
            g.fillRect(x0 + inset * b, top + r * b, Math.max(1, size - inset * 2) * b, b);
        }
        // a tree on top
        if (size >= 6) {
            int tx = x0 + size * b / 3;
            g.setColor(earth.darker());
            g.fillRect(tx + b, top - b * 3, Math.max(3, b / 2), b * 3);
            g.setColor(grass.darker());
            g.fillRect(tx - b / 2, top - b * 6, b * 3, b * 3);
            g.fillRect(tx, top - b * 7, b * 2, b);
        }
        // waterfall from the edge
        if (fall != null) {
            int fx = x0 + size * b - b * 2;
            g.setPaint(new GradientPaint(0, top, fall, 0, top + b * 18, new Color(fall.getRed(), fall.getGreen(), fall.getBlue(), 0)));
            g.fillRect(fx, top + b, Math.max(4, b / 2 + 2), b * 18);
        }
    }

    private static void islands(Graphics2D g, Random rnd, int w, int h, int b) {
        Color accent = Theme.ACCENT;
        Color far = Theme.mix(new Color(0x0B1A36), accent, 0.30);
        hills(g, rnd, w, h, b, far, Theme.mix(far, accent, 0.35), 0.82, 0.04);
        Color grass = Theme.mix(new Color(0x1E5B4A), accent, 0.30);
        Color earth = Theme.mix(new Color(0x0C1630), accent, 0.15);
        Color fall = new Color(170, 225, 255, 150);
        island(g, rnd, (int) (w * 0.80), (int) (h * 0.36), 12, b, grass, earth, fall);
        island(g, rnd, (int) (w * 0.62), (int) (h * 0.62), 6, b, Theme.mix(grass, earth, 0.3), earth, null);
        island(g, rnd, (int) (w * 0.94), (int) (h * 0.70), 5, b, Theme.mix(grass, earth, 0.4), earth, null);
        island(g, rnd, (int) (w * 0.14), (int) (h * 0.52), 8, b, Theme.mix(grass, earth, 0.2), earth, fall);
        // small floating blocks
        g.setColor(earth);
        for (int i = 0; i < 7; i++) {
            int x = (int) (w * (0.05 + rnd.nextDouble() * 0.9));
            if (x > w * 0.32 && x < w * 0.58) {
                continue;
            }
            g.fillRect(x / b * b, (int) (h * (0.2 + rnd.nextDouble() * 0.45)) / b * b, b, b);
        }
        Color near = Theme.mix(new Color(0x050B1A), accent, 0.10);
        hills(g, rnd, w, h, b, near, Theme.mix(near, accent, 0.25), 0.92, 0.03);
    }

    // ------------------------------------------------------------------ lighthouse (scene 4)

    private static void lighthouse(Graphics2D g, Random rnd, int w, int h, int b) {
        Color accent = Theme.ACCENT;
        int horizon = (int) (h * 0.70) / b * b;
        moon(g, (int) (w * 0.22), (int) (h * 0.22), Math.max(14, h / 12), b);
        // sea with light streaks
        Color sea = Theme.mix(new Color(0x071530), accent, 0.25);
        g.setPaint(new GradientPaint(0, horizon, Theme.mix(sea, Color.WHITE, 0.10), 0, h, Theme.mix(sea, Color.BLACK, 0.4)));
        g.fillRect(0, horizon, w, h - horizon);
        for (int i = 0; i < w / 18; i++) {
            int y = horizon + b / 2 + rnd.nextInt(Math.max(1, h - horizon));
            int len = b * (1 + rnd.nextInt(4));
            g.setColor(new Color(190, 225, 255, 25 + rnd.nextInt(60)));
            g.fillRect(rnd.nextInt(w) / b * b, y / 2 * 2, len, 2);
        }
        // moonlight path below the moon
        for (int y = horizon + b; y < h; y += b / 2 + 2) {
            int len = b * (2 + rnd.nextInt(3));
            g.setColor(new Color(220, 240, 255, 90));
            g.fillRect((int) (w * 0.22) - len / 2 + rnd.nextInt(b + 1) - b / 2, y, len, 2);
        }
        // cliff on the right
        Color rock = Theme.mix(new Color(0x07122A), accent, 0.12);
        g.setColor(rock);
        int[] heights = {3, 5, 7, 9, 10, 11, 11, 12, 12, 13, 13, 13, 14, 14, 14, 14, 15, 15, 15, 15};
        int cx0 = w - heights.length * b;
        for (int i = 0; i < heights.length; i++) {
            int top = horizon - (heights[i] - 4) * b;
            g.fillRect(cx0 + i * b, top, b, h - top);
        }
        // tower with stripes, lantern and beam
        int tx = cx0 + 11 * b;
        int base = horizon - 9 * b;
        int tw = b * 3;
        int th = b * 8;
        Color white = Theme.mix(new Color(0xDDE8F7), accent, 0.10);
        Color stripe = Theme.mix(new Color(0x9B2C3C), accent, 0.35);
        for (int i = 0; i < 8; i++) {
            g.setColor(i / 2 % 2 == 0 ? white : stripe);
            g.fillRect(tx, base - (i + 1) * b, tw, b);
        }
        Color lamp = new Color(0xFFF2B8);
        g.setColor(rock);
        g.fillRect(tx - b / 2, base - th - b, tw + b, b);
        g.setColor(lamp);
        g.fillRect(tx, base - th - b * 3, tw, b * 2);
        g.setColor(rock);
        g.fillRect(tx - b / 2, base - th - b * 4, tw + b, b);
        int ly = base - th - b * 2;
        g.setPaint(new GradientPaint(tx, ly, new Color(255, 242, 184, 110), (float) (w * 0.35), ly, new Color(255, 242, 184, 0)));
        g.fillPolygon(new int[]{tx, (int) (w * 0.30), (int) (w * 0.30)}, new int[]{ly, ly - b * 6, ly + b * 5}, 3);
        g.setPaint(new RadialGradientPaint(new Point2D.Float(tx + tw / 2f, ly), b * 5f, new float[]{0f, 1f},
                new Color[]{new Color(255, 242, 184, 140), new Color(0, 0, 0, 0)}));
        g.fillRect(tx - b * 5, ly - b * 5, b * 13, b * 10);
    }
}
