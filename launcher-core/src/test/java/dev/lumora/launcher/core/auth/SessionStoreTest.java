package dev.lumora.launcher.core.auth;

import dev.lumora.launcher.core.util.OsInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class SessionStoreTest {
    @TempDir
    Path tmp;

    static final MinecraftSession SESSION = new MinecraftSession("Alex", "uuid1", "xuid1", "mc-access-token-0001",
            Instant.ofEpochSecond(2_000_000_000L), "ms-refresh-token-0001");

    @Test
    void plainRoundTrip() throws Exception {
        SessionStore s = new SessionStore(tmp.resolve("session.bin"), false);
        s.save(SESSION);
        MinecraftSession loaded = s.load();
        assertEquals(SESSION, loaded);
        s.clear();
        assertNull(s.load());
    }

    @Test
    void dpapiRoundTripAndCiphertextHidesTokens() throws Exception {
        assumeTrue(OsInfo.current().name() == OsInfo.Name.WINDOWS, "DPAPI is Windows-only");
        Path f = tmp.resolve("session.bin");
        SessionStore s = new SessionStore(f, true);
        s.save(SESSION);
        String raw = new String(Files.readAllBytes(f), StandardCharsets.ISO_8859_1);
        assertFalse(raw.contains("mc-access-token-0001"), "token must not be stored in clear text");
        assertFalse(raw.contains("ms-refresh-token-0001"));
        assertEquals(SESSION, s.load());
    }

    @Test
    void corruptOrForeignFileYieldsNoSession() throws Exception {
        Path f = tmp.resolve("session.bin");
        Files.write(f, new byte[]{'L', 'M', 'D', '1', 1, 2, 3});
        assertNull(new SessionStore(f, OsInfo.current()).load());
        Files.writeString(f, "garbage");
        assertNull(new SessionStore(f, false).load());
    }

    @Test
    void tokenValidityWindow() {
        Instant now = Instant.ofEpochSecond(1_000_000);
        var valid = new MinecraftSession("a", "b", "c", "t", now.plusSeconds(3600), null);
        var nearlyExpired = new MinecraftSession("a", "b", "c", "t", now.plusSeconds(60), null);
        assertTrue(valid.accessTokenValid(now));
        assertFalse(nearlyExpired.accessTokenValid(now), "tokens expiring within 5 minutes are refreshed first");
    }
}
