package dev.lumora.client.mixin.perf;

import dev.lumora.client.perf.Culling;
import dev.lumora.core.perf.Cullable;
import dev.lumora.core.perf.OcclusionCuller;
import net.minecraft.client.render.CameraView;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Entity culling: an entity that passed Minecraft's own check ({@code shouldRender}: distance and view frustum) is
 * still skipped when the occlusion culler found it completely hidden behind opaque blocks. Uses the entity's box
 * inflated by 0.5. Entities whose name tag would show (name tags are visible through walls) or that
 * {@link Culling#neverCull} names are always drawn.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityCullingMixin<T extends Entity> {
    @Shadow
    protected abstract boolean hasLabel(T entity);

    @Inject(method = "shouldRender", at = @At("RETURN"), cancellable = true)
    private void lumora$occlusion(T entity, CameraView camera, double camX, double camY, double camZ, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) {
            return;
        }
        OcclusionCuller culler = Culling.active();
        if (culler == null || Culling.neverCull(entity)) {
            return;
        }
        Box b = entity.getVisibilityBoundingBox();
        if (culler.hidden((Cullable) entity, b.minX - 0.5, b.minY - 0.5, b.minZ - 0.5, b.maxX + 0.5, b.maxY + 0.5, b.maxZ + 0.5)
                && !hasLabel(entity)) {
            cir.setReturnValue(false);
        }
    }
}
