package dev.lumora.voice.svc;

import dev.lumora.voice.VoiceLog;
import dev.lumora.voice.platform.VoicePlatform;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Minecraft plugin channels of Simple Voice Chat ({@code voicechat:*}). Only the channels needed for speaking and
 * groups are registered; categories stay unknown and the game drops them. The payloads are encoded here byte by byte
 * (Minecraft's FriendlyByteBuf format: big-endian numbers, VarInt-prefixed UTF-8 strings, UUIDs as two longs), so the
 * same code works on every Minecraft version.
 */
public final class SimpleVoiceChannel {
    public interface Handler {
        void secret(Secret packet);
        void states(List<SimpleVoiceConnection.State> states);
        void state(SimpleVoiceConnection.State state);
        void removeState(UUID id);
        /** A group was created or changed ({@code voicechat:add_group}). */
        default void group(SvcGroups.Group group) {}
        /** A group was removed ({@code voicechat:remove_group}). */
        default void removeGroup(UUID id) {}
        /** Answer to joining a group: the group, or {@code null} when not joined ({@code voicechat:joined_group}). */
        default void joinedGroup(UUID group, boolean wrongPassword) {}
    }

    public record Secret(byte[] secret, int port, UUID player, int codec, int mtu, double distance, int keepAlive, boolean groups, String voiceHost, boolean recording) {}

    /** Answer of {@code voicechat:joined_group}. */
    public record Joined(UUID group, boolean wrongPassword) {}

    private static final int MAX_STRING = 32767, MAX_STATES = 4096, MAX_GROUP_NAME = 512;
    private final VoicePlatform.Channel requestSecret, updateState, setGroup, createGroup, leaveGroup;

    private SimpleVoiceChannel(VoicePlatform platform, Handler handler) {
        requestSecret = platform.channel("voicechat:request_secret", ignored -> {});
        updateState = platform.channel("voicechat:update_state", ignored -> {});
        setGroup = platform.channel("voicechat:set_group", ignored -> {});
        createGroup = platform.channel("voicechat:create_group", ignored -> {});
        leaveGroup = platform.channel("voicechat:leave_group", ignored -> {});
        platform.channel("voicechat:secret", bytes -> receive("secret", () -> handler.secret(readSecret(bytes))));
        platform.channel("voicechat:states", bytes -> receive("states", () -> handler.states(readStates(bytes))));
        platform.channel("voicechat:state", bytes -> receive("state", () -> handler.state(readState(in(bytes)))));
        platform.channel("voicechat:remove_state", bytes -> receive("remove_state", () -> handler.removeState(readUuid(in(bytes)))));
        platform.channel("voicechat:add_group", bytes -> receive("add_group", () -> handler.group(readGroup(bytes))));
        platform.channel("voicechat:remove_group", bytes -> receive("remove_group", () -> handler.removeGroup(readUuid(in(bytes)))));
        platform.channel("voicechat:joined_group", bytes -> receive("joined_group", () -> { var j = readJoined(bytes); handler.joinedGroup(j.group(), j.wrongPassword()); }));
    }

    /** Joins a group ({@code password} empty for none); the server answers with {@code joined_group}. */
    public boolean joinGroup(UUID group, String password) {
        if (!setGroup.serverSupports()) return false;
        setGroup.send(writeSetGroup(group, password));
        return true;
    }

    /** Creates a group; the server puts the creator into it. */
    public boolean createGroup(String name, String password, SvcGroups.Type type) {
        if (!createGroup.serverSupports()) return false;
        createGroup.send(writeCreateGroup(name, password, type));
        return true;
    }

    public boolean leaveGroup() {
        if (!leaveGroup.serverSupports()) return false;
        leaveGroup.send(new byte[0]);
        return true;
    }

    /** The real Simple Voice Chat mod uses the same channels; then LumoraVoice stays with Laby Voice. */
    public static boolean conflictingMod(VoicePlatform platform) { return platform.modLoaded("voicechat"); }

    /** Registers the channels; {@code null} if the Simple Voice Chat mod itself is installed. */
    public static SimpleVoiceChannel register(VoicePlatform platform, Handler handler) {
        return conflictingMod(platform) ? null : new SimpleVoiceChannel(platform, handler);
    }

    /** The server announced the Simple Voice Chat channel (mod or Bukkit/Paper plugin). */
    public boolean available() { return requestSecret.serverSupports(); }

    public void requestSecret() {
        if (available()) requestSecret.send(write(out -> out.writeInt(SimpleVoiceProtocol.COMPATIBILITY_VERSION)));
    }

    public void updateState(boolean disabled) {
        if (updateState.serverSupports()) updateState.send(write(out -> out.writeBoolean(disabled)));
    }

    private interface Body { void run() throws IOException; }

    private static void receive(String channel, Body body) {
        try { body.run(); }
        catch (IOException | RuntimeException malformed) { VoiceLog.warn("Ignoring malformed Simple Voice Chat packet on voicechat:" + channel, malformed); }
    }

    // --- codec (package-private for tests) ---

    static Secret readSecret(byte[] bytes) throws IOException {
        DataInputStream in = in(bytes);
        byte[] secret = new byte[SimpleVoiceProtocol.SECRET_BYTES]; in.readFully(secret);
        return new Secret(secret, in.readInt(), readUuid(in), in.readUnsignedByte(), in.readInt(), in.readDouble(), in.readInt(), in.readBoolean(), readUtf(in), in.readBoolean());
    }

    static byte[] writeSecret(Secret p) {
        return write(out -> {
            out.write(p.secret()); out.writeInt(p.port()); writeUuid(out, p.player()); out.writeByte(p.codec()); out.writeInt(p.mtu());
            out.writeDouble(p.distance()); out.writeInt(p.keepAlive()); out.writeBoolean(p.groups()); writeUtf(out, p.voiceHost()); out.writeBoolean(p.recording());
        });
    }

    static List<SimpleVoiceConnection.State> readStates(byte[] bytes) throws IOException {
        DataInputStream in = in(bytes);
        int count = in.readInt();
        if (count < 0 || count > MAX_STATES) throw new IOException("Zu viele Voice-Spieler: " + count);
        var states = new ArrayList<SimpleVoiceConnection.State>(count);
        for (int i = 0; i < count; i++) states.add(readState(in));
        return states;
    }

    static byte[] writeStates(List<SimpleVoiceConnection.State> states) {
        return write(out -> { out.writeInt(states.size()); for (var s : states) writeState(out, s); });
    }

    static byte[] writeState(SimpleVoiceConnection.State state) { return write(out -> writeState(out, state)); }

    static SimpleVoiceConnection.State readState(DataInputStream in) throws IOException {
        boolean disabled = in.readBoolean(), disconnected = in.readBoolean();
        UUID id = readUuid(in); String name = readUtf(in);
        return new SimpleVoiceConnection.State(id, name, disabled, disconnected, in.readBoolean() ? readUuid(in) : null);
    }

    private static void writeState(DataOutputStream out, SimpleVoiceConnection.State s) throws IOException {
        out.writeBoolean(s.disabled()); out.writeBoolean(s.disconnected()); writeUuid(out, s.id()); writeUtf(out, s.name());
        out.writeBoolean(s.group() != null); if (s.group() != null) writeUuid(out, s.group());
    }

    /** {@code add_group}: id, name, has password, persistent, hidden, type (short). */
    static SvcGroups.Group readGroup(byte[] bytes) throws IOException {
        DataInputStream in = in(bytes);
        UUID id = readUuid(in); String name = readUtf(in);
        if (name.length() > MAX_GROUP_NAME) throw new IOException("Gruppenname zu lang");
        boolean password = in.readBoolean(), persistent = in.readBoolean(), hidden = in.readBoolean();
        return new SvcGroups.Group(id, name, password, persistent, hidden, SvcGroups.Type.of(in.readShort()));
    }

    static byte[] writeGroup(SvcGroups.Group g) {
        return write(out -> { writeUuid(out, g.id()); writeUtf(out, g.name()); out.writeBoolean(g.password()); out.writeBoolean(g.persistent());
            out.writeBoolean(g.hidden()); out.writeShort(g.type().id()); });
    }

    /** {@code joined_group}: optional group id, then whether the password was wrong. */
    static Joined readJoined(byte[] bytes) throws IOException {
        DataInputStream in = in(bytes);
        UUID group = in.readBoolean() ? readUuid(in) : null;
        return new Joined(group, in.readBoolean());
    }

    /** {@code set_group}: group id and an optional password. */
    static byte[] writeSetGroup(UUID group, String password) {
        return write(out -> { writeUuid(out, group); writeOptionalUtf(out, password); });
    }

    /** {@code create_group}: name, optional password, type (short). */
    static byte[] writeCreateGroup(String name, String password, SvcGroups.Type type) {
        return write(out -> { writeUtf(out, name); writeOptionalUtf(out, password); out.writeShort(type.id()); });
    }

    private static void writeOptionalUtf(DataOutputStream out, String text) throws IOException {
        boolean present = text != null && !text.isEmpty();
        out.writeBoolean(present);
        if (present) writeUtf(out, text);
    }

    static DataInputStream in(byte[] bytes) { return new DataInputStream(new ByteArrayInputStream(bytes)); }

    interface Writer { void write(DataOutputStream out) throws IOException; }

    static byte[] write(Writer writer) {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) { writer.write(out); }
        catch (IOException impossible) { throw new UncheckedIOException(impossible); }
        return bytes.toByteArray();
    }

    static UUID readUuid(DataInputStream in) throws IOException { return new UUID(in.readLong(), in.readLong()); }

    static void writeUuid(DataOutputStream out, UUID id) throws IOException { out.writeLong(id.getMostSignificantBits()); out.writeLong(id.getLeastSignificantBits()); }

    static int readVarInt(DataInputStream in) throws IOException {
        int value = 0;
        for (int shift = 0; shift < 35; shift += 7) {
            int b = in.readUnsignedByte();
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) return value;
        }
        throw new IOException("VarInt zu lang");
    }

    static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while ((value & ~0x7F) != 0) { out.writeByte((value & 0x7F) | 0x80); value >>>= 7; }
        out.writeByte(value);
    }

    static String readUtf(DataInputStream in) throws IOException {
        int length = readVarInt(in);
        if (length < 0 || length > MAX_STRING * 3) throw new IOException("Text zu lang: " + length);
        byte[] bytes = new byte[length]; in.readFully(bytes);
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (text.length() > MAX_STRING) throw new IOException("Text zu lang: " + text.length());
        return text;
    }

    static void writeUtf(DataOutputStream out, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length); out.write(bytes);
    }
}
