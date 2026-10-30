package dev.lumora.client.mixin;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.lumora.client.gui.LumoraShaders;
import dev.lumora.client.gui.LumoraUi;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Sliders draw track and handle with two blitSprite calls; with Lumora shaders active both become glass
 * (dark track, accent handle with glow). Value, text and input handling remain vanilla.
 */
@Mixin(AbstractSliderButton.class)
public abstract class SliderGlassMixin {
    @Redirect(method = "extractWidgetRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIII)V"))
    private void lumora$glassSlider(GuiGraphicsExtractor g, RenderPipeline pipeline, Identifier sprite, int x, int y, int w, int h, int color) {
        if (!LumoraShaders.enabled()) {
            g.blitSprite(pipeline, sprite, x, y, w, h, color);
            return;
        }
        String path = sprite.getPath();
        boolean highlighted = path.contains("highlighted");
        if (path.contains("handle")) {
            if (highlighted) {
                LumoraShaders.glow(g, x, y, w, h, LumoraUi.ACCENT, 5);
            }
            LumoraShaders.glass(g, x, y, w, h, highlighted ? 0xFFA78BFA : 0xFF8B5CF6);
        } else {
            LumoraShaders.glass(g, x, y, w, h, highlighted ? 0xE8241D31 : 0xDC13101A);
        }
    }
}
