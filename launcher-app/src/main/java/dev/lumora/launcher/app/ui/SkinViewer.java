package dev.lumora.launcher.app.ui;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 3D preview of a Minecraft skin (64x64 or legacy 64x32): head, body, arms (classic or slim) and legs including the
 * second layer (hat, jacket, sleeves, trousers). Software renderer: every skin pixel is a small quad, faces are
 * sorted back to front. Drag to rotate; turns slowly on its own while idle.
 */
public final class SkinViewer extends JComponent {
    private BufferedImage skin;
    private boolean slim;
    private double yaw = Math.toRadians(-28);
    private double pitch = Math.toRadians(-8);
    private int lastX;
    private int lastY;
    private long lastDrag;
    private double walk;
    private final Timer timer;

    public SkinViewer() {
        setOpaque(false);
        setPreferredSize(new Dimension(260, 380));
        MouseAdapter drag = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                lastX = e.getX();
                lastY = e.getY();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                yaw += (e.getX() - lastX) * 0.012;
                pitch = Math.clamp(pitch + (e.getY() - lastY) * 0.008, -0.9, 0.9);
                lastX = e.getX();
                lastY = e.getY();
                lastDrag = System.currentTimeMillis();
                repaint();
            }
        };
        addMouseListener(drag);
        addMouseMotionListener(drag);
        timer = new Timer(33, e -> {
            if (!isShowing() || skin == null || Theme.reduceMotion) {
                return;
            }
            walk += 0.06;
            if (System.currentTimeMillis() - lastDrag > 2500) {
                yaw += 0.006;
            }
            repaint();
        });
        timer.start();
    }

    /** Sets the skin PNG (null shows nothing). */
    public void setSkin(byte[] png, boolean slim) {
        BufferedImage img = null;
        if (png != null) {
            try {
                img = ImageIO.read(new ByteArrayInputStream(png));
            } catch (IOException ignored) {
                // invalid image: nothing shown
            }
        }
        if (img != null && (img.getWidth() != 64 || (img.getHeight() != 64 && img.getHeight() != 32))) {
            img = null;
        }
        if (img != null) {
            img = normalize(img);
        }
        this.skin = img;
        this.slim = slim;
        repaint();
    }

    /**
     * Same rules as Minecraft's skin loader: base layers are always opaque, and an old 64x32 skin whose hat area has
     * no transparent pixel at all (many old skins fill it with black) shows no hat.
     */
    static BufferedImage normalize(BufferedImage src) {
        BufferedImage img = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        img.getGraphics().drawImage(src, 0, 0, null);
        boolean legacy = img.getHeight() == 32;
        if (legacy) {
            boolean anyTransparent = false;
            for (int x = 32; x < 64 && !anyTransparent; x++) {
                for (int y = 0; y < 16; y++) {
                    if ((img.getRGB(x, y) >>> 24) < 128) {
                        anyTransparent = true;
                        break;
                    }
                }
            }
            if (!anyTransparent) {
                for (int x = 32; x < 64; x++) {
                    for (int y = 0; y < 16; y++) {
                        img.setRGB(x, y, 0);
                    }
                }
            }
        }
        int[][] opaque = legacy ? new int[][]{{0, 0, 32, 16}, {0, 16, 64, 32}}
                : new int[][]{{0, 0, 32, 16}, {0, 16, 64, 32}, {16, 48, 48, 64}};
        for (int[] r : opaque) {
            for (int x = r[0]; x < r[2]; x++) {
                for (int y = r[1]; y < r[3]; y++) {
                    img.setRGB(x, y, img.getRGB(x, y) | 0xFF000000);
                }
            }
        }
        return img;
    }

    // ---------------------------------------------------------------- model

    /** Axis-aligned textured box: model coordinates (y up, character faces +z), skin UV origin and size. */
    private record Box(double x0, double y0, double z0, double x1, double y1, double z1, int u, int v, int w, int h, int d,
                       String part, boolean overlay) {
    }

    /** One textured face: corners (top-left, top-right, bottom-left) in model space and its texture rectangle. */
    private record Face(double[] a, double[] b, double[] c, int tu, int tv, int tw, int th, boolean flipU, boolean flipV,
                        double[] normal, String part) {
    }

    private List<Box> boxes() {
        boolean legacy = skin.getHeight() == 32;
        int arm = slim ? 3 : 4;
        List<Box> out = new ArrayList<>();
        out.add(new Box(-4, 24, -4, 4, 32, 4, 0, 0, 8, 8, 8, "head", false));
        out.add(new Box(-4, 12, -2, 4, 24, 2, 16, 16, 8, 12, 4, "body", false));
        out.add(new Box(-4 - arm, 12, -2, -4, 24, 2, 40, 16, arm, 12, 4, "rarm", false));
        out.add(new Box(4, 12, -2, 4 + arm, 24, 2, legacy ? 40 : 32, legacy ? 16 : 48, arm, 12, 4, "larm", false));
        out.add(new Box(-4, 0, -2, 0, 12, 2, 0, 16, 4, 12, 4, "rleg", false));
        out.add(new Box(0, 0, -2, 4, 12, 2, legacy ? 0 : 16, legacy ? 16 : 48, 4, 12, 4, "lleg", false));
        double o = 0.5;
        double p = 0.25;
        out.add(new Box(-4 - o, 24 - o, -4 - o, 4 + o, 32 + o, 4 + o, 32, 0, 8, 8, 8, "head", true));
        if (!legacy) {
            out.add(new Box(-4 - p, 12 - p, -2 - p, 4 + p, 24 + p, 2 + p, 16, 32, 8, 12, 4, "body", true));
            out.add(new Box(-4 - arm - p, 12 - p, -2 - p, -4 + p, 24 + p, 2 + p, 40, 32, arm, 12, 4, "rarm", true));
            out.add(new Box(4 - p, 12 - p, -2 - p, 4 + arm + p, 24 + p, 2 + p, 48, 48, arm, 12, 4, "larm", true));
            out.add(new Box(-4 - p, -p, -2 - p, p, 12 + p, 2 + p, 0, 32, 4, 12, 4, "rleg", true));
            out.add(new Box(-p, -p, -2 - p, 4 + p, 12 + p, 2 + p, 0, 48, 4, 12, 4, "lleg", true));
        }
        return out;
    }

    private static List<Face> faces(Box b) {
        double x0 = b.x0, x1 = b.x1, y0 = b.y0, y1 = b.y1, z0 = b.z0, z1 = b.z1;
        int u = b.u, v = b.v, w = b.w, h = b.h, d = b.d;
        List<Face> f = new ArrayList<>();
        // front (+z): left-right = x0..x1, top-bottom = y1..y0
        f.add(new Face(p(x0, y1, z1), p(x1, y1, z1), p(x0, y0, z1), u + d, v + d, w, h, false, false, p(0, 0, 1), b.part));
        // back (-z): seen from behind, left-right = x1..x0
        f.add(new Face(p(x1, y1, z0), p(x0, y1, z0), p(x1, y0, z0), u + 2 * d + w, v + d, w, h, false, false, p(0, 0, -1), b.part));
        // character's right side (-x): left-right = z0..z1
        f.add(new Face(p(x0, y1, z0), p(x0, y1, z1), p(x0, y0, z0), u, v + d, d, h, false, false, p(-1, 0, 0), b.part));
        // character's left side (+x): left-right = z1..z0
        f.add(new Face(p(x1, y1, z1), p(x1, y1, z0), p(x1, y0, z1), u + d + w, v + d, d, h, false, false, p(1, 0, 0), b.part));
        // top (+y): rows z0 (back) .. z1 (front)
        f.add(new Face(p(x0, y1, z0), p(x1, y1, z0), p(x0, y1, z1), u + d, v, w, d, false, false, p(0, 1, 0), b.part));
        // bottom (-y)
        f.add(new Face(p(x0, y0, z1), p(x1, y0, z1), p(x0, y0, z0), u + d + w, v, w, d, false, false, p(0, -1, 0), b.part));
        return f;
    }

    private static double[] p(double x, double y, double z) {
        return new double[]{x, y, z};
    }

    // ---------------------------------------------------------------- rendering

    private double[] transform(double[] v, String part) {
        double x = v[0];
        double y = v[1];
        double z = v[2];
        // gentle idle swing of arms and legs around their shoulder / hip
        double swing = Math.sin(walk) * 0.35;
        double angle = switch (part) {
            case "rarm", "lleg" -> swing;
            case "larm", "rleg" -> -swing;
            default -> 0;
        };
        if (angle != 0) {
            double pivotY = part.endsWith("arm") ? 22 : 12;
            double dy = y - pivotY;
            double ny = dy * Math.cos(angle) - z * Math.sin(angle);
            double nz = dy * Math.sin(angle) + z * Math.cos(angle);
            y = ny + pivotY;
            z = nz;
        }
        y -= 16; // center of the model
        double cx = x * Math.cos(yaw) + z * Math.sin(yaw);
        double cz = -x * Math.sin(yaw) + z * Math.cos(yaw);
        double cy = y * Math.cos(pitch) - cz * Math.sin(pitch);
        double cz2 = y * Math.sin(pitch) + cz * Math.cos(pitch);
        return new double[]{cx, cy, cz2};
    }

    @Override
    protected void paintComponent(Graphics g0) {
        if (skin == null) {
            return;
        }
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        double scale = Math.min(getWidth() / 22.0, getHeight() / 38.0);
        double ox = getWidth() / 2.0;
        double oy = getHeight() / 2.0;
        record Drawn(Face face, double[] a, double[] b, double[] c, double depth, double light) {
        }
        List<Drawn> list = new ArrayList<>();
        for (Box box : boxes()) {
            for (Face f : faces(box)) {
                double[] a = transform(f.a, f.part);
                double[] b = transform(f.b, f.part);
                double[] c = transform(f.c, f.part);
                // normal from the transformed corners (works with the limb swing)
                double ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2];
                double vx = c[0] - a[0], vy = c[1] - a[1], vz = c[2] - a[2];
                double nz = ux * vy - uy * vx;
                double nx = uy * vz - uz * vy;
                double ny = uz * vx - ux * vz;
                double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (len == 0 || -nz / len <= 0.0001) {
                    continue; // facing away
                }
                double light = 0.62 + 0.38 * Math.max(0, (-nz * 0.75 + ny * -0.4) / len);
                double depth = (a[2] + b[2] + c[2] + (b[2] + c[2] - a[2])) / 4;
                list.add(new Drawn(f, a, b, c, depth, Math.min(1, light)));
            }
        }
        list.sort(Comparator.comparingDouble(Drawn::depth));
        Polygon poly = new Polygon(new int[4], new int[4], 4);
        for (Drawn dr : list) {
            Face f = dr.face;
            double ax = ox + dr.a[0] * scale, ay = oy - dr.a[1] * scale;
            double bx = ox + dr.b[0] * scale, by = oy - dr.b[1] * scale;
            double cx = ox + dr.c[0] * scale, cy = oy - dr.c[1] * scale;
            double ex = (bx - ax) / f.tw, ey = (by - ay) / f.tw;
            double fx = (cx - ax) / f.th, fy = (cy - ay) / f.th;
            for (int j = 0; j < f.th; j++) {
                for (int i = 0; i < f.tw; i++) {
                    int px = f.tu + i;
                    int py = f.tv + j;
                    if (px >= skin.getWidth() || py >= skin.getHeight()) {
                        continue;
                    }
                    int argb = skin.getRGB(px, py);
                    int alpha = argb >>> 24;
                    if (alpha < 16) {
                        continue;
                    }
                    double x = ax + ex * i + fx * j;
                    double y = ay + ey * i + fy * j;
                    poly.xpoints[0] = (int) Math.round(x);
                    poly.ypoints[0] = (int) Math.round(y);
                    poly.xpoints[1] = (int) Math.round(x + ex + 0.6);
                    poly.ypoints[1] = (int) Math.round(y + ey);
                    poly.xpoints[2] = (int) Math.round(x + ex + fx + 0.6);
                    poly.ypoints[2] = (int) Math.round(y + ey + fy + 0.6);
                    poly.xpoints[3] = (int) Math.round(x + fx);
                    poly.ypoints[3] = (int) Math.round(y + fy + 0.6);
                    poly.invalidate();
                    int r = (int) (((argb >> 16) & 0xFF) * dr.light);
                    int gr = (int) (((argb >> 8) & 0xFF) * dr.light);
                    int bl = (int) ((argb & 0xFF) * dr.light);
                    g.setColor(new Color(r, gr, bl, alpha));
                    g.fillPolygon(poly);
                }
            }
        }
        g.dispose();
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
        timer.stop();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        timer.start();
    }
}
