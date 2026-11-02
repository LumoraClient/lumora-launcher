package dev.lumora.client.mixin.platform;

import dev.lumora.client.platform.Channels;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Payloads of registered plugin channels go to Lumora (vanilla would ignore them). */
@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class ClientCustomPayloadMixin {
    @Inject(method = "handleCustomPayload(Lnet/minecraft/network/protocol/common/ClientboundCustomPayloadPacket;)V",
            at = @At("HEAD"), cancellable = true)
    private void lumora$payload(ClientboundCustomPayloadPacket packet, CallbackInfo ci) {
        if (Channels.receive(packet.payload())) {
            ci.cancel();
        }
    }
}
