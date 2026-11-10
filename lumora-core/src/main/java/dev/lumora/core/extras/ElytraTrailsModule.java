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
    BooleanSetting turnBoost;
    BooleanSetting speedWidth;
    BooleanSetting glow;
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
        turnBoost = s.toggle("turn_boost", true).name(k + "turn_boost");
        speedWidth = s.toggle("speed_width", true).name(k + "speed_width");
        glow = s.toggle("glow", false).name(k + "glow");
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

    /** Quads of the trails, relative to the player position given to {@link #mesh}: 4 vertices per quad.
     * {@code glow}: draw them with additive blending (they shine instead of covering what is behind). */
    public record Mesh(float[] xyz, int[] colors, int vertices, boolean glow) {
        public static final Mesh EMPTY = new Mesh(new float[0], new int[0], 0, false);

        public boolean isEmpty() {
            return vertices == 0;
        }
    }

    /** One sampled point behind a wing tip. */
    record Point(double x, double y, double z, long time, double distance,
                 double rx, double ry, double rz, double ux, double uy, double uz, double boost, double bright) {
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
        float lastYaw = Float.NaN;
        float lastPitch;
        long lastFrame;
        /** 0 (straight) .. 1 (hard turn), smoothed. */
        double load;
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
        double roll = roll(yaw, vx, vz);
        Basis b = basis(yaw, pitch).rolled(roll);
        double spread = spread(vx, vy, vz);
        double[] tipL = tip(b, x, y, z, -1, spread);
        double[] tipR = tip(b, x, y, z, 1, spread);
        // how hard the player turns: from the bank of the model and from how fast the view turns
        double turnRate = 0;
        if (!Float.isNaN(t.lastYaw) && now > t.lastFrame) {
            double dYaw = wrap(yaw - t.lastYaw);
            double dPitch = pitch - t.lastPitch;
            turnRate = Math.sqrt(dYaw * dYaw + dPitch * dPitch) * 1000.0 / (now - t.lastFrame);
        }
        double target = Math.min(1, Math.max(Math.abs(roll) / MAX_ROLL, turnRate / 120.0));
        double smooth = Math.min(1, (now - t.lastFrame) / 150.0);
        t.load = Float.isNaN(t.lastYaw) ? target : t.load + (target - t.load) * Math.max(0, smooth);
        t.lastYaw = yaw;
        t.lastPitch = pitch;
        t.lastFrame = now;
        double boost = strength(t.load, speed);
        double bright = turnBoost.get() ? 1 + 0.5 * t.load : 1;
        if (emit) {
            double moved = Double.isNaN(t.lastX) ? 0 : Math.sqrt(sq(x - t.lastX) + sq(y - t.lastY) + sq(z - t.lastZ));
            if (Double.isNaN(t.lastX) || moved >= SAMPLE_DISTANCE || now - t.lastSample >= SAMPLE_MILLIS) {
                t.distance += moved;
                add(t.left, tipL, now, t.distance, b, boost, bright);
                add(t.right, tipR, now, t.distance, b, boost, bright);
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
        Point headL = emit ? new Point(tipL[0], tipL[1], tipL[2], now, t.distance, b.rx, b.ry, b.rz, b.ux, b.uy, b.uz, boost, bright) : null;
        Point headR = emit ? new Point(tipR[0], tipR[1], tipR[2], now, t.distance, b.rx, b.ry, b.rz, b.ux, b.uy, b.uz, boost, bright) : null;
        strip(out, t.left, headL, b, -1, vortex, keep, x, y, z, camX, camY, camZ, now);
        strip(out, t.right, headR, b, 1, vortex, keep, x, y, z, camX, camY, camZ, now);
        Mesh built = out.build(glow.get());
        lastVertices = built.vertices();
        return built;
    }

    /** Width factor of new points: wider in turns and (if chosen) at higher speed. */
    double strength(double load, double speed) {
        double s = turnBoost.get() ? 1 + 1.5 * load : 1;
        if (speedWidth.get()) {
            s *= Math.max(0.6, Math.min(1.6, speed / 25.0));
        }
        return s;
    }

    private static double wrap(double degrees) {
        double d = degrees % 360;
        if (d > 180) {
            d -= 360;
        } else if (d < -180) {
            d += 360;
        }
        return d;
    }

    private static void add(ArrayDeque<Point> points, double[] tip, long now, double distance, Basis b, double boost,
                            double bright) {
        points.addLast(new Point(tip[0], tip[1], tip[2], now, distance, b.rx, b.ry, b.rz, b.ux, b.uy, b.uz, boost, bright));
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
        /** The same body rolled around its forward axis; positive lowers the left wing. */
        Basis rolled(double angle) {
            if (angle == 0) {
                return this;
            }
            double c = Math.cos(angle);
            double s = Math.sin(angle);
            return new Basis(fx, fy, fz,
                    rx * c + ux * s, ry * c + uy * s, rz * c + uz * s,
                    ux * c - rx * s, uy * c - ry * s, uz * c - rz * s);
        }
    }

    /** Largest bank of the gliding model that is used (radians). */
    static final double MAX_ROLL = Math.toRadians(60);

    /**
     * Bank of the gliding model, like Minecraft's renderer: the body rolls by the angle between the horizontal look and
     * the horizontal motion, towards the side the player looks to (positive = left wing down).
     */
    static double roll(float yaw, double vx, double vz) {
        double y = Math.toRadians(yaw);
        double lx = -Math.sin(y);
        double lz = Math.cos(y);
        double m = vx * vx + vz * vz;
        if (m < 1e-6) {
            return 0;
        }
        double dot = (vx * lx + vz * lz) / Math.sqrt(m);
        double cross = vx * lz - vz * lx;
        double angle = Math.acos(Math.max(-1, Math.min(1, dot)));
        return Math.max(-MAX_ROLL, Math.min(MAX_ROLL, cross < 0 ? angle : -angle));
    }

    /**
     * How far the wings are spread (0 folded .. 1 fully spread), like Minecraft's elytra model: spread in level flight,
     * folding in the steeper the player dives.
     */
    static double spread(double vx, double vy, double vz) {
        double l = Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (vy >= 0 || l < 1e-6) {
            return 1;
        }
        return 1 - Math.pow(-vy / l, 1.5);
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
     * end about 0.45 blocks along it and 0.8 blocks to the side, slightly above the body; folded (steep dive) they end
     * closer to the body and further back.
     */
    static double[] tip(Basis b, double x, double y, double z, int side, double spread) {
        double along = 0.45 - 0.25 * (1 - spread);
        double out = (0.35 + 0.45 * spread) * side;
        double up = 0.1;
        return new double[]{
                x + b.fx * along + b.rx * out + b.ux * up,
                y + b.fy * along + b.ry * out + b.uy * up,
                z + b.fz * along + b.rz * out + b.uz * up};
    }

    /** One trail as a camera-facing ribbon, oldest point first, the wing tip last (while it still emits). */
    private void strip(MeshBuilder out, ArrayDeque<Point> points, Point head, Basis b, int side,
                       boolean vortex, long keep, double ox, double oy, double oz, double camX, double camY, double camZ,
                       long now) {
        int n = points.size() + (head != null ? 1 : 0);
        if (n < 2) {
            return;
        }
        double[] px = new double[n];
        double[] py = new double[n];
        double[] pz = new double[n];
        float[] age = new float[n];
        long[] time = new long[n];
        double[] boost = new double[n];
        double[] bright = new double[n];
        int i = 0;
        java.util.List<Point> all = new java.util.ArrayList<>(points);
        if (head != null) {
            all.add(head);
        }
        for (Point p : all) {
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
            boost[i] = p.boost();
            bright[i] = p.bright();
            i++;
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
            double half = baseWidth * (0.5 + 1.5 * age[k]) * boost[k];
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
        boolean halo = glow.get();
        for (int k = 0; k + 1 < n; k++) {
            int c1 = shade(time[k], age[k], alpha * bright[k]);
            int c2 = shade(time[k + 1], age[k + 1], alpha * bright[k + 1]);
            if ((c1 >>> 24) == 0 && (c2 >>> 24) == 0) {
                continue;
            }
            if (halo) {
                // glow: a wide, faint band around the core (drawn additively, so it lights up the sky behind)
                segment(out, px, py, pz, sideX, sideY, sideZ, k, 3.0, scaleAlpha(c1, 0.3), scaleAlpha(c2, 0.3), ox, oy, oz);
            }
            segment(out, px, py, pz, sideX, sideY, sideZ, k, 1.0, c1, c2, ox, oy, oz);
        }
    }

    private static void segment(MeshBuilder out, double[] px, double[] py, double[] pz, double[] sx, double[] sy,
                                double[] sz, int k, double w, int c1, int c2, double ox, double oy, double oz) {
        int j = k + 1;
        out.quad(px[k] - sx[k] * w - ox, py[k] - sy[k] * w - oy, pz[k] - sz[k] * w - oz, c1,
                px[k] + sx[k] * w - ox, py[k] + sy[k] * w - oy, pz[k] + sz[k] * w - oz, c1,
                px[j] + sx[j] * w - ox, py[j] + sy[j] * w - oy, pz[j] + sz[j] * w - oz, c2,
                px[j] - sx[j] * w - ox, py[j] - sy[j] * w - oy, pz[j] - sz[j] * w - oz, c2);
    }

    private static int scaleAlpha(int argb, double f) {
        return (int) Math.round((argb >>> 24) * f) << 24 | (argb & 0xFFFFFF);
    }

    /** Colour of a point: the chosen colour (rainbow follows the time the point was made), fading out with age. */
    private int shade(long time, float age, double alpha) {
        int rgb = color.argbAt(time) & 0xFFFFFF;
        float fade = (1f - age);
        long a = Math.round(alpha * fade * fade);
        return (int) Math.max(0, Math.min(255, a)) << 24 | rgb;
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

        Mesh build(boolean glow) {
            return n == 0 ? Mesh.EMPTY : new Mesh(xyz, colors, n, glow);
        }
    }
}
