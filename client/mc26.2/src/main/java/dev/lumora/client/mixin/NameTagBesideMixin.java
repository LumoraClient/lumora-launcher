package dev.lumora.client.mixin;

import dev.lumora.client.render.NameTagIcons;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
import net.minecraft.client.renderer.feature.phase.SimpleFeatureRenderPhase;
import net.minecraft.client.renderer.feature.phase.TranslucentFeatureRenderPhase;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import net.minecraft.client.renderer.feature.submit.TranslucentSubmit;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Addon icons beside the name tag instead of inside it: next to every part of the name tag (normal and see-through)
 * submitted while an icon is pending ({@link NameTagIcons#current()}), the icon is submitted as its own text right of
 * the box, without a background.
 */
@Mixin(SubmitNodeCollection.class)
public abstract class NameTagBesideMixin {
    @Redirect(method = "submitNameTag", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/feature/phase/SimpleFeatureRenderPhase;submit(Lnet/minecraft/client/renderer/feature/submit/SubmitNode;)V"))
    private void lumora$normalBeside(SimpleFeatureRenderPhase phase, SubmitNode submit) {
        phase.submit(submit);
        var icon = lumora$beside(submit);
        if (icon != null) {
            phase.submit(icon);
        }
    }

    @Redirect(method = "submitNameTag", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/feature/phase/TranslucentFeatureRenderPhase;submit(Lnet/minecraft/client/renderer/feature/submit/TranslucentSubmit;)V"))
    private void lumora$seeThroughBeside(TranslucentFeatureRenderPhase phase, TranslucentSubmit submit) {
        phase.submit(submit);
        var icon = lumora$beside(submit);
        if (icon != null) {
            phase.submit(icon);
        }
    }

    /** The icon right of the name's box, or {@code null} when no icon is pending. */
    private static NameTagFeatureRenderer.Submit lumora$beside(Object submit) {
        Component icon = NameTagIcons.current();
        if (icon == null || !(submit instanceof NameTagFeatureRenderer.Submit name)) {
            return null;
        }
        // the name starts at -width/2, so its end is at -x
        return new NameTagFeatureRenderer.Submit(name.pose(), -name.x() + NameTagIcons.GAP, name.y(), icon, name.lightCoords(), name.color(), 0,
                name.displayMode());
    }
}
