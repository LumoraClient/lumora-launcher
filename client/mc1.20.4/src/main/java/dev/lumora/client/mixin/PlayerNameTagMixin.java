package dev.lumora.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.lumora.api.render.NameTags;
import dev.lumora.client.render.NameTagIcons;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Addon icons after player names (Lumora API 1.1 NameTags). {@link PlayerRenderer} draws the score line first (only
 * with a scoreboard objective), then the name, both through {@code super.renderNameTag}; the second call is the name.
 */
@Mixin(PlayerRenderer.class)
public abstract class PlayerNameTagMixin {
    @ModifyArg(method = "renderNameTag(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;renderNameTag(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", ordinal = 1),
            index = 1)
    private Component lumora$icon(Entity entity, Component name, PoseStack pose, MultiBufferSource buffers, int light) {
        return NameTagIcons.decorate(name, entity.getUUID(), NameTags.Place.WORLD);
    }
}
