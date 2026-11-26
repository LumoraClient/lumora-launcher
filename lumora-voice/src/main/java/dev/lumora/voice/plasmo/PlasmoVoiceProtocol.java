package dev.lumora.voice.plasmo;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Independent implementation of the Plasmo Voice 2.x wire format, only for working together with Plasmo Voice servers
 * (no code of Plasmo Voice is used). Control packets go over the plugin channel {@code plasmo:voice/v2} as
 * {@code byte id} + fields; voice goes over UDP as {@code int magic, byte id, UUID secret, long timestamp} + fields.
 * Numbers are big-endian, strings Java {@code writeUTF} (u16 length, modified UTF-8), byte arrays {@code int length} +
 * bytes. Voice data is an Opus frame, encrypted with AES/CBC/PKCS5Padding: 16 random IV bytes, then the cipher text.
 */
public final class PlasmoVoiceProtocol {
    public static final String CHANNEL = "plasmo:voice/v2", FLAG_CHANNEL = "plasmo:voice/v2/installed";
    /** Version sent to the server: it accepts the same major version (2) from {@code clientModMinVersion} (default 2.0.0) on. */
    public static final String VERSION = "2.1.0";
    public static final int MAGIC = 0x4e9004e9, MAX_DATA = 2048, SAMPLE_RATE = 48_000;
    // control packets (server → client unless noted)
    public static final int CONNECTION = 1, PLAYER_INFO_REQUEST = 2, CONFIG = 3, PLAYER_LIST = 7, PLAYER_INFO_UPDATE = 8,
            PLAYER_DISCONNECT = 9, PLAYER_INFO = 10 /* client → server */, PLAYER_STATE = 11 /* client → server */,
            PLAYER_AUDIO_END = 12 /* client → server */, SOURCE_INFO_REQUEST = 15 /* client → server */, SOURCE_INFO = 16, SOURCE_AUDIO_END = 18;
    // UDP packets
    public static final int PING = 1, PLAYER_AUDIO = 2, SOURCE_AUDIO = 3;
    private static final String AES = "AES/CBC/PKCS5Padding";
    private static final SecureRandom RANDOM = new SecureRandom();

    private PlasmoVoiceProtocol() {}

    /** Where to send voice: {@code ip} "0.0.0.0" means the address of the Minecraft server. */
    public record Connection(UUID secret, String ip, int port) {}

    /** A way of speaking the server offers; {@code id} goes into every voice packet. */
    public record Activation(UUID id, String name, List<Integer> distances, int defaultDistance, boolean proximity) {}

    /** Server configuration: audio format, the encrypted AES key and the activations. */
    public record Config(UUID serverId, int sampleRate, int mtu, String codec, String encryption, byte[] encryptedKey, List<Activation> activations) {
        /** The normal proximity activation ("proximity"), else the first proximity one, else the first; {@code null} without any. */
        public Activation proximity() {
            return activations.stream().filter(a -> a.name().equals("proximity")).findFirst()
                    .or(() -> activations.stream().filter(Activation::proximity).findFirst())
                    .orElse(activations.isEmpty() ? null : activations.getFirst());
        }

        /** The activation of pv-addon-groups ("groups"), or {@code null} when the server has no groups. */
        public Activation groups() { return activations.stream().filter(a -> a.name().equals("groups")).findFirst().orElse(null); }
    }

    /** A player with Plasmo Voice; {@code muted} = muted by the server. */
    public record PlayerInfo(UUID id, String name, boolean muted, boolean voiceDisabled, boolean microphoneMuted) {}

    /** A sound source; {@code player} is set for player sources only. */
    public record Source(UUID id, String type, String name, int state, UUID player) {}

    /** Header of a received datagram and the reader positioned at its fields. */
    public record Datagram(int type, UUID secret, DataInputStream body) {}

    /** Voice of a source; {@code data} still encrypted. */
    public record SourceAudio(long sequence, byte[] data, UUID source, int state, int distance) {}

    @FunctionalInterface
    interface Writer { void write(DataOutputStream out) throws IOException; }

    static byte[] write(Writer writer) {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) { writer.write(out); }
        catch (IOException impossible) { throw new UncheckedIOException(impossible); }
        return bytes.toByteArray();
    }

    // --- control packets: client → server ---

    public static byte[] playerInfo(String minecraftVersion, String version, byte[] publicKey, boolean voiceDisabled, boolean microphoneMuted) {
        return write(out -> {
            out.writeByte(PLAYER_INFO); out.writeBoolean(voiceDisabled); out.writeBoolean(microphoneMuted);
            out.writeUTF(minecraftVersion); out.writeUTF(version); bytes(out, publicKey);
        });
    }

    public static byte[] playerState(boolean voiceDisabled, boolean microphoneMuted) {
        return write(out -> { out.writeByte(PLAYER_STATE); out.writeBoolean(voiceDisabled); out.writeBoolean(microphoneMuted); });
    }

    public static byte[] audioEnd(long sequence, UUID activation, int distance) {
        return write(out -> { out.writeByte(PLAYER_AUDIO_END); out.writeLong(sequence); uuid(out, activation); out.writeShort(distance); });
    }

    public static byte[] sourceInfoRequest(UUID source) { return write(out -> { out.writeByte(SOURCE_INFO_REQUEST); uuid(out, source); }); }

    // --- control packets: server → client ---

    /** Packet id of a control payload, or -1 when empty. */
    public static int type(byte[] payload) { return payload.length == 0 ? -1 : payload[0] & 0xFF; }

    private static DataInputStream body(byte[] payload) {
        var in = new DataInputStream(new ByteArrayInputStream(payload));
        try { in.skipBytes(1); } catch (IOException impossible) { throw new UncheckedIOException(impossible); }
        return in;
    }

    public static Connection readConnection(byte[] payload) throws IOException {
        var in = body(payload);
        return new Connection(uuid(in), utf(in, 255), in.readInt());
    }

    public static Config readConfig(byte[] payload) throws IOException {
        var in = body(payload);
        UUID serverId = uuid(in);
        int sampleRate = in.readInt(), mtu = in.readInt();
        String codec = in.readBoolean() ? codec(in) : "";
        String encryption = ""; byte[] key = new byte[0];
        if (in.readBoolean()) { encryption = utf(in, 64); key = bytes(in, 2048); }
        int lines = count(in, Byte.MAX_VALUE);
        for (int i = 0; i < lines; i++) skipSourceLine(in);
        int count = count(in, Byte.MAX_VALUE);
        List<Activation> activations = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String name = utf(in, 64); utf(in, 256); utf(in, 256); // name, translation, icon
            int distanceCount = count(in, 64); List<Integer> distances = new ArrayList<>(distanceCount);
            for (int d = 0; d < distanceCount; d++) distances.add(in.readInt());
            int defaultDistance = in.readInt();
            boolean proximity = in.readBoolean(); in.readBoolean(); in.readBoolean(); // transitive, stereo
            if (in.readBoolean()) codec(in);
            in.readInt(); // weight
            activations.add(new Activation(activationId(name), name, List.copyOf(distances), defaultDistance, proximity));
        }
        return new Config(serverId, sampleRate, mtu, codec, encryption, key, List.copyOf(activations));
    }

    public static List<PlayerInfo> readPlayerList(byte[] payload) throws IOException {
        var in = body(payload);
        int count = count(in, Short.MAX_VALUE);
        List<PlayerInfo> players = new ArrayList<>(count);
        for (int i = 0; i < count; i++) players.add(playerInfo(in));
        return players;
    }

    public static PlayerInfo readPlayerInfo(byte[] payload) throws IOException { return playerInfo(body(payload)); }

    /** {@code PLAYER_DISCONNECT}, {@code SOURCE_AUDIO_END}: the first field is a UUID. */
    public static UUID readUuid(byte[] payload) throws IOException { return uuid(body(payload)); }

    public static Source readSource(byte[] payload) throws IOException {
        var in = body(payload);
        String type = utf(in, 16);
        utf(in, 128); // addon id
        UUID id = uuid(in);
        String name = in.readBoolean() ? utf(in, 256) : "";
        int state = in.readByte();
        if (in.readBoolean()) codec(in);
        in.readBoolean(); uuid(in); in.readBoolean(); in.readInt(); // stereo, line, icon visible, angle
        UUID player = null;
        if (type.equals("PLAYER")) player = playerInfo(in).id();
        else if (type.equals("DIRECT") && in.readBoolean()) {
            // broadcast source (e.g. a pv-addon-groups group): the speaking player is the sender
            player = uuid(in); name = utf(in, 64);
            int properties = count(in, 100);
            for (int p = 0; p < properties; p++) { in.readUTF(); in.readUTF(); in.readUTF(); }
        }
        return new Source(id, type, name, state, player);
    }

    /** Id of an activation: {@code UUID.nameUUIDFromBytes(name + "_activation")}. */
    public static UUID activationId(String name) { return UUID.nameUUIDFromBytes((name + "_activation").getBytes(java.nio.charset.StandardCharsets.UTF_8)); }

    private static PlayerInfo playerInfo(DataInputStream in) throws IOException {
        return new PlayerInfo(uuid(in), utf(in, 64), in.readBoolean(), in.readBoolean(), in.readBoolean());
    }

    private static String codec(DataInputStream in) throws IOException {
        String name = utf(in, 64);
        int params = count(in, 128);
        for (int i = 0; i < params; i++) { utf(in, 256); utf(in, 256); }
        return name;
    }

    private static void skipSourceLine(DataInputStream in) throws IOException {
        utf(in, 64); utf(in, 256); utf(in, 256); in.readDouble(); in.readInt(); // name, translation, icon, volume, weight
        if (in.readBoolean()) {
            int players = count(in, Short.MAX_VALUE);
            for (int i = 0; i < players; i++) {
                uuid(in); utf(in, 64);
                int properties = count(in, 100);
                for (int p = 0; p < properties; p++) { in.readUTF(); in.readUTF(); in.readUTF(); }
            }
        }
    }

    // --- UDP ---

    static byte[] datagram(int type, UUID secret, Writer body) {
        return write(out -> { out.writeInt(MAGIC); out.writeByte(type); uuid(out, secret); out.writeLong(System.currentTimeMillis()); body.write(out); });
    }

    /** Ping; while not connected with the server's address (as the real client does), as answer without. */
    public static byte[] ping(UUID secret, String ip, int port) {
        return datagram(PING, secret, out -> { out.writeLong(System.currentTimeMillis()); if (ip != null && port > 0) { out.writeUTF(ip); out.writeShort(port); } });
    }

    public static byte[] playerAudio(UUID secret, long sequence, byte[] data, UUID activation, int distance) {
        return datagram(PLAYER_AUDIO, secret, out -> {
            out.writeLong(sequence); bytes(out, data); uuid(out, activation); out.writeShort(distance); out.writeBoolean(false);
        });
    }

    /** The header of a datagram, or {@code null} for anything that is not Plasmo Voice. */
    public static Datagram readDatagram(byte[] datagram) {
        try {
            var in = new DataInputStream(new ByteArrayInputStream(datagram));
            if (in.readInt() != MAGIC) return null;
            int type = in.readUnsignedByte(); UUID secret = uuid(in); in.readLong();
            return new Datagram(type, secret, in);
        } catch (IOException tooShort) { return null; }
    }

    public static SourceAudio readSourceAudio(DataInputStream in) throws IOException {
        long sequence = in.readLong(); byte[] data = bytes(in, MAX_DATA);
        return new SourceAudio(sequence, data, uuid(in), in.readByte(), in.readShort());
    }

    // --- encryption ---

    public static KeyPair rsaKeyPair() throws GeneralSecurityException {
        var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        return generator.generateKeyPair();
    }

    /** The AES key from the config, encrypted by the server with our RSA public key. */
    public static SecretKeySpec aesKey(PrivateKey privateKey, byte[] encrypted) throws GeneralSecurityException {
        var cipher = Cipher.getInstance("RSA"); cipher.init(Cipher.DECRYPT_MODE, privateKey);
        return new SecretKeySpec(cipher.doFinal(encrypted), "AES");
    }

    public static byte[] encrypt(SecretKeySpec key, byte[] data) throws GeneralSecurityException {
        byte[] iv = new byte[16]; RANDOM.nextBytes(iv);
        var cipher = Cipher.getInstance(AES); cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(iv));
        byte[] sealed = cipher.doFinal(data), out = Arrays.copyOf(iv, 16 + sealed.length);
        System.arraycopy(sealed, 0, out, 16, sealed.length);
        return out;
    }

    public static byte[] decrypt(SecretKeySpec key, byte[] data) throws GeneralSecurityException {
        if (data.length < 32) throw new GeneralSecurityException("Verschlüsselte Daten zu kurz");
        var cipher = Cipher.getInstance(AES); cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(data, 0, 16));
        return cipher.doFinal(data, 16, data.length - 16);
    }

    // --- primitives ---

    static void uuid(DataOutputStream out, UUID id) throws IOException { out.writeLong(id.getMostSignificantBits()); out.writeLong(id.getLeastSignificantBits()); }

    static UUID uuid(DataInputStream in) throws IOException { return new UUID(in.readLong(), in.readLong()); }

    static void bytes(DataOutputStream out, byte[] bytes) throws IOException { out.writeInt(bytes.length); out.write(bytes); }

    static byte[] bytes(DataInputStream in, int max) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > max) throw new IOException("Ungültige Länge " + length);
        byte[] bytes = new byte[length]; in.readFully(bytes);
        return bytes;
    }

    private static int count(DataInputStream in, int max) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > max) throw new IOException("Ungültige Anzahl " + count);
        return count;
    }

    private static String utf(DataInputStream in, int max) throws IOException {
        String value = in.readUTF();
        if (value.length() > max) throw new IOException("Text zu lang");
        return value;
    }
}
