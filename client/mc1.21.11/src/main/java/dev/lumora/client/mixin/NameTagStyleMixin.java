package dev.lumora.client.mixin;

import dev.lumora.core.extras.NameTagsModule;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Module "Name Tags": text shadow and background box of every name tag part (the see-through part never gets a
 * shadow, parts without a box keep none). Without the module the values stay as Minecraft set them.
 */
@Mixin(NameTagFeatureRenderer.class)
public abstract class NameTagStyleMixin {
    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Font;drawInBatch(Lnet/minecraft/network/chat/Component;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)V"))
    private void lumora$style(Font font, Component text, float x, float y, int color, boolean shadow, org.joml.Matrix4f pose, MultiBufferSource buffers,
                              Font.DisplayMode mode, int background, int light) {
        font.drawInBatch(text, x, y, color, NameTagsModule.shadow(shadow, mode == Font.DisplayMode.SEE_THROUGH), pose, buffers, mode,
                NameTagsModule.background(background), light);
    }
}
