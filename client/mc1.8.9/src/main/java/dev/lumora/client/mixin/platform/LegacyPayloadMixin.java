package dev.lumora.client.mixin.platform;

import dev.lumora.client.platform.Channels;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.CustomPayloadS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Payloads of registered plugin channels go to Lumora (see {@link Channels}); everything else to vanilla. */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class LegacyPayloadMixin {
    @Inject(method = "onCustomPayload", at = @At("HEAD"), cancellable = true)
    private void lumora$payload(CustomPayloadS2CPacket packet, CallbackInfo ci) {
        if (Channels.receive(packet.getChannel(), packet.getPayload())) {
            ci.cancel();
        }
    }
}
