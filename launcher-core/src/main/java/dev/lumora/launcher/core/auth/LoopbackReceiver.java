package dev.lumora.launcher.core.auth;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.lumora.launcher.core.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;

/**
 * Receives the OAuth redirect on {@code http://localhost:<port>/}. Listens on loopback only (127.0.0.1 and,
 * when available, ::1 on the same port, because browsers may resolve "localhost" to either). Requests
 * without the expected {@code state} are answered with 400 and ignored, so another local page cannot
 * inject a code.
 */
final class LoopbackReceiver implements AutoCloseable {
    private final HttpServer v4;
    private final HttpServer v6;
    private final String state;
    private final CompletableFuture<Map<String, String>> result = new CompletableFuture<>();

    private LoopbackReceiver(HttpServer v4, HttpServer v6, String state) {
        this.v4 = v4;
        this.v6 = v6;
        this.state = state;
    }

    static LoopbackReceiver start(String state) throws IOException {
        HttpServer v4 = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        int port = v4.getAddress().getPort();
        HttpServer v6 = null;
        try {
            InetAddress ip6 = InetAddress.getByName("::1");
            if (ip6 instanceof Inet6Address) {
                v6 = HttpServer.create(new InetSocketAddress(ip6, port), 0);
            }
        } catch (IOException e) {
            Log.debug("IPv6 loopback not available for the sign-in redirect: " + e.getMessage());
        }
        LoopbackReceiver r = new LoopbackReceiver(v4, v6, state);
        v4.createContext("/", r::handle);
        v4.start();
        if (v6 != null) {
            v6.createContext("/", r::handle);
            v6.start();
        }
        return r;
    }

    String redirectUri() {
        return "http://localhost:" + v4.getAddress().getPort();
    }

    /** Waits for the redirect. Returns null on timeout or cancellation. */
    Map<String, String> await(Duration timeout, BooleanSupplier cancelled) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (cancelled.getAsBoolean()) {
                return null;
            }
            try {
                return result.get(250, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                // keep waiting
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } catch (ExecutionException e) {
                return null;
            }
        }
        return null;
    }

    private void handle(HttpExchange ex) throws IOException {
        try (ex) {
            if (!"GET".equals(ex.getRequestMethod()) || !"/".equals(ex.getRequestURI().getPath())) {
                respond(ex, 404, page("Nicht gefunden", ""));
                return;
            }
            Map<String, String> q = parseQuery(ex.getRequestURI().getRawQuery());
            if (!state.equals(q.get("state"))) {
                respond(ex, 400, page("Ungültige Anfrage", "Diese Anmeldeantwort gehört nicht zur aktuellen Anmeldung."));
                return;
            }
            if (q.containsKey("code")) {
                respond(ex, 200, page("Anmeldung erfolgreich",
                        "Du bist angemeldet. Dieses Fenster kann geschlossen werden – zurück zum Lumora Launcher."));
            } else {
                respond(ex, 200, page("Anmeldung abgebrochen",
                        "Die Anmeldung wurde nicht abgeschlossen. Du kannst es im Launcher erneut versuchen."));
            }
            result.complete(q);
        }
    }

    static Map<String, String> parseQuery(String raw) {
        Map<String, String> out = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) {
            return out;
        }
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            String k = URLDecoder.decode(eq >= 0 ? pair.substring(0, eq) : pair, StandardCharsets.UTF_8);
            String v = eq >= 0 ? URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8) : "";
            out.putIfAbsent(k, v);
        }
        return out;
    }

    private static String page(String title, String text) {
        return "<!doctype html><html lang=\"de\"><head><meta charset=\"utf-8\"><title>Lumora Client – " + title
                + "</title><style>body{font-family:Segoe UI,sans-serif;background:#1e2227;color:#e6e6e6;display:flex;"
                + "align-items:center;justify-content:center;height:100vh;margin:0}div{max-width:480px;padding:32px;"
                + "background:#2b3036;border-radius:12px}h1{color:#13a8c0;font-size:22px}</style></head><body><div><h1>"
                + title + "</h1><p>" + text + "</p></div></body></html>";
    }

    private static void respond(HttpExchange ex, int status, String html) throws IOException {
        byte[] body = html.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(status, body.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(body);
        }
    }

    @Override
    public void close() {
        v4.stop(0);
        if (v6 != null) {
            v6.stop(0);
        }
    }
}
