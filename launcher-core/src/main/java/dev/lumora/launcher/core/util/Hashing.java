package dev.lumora.launcher.core.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

public final class Hashing {
    public enum Algorithm {
        SHA1("SHA-1", 40), SHA256("SHA-256", 64), SHA512("SHA-512", 128);

        public final String jcaName;
        public final int hexLength;

        Algorithm(String jcaName, int hexLength) {
            this.jcaName = jcaName;
            this.hexLength = hexLength;
        }

        public MessageDigest newDigest() {
            try {
                return MessageDigest.getInstance(jcaName);
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(jcaName + " not available", e);
            }
        }
    }

    /** An expected hash. Both algorithm and value are mandatory: unverified downloads are not allowed. */
    public record Expected(Algorithm algorithm, String hex) {
        public Expected {
            if (algorithm == null || hex == null) {
                throw new IllegalArgumentException("hash algorithm and value are required");
            }
            hex = hex.toLowerCase(Locale.ROOT);
            if (hex.length() != algorithm.hexLength || !hex.chars().allMatch(c -> Character.digit(c, 16) >= 0)) {
                throw new IllegalArgumentException("Malformed " + algorithm + " hash: " + hex);
            }
        }

        public static Expected sha1(String hex) {
            return new Expected(Algorithm.SHA1, hex);
        }

        public static Expected sha512(String hex) {
            return new Expected(Algorithm.SHA512, hex);
        }

        @Override
        public String toString() {
            return algorithm + ":" + hex;
        }
    }

    private Hashing() {
    }

    public static String hash(Path file, Algorithm algorithm) throws IOException {
        MessageDigest digest = algorithm.newDigest();
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                digest.update(buf, 0, n);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    public static String hash(byte[] data, Algorithm algorithm) {
        return HexFormat.of().formatHex(algorithm.newDigest().digest(data));
    }

    public static boolean matches(Path file, Expected expected) throws IOException {
        return Files.isRegularFile(file) && hash(file, expected.algorithm()).equals(expected.hex());
    }
}
