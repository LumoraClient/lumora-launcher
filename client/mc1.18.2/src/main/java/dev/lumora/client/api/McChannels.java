package dev.lumora.client.api;

import dev.lumora.core.net.ChannelRegistry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Addon plugin channels on Minecraft 1.18.2 (Fabric channel API with raw buffers); received payloads go to the
 * core on the client thread.
 */
final class McChannels implements ChannelRegistry.Platform {
    private final Map<String, ResourceLocation> ids = new ConcurrentHashMap<>();

    @Override
    public boolean register(String id) {
        try {
            ResourceLocation channel = new ResourceLocation(id);
            ClientPlayNetworking.registerGlobalReceiver(channel, (client, handler, buf, sender) -> {
                byte[] data = new byte[buf.readableBytes()];
                buf.readBytes(data);
                client.execute(() -> {
                    var core = LumoraRuntime.core();
                    if (core != null) {
                        core.channelRegistry().deliver(id, data);
                    }
                });
            });
            ids.put(id, channel);
            return true;
        } catch (RuntimeException e) {
            LumoraRuntime.core().logger().warn("Could not register plugin channel " + id, e);
            return false;
        }
    }

    @Override
    public boolean canSend(String id) {
        var channel = ids.get(id);
        try {
            return channel != null && ClientPlayNetworking.canSend(channel);
        } catch (IllegalStateException notInGame) {
            return false;
        }
    }

    @Override
    public void send(String id, byte[] payload) {
        var channel = ids.get(id);
        if (channel != null && canSend(id)) {
            FriendlyByteBuf buf = PacketByteBufs.create();
            buf.writeBytes(payload);
            ClientPlayNetworking.send(channel, buf);
        }
    }
}
