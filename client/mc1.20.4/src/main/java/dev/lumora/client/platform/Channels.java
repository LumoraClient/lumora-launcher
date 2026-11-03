package dev.lumora.client.platform;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Plugin channels (custom payloads) without a mod loader, Minecraft 1.20.2 - 1.20.4. Payloads of channels Minecraft
 * does not know are read as {@code DiscardedPayload} and their bytes skipped; for registered channels
 * {@code CustomPayloadReadMixin} keeps the bytes in a {@link Raw} payload instead. {@code ClientCustomPayloadMixin}
 * delivers those on the client thread. Registered channels are announced with {@code minecraft:register} on join.
 */
public final class Channels {
    public static final ResourceLocation REGISTER = new ResourceLocation("minecraft", "register");
    private static final int MAX_BYTES = 1048576;

    /** The whole payload as bytes. */
    public record Raw(ResourceLocation id, byte[] data) implements CustomPacketPayload {
        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeBytes(data);
        }
    }

    private static final Map<ResourceLocation, BiConsumer<ResourceLocation, byte[]>> RECEIVERS = new ConcurrentHashMap<>();

    private Channels() {
    }

    public static boolean register(ResourceLocation id, BiConsumer<ResourceLocation, byte[]> receiver) {
        return RECEIVERS.putIfAbsent(id, receiver) == null;
    }

    public static boolean registered(ResourceLocation id) {
        return RECEIVERS.containsKey(id);
    }

    /** Network thread, while the packet is decoded: the bytes of a registered channel, else null (vanilla decides). */
    public static Raw read(ResourceLocation id, FriendlyByteBuf buf) {
        if (!RECEIVERS.containsKey(id) && !REGISTER.equals(id)) {
            return null;
        }
        int n = buf.readableBytes();
        if (n > MAX_BYTES) {
            throw new IllegalArgumentException("Payload may not be larger than " + MAX_BYTES + " bytes");
        }
        byte[] data = new byte[n];
        buf.readBytes(data);
        return new Raw(id, data);
    }

    /** Network thread: hands a received payload to its receiver on the client thread. */
    public static boolean receive(CustomPacketPayload payload) {
        if (!(payload instanceof Raw raw)) {
            return false;
        }
        BiConsumer<ResourceLocation, byte[]> r = RECEIVERS.get(raw.id());
        if (r != null) {
            Minecraft.getInstance().execute(() -> r.accept(raw.id(), raw.data()));
        }
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
        connection.send(new ServerboundCustomPayloadPacket(new Raw(id, data)));
        return true;
    }

    /** Tells the server which channels this client listens on (NUL separated ids, as Bukkit/Paper expect). */
    public static void announce() {
        var connection = Minecraft.getInstance().getConnection();
        if (connection == null || RECEIVERS.isEmpty()) {
            return;
        }
        String ids = String.join("\0", RECEIVERS.keySet().stream().map(ResourceLocation::toString).sorted().toList());
        connection.send(new ServerboundCustomPayloadPacket(new Raw(REGISTER, ids.getBytes(StandardCharsets.UTF_8))));
    }
}
