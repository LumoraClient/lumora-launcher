package dev.lumora.social.server;

import dev.lumora.social.Rules;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.concurrent.CountDownLatch;

/**
 * Lumora Social server.
 * <pre>
 * java -jar lumora-social-server.jar [--port 25600] [--bind 0.0.0.0] [--data ./lumora-social-data]
 *                                    [--tls-keystore server.p12]   (password in LUMORA_SOCIAL_TLS_PASSWORD)
 *                                    [--dev-auth]                  (only with --bind 127.0.0.1: no Mojang check)
 * </pre>
 * Without {@code --tls-keystore} the server speaks plain WebSocket; clients accept that only on the local network, so
 * a server on the internet needs TLS here or a reverse proxy (Caddy, nginx) in front. See docs/SOCIAL.md.
 */
public final class Main {
    private Main() {
    }

    /** A running server (also used by tests and by the launcher's integration test). */
    public static final class Running implements AutoCloseable {
        private final WsServer ws;
        private final Hub hub;

        Running(WsServer ws, Hub hub) {
            this.ws = ws;
            this.hub = hub;
        }

        public int port() {
            return ws.port();
        }

        public int onlineUsers() {
            return hub.onlineUsers();
        }

        @Override
        public void close() {
            ws.close();
            hub.shutdown();
        }
    }

    /** Starts a server; {@code dev} skips the Mojang check (tests, local development) and needs a loopback address. */
    public static Running start(InetSocketAddress bind, Path data, SSLContext tls, boolean dev) throws IOException {
        if (dev && !bind.getAddress().isLoopbackAddress()) {
            throw new IllegalArgumentException("--dev-auth only together with --bind 127.0.0.1 (no Mojang check!)");
        }
        Store store = new Store(data);
        State state = store.load();
        Hub hub = new Hub(store, state, new MojangVerifier(), dev);
        WsServer ws = new WsServer(bind, tls, hub);
        return new Running(ws, hub);
    }

    public static void main(String[] args) throws Exception {
        int port = 25600;
        String bind = "0.0.0.0";
        Path data = Path.of("lumora-social-data");
        Path keystore = null;
        boolean dev = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--port" -> port = Integer.parseInt(args[++i]);
                case "--bind" -> bind = args[++i];
                case "--data" -> data = Path.of(args[++i]);
                case "--tls-keystore" -> keystore = Path.of(args[++i]);
                case "--dev-auth" -> dev = true;
                case "--quiet" -> Log.quiet = true;
                case "--help", "-h" -> {
                    System.out.println("Lumora Social server (protocol " + Rules.PROTOCOL + ")\n"
                            + "  --port <n>             default 25600\n"
                            + "  --bind <address>       default 0.0.0.0\n"
                            + "  --data <dir>           default ./lumora-social-data\n"
                            + "  --tls-keystore <p12>   TLS (wss://); password in env LUMORA_SOCIAL_TLS_PASSWORD\n"
                            + "  --dev-auth             no Mojang check, only with --bind 127.0.0.1 (testing)");
                    return;
                }
                default -> {
                    System.err.println("Unknown option " + args[i] + " (--help)");
                    System.exit(2);
                }
            }
        }
        SSLContext tls = keystore == null ? null : tls(keystore);
        if (dev) {
            Log.warn("--dev-auth: logins are NOT checked with Mojang. Testing only.");
        }
        Running r = start(new InetSocketAddress(InetAddress.getByName(bind), port), data, tls, dev);
        Log.info("Lumora Social server listening on " + bind + ":" + r.port() + (tls != null ? " (TLS)" : " (no TLS)")
                + ", data in " + data.toAbsolutePath());
        CountDownLatch stop = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Log.info("Stopping");
            r.close();
            stop.countDown();
        }));
        stop.await();
    }

    private static SSLContext tls(Path keystore) throws Exception {
        String pw = System.getenv("LUMORA_SOCIAL_TLS_PASSWORD");
        char[] password = pw == null ? new char[0] : pw.toCharArray();
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(keystore)) {
            ks.load(in, password);
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, password);
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), null, null);
        return ctx;
    }
}
