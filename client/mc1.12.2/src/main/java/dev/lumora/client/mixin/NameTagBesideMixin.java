package dev.lumora.client.mixin;

import dev.lumora.client.render.NameTagIcons;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.BufferBuilder;
import dev.lumora.core.extras.NameTagsModule;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Addon icons beside the name tag instead of inside it: 1.12.2 draws labels in a static helper of
 * {@link GameRenderer} (see-through first, then normal); the icon remembered by {@link NameTagIconMixin} is drawn right
 * of the label's box, without a background.
 */
@Mixin(GameRenderer.class)
public abstract class NameTagBesideMixin {
    @Redirect(method = "method_13427", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/font/TextRenderer;draw(Ljava/lang/String;III)I"))
    private static int lumora$drawBeside(TextRenderer font, String text, int x, int y, int color) {
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
    @Redirect(method = "method_13427", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/BufferBuilder;color(FFFF)Lnet/minecraft/client/render/BufferBuilder;"))
    private static BufferBuilder lumora$background(BufferBuilder buffer, float r, float g, float b, float a) {
        if (NameTagsModule.active() == null) {
            return buffer.color(r, g, b, a);
        }
        int c = NameTagsModule.backgroundArgb(0);
        return buffer.color(((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f, (c >>> 24) / 255f);
    }
}
