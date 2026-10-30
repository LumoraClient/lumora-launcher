package dev.lumora.launcher.app.ui;

import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Own pixel-art backgrounds for the version cards, generated in code (no Mojang artwork): a small landscape per
 * major version in the colours of its update (1.16 Nether, 1.17 lush caves, 1.19 deep dark, 1.20 cherry grove …),
 * rendered at low resolution and scaled up with nearest-neighbour so it looks blocky like the game.
 */
public final class VersionArt {
    private enum Kind { HILLS, FOREST, MOUNTAINS, CAVE, NETHER, OCEAN }

    private record Theme(Kind kind, int skyTop, int skyBottom, int ground, int groundDark, int accent, int accent2, long seed) {
    }

    private static final Map<String, Theme> THEMES = Map.ofEntries(
            Map.entry("26.3", new Theme(Kind.FOREST, 0x7A8FB8, 0xE0B07A, 0x6E4A2B, 0x3E2A1A, 0xD9762B, 0xF2C14E, 263)),
            Map.entry("26.2", new Theme(Kind.CAVE, 0x2A3320, 0x4A5A2E, 0x5B6B2C, 0x2C3317, 0xC9B455, 0x8FA347, 262)),
            Map.entry("26.1", new Theme(Kind.HILLS, 0x5C8FE0, 0xA9CBF5, 0x5DA03E, 0x6B4E33, 0xF0F0F0, 0x8E6B4A, 261)),
            Map.entry("1.21", new Theme(Kind.MOUNTAINS, 0x2E2A4A, 0xC77A5C, 0x6E6A5E, 0x3A3832, 0xC9733E, 0x3FA7A0, 121)),
            Map.entry("1.20", new Theme(Kind.FOREST, 0x9A7BD1, 0xF2B5D4, 0x5E9A45, 0x4A3527, 0xF3A6C8, 0xFFD3E6, 120)),
            Map.entry("1.19", new Theme(Kind.CAVE, 0x05080C, 0x0B1A22, 0x0E1C24, 0x050A0E, 0x25D6D6, 0x0F6F7A, 119)),
            Map.entry("1.18", new Theme(Kind.MOUNTAINS, 0x6E90C8, 0xD8C9B8, 0x9A8E80, 0x5E554C, 0xF4F4F4, 0xBFAF9A, 118)),
            Map.entry("1.17", new Theme(Kind.CAVE, 0x101A10, 0x1E3320, 0x3F7A2E, 0x1A2E18, 0xF2A33A, 0x7BC24B, 117)),
            Map.entry("1.16", new Theme(Kind.NETHER, 0x2A0606, 0x6E1410, 0x7A1C16, 0x3A0A08, 0xFF7A1A, 0xC23A2A, 116)),
            Map.entry("1.12", new Theme(Kind.HILLS, 0x4A2A78, 0xB04FC9, 0x7B3FA6, 0x3E2266, 0x3FD1C9, 0xF2D13A, 112)),
            Map.entry("1.8", new Theme(Kind.OCEAN, 0x0C1B3A, 0x1F4E7A, 0x2E6E7A, 0x173A4A, 0x7FD6C9, 0x3E8E9E, 18)));

    private static final Map<String, BufferedImage> CACHE = new ConcurrentHashMap<>();
    private static final int W = 120;
    private static final int H = 54;

    private VersionArt() {
    }

    /** Low-resolution landscape for a family key ("1.21", "26.1", …); draw it scaled up without interpolation. */
    public static BufferedImage of(String family) {
        return CACHE.computeIfAbsent(family, VersionArt::render);
    }

    private static int mix(int a, int b, double t) {
        int r = (int) Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private static int shade(int c, double f) {
        return mix(c, f < 0 ? 0x000000 : 0xFFFFFF, Math.abs(f));
    }

    /** Smooth 1D value noise from a seeded random table. */
    private static double[] ridge(Random r, int n, double rough) {
        double[] p = new double[n];
        double v = r.nextDouble();
        for (int i = 0; i < n; i++) {
            v += (r.nextDouble() - 0.5) * rough;
            v = Math.max(0, Math.min(1, v));
            p[i] = v;
        }
        for (int k = 0; k < 2; k++) {
            for (int i = 1; i < n - 1; i++) {
                p[i] = (p[i - 1] + p[i] * 2 + p[i + 1]) / 4;
            }
        }
        return p;
    }

    private static BufferedImage render(String family) {
        Theme t = THEMES.getOrDefault(family, new Theme(Kind.HILLS, 0x2B4A6E, 0x6E8FB8, 0x3F6E4A, 0x2A3A2E, 0x18B4CC, 0x8FD6E0,
                family.hashCode()));
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Random r = new Random(t.seed);
        for (int y = 0; y < H; y++) {
            int c = mix(t.skyTop, t.skyBottom, (double) y / H);
            for (int x = 0; x < W; x++) {
                img.setRGB(x, y, c);
            }
        }
        switch (t.kind) {
            case HILLS, FOREST -> {
                // clouds
                for (int i = 0; i < 4; i++) {
                    int cx = r.nextInt(W);
                    int cy = 3 + r.nextInt(10);
                    int cw = 8 + r.nextInt(10);
                    for (int x = cx; x < cx + cw; x++) {
                        for (int y = cy; y < cy + 2 + (x % 3 == 0 ? 1 : 0); y++) {
                            put(img, x, y, shade(t.skyBottom, 0.6));
                        }
                    }
                }
                double[] far = ridge(r, W, 0.25);
                double[] near = ridge(r, W, 0.35);
                for (int x = 0; x < W; x++) {
                    int top = (int) (H * 0.45 + far[x] * H * 0.18);
                    for (int y = top; y < H; y++) {
                        put(img, x, y, mix(shade(t.ground, -0.15), t.skyBottom, 0.45));
                    }
                }
                for (int x = 0; x < W; x++) {
                    int top = (int) (H * 0.6 + near[x] * H * 0.2);
                    for (int y = top; y < H; y++) {
                        put(img, x, y, y == top ? t.ground : y < top + 3 ? shade(t.ground, -0.15) : t.groundDark);
                    }
                    if (t.kind == Kind.FOREST && r.nextInt(9) == 0 && x > 2 && x < W - 3) {
                        tree(img, r, x, top, t.groundDark, t.accent, t.accent2);
                    }
                }
            }
            case MOUNTAINS -> {
                double[] peaks = ridge(r, W, 0.55);
                for (int x = 0; x < W; x++) {
                    int top = (int) (H * 0.12 + (1 - peaks[x]) * H * 0.5);
                    for (int y = top; y < H; y++) {
                        int c = y < top + 3 + (x % 4) ? t.accent : mix(t.ground, t.groundDark, (double) (y - top) / (H - top));
                        put(img, x, y, c);
                    }
                }
                double[] front = ridge(r, W, 0.3);
                for (int x = 0; x < W; x++) {
                    int top = (int) (H * 0.72 + front[x] * H * 0.15);
                    for (int y = top; y < H; y++) {
                        put(img, x, y, y < top + 2 ? t.accent2 : t.groundDark);
                    }
                }
            }
            case CAVE -> {
                double[] ceil = ridge(r, W, 0.4);
                double[] floor = ridge(r, W, 0.4);
                for (int x = 0; x < W; x++) {
                    int c1 = (int) (ceil[x] * H * 0.3);
                    int f1 = (int) (H * 0.7 + floor[x] * H * 0.2);
                    for (int y = 0; y < c1; y++) {
                        put(img, x, y, t.groundDark);
                    }
                    for (int y = f1; y < H; y++) {
                        put(img, x, y, y < f1 + 2 ? t.ground : t.groundDark);
                    }
                    if (r.nextInt(5) == 0) {
                        int len = 2 + r.nextInt(8);
                        for (int y = c1; y < c1 + len && y < H; y++) {
                            put(img, x, y, t.accent2); // vines / sculk veins
                        }
                    }
                }
                for (int i = 0; i < 40; i++) {
                    int x = r.nextInt(W);
                    int y = r.nextInt(H);
                    put(img, x, y, t.accent);
                    if (r.nextBoolean()) {
                        put(img, x + 1, y, shade(t.accent, -0.3));
                    }
                }
            }
            case NETHER -> {
                for (int y = (int) (H * 0.78); y < H; y++) {
                    for (int x = 0; x < W; x++) {
                        put(img, x, y, (x + y) % 5 == 0 ? 0xFFFFC23A : t.accent); // lava sea
                    }
                }
                double[] rocks = ridge(r, W, 0.6);
                for (int x = 0; x < W; x++) {
                    int top = (int) (H * 0.35 + rocks[x] * H * 0.4);
                    for (int y = top; y < H * 0.85 && y < H; y++) {
                        put(img, x, y, y < top + 2 ? t.ground : t.groundDark);
                    }
                    int hang = (int) ((1 - rocks[(x * 7) % W]) * H * 0.25);
                    for (int y = 0; y < hang; y++) {
                        put(img, x, y, t.groundDark);
                    }
                }
                for (int i = 0; i < 25; i++) {
                    put(img, r.nextInt(W), r.nextInt((int) (H * 0.7)), t.accent2);
                }
            }
            case OCEAN -> {
                for (int i = 0; i < 18; i++) {
                    int x = r.nextInt(W);
                    for (int y = 0; y < H; y += 3 + r.nextInt(4)) {
                        put(img, x, y, shade(t.skyBottom, 0.15)); // light rays
                    }
                }
                // ocean monument
                int mx = W / 2 - 22;
                int base = (int) (H * 0.62);
                for (int level = 0; level < 4; level++) {
                    for (int x = mx + level * 5; x < mx + 44 - level * 5; x++) {
                        for (int y = base - level * 4; y < base - level * 4 + 4; y++) {
                            put(img, x, y, (x + y) % 4 == 0 ? t.accent : t.ground);
                        }
                    }
                }
                for (int x = 0; x < W; x++) {
                    int top = (int) (H * 0.8 + Math.sin(x * 0.2) * 2);
                    for (int y = top; y < H; y++) {
                        put(img, x, y, t.groundDark);
                    }
                }
                for (int i = 0; i < 12; i++) {
                    put(img, r.nextInt(W), (int) (H * 0.4) + r.nextInt((int) (H * 0.5)), t.accent);
                }
            }
        }
        return img;
    }

    private static void tree(BufferedImage img, Random r, int x, int ground, int trunk, int leaves, int light) {
        int h = 5 + r.nextInt(5);
        for (int y = ground - h; y < ground; y++) {
            put(img, x, y, trunk);
        }
        int cy = ground - h;
        int rad = 3 + r.nextInt(2);
        for (int dx = -rad; dx <= rad; dx++) {
            for (int dy = -rad; dy <= 1; dy++) {
                if (dx * dx + dy * dy <= rad * rad + 1) {
                    put(img, x + dx, cy + dy, (dx + dy) % 3 == 0 ? light : leaves);
                }
            }
        }
    }

    private static void put(BufferedImage img, int x, int y, int c) {
        if (x >= 0 && y >= 0 && x < img.getWidth() && y < img.getHeight()) {
            img.setRGB(x, y, c);
        }
    }
}
