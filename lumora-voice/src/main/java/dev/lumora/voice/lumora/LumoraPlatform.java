package dev.lumora.voice.lumora;

import dev.lumora.api.addon.AddonContext;
import dev.lumora.api.game.ConnectionInfo;
import dev.lumora.api.game.WorldAccess;
import dev.lumora.voice.platform.VoicePlatform;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/** {@link VoicePlatform} on the Lumora API 1.1: the same code on every Minecraft version Lumora supports. */
final class LumoraPlatform implements VoicePlatform {
    private final AddonContext context;
    private final WorldAccess world;

    LumoraPlatform(AddonContext context) {
        this.context = context;
        this.world = context.world();
    }

    @Override
    public Optional<Profile> profile() {
        return context.session().profile().map(p -> new Profile(p.id(), p.name()));
    }

    @Override
    public void joinServer(String serverHash) throws Exception {
        context.session().joinServer(serverHash);
    }

    @Override
    public Optional<Server> server() {
        ConnectionInfo connection = context.game().connection();
        if (!connection.multiplayer() || connection.address().isBlank()) return Optional.empty();
        return Optional.of(new Server(connection.address(), world.remoteIp()));
    }

    @Override public boolean screenOpen() { return context.game().screenOpen(); }

    @Override public boolean windowFocused() { return context.game().windowFocused(); }

    @Override
    public Optional<Listener> listener() {
        return world.viewPoint().map(v -> new Listener(v.id(), v.x(), v.y(), v.z(), v.lookX(), v.lookY(), v.lookZ(), v.inWater()));
    }

    @Override
    public List<Peer> players(int limit) {
        var players = world.players(limit);
        var peers = new ArrayList<Peer>(players.size());
        for (var p : players) peers.add(new Peer(p.id(), p.name(), p.x(), p.y(), p.z(), p.inWater(), p.skyVisible(), p.teamColor()));
        return peers;
    }

    @Override
    public boolean occluded(Peer peer) {
        var view = world.viewPoint();
        return view.isPresent() && world.lineOfSightBlocked(view.get().x(), view.get().y(), view.get().z(), peer.x(), peer.y(), peer.z());
    }

    @Override public int seaLevel() { return world.seaLevel(); }

    @Override
    public List<Named> tabList() {
        var entries = world.tabList();
        var named = new ArrayList<Named>(entries.size());
        for (var e : entries) named.add(new Named(e.id(), e.name()));
        return named;
    }

    @Override
    public Optional<Named> targetPlayer() {
        return world.crosshairPlayer().map(e -> new Named(e.id(), e.name()));
    }

    @Override public void actionBar(String message) { world.actionBar(message); }

    @Override public void uiSound() { world.playClickSound(); }

    @Override
    public Channel channel(String id, Consumer<byte[]> receiver) {
        var channel = context.channels().register(id, receiver);
        return new Channel() {
            @Override public boolean serverSupports() { return channel.serverSupports(); }
            @Override public void send(byte[] payload) { channel.send(payload); }
        };
    }

    @Override public boolean modLoaded(String id) { return context.modLoaded(id); }

    @Override public Path configDir() { return context.configDirectory(); }

    @Override public String minecraftVersion() { return context.lumora().minecraftVersion(); }
}
