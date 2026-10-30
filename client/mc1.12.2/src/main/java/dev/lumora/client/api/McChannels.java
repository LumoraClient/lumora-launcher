package dev.lumora.client.api;

import dev.lumora.core.net.ChannelRegistry;
import net.legacyfabric.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.legacyfabric.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.util.PacketByteBuf;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Addon plugin channels on Minecraft 1.12.2 (Legacy Fabric networking API, raw buffers). Channel names are
 * limited to 20 characters on this protocol; longer ones cannot be used here. Received payloads go to the core on
 * the client thread.
 */
final class McChannels implements ChannelRegistry.Platform {
    private static final int MAX_NAME = 20;
    private final Set<String> ids = ConcurrentHashMap.newKeySet();

    @Override
    public boolean register(String id) {
        if (id.length() > MAX_NAME) {
            return false;
        }
        try {
            ClientPlayNetworking.registerGlobalReceiver(id, (client, handler, buf, sender) -> {
                byte[] data = new byte[buf.readableBytes()];
                buf.readBytes(data);
                client.submit(() -> {
                    var core = LumoraRuntime.core();
                    if (core != null) {
                        core.channelRegistry().deliver(id, data);
                    }
                });
            });
            ids.add(id);
            return true;
        } catch (RuntimeException e) {
            LumoraRuntime.core().logger().warn("Could not register plugin channel " + id, e);
            return false;
        }
    }

    @Override
    public boolean canSend(String id) {
        try {
            return ids.contains(id) && ClientPlayNetworking.canSend(id);
        } catch (IllegalStateException | IllegalArgumentException notInGame) {
            return false;
        }
    }

    @Override
    public void send(String id, byte[] payload) {
        if (canSend(id)) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeBytes(payload);
            ClientPlayNetworking.send(id, buf);
        }
    }
}
