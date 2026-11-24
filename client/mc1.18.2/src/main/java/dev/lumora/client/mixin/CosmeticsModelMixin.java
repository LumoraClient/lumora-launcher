package dev.lumora.client.mixin;

import dev.lumora.client.render.CosmeticsRender;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lumora "Cosmetics & Emotes": the emote moves the model parts after Minecraft's own animation. */
@Mixin(PlayerModel.class)
public abstract class CosmeticsModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void lumora$emotePose(LivingEntity entity, float limbSwing, float limbAmount, float age, float headYaw, float headPitch,
                                  CallbackInfo ci) {
        CosmeticsRender.pose((PlayerModel<?>) (Object) this, entity);
    }
}
