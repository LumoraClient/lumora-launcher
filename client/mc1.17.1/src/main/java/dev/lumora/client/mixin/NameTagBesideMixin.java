package dev.lumora.client.mixin;

import dev.lumora.client.render.NameTagIcons;
import com.mojang.math.Matrix4f;
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
            target = "Lnet/minecraft/client/gui/Font;drawInBatch(Lnet/minecraft/network/chat/Component;FFIZLcom/mojang/math/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;ZII)I"))
    private int lumora$drawBeside(Font font, Component text, float x, float y, int color, boolean shadow, Matrix4f pose, MultiBufferSource buffers, boolean seeThrough, int background, int light) {
        int result = font.drawInBatch(text, x, y, color, shadow, pose, buffers, seeThrough, background, light);
        Component icon = NameTagIcons.current();
        if (icon != null) {
            // the name starts at -width/2, so its end is at -x
            font.drawInBatch(icon, -x + NameTagIcons.GAP, y, color, shadow, pose, buffers, seeThrough, 0, light);
        }
        // lines above and below the name: like the name itself, with its box
        Component above = NameTagIcons.above();
        if (above != null) {
            font.drawInBatch(above, -font.width(above) / 2f, y - NameTagIcons.LINE, color, shadow, pose, buffers, seeThrough, background, light);
        }
        Component below = NameTagIcons.below();
        if (below != null) {
            font.drawInBatch(below, -font.width(below) / 2f, y + NameTagIcons.LINE, color, shadow, pose, buffers, seeThrough, background, light);
        }
        return result;
    }
}
