package dev.lumora.client.mixin;

import dev.lumora.client.render.NameTagIcons;
import net.minecraft.client.font.TextRenderer;
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
        int result = font.draw(text, x, y, color);
        String icon = NameTagIcons.current();
        if (icon != null) {
            // the label starts at -width/2, so its end is at -x
            font.draw(icon, -x + (int) NameTagIcons.GAP, y, color);
        }
        return result;
    }
}
