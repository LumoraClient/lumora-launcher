package dev.lumora.client.mixin;

import dev.lumora.core.extras.NameTagsModule;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Module "Name Tags": text shadow and background box of every name tag part (the see-through part never gets a
 * shadow, parts without a box keep none). Without the module the values stay as Minecraft set them.
 */
@Mixin(NameTagFeatureRenderer.class)
public abstract class NameTagStyleMixin {
    /** Whether the part being prepared is drawn through walls (render thread). */
    @Unique
    private static boolean lumora$seeThrough;

    @Inject(method = "prepareText", at = @At("HEAD"))
    private static void lumora$part(Font font, NameTagFeatureRenderer.Submit submit, CallbackInfoReturnable<Font.PreparedText> cir) {
        lumora$seeThrough = submit.displayMode() == Font.DisplayMode.SEE_THROUGH;
    }

    @Redirect(method = "prepareText", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Font;prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZZI)Lnet/minecraft/client/gui/Font$PreparedText;"))
    private static Font.PreparedText lumora$style(Font font, FormattedCharSequence text, float x, float y, int color, boolean shadow,
                                                  boolean includeEmpty, int background) {
        return font.prepareText(text, x, y, color, NameTagsModule.shadow(shadow, lumora$seeThrough), includeEmpty,
                NameTagsModule.background(background));
    }
}
