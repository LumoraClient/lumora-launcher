package dev.lumora.client.mixin.perf;

import dev.lumora.client.perf.Culling;
import dev.lumora.core.perf.Cullable;
import dev.lumora.core.perf.OcclusionCuller;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Entity culling: an entity that passed Minecraft's own check ({@code shouldRender}: distance and view frustum) is
 * still skipped when the occlusion culler found it completely hidden behind opaque blocks. Uses the same box as
 * Minecraft's frustum check (culling box inflated by 0.5). Entities whose renderer opts out of culling, whose name tag
 * would show (name tags are visible through walls) or that {@link Culling#neverCull} names are always drawn.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityCullingMixin<T extends Entity> {
    @Shadow
    protected abstract AABB getBoundingBoxForCulling(T entity);

    @Shadow
    protected abstract boolean affectedByCulling(T entity);

    @Shadow
    protected abstract boolean shouldShowName(T entity, double distanceToCameraSq);

    @Inject(method = "shouldRender", at = @At("RETURN"), cancellable = true)
    private void lumora$occlusion(T entity, Frustum frustum, double camX, double camY, double camZ,
                                  CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) {
            return;
        }
        OcclusionCuller culler = Culling.active();
        if (culler == null || !affectedByCulling(entity)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (Culling.neverCull(entity, mc)) {
            return;
        }
        AABB b = getBoundingBoxForCulling(entity);
        if (culler.hidden((Cullable) entity, b.minX - 0.5, b.minY - 0.5, b.minZ - 0.5, b.maxX + 0.5, b.maxY + 0.5, b.maxZ + 0.5)
                && !shouldShowName(entity, entity.distanceToSqr(camX, camY, camZ))) {
            cir.setReturnValue(false);
        }
    }
}
