package dev.lumora.core.extras;

import dev.lumora.api.addon.AddonContext;
import dev.lumora.api.addon.LumoraAddon;
import dev.lumora.api.config.BooleanSetting;
import dev.lumora.api.config.ChoiceSetting;
import dev.lumora.api.config.ColorSetting;
import dev.lumora.api.config.DecimalSetting;
import dev.lumora.api.config.IntegerSetting;
import dev.lumora.api.config.SettingsGroup;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Built-in module "Elytra-Streifen": vapor trails (or counter-rotating wingtip vortices) behind the wing tips of every
 * player gliding with an elytra. Only drawn on this client.
 * <p>
 * The version adapters call {@link #mesh} once per frame for every player they draw (from the player renderer, with
 * the interpolated position, look, motion and the camera); the module samples the wing tips, forgets old points and
 * returns the trails as camera-facing quads relative to the player's position.
 */
public final class ElytraTrailsModule implements LumoraAddon {
    /** Elytras exist since Minecraft 1.9. */
    public static final String MIN_MINECRAFT = "1.9";
    static final long SAMPLE_MILLIS = 40;
    static final double SAMPLE_DISTANCE = 0.3;
    /** A trail of a player that was not drawn for this long is dropped. */
    static final long FORGET_MILLIS = 15_000;

    private static volatile ElytraTrailsModule active;

    BooleanSetting own;
    BooleanSetting others;
    ChoiceSetting style;
    ColorSetting color;
    DecimalSetting length;
    IntegerSetting width;
    IntegerSetting minSpeed;
    private final Map<Integer, Trail> trails = new HashMap<>();
    private long lastCleanup;
    /** Vertices of the last built mesh (diagnostics, smoke test). */
    private volatile int lastVertices;

    /** The running module, {@code null} while switched off. */
    public static ElytraTrailsModule active() {
        return active;
    }

    @Override
    public void onInitialize(AddonContext context) {
        configure(context.configuration().settings());
    }

    void configure(SettingsGroup s) {
        String k = "lumora.extras.elytratrails.";
        own = s.toggle("own", true).name(k + "own").section(k + "section.players");
        others = s.toggle("others", true).name(k + "others");
        style = s.selection("style", "vapor", "vapor", "vortex").name(k + "style").section(k + "section.look");
        color = s.chroma("color", 0xC8FFFFFF, true).name(k + "color");
        length = s.slider("length", 2.0, 0.5, 6.0, 0.5).name(k + "length");
        width = s.slider("width", 4, 1, 10).name(k + "width");
        minSpeed = s.slider("min_speed", 12, 0, 40).name(k + "min_speed");
    }

    @Override
    public void onEnable(AddonContext context) {
        active = this;
    }

    @Override
    public void onDisable(AddonContext context) {
        active = null;
        synchronized (trails) {
            trails.clear();
        }
    }

    /** Quads of the trails, relative to the player position given to {@link #mesh}: 4 vertices per quad. */
    public record Mesh(float[] xyz, int[] colors, int vertices) {
        public static final Mesh EMPTY = new Mesh(new float[0], new int[0], 0);

        public boolean isEmpty() {
            return vertices == 0;
        }
    }

    /** One sampled point behind a wing tip. */
    record Point(double x, double y, double z, long time, double distance,
                 double rx, double ry, double rz, double ux, double uy, double uz) {
    }

    /** Both trails of one player. */
    static final class Trail {
        final ArrayDeque<Point> left = new ArrayDeque<>();
        final ArrayDeque<Point> right = new ArrayDeque<>();
        double lastX = Double.NaN;
        double lastY;
        double lastZ;
        long lastSample;
        double distance;
        long seen;
    }

    /**
     * Samples the wing tips of one player and returns the trails to draw (render thread, once per frame per drawn
     * player).
     *
     * @param id      entity id
     * @param self    the local player
     * @param gliding the player glides with an elytra right now
     * @param x       interpolated position (feet), the origin of the returned quads
     * @param yaw     look yaw in degrees (Minecraft convention: 0 = south, 90 = west)
     * @param pitch   look pitch in degrees (positive = down)
     * @param vx      motion in blocks per tick
     * @param camX    camera position
     * @param now     wall clock in milliseconds
     */
    public Mesh mesh(int id, boolean self, boolean gliding, double x, double y, double z, float yaw, float pitch,
                     double vx, double vy, double vz, double camX, double camY, double camZ, long now) {
        if (self ? !own.get() : !others.get()) {
            return Mesh.EMPTY;
        }
        long keep = Math.round(length.get() * 1000);
        double speed = Math.sqrt(vx * vx + vy * vy + vz * vz) * 20;
        boolean emit = gliding && speed >= minSpeed.get();
        Trail t;
        synchronized (trails) {
            cleanup(now);
            t = trails.get(id);
            if (t == null) {
                if (!emit) {
                    return Mesh.EMPTY;
                }
                t = new Trail();
                trails.put(id, t);
            }
        }
        t.seen = now;
        Basis b = basis(yaw, pitch);
        double[] tipL = tip(b, x, y, z, -1);
        double[] tipR = tip(b, x, y, z, 1);
        if (emit) {
            double moved = Double.isNaN(t.lastX) ? 0 : Math.sqrt(sq(x - t.lastX) + sq(y - t.lastY) + sq(z - t.lastZ));
            if (Double.isNaN(t.lastX) || moved >= SAMPLE_DISTANCE || now - t.lastSample >= SAMPLE_MILLIS) {
                t.distance += moved;
                add(t.left, tipL, now, t.distance, b);
                add(t.right, tipR, now, t.distance, b);
                t.lastX = x;
                t.lastY = y;
                t.lastZ = z;
                t.lastSample = now;
            }
        } else {
            t.lastX = Double.NaN;
        }
        prune(t.left, now - keep);
        prune(t.right, now - keep);
        if (t.left.isEmpty()) {
            return Mesh.EMPTY;
        }
        MeshBuilder out = new MeshBuilder();
        boolean vortex = "vortex".equals(style.get());
        strip(out, t.left, emit ? tipL : null, b, -1, vortex, keep, x, y, z, camX, camY, camZ, now);
        strip(out, t.right, emit ? tipR : null, b, 1, vortex, keep, x, y, z, camX, camY, camZ, now);
        Mesh built = out.build();
        lastVertices = built.vertices();
        return built;
    }

    private static void add(ArrayDeque<Point> points, double[] tip, long now, double distance, Basis b) {
        points.addLast(new Point(tip[0], tip[1], tip[2], now, distance, b.rx, b.ry, b.rz, b.ux, b.uy, b.uz));
        while (points.size() > 400) {
            points.removeFirst();
        }
    }

    private static void prune(ArrayDeque<Point> points, long oldest) {
        while (!points.isEmpty() && points.peekFirst().time() < oldest) {
            points.removeFirst();
        }
    }

    private void cleanup(long now) {
        if (now - lastCleanup < 1000) {
            return;
        }
        lastCleanup = now;
        for (Iterator<Trail> it = trails.values().iterator(); it.hasNext(); ) {
            if (now - it.next().seen > FORGET_MILLIS) {
                it.remove();
            }
        }
    }

    /** Vertices of the last trail mesh that was built (smoke test). */
    public int lastVertices() {
        return lastVertices;
    }

    /** Number of players with a trail (tests, smoke test). */
    public int activeTrails() {
        synchronized (trails) {
            return trails.size();
        }
    }

    // ---- geometry

    /** Forward, right and up of a gliding player (the body lies along the look direction). */
    record Basis(double fx, double fy, double fz, double rx, double ry, double rz, double ux, double uy, double uz) {
    }

    static Basis basis(float yaw, float pitch) {
        double y = Math.toRadians(yaw);
        double p = Math.toRadians(pitch);
        double fx = -Math.sin(y) * Math.cos(p);
        double fy = -Math.sin(p);
        double fz = Math.cos(y) * Math.cos(p);
        // right = forward x up(0,1,0), horizontal
        double rx = -fz;
        double rz = fx;
        double rl = Math.sqrt(rx * rx + rz * rz);
        if (rl < 1e-6) {
            rx = -Math.cos(y);
            rz = -Math.sin(y);
            rl = 1;
        }
        rx /= rl;
        rz /= rl;
        // up of the body = right x forward
        double ux = 0 * fz - rz * fy;
        double uy = rz * fx - rx * fz;
        double uz = rx * fy - 0 * fx;
        return new Basis(fx, fy, fz, rx, 0, rz, ux, uy, uz);
    }

    /**
     * Wing tip of the gliding model: the body pivots at the feet and lies along the look direction, the spread wings
     * end about 0.45 blocks along it and 0.8 blocks to the side, slightly above the body.
     */
    static double[] tip(Basis b, double x, double y, double z, int side) {
        double along = 0.45;
        double out = 0.8 * side;
        double up = 0.1;
        return new double[]{
                x + b.fx * along + b.rx * out + b.ux * up,
                y + b.fy * along + b.ry * out + b.uy * up,
                z + b.fz * along + b.rz * out + b.uz * up};
    }

    /** One trail as a camera-facing ribbon, oldest point first, the wing tip last (while it still emits). */
    private void strip(MeshBuilder out, ArrayDeque<Point> points, double[] tip, Basis b, int side,
                       boolean vortex, long keep, double ox, double oy, double oz, double camX, double camY, double camZ,
                       long now) {
        int n = points.size() + (tip != null ? 1 : 0);
        if (n < 2) {
            return;
        }
        double[] px = new double[n];
        double[] py = new double[n];
        double[] pz = new double[n];
        float[] age = new float[n];
        long[] time = new long[n];
        int i = 0;
        for (Point p : points) {
            float a = Math.min(1f, (now - p.time()) / (float) keep);
            double sx = p.x();
            double sy = p.y();
            double sz = p.z();
            if (vortex) {
                // counter-rotating spirals (left and right turn the other way) that widen as they age
                double phase = p.distance() * 1.6 * side;
                double r = 0.06 + 0.35 * a;
                sx += (p.rx() * Math.cos(phase) + p.ux() * Math.sin(phase)) * r;
                sy += (p.ry() * Math.cos(phase) + p.uy() * Math.sin(phase)) * r;
                sz += (p.rz() * Math.cos(phase) + p.uz() * Math.sin(phase)) * r;
            }
            px[i] = sx;
            py[i] = sy;
            pz[i] = sz;
            age[i] = a;
            time[i] = p.time();
            i++;
        }
        if (tip != null) {
            px[i] = tip[0];
            py[i] = tip[1];
            pz[i] = tip[2];
            age[i] = 0;
            time[i] = now;
        }
        double baseWidth = width.get() * 0.025;
        double[] sideX = new double[n];
        double[] sideY = new double[n];
        double[] sideZ = new double[n];
        for (int k = 0; k < n; k++) {
            int a = Math.max(0, k - 1);
            int c = Math.min(n - 1, k + 1);
            double tx = px[c] - px[a];
            double ty = py[c] - py[a];
            double tz = pz[c] - pz[a];
            double vx = camX - px[k];
            double vy = camY - py[k];
            double vz = camZ - pz[k];
            // perpendicular to the trail and to the view: the ribbon always faces the camera
            double cx = ty * vz - tz * vy;
            double cy = tz * vx - tx * vz;
            double cz = tx * vy - ty * vx;
            double l = Math.sqrt(cx * cx + cy * cy + cz * cz);
            // thin at the wing, wider (spread vapor) further back
            double half = baseWidth * (0.5 + 1.5 * age[k]);
            if (l < 1e-9) {
                cx = b.ux;
                cy = b.uy;
                cz = b.uz;
                l = 1;
            }
            sideX[k] = cx / l * half;
            sideY[k] = cy / l * half;
            sideZ[k] = cz / l * half;
        }
        int alpha = color.get() >>> 24;
        for (int k = 0; k + 1 < n; k++) {
            int c1 = shade(time[k], age[k], alpha);
            int c2 = shade(time[k + 1], age[k + 1], alpha);
            if ((c1 >>> 24) == 0 && (c2 >>> 24) == 0) {
                continue;
            }
            out.quad(px[k] - sideX[k] - ox, py[k] - sideY[k] - oy, pz[k] - sideZ[k] - oz, c1,
                    px[k] + sideX[k] - ox, py[k] + sideY[k] - oy, pz[k] + sideZ[k] - oz, c1,
                    px[k + 1] + sideX[k + 1] - ox, py[k + 1] + sideY[k + 1] - oy, pz[k + 1] + sideZ[k + 1] - oz, c2,
                    px[k + 1] - sideX[k + 1] - ox, py[k + 1] - sideY[k + 1] - oy, pz[k + 1] - sideZ[k + 1] - oz, c2);
        }
    }

    /** Colour of a point: the chosen colour (rainbow follows the time the point was made), fading out with age. */
    private int shade(long time, float age, int alpha) {
        int rgb = color.argbAt(time) & 0xFFFFFF;
        float fade = (1f - age);
        int a = Math.round(alpha * fade * fade);
        return Math.max(0, Math.min(255, a)) << 24 | rgb;
    }

    private static double sq(double v) {
        return v * v;
    }

    /** Collects quads (both sides, so culling never hides a ribbon). */
    static final class MeshBuilder {
        private float[] xyz = new float[96];
        private int[] colors = new int[32];
        private int n;

        void quad(double x1, double y1, double z1, int c1, double x2, double y2, double z2, int c2,
                  double x3, double y3, double z3, int c3, double x4, double y4, double z4, int c4) {
            vertex(x1, y1, z1, c1);
            vertex(x2, y2, z2, c2);
            vertex(x3, y3, z3, c3);
            vertex(x4, y4, z4, c4);
            // back side
            vertex(x4, y4, z4, c4);
            vertex(x3, y3, z3, c3);
            vertex(x2, y2, z2, c2);
            vertex(x1, y1, z1, c1);
        }

        private void vertex(double x, double y, double z, int c) {
            if (n == colors.length) {
                colors = java.util.Arrays.copyOf(colors, n * 2);
                xyz = java.util.Arrays.copyOf(xyz, n * 6);
            }
            xyz[n * 3] = (float) x;
            xyz[n * 3 + 1] = (float) y;
            xyz[n * 3 + 2] = (float) z;
            colors[n] = c;
            n++;
        }

        Mesh build() {
            return n == 0 ? Mesh.EMPTY : new Mesh(xyz, colors, n);
        }
    }
}
