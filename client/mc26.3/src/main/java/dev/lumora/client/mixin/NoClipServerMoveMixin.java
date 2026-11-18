package dev.lumora.client.mixin;

import dev.lumora.client.api.McBuilding;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NoClip (Lumora API 1.12): a position from the server while the player is inside blocks means the server refused the
 * movement; NoClip then switches itself off. Runs on the client thread, after the packet was handed over to it.
 */
@Mixin(ClientPacketListener.class)
public abstract class NoClipServerMoveMixin {
    @Inject(method = "handleMovePlayer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
            shift = At.Shift.AFTER))
    private void lumora$serverMoved(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        McBuilding.serverMoved();
    }
}
