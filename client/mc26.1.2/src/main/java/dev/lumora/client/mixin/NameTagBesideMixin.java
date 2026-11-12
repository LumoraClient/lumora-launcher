package dev.lumora.client.mixin;

import dev.lumora.client.render.NameTagIcons;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/**
 * Addon icons beside the name tag instead of inside it: next to every part of the name tag (see-through and normal)
 * that is stored while an icon is pending ({@link NameTagIcons#current()}), the icon is stored as its own text right
 * of the box, without a background.
 */
@Mixin(NameTagFeatureRenderer.Storage.class)
public abstract class NameTagBesideMixin {
    @Redirect(method = "add", at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z"))
    private boolean lumora$addBeside(List<Object> list, Object submit) {
        boolean added = list.add(submit);
        Component icon = NameTagIcons.current();
        if (icon != null && submit instanceof SubmitNodeStorage.NameTagSubmit name) {
            // the name starts at -width/2, so its end is at -x
            list.add(new SubmitNodeStorage.NameTagSubmit(name.pose(), -name.x() + NameTagIcons.GAP, name.y(), icon, name.lightCoords(),
                    name.color(), 0, name.distanceToCameraSq()));
        }
        return added;
    }
}
