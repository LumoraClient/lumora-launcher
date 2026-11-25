package dev.lumora.launcher.core.platform;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * CurseForge's file fingerprint: 32-bit MurmurHash2 (seed 1) over the file's bytes without the whitespace bytes
 * 9, 10, 13 and 32. The API identifies installed files by it (POST /v1/fingerprints).
 */
public final class CurseForgeFingerprint {
    private static final long MAX_BYTES = 512L * 1024 * 1024;

    private CurseForgeFingerprint() {
    }

    public static long of(Path file) throws IOException {
        if (Files.size(file) > MAX_BYTES) {
            throw new IOException("file too large for a fingerprint: " + file.getFileName());
        }
        ByteArrayOutputStream normalized = new ByteArrayOutputStream((int) Math.min(Files.size(file), Integer.MAX_VALUE - 8));
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buf = new byte[64 * 1024];
            int r;
            while ((r = in.read(buf)) > 0) {
                for (int i = 0; i < r; i++) {
                    byte b = buf[i];
                    if (b != 9 && b != 10 && b != 13 && b != 32) {
                        normalized.write(b);
                    }
                }
            }
        }
        return murmur2(normalized.toByteArray());
    }

    /** MurmurHash2, 32 bit, seed 1, as an unsigned value. */
    public static long of(byte[] data) {
        ByteArrayOutputStream normalized = new ByteArrayOutputStream(data.length);
        for (byte b : data) {
            if (b != 9 && b != 10 && b != 13 && b != 32) {
                normalized.write(b);
            }
        }
        return murmur2(normalized.toByteArray());
    }

    @SuppressWarnings("fallthrough")
    static long murmur2(byte[] d) {
        final int m = 0x5bd1e995;
        final int r = 24;
        int len = d.length;
        int h = 1 ^ len;
        int i = 0;
        while (len - i >= 4) {
            int k = (d[i] & 0xff) | (d[i + 1] & 0xff) << 8 | (d[i + 2] & 0xff) << 16 | (d[i + 3] & 0xff) << 24;
            k *= m;
            k ^= k >>> r;
            k *= m;
            h *= m;
            h ^= k;
            i += 4;
        }
        switch (len - i) {
            case 3:
                h ^= (d[i + 2] & 0xff) << 16;
                // fall through
            case 2:
                h ^= (d[i + 1] & 0xff) << 8;
                // fall through
            case 1:
                h ^= d[i] & 0xff;
                h *= m;
                break;
            default:
                break;
        }
        h ^= h >>> 13;
        h *= m;
        h ^= h >>> 15;
        return h & 0xffffffffL;
    }
}
