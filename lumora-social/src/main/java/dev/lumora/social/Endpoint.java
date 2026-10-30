package dev.lumora.social;

import java.net.URI;
import java.util.Locale;

/**
 * The address of a Lumora Social server as the user types it: {@code chat.example.com}, {@code host:port},
 * {@code wss://host/path} or {@code ws://192.168.0.10:25600}. Unencrypted {@code ws://} is only accepted for this computer
 * and the local network (loopback, private IPv4 ranges, single-label and {@code .local}/{@code .lan} names); everything
 * else must use TLS ({@code wss://}), so chats and the session are not readable on the way.
 */
public final class Endpoint {
    public static final int DEFAULT_PORT = 25600;

    private Endpoint() {
    }

    /** @throws IllegalArgumentException with a German message for the settings dialog */
    public static URI parse(String input) {
        String s = input == null ? "" : input.strip();
        if (s.isEmpty()) {
            throw new IllegalArgumentException("Keine Server-Adresse angegeben.");
        }
        boolean explicit = s.contains("://");
        URI uri;
        try {
            uri = URI.create(explicit ? s : "x://" + s);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Ungültige Adresse: " + s);
        }
        String host = uri.getHost();
        if (host == null || host.isEmpty()) {
            throw new IllegalArgumentException("Ungültige Adresse: " + s);
        }
        if (uri.getUserInfo() != null) {
            throw new IllegalArgumentException("Die Adresse darf keine Zugangsdaten enthalten.");
        }
        String scheme = explicit ? uri.getScheme().toLowerCase(Locale.ROOT) : (local(host) ? "ws" : "wss");
        if (!scheme.equals("ws") && !scheme.equals("wss")) {
            throw new IllegalArgumentException("Nur ws:// oder wss:// werden unterstützt.");
        }
        if (scheme.equals("ws") && !local(host)) {
            throw new IllegalArgumentException("Unverschlüsselt (ws://) nur im lokalen Netz. Für " + host + " bitte wss:// verwenden.");
        }
        int port = uri.getPort() > 0 ? uri.getPort() : scheme.equals("wss") ? 443 : DEFAULT_PORT;
        String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        return URI.create(scheme + "://" + (host.contains(":") ? "[" + host + "]" : host) + ":" + port + path);
    }

    /** Short form for the UI ("chat.example.com" or "192.168.0.10:25600"). */
    public static String display(URI uri) {
        boolean defaultPort = uri.getScheme().equals("wss") ? uri.getPort() == 443 : uri.getPort() == DEFAULT_PORT;
        return uri.getHost() + (defaultPort ? "" : ":" + uri.getPort()) + (uri.getPath().equals("/") ? "" : uri.getPath());
    }

    /** This computer or the local network (no lookup: decided from the written name or address). */
    public static boolean local(String host) {
        String h = host.toLowerCase(Locale.ROOT);
        if (h.startsWith("[") && h.endsWith("]")) {
            h = h.substring(1, h.length() - 1);
        }
        if (h.equals("localhost") || h.endsWith(".localhost") || h.equals("::1") || h.endsWith(".local")
                || h.endsWith(".lan") || h.endsWith(".home.arpa") || h.endsWith(".test")) {
            return true;
        }
        if (!h.contains(".") && !h.contains(":")) {
            return true; // single-label name: never a public DNS name
        }
        String[] p = h.split("\\.");
        if (p.length != 4) {
            return h.startsWith("fd") || h.startsWith("fe80:");
        }
        int[] b = new int[4];
        for (int i = 0; i < 4; i++) {
            try {
                b[i] = Integer.parseInt(p[i]);
            } catch (NumberFormatException e) {
                return false;
            }
            if (b[i] < 0 || b[i] > 255) {
                return false;
            }
        }
        return b[0] == 127 || b[0] == 10 || (b[0] == 172 && b[1] >= 16 && b[1] <= 31) || (b[0] == 192 && b[1] == 168)
                || (b[0] == 169 && b[1] == 254);
    }
}
