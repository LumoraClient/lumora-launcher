package dev.lumora.client.mixin.platform;

import dev.lumora.client.platform.Channels;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Registered plugin channels keep their bytes instead of being discarded (see {@link Channels}). */
@Mixin(ClientboundCustomPayloadPacket.class)
public abstract class CustomPayloadReadMixin {
    @Inject(method = "readPayload", at = @At("HEAD"), cancellable = true)
    private static void lumora$read(ResourceLocation id, FriendlyByteBuf buf, CallbackInfoReturnable<CustomPacketPayload> cir) {
        Channels.Raw raw = Channels.read(id, buf);
        if (raw != null) {
            cir.setReturnValue(raw);
        }
    }
}
