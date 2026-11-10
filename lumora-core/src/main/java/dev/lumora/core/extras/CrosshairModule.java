package dev.lumora.core.extras;

import dev.lumora.api.addon.AddonContext;
import dev.lumora.api.addon.LumoraAddon;
import dev.lumora.api.config.BooleanSetting;
import dev.lumora.api.config.ChoiceSetting;
import dev.lumora.api.config.ColorSetting;
import dev.lumora.api.config.DecimalSetting;
import dev.lumora.api.config.IntegerSetting;
import dev.lumora.api.config.SettingsGroup;
import dev.lumora.api.config.TextSetting;
import dev.lumora.api.render.HudGraphics;
import dev.lumora.core.hub.SettingsView;

import java.util.ArrayList;
import java.util.List;

/**
 * Built-in module "Fadenkreuz": own crosshair in three modes. "shape": a drawn shape (cross, cross with gap, T, X,
 * circle, dot) with size, gap and thickness; "preset": one of the {@link CrosshairPresets}; "custom": a pixel grid the
 * player paints in the editor. Colour, outline and scale apply to all of them. The editor with preview sits above the
 * settings ({@link CrosshairEditor}). The version adapters ask {@link #active()} in Minecraft's crosshair method; while
 * the module is on they draw this crosshair instead of the vanilla one (where vanilla would show one).
 */
public final class CrosshairModule implements LumoraAddon {
    private static volatile CrosshairModule active;
    static final String[] MODES = {"shape", "preset", "custom"};
    static final String[] STYLES = {"gap", "cross", "t", "x", "circle", "dot"};

    ChoiceSetting mode;
    ChoiceSetting style;
    ChoiceSetting preset;
    TextSetting pixels;
    IntegerSetting size;
    IntegerSetting gap;
    IntegerSetting thickness;
    DecimalSetting scale;
    ColorSetting color;
    BooleanSetting outline;
    ColorSetting outlineColor;
    BooleanSetting dot;

    private String parsedFrom;
    private boolean[] parsed = new boolean[CrosshairPresets.GRID * CrosshairPresets.GRID];

    /** The module while it is switched on, else null. */
    public static CrosshairModule active() {
        return active;
    }

    @Override
    public void onInitialize(AddonContext context) {
        SettingsGroup s = context.configuration().settings();
        mode = s.choice("mode", "shape", MODES).name("lumora.extras.crosshair.mode");
        style = s.choice("style", "gap", STYLES).name("lumora.extras.crosshair.style");
        preset = s.choice("preset", CrosshairPresets.all().get(0).id(), CrosshairPresets.ids())
                .name("lumora.extras.crosshair.preset");
        pixels = s.text("pixels", CrosshairPresets.encode(CrosshairPresets.get("circle_dot").pixels()),
                CrosshairPresets.GRID * 4).name("lumora.extras.crosshair.pixels");
        size = s.slider("size", 5, 1, 20).unit(" px").name("lumora.extras.crosshair.size").section("lumora.extras.crosshair.section.shape");
        gap = s.slider("gap", 2, 0, 10).unit(" px").name("lumora.extras.crosshair.gap");
        thickness = s.slider("thickness", 1, 1, 5).unit(" px").name("lumora.extras.crosshair.thickness");
        scale = s.slider("scale", 1.0, 0.5, 3.0, 0.5).unit("x").name("lumora.extras.crosshair.scale");
        dot = s.checkbox("dot", false).name("lumora.extras.crosshair.dot");
        color = s.chroma("color", 0xFFFFFFFF, true).name("lumora.extras.crosshair.color").section("lumora.extras.crosshair.section.colors");
        outline = s.toggle("outline", true).name("lumora.extras.crosshair.outline");
        outlineColor = s.color("outline_color", 0xB0000000, true).name("lumora.extras.crosshair.outline_color").requires(outline);
        SettingsView.registerPanel(s, german -> new CrosshairEditor(this, german));
    }

    @Override
    public void onEnable(AddonContext context) {
        active = this;
    }

    @Override
    public void onDisable(AddonContext context) {
        active = null;
    }

    /** Draws the crosshair centred on the GUI-scaled screen (without the framebuffer size: on the GUI grid). */
    public void draw(HudGraphics g, int screenWidth, int screenHeight) {
        draw(g, screenWidth, screenHeight, 0, 0);
    }

    /**
     * Draws the crosshair in the real middle of the window: the GUI grid is coarser than the screen (2-4 real pixels
     * per GUI pixel), so the GUI centre can be off by a few pixels; the framebuffer size gives the exact middle.
     *
     * @param framebufferWidth window size in real pixels (0 if unknown)
     */
    public void draw(HudGraphics g, int screenWidth, int screenHeight, int framebufferWidth, int framebufferHeight) {
        double[] c = centre(screenWidth, screenHeight, framebufferWidth, framebufferHeight);
        g.push();
        g.translate((float) c[0], (float) c[1]);
        render(g, 0, 0);
        g.pop();
    }

    /**
     * Top left corner of the crosshair's centre pixel in GUI units: the pixel (one GUI pixel = {@code scale} real
     * pixels) sits in the middle of the framebuffer, its edges on whole real pixels so it stays sharp.
     */
    static double[] centre(int guiWidth, int guiHeight, int framebufferWidth, int framebufferHeight) {
        if (guiWidth <= 0 || guiHeight <= 0 || framebufferWidth <= 0 || framebufferHeight <= 0) {
            return new double[]{guiWidth / 2, guiHeight / 2};
        }
        // Minecraft's GUI size is the window size divided by the whole GUI scale, rounded up
        double scale = Math.max(1, Math.ceil(framebufferWidth / (double) guiWidth - 1e-9));
        return new double[]{Math.round((framebufferWidth - scale) / 2.0) / scale,
                Math.round((framebufferHeight - scale) / 2.0) / scale};
    }

    /** Draws the crosshair with its middle pixel at (cx, cy) of the current transform. */
    void render(HudGraphics g, int cx, int cy) {
        String m = mode.get();
        int oc = outline.get() ? outlineColor.argb() : 0;
        if ("shape".equals(m)) {
            List<int[]> parts = shape(style.get(), size.get(), gap.get(), thickness.get(), dot.get());
            if (oc != 0) {
                for (int[] r : parts) {
                    g.fill(cx + r[0] - 1, cy + r[1] - 1, cx + r[2] + 1, cy + r[3] + 1, oc);
                }
            }
            int c = color.argb();
            for (int[] r : parts) {
                g.fill(cx + r[0], cy + r[1], cx + r[2], cy + r[3], c);
            }
            return;
        }
        float s = (float) scale.get().doubleValue();
        g.push();
        // the middle pixel stays centred on the vanilla centre pixel at every scale
        g.translate(cx + 0.5f - s / 2f, cy + 0.5f - s / 2f);
        g.scale(s);
        bitmap(g, pixels(), color.argb(), oc);
        g.pop();
    }

    /** The pixels drawn in the preset and custom modes. */
    boolean[] pixels() {
        if ("custom".equals(mode.get())) {
            return customPixels();
        }
        return CrosshairPresets.get(preset.get()).pixels();
    }

    /** The painted grid (parsed once per change). */
    boolean[] customPixels() {
        String raw = pixels.get();
        if (!raw.equals(parsedFrom)) {
            boolean[] px = CrosshairPresets.decode(raw);
            parsed = px != null ? px : new boolean[CrosshairPresets.GRID * CrosshairPresets.GRID];
            parsedFrom = raw;
        }
        return parsed;
    }

    void setCustomPixels(boolean[] px) {
        pixels.set(CrosshairPresets.encode(px));
    }

    /**
     * Draws a pixel grid with its middle pixel at (0, 0): runs of pixels per row, the outline once per pixel next to
     * the shape (so a translucent outline stays even).
     */
    static void bitmap(HudGraphics g, boolean[] px, int color, int outlineColor) {
        int n = CrosshairPresets.GRID;
        int half = n / 2;
        if (outlineColor != 0) {
            for (int y = -1; y <= n; y++) {
                int run = Integer.MIN_VALUE;
                for (int x = -1; x <= n + 1; x++) {
                    boolean edge = x <= n && !at(px, x, y) && near(px, x, y);
                    if (edge && run == Integer.MIN_VALUE) {
                        run = x;
                    } else if (!edge && run != Integer.MIN_VALUE) {
                        g.fill(run - half, y - half, x - half, y - half + 1, outlineColor);
                        run = Integer.MIN_VALUE;
                    }
                }
            }
        }
        for (int y = 0; y < n; y++) {
            int run = -1;
            for (int x = 0; x <= n; x++) {
                boolean on = x < n && px[y * n + x];
                if (on && run < 0) {
                    run = x;
                } else if (!on && run >= 0) {
                    g.fill(run - half, y - half, x - half, y - half + 1, color);
                    run = -1;
                }
            }
        }
    }

    private static boolean at(boolean[] px, int x, int y) {
        int n = CrosshairPresets.GRID;
        return x >= 0 && y >= 0 && x < n && y < n && px[y * n + x];
    }

    private static boolean near(boolean[] px, int x, int y) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if ((dx != 0 || dy != 0) && at(px, x + dx, y + dy)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Rectangles {x0, y0, x1, y1} relative to the screen centre (x1/y1 exclusive). The centre pixel is the one right
     * of and below the centre point, like vanilla's 15 px sprite.
     */
    static List<int[]> shape(String style, int size, int gap, int thickness, boolean dot) {
        List<int[]> out = new ArrayList<>();
        int t = Math.max(1, thickness);
        int a = -(t - 1) / 2;   // thick lines grow around the centre pixel
        int b = a + t;
        switch (style) {
            case "cross" -> {
                out.add(new int[]{-size, a, size + 1, b});
                out.add(new int[]{a, -size, b, a});
                out.add(new int[]{a, b, b, size + 1});
            }
            case "t" -> {
                out.add(new int[]{-gap - size, a, -gap, b});
                out.add(new int[]{gap + 1, a, gap + 1 + size, b});
                out.add(new int[]{a, gap + 1, b, gap + 1 + size});
            }
            case "x" -> {
                for (int i = gap; i < gap + size; i++) {
                    int d = i + 1;
                    out.add(new int[]{-d + a, -d + a, -d + b, -d + b});
                    out.add(new int[]{d + a, -d + a, d + b, -d + b});
                    out.add(new int[]{-d + a, d + a, -d + b, d + b});
                    out.add(new int[]{d + a, d + a, d + b, d + b});
                }
            }
            case "circle" -> {
                int r = Math.max(2, size);
                // midpoint circle: one square of the line thickness per point of the octants
                int x = r;
                int y = 0;
                int err = 1 - r;
                while (x >= y) {
                    int[][] pts = {{x, y}, {y, x}, {-y, x}, {-x, y}, {-x, -y}, {-y, -x}, {y, -x}, {x, -y}};
                    for (int[] p : pts) {
                        out.add(new int[]{p[0] + a, p[1] + a, p[0] + b, p[1] + b});
                    }
                    y++;
                    if (err < 0) {
                        err += 2 * y + 1;
                    } else {
                        x--;
                        err += 2 * (y - x) + 1;
                    }
                }
            }
            case "dot" -> out.add(new int[]{a - 1, a - 1, b + 1, b + 1});
            default -> { // "gap": classic cross with an open centre
                out.add(new int[]{-gap - size, a, -gap, b});
                out.add(new int[]{gap + 1, a, gap + 1 + size, b});
                out.add(new int[]{a, -gap - size, b, -gap});
                out.add(new int[]{a, gap + 1, b, gap + 1 + size});
            }
        }
        if (dot && !"dot".equals(style)) {
            out.add(new int[]{a, a, b, b});
        }
        return out;
    }

    /** Half the width of a shape (for fitting it into a box): the largest distance of a part from the centre. */
    static int extent(List<int[]> parts) {
        int e = 1;
        for (int[] r : parts) {
            e = Math.max(e, Math.max(Math.max(-r[0], r[2]), Math.max(-r[1], r[3])));
        }
        return e;
    }
}
