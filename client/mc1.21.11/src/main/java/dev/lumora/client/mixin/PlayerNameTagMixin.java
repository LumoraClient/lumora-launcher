package dev.lumora.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.lumora.api.render.NameTags;
import dev.lumora.client.render.NameTagIcons;
import dev.lumora.client.render.PlayerIdHolder;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** Addon icons after player names and an optional line under the name tag (Lumora API 1.1 NameTags). */
@Mixin(AvatarRenderer.class)
public abstract class PlayerNameTagMixin {
    // The final name after player/team formatting; the first call draws the separate score line.
    @ModifyArgs(method = "submitNameTag(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitNameTag(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/phys/Vec3;ILnet/minecraft/network/chat/Component;ZIDLnet/minecraft/client/renderer/state/CameraRenderState;)V", ordinal = 1))
    private void lumora$icon(Args args, AvatarRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        var id = ((PlayerIdHolder) state).lumora$playerId();
        if (id != null) {
            args.set(3, NameTagIcons.decorate(args.get(3), id, NameTags.Place.WORLD));
        }
    }

    @Inject(method = "submitNameTag(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V", at = @At("TAIL"))
    private void lumora$subtitle(AvatarRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
        var id = ((PlayerIdHolder) state).lumora$playerId();
        if (id == null || state.nameTagAttachment == null || state.distanceToCameraSq > 4096 || state.isInvisible || state.isDiscrete) {
            return;
        }
        String line = NameTagIcons.subtitle(id);
        if (!line.isEmpty()) {
            collector.submitNameTag(pose, state.nameTagAttachment.add(0, -.24, 0), 0, Component.literal(line).withStyle(ChatFormatting.GRAY),
                    true, state.lightCoords, state.distanceToCameraSq, camera);
        }
    }
}
