package dev.lumora.core.extras;

import dev.lumora.api.addon.AddonContext;
import dev.lumora.api.addon.LumoraAddon;
import dev.lumora.api.config.BooleanSetting;
import dev.lumora.api.config.ChoiceSetting;
import dev.lumora.api.config.ColorSetting;
import dev.lumora.api.config.IntegerSetting;
import dev.lumora.api.config.SettingsGroup;
import dev.lumora.api.hud.RenderContext;
import dev.lumora.api.hud.WidgetCategory;
import dev.lumora.api.hud.WidgetPlacement;
import dev.lumora.core.Platform;

import java.util.List;
import java.util.function.LongSupplier;

/**
 * Built-in module "Block-Overlay" after FDPClient's BlockOverlay (GPL-3.0) and BThack's BlockHighlight (MIT), own
 * implementation: a filled box and / or outline around the targeted block (its real shape, slabs and stairs included),
 * separate fill and outline colours, rainbow, line width, only the face looked at, a slide between blocks (linear, quad
 * or expo, like FDPClient), a fade-in when a block is targeted (like BThack), optionally only in first person, and a
 * "Blockinfo" widget with name and id. The version adapters pass the block shape to {@link #frame} where Minecraft
 * draws the block outline and draw the returned box instead of the vanilla outline.
 */
public final class BlockOverlayModule implements LumoraAddon {
    private static volatile BlockOverlayModule active;

    private final Platform platform;
    private final LongSupplier nanos;
    ColorSetting color;
    BooleanSetting rainbow;
    IntegerSetting rainbowSpeed;
    IntegerSetting thickness;
    BooleanSetting fade;
    IntegerSetting fadeMillis;
    BooleanSetting firstPersonOnly;
    ChoiceSetting mode;
    ColorSetting fillColor;
    BooleanSetting sideOnly;
    BooleanSetting slide;
    ChoiceSetting slideEasing;
    IntegerSetting slideMillis;
    private final double[] shown = new double[6];
    private final double[] slideFrom = new double[6];
    private final double[] lastBox = new double[6];
    private long lastFrame = Long.MIN_VALUE;
    private long slideStart;
    private long boxSince;
    private long lastTarget = Long.MIN_VALUE;
    private long targetSince;

    public BlockOverlayModule(Platform platform) {
        this(platform, System::nanoTime);
    }

    BlockOverlayModule(Platform platform, LongSupplier nanos) {
        this.platform = platform;
        this.nanos = nanos;
    }

    public static BlockOverlayModule active() {
        return active;
    }

    @Override
    public void onInitialize(AddonContext context) {
        SettingsGroup s = context.configuration().settings();
        String k = "lumora.extras.blockoverlay.";
        mode = s.selection("mode", "box", "box", "outline", "fill").name(k + "mode").section(k + "section.look");
        thickness = s.slider("thickness", 3, 1, 8).unit(" px").name(k + "thickness").visibleWhen(() -> !"fill".equals(mode.get()));
        color = s.color("color", 0xE6A27BFA, true).name(k + "color").visibleWhen(() -> !"fill".equals(mode.get()))
                .section(k + "section.colors");
        fillColor = s.color("fill_color", 0x508B5CF6, true).name(k + "fill_color").visibleWhen(() -> !"outline".equals(mode.get()));
        rainbow = s.toggle("rainbow", false).name(k + "rainbow");
        rainbowSpeed = s.slider("rainbow_speed", 4, 1, 10).name(k + "rainbow_speed").requires(rainbow);
        fade = s.toggle("fade", true).name(k + "fade").section(k + "section.animation");
        fadeMillis = s.slider("fade_millis", 180, 50, 1000).step(10).unit(" ms").name(k + "fade_millis").requires(fade).advanced();
        slide = s.toggle("slide", true).name(k + "slide");
        slideEasing = s.dropdown("slide_easing", "quad", "linear", "quad", "expo").name(k + "slide_easing").requires(slide).advanced();
        slideMillis = s.slider("slide_millis", 150, 20, 1000).step(10).unit(" ms").name(k + "slide_millis").requires(slide).advanced();
        sideOnly = s.checkbox("side_only", false).name(k + "side_only").section(k + "section.behaviour");
        firstPersonOnly = s.checkbox("first_person_only", false).name(k + "first_person_only");
    }

    @Override
    public void onEnable(AddonContext context) {
        lastTarget = Long.MIN_VALUE;
        lastFrame = Long.MIN_VALUE;
        context.widgets().register(new BlockInfo());
        active = this;
    }

    @Override
    public void onDisable(AddonContext context) {
        active = null;
    }

    /**
     * Outline colour (ARGB) for this frame.
     *
     * @param firstPerson the camera is in first person
     */
    public int outlineColor(boolean firstPerson) {
        if (firstPersonOnly.get() && !firstPerson) {
            return 0;
        }
        long now = nanos.getAsLong();
        long target = platform.extras().targetBlockPos();
        if (target != lastTarget) {
            lastTarget = target;
            targetSince = now;
        }
        int c = color.argb();
        if (rainbow.get()) {
            // one turn of the colour wheel every 12..1.2 seconds
            double turn = (now / 1e9) * rainbowSpeed.get() / 12.0;
            c = (c & 0xFF000000) | (hsbToRgb((float) (turn % 1.0), 0.65f, 1f) & 0xFFFFFF);
        }
        float alpha = ((c >>> 24) & 0xFF) / 255f;
        if (fade.get()) {
            alpha *= fadeFactor(now - targetSince, fadeMillis.get());
        }
        return (Math.round(alpha * 255) << 24) | (c & 0xFFFFFF);
    }

    /**
     * What to draw this frame, in world coordinates: the box and its fill and outline colour (ARGB, 0 = do not draw
     * that part).
     */
    public record Frame(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, int fill, int outline) {
    }

    /** Pushes the box outward so it does not flicker with the block faces (like vanilla and FDPClient). */
    static final double GROW = 0.002;
    /** A pause longer than this (no block targeted) starts the next box without sliding, faded in again. */
    static final long GAP_NANOS = 120_000_000L;

    /**
     * The box for the targeted block this frame, or null to draw nothing. The version adapters call this where
     * Minecraft draws the block outline (every frame while a block is targeted) and draw the result instead.
     *
     * @param side the face looked at as {@code Direction} ordinal (0 down, 1 up, 2 north, 3 south, 4 west, 5 east), -1
     *             if unknown
     */
    public Frame frame(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, int side, boolean firstPerson) {
        if (firstPersonOnly.get() && !firstPerson) {
            return null;
        }
        double[] box = {minX - GROW, minY - GROW, minZ - GROW, maxX + GROW, maxY + GROW, maxZ + GROW};
        if (sideOnly.get() && side >= 0 && side <= 5) {
            flatten(box, side);
        }
        long now = nanos.getAsLong();
        boolean gap = lastFrame == Long.MIN_VALUE || now - lastFrame > GAP_NANOS;
        lastFrame = now;
        if (gap) {
            System.arraycopy(box, 0, shown, 0, 6);
            System.arraycopy(box, 0, lastBox, 0, 6);
            slideStart = Long.MIN_VALUE;
            boxSince = now;
        } else if (!java.util.Arrays.equals(box, lastBox)) {
            System.arraycopy(box, 0, lastBox, 0, 6);
            if (slide.get()) {
                System.arraycopy(shown, 0, slideFrom, 0, 6); // from where the box is now, also mid-slide
                slideStart = now;
            } else {
                boxSince = now; // without sliding every new block fades in
            }
        }
        if (slide.get() && slideStart != Long.MIN_VALUE) {
            float t = Math.min(1f, (now - slideStart) / 1e6f / Math.max(1, slideMillis.get()));
            double e = ease(slideEasing.get(), t);
            for (int i = 0; i < 6; i++) {
                shown[i] = slideFrom[i] + (box[i] - slideFrom[i]) * e;
            }
            if (t >= 1f) {
                slideStart = Long.MIN_VALUE;
            }
        } else {
            System.arraycopy(box, 0, shown, 0, 6);
        }
        float alpha = fade.get() ? fadeFactor(now - boxSince, fadeMillis.get()) : 1f;
        String m = mode.get();
        int outline = m.equals("fill") ? 0 : withAlpha(tint(color.argb(), now), alpha);
        int fill = m.equals("outline") ? 0 : withAlpha(tint(fillColor.argb(), now), alpha);
        return new Frame(shown[0], shown[1], shown[2], shown[3], shown[4], shown[5], fill, outline);
    }

    /** Keeps only the face on {@code side} (zero thickness), slightly outside the block like the box. */
    static void flatten(double[] b, int side) {
        switch (side) {
            case 0 -> b[4] = b[1];
            case 1 -> b[1] = b[4];
            case 2 -> b[5] = b[2];
            case 3 -> b[2] = b[5];
            case 4 -> b[3] = b[0];
            default -> b[0] = b[3];
        }
    }

    /** Slide curves of FDPClient: linear, quadratic and exponential in-out. */
    static double ease(String curve, float t) {
        return switch (curve) {
            case "linear" -> t;
            case "expo" -> t <= 0 ? 0 : t >= 1 ? 1 : t < 0.5 ? Math.pow(2, 20 * t - 10) / 2 : (2 - Math.pow(2, -20 * t + 10)) / 2;
            default -> t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
        };
    }

    /** Rainbow hue (keeping the colour's alpha) when the rainbow option is on. */
    private int tint(int argb, long now) {
        if (!rainbow.get()) {
            return argb;
        }
        double turn = (now / 1e9) * rainbowSpeed.get() / 12.0;
        return (argb & 0xFF000000) | (hsbToRgb((float) (turn % 1.0), 0.65f, 1f) & 0xFFFFFF);
    }

    private static int withAlpha(int argb, float factor) {
        int a = Math.round(((argb >>> 24) & 0xFF) * factor);
        return a <= 0 ? 0 : (a << 24) | (argb & 0xFFFFFF);
    }

    /** Line width in pixels (only used where the Minecraft version lets the outline width be chosen). */
    public float lineWidth() {
        return thickness.get();
    }

    /** 0..1 over {@code millis}, eased (quadratic out). */
    static float fadeFactor(long elapsedNanos, int millis) {
        if (millis <= 0) {
            return 1f;
        }
        float t = Math.min(1f, Math.max(0f, elapsedNanos / 1e6f / millis));
        return 1f - (1f - t) * (1f - t);
    }

    static int hsbToRgb(float hue, float saturation, float brightness) {
        float h = (hue - (float) Math.floor(hue)) * 6f;
        float f = h - (float) Math.floor(h);
        float p = brightness * (1f - saturation);
        float q = brightness * (1f - saturation * f);
        float t = brightness * (1f - saturation * (1f - f));
        float r;
        float g;
        float b;
        switch ((int) h) {
            case 0 -> { r = brightness; g = t; b = p; }
            case 1 -> { r = q; g = brightness; b = p; }
            case 2 -> { r = p; g = brightness; b = t; }
            case 3 -> { r = p; g = q; b = brightness; }
            case 4 -> { r = t; g = p; b = brightness; }
            default -> { r = brightness; g = p; b = q; }
        }
        return 0xFF000000 | (Math.round(r * 255) << 16) | (Math.round(g * 255) << 8) | Math.round(b * 255);
    }

    /** Name and id of the targeted block, below the crosshair by default. */
    final class BlockInfo extends Panels.Text {
        BlockInfo() {
            super("blockinfo");
        }

        @Override
        boolean inWorld() {
            return platform.game().inWorld();
        }

        @Override
        public WidgetPlacement defaultPlacement() {
            return WidgetPlacement.at(0.5f, 0.56f).enabledByDefault();
        }

        @Override
        public String category() {
            return WidgetCategory.WORLD;
        }

        @Override
        List<String[]> lines(boolean preview) {
            GameExtras x = platform.extras();
            String name = preview ? platform.translations().translateOr("lumora.hud.blockinfo.sample", "Eichenstamm") : x.targetBlockName();
            String id = preview ? "minecraft:oak_log" : x.targetBlockId();
            if (name.isEmpty()) {
                return List.of();
            }
            return List.of(new String[]{"", name}, new String[]{"ID", id});
        }

        @Override
        public boolean isVisible(RenderContext ctx) {
            return ctx.editing() || ctx.preview() || !platform.extras().targetBlockName().isEmpty();
        }
    }
}
