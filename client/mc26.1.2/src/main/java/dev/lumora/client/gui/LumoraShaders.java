package dev.lumora.client.gui;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.lumora.client.LumoraClient;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * GLSL effects for the Lumora menus (assets/lumora/shaders/core): animated aurora sky, glass panels with
 * anti-aliased rounded corners and soft glows. Each effect is its own RenderPipeline with the same uniforms
 * as the vanilla GUI pipeline; parameters travel in the vertex color, so no custom uniform buffers are needed.
 *
 * <p>Safety: pipelines are compiled once and checked with {@code isValid()}; on failure everything falls back
 * to the plain {@link LumoraUi} drawing. "menuShaders": "off" disables them ("auto" and "on" enable them).
 */
public final class LumoraShaders {
    private static final Logger LOG = LoggerFactory.getLogger("Lumora");

    public static final RenderPipeline AURORA = pipeline("menu_aurora");
    public static final RenderPipeline GLASS = pipeline("glass_panel");
    public static final RenderPipeline GLOW = pipeline("glow");

    private static Boolean usable;

    private LumoraShaders() {
    }

    private static RenderPipeline pipeline(String name) {
        return RenderPipeline.builder()
                .withLocation(Identifier.fromNamespaceAndPath("lumora", "pipeline/" + name))
                .withVertexShader(Identifier.fromNamespaceAndPath("lumora", "core/lumora_gui"))
                .withFragmentShader(Identifier.fromNamespaceAndPath("lumora", "core/" + name))
                .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withCull(false)
                .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
                .build();
    }

    /** Render thread only. */
    public static boolean enabled() {
        String mode = LumoraClient.config() == null ? "auto" : LumoraClient.config().menuShaders;
        if ("off".equals(mode)) {
            return false;
        }
        if (usable == null) {
            usable = compileAll();
        }
        return usable;
    }

    private static boolean compileAll() {
        try {
            GpuDevice device = RenderSystem.tryGetDevice();
            if (device == null) {
                return false;
            }
            for (RenderPipeline p : List.of(AURORA, GLASS, GLOW)) {
                if (!device.precompilePipeline(p).isValid()) {
                    LOG.warn("Lumora shader {} could not be compiled - using plain menu rendering", p.getLocation());
                    return false;
                }
            }
            LOG.info("Lumora menu shaders active (backend {})", device.getBackendName());
            return true;
        } catch (RuntimeException | LinkageError e) {
            LOG.warn("Lumora shaders unavailable - using plain menu rendering", e);
            return false;
        }
    }

    /** Animated aurora + stars over the whole screen, followed by a vignette. */
    public static void sky(GuiGraphicsExtractor g, int w, int h, float strength) {
        if (!enabled()) {
            return;
        }
        int centis = (int) ((System.nanoTime() / 10_000_000L) & 0xFFFF);
        int a = Math.clamp(Math.round(strength * 255), 1, 255);
        int time = ((centis >> 8) & 0xFF) << 16 | (centis & 0xFF) << 8;
        g.fill(AURORA, 0, 0, w, h, a << 24 | time);
        g.fill(AURORA, 0, 0, w, h, a << 24 | time | 0xFF);
    }

    /** Glass panel; falls back to a flat card. */
    public static void glass(GuiGraphicsExtractor g, int x, int y, int w, int h, int argb) {
        if (enabled()) {
            g.fill(GLASS, x, y, x + w, y + h, argb);
        } else {
            LumoraUi.card(g, x, y, w, h, argb, LumoraUi.BORDER);
        }
    }

    /** Soft glow of the given color around a rectangle (no-op without shaders). */
    public static void glow(GuiGraphicsExtractor g, int x, int y, int w, int h, int rgb, int margin) {
        if (!enabled() || margin <= 0) {
            return;
        }
        int total = h + 2 * margin;
        int a = Math.clamp(Math.round(255f * margin / total), 1, 254);
        g.fill(GLOW, x - margin, y - margin, x + w + margin, y + h + margin, a << 24 | (rgb & 0xFFFFFF));
    }
}
