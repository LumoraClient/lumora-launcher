package dev.lumora.client.platform;

import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Plugin channels (custom payloads) without a mod loader, Minecraft up to 1.20.1: the packet carries the channel id
 * and the raw bytes. {@code LegacyCustomPayloadMixin} takes the payloads of registered channels before vanilla sees
 * them and delivers them on the client thread. Registered channels are announced with {@code minecraft:register} on
 * join (servers such as Paper only send to channels a client registered).
 */
public final class Channels {
    public static final ResourceLocation REGISTER = new ResourceLocation("minecraft", "register");

    private static final Map<ResourceLocation, BiConsumer<ResourceLocation, byte[]>> RECEIVERS = new ConcurrentHashMap<>();

    private Channels() {
    }

    public static boolean register(ResourceLocation id, BiConsumer<ResourceLocation, byte[]> receiver) {
        return RECEIVERS.putIfAbsent(id, receiver) == null;
    }

    public static boolean registered(ResourceLocation id) {
        return RECEIVERS.containsKey(id);
    }

    /**
     * Network thread: a payload of a registered channel goes to its receiver on the client thread; the buffer is
     * released here because vanilla's handler (which would release it) is skipped. False for other channels.
     */
    public static boolean receive(ResourceLocation id, FriendlyByteBuf data) {
        BiConsumer<ResourceLocation, byte[]> r = RECEIVERS.get(id);
        if (r == null) {
            return false;
        }
        byte[] bytes = new byte[data.readableBytes()];
        data.getBytes(data.readerIndex(), bytes);
        data.release();
        Minecraft.getInstance().execute(() -> r.accept(id, bytes));
        return true;
    }

    public static boolean canSend(ResourceLocation id) {
        return RECEIVERS.containsKey(id) && Minecraft.getInstance().getConnection() != null;
    }

    public static boolean send(ResourceLocation id, byte[] data) {
        var connection = Minecraft.getInstance().getConnection();
        if (!RECEIVERS.containsKey(id) || connection == null) {
            return false;
        }
        connection.send(new ServerboundCustomPayloadPacket(id, new FriendlyByteBuf(Unpooled.wrappedBuffer(data))));
        return true;
    }

    /** Tells the server which channels this client listens on (NUL separated ids, as Bukkit/Paper expect). */
    public static void announce() {
        var connection = Minecraft.getInstance().getConnection();
        if (connection == null || RECEIVERS.isEmpty()) {
            return;
        }
        String ids = String.join("\0", RECEIVERS.keySet().stream().map(ResourceLocation::toString).sorted().toList());
        connection.send(new ServerboundCustomPayloadPacket(REGISTER,
                new FriendlyByteBuf(Unpooled.wrappedBuffer(ids.getBytes(StandardCharsets.UTF_8)))));
    }
}
