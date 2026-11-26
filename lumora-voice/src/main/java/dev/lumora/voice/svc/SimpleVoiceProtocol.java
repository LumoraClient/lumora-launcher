package dev.lumora.voice.svc;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.UUID;

/**
 * Unabhängige Implementierung des UDP-Formats von Simple Voice Chat 2.6.x (Kompatibilitätsversion 20), nur für
 * die Zusammenarbeit mit SVC-Servern. Client → Server: {@code 0xFF, Spieler-UUID, VarInt-Länge, Nutzlast};
 * Server → Client: {@code 0xFF, VarInt-Länge, Nutzlast}. Nutzlast: 12-Byte-IV + AES-128-GCM über
 * {@code Pakettyp, Inhalt}. Zahlen sind Big-Endian, Zeichenketten VarInt-Länge + UTF-8.
 */
public final class SimpleVoiceProtocol {
    public static final int COMPATIBILITY_VERSION = 20, DEFAULT_PORT = 24454, MAX_PACKET = 2048, MAX_OPUS = 1275, SECRET_BYTES = 16;
    public static final int MIC = 1, PLAYER_SOUND = 2, GROUP_SOUND = 3, LOCATION_SOUND = 4, AUTHENTICATE = 5, AUTHENTICATE_ACK = 6,
            PING = 7, KEEP_ALIVE = 8, CONNECTION_CHECK = 9, CONNECTION_CHECK_ACK = 10;
    private static final int MAGIC = 0xFF, IV_BYTES = 12, TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();
    @FunctionalInterface public interface Writer { void write(DataOutputStream out) throws IOException; }
    /** Entschlüsseltes Paket: Typ und Inhalt ohne Typbyte. */
    public record Message(int type, byte[] body) {}
    /** Sprachpaket eines anderen Spielers; {@code distance} ist bei Gruppen {@code -1}. */
    public record Sound(int type, UUID channel, UUID sender, byte[] opus, long sequence, float distance, boolean whispering) {}

    private final UUID player;
    private final byte[] secret;
    private final SecretKeySpec key;
    public SimpleVoiceProtocol(UUID player, byte[] secret) {
        if (secret.length != SECRET_BYTES) throw new IllegalArgumentException("Simple-Voice-Chat-Schlüssel muss 16 Bytes haben");
        this.player = player; this.secret = secret.clone(); key = new SecretKeySpec(this.secret, "AES");
    }

    public byte[] authenticate() throws IOException, GeneralSecurityException { return client(AUTHENTICATE, out -> { uuid(out, player); out.write(secret); }); }
    public byte[] connectionCheck() throws IOException, GeneralSecurityException { return client(CONNECTION_CHECK, out -> {}); }
    public byte[] keepAlive() throws IOException, GeneralSecurityException { return client(KEEP_ALIVE, out -> {}); }
    /** Ping-Antwort: der Server erwartet den Inhalt unverändert zurück. */
    public byte[] pong(byte[] body) throws IOException, GeneralSecurityException { return client(PING, out -> out.write(body)); }
    public byte[] mic(byte[] opus, long sequence, boolean whispering) throws IOException, GeneralSecurityException {
        if (opus.length > MAX_OPUS) throw new IOException("Opus-Frame zu groß");
        return client(MIC, out -> { bytes(out, opus); out.writeLong(sequence); out.writeBoolean(whispering); });
    }

    /** Baut ein Datagramm an den Server. */
    public byte[] client(int type, Writer body) throws IOException, GeneralSecurityException {
        byte[] sealed = seal(key, type, body);
        var bytes = new ByteArrayOutputStream(sealed.length + 24); var out = new DataOutputStream(bytes);
        out.writeByte(MAGIC); uuid(out, player); bytes(out, sealed);
        return bytes.toByteArray();
    }
    /** Liest ein Datagramm vom Server. */
    public Message server(byte[] datagram) throws IOException, GeneralSecurityException {
        var in = new DataInputStream(new ByteArrayInputStream(datagram));
        if (in.readUnsignedByte() != MAGIC) throw new IOException("Kein Simple-Voice-Chat-Paket");
        return open(key, bytes(in, MAX_PACKET));
    }

    /** Wertet Spieler-, Gruppen- und Positionsklänge aus; {@code null} für andere Pakettypen. */
    public static Sound sound(Message message) throws IOException {
        int type = message.type();
        if (type != PLAYER_SOUND && type != GROUP_SOUND && type != LOCATION_SOUND) return null;
        var in = new DataInputStream(new ByteArrayInputStream(message.body()));
        UUID channel = uuid(in), sender = uuid(in);
        if (type == LOCATION_SOUND) { in.readDouble(); in.readDouble(); in.readDouble(); }
        byte[] opus = bytes(in, MAX_OPUS);
        long sequence = in.readLong();
        float distance = type == GROUP_SOUND ? -1 : in.readFloat();
        int flags = in.readUnsignedByte();
        return new Sound(type, channel, sender, opus, sequence, distance, type == PLAYER_SOUND && (flags & 1) != 0);
    }

    static byte[] seal(SecretKeySpec key, int type, Writer body) throws IOException, GeneralSecurityException {
        var plain = new ByteArrayOutputStream(); var out = new DataOutputStream(plain);
        out.writeByte(type); body.write(out);
        byte[] iv = new byte[IV_BYTES]; RANDOM.nextBytes(iv);
        var cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
        byte[] encrypted = cipher.doFinal(plain.toByteArray());
        byte[] sealed = Arrays.copyOf(iv, IV_BYTES + encrypted.length); System.arraycopy(encrypted, 0, sealed, IV_BYTES, encrypted.length);
        return sealed;
    }
    static Message open(SecretKeySpec key, byte[] sealed) throws IOException, GeneralSecurityException {
        if (sealed.length < IV_BYTES + TAG_BITS / 8 + 1) throw new IOException("Simple-Voice-Chat-Paket zu kurz");
        var cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES));
        byte[] plain = cipher.doFinal(sealed, IV_BYTES, sealed.length - IV_BYTES);
        if (plain.length < 1) throw new IOException("Leeres Simple-Voice-Chat-Paket");
        return new Message(plain[0] & 0xFF, Arrays.copyOfRange(plain, 1, plain.length));
    }

    public static void uuid(DataOutputStream out, UUID id) throws IOException { out.writeLong(id.getMostSignificantBits()); out.writeLong(id.getLeastSignificantBits()); }
    public static UUID uuid(DataInputStream in) throws IOException { return new UUID(in.readLong(), in.readLong()); }
    public static void varInt(DataOutputStream out, int value) throws IOException {
        while ((value & ~0x7F) != 0) { out.writeByte((value & 0x7F) | 0x80); value >>>= 7; }
        out.writeByte(value);
    }
    public static int varInt(DataInputStream in) throws IOException {
        int value = 0;
        for (int shift = 0; shift < 35; shift += 7) { int b = in.readUnsignedByte(); value |= (b & 0x7F) << shift; if ((b & 0x80) == 0) return value; }
        throw new IOException("VarInt zu lang");
    }
    public static void bytes(DataOutputStream out, byte[] data) throws IOException { varInt(out, data.length); out.write(data); }
    public static byte[] bytes(DataInputStream in, int max) throws IOException {
        int length = varInt(in);
        if (length < 0 || length > max) throw new IOException("Simple-Voice-Chat-Feld zu groß (" + length + ")");
        byte[] data = in.readNBytes(length);
        if (data.length != length) throw new EOFException("Simple-Voice-Chat-Paket unvollständig");
        return data;
    }
    public static String utf(DataInputStream in, int maxChars) throws IOException { return new String(bytes(in, maxChars * 3), StandardCharsets.UTF_8); }
    /** Sprachserver-Adresse wie bei Simple Voice Chat: leer → Spielserver, Zahl → anderer Port, sonst {@code host[:port]}. */
    public static String[] voiceAddress(String voiceHost, String serverHost, int port) {
        String host = serverHost; int resolved = port;
        String value = voiceHost == null ? "" : voiceHost.trim();
        if (!value.isEmpty()) {
            try { int parsed = Integer.parseInt(value); if (parsed > 0 && parsed <= 65535) resolved = parsed; }
            catch (NumberFormatException notPort) {
                try {
                    var uri = new java.net.URI("voicechat://" + value);
                    if (uri.getHost() != null) host = uri.getHost();
                    if (uri.getPort() > 0) resolved = uri.getPort();
                } catch (java.net.URISyntaxException ignored) { /* Spielserver verwenden. */ }
            }
        }
        return new String[]{host, Integer.toString(resolved)};
    }
}
