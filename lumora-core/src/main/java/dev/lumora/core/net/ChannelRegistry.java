package dev.lumora.core.net;

import dev.lumora.api.net.PluginChannels;
import dev.lumora.core.CoreLogger;
import dev.lumora.core.RegistrationTracker;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Plugin channels of all addons. The version adapter registers a channel with Minecraft once ({@link Platform#register})
 * and hands every received payload to {@link #deliver}; the receiver of the owning addon gets it. Minecraft keeps the
 * registration after an addon is disabled, but nothing is delivered to it any more.
 */
public final class ChannelRegistry {
    /** What the version adapter does with Minecraft's networking. */
    public interface Platform {
        /** Registers {@code id} for both directions; {@code false} if this version cannot (e.g. name too long). */
        boolean register(String id);

        boolean canSend(String id);

        void send(String id, byte[] payload);

        Platform NONE = new Platform() {
            @Override public boolean register(String id) { return false; }
            @Override public boolean canSend(String id) { return false; }
            @Override public void send(String id, byte[] payload) { }
        };
    }

    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private record Owner(String addon, Consumer<byte[]> receiver) {}

    private final Platform platform;
    private final CoreLogger log;
    private final Map<String, Owner> owners = new ConcurrentHashMap<>();
    private final Map<String, Boolean> registered = new ConcurrentHashMap<>();

    public ChannelRegistry(Platform platform, CoreLogger log) {
        this.platform = platform;
        this.log = log;
    }

    /** Payload from the server on {@code id} (client thread). */
    public void deliver(String id, byte[] payload) {
        Owner owner = owners.get(id);
        if (owner == null) {
            return;
        }
        try {
            owner.receiver.accept(payload);
        } catch (RuntimeException | LinkageError e) {
            log.warn("Plugin channel " + id + " of " + owner.addon + " failed", e);
        }
    }

    /** Ids registered with Minecraft so far (for the adapter's channel announcement). */
    public java.util.Set<String> ids() {
        return java.util.Set.copyOf(registered.keySet());
    }

    public PluginChannels scoped(String addon, RegistrationTracker tracker, Runnable guard) {
        return (id, receiver) -> {
            guard.run();
            if (id == null || id.length() > 128 || !ID.matcher(id).matches()) {
                throw new IllegalArgumentException("invalid channel id: " + id);
            }
            if (receiver == null) {
                throw new IllegalArgumentException("receiver");
            }
            Owner previous = owners.putIfAbsent(id, new Owner(addon, receiver));
            if (previous != null) {
                throw new IllegalStateException("channel " + id + " already belongs to " + previous.addon);
            }
            boolean usable = registered.computeIfAbsent(id, platform::register);
            if (!usable) {
                log.info("Plugin channel " + id + " is not available in this Minecraft version");
            }
            tracker.track(RegistrationTracker.of(() -> owners.remove(id)));
            return new PluginChannels.PluginChannel() {
                @Override
                public String id() {
                    return id;
                }

                @Override
                public boolean serverSupports() {
                    return usable && owners.containsKey(id) && platform.canSend(id);
                }

                @Override
                public void send(byte[] payload) {
                    if (serverSupports()) {
                        platform.send(id, payload.clone());
                    }
                }
            };
        };
    }
}
