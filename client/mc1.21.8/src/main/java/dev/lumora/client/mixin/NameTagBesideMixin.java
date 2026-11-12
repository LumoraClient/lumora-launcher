package dev.lumora.client.mixin;

import dev.lumora.client.render.NameTagIcons;
import org.joml.Matrix4f;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Addon icons beside the name tag instead of inside it: the name is drawn as usual (box sized to the name), then the
 * icon remembered by {@link PlayerNameTagMixin} right of the box without a background, in both passes (see-through and
 * normal).
 */
@Mixin(EntityRenderer.class)
public abstract class NameTagBesideMixin {
    @Redirect(method = "renderNameTag", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Font;drawInBatch(Lnet/minecraft/network/chat/Component;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)V"))
    private void lumora$drawBeside(Font font, Component text, float x, float y, int color, boolean shadow, Matrix4f pose, MultiBufferSource buffers, Font.DisplayMode mode, int background, int light) {
        font.drawInBatch(text, x, y, color, shadow, pose, buffers, mode, background, light);
        Component icon = NameTagIcons.current();
        if (icon != null) {
            // the name starts at -width/2, so its end is at -x
            font.drawInBatch(icon, -x + NameTagIcons.GAP, y, color, shadow, pose, buffers, mode, 0, light);
        }
    }
}
