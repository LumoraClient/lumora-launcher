package dev.lumora.client.mixin;

import dev.lumora.client.api.McConnection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Remembers which server a connection is for. From 1.17 on Minecraft only stores the server entry after a successful
 * login, so a failed attempt would otherwise not know its address (no "Reconnect").
 */
@Mixin(ConnectScreen.class)
public abstract class ConnectStartMixin {
    @Inject(method = "startConnecting", at = @At("HEAD"))
    private static void lumora$target(Screen parent, Minecraft mc, ServerAddress address, ServerData data, boolean quickPlay,
                                      CallbackInfo ci) {
        String host = address == null ? "" : address.getHost() + (address.getPort() == 25565 ? "" : ":" + address.getPort());
        McConnection.starting(data == null ? "" : data.name, data != null && data.ip != null && !data.ip.isEmpty() ? data.ip : host);
    }
}
