package dev.lumora.client.mixin;

import dev.lumora.client.render.CosmeticsRender;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lumora "Cosmetics & Emotes": the emote moves the model parts after Minecraft's own animation. */
@Mixin(PlayerEntityModel.class)
public abstract class CosmeticsModelMixin {
    @Inject(method = "setAngles(FFFFFFLnet/minecraft/entity/Entity;)V", at = @At("TAIL"))
    private void lumora$emotePose(float limbAngle, float limbDistance, float age, float headYaw, float headPitch, float scale,
                                  Entity entity, CallbackInfo ci) {
        CosmeticsRender.pose((PlayerEntityModel) (Object) this, entity);
    }
}
