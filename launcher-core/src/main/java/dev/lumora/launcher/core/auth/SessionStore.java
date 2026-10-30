package dev.lumora.launcher.core.auth;

import com.sun.jna.platform.win32.Crypt32Util;
import dev.lumora.launcher.core.util.FileUtil;
import dev.lumora.launcher.core.util.Json;
import dev.lumora.launcher.core.util.Log;
import dev.lumora.launcher.core.util.OsInfo;
import dev.lumora.launcher.core.util.Redactor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.Arrays;

/**
 * Persists the signed-in session. On Windows the data is encrypted with DPAPI (CryptProtectData,
 * bound to the current Windows user), so the file is useless on another machine/account. On other
 * systems the file is restricted to the owner (0600); see docs/ARCHITECTURE.md for the trade-off.
 * No password is ever handled by the launcher: sign-in happens in Microsoft's own web page.
 */
public final class SessionStore {
    private static final byte[] MAGIC_DPAPI = {'L', 'M', 'D', '1'};
    private static final byte[] MAGIC_PLAIN = {'L', 'M', 'P', '1'};
    private static final byte[] ENTROPY = "dev.lumora.launcher.session".getBytes(StandardCharsets.UTF_8);

    /** JSON shape on disk (inside the encryption envelope). */
    static final class Stored {
        String username;
        String uuid;
        String xuid;
        String accessToken;
        long accessTokenExpiresAt;
        String msRefreshToken;
    }

    private final Path file;
    private final boolean useDpapi;

    public SessionStore(Path file, OsInfo os) {
        this(file, os.name() == OsInfo.Name.WINDOWS);
    }

    SessionStore(Path file, boolean useDpapi) {
        this.file = file;
        this.useDpapi = useDpapi;
    }

    public void save(MinecraftSession s) throws IOException {
        Stored st = new Stored();
        st.username = s.username();
        st.uuid = s.uuid();
        st.xuid = s.xuid();
        st.accessToken = s.accessToken();
        st.accessTokenExpiresAt = s.accessTokenExpiresAt() == null ? 0 : s.accessTokenExpiresAt().getEpochSecond();
        st.msRefreshToken = s.msRefreshToken();
        byte[] plain = Json.GSON.toJson(st).getBytes(StandardCharsets.UTF_8);
        byte[] out;
        try {
            if (useDpapi) {
                byte[] enc = Crypt32Util.cryptProtectData(plain, ENTROPY, 0, "Lumora session", null);
                out = concat(MAGIC_DPAPI, enc);
            } else {
                out = concat(MAGIC_PLAIN, plain);
            }
        } finally {
            Arrays.fill(plain, (byte) 0);
        }
        FileUtil.writeAtomically(file, out);
        if (!useDpapi) {
            try {
                Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException e) {
                Log.warn("File system does not support POSIX permissions; session file is not access-restricted");
            }
        }
    }

    /** Returns the stored session, or null if none exists or it cannot be decrypted (e.g. other user). */
    public MinecraftSession load() {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            byte[] data = Files.readAllBytes(file);
            if (data.length < 4) {
                throw new IOException("truncated");
            }
            byte[] magic = Arrays.copyOf(data, 4);
            byte[] payload = Arrays.copyOfRange(data, 4, data.length);
            byte[] plain;
            if (Arrays.equals(magic, MAGIC_DPAPI)) {
                plain = Crypt32Util.cryptUnprotectData(payload, ENTROPY, 0, null);
            } else if (Arrays.equals(magic, MAGIC_PLAIN) && !useDpapi) {
                plain = payload;
            } else {
                throw new IOException("unknown session file format");
            }
            Stored st = Json.GSON.fromJson(new String(plain, StandardCharsets.UTF_8), Stored.class);
            Arrays.fill(plain, (byte) 0);
            if (st == null || st.username == null || st.uuid == null) {
                throw new IOException("incomplete session");
            }
            Redactor.registerSecret(st.accessToken);
            Redactor.registerSecret(st.msRefreshToken);
            return new MinecraftSession(st.username, st.uuid, st.xuid, st.accessToken,
                    st.accessTokenExpiresAt == 0 ? null : Instant.ofEpochSecond(st.accessTokenExpiresAt), st.msRefreshToken);
        } catch (IOException | RuntimeException e) {
            Log.warn("Stored session unreadable, sign-in required: " + e.getMessage());
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
