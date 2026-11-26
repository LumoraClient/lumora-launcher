package dev.lumora.social.server;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every server test of {@link SocialServerTest} again, on a real PostgreSQL (started from the official binaries in a
 * temporary folder). Each test gets its own database; a restart inside a test keeps it.
 */
class SocialServerPgTest extends SocialServerTest {
    private static EmbeddedPostgres pg;
    private static final AtomicInteger DB = new AtomicInteger();
    private String database;

    @BeforeAll
    static void startDatabase() throws IOException {
        pg = EmbeddedPostgres.builder().start();
    }

    @AfterAll
    static void stopDatabase() throws IOException {
        if (pg != null) {
            pg.close();
        }
    }

    @BeforeEach
    void freshDatabase() throws Exception {
        database = "lumora_test_" + DB.incrementAndGet();
        try (Connection c = pg.getPostgresDatabase().getConnection(); Statement st = c.createStatement()) {
            st.execute("CREATE DATABASE " + database);
        }
    }

    String url() {
        return pg.getJdbcUrl("postgres", database);
    }

    @Override
    void startServer() throws IOException {
        server = Main.start(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), new PgStore(url(), "postgres", null), null, true);
    }

    @Override
    void checkStorage() throws Exception {
        try (Connection c = java.sql.DriverManager.getConnection(url(), "postgres", null); Statement st = c.createStatement()) {
            assertEquals(3, count(st, "SELECT COUNT(*) FROM users"));
            assertEquals(2, count(st, "SELECT COUNT(*) FROM friendships"), "max-toby and max-sarah, each stored once");
            assertTrue(count(st, "SELECT COUNT(*) FROM messages") >= 61, "dm + 60 group messages");
            assertEquals(1, count(st, "SELECT version FROM schema_version"));
        }
    }

    static long count(Statement st, String sql) throws Exception {
        try (ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }
}
