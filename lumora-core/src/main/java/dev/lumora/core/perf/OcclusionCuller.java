package dev.lumora.core.perf;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

/**
 * Occlusion culling for entities and block entities, independent of the Minecraft version.
 * <p>
 * The render thread hands every entity that passed Minecraft's own visibility check to {@link #hidden} together with
 * its culling box (Minecraft's frustum box) and gets the result of the last check back: one array write and a few
 * field reads, no allocation. At the start of each frame ({@link #frame}) the collected entities go to a background
 * thread, which casts rays from the camera to points on the faces of each box (at most 1 block apart, so no gap of one
 * block or more between opaque blocks is missed) through a cache of the opaque full blocks; an entity counts as hidden
 * only if every ray ends in an opaque full block. Results older than a few passes count as visible, entities near the
 * camera are never culled, and when the camera itself is inside an opaque block nothing is culled.
 * <p>
 * The version adapter provides the opaque blocks ({@link World}, called on the culling thread) and reports changed
 * sections ({@link #invalidate}) and world changes ({@link #reset}).
 */
public final class OcclusionCuller {
    /** Opaque full blocks of the client world, read on the culling thread. */
    public interface World {
        /**
         * The opaque full blocks of the 16x16x16 section as 64 longs, bit {@code (y << 8) | (z << 4) | x} = local
         * position; {@link #EMPTY} if it has none; {@code null} if the section is not loaded or cannot be read now
         * (it then counts as transparent).
         */
        long[] section(int sectionX, int sectionY, int sectionZ);
    }

    /** A section without opaque full blocks. */
    public static final long[] EMPTY = new long[64];
    /** Results computed more than this many passes ago count as visible. */
    static final int STALE_PASSES = 3;
    /** Boxes closer to the camera than this (blocks) are never culled. */
    static final double NEAR = 4.0;
    /** Boxes farther away than this (blocks) are not checked (they stay visible). */
    static final double FAR = 256.0;
    private static final int MAX_SAMPLES_PER_AXIS = 8;
    private static final long SECTION_TTL_NANOS = 3_000_000_000L;
    private static final long MISSING_TTL_NANOS = 500_000_000L;
    private static final long[] MISSING = new long[0];

    static final class Batch {
        double camX;
        double camY;
        double camZ;
        Cullable[] targets = new Cullable[256];
        double[] boxes = new double[256 * 6];
        int count;

        void add(Cullable t, double x0, double y0, double z0, double x1, double y1, double z1) {
            int n = count;
            if (n == targets.length) {
                targets = java.util.Arrays.copyOf(targets, n * 2);
                boxes = java.util.Arrays.copyOf(boxes, n * 12);
            }
            targets[n] = t;
            int b = n * 6;
            boxes[b] = x0;
            boxes[b + 1] = y0;
            boxes[b + 2] = z0;
            boxes[b + 3] = x1;
            boxes[b + 4] = y1;
            boxes[b + 5] = z1;
            count = n + 1;
        }
    }

    private final World world;
    private final Consumer<String> warn;
    private final AtomicInteger pass = new AtomicInteger(1);
    private final AtomicReference<Batch> published = new AtomicReference<>();
    private final AtomicReference<Batch> free = new AtomicReference<>();
    /** Render thread only. */
    private Batch writing = new Batch();
    private volatile boolean running;
    private volatile boolean failed;
    private Thread thread;
    private final boolean background;

    // section cache, culling thread only (open addressing, key = packed section position)
    private long[] keys = new long[4096];
    private long[][] sections = new long[4096][];
    private long[] built = new long[4096];
    private int size;
    private long[] current;
    private int curX = Integer.MIN_VALUE;
    private int curY;
    private int curZ;
    private long now;

    // changed sections, written by the client thread
    private final long[] changed = new long[4096];
    private int changedCount;
    private boolean clearAll;

    // statistics (diagnostics, tests)
    private volatile int lastChecked;
    private volatile int lastHidden;
    private volatile long lastPassNanos;
    private volatile long passes;

    public OcclusionCuller(World world, Consumer<String> warn) {
        this(world, warn, true);
    }

    /** @param background false for tests: no thread, {@link #processOnce} is called directly */
    OcclusionCuller(World world, Consumer<String> warn, boolean background) {
        this.world = world;
        this.warn = warn;
        this.background = background;
    }

    // ------------------------------------------------------------------ render thread

    /**
     * Start of a frame's entity selection: the entities collected in the last frame go to the culling thread, the new
     * frame collects with this camera position.
     */
    public void frame(double camX, double camY, double camZ) {
        Batch full = writing;
        Batch next = full;
        if (full.count > 0) {
            Batch old = published.getAndSet(full);
            next = old != null ? old : free.getAndSet(null);
            if (next == null) {
                next = new Batch(); // at most three exist
            }
            Thread t = thread;
            if (t != null) {
                LockSupport.unpark(t);
            }
        }
        next.count = 0;
        next.camX = camX;
        next.camY = camY;
        next.camZ = camZ;
        writing = next;
        if (background && thread == null && !failed) {
            start();
        }
    }

    /**
     * Whether the target was completely hidden at its last check; also queues it for the next check. Targets near the
     * camera or very far away are never hidden.
     */
    public boolean hidden(Cullable target, double x0, double y0, double z0, double x1, double y1, double z1) {
        if (failed) {
            return false;
        }
        Batch b = writing;
        double cx = b.camX;
        double cy = b.camY;
        double cz = b.camZ;
        if (cx > x0 - NEAR && cx < x1 + NEAR && cy > y0 - NEAR && cy < y1 + NEAR && cz > z0 - NEAR && cz < z1 + NEAR) {
            return false;
        }
        double dx = (x0 + x1) * 0.5 - cx;
        double dy = (y0 + y1) * 0.5 - cy;
        double dz = (z0 + z1) * 0.5 - cz;
        if (dx * dx + dy * dy + dz * dz > FAR * FAR) {
            return false;
        }
        b.add(target, x0, y0, z0, x1, y1, z1);
        int state = target.lumora$cull();
        return (state & 1) != 0 && pass.get() - (state >>> 1) <= STALE_PASSES;
    }

    // ------------------------------------------------------------------ client thread

    /** Blocks of a section changed (or it was loaded or unloaded): read it again. */
    public void invalidate(int sectionX, int sectionY, int sectionZ) {
        synchronized (changed) {
            if (changedCount < changed.length) {
                changed[changedCount++] = key(sectionX, sectionY, sectionZ);
            } else {
                clearAll = true;
            }
        }
    }

    /** Another world or dimension: forget all sections and results. */
    public void reset() {
        synchronized (changed) {
            clearAll = true;
            changedCount = 0;
        }
        pass.addAndGet(STALE_PASSES + 1);
    }

    /** Entity culling switched on or off: results from before count as stale. */
    void switched(boolean on) {
        pass.addAndGet(STALE_PASSES + 1);
    }

    public synchronized void stop() {
        running = false;
        Thread t = thread;
        if (t != null) {
            LockSupport.unpark(t);
            try {
                t.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        thread = null;
    }

    /** Targets checked and found hidden in the last pass, its duration and the number of passes (diagnostics). */
    public int lastChecked() {
        return lastChecked;
    }

    public int lastHidden() {
        return lastHidden;
    }

    public long lastPassNanos() {
        return lastPassNanos;
    }

    public long passes() {
        return passes;
    }

    /** Whether the culler gave up after an error (everything is drawn then). */
    public boolean failed() {
        return failed;
    }

    // ------------------------------------------------------------------ culling thread

    private synchronized void start() {
        if (thread != null) {
            return;
        }
        running = true;
        Thread t = new Thread(this::run, "Lumora Occlusion Culling");
        t.setDaemon(true);
        t.setPriority(Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 1));
        thread = t;
        t.start();
    }

    private void run() {
        while (running) {
            try {
                if (!processOnce()) {
                    LockSupport.parkNanos(this, 2_000_000L);
                }
            } catch (Throwable e) {
                failed = true;
                running = false;
                warn.accept("Entity culling stopped after an error, everything is drawn again: " + e);
                return;
            }
        }
    }

    /** Processes the newest published batch; false if there was none. Package-private for tests. */
    boolean processOnce() {
        Batch b = published.getAndSet(null);
        drainChanges();
        if (b == null) {
            return false;
        }
        if (!PerformanceEngine.entityCulling) {
            release(b);
            return true;
        }
        long start = System.nanoTime();
        now = start;
        int p = pass.incrementAndGet();
        boolean cameraInside = opaque(floor(b.camX), floor(b.camY), floor(b.camZ));
        int hiddenCount = 0;
        int n = b.count;
        double[] box = b.boxes;
        for (int i = 0; i < n; i++) {
            int o = i * 6;
            boolean h = !cameraInside && hiddenBox(b.camX, b.camY, b.camZ, box[o], box[o + 1], box[o + 2], box[o + 3], box[o + 4], box[o + 5]);
            b.targets[i].lumora$cull(p << 1 | (h ? 1 : 0));
            if (h) {
                hiddenCount++;
            }
        }
        lastChecked = n;
        lastHidden = hiddenCount;
        lastPassNanos = System.nanoTime() - start;
        passes++;
        release(b);
        return true;
    }

    private void release(Batch b) {
        java.util.Arrays.fill(b.targets, 0, b.count, null);
        b.count = 0;
        free.set(b);
    }

    private void drainChanges() {
        boolean all;
        int n;
        long[] keysToDrop = null;
        synchronized (changed) {
            all = clearAll;
            clearAll = false;
            n = changedCount;
            if (!all && n > 0) {
                keysToDrop = java.util.Arrays.copyOf(changed, n);
            }
            changedCount = 0;
        }
        if (all) {
            java.util.Arrays.fill(sections, null);
            size = 0;
            curX = Integer.MIN_VALUE;
            current = null;
        } else if (keysToDrop != null) {
            for (long k : keysToDrop) {
                int slot = find(k);
                if (sections[slot] != null) {
                    built[slot] = Long.MIN_VALUE; // read again on next use
                }
            }
            curX = Integer.MIN_VALUE;
            current = null;
        }
    }

    /** Whether all sample points on the faces of the box that face the camera are behind opaque blocks. */
    boolean hiddenBox(double cx, double cy, double cz, double x0, double y0, double z0, double x1, double y1, double z1) {
        boolean faceX = cx < x0 || cx > x1;
        boolean faceY = cy < y0 || cy > y1;
        boolean faceZ = cz < z0 || cz > z1;
        if (!faceX && !faceY && !faceZ) {
            return false; // camera inside the box
        }
        double fx = cx < x0 ? x0 : x1;
        double fy = cy < y0 ? y0 : y1;
        double fz = cz < z0 ? z0 : z1;
        double mx = (x0 + x1) * 0.5;
        double my = (y0 + y1) * 0.5;
        double mz = (z0 + z1) * 0.5;
        // face centres first: most visible targets are found with one ray
        if (faceX && clear(cx, cy, cz, fx, my, mz) || faceY && clear(cx, cy, cz, mx, fy, mz) || faceZ && clear(cx, cy, cz, mx, my, fz)) {
            return false;
        }
        int nx = samples(x1 - x0);
        int ny = samples(y1 - y0);
        int nz = samples(z1 - z0);
        if (faceX && anyClear(cx, cy, cz, 0, fx, y0, y1, ny, z0, z1, nz)) {
            return false;
        }
        if (faceY && anyClear(cx, cy, cz, 1, fy, x0, x1, nx, z0, z1, nz)) {
            return false;
        }
        return !(faceZ && anyClear(cx, cy, cz, 2, fz, x0, x1, nx, y0, y1, ny));
    }

    /** Grid of points on one face: {@code axis} 0 = x fixed (a = y, b = z), 1 = y fixed (a = x, b = z), 2 = z fixed (a = x, b = y). */
    private boolean anyClear(double cx, double cy, double cz, int axis, double f, double a0, double a1, int na, double b0, double b1, int nb) {
        for (int i = 0; i < na; i++) {
            double a = a0 + (a1 - a0) * i / (na - 1);
            for (int j = 0; j < nb; j++) {
                double b = b0 + (b1 - b0) * j / (nb - 1);
                boolean c = switch (axis) {
                    case 0 -> clear(cx, cy, cz, f, a, b);
                    case 1 -> clear(cx, cy, cz, a, f, b);
                    default -> clear(cx, cy, cz, a, b, f);
                };
                if (c) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Points per axis so that neighbouring points are at most one block apart. */
    static int samples(double size) {
        return Math.max(2, Math.min(MAX_SAMPLES_PER_AXIS, (int) Math.ceil(size) + 1));
    }

    /**
     * Whether the segment from the camera to the point passes no opaque full block (the camera's own block is not
     * tested, the block of the point is: a point inside an opaque block cannot be seen).
     */
    boolean clear(double x0, double y0, double z0, double x1, double y1, double z1) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        double dz = z1 - z0;
        int x = floor(x0);
        int y = floor(y0);
        int z = floor(z0);
        int steps = Math.abs(floor(x1) - x) + Math.abs(floor(y1) - y) + Math.abs(floor(z1) - z);
        int sx = dx > 0 ? 1 : dx < 0 ? -1 : 0;
        int sy = dy > 0 ? 1 : dy < 0 ? -1 : 0;
        int sz = dz > 0 ? 1 : dz < 0 ? -1 : 0;
        double tdx = sx != 0 ? Math.abs(1.0 / dx) : Double.MAX_VALUE;
        double tdy = sy != 0 ? Math.abs(1.0 / dy) : Double.MAX_VALUE;
        double tdz = sz != 0 ? Math.abs(1.0 / dz) : Double.MAX_VALUE;
        double tmx = sx > 0 ? (x + 1 - x0) * tdx : sx < 0 ? (x0 - x) * tdx : Double.MAX_VALUE;
        double tmy = sy > 0 ? (y + 1 - y0) * tdy : sy < 0 ? (y0 - y) * tdy : Double.MAX_VALUE;
        double tmz = sz > 0 ? (z + 1 - z0) * tdz : sz < 0 ? (z0 - z) * tdz : Double.MAX_VALUE;
        for (int i = 0; i < steps; i++) {
            if (tmx < tmy) {
                if (tmx < tmz) {
                    x += sx;
                    tmx += tdx;
                } else {
                    z += sz;
                    tmz += tdz;
                }
            } else if (tmy < tmz) {
                y += sy;
                tmy += tdy;
            } else {
                z += sz;
                tmz += tdz;
            }
            if (opaque(x, y, z)) {
                return false;
            }
        }
        return true;
    }

    boolean opaque(int x, int y, int z) {
        int sx = x >> 4;
        int sy = y >> 4;
        int sz = z >> 4;
        if (sx != curX || sy != curY || sz != curZ) {
            current = section(sx, sy, sz);
            curX = sx;
            curY = sy;
            curZ = sz;
        }
        long[] s = current;
        if (s == null || s.length == 0 || s == EMPTY) {
            return false;
        }
        int i = (y & 15) << 8 | (z & 15) << 4 | (x & 15);
        return (s[i >>> 6] >>> (i & 63) & 1L) != 0;
    }

    private long[] section(int sx, int sy, int sz) {
        long k = key(sx, sy, sz);
        int slot = find(k);
        long[] s = sections[slot];
        if (s != null) {
            long age = now - built[slot];
            if (age < (s == MISSING ? MISSING_TTL_NANOS : SECTION_TTL_NANOS) && built[slot] != Long.MIN_VALUE) {
                return s;
            }
        }
        long[] read = world.section(sx, sy, sz);
        long[] store = read == null ? MISSING : read.length == 64 && isEmpty(read) ? EMPTY : read;
        if (sections[slot] == null) {
            if (size >= keys.length / 2) {
                grow();
                slot = find(k);
            }
            size++;
        }
        keys[slot] = k;
        sections[slot] = store;
        built[slot] = now;
        return store;
    }

    private static boolean isEmpty(long[] bits) {
        for (long l : bits) {
            if (l != 0) {
                return false;
            }
        }
        return true;
    }

    private int find(long k) {
        int mask = keys.length - 1;
        int slot = (int) (mix(k) & mask);
        while (sections[slot] != null && keys[slot] != k) {
            slot = (slot + 1) & mask;
        }
        return slot;
    }

    private void grow() {
        if (keys.length >= 1 << 16) {
            // too many sections remembered (long flights): start over instead of growing without end
            java.util.Arrays.fill(sections, null);
            size = 0;
            curX = Integer.MIN_VALUE;
            return;
        }
        long[] oldKeys = keys;
        long[][] oldSections = sections;
        long[] oldBuilt = built;
        keys = new long[oldKeys.length * 2];
        sections = new long[oldKeys.length * 2][];
        built = new long[oldKeys.length * 2];
        for (int i = 0; i < oldKeys.length; i++) {
            if (oldSections[i] != null) {
                int slot = find(oldKeys[i]);
                keys[slot] = oldKeys[i];
                sections[slot] = oldSections[i];
                built[slot] = oldBuilt[i];
            }
        }
    }

    private static long mix(long k) {
        k ^= k >>> 33;
        k *= 0xff51afd7ed558ccdL;
        k ^= k >>> 33;
        return k;
    }

    static long key(int sx, int sy, int sz) {
        return ((long) sx & 0x3FFFFFL) << 42 | ((long) sz & 0x3FFFFFL) << 20 | ((long) sy & 0xFFFFFL);
    }

    private static int floor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }
}
