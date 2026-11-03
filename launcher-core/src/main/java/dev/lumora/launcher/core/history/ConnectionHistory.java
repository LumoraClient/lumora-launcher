package dev.lumora.launcher.core.history;

import dev.lumora.launcher.core.util.Json;
import dev.lumora.launcher.core.util.Log;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Local connection history of this PC (config/connections.json): sign-ins, token renewals, sign-outs and game starts
 * per account, plus the servers the game connected to. Holds names, UUIDs, versions and server addresses only - never
 * tokens. Can be switched off; a broken file is ignored and replaced on the next write.
 */
public final class ConnectionHistory {
    /** Kept events (oldest dropped first). */
    static final int MAX_EVENTS = 400;
    /** Kept servers (least recently used dropped first). */
    static final int MAX_SERVERS = 200;
    /** Renewals of the same account within this time update the last entry instead of adding one. */
    static final Duration MERGE_REFRESH = Duration.ofHours(6);

    public enum Kind {
        SIGN_IN("Anmeldung"), REFRESH("Sitzung erneuert"), SIGN_OUT("Abmeldung"), REMOVED("Konto entfernt"), GAME_START("Spielstart");

        private final String label;

        Kind(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** One account event; {@code detail} is the Minecraft version for game starts, otherwise empty. */
    public record Event(Instant time, Kind kind, String uuid, String username, String detail) {
    }

    /** A server the game connected to: first and last visit, number of visits, account and version of the last one. */
    public record Server(String address, Instant first, Instant last, int visits, String username, String version) {
    }

    static final class StoredEvent {
        long time;
        String kind;
        String uuid;
        String username;
        String detail;
    }

    static final class StoredServer {
        long first;
        long last;
        int visits;
        String username;
        String version;
    }

    static final class Document {
        List<StoredEvent> events = new ArrayList<>();
        Map<String, StoredServer> servers = new LinkedHashMap<>();
    }

    /** "Connecting to <host>, <port>" - logged by every supported Minecraft version when it joins a server. */
    private static final Pattern CONNECTING = Pattern.compile("Connecting to ([A-Za-z0-9._\\-\\[\\]:]{1,253}), (\\d{1,5})(?!\\d)");
    private static final Pattern HOST = Pattern.compile("[A-Za-z0-9._\\-]{1,253}|\\[[0-9A-Fa-f:.]{2,45}\\]|[0-9A-Fa-f:.]{2,45}");

    private final Path file;
    private volatile boolean enabled = true;
    private Document doc;

    public ConnectionHistory(Path file) {
        this.file = file;
    }

    public boolean enabled() {
        return enabled;
    }

    /** Off: nothing new is recorded (what is there stays until it is cleared). */
    public void setEnabled(boolean on) {
        this.enabled = on;
    }

    private Document doc() {
        if (doc == null) {
            Document d = null;
            if (Files.isRegularFile(file)) {
                try {
                    d = Json.read(file, Document.class);
                } catch (IOException | RuntimeException e) {
                    Log.warn("Ignoring unreadable " + file + ": " + e.getMessage());
                }
            }
            if (d == null) {
                d = new Document();
            }
            if (d.events == null) {
                d.events = new ArrayList<>();
            }
            if (d.servers == null) {
                d.servers = new LinkedHashMap<>();
            }
            d.events.removeIf(e -> e == null || kind(e.kind) == null);
            d.servers.values().removeIf(s -> s == null);
            doc = d;
        }
        return doc;
    }

    private static Kind kind(String name) {
        try {
            return name == null ? null : Kind.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Records an account event (no-op when switched off or without an account). */
    public synchronized void record(Kind kind, String uuid, String username, String detail, Instant now) {
        if (!enabled || kind == null || uuid == null || uuid.isBlank()) {
            return;
        }
        List<StoredEvent> events = doc().events;
        if (kind == Kind.REFRESH && !events.isEmpty()) {
            StoredEvent last = events.get(events.size() - 1);
            if (Kind.REFRESH.name().equals(last.kind) && uuid.equals(last.uuid)
                    && now.toEpochMilli() - last.time < MERGE_REFRESH.toMillis()) {
                last.time = now.toEpochMilli();
                save();
                return;
            }
        }
        StoredEvent e = new StoredEvent();
        e.time = now.toEpochMilli();
        e.kind = kind.name();
        e.uuid = uuid;
        e.username = username == null ? "" : username;
        e.detail = detail == null ? "" : detail;
        events.add(e);
        while (events.size() > MAX_EVENTS) {
            events.remove(0);
        }
        save();
    }

    /** Records a server visit; {@code address} is normalised (lower case, default port 25565 left out). */
    public synchronized void recordServer(String address, String username, String version, Instant now) {
        String key = normalise(address);
        if (!enabled || key == null) {
            return;
        }
        Map<String, StoredServer> servers = doc().servers;
        StoredServer s = servers.remove(key);
        if (s == null) {
            s = new StoredServer();
            s.first = now.toEpochMilli();
        }
        s.last = now.toEpochMilli();
        s.visits++;
        s.username = username == null ? "" : username;
        s.version = version == null ? "" : version;
        // most recent last, so the oldest entry is the first one dropped
        servers.put(key, s);
        while (servers.size() > MAX_SERVERS) {
            servers.remove(servers.keySet().iterator().next());
        }
        save();
    }

    /** Account events, newest first. */
    public synchronized List<Event> events() {
        List<Event> out = new ArrayList<>();
        for (StoredEvent e : doc().events) {
            out.add(new Event(Instant.ofEpochMilli(e.time), kind(e.kind), e.uuid, e.username, e.detail == null ? "" : e.detail));
        }
        out.sort(Comparator.comparing(Event::time).reversed());
        return out;
    }

    /** Servers, most recently visited first. */
    public synchronized List<Server> servers() {
        List<Server> out = new ArrayList<>();
        doc().servers.forEach((address, s) -> out.add(new Server(address, Instant.ofEpochMilli(s.first), Instant.ofEpochMilli(s.last),
                s.visits, s.username == null ? "" : s.username, s.version == null ? "" : s.version)));
        out.sort(Comparator.comparing(Server::last).reversed());
        return out;
    }

    /** First and last event of an account (for the sessions table), or null if it has none. */
    public synchronized Instant[] span(String uuid) {
        Instant first = null;
        Instant last = null;
        for (StoredEvent e : doc().events) {
            if (uuid.equals(e.uuid)) {
                Instant t = Instant.ofEpochMilli(e.time);
                first = first == null || t.isBefore(first) ? t : first;
                last = last == null || t.isAfter(last) ? t : last;
            }
        }
        return first == null ? null : new Instant[]{first, last};
    }

    public synchronized void removeServer(String address) {
        String key = normalise(address);
        if (key != null && doc().servers.remove(key) != null) {
            save();
        }
    }

    public synchronized void clearEvents() {
        doc().events.clear();
        save();
    }

    public synchronized void clearServers() {
        doc().servers.clear();
        save();
    }

    private void save() {
        try {
            Json.write(file, doc);
        } catch (IOException e) {
            Log.warn("Could not save the connection history: " + e.getMessage());
        }
    }

    /** The server address in a game log line ("Connecting to host, port"), or null. */
    public static String serverFromLog(String line) {
        if (line == null || line.length() > 4096 || !line.contains("Connecting to ")) {
            return null;
        }
        Matcher m = CONNECTING.matcher(line);
        if (!m.find()) {
            return null;
        }
        return normalise(m.group(1) + ":" + m.group(2));
    }

    /** "Play.Example.net:25565" -> "play.example.net"; null for anything that is not a host with an optional port. */
    static String normalise(String address) {
        if (address == null) {
            return null;
        }
        String a = address.strip().toLowerCase(Locale.ROOT);
        String host = a;
        String port = null;
        if (a.startsWith("[")) {
            // [IPv6] or [IPv6]:port
            int end = a.indexOf(']');
            if (end < 0) {
                return null;
            }
            host = a.substring(0, end + 1);
            String rest = a.substring(end + 1);
            if (!rest.isEmpty()) {
                if (!rest.startsWith(":")) {
                    return null;
                }
                port = rest.substring(1);
            }
        } else if (a.indexOf(':') >= 0 && a.indexOf(':') == a.lastIndexOf(':')) {
            // host:port (several colons: a bare IPv6 address without port)
            host = a.substring(0, a.indexOf(':'));
            port = a.substring(a.indexOf(':') + 1);
        }
        int p = 25565;
        if (port != null) {
            if (!port.matches("\\d{1,5}")) {
                return null;
            }
            p = Integer.parseInt(port);
        }
        if (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        if (host.isEmpty() || p < 1 || p > 65535 || !HOST.matcher(host).matches()) {
            return null;
        }
        return p == 25565 ? host : host + ":" + p;
    }
}
