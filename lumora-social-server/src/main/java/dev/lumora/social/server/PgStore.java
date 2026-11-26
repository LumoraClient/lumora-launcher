package dev.lumora.social.server;

import dev.lumora.social.Rules;
import dev.lumora.social.model.ChatMessage;
import dev.lumora.social.model.Role;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

/**
 * PostgreSQL storage (see {@code db/V*.sql}). The server keeps working on its in-memory {@link State}; {@link #save}
 * compares it with what was written last and writes only the rows that changed, all in one transaction together with
 * the queued new, edited and deleted messages. If the database is unreachable, nothing is lost: the transaction is rolled
 * back, the queue stays and the next save (one second later) tries again on a fresh connection.
 *
 * <p>Connection settings come from the environment (never from the code): {@code LUMORA_DB_URL}
 * ({@code jdbc:postgresql://host/db}), {@code LUMORA_DB_USER}, {@code LUMORA_DB_PASSWORD}.
 */
final class PgStore implements Persistence {
    /** Migrations in order; each runs once, inside a transaction. */
    private static final String[] MIGRATIONS = {"V1__init.sql"};

    private final String url;
    private final Properties props;
    private Connection db;
    /** Rows as written by the last successful save, per table: key -> values. */
    private Map<String, Map<String, List<Object>>> written = new HashMap<>();
    /** Conversations known to exist in the database. */
    private final Set<String> conversations = new HashSet<>();
    private final List<ChatMessage> pendingInserts = new ArrayList<>();
    private final Map<Long, ChatMessage> pendingUpdates = new LinkedHashMap<>();
    private final Set<String> pendingConvDeletes = new HashSet<>();
    private final List<Object[]> pendingAudits = new ArrayList<>();

    PgStore(String url, String user, String password) throws IOException {
        this.url = Objects.requireNonNull(url, "database URL");
        this.props = new Properties();
        if (user != null) {
            props.setProperty("user", user);
        }
        if (password != null) {
            props.setProperty("password", password);
        }
        props.setProperty("ApplicationName", "lumora-backend");
        try {
            connection();
            migrate();
        } catch (SQLException e) {
            throw new IOException("Database: " + e.getMessage(), e);
        }
    }

    /** From the environment; null if {@code LUMORA_DB_URL} is not set. */
    static PgStore fromEnvironment() throws IOException {
        String url = System.getenv("LUMORA_DB_URL");
        if (url == null || url.isBlank()) {
            return null;
        }
        return new PgStore(url.strip(), System.getenv("LUMORA_DB_USER"), System.getenv("LUMORA_DB_PASSWORD"));
    }

    private Connection connection() throws SQLException {
        if (db != null) {
            try {
                if (db.isValid(3)) {
                    return db;
                }
            } catch (SQLException ignored) {
                // reconnect below
            }
            try {
                db.close();
            } catch (SQLException ignored) {
                // already broken
            }
        }
        db = DriverManager.getConnection(url, props);
        db.setAutoCommit(false);
        return db;
    }

    private void migrate() throws SQLException, IOException {
        Connection c = connection();
        try (Statement st = c.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS schema_version (version INTEGER PRIMARY KEY, applied BIGINT NOT NULL)");
            c.commit();
        }
        int current = 0;
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT COALESCE(MAX(version), 0) FROM schema_version")) {
            if (rs.next()) {
                current = rs.getInt(1);
            }
        }
        for (int i = current; i < MIGRATIONS.length; i++) {
            String sql = resource("/db/" + MIGRATIONS[i]);
            try (Statement st = c.createStatement()) {
                for (String part : statements(sql)) {
                    st.execute(part);
                }
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO schema_version (version, applied) VALUES (?, ?)")) {
                    ps.setInt(1, i + 1);
                    ps.setLong(2, System.currentTimeMillis());
                    ps.executeUpdate();
                }
                c.commit();
                Log.info("Database schema migrated to version " + (i + 1));
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        }
    }

    /** Splits a migration at ";" ending a line; "--" comments are dropped. */
    static List<String> statements(String sql) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String line : sql.split("\n")) {
            String l = line.strip();
            if (l.isEmpty() || l.startsWith("--")) {
                continue;
            }
            int comment = l.indexOf("--");
            if (comment >= 0) {
                l = l.substring(0, comment).strip();
            }
            cur.append(l).append('\n');
            if (l.endsWith(";")) {
                String s = cur.toString().strip();
                out.add(s.substring(0, s.length() - 1));
                cur.setLength(0);
            }
        }
        if (!cur.toString().isBlank()) {
            out.add(cur.toString().strip());
        }
        return out;
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = PgStore.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("missing " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // ---------------------------------------------------------------- load

    @Override
    public synchronized State load() throws IOException {
        try {
            Connection c = connection();
            State s = new State();
            try (Statement st = c.createStatement()) {
                try (ResultSet rs = st.executeQuery("SELECT uuid, name, first_seen, last_seen FROM users")) {
                    while (rs.next()) {
                        State.User u = new State.User(rs.getString(1).strip(), rs.getString(2));
                        u.firstSeen = rs.getLong(3);
                        u.lastSeen = rs.getLong(4);
                        s.users.put(u.uuid, u);
                    }
                }
                try (ResultSet rs = st.executeQuery("SELECT user_a, user_b, since FROM friendships")) {
                    while (rs.next()) {
                        String a = rs.getString(1).strip();
                        String b = rs.getString(2).strip();
                        s.friends.computeIfAbsent(a, k -> new HashMap<>()).put(b, rs.getLong(3));
                        s.friends.computeIfAbsent(b, k -> new HashMap<>()).put(a, rs.getLong(3));
                    }
                }
                pairs(st, "SELECT from_uuid, to_uuid, created FROM friend_requests", s.requests);
                pairs(st, "SELECT blocker, blocked, created FROM blocked_users", s.blocks);
                try (ResultSet rs = st.executeQuery("SELECT id, name, description, icon, color, owner, created FROM user_groups")) {
                    while (rs.next()) {
                        State.GroupRec g = new State.GroupRec(rs.getString(1));
                        g.name = rs.getString(2);
                        g.description = rs.getString(3);
                        g.icon = rs.getString(4);
                        g.color = rs.getInt(5);
                        g.owner = rs.getString(6).strip();
                        g.created = rs.getLong(7);
                        s.groups.put(g.id, g);
                    }
                }
                try (ResultSet rs = st.executeQuery(
                        "SELECT group_id, role_id, name, icon, color, manage FROM group_roles ORDER BY group_id, position")) {
                    while (rs.next()) {
                        State.GroupRec g = s.groups.get(rs.getString(1));
                        if (g != null) {
                            Role r = new Role(rs.getString(2), rs.getString(3), rs.getString(4), rs.getInt(5), rs.getBoolean(6));
                            g.roles.put(r.id, r);
                        }
                    }
                }
                try (ResultSet rs = st.executeQuery("SELECT group_id, uuid, role, joined FROM group_members ORDER BY group_id, position")) {
                    while (rs.next()) {
                        State.GroupRec g = s.groups.get(rs.getString(1));
                        if (g != null) {
                            g.members.put(rs.getString(2).strip(), new State.MemberRec(rs.getString(3), rs.getLong(4)));
                        }
                    }
                }
                try (ResultSet rs = st.executeQuery("SELECT id FROM conversations")) {
                    while (rs.next()) {
                        conversations.add(rs.getString(1));
                    }
                }
                try (ResultSet rs = st.executeQuery("SELECT conv, uuid, last_read, muted FROM conversation_members")) {
                    while (rs.next()) {
                        String conv = rs.getString(1);
                        String uuid = rs.getString(2).strip();
                        if (rs.getLong(3) > 0) {
                            s.reads.computeIfAbsent(uuid, k -> new HashMap<>()).put(conv, rs.getLong(3));
                        }
                        if (rs.getBoolean(4)) {
                            s.muted.computeIfAbsent(uuid, k -> new HashSet<>()).add(conv);
                        }
                    }
                }
                try (ResultSet rs = st.executeQuery("SELECT uuid, request_policy, presence_mode, updated, show_badge FROM user_settings")) {
                    while (rs.next()) {
                        State.Settings x = new State.Settings();
                        x.requestPolicy = State.requestPolicy(rs.getString(2));
                        x.presenceMode = State.presenceMode(rs.getString(3));
                        x.updated = rs.getLong(4);
                        x.showBadge = rs.getBoolean(5);
                        s.settings.put(rs.getString(1).strip(), x);
                    }
                }
                try (ResultSet rs = st.executeQuery("SELECT COALESCE(MAX(id), 0) FROM messages")) {
                    if (rs.next()) {
                        s.nextMessageId = Math.max(1, rs.getLong(1) + 1);
                    }
                }
            }
            // the newest messages of every conversation (older ones stay in the database)
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id, conv, sender, body, sent_at, edited_at, deleted FROM (SELECT m.*, row_number() OVER "
                            + "(PARTITION BY conv ORDER BY id DESC) AS rn FROM messages m) t WHERE rn <= ? ORDER BY conv, id")) {
                ps.setInt(1, Rules.HISTORY_KEPT);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        long edited = rs.getLong(6);
                        ChatMessage m = new ChatMessage(rs.getLong(1), rs.getString(2), rs.getString(3).strip(), rs.getString(4),
                                rs.getLong(5), rs.wasNull() ? 0 : edited, rs.getBoolean(7));
                        s.messages.computeIfAbsent(m.conv, k -> new ArrayDeque<>()).addLast(m);
                    }
                }
            }
            c.commit();
            written = snapshot(s);
            return s;
        } catch (SQLException e) {
            rollbackQuietly();
            throw new IOException("Database: " + e.getMessage(), e);
        }
    }

    private static void pairs(Statement st, String sql, Map<String, Map<String, Long>> into) throws SQLException {
        try (ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                into.computeIfAbsent(rs.getString(1).strip(), k -> new HashMap<>()).put(rs.getString(2).strip(), rs.getLong(3));
            }
        }
    }

    /** The highest message id in the database (for tests and the import). */
    synchronized long messageCount() throws IOException {
        try (Statement st = connection().createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM messages")) {
            rs.next();
            long n = rs.getLong(1);
            db.commit();
            return n;
        } catch (SQLException e) {
            rollbackQuietly();
            throw new IOException(e.getMessage(), e);
        }
    }

    /** Whether the database holds no players yet (a fresh installation, target of an import). */
    synchronized boolean empty() throws IOException {
        try (Statement st = connection().createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM users")) {
            rs.next();
            boolean e = rs.getLong(1) == 0;
            db.commit();
            return e;
        } catch (SQLException e) {
            rollbackQuietly();
            throw new IOException(e.getMessage(), e);
        }
    }

    // ---------------------------------------------------------------- save

    @Override
    public synchronized void append(ChatMessage m) {
        pendingInserts.add(m);
    }

    @Override
    public synchronized void update(ChatMessage m, Collection<ChatMessage> conversation) {
        for (int i = 0; i < pendingInserts.size(); i++) {
            if (pendingInserts.get(i).id == m.id) {
                pendingInserts.set(i, m); // not written yet: insert the new version
                return;
            }
        }
        pendingUpdates.put(m.id, m);
    }

    @Override
    public synchronized void deleteConversation(String conv) {
        pendingInserts.removeIf(m -> m.conv.equals(conv));
        pendingUpdates.values().removeIf(m -> m.conv.equals(conv));
        pendingConvDeletes.add(conv);
    }

    @Override
    public synchronized void save(State s) throws IOException {
        Map<String, Map<String, List<Object>>> now = snapshot(s);
        try {
            Connection c = connection();
            // parents first: players, groups, conversations
            upsert(c, now, "users", "INSERT INTO users (uuid, name, first_seen, last_seen) VALUES (?, ?, ?, ?) "
                    + "ON CONFLICT (uuid) DO UPDATE SET name = EXCLUDED.name, first_seen = EXCLUDED.first_seen, "
                    + "last_seen = EXCLUDED.last_seen", 1);
            insertNames(c, now);
            upsert(c, now, "groups", "INSERT INTO user_groups (id, name, description, icon, color, owner, created) VALUES "
                    + "(?, ?, ?, ?, ?, ?, ?) ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, description = "
                    + "EXCLUDED.description, icon = EXCLUDED.icon, color = EXCLUDED.color, owner = EXCLUDED.owner", 1);
            Set<String> newConvs = new HashSet<>();
            for (String conv : now.getOrDefault("convs", Map.of()).keySet()) {
                if (!conversations.contains(conv) && !pendingConvDeletes.contains(conv)) {
                    newConvs.add(conv);
                }
            }
            for (ChatMessage m : pendingInserts) {
                if (!conversations.contains(m.conv)) {
                    newConvs.add(m.conv);
                }
            }
            if (!newConvs.isEmpty()) {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO conversations (id, kind, created) VALUES (?, ?, ?) ON CONFLICT (id) DO NOTHING")) {
                    for (String conv : newConvs) {
                        ps.setString(1, conv);
                        ps.setString(2, conv.startsWith("g:") ? "group" : "dm");
                        ps.setLong(3, System.currentTimeMillis());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }
            // children: delete what is gone, then write what changed
            delete(c, now, "friendships", "DELETE FROM friendships WHERE user_a = ? AND user_b = ?", 2);
            delete(c, now, "requests", "DELETE FROM friend_requests WHERE from_uuid = ? AND to_uuid = ?", 2);
            delete(c, now, "blocks", "DELETE FROM blocked_users WHERE blocker = ? AND blocked = ?", 2);
            delete(c, now, "roles", "DELETE FROM group_roles WHERE group_id = ? AND role_id = ?", 2);
            delete(c, now, "members", "DELETE FROM group_members WHERE group_id = ? AND uuid = ?", 2);
            delete(c, now, "convMembers", "DELETE FROM conversation_members WHERE conv = ? AND uuid = ?", 2);
            delete(c, now, "settings", "DELETE FROM user_settings WHERE uuid = ?", 1);
            upsert(c, now, "friendships", "INSERT INTO friendships (user_a, user_b, since) VALUES (?, ?, ?) "
                    + "ON CONFLICT (user_a, user_b) DO UPDATE SET since = EXCLUDED.since", 2);
            upsert(c, now, "requests", "INSERT INTO friend_requests (from_uuid, to_uuid, created) VALUES (?, ?, ?) "
                    + "ON CONFLICT (from_uuid, to_uuid) DO UPDATE SET created = EXCLUDED.created", 2);
            upsert(c, now, "blocks", "INSERT INTO blocked_users (blocker, blocked, created) VALUES (?, ?, ?) "
                    + "ON CONFLICT (blocker, blocked) DO UPDATE SET created = EXCLUDED.created", 2);
            upsert(c, now, "roles", "INSERT INTO group_roles (group_id, role_id, name, icon, color, manage, position) VALUES "
                    + "(?, ?, ?, ?, ?, ?, ?) ON CONFLICT (group_id, role_id) DO UPDATE SET name = EXCLUDED.name, icon = "
                    + "EXCLUDED.icon, color = EXCLUDED.color, manage = EXCLUDED.manage, position = EXCLUDED.position", 2);
            upsert(c, now, "members", "INSERT INTO group_members (group_id, uuid, role, joined, position) VALUES (?, ?, ?, ?, ?) "
                    + "ON CONFLICT (group_id, uuid) DO UPDATE SET role = EXCLUDED.role, joined = EXCLUDED.joined, "
                    + "position = EXCLUDED.position", 2);
            upsert(c, now, "convMembers", "INSERT INTO conversation_members (conv, uuid, last_read, muted) VALUES (?, ?, ?, ?) "
                    + "ON CONFLICT (conv, uuid) DO UPDATE SET last_read = EXCLUDED.last_read, muted = EXCLUDED.muted", 2);
            upsert(c, now, "settings", "INSERT INTO user_settings (uuid, request_policy, presence_mode, updated, show_badge) VALUES "
                    + "(?, ?, ?, ?, ?) ON CONFLICT (uuid) DO UPDATE SET request_policy = EXCLUDED.request_policy, "
                    + "presence_mode = EXCLUDED.presence_mode, updated = EXCLUDED.updated, show_badge = EXCLUDED.show_badge", 1);
            writeMessages(c);
            if (!pendingConvDeletes.isEmpty()) {
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM conversations WHERE id = ?")) {
                    for (String conv : pendingConvDeletes) {
                        ps.setString(1, conv);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }
            delete(c, now, "groups", "DELETE FROM user_groups WHERE id = ?", 1);
            writeAudits(c);
            c.commit();
            written = now;
            conversations.addAll(newConvs);
            conversations.removeAll(pendingConvDeletes);
            pendingInserts.clear();
            pendingUpdates.clear();
            pendingConvDeletes.clear();
            pendingAudits.clear();
        } catch (SQLException e) {
            rollbackQuietly();
            throw new IOException("Database: " + e.getMessage(), e);
        }
    }

    private void writeMessages(Connection c) throws SQLException {
        if (!pendingInserts.isEmpty()) {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO messages (id, conv, sender, body, sent_at, edited_at, deleted) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT (id) DO NOTHING")) {
                for (ChatMessage m : pendingInserts) {
                    ps.setLong(1, m.id);
                    ps.setString(2, m.conv);
                    ps.setString(3, m.sender);
                    ps.setString(4, m.text);
                    ps.setLong(5, m.at);
                    if (m.editedAt > 0) {
                        ps.setLong(6, m.editedAt);
                    } else {
                        ps.setNull(6, Types.BIGINT);
                    }
                    ps.setBoolean(7, m.deleted);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }
        if (!pendingUpdates.isEmpty()) {
            try (PreparedStatement ps = c.prepareStatement("UPDATE messages SET body = ?, edited_at = ?, deleted = ? WHERE id = ?")) {
                for (ChatMessage m : pendingUpdates.values()) {
                    ps.setString(1, m.text);
                    ps.setLong(2, m.editedAt);
                    ps.setBoolean(3, m.deleted);
                    ps.setLong(4, m.id);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }
    }

    /** Remembers every name a player was seen with (name changes). */
    private void insertNames(Connection c, Map<String, Map<String, List<Object>>> now) throws SQLException {
        Map<String, List<Object>> before = written.getOrDefault("users", Map.of());
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO minecraft_accounts (uuid, name, first_seen) VALUES (?, ?, ?) "
                + "ON CONFLICT (uuid, name) DO NOTHING")) {
            int n = 0;
            for (Map.Entry<String, List<Object>> e : now.getOrDefault("users", Map.of()).entrySet()) {
                List<Object> old = before.get(e.getKey());
                if (old == null || !Objects.equals(old.get(0), e.getValue().get(0))) {
                    ps.setString(1, e.getKey());
                    ps.setString(2, (String) e.getValue().get(0));
                    ps.setLong(3, System.currentTimeMillis());
                    ps.addBatch();
                    n++;
                }
            }
            if (n > 0) {
                ps.executeBatch();
            }
        }
    }

    /**
     * Writes rows that are new or changed. The SQL takes the key parts first ({@code keyParts} of them, from the key
     * split at "|"), then the values.
     */
    private void upsert(Connection c, Map<String, Map<String, List<Object>>> now, String table, String sql, int keyParts)
            throws SQLException {
        Map<String, List<Object>> before = written.getOrDefault(table, Map.of());
        Map<String, List<Object>> after = now.getOrDefault(table, Map.of());
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int n = 0;
            for (Map.Entry<String, List<Object>> e : after.entrySet()) {
                if (e.getValue().equals(before.get(e.getKey()))) {
                    continue;
                }
                int i = 1;
                for (String part : e.getKey().split("\\|", keyParts)) {
                    ps.setString(i++, part);
                }
                for (Object v : e.getValue()) {
                    set(ps, i++, v);
                }
                ps.addBatch();
                n++;
            }
            if (n > 0) {
                ps.executeBatch();
            }
        }
    }

    private void delete(Connection c, Map<String, Map<String, List<Object>>> now, String table, String sql, int keyParts)
            throws SQLException {
        Map<String, List<Object>> before = written.getOrDefault(table, Map.of());
        Map<String, List<Object>> after = now.getOrDefault(table, Map.of());
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int n = 0;
            for (String key : before.keySet()) {
                if (after.containsKey(key)) {
                    continue;
                }
                int i = 1;
                for (String part : key.split("\\|", keyParts)) {
                    ps.setString(i++, part);
                }
                ps.addBatch();
                n++;
            }
            if (n > 0) {
                ps.executeBatch();
            }
        }
    }

    private static void set(PreparedStatement ps, int i, Object v) throws SQLException {
        if (v instanceof String s) {
            ps.setString(i, s);
        } else if (v instanceof Long l) {
            ps.setLong(i, l);
        } else if (v instanceof Integer n) {
            ps.setInt(i, n);
        } else if (v instanceof Boolean b) {
            ps.setBoolean(i, b);
        } else if (v == null) {
            ps.setNull(i, Types.VARCHAR);
        } else {
            throw new IllegalArgumentException("unsupported value " + v.getClass());
        }
    }

    /** The state as rows: table -> key ("a|b" for composite keys) -> values in SQL order. */
    static Map<String, Map<String, List<Object>>> snapshot(State s) {
        Map<String, Map<String, List<Object>>> t = new HashMap<>();
        Map<String, List<Object>> users = table(t, "users");
        for (State.User u : s.users.values()) {
            users.put(u.uuid, List.of(u.name, u.firstSeen, u.lastSeen));
        }
        Map<String, List<Object>> friendships = table(t, "friendships");
        s.friends.forEach((a, m) -> m.forEach((b, since) -> {
            if (a.compareTo(b) < 0 && users.containsKey(a) && users.containsKey(b)) {
                friendships.put(a + "|" + b, List.of(since));
            }
        }));
        pairRows(s.requests, table(t, "requests"), users);
        pairRows(s.blocks, table(t, "blocks"), users);
        Map<String, List<Object>> groups = table(t, "groups");
        Map<String, List<Object>> roles = table(t, "roles");
        Map<String, List<Object>> members = table(t, "members");
        Map<String, List<Object>> convs = table(t, "convs");
        for (State.GroupRec g : s.groups.values()) {
            if (!users.containsKey(g.owner)) {
                continue;
            }
            groups.put(g.id, List.of(g.name, g.description == null ? "" : g.description, g.icon, g.color, g.owner, g.created));
            int pos = 0;
            for (Role r : g.roles.values()) {
                roles.put(g.id + "|" + r.id, List.of(r.name, r.icon, r.color, r.manage, pos++));
            }
            pos = 0;
            for (Map.Entry<String, State.MemberRec> m : g.members.entrySet()) {
                if (users.containsKey(m.getKey())) {
                    members.put(g.id + "|" + m.getKey(), List.of(m.getValue().role, m.getValue().joined, pos));
                }
                pos++;
            }
        }
        for (String conv : s.messages.keySet()) {
            convs.put(conv, List.of());
        }
        Map<String, List<Object>> convMembers = table(t, "convMembers");
        Map<String, long[]> readMute = new LinkedHashMap<>();
        s.reads.forEach((uuid, m) -> m.forEach((conv, id) -> readMute.computeIfAbsent(conv + "|" + uuid, k -> new long[2])[0] = id));
        s.muted.forEach((uuid, set) -> set.forEach(conv -> readMute.computeIfAbsent(conv + "|" + uuid, k -> new long[2])[1] = 1));
        readMute.forEach((key, v) -> {
            String conv = key.substring(0, key.lastIndexOf('|'));
            String uuid = key.substring(key.lastIndexOf('|') + 1);
            if (users.containsKey(uuid) && validConv(conv)) {
                convs.put(conv, List.of());
                convMembers.put(key, List.of(v[0], v[1] == 1));
            }
        });
        Map<String, List<Object>> settings = table(t, "settings");
        s.settings.forEach((uuid, x) -> {
            if (users.containsKey(uuid)) {
                settings.put(uuid, List.of(x.requestPolicy, x.presenceMode, x.updated, x.showBadge));
            }
        });
        return t;
    }

    private static boolean validConv(String conv) {
        return conv.length() <= 80 && (conv.startsWith("dm:") || conv.startsWith("g:"));
    }

    private static void pairRows(Map<String, Map<String, Long>> src, Map<String, List<Object>> into, Map<String, List<Object>> users) {
        src.forEach((a, m) -> m.forEach((b, at) -> {
            if (!a.equals(b) && users.containsKey(a) && users.containsKey(b)) {
                into.put(a + "|" + b, List.of(at));
            }
        }));
    }

    private static Map<String, List<Object>> table(Map<String, Map<String, List<Object>>> t, String name) {
        return t.computeIfAbsent(name, k -> new LinkedHashMap<>());
    }

    /** Queued and written with the next {@link #save} (no database round trip inside the server lock). */
    @Override
    public synchronized void audit(String actor, String action, String target, String detail) {
        pendingAudits.add(new Object[]{System.currentTimeMillis(), actor, action, cut(target, 80), cut(detail, 500)});
    }

    private static String cut(String s, int max) {
        return s == null ? null : s.length() > max ? s.substring(0, max) : s;
    }

    private void writeAudits(Connection c) throws SQLException {
        if (pendingAudits.isEmpty()) {
            return;
        }
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO audit_logs (at, actor, action, target, detail) VALUES (?, ?, ?, ?, ?)")) {
            for (Object[] a : pendingAudits) {
                ps.setLong(1, (Long) a[0]);
                for (int i = 1; i < 5; i++) {
                    ps.setString(i + 1, (String) a[i]);
                }
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void rollbackQuietly() {
        try {
            if (db != null) {
                db.rollback();
            }
        } catch (SQLException ignored) {
            // the next save reconnects
        }
    }

    @Override
    public synchronized void close() {
        if (db != null) {
            try {
                db.close();
            } catch (SQLException ignored) {
                // shutting down
            }
        }
    }
}
