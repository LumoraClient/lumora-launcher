package dev.lumora.client.mixin;

import dev.lumora.client.render.NameTagIcons;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.feature.TextFeatureRenderer;
import net.minecraft.client.renderer.feature.phase.TranslucentFeatureRenderPhase;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Addon icons beside the name tag instead of inside it: for every part of the name tag (normal and see-through) built
 * while an icon is pending ({@link NameTagIcons#current()}), the icon is submitted as its own text right of the box,
 * without a background, into the same phase as that part.
 */
@Mixin(SubmitNodeCollection.class)
public abstract class NameTagBesideMixin {
    @Shadow
    @Final
    public TranslucentFeatureRenderPhase seeThrough;

    @Shadow
    private static TextFeatureRenderer.Submit nameTag(Matrix4f pose, float x, float y, FormattedCharSequence text, int light, int color,
            int background, Font.DisplayMode mode) {
        throw new AssertionError();
    }

    @Shadow
    private void submitNameTagPart(TextFeatureRenderer.Submit submit) {
        throw new AssertionError();
    }

    @Redirect(method = "submitNameTag", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/SubmitNodeCollection;nameTag(Lorg/joml/Matrix4f;FFLnet/minecraft/util/FormattedCharSequence;IIILnet/minecraft/client/gui/Font$DisplayMode;)Lnet/minecraft/client/renderer/feature/TextFeatureRenderer$Submit;"))
    private TextFeatureRenderer.Submit lumora$nameBeside(Matrix4f pose, float x, float y, FormattedCharSequence text, int light, int color,
            int background, Font.DisplayMode mode) {
        Component icon = NameTagIcons.current();
        if (icon != null) {
            // the name starts at -width/2, so its end is at -x
            var beside = nameTag(pose, -x + NameTagIcons.GAP, y, icon.getVisualOrderText(), light, color, 0, mode);
            if (mode == Font.DisplayMode.SEE_THROUGH) {
                seeThrough.submit(beside);
            } else {
                submitNameTagPart(beside);
            }
        }
        // lines above and below the name (Lumora API 1.7 NameTags.Lines): like the name itself, with its box
        for (int i = 0; i < 2; i++) {
            Component line = i == 0 ? NameTagIcons.above() : NameTagIcons.below();
            if (line == null) {
                continue;
            }
            var font = net.minecraft.client.Minecraft.getInstance().font;
            var part = nameTag(pose, -font.width(line) / 2f, y + (i == 0 ? -NameTagIcons.LINE : NameTagIcons.LINE), line.getVisualOrderText(), light, color,
                    background, mode);
            if (mode == Font.DisplayMode.SEE_THROUGH) {
                seeThrough.submit(part);
            } else {
                submitNameTagPart(part);
            }
        }
        return nameTag(pose, x, y, text, light, color, background, mode);
    }
}
