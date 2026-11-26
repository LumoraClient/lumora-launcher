package dev.lumora.voice.platform;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Everything LumoraVoice needs from the running game, independent of the Minecraft version. The Lumora addon
 * implements it on top of the Lumora API ({@code LumoraPlatform}); tests use a fake. Calls happen on the client
 * thread unless noted otherwise.
 */
public interface VoicePlatform {
    /** The signed-in account; empty in demo mode or without a valid session. */
    Optional<Profile> profile();

    /**
     * Announces this client to Mojang's session server with {@code serverHash} (like joining an online-mode server),
     * so the voice service can verify the account. The access token stays inside the game. Called on a network
     * thread; throws on failure.
     */
    void joinServer(String serverHash) throws Exception;

    /** The multiplayer server the client is connected to; empty in menus, singleplayer and demo worlds. */
    Optional<Server> server();

    boolean screenOpen();

    boolean windowFocused();

    /** Own eye position and view direction; empty outside a world. */
    Optional<Listener> listener();

    /** Other players in the loaded world (never the own player), nearest first, at most {@code limit}. */
    List<Peer> players(int limit);

    /** Whether solid blocks are between the own eyes and {@code peer}'s eyes (for muffled voices). */
    boolean occluded(Peer peer);

    /** World sea level (for the cave echo). */
    int seaLevel();

    /** Players in the tab list, own player included. */
    List<Named> tabList();

    /** The player under the crosshair, if any. */
    Optional<Named> targetPlayer();

    /** Short message above the hotbar. */
    void actionBar(String message);

    /** Short UI click sound (notification sound setting). */
    void uiSound();

    /**
     * Registers a plugin channel ({@code namespace:path}). Must be called during addon start, before joining a
     * server. {@code receiver} gets the raw payload bytes on the client thread.
     */
    Channel channel(String id, Consumer<byte[]> receiver);

    /** Whether a Fabric mod with this id is installed (e.g. the real Simple Voice Chat mod). */
    boolean modLoaded(String id);

    /** Folder for lumoravoice.json. */
    Path configDir();

    String minecraftVersion();

    record Profile(UUID id, String name) {}

    /**
     * @param address  the server address as entered in the server list ({@code host} or {@code host:port})
     * @param remoteIp the IP address the game is actually connected to (SRV records resolved), or empty
     */
    record Server(String address, String remoteIp) {}

    /** Eye position and normalized view direction. */
    record Listener(UUID id, double x, double y, double z, double lookX, double lookY, double lookZ, boolean inWater) {}

    /**
     * A player with its eye position.
     *
     * @param skyVisible whether the block at the player's position sees the sky
     * @param teamColor  RGB team color, if the player is in a team with a color
     */
    record Peer(UUID id, String name, double x, double y, double z, boolean inWater, boolean skyVisible, OptionalInt teamColor) {
        public double distance(Listener listener) {
            double dx = x - listener.x(), dy = y - listener.y(), dz = z - listener.z();
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
    }

    record Named(UUID id, String name) {}

    interface Channel {
        /** Whether the current server announced this channel (mod or Bukkit/Paper plugin). */
        boolean serverSupports();

        void send(byte[] payload);
    }
}
