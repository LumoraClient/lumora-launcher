package dev.lumora.launcher.core.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountStoreTest {
    @TempDir
    Path tmp;

    @AfterEach
    void clear() {
        dev.lumora.launcher.core.util.Redactor.clearSecrets();
    }

    static MinecraftSession session(String name, String uuid) {
        return new MinecraftSession(name, uuid, "1", "token-" + name, Instant.now().plusSeconds(3600), "refresh-" + name);
    }

    static final String A = "0123456789abcdef0123456789abcdef";
    static final String B = "fedcba9876543210fedcba9876543210";

    @Test
    void addsSwitchesAndRemovesAccounts() throws IOException {
        AccountStore s = new AccountStore(tmp, false);
        assertNull(s.active());
        s.save(session("Alex", A));
        s.save(session("Steve", B));
        assertEquals(List.of("Alex", "Steve"), s.list().stream().map(AccountStore.Account::username).toList());
        assertEquals("Steve", s.active().username(), "the account signed in last is active");
        s.setActive(A);
        assertEquals("token-Alex", s.active().accessToken());
        // updating a session keeps one entry per account
        s.save(new MinecraftSession("Alex2", A, "1", "token-new", Instant.now().plusSeconds(3600), "r"));
        assertEquals(2, s.list().size());
        assertEquals("Alex2", s.activeAccount().orElseThrow().username());
        s.remove(A);
        assertEquals("Steve", s.active().username(), "another account takes over");
        assertThrows(IOException.class, () -> s.setActive("not-a-uuid"));
        String index = Files.readString(tmp.resolve("accounts.json"));
        assertFalse(index.contains("token"), "the index holds no secrets");
    }

    @Test
    void takesOverTheSingleSessionOfOlderVersions() throws IOException {
        new SessionStore(tmp.resolve("session.bin"), false).save(session("Legacy", A));
        AccountStore s = new AccountStore(tmp, false);
        assertEquals("Legacy", s.active().username());
        assertTrue(Files.notExists(tmp.resolve("session.bin")));
        assertEquals(1, s.list().size());
    }
}
