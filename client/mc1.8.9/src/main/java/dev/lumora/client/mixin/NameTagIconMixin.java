package dev.lumora.client.mixin;

import dev.lumora.client.render.NameTagIcons;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.BufferBuilder;
import dev.lumora.core.extras.NameTagsModule;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Addon icons next to player name tags (Lumora API 1.1 NameTags, plain-text fallback on this version): remembered while
 * the player's label is drawn and drawn right of the label's box, without a background.
 */
@Mixin(EntityRenderer.class)
public abstract class NameTagIconMixin {
    @Inject(method = "renderLabelIfPresent", at = @At("HEAD"))
    private void lumora$icon(Entity entity, String label, double x, double y, double z, int range, CallbackInfo ci) {
        // the score line under the name goes through here as well; it does not contain the player's name
        if (entity instanceof PlayerEntity player && label != null && label.contains(player.getGameProfile().getName())) {
            NameTagIcons.begin(player.getUuid());
        } else {
            NameTagIcons.end();
        }
    }

    @Inject(method = "renderLabelIfPresent", at = @At("RETURN"))
    private void lumora$iconDone(Entity entity, String label, double x, double y, double z, int range, CallbackInfo ci) {
        NameTagIcons.end();
    }

    // 1.8.9 draws the label itself: see-through first, then normal
    @Redirect(method = "renderLabelIfPresent", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/font/TextRenderer;draw(Ljava/lang/String;III)I"))
    private int lumora$drawBeside(TextRenderer font, String text, int x, int y, int color) {
        int result = lumora$text(font, text, x, y, color);
        String icon = NameTagIcons.current();
        if (icon != null) {
            // the label starts at -width/2, so its end is at -x
            font.draw(icon, -x + (int) NameTagIcons.GAP, y, color);
        }
        // the core's icon (Lumora Badge) left of the label, which starts at x
        String lead = NameTagIcons.leading();
        if (lead != null) {
            font.draw(lead, x - (int) NameTagIcons.GAP - NameTagIcons.width(font, lead), y, color);
        }
        // lines above and below the name
        String above = NameTagIcons.above();
        if (above != null) {
            lumora$text(font, above, -NameTagIcons.width(font, above) / 2, y - (int) NameTagIcons.LINE, color);
        }
        String below = NameTagIcons.below();
        if (below != null) {
            lumora$text(font, below, -NameTagIcons.width(font, below) / 2, y + (int) NameTagIcons.LINE, color);
        }
        return result;
    }

    /** Module "Name Tags": the normal (not see-through) text part with a shadow when chosen. */
    @org.spongepowered.asm.mixin.Unique
    private static int lumora$text(TextRenderer font, String text, int x, int y, int color) {
        boolean shadow = NameTagsModule.shadow(false, (color >>> 24) != 0xFF);
        return shadow ? font.drawWithShadow(text, x, y, color) : font.draw(text, x, y, color);
    }

    /** Module "Name Tags": the color of the background box (Minecraft draws it as four colored corners). */
    @Redirect(method = "renderLabelIfPresent", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/BufferBuilder;color(FFFF)Lnet/minecraft/client/render/BufferBuilder;"))
    private BufferBuilder lumora$background(BufferBuilder buffer, float r, float g, float b, float a) {
        if (NameTagsModule.active() == null) {
            return buffer.color(r, g, b, a);
        }
        int c = NameTagsModule.backgroundArgb(0);
        return buffer.color(((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f, (c >>> 24) / 255f);
    }
}
