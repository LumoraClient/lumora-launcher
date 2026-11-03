package dev.lumora.client.mixin;

import dev.lumora.client.api.McWorldLoad;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetDefaultSpawnPositionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lumora's world loading screen: Minecraft tells its terrain screen about the spawn position only while that screen
 * is shown; with Lumora's screen in front the message is passed on to it (client thread, after Minecraft's handling).
 */
@Mixin(ClientPacketListener.class)
public abstract class SpawnPositionMixin {
    @Inject(method = "handleSetSpawn", at = @At("TAIL"))
    private void lumora$spawn(ClientboundSetDefaultSpawnPositionPacket packet, CallbackInfo ci) {
        McWorldLoad.loadingPacketsReceived();
    }
}
