package dev.lumora.launcher.core.library;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Minimal reader for Minecraft's NBT format (level.dat, servers.dat). Compounds become {@code Map<String,Object>},
 * lists {@code List<Object>}, numbers boxed primitives, strings {@code String}; arrays are skipped (null).
 * Only for reading display data: depth, list lengths and file size are capped, so a broken or hostile file
 * cannot exhaust memory.
 */
public final class Nbt {
    static final long MAX_FILE_BYTES = 16L * 1024 * 1024;
    private static final int MAX_DEPTH = 64;
    private static final int MAX_LIST = 1_000_000;

    private Nbt() {
    }

    /** Reads a gzip-compressed (level.dat) or uncompressed (servers.dat) file; returns the root compound. */
    public static Map<String, Object> read(Path file) throws IOException {
        if (Files.size(file) > MAX_FILE_BYTES) {
            throw new IOException(file.getFileName() + " is too large");
        }
        byte[] head = new byte[2];
        try (InputStream raw = Files.newInputStream(file)) {
            if (raw.readNBytes(head, 0, 2) < 2) {
                throw new IOException(file.getFileName() + " is empty");
            }
        }
        boolean gzip = (head[0] & 0xFF) == 0x1F && (head[1] & 0xFF) == 0x8B;
        try (InputStream raw = new BufferedInputStream(Files.newInputStream(file));
             InputStream in = gzip ? new GZIPInputStream(raw) : raw) {
            return read(new DataInputStream(new LimitedInputStream(in, MAX_FILE_BYTES * 8)));
        }
    }

    static Map<String, Object> read(DataInputStream in) throws IOException {
        int type = in.readUnsignedByte();
        if (type != 10) {
            throw new IOException("NBT root is not a compound (" + type + ")");
        }
        in.readUTF();
        @SuppressWarnings("unchecked")
        Map<String, Object> root = (Map<String, Object>) payload(in, 10, 0);
        return root;
    }

    private static Object payload(DataInputStream in, int type, int depth) throws IOException {
        if (depth > MAX_DEPTH) {
            throw new IOException("NBT nested too deeply");
        }
        return switch (type) {
            case 1 -> in.readByte();
            case 2 -> in.readShort();
            case 3 -> in.readInt();
            case 4 -> in.readLong();
            case 5 -> in.readFloat();
            case 6 -> in.readDouble();
            case 7 -> skipArray(in, 1);
            case 8 -> in.readUTF();
            case 9 -> {
                int elementType = in.readUnsignedByte();
                int n = in.readInt();
                if (n < 0 || n > MAX_LIST) {
                    throw new IOException("invalid NBT list length " + n);
                }
                List<Object> list = new ArrayList<>(Math.min(n, 1024));
                for (int i = 0; i < n; i++) {
                    list.add(payload(in, elementType, depth + 1));
                }
                yield list;
            }
            case 10 -> {
                Map<String, Object> map = new LinkedHashMap<>();
                while (true) {
                    int t = in.readUnsignedByte();
                    if (t == 0) {
                        break;
                    }
                    String name = in.readUTF();
                    map.put(name, payload(in, t, depth + 1));
                }
                yield map;
            }
            case 11 -> skipArray(in, 4);
            case 12 -> skipArray(in, 8);
            case 0 -> null;
            default -> throw new IOException("unknown NBT tag " + type);
        };
    }

    private static Object skipArray(DataInputStream in, int elementBytes) throws IOException {
        int n = in.readInt();
        if (n < 0 || (long) n * elementBytes > MAX_FILE_BYTES * 8) {
            throw new IOException("invalid NBT array length " + n);
        }
        in.skipNBytes((long) n * elementBytes);
        return null;
    }

    /** Typed lookup of a path like "Data", "LevelName"; null if absent or of another type. */
    public static <T> T get(Map<String, Object> root, Class<T> type, String... path) {
        Object cur = root;
        for (String p : path) {
            if (!(cur instanceof Map<?, ?> m)) {
                return null;
            }
            cur = m.get(p);
        }
        return type.isInstance(cur) ? type.cast(cur) : null;
    }

    /** Caps the decompressed size (protection against gzip bombs). */
    private static final class LimitedInputStream extends java.io.FilterInputStream {
        private long left;

        LimitedInputStream(InputStream in, long limit) {
            super(in);
            this.left = limit;
        }

        @Override
        public int read() throws IOException {
            if (left <= 0) {
                throw new IOException("NBT data exceeds size limit");
            }
            int b = super.read();
            if (b >= 0) {
                left--;
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (left <= 0) {
                throw new IOException("NBT data exceeds size limit");
            }
            int n = super.read(b, off, (int) Math.min(len, left));
            if (n > 0) {
                left -= n;
            }
            return n;
        }
    }
}
