package dev.lumora.voice.protocol;

import java.io.*;
import java.util.*;

/** Bounded, independent representation of the LM4 channel wire messages. */
public final class VoiceChannels {
    public static final UUID PROXIMITY = new UUID(0, 0);
    public record Channel(UUID id, Map<String, String> properties) {
        public String name() { return properties.getOrDefault("name", id.equals(PROXIMITY) ? "Umgebung" : "Sprachkanal"); }
        public boolean proximity() { return id.equals(PROXIMITY) || Boolean.parseBoolean(properties.getOrDefault("proximity", "false")); }
        public boolean password() { return Boolean.parseBoolean(properties.getOrDefault("password", "false")); }
    }
    public record User(UUID id, UUID channelId, Map<String, String> properties) { public String name() { return properties.getOrDefault("name", id.toString().substring(0, 8)); } }
    private final UUID self;
    private final Map<UUID, Channel> channels = new LinkedHashMap<>();
    private final Map<UUID, User> users = new LinkedHashMap<>();
    private String alert = "";
    public VoiceChannels(UUID self) { this.self = self; reset(); }
    private void reset() { channels.clear(); users.clear(); channels.put(PROXIMITY, new Channel(PROXIMITY, Map.of("name", "Umgebung", "proximity", "true"))); }
    public synchronized List<Channel> channels() { return List.copyOf(channels.values()); }
    public synchronized List<User> users() { return List.copyOf(users.values()); }
    public synchronized User user(UUID id) { return users.get(id); }
    public synchronized UUID currentId() { var user = users.get(self); return user == null ? PROXIMITY : user.channelId(); }
    public synchronized Channel current() { return channels.getOrDefault(currentId(), channels.get(PROXIMITY)); }
    public synchronized boolean remoteAllowed(UUID id) { var user = users.get(id); return user != null && !current().proximity() && user.channelId().equals(currentId()); }
    public synchronized String channelName(UUID id) { var user = users.get(id); if (user == null) return ""; var channel = channels.get(user.channelId()); return channel == null ? "" : channel.name(); }
    /** {@code id} is in a channel without proximity that you are not in (you never hear them). */
    public synchronized boolean otherGroup(UUID id) { var user = users.get(id); if (user == null || id.equals(self) || user.channelId().equals(currentId())) return false; var channel = channels.get(user.channelId()); return channel == null ? !user.channelId().equals(PROXIMITY) : !channel.proximity(); }
    public synchronized String alert() { return alert; }
    private static int count(DataInputStream in, int max) throws IOException { int count = in.readUnsignedShort(); if (count > max) throw new IOException("Too many voice entries"); return count; }
    public static Map<String, String> properties(DataInputStream in) throws IOException {
        int count = count(in, 32); Map<String, String> values = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) values.put(VoiceProtocol.string(in), VoiceProtocol.string(in));
        return Map.copyOf(values);
    }
    public static void properties(DataOutputStream out, Map<String, String> values) throws IOException {
        if (values.size() > 32) throw new IOException("Too many voice properties"); out.writeShort(values.size());
        for (var entry : values.entrySet()) { VoiceProtocol.string(out, entry.getKey()); VoiceProtocol.string(out, entry.getValue()); }
    }
    private static Map<String, String> merge(Map<String, String> before, Map<String, String> update) { var values = new LinkedHashMap<>(before); values.putAll(update); return Map.copyOf(values); }
    public synchronized boolean receive(int id, DataInputStream in) throws IOException {
        switch (id) {
            case VoicePackets.CHANNEL_SHOW -> {
                int count = count(in, 128);
                for (int i = 0; i < count; i++) { UUID channel = VoiceProtocol.uuid(in); var props = properties(in); if (channels.size() < 128 || channels.containsKey(channel)) channels.put(channel, new Channel(channel, props)); }
            }
            case VoicePackets.CHANNEL_UPDATE -> { UUID channel = VoiceProtocol.uuid(in); var props = properties(in); var old = channels.get(channel); if (old != null) channels.put(channel, new Channel(channel, merge(old.properties(), props))); }
            case VoicePackets.CHANNEL_HIDE -> { int count = count(in, 128); for (int i = 0; i < count; i++) { UUID channel = VoiceProtocol.uuid(in); if (!channel.equals(PROXIMITY)) channels.remove(channel); } }
            case VoicePackets.USER_SHOW -> {
                int count = count(in, 512);
                for (int i = 0; i < count; i++) { UUID user = VoiceProtocol.uuid(in), channel = VoiceProtocol.uuid(in); var props = properties(in); if (users.size() < 512 || users.containsKey(user)) users.put(user, new User(user, channel, props)); }
            }
            case VoicePackets.USER_UPDATE -> { UUID user = VoiceProtocol.uuid(in); var props = properties(in); var old = users.get(user); if (old != null) users.put(user, new User(user, old.channelId(), merge(old.properties(), props))); }
            case VoicePackets.USER_SWITCH -> { UUID user = VoiceProtocol.uuid(in); VoiceProtocol.uuid(in); UUID channel = VoiceProtocol.uuid(in); var props = properties(in); var old = users.get(user); if (users.size() < 512 || old != null) users.put(user, new User(user, channel, old == null ? props : merge(old.properties(), props))); }
            case VoicePackets.USER_HIDE -> { int count = count(in, 512); for (int i = 0; i < count; i++) { UUID user = VoiceProtocol.uuid(in); VoiceProtocol.uuid(in); users.remove(user); } }
            case VoicePackets.CHANNEL_ALERT -> { String message = VoiceProtocol.string(in); int count = count(in, 16); var args = new ArrayList<String>(); for (int i = 0; i < count; i++) args.add(VoiceProtocol.string(in)); alert = message + (args.isEmpty() ? "" : ": " + String.join(", ", args)); }
            case VoicePackets.CHANNEL_RESET -> reset();
            default -> { return false; }
        }
        return true;
    }
}
