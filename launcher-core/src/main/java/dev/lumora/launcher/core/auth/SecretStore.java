package dev.lumora.launcher.core.auth;

import com.sun.jna.platform.win32.Crypt32Util;
import dev.lumora.launcher.core.util.FileUtil;
import dev.lumora.launcher.core.util.Log;
import dev.lumora.launcher.core.util.OsInfo;
import dev.lumora.launcher.core.util.Redactor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Arrays;

/**
 * One secret the player entered (e.g. their own CurseForge API key), stored like the Microsoft sessions: encrypted with
 * DPAPI for the current Windows user, on other systems a file only the owner can read. The value is registered with
 * {@link Redactor} on load and save, so it never reaches a log. Nothing is built in: without an entry there is no key.
 */
public final class SecretStore {
    private static final byte[] MAGIC_DPAPI = {'L', 'M', 'S', '1'};
    private static final byte[] MAGIC_PLAIN = {'L', 'M', 'Q', '1'};

    private final Path file;
    private final boolean dpapi;
    private final byte[] entropy;

    public SecretStore(Path file, String purpose, OsInfo os) {
        this(file, purpose, os.name() == OsInfo.Name.WINDOWS);
    }

    SecretStore(Path file, String purpose, boolean dpapi) {
        this.file = file;
        this.dpapi = dpapi;
        this.entropy = ("dev.lumora.launcher." + purpose).getBytes(StandardCharsets.UTF_8);
    }

    public boolean present() {
        return Files.isRegularFile(file);
    }

    /** Saves {@code secret}; blank removes the stored one. */
    public void save(String secret) throws IOException {
        if (secret == null || secret.isBlank()) {
            clear();
            return;
        }
        Redactor.registerSecret(secret.strip());
        byte[] plain = secret.strip().getBytes(StandardCharsets.UTF_8);
        byte[] out;
        try {
            out = dpapi ? concat(MAGIC_DPAPI, Crypt32Util.cryptProtectData(plain, entropy, 0, "Lumora secret", null))
                    : concat(MAGIC_PLAIN, plain);
        } finally {
            Arrays.fill(plain, (byte) 0);
        }
        FileUtil.writeAtomically(file, out);
        if (!dpapi) {
            try {
                Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException e) {
                Log.warn("File system does not support POSIX permissions; secret file is not access-restricted");
            }
        }
    }

    /** The stored secret, or null (none, or not readable by this user). */
    public String load() {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            byte[] data = Files.readAllBytes(file);
            if (data.length < 5) {
                return null;
            }
            byte[] magic = Arrays.copyOf(data, 4);
            byte[] payload = Arrays.copyOfRange(data, 4, data.length);
            byte[] plain;
            if (Arrays.equals(magic, MAGIC_DPAPI) && dpapi) {
                plain = Crypt32Util.cryptUnprotectData(payload, entropy, 0, null);
            } else if (Arrays.equals(magic, MAGIC_PLAIN) && !dpapi) {
                plain = payload;
            } else {
                return null;
            }
            String s = new String(plain, StandardCharsets.UTF_8);
            Arrays.fill(plain, (byte) 0);
            Redactor.registerSecret(s);
            return s;
        } catch (IOException | RuntimeException e) {
            Log.warn("Stored secret " + file.getFileName() + " unreadable: " + e.getClass().getSimpleName());
            return null;
        }
    }

    public void clear() throws IOException {
        Files.deleteIfExists(file);
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] r = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }
}
