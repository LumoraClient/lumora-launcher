package dev.lumora.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.lumora.api.render.NameTags;
import dev.lumora.client.render.NameTagIcons;
import dev.lumora.client.render.PlayerIdHolder;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Addon icons after player names (Lumora API 1.1 NameTags). {@link PlayerRenderer} draws the score line first (only
 * with a scoreboard objective), then the name, both through {@code super.renderNameTag}; the second call is the name.
 */
@Mixin(PlayerRenderer.class)
public abstract class PlayerNameTagMixin {
    @ModifyArg(method = "renderNameTag(Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;renderNameTag(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", ordinal = 1),
            index = 1)
    private Component lumora$icon(EntityRenderState state, Component name, PoseStack pose, MultiBufferSource buffers, int light) {
        if (state instanceof PlayerIdHolder holder && holder.lumora$playerId() != null) {
            return NameTagIcons.decorate(name, holder.lumora$playerId(), NameTags.Place.WORLD);
        }
        return name;
    }
}
