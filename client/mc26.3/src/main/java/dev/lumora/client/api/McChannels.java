package dev.lumora.client.api;

import dev.lumora.client.platform.Channels;
import dev.lumora.core.net.ChannelRegistry;
import net.minecraft.resources.Identifier;

/**
 * Addon plugin channels on Minecraft 26.3 through Lumora's own channel support ({@link Channels}): raw bytes per
 * channel, received payloads go to the core on the client thread.
 */
final class McChannels implements ChannelRegistry.Platform {
    @Override
    public boolean register(String id) {
        try {
            Identifier channel = Identifier.parse(id);
            return Channels.register(channel, (ch, data) -> {
                var core = LumoraRuntime.core();
                if (core != null) {
                    core.channelRegistry().deliver(id, data);
                }
            });
        } catch (RuntimeException e) {
            LumoraRuntime.core().logger().warn("Could not register plugin channel " + id, e);
            return false;
        }
    }

    @Override
    public boolean canSend(String id) {
        try {
            return Channels.canSend(Identifier.parse(id));
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public void send(String id, byte[] payload) {
        try {
            Channels.send(Identifier.parse(id), payload);
        } catch (RuntimeException e) {
            LumoraRuntime.core().logger().warn("Could not send on plugin channel " + id, e);
        }
    }
}
