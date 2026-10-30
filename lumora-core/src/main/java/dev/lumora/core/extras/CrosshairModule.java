package dev.lumora.core.extras;

import dev.lumora.api.addon.AddonContext;
import dev.lumora.api.addon.LumoraAddon;
import dev.lumora.api.config.BooleanSetting;
import dev.lumora.api.config.ChoiceSetting;
import dev.lumora.api.config.ColorSetting;
import dev.lumora.api.config.IntegerSetting;
import dev.lumora.api.config.SettingsGroup;
import dev.lumora.api.render.HudGraphics;

import java.util.ArrayList;
import java.util.List;

/**
 * Built-in module "Fadenkreuz": own crosshair (cross, cross with gap, T, X, circle, dot) with size, gap, thickness,
 * colour, outline and centre dot. The version adapters ask {@link #active()} in Minecraft's crosshair method; while the
 * module is on they draw this crosshair instead of the vanilla one (where vanilla would show one).
 */
public final class CrosshairModule implements LumoraAddon {
    private static volatile CrosshairModule active;

    ChoiceSetting style;
    IntegerSetting size;
    IntegerSetting gap;
    IntegerSetting thickness;
    ColorSetting color;
    BooleanSetting outline;
    ColorSetting outlineColor;
    BooleanSetting dot;

    /** The module while it is switched on, else null. */
    public static CrosshairModule active() {
        return active;
    }

    @Override
    public void onInitialize(AddonContext context) {
        SettingsGroup s = context.configuration().settings();
        style = (ChoiceSetting) s.choice("style", "gap", "gap", "cross", "t", "x", "circle", "dot").name("lumora.extras.crosshair.style");
        size = (IntegerSetting) s.slider("size", 5, 1, 20).name("lumora.extras.crosshair.size");
        gap = (IntegerSetting) s.slider("gap", 2, 0, 10).name("lumora.extras.crosshair.gap");
        thickness = (IntegerSetting) s.slider("thickness", 1, 1, 5).name("lumora.extras.crosshair.thickness");
        color = (ColorSetting) s.color("color", 0xFFFFFFFF, true).name("lumora.extras.crosshair.color");
        outline = (BooleanSetting) s.toggle("outline", true).name("lumora.extras.crosshair.outline");
        outlineColor = (ColorSetting) s.color("outline_color", 0xB0000000, true).name("lumora.extras.crosshair.outline_color").advanced();
        dot = (BooleanSetting) s.toggle("dot", false).name("lumora.extras.crosshair.dot");
    }

    @Override
    public void onEnable(AddonContext context) {
        active = this;
    }

    @Override
    public void onDisable(AddonContext context) {
        active = null;
    }

    /** Draws the crosshair centred on the GUI-scaled screen. */
    public void draw(HudGraphics g, int screenWidth, int screenHeight) {
        int cx = screenWidth / 2;
        int cy = screenHeight / 2;
        List<int[]> parts = shape(style.get(), size.get(), gap.get(), thickness.get(), dot.get());
        if (outline.get()) {
            int oc = outlineColor.argb();
            for (int[] r : parts) {
                g.fill(cx + r[0] - 1, cy + r[1] - 1, cx + r[2] + 1, cy + r[3] + 1, oc);
            }
        }
        int c = color.argb();
        for (int[] r : parts) {
            g.fill(cx + r[0], cy + r[1], cx + r[2], cy + r[3], c);
        }
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
}
