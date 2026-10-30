package dev.lumora.client.api;

import dev.lumora.core.net.ChannelRegistry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Addon plugin channels on Minecraft 1.21.11: one raw-bytes payload type per channel, registered for both directions;
 * received payloads go to the core on the client thread.
 */
final class McChannels implements ChannelRegistry.Platform {
    /** The whole payload as bytes. */
    record Raw(Type<Raw> type, byte[] data) implements CustomPacketPayload {
    }

    private final Map<String, CustomPacketPayload.Type<Raw>> types = new ConcurrentHashMap<>();

    private static StreamCodec<FriendlyByteBuf, Raw> codec(CustomPacketPayload.Type<Raw> type) {
        return StreamCodec.of((buf, payload) -> buf.writeBytes(payload.data()), buf -> {
            byte[] data = new byte[buf.readableBytes()];
            buf.readBytes(data);
            return new Raw(type, data);
        });
    }

    @Override
    public boolean register(String id) {
        try {
            CustomPacketPayload.Type<Raw> type = new CustomPacketPayload.Type<>(Identifier.parse(id));
            var codec = codec(type);
            PayloadTypeRegistry.playC2S().register(type, codec);
            PayloadTypeRegistry.playS2C().register(type, codec);
            ClientPlayNetworking.registerGlobalReceiver(type, (payload, context) -> {
                var core = LumoraRuntime.core();
                if (core != null) {
                    core.channelRegistry().deliver(id, payload.data());
                }
            });
            types.put(id, type);
            return true;
        } catch (RuntimeException e) {
            // e.g. another mod owns the channel type
            LumoraRuntime.core().logger().warn("Could not register plugin channel " + id, e);
            return false;
        }
    }

    @Override
    public boolean canSend(String id) {
        var type = types.get(id);
        try {
            return type != null && ClientPlayNetworking.canSend(type);
        } catch (IllegalStateException notInGame) {
            return false;
        }
    }

    @Override
    public void send(String id, byte[] payload) {
        var type = types.get(id);
        if (type != null && canSend(id)) {
            ClientPlayNetworking.send(new Raw(type, payload));
        }
    }
}
