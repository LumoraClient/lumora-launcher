package dev.lumora.client.platform;

import io.netty.buffer.Unpooled;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.CustomPayloadC2SPacket;
import net.minecraft.util.PacketByteBuf;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Plugin channels on Minecraft 1.8.9 / 1.12.2 without a mod loader: channels are plain names (at most 20
 * characters on this protocol). {@code LegacyPayloadMixin} takes payloads of registered channels before vanilla sees
 * them and delivers them on the client thread; registered channels are announced with {@code REGISTER} on join.
 */
public final class Channels {
    public static final String REGISTER = "REGISTER";
    public static final int MAX_NAME = 20;

    private static final Map<String, BiConsumer<String, byte[]>> RECEIVERS = new ConcurrentHashMap<>();

    private Channels() {
    }

    public static boolean register(String id, BiConsumer<String, byte[]> receiver) {
        return id.length() <= MAX_NAME && RECEIVERS.putIfAbsent(id, receiver) == null;
    }

    public static boolean registered(String id) {
        return RECEIVERS.containsKey(id);
    }

    /** Network thread: a payload of a registered channel goes to its receiver on the client thread. */
    public static boolean receive(String id, PacketByteBuf data) {
        BiConsumer<String, byte[]> r = RECEIVERS.get(id);
        if (r == null) {
            return false;
        }
        byte[] bytes = new byte[data.readableBytes()];
        data.getBytes(data.readerIndex(), bytes);
        if (data.refCnt() > 0) {
            data.release();
        }
        MinecraftClient.getInstance().submit(() -> r.accept(id, bytes));
        return true;
    }

    public static boolean canSend(String id) {
        return RECEIVERS.containsKey(id) && MinecraftClient.getInstance().getNetworkHandler() != null;
    }

    public static boolean send(String id, byte[] data) {
        var handler = MinecraftClient.getInstance().getNetworkHandler();
        if (!RECEIVERS.containsKey(id) || handler == null) {
            return false;
        }
        handler.sendPacket(new CustomPayloadC2SPacket(id, new PacketByteBuf(Unpooled.wrappedBuffer(data))));
        return true;
    }

    /** Tells the server which channels this client listens on (NUL separated names). */
    public static void announce() {
        var handler = MinecraftClient.getInstance().getNetworkHandler();
        if (handler == null || RECEIVERS.isEmpty()) {
            return;
        }
        String ids = String.join("\0", RECEIVERS.keySet().stream().sorted().toList());
        handler.sendPacket(new CustomPayloadC2SPacket(REGISTER,
                new PacketByteBuf(Unpooled.wrappedBuffer(ids.getBytes(StandardCharsets.UTF_8)))));
    }
}
