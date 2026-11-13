package dev.lumora.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.lumora.client.render.NameTagIcons;
import dev.lumora.client.render.PlayerIdHolder;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The optional line under a player's name tag (Lumora API 1.1 NameTags subtitle, e.g. a voice channel). Players are
 * drawn by {@link AvatarRenderer}, which overrides {@code submitNameDisplay} without calling the base version.
 */
@Mixin(AvatarRenderer.class)
public abstract class AvatarSubtitleMixin {
    @Inject(method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At("TAIL"))
    private void lumora$subtitle(AvatarRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
        var id = ((PlayerIdHolder) state).lumora$playerId();
        if (id == null || state.nameTagAttachment == null || state.distanceToCameraSq > 4096 || state.isInvisible || state.isDiscrete) {
            return;
        }
        String line = NameTagIcons.subtitle(id);
        if (!line.isEmpty()) {
            collector.submitNameTag(pose, state.nameTagAttachment.add(0, NameTagIcons.hasBelow(id) ? -.49 : -.24, 0), 0, Component.literal(line).withStyle(ChatFormatting.GRAY),
                    true, state.lightCoords, state.distanceToCameraSq, camera);
        }
    }
}
