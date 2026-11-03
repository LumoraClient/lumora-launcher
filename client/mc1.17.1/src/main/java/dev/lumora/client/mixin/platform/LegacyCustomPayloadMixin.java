package dev.lumora.client.mixin.platform;

import dev.lumora.client.platform.Channels;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Payloads of registered plugin channels go to Lumora (see {@link Channels}); everything else to vanilla. */
@Mixin(ClientPacketListener.class)
public abstract class LegacyCustomPayloadMixin {
    @Inject(method = "handleCustomPayload", at = @At("HEAD"), cancellable = true)
    private void lumora$payload(ClientboundCustomPayloadPacket packet, CallbackInfo ci) {
        if (Channels.receive(packet.getIdentifier(), packet.getData())) {
            ci.cancel();
        }
    }
}
