package dev.lumora.api.net;

import java.util.function.Consumer;

/**
 * Minecraft plugin channels ({@code namespace:path}) with raw payload bytes (permission {@code network}). The payload
 * format is the addon's business; Lumora only moves the bytes, so the same addon code works on every version.
 *
 * @since 1.1.0
 */
public interface PluginChannels {
    /**
     * Registers a channel; only during {@code onEnable}, before joining a server. {@code receiver} gets every payload
     * the server sends on it, on the client thread. One addon per channel.
     *
     * @throws IllegalArgumentException for an invalid id
     * @throws IllegalStateException    if another addon registered the channel
     */
    PluginChannel register(String id, Consumer<byte[]> receiver);

    interface PluginChannel {
        String id();

        /** Whether the current server announced this channel (mod or Bukkit/Paper plugin). */
        boolean serverSupports();

        /** Sends to the server; ignored when not connected or not supported. */
        void send(byte[] payload);
    }
}
