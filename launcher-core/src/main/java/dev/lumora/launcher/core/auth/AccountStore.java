package dev.lumora.launcher.core.auth;

import dev.lumora.launcher.core.util.FileUtil;
import dev.lumora.launcher.core.util.Json;
import dev.lumora.launcher.core.util.Log;
import dev.lumora.launcher.core.util.OsInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Several Microsoft/Minecraft accounts: each session (tokens) is encrypted on its own with DPAPI
 * (auth/accounts/&lt;uuid&gt;.bin, see {@link SessionStore}); auth/accounts.json only holds non-secret data - which
 * account is active, names, UUIDs and when they were added/used. The single-session file of older versions
 * (auth/session.bin) is taken over as the first account.
 */
public final class AccountStore {
    private static final Pattern UUID = Pattern.compile("^[0-9a-fA-F]{32}$|^[0-9a-fA-F-]{36}$");

    /** Non-secret account entry. */
    public record Account(String uuid, String username, Instant added, Instant lastUsed) {
    }

    static final class Index {
        String active;
        List<Entry> accounts = new ArrayList<>();
    }

    static final class Entry {
        String uuid;
        String username;
        long added;
        long lastUsed;
    }

    private final Path dir;
    private final Path indexFile;
    private final Path legacyFile;
    private final boolean dpapi;

    public AccountStore(Path authDir, OsInfo os) {
        this(authDir, os.name() == OsInfo.Name.WINDOWS);
    }

    AccountStore(Path authDir, boolean dpapi) {
        this.dir = authDir.resolve("accounts");
        this.indexFile = authDir.resolve("accounts.json");
        this.legacyFile = authDir.resolve("session.bin");
        this.dpapi = dpapi;
        migrateLegacy();
    }

    private SessionStore store(String uuid) throws IOException {
        if (uuid == null || !UUID.matcher(uuid).matches()) {
            throw new IOException("invalid account id");
        }
        return new SessionStore(FileUtil.resolveInside(dir, uuid.replace("-", "") + ".bin"), dpapi);
    }

    private synchronized Index index() {
        if (Files.isRegularFile(indexFile)) {
            try {
                Index i = Json.read(indexFile, Index.class);
                if (i != null && i.accounts != null) {
                    i.accounts.removeIf(e -> e == null || e.uuid == null || !UUID.matcher(e.uuid).matches());
                    return i;
                }
            } catch (IOException | RuntimeException e) {
                Log.warn("Account list unreadable, starting empty: " + e.getMessage());
            }
        }
        return new Index();
    }

    private synchronized void write(Index i) throws IOException {
        Json.write(indexFile, i);
    }

    private void migrateLegacy() {
        if (!Files.isRegularFile(legacyFile)) {
            return;
        }
        MinecraftSession s = new SessionStore(legacyFile, dpapi).load();
        try {
            if (s != null) {
                save(s);
                Log.info("Moved the stored session of " + s.username() + " into the account list");
            }
            Files.deleteIfExists(legacyFile);
        } catch (IOException e) {
            Log.warn("Could not migrate the stored session: " + e.getMessage());
        }
    }

    public synchronized List<Account> list() {
        List<Account> out = new ArrayList<>();
        for (Entry e : index().accounts) {
            out.add(new Account(e.uuid, e.username, Instant.ofEpochMilli(e.added), e.lastUsed == 0 ? null : Instant.ofEpochMilli(e.lastUsed)));
        }
        return out;
    }

    public synchronized Optional<Account> activeAccount() {
        String active = index().active;
        return list().stream().filter(a -> a.uuid().equals(active)).findFirst();
    }

    /** Session of the active account, or null (none, or not decryptable for this Windows user). */
    public synchronized MinecraftSession active() {
        Index i = index();
        if (i.active == null) {
            return null;
        }
        try {
            return store(i.active).load();
        } catch (IOException e) {
            return null;
        }
    }

    /** Adds or updates the account of this session and makes it the active one. */
    public synchronized void save(MinecraftSession s) throws IOException {
        Files.createDirectories(dir);
        store(s.uuid()).save(s);
        Index i = index();
        Entry e = i.accounts.stream().filter(x -> x.uuid.equalsIgnoreCase(s.uuid())).findFirst().orElse(null);
        long now = System.currentTimeMillis();
        if (e == null) {
            e = new Entry();
            e.uuid = s.uuid();
            e.added = now;
            i.accounts.add(e);
        }
        e.username = s.username();
        e.lastUsed = now;
        i.active = e.uuid;
        write(i);
    }

    public synchronized void setActive(String uuid) throws IOException {
        Index i = index();
        if (i.accounts.stream().noneMatch(e -> e.uuid.equals(uuid))) {
            throw new IOException("unknown account");
        }
        i.active = uuid;
        write(i);
    }

    /** Removes the account and its encrypted session; another account becomes active if there is one. */
    public synchronized void remove(String uuid) throws IOException {
        store(uuid).clear();
        Index i = index();
        i.accounts.removeIf(e -> e.uuid.equals(uuid));
        if (uuid.equals(i.active)) {
            i.active = i.accounts.isEmpty() ? null : i.accounts.getFirst().uuid;
        }
        write(i);
    }

    /** Signs out of the active account (removes it). */
    public synchronized void removeActive() throws IOException {
        String active = index().active;
        if (active != null) {
            remove(active);
        }
    }
}
