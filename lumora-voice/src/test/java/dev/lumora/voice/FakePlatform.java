package dev.lumora.voice;

import dev.lumora.voice.platform.VoicePlatform;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

/** Scriptable {@link VoicePlatform} for tests. */
final class FakePlatform implements VoicePlatform {
    Optional<Profile> profile = Optional.empty();
    Optional<Server> server = Optional.empty();
    Optional<Listener> listener = Optional.empty();
    List<Peer> players = new ArrayList<>();
    Set<UUID> occluded = new HashSet<>();
    List<Named> tabList = new ArrayList<>();
    Optional<Named> target = Optional.empty();
    boolean screenOpen, focused = true;
    final Set<String> mods = new HashSet<>();
    final Set<String> serverChannels = new HashSet<>();
    final Map<String, Consumer<byte[]>> receivers = new HashMap<>();
    final List<String> sent = new ArrayList<>();
    final Map<String, byte[]> lastPayload = new HashMap<>();
    final List<String> actionBar = new ArrayList<>();
    final List<String> joins = new ArrayList<>();
    int sounds;
    Path configDir = Path.of("build", "tmp", "fake-config");

    void deliver(String channel, byte[] payload) { receivers.get(channel).accept(payload); }

    @Override public Optional<Profile> profile() { return profile; }
    @Override public void joinServer(String serverHash) { joins.add(serverHash); }
    @Override public Optional<Server> server() { return server; }
    @Override public boolean screenOpen() { return screenOpen; }
    @Override public boolean windowFocused() { return focused; }
    @Override public Optional<Listener> listener() { return listener; }
    @Override public List<Peer> players(int limit) { return players.stream().limit(limit).toList(); }
    @Override public boolean occluded(Peer peer) { return occluded.contains(peer.id()); }
    @Override public int seaLevel() { return 63; }
    @Override public List<Named> tabList() { return tabList; }
    @Override public Optional<Named> targetPlayer() { return target; }
    @Override public void actionBar(String message) { actionBar.add(message); }
    @Override public void uiSound() { sounds++; }
    @Override public Channel channel(String id, Consumer<byte[]> receiver) {
        receivers.put(id, receiver);
        return new Channel() {
            @Override public boolean serverSupports() { return serverChannels.contains(id); }
            @Override public void send(byte[] payload) { sent.add(id); lastPayload.put(id, payload); }
        };
    }
    @Override public boolean modLoaded(String id) { return mods.contains(id); }
    @Override public Path configDir() { return configDir; }
    @Override public String minecraftVersion() { return "test"; }
}
