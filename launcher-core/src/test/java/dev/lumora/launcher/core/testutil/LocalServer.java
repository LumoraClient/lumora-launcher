package dev.lumora.launcher.core.testutil;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Tiny HTTP server for tests: path -> handler, with per-path request counters. */
public final class LocalServer implements AutoCloseable {
    public interface Handler {
        Response handle(HttpExchange ex, int callNumber) throws IOException;
    }

    public record Response(int status, byte[] body, String contentType) {
        public static Response ok(byte[] body) {
            return new Response(200, body, "application/octet-stream");
        }

        public static Response json(int status, String json) {
            return new Response(status, json.getBytes(StandardCharsets.UTF_8), "application/json");
        }
    }

    private final HttpServer server;
    private final Map<String, AtomicInteger> counts = new ConcurrentHashMap<>();
    private final Map<String, String> lastBodies = new ConcurrentHashMap<>();

    public LocalServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.start();
    }

    public LocalServer on(String path, Handler h) {
        server.createContext(path, ex -> {
            int n = counts.computeIfAbsent(path, k -> new AtomicInteger()).incrementAndGet();
            lastBodies.put(path, new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            Response r = h.handle(ex, n);
            ex.getResponseHeaders().add("Content-Type", r.contentType());
            ex.sendResponseHeaders(r.status(), r.body().length == 0 ? -1 : r.body().length);
            if (r.body().length > 0) {
                ex.getResponseBody().write(r.body());
            }
            ex.close();
        });
        return this;
    }

    public URI uri(String path) {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
    }

    public int count(String path) {
        AtomicInteger c = counts.get(path);
        return c == null ? 0 : c.get();
    }

    public String lastBody(String path) {
        return lastBodies.get(path);
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
