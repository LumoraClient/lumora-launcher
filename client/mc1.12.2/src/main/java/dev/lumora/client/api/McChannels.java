package dev.lumora.client.api;

import dev.lumora.client.platform.Channels;
import dev.lumora.core.net.ChannelRegistry;

/**
 * Addon plugin channels on Minecraft 1.8.9 / 1.12.2 through Lumora's own channel support ({@link Channels}). Channel
 * names are limited to 20 characters on this protocol; longer ones cannot be used here. Received payloads go to the
 * core on the client thread.
 */
final class McChannels implements ChannelRegistry.Platform {
    @Override
    public boolean register(String id) {
        return Channels.register(id, (ch, data) -> {
            var core = LumoraRuntime.core();
            if (core != null) {
                core.channelRegistry().deliver(id, data);
            }
        });
    }

    @Override
    public boolean canSend(String id) {
        return Channels.canSend(id);
    }

    @Override
    public void send(String id, byte[] payload) {
        Channels.send(id, payload);
    }
}
