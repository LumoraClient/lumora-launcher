package dev.lumora.client.platform;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Plugin channels (custom payloads) without a mod loader. Minecraft decodes payloads of channels it does not know as
 * {@code DiscardedPayload} and drops their bytes; for registered channels {@code DiscardedPayloadMixin} hands out
 * {@link #codec} instead, which keeps the bytes in a {@link Raw} payload. {@code ClientCustomPayloadMixin} delivers
 * those on the client thread. Registered channels are announced to the server with {@code minecraft:register}
 * when a world is joined (servers such as Paper only send to channels a client registered).
 */
public final class Channels {
    public static final ResourceLocation REGISTER = ResourceLocation.fromNamespaceAndPath("minecraft", "register");

    /** The whole payload as bytes. */
    public record Raw(Type<Raw> type, byte[] data) implements CustomPacketPayload {
    }

    private static final Map<ResourceLocation, CustomPacketPayload.Type<Raw>> TYPES = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, BiConsumer<ResourceLocation, byte[]>> RECEIVERS = new ConcurrentHashMap<>();

    private Channels() {
    }

    public static boolean register(ResourceLocation id, BiConsumer<ResourceLocation, byte[]> receiver) {
        if (TYPES.containsKey(id)) {
            return false;
        }
        TYPES.put(id, new CustomPacketPayload.Type<>(id));
        RECEIVERS.put(id, receiver);
        return true;
    }

    public static boolean registered(ResourceLocation id) {
        return TYPES.containsKey(id);
    }

    /** Codec for a registered channel or {@code minecraft:register}; null lets Minecraft discard the payload. */
    public static StreamCodec<ByteBuf, Raw> codec(ResourceLocation id, int maxBytes) {
        CustomPacketPayload.Type<Raw> type = TYPES.get(id);
        if (type == null && REGISTER.equals(id)) {
            type = new CustomPacketPayload.Type<>(REGISTER);
        }
        if (type == null) {
            return null;
        }
        CustomPacketPayload.Type<Raw> t = type;
        return StreamCodec.of((buf, payload) -> buf.writeBytes(payload.data()), buf -> {
            int n = buf.readableBytes();
            if (n > maxBytes) {
                throw new IllegalArgumentException("Payload may not be larger than " + maxBytes + " bytes");
            }
            byte[] data = new byte[n];
            buf.readBytes(data);
            return new Raw(t, data);
        });
    }

    /** Network thread: hands a received payload to its receiver on the client thread. */
    public static boolean receive(CustomPacketPayload payload) {
        if (!(payload instanceof Raw raw)) {
            return false;
        }
        BiConsumer<ResourceLocation, byte[]> r = RECEIVERS.get(raw.type().id());
        if (r != null) {
            Minecraft.getInstance().execute(() -> r.accept(raw.type().id(), raw.data()));
        }
        return true;
    }

    public static boolean canSend(ResourceLocation id) {
        return TYPES.containsKey(id) && Minecraft.getInstance().getConnection() != null;
    }

    public static boolean send(ResourceLocation id, byte[] data) {
        CustomPacketPayload.Type<Raw> type = TYPES.get(id);
        var connection = Minecraft.getInstance().getConnection();
        if (type == null || connection == null) {
            return false;
        }
        connection.send(new ServerboundCustomPayloadPacket(new Raw(type, data)));
        return true;
    }

    /** Tells the server which channels this client listens on (NUL separated ids, as Bukkit/Paper expect). */
    public static void announce() {
        var connection = Minecraft.getInstance().getConnection();
        if (connection == null || TYPES.isEmpty()) {
            return;
        }
        String ids = String.join("\0", TYPES.keySet().stream().map(ResourceLocation::toString).sorted().toList());
        connection.send(new ServerboundCustomPayloadPacket(new Raw(new CustomPacketPayload.Type<>(REGISTER),
                ids.getBytes(StandardCharsets.UTF_8))));
    }
}
