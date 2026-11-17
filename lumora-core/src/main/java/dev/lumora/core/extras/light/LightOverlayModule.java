package dev.lumora.core.extras.light;

import dev.lumora.api.addon.AddonContext;
import dev.lumora.api.addon.LumoraAddon;
import dev.lumora.api.config.BooleanSetting;
import dev.lumora.api.config.ChoiceSetting;
import dev.lumora.api.config.ColorSetting;
import dev.lumora.api.config.IntegerSetting;
import dev.lumora.api.config.SettingsGroup;
import dev.lumora.api.event.client.ClientStoppingEvent;
import dev.lumora.api.event.client.ClientTickEvent;
import dev.lumora.api.event.client.WorldLeaveEvent;
import dev.lumora.api.input.Keys;
import dev.lumora.core.addon.VersionSupport;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Built-in module "Light Block Overlay" with two modes. "Light level" shows the light of every block you can stand on,
 * from 15 (fully lit) to 0 (no light), as a number, tile or marker in a colour from dark to bright (block light, sky
 * light or both). "Spawn" marks every block in reach where hostile mobs can spawn, red where they can spawn right now,
 * yellow where only at night (enough sky light by day); styles cross, number (block light level, also on safe blocks),
 * tile and marker, optional colour gradient by sky light. A key shows and hides it.
 * <p>
 * Performance: the world is read on the client thread in whole chunk columns, nearest chunks first, within a time
 * budget per tick ({@link #BUDGET_NANOS}); a chunk is read again only every few ticks (nearby more often, so a new
 * torch shows within half a second). The geometry of a chunk ({@link LightMesh}) is built only after a scan or a
 * setting change, so drawing a frame only copies finished vertices. Nothing is read or drawn while the overlay is
 * hidden. Idea after the mods "Light Overlay" (shedaniel) and Lunar Client's light overlay; own code.
 */
public final class LightOverlayModule implements LumoraAddon {
    public static final String ID = "lumora-lightoverlay";
    static final String K = "lumora.lightoverlay.";
    /** Longest time the world is read per client tick. */
    static final long BUDGET_NANOS = 1_500_000L;
    /** Extra height scanned above and below the reach, so a step up or down does not need a new scan. */
    static final int Y_MARGIN = 4;
    /** Mobs only spawn where the light from the sky is at most this (at night the sky gives less). */
    static final int SKY_LIMIT = 7;
    /** Numbers are drawn only this many blocks around the player (they cost several lines each). */
    static final int NUMBER_REACH = 16;
    /** Since 1.18 hostile mobs need block light 0; before, block light up to 7 was enough. */
    static final String BLOCK_LIGHT_ZERO_SINCE = "1.18";

    private static volatile LightOverlayModule active;

    private final Supplier<LightProbe> probe;
    private final String minecraftVersion;
    private final LongSupplier nanos;
    ChoiceSetting mode;
    ChoiceSetting style;
    ChoiceSetting lightStyle;
    ChoiceSetting lightType;
    IntegerSetting horizontal;
    IntegerSetting vertical;
    IntegerSetting lightLevel;
    BooleanSetting gradient;
    BooleanSetting safeNumbers;
    ColorSetting colorNow;
    ColorSetting colorNight;
    ColorSetting colorSafe;
    ColorSetting colorDark;
    ColorSetting colorMedium;
    ColorSetting colorBright;
    /** Shown or hidden with the key (the module itself stays switched on). */
    private boolean shown = true;
    private boolean enabled;

    private final Map<Long, ChunkData> chunks = new HashMap<>();
    private LightMesh[] meshes = new LightMesh[0];
    private Object world;
    private final int[] player = new int[4];
    private int lastY = Integer.MIN_VALUE;
    private int lastFacing = -1;
    private int lastLookHash;
    private long tick;
    /** Chunk offsets sorted by distance, for the current reach in chunks. */
    private int[] order = new int[0];
    private int orderRadius = -1;
    private final float[] corners = new float[12];
    /** Longest time one tick spent reading the world, and chunks read so far (smoke test, diagnostics). */
    private long maxTickNanos;
    private long scans;
    /** Time spent in chunk scans and ticks that went over the budget. */
    private long scanNanos;
    private long overBudget;
    /** Moving average of one chunk scan, to stop before the budget is used up. */
    private double avgChunkNanos = 300_000;
    /** Player position in steps of four blocks while numbers are shown (0 otherwise). */
    private int lastCell;
    /** Whether the scanned chunks hold every standable block (light level mode) or only spawn positions. */
    private boolean scannedSurfaces;

    public LightOverlayModule(Supplier<LightProbe> probe, String minecraftVersion) {
        this(probe, minecraftVersion, System::nanoTime);
    }

    LightOverlayModule(Supplier<LightProbe> probe, String minecraftVersion, LongSupplier nanos) {
        this.probe = probe;
        this.minecraftVersion = minecraftVersion;
        this.nanos = nanos;
    }

    /** The module while the overlay is on screen (switched on and shown), else null. Read by the version renderers. */
    public static LightOverlayModule active() {
        return active;
    }

    /** The chunk meshes to draw this frame (never null; empty meshes are left out). */
    public LightMesh[] meshes() {
        return meshes;
    }

    /** Chunks, scanned spawn positions, segments, quads, chunk scans so far and the longest tick in ms (smoke test). */
    public String stats() {
        int marks = 0;
        for (ChunkData d : chunks.values()) {
            marks += d.count;
        }
        int lines = 0;
        int quads = 0;
        for (LightMesh m : meshes) {
            lines += m.lineCount;
            quads += m.quadCount;
        }
        return "chunks=" + chunks.size() + " positions=" + marks + " lines=" + lines + " quads=" + quads + " scans=" + scans
                + " maxTickMs=" + String.format(java.util.Locale.ROOT, "%.2f", maxTickNanos / 1e6)
                + " avgChunkMs=" + String.format(java.util.Locale.ROOT, "%.3f", scans == 0 ? 0 : scanNanos / 1e6 / scans)
                + " ticksOverBudget=" + overBudget;
    }

    /** Switches the style of the spawn mode (smoke test: one screenshot per style). */
    public void setStyle(String value) {
        style.decode(value);
    }

    /** Switches the mode: "light" (light level of every block) or "spawn" (smoke test). */
    public void setMode(String value) {
        mode.decode(value);
    }

    /** Switches the style of the light level mode: "number", "tile" or "marker" (smoke test). */
    public void setLightStyle(String value) {
        lightStyle.decode(value);
    }

    /** Whether the light level of every standable block is shown (else only spawn positions). */
    boolean lightMode() {
        return "light".equals(mode.get());
    }

    /** The style drawn now: the light level style or the spawn style, by mode. */
    String drawStyle() {
        return lightMode() ? lightStyle.get() : style.get();
    }

    /** Horizontal reach in blocks (the renderer skips chunks farther away). */
    public int reach() {
        return horizontal.get();
    }

    @Override
    public void onInitialize(AddonContext context) {
        settings(context.configuration().settings());
    }

    void settings(SettingsGroup s) {
        mode = s.selection("mode", "light", "light", "spawn").name(K + "setting.mode")
                .description(K + "setting.mode.description").section(K + "section.general");
        lightStyle = s.selection("light_style", "number", "number", "tile", "marker").name(K + "setting.light_style")
                .description(K + "setting.light_style.description").visibleWhen(this::lightMode);
        lightType = s.selection("light_type", "block", "block", "sky", "combined").name(K + "setting.light_type")
                .description(K + "setting.light_type.description").visibleWhen(this::lightMode);
        style = s.selection("style", "cross", "cross", "number", "tile", "marker").name(K + "setting.style")
                .description(K + "setting.style.description").visibleWhen(() -> !lightMode());
        horizontal = s.slider("horizontal", 32, 8, 64).step(4).name(K + "setting.horizontal")
                .description(K + "setting.horizontal.description");
        vertical = s.slider("vertical", 16, 4, 32).step(2).name(K + "setting.vertical")
                .description(K + "setting.vertical.description");
        colorDark = s.color("color_dark", 0xFFFF4040, false).name(K + "setting.color_dark")
                .description(K + "setting.color_dark.description").section(K + "section.look").visibleWhen(this::lightMode);
        colorMedium = s.color("color_medium", 0xFFFFD43B, false).name(K + "setting.color_medium")
                .description(K + "setting.color_medium.description").visibleWhen(this::lightMode);
        colorBright = s.color("color_bright", 0xFF4ADE80, false).name(K + "setting.color_bright")
                .description(K + "setting.color_bright.description").visibleWhen(this::lightMode);
        gradient = s.toggle("gradient", false).name(K + "setting.gradient").description(K + "setting.gradient.description")
                .visibleWhen(() -> !lightMode());
        colorNow = s.color("color_now", 0xFFFF4040, false).name(K + "setting.color_now").description(K + "setting.color_now.description")
                .visibleWhen(() -> !lightMode());
        colorNight = s.color("color_night", 0xFFFFD43B, false).name(K + "setting.color_night")
                .description(K + "setting.color_night.description").visibleWhen(() -> !lightMode());
        safeNumbers = s.toggle("safe_numbers", true).name(K + "setting.safe_numbers")
                .description(K + "setting.safe_numbers.description").visibleWhen(() -> !lightMode() && "number".equals(style.get()));
        colorSafe = s.color("color_safe", 0xFF4ADE80, false).name(K + "setting.color_safe")
                .visibleWhen(() -> !lightMode() && "number".equals(style.get()));
        int defaultLevel = VersionSupport.compare(minecraftVersion, BLOCK_LIGHT_ZERO_SINCE) >= 0 ? 0 : 7;
        lightLevel = s.slider("light_level", defaultLevel, 0, 15).name(K + "setting.light_level")
                .description(K + "setting.light_level.description").section(K + "section.advanced").advanced()
                .visibleWhen(() -> !lightMode());
    }

    @Override
    public void onEnable(AddonContext context) {
        enable();
        context.keybinds().register("toggle", K + "key", Keys.UNBOUND, this::toggleShown);
        context.events().subscribe(ClientTickEvent.class, e -> tick());
        context.events().subscribe(WorldLeaveEvent.class, e -> clear());
        context.events().subscribe(ClientStoppingEvent.class, e -> clear());
    }

    void enable() {
        enabled = true;
        shown = true;
        publish();
    }

    @Override
    public void onDisable(AddonContext context) {
        disable();
    }

    void disable() {
        enabled = false;
        clear();
        publish();
    }

    private void publish() {
        active = enabled && shown ? this : null;
    }

    /** Forgets every scanned chunk (hidden, world left, module off). */
    void clear() {
        chunks.clear();
        meshes = new LightMesh[0];
        world = null;
        lastY = Integer.MIN_VALUE;
        lastFacing = -1;
    }

    boolean shown() {
        return shown;
    }

    void toggleShown() {
        shown = !shown;
        if (!shown) {
            clear();
        }
        publish();
    }

    // --- scanning

    void tick() {
        tick++;
        if (!enabled || !shown) {
            return;
        }
        LightProbe p = probe.get();
        Object w = p == null ? null : p.world();
        if (w == null || !p.player(player)) {
            if (world != null) {
                clear();
            }
            return;
        }
        boolean surfaces = lightMode();
        if (surfaces != scannedSurfaces) {
            // the other mode reads other positions: everything is read again
            clear();
            scannedSurfaces = surfaces;
        }
        if (w != world) {
            clear();
            world = w;
        }
        int h = horizontal.get();
        int v = vertical.get();
        int radius = (h + 15) >> 4;
        int pcx = player[0] >> 4;
        int pcz = player[2] >> 4;
        int py = player[1];
        boolean dirtyMeshes = false;
        // a setting that only changes the look rebuilds the meshes, the scanned light stays valid
        int look = lookHash();
        // numbers are drawn only near the player: their meshes follow the player in steps of a few blocks
        int cell = "number".equals(drawStyle()) ? (player[0] >> 2) * 92821 + (player[2] >> 2) : 0;
        if (look != lastLookHash || py != lastY || player[3] != lastFacing || cell != lastCell) {
            lastLookHash = look;
            lastY = py;
            lastFacing = player[3];
            lastCell = cell;
            dirtyMeshes = true;
        }
        // chunks that fell out of reach
        for (Iterator<ChunkData> it = chunks.values().iterator(); it.hasNext(); ) {
            ChunkData d = it.next();
            if (Math.abs(d.cx - pcx) > radius + 1 || Math.abs(d.cz - pcz) > radius + 1) {
                it.remove();
                dirtyMeshes = true;
            }
        }
        if (orderRadius != radius) {
            order = spiral(radius);
            orderRadius = radius;
        }
        int minY = py - v;
        int maxY = py + v;
        long start = nanos.getAsLong();
        long deadline = start + BUDGET_NANOS;
        boolean scanned = false;
        for (int i = 0; i < order.length; i += 2) {
            int cx = pcx + order[i];
            int cz = pcz + order[i + 1];
            long key = key(cx, cz);
            ChunkData d = chunks.get(key);
            int dist = Math.max(Math.abs(order[i]), Math.abs(order[i + 1]));
            if (d != null && d.covers(minY, maxY) && tick - d.scannedAt < interval(dist)) {
                continue;
            }
            // start another chunk only if it is expected to fit into the budget; at least one chunk per tick
            if (scanned && nanos.getAsLong() + avgChunkNanos > deadline) {
                break;
            }
            if (d == null) {
                d = new ChunkData(cx, cz);
            }
            long t0 = nanos.getAsLong();
            boolean ok = scan(p, d, minY - Y_MARGIN, maxY + Y_MARGIN, surfaces);
            long took = nanos.getAsLong() - t0;
            scanNanos += took;
            avgChunkNanos = avgChunkNanos * 0.9 + took * 0.1;
            if (ok) {
                chunks.put(key, d);
                build(d);
                dirtyMeshes = true;
                scans++;
            }
            scanned = true;
        }
        if (scanned) {
            long spent = nanos.getAsLong() - start;
            maxTickNanos = Math.max(maxTickNanos, spent);
            if (spent > BUDGET_NANOS) {
                overBudget++;
            }
        }
        if (dirtyMeshes) {
            long built = buildKey();
            for (ChunkData d : chunks.values()) {
                if (d.builtFor != built) {
                    build(d);
                }
            }
            List<LightMesh> out = new ArrayList<>(chunks.size());
            for (ChunkData d : chunks.values()) {
                if (!d.mesh.isEmpty()) {
                    out.add(d.mesh);
                }
            }
            meshes = out.toArray(new LightMesh[0]);
        }
    }

    /** Ticks between two scans of a chunk at this distance (in chunks): the own chunk twice a second. */
    static int interval(int dist) {
        return dist <= 1 ? 10 : 20 + dist * 10;
    }

    private boolean scan(LightProbe p, ChunkData d, int minY, int maxY, boolean surfaces) {
        d.count = 0;
        boolean ok = p.scan(d.cx, d.cz, minY, maxY, surfaces, d);
        if (ok) {
            d.minY = minY;
            d.maxY = maxY;
            d.scannedAt = tick;
        }
        return ok;
    }

    /** Offsets (dx, dz pairs) of all chunks within {@code radius}, nearest first. */
    static int[] spiral(int radius) {
        List<int[]> all = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                all.add(new int[]{dx, dz});
            }
        }
        all.sort((a, b) -> Integer.compare(a[0] * a[0] + a[1] * a[1], b[0] * b[0] + b[1] * b[1]));
        int[] out = new int[all.size() * 2];
        for (int i = 0; i < all.size(); i++) {
            out[i * 2] = all.get(i)[0];
            out[i * 2 + 1] = all.get(i)[1];
        }
        return out;
    }

    static long key(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
    }

    private int lookHash() {
        int hsh = mode.get().hashCode();
        hsh = hsh * 31 + style.get().hashCode();
        hsh = hsh * 31 + lightStyle.get().hashCode();
        hsh = hsh * 31 + lightType.get().hashCode();
        hsh = hsh * 31 + colorDark.get();
        hsh = hsh * 31 + colorMedium.get();
        hsh = hsh * 31 + colorBright.get();
        hsh = hsh * 31 + lightLevel.get();
        hsh = hsh * 31 + (gradient.get() ? 1 : 0);
        hsh = hsh * 31 + (safeNumbers.get() ? 1 : 0);
        hsh = hsh * 31 + colorNow.get();
        hsh = hsh * 31 + colorNight.get();
        hsh = hsh * 31 + colorSafe.get();
        hsh = hsh * 31 + vertical.get();
        hsh = hsh * 31 + horizontal.get();
        return hsh == 0 ? 1 : hsh;
    }

    /** What a mesh was built for: the look, the player height and facing. */
    private long buildKey() {
        return (((long) lastLookHash << 32) ^ ((long) (lastY & 0xFFFFF) << 2) ^ (lastFacing & 3))
                + lastCell * 0x9E3779B97F4A7C15L;
    }

    // --- geometry

    /** Kind of a scanned position, or -1 if it is not shown. */
    static final int NOW = 0;
    static final int NIGHT = 1;
    static final int SAFE = 2;

    static int kind(int blockLight, int skyLight, int level, boolean showSafe) {
        if (blockLight <= level) {
            return skyLight <= SKY_LIMIT ? NOW : NIGHT;
        }
        return showSafe ? SAFE : -1;
    }

    int color(int kind, int skyLight) {
        if (kind == SAFE) {
            return colorSafe.get() | 0xFF000000;
        }
        if (gradient.get()) {
            return lerp(colorNow.get(), colorNight.get(), skyLight / 15f) | 0xFF000000;
        }
        return (kind == NOW ? colorNow.get() : colorNight.get()) | 0xFF000000;
    }

    /** The light value shown in light level mode: block light, sky light (as by day) or the brighter of both. */
    static int lightValue(String type, int blockLight, int skyLight) {
        return switch (type) {
            case "sky" -> skyLight;
            case "combined" -> Math.max(blockLight, skyLight);
            default -> blockLight;
        };
    }

    /** Colour of a light level: dark (0) over medium (7) to bright (15). */
    int lightColor(int value) {
        int c = value <= 7 ? lerp(colorDark.get(), colorMedium.get(), value / 7f)
                : lerp(colorMedium.get(), colorBright.get(), (value - 7) / 8f);
        return c | 0xFF000000;
    }

    static int lerp(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int g = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return (r << 16) | (g << 8) | bl;
    }

    /** Rebuilds the geometry of one chunk from its scanned positions (only those within the vertical reach). */
    void build(ChunkData d) {
        LightMesh m = d.mesh;
        m.clear();
        d.builtFor = buildKey();
        boolean light = lightMode();
        String st = drawStyle();
        String type = lightType.get();
        int level = lightLevel.get();
        boolean number = "number".equals(st);
        boolean showSafe = number && safeNumbers.get();
        int v = vertical.get();
        for (int i = 0; i < d.count; i++) {
            int packed = d.marks[i];
            int x = (packed >>> 4) & 15;
            int z = packed & 15;
            int bl = (packed >>> 8) & 15;
            int sky = (packed >>> 12) & 15;
            int y = packed >> 16;
            if (Math.abs(y - lastY) > v) {
                continue;
            }
            // numbers take up to 14 segments each: only near the player
            if (number && (Math.abs(d.cx * 16 + x - player[0]) > NUMBER_REACH || Math.abs(d.cz * 16 + z - player[2]) > NUMBER_REACH)) {
                continue;
            }
            int shown = bl;
            int c;
            if (light) {
                shown = lightValue(type, bl, sky);
                c = lightColor(shown);
            } else {
                int kind = kind(bl, sky, level, showSafe);
                if (kind < 0) {
                    continue;
                }
                c = color(kind, sky);
            }
            float fy = y + 0.02f;
            switch (st) {
                case "number" -> number(m, x, fy, z, shown, c, lastFacing);
                case "tile" -> tile(m, x, fy, z, c);
                case "marker" -> marker(m, x, y, z, c);
                default -> {
                    m.line(x, fy, z, x + 1, fy, z + 1, c);
                    m.line(x + 1, fy, z, x, fy, z + 1, c);
                }
            }
        }
    }

    private void tile(LightMesh m, int x, float y, int z, int c) {
        float[] q = corners;
        set(q, 0, x, y, z);
        set(q, 1, x, y, z + 1);
        set(q, 2, x + 1, y, z + 1);
        set(q, 3, x + 1, y, z);
        m.quad(q, (c & 0xFFFFFF) | 0x50000000);
        m.line(x, y, z, x + 1, y, z, c);
        m.line(x, y, z + 1, x + 1, y, z + 1, c);
        m.line(x, y, z, x, y, z + 1, c);
        m.line(x + 1, y, z, x + 1, y, z + 1, c);
    }

    /** A low translucent pillar on the block (four sides and the top). */
    private void marker(LightMesh m, int x, int y, int z, int c) {
        float x1 = x + 0.3f;
        float x2 = x + 0.7f;
        float z1 = z + 0.3f;
        float z2 = z + 0.7f;
        float y1 = y + 0.01f;
        float y2 = y + 0.6f;
        int fill = (c & 0xFFFFFF) | 0x70000000;
        float[] q = corners;
        set(q, 0, x1, y2, z1);
        set(q, 1, x1, y2, z2);
        set(q, 2, x2, y2, z2);
        set(q, 3, x2, y2, z1);
        m.quad(q, fill);
        set(q, 0, x1, y1, z1);
        set(q, 1, x1, y2, z1);
        set(q, 2, x2, y2, z1);
        set(q, 3, x2, y1, z1);
        m.quad(q, fill);
        set(q, 0, x1, y1, z2);
        set(q, 1, x2, y1, z2);
        set(q, 2, x2, y2, z2);
        set(q, 3, x1, y2, z2);
        m.quad(q, fill);
        set(q, 0, x1, y1, z1);
        set(q, 1, x1, y1, z2);
        set(q, 2, x1, y2, z2);
        set(q, 3, x1, y2, z1);
        m.quad(q, fill);
        set(q, 0, x2, y1, z1);
        set(q, 1, x2, y2, z1);
        set(q, 2, x2, y2, z2);
        set(q, 3, x2, y1, z2);
        m.quad(q, fill);
    }

    private static void set(float[] q, int corner, float x, float y, float z) {
        q[corner * 3] = x;
        q[corner * 3 + 1] = y;
        q[corner * 3 + 2] = z;
    }

    /** Segments a..g of a seven-segment digit (bit 0 = a). */
    static final int[] DIGITS = {0x3F, 0x06, 0x5B, 0x4F, 0x66, 0x6D, 0x7D, 0x07, 0x7F, 0x6F};
    /** Segment ends in a unit cell, u to the right and v up: a, b, c, d, e, f, g. */
    static final float[][] SEGMENTS = {{0, 1, 1, 1}, {1, 1, 1, 0.5f}, {1, 0.5f, 1, 0}, {0, 0, 1, 0}, {0, 0.5f, 0, 0},
            {0, 1, 0, 0.5f}, {0, 0.5f, 1, 0.5f}};

    /**
     * The block light level as seven-segment digits flat on the block, upright for the player: the top of the digits
     * points the way the player looks.
     */
    static void number(LightMesh m, int x, float y, int z, int value, int c, int facing) {
        String text = Integer.toString(value);
        float cw = 0.22f;
        float ch = 0.4f;
        float gap = 0.1f;
        float total = text.length() * cw + (text.length() - 1) * gap;
        // forward (direction of view) and right on the ground
        float fx;
        float fz;
        switch (facing & 3) {
            case 1 -> { fx = -1; fz = 0; } // west
            case 2 -> { fx = 0; fz = -1; } // north
            case 3 -> { fx = 1; fz = 0; } // east
            default -> { fx = 0; fz = 1; } // south
        }
        float rx = -fz;
        float rz = fx;
        float cx = x + 0.5f;
        float cz = z + 0.5f;
        for (int d = 0; d < text.length(); d++) {
            int mask = DIGITS[text.charAt(d) - '0'];
            float u0 = -total / 2 + d * (cw + gap);
            for (int s = 0; s < 7; s++) {
                if ((mask & (1 << s)) == 0) {
                    continue;
                }
                float[] seg = SEGMENTS[s];
                float u1 = u0 + seg[0] * cw;
                float v1 = -ch / 2 + seg[1] * ch;
                float u2 = u0 + seg[2] * cw;
                float v2 = -ch / 2 + seg[3] * ch;
                m.line(cx + rx * u1 + fx * v1, y, cz + rz * u1 + fz * v1, cx + rx * u2 + fx * v2, y, cz + rz * u2 + fz * v2, c);
            }
        }
    }

    // --- state of one chunk column

    /** Scanned positions of a chunk column (packed: y << 16 | sky << 12 | block << 8 | x << 4 | z) and its mesh. */
    static final class ChunkData implements LightProbe.Sink {
        final int cx;
        final int cz;
        int[] marks = new int[64];
        int count;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        long scannedAt;
        long builtFor = Long.MIN_VALUE;
        final LightMesh mesh;

        ChunkData(int cx, int cz) {
            this.cx = cx;
            this.cz = cz;
            this.mesh = new LightMesh(cx << 4, cz << 4);
        }

        boolean covers(int min, int max) {
            return min >= minY && max <= maxY;
        }

        @Override
        public void mark(int x, int y, int z, int blockLight, int skyLight) {
            if (count == marks.length) {
                marks = java.util.Arrays.copyOf(marks, marks.length * 2);
            }
            marks[count++] = (y << 16) | ((skyLight & 15) << 12) | ((blockLight & 15) << 8) | ((x & 15) << 4) | (z & 15);
        }
    }
}
