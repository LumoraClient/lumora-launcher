package dev.lumora.launcher.core.library;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import dev.lumora.launcher.core.util.Json;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Hashtable;

/**
 * Status query of a Minecraft Java server with the public "Server List Ping" protocol (handshake, status
 * request, ping), the same request the multiplayer menu sends. Used only for the servers in the player's own
 * servers.dat. Response size and time are limited; nothing else is sent.
 */
public final class ServerPing {
    public record Status(String motd, int online, int max, String version, long pingMs, byte[] favicon) {
    }

    static final int DEFAULT_PORT = 25565;
    private static final int TIMEOUT_MS = 3000;
    private static final int MAX_RESPONSE = 1024 * 1024;

    private ServerPing() {
    }

    public record Address(String host, int port) {
    }

    /** "host", "host:port" or "[v6]:port"; without a port the SRV record _minecraft._tcp is honoured. */
    public static Address parse(String address) {
        String a = address.strip();
        String host = a;
        int port = -1;
        if (a.startsWith("[")) {
            int end = a.indexOf(']');
            host = end > 0 ? a.substring(1, end) : a;
            if (end > 0 && a.length() > end + 2 && a.charAt(end + 1) == ':') {
                port = parsePort(a.substring(end + 2));
            }
        } else if (a.indexOf(':') > 0 && a.indexOf(':') == a.lastIndexOf(':')) {
            host = a.substring(0, a.indexOf(':'));
            port = parsePort(a.substring(a.indexOf(':') + 1));
        }
        return new Address(host, port);
    }

    private static int parsePort(String s) {
        try {
            int p = Integer.parseInt(s);
            return p > 0 && p < 65536 ? p : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static Status ping(String address) throws IOException {
        Address parsed = parse(address);
        String host = parsed.host();
        int port = parsed.port();
        if (port < 0) {
            Address srv = srv(host);
            if (srv != null) {
                host = srv.host();
                port = srv.port();
            } else {
                port = DEFAULT_PORT;
            }
        }
        try (Socket s = new Socket()) {
            long start = System.nanoTime();
            s.connect(new InetSocketAddress(host, port), TIMEOUT_MS);
            s.setSoTimeout(TIMEOUT_MS);
            DataOutputStream out = new DataOutputStream(s.getOutputStream());
            DataInputStream in = new DataInputStream(s.getInputStream());

            ByteArrayOutputStream hs = new ByteArrayOutputStream();
            DataOutputStream h = new DataOutputStream(hs);
            writeVarInt(h, 0x00);
            writeVarInt(h, -1); // protocol version: -1 = "just asking for the status"
            writeString(h, parsed.host());
            h.writeShort(port);
            writeVarInt(h, 1); // next state: status
            writePacket(out, hs.toByteArray());
            writePacket(out, new byte[]{0x00}); // status request

            int length = readVarInt(in);
            if (length <= 0 || length > MAX_RESPONSE) {
                throw new IOException("invalid status response length " + length);
            }
            byte[] packet = in.readNBytes(length);
            DataInputStream p = new DataInputStream(new java.io.ByteArrayInputStream(packet));
            if (readVarInt(p) != 0x00) {
                throw new IOException("unexpected packet");
            }
            int jsonLength = readVarInt(p);
            if (jsonLength < 0 || jsonLength > packet.length) {
                throw new IOException("invalid status string");
            }
            String json = new String(p.readNBytes(jsonLength), StandardCharsets.UTF_8);
            long pingMs = (System.nanoTime() - start) / 1_000_000;
            return parseStatus(json, pingMs);
        }
    }

    static Status parseStatus(String json, long pingMs) throws IOException {
        try {
            JsonObject o = Json.GSON.fromJson(json, JsonObject.class);
            if (o == null) {
                throw new IOException("empty status");
            }
            JsonObject players = o.has("players") && o.get("players").isJsonObject() ? o.getAsJsonObject("players") : new JsonObject();
            JsonObject version = o.has("version") && o.get("version").isJsonObject() ? o.getAsJsonObject("version") : new JsonObject();
            byte[] favicon = null;
            if (o.has("favicon") && o.get("favicon").isJsonPrimitive()) {
                String f = o.get("favicon").getAsString();
                int comma = f.indexOf(',');
                if (f.startsWith("data:image/png;base64,") && comma > 0) {
                    try {
                        favicon = Base64.getMimeDecoder().decode(f.substring(comma + 1));
                    } catch (IllegalArgumentException ignored) {
                        // broken favicon, ignore
                    }
                }
            }
            return new Status(stripFormatting(text(o.get("description"))).strip(),
                    players.has("online") ? players.get("online").getAsInt() : -1,
                    players.has("max") ? players.get("max").getAsInt() : -1,
                    version.has("name") ? stripFormatting(version.get("name").getAsString()) : "?", pingMs, favicon);
        } catch (JsonParseException | IllegalStateException | ClassCastException | NumberFormatException | UnsupportedOperationException e) {
            throw new IOException("invalid status JSON: " + e.getMessage(), e);
        }
    }

    /** Flattens a chat component (string, {"text":..,"extra":[..]} or array) to plain text. */
    static String text(JsonElement e) {
        if (e == null || e.isJsonNull()) {
            return "";
        }
        if (e.isJsonPrimitive()) {
            return e.getAsString();
        }
        StringBuilder sb = new StringBuilder();
        if (e.isJsonArray()) {
            e.getAsJsonArray().forEach(x -> sb.append(text(x)));
        } else if (e.isJsonObject()) {
            JsonObject o = e.getAsJsonObject();
            if (o.has("text")) {
                sb.append(text(o.get("text")));
            }
            if (o.has("extra")) {
                sb.append(text(o.get("extra")));
            }
        }
        return sb.toString();
    }

    /** Removes legacy color codes (§x). */
    static String stripFormatting(String s) {
        return s.replaceAll("§.", "");
    }

    private static Address srv(String host) {
        try {
            Hashtable<String, String> env = new Hashtable<>();
            env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
            env.put("com.sun.jndi.dns.timeout.initial", "1500");
            env.put("com.sun.jndi.dns.timeout.retries", "1");
            javax.naming.directory.DirContext ctx = new javax.naming.directory.InitialDirContext(env);
            try {
                javax.naming.directory.Attribute a = ctx.getAttributes("_minecraft._tcp." + host, new String[]{"SRV"}).get("SRV");
                if (a == null || a.size() == 0) {
                    return null;
                }
                String[] parts = a.get(0).toString().split(" ");
                if (parts.length < 4) {
                    return null;
                }
                String target = parts[3].endsWith(".") ? parts[3].substring(0, parts[3].length() - 1) : parts[3];
                return new Address(target, Integer.parseInt(parts[2]));
            } finally {
                ctx.close();
            }
        } catch (javax.naming.NamingException | RuntimeException e) {
            return null; // no SRV record or DNS provider not available
        }
    }

    private static void writePacket(DataOutputStream out, byte[] body) throws IOException {
        writeVarInt(out, body.length);
        out.write(body);
        out.flush();
    }

    static void writeVarInt(DataOutputStream out, int value) throws IOException {
        int v = value;
        while ((v & ~0x7F) != 0) {
            out.writeByte((v & 0x7F) | 0x80);
            v >>>= 7;
        }
        out.writeByte(v);
    }

    static int readVarInt(DataInputStream in) throws IOException {
        int value = 0;
        for (int i = 0; i < 5; i++) {
            int b = in.readUnsignedByte();
            value |= (b & 0x7F) << (7 * i);
            if ((b & 0x80) == 0) {
                return value;
            }
        }
        throw new IOException("VarInt too long");
    }

    private static void writeString(DataOutputStream out, String s) throws IOException {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, b.length);
        out.write(b);
    }
}
