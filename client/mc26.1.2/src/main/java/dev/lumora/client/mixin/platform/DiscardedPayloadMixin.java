package dev.lumora.client.mixin.platform;

import dev.lumora.client.platform.Channels;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.DiscardedPayload;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Registered plugin channels keep their bytes instead of being discarded (see {@link Channels}). */
@Mixin(DiscardedPayload.class)
public abstract class DiscardedPayloadMixin {
    @Inject(method = "codec", at = @At("HEAD"), cancellable = true)
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void lumora$codec(Identifier id, int maxBytes, CallbackInfoReturnable<StreamCodec> cir) {
        StreamCodec codec = Channels.codec(id, maxBytes);
        if (codec != null) {
            cir.setReturnValue(codec);
        }
    }
}
