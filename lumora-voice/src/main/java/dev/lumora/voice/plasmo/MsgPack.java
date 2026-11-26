package dev.lumora.voice.plasmo;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Minimal MessagePack for the group channel of pv-addon-groups (kotlinx-serialization-msgpack on the other side):
 * classes are maps with field-name keys, enums strings, byte arrays {@code bin}, sealed values a type-name string
 * followed by the value. Reading gives {@code Map<String,Object>}, {@code List<Object>}, {@code String}, {@code Long},
 * {@code Double}, {@code Boolean}, {@code byte[]} or {@code null}.
 */
final class MsgPack {
    private MsgPack() {}

    /** Builds a message; values are written in order. */
    static final class Writer {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final DataOutputStream out = new DataOutputStream(bytes);

        Writer map(int size) throws IOException {
            if (size < 16) out.writeByte(0x80 | size); else { out.writeByte(0xDE); out.writeShort(size); }
            return this;
        }

        Writer array(int size) throws IOException {
            if (size < 16) out.writeByte(0x90 | size); else { out.writeByte(0xDC); out.writeShort(size); }
            return this;
        }

        Writer string(String value) throws IOException {
            if (value == null) return nil();
            byte[] utf = value.getBytes(StandardCharsets.UTF_8);
            if (utf.length < 32) out.writeByte(0xA0 | utf.length);
            else if (utf.length < 256) { out.writeByte(0xD9); out.writeByte(utf.length); }
            else { out.writeByte(0xDA); out.writeShort(utf.length); }
            out.write(utf);
            return this;
        }

        Writer integer(long value) throws IOException {
            if (value >= 0 && value < 128) out.writeByte((int) value);
            else if (value >= -32 && value < 0) out.writeByte((int) value);
            else if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) { out.writeByte(0xD2); out.writeInt((int) value); }
            else { out.writeByte(0xD3); out.writeLong(value); }
            return this;
        }

        Writer bool(boolean value) throws IOException { out.writeByte(value ? 0xC3 : 0xC2); return this; }

        Writer nil() throws IOException { out.writeByte(0xC0); return this; }

        Writer bin(byte[] value) throws IOException {
            if (value.length < 256) { out.writeByte(0xC4); out.writeByte(value.length); } else { out.writeByte(0xC5); out.writeShort(value.length); }
            out.write(value);
            return this;
        }

        Writer uuid(UUID id) throws IOException {
            return bin(PlasmoVoiceProtocol.write(o -> PlasmoVoiceProtocol.uuid(o, id)));
        }

        byte[] bytes() { return bytes.toByteArray(); }
    }

    /** All top-level values (a sealed value is two: its type name and the value). */
    static List<Object> readAll(byte[] data) throws IOException {
        var in = new DataInputStream(new ByteArrayInputStream(data));
        List<Object> values = new ArrayList<>();
        while (in.available() > 0) values.add(read(in, 0));
        return values;
    }

    private static Object read(DataInputStream in, int depth) throws IOException {
        if (depth > 32) throw new IOException("MessagePack zu tief verschachtelt");
        int b = in.readUnsignedByte();
        if (b <= 0x7F) return (long) b;
        if (b >= 0xE0) return (long) (byte) b;
        if ((b & 0xF0) == 0x80) return map(in, b & 0x0F, depth);
        if ((b & 0xF0) == 0x90) return array(in, b & 0x0F, depth);
        if ((b & 0xE0) == 0xA0) return string(in, b & 0x1F);
        return switch (b) {
            case 0xC0 -> null;
            case 0xC2 -> false;
            case 0xC3 -> true;
            case 0xC4 -> raw(in, in.readUnsignedByte());
            case 0xC5 -> raw(in, in.readUnsignedShort());
            case 0xC6 -> raw(in, in.readInt());
            case 0xCA -> (double) in.readFloat();
            case 0xCB -> in.readDouble();
            case 0xCC -> (long) in.readUnsignedByte();
            case 0xCD -> (long) in.readUnsignedShort();
            case 0xCE -> in.readInt() & 0xFFFFFFFFL;
            case 0xCF -> in.readLong();
            case 0xD0 -> (long) in.readByte();
            case 0xD1 -> (long) in.readShort();
            case 0xD2 -> (long) in.readInt();
            case 0xD3 -> in.readLong();
            case 0xD9 -> string(in, in.readUnsignedByte());
            case 0xDA -> string(in, in.readUnsignedShort());
            case 0xDB -> string(in, in.readInt());
            case 0xDC -> array(in, in.readUnsignedShort(), depth);
            case 0xDD -> array(in, in.readInt(), depth);
            case 0xDE -> map(in, in.readUnsignedShort(), depth);
            case 0xDF -> map(in, in.readInt(), depth);
            default -> throw new IOException("Unbekannter MessagePack-Typ " + Integer.toHexString(b));
        };
    }

    private static byte[] raw(DataInputStream in, int length) throws IOException {
        if (length < 0 || length > 1 << 20) throw new IOException("Ungültige Länge");
        byte[] bytes = new byte[length]; in.readFully(bytes);
        return bytes;
    }

    private static String string(DataInputStream in, int length) throws IOException { return new String(raw(in, length), StandardCharsets.UTF_8); }

    private static List<Object> array(DataInputStream in, int size, int depth) throws IOException {
        if (size < 0 || size > 65536) throw new IOException("Liste zu groß");
        List<Object> list = new ArrayList<>(Math.min(size, 256));
        for (int i = 0; i < size; i++) list.add(read(in, depth + 1));
        return list;
    }

    private static Map<String, Object> map(DataInputStream in, int size, int depth) throws IOException {
        if (size < 0 || size > 65536) throw new IOException("Map zu groß");
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) { Object key = read(in, depth + 1); map.put(String.valueOf(key), read(in, depth + 1)); }
        return map;
    }

    // --- reading helpers for decoded values ---

    @SuppressWarnings("unchecked")
    static Map<String, Object> map(Object value) { return value instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of(); }

    @SuppressWarnings("unchecked")
    static List<Object> list(Object value) { return value instanceof List<?> l ? (List<Object>) l : List.of(); }

    static String text(Object value) { return value instanceof String s ? s : ""; }

    static boolean flag(Object value) { return Boolean.TRUE.equals(value); }

    static int number(Object value) { return value instanceof Number n ? n.intValue() : 0; }

    /** A UUID sent as 16 bytes (UUIDByteSerializer); {@code null} otherwise. */
    static UUID uuid(Object value) {
        if (!(value instanceof byte[] b) || b.length != 16) return null;
        long most = 0, least = 0;
        for (int i = 0; i < 8; i++) { most = most << 8 | (b[i] & 0xFF); least = least << 8 | (b[i + 8] & 0xFF); }
        return new UUID(most, least);
    }
}
