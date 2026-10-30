package dev.lumora.launcher.core.servers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.lumora.launcher.core.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Servers of the launcher's server page: the bundled list of well-known public servers ({@code /lumora/servers.json},
 * name, modes, languages only) plus the player's own servers (addresses from the settings), and the player's own
 * ratings (1-5, stored locally in {@code ratings.json}; there is no shared rating service).
 */
public final class ServerDirectory {
    /** {@code custom}: added by the player. */
    public record Entry(String name, String address, List<String> modes, List<String> languages, boolean custom) {
        public String key() {
            return address.toLowerCase(Locale.ROOT);
        }
    }

    private final Path ratingsFile;
    private final Map<String, Integer> ratings = new HashMap<>();

    public ServerDirectory(Path dir) {
        this.ratingsFile = dir.resolve("ratings.json");
        load();
    }

    public static List<Entry> bundled() {
        try (InputStream in = ServerDirectory.class.getResourceAsStream("/lumora/servers.json")) {
            if (in == null) {
                return List.of();
            }
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            Log.warn("Bundled servers.json unreadable: " + e);
            return List.of();
        }
    }

    static List<Entry> parse(String json) {
        List<Entry> out = new ArrayList<>();
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        for (JsonElement e : root.getAsJsonArray("servers")) {
            JsonObject o = e.getAsJsonObject();
            String address = o.get("address").getAsString().strip();
            if (!validAddress(address)) {
                continue;
            }
            out.add(new Entry(o.get("name").getAsString(), address, strings(o, "modes"), strings(o, "languages"), false));
        }
        return out;
    }

    private static List<String> strings(JsonObject o, String key) {
        List<String> l = new ArrayList<>();
        if (o.has(key) && o.get(key).isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray(key)) {
                l.add(e.getAsString());
            }
        }
        return List.copyOf(l);
    }

    /** host[:port] with a plausible host name; no spaces, no leading '-' (it becomes a Quick Play argument). */
    public static boolean validAddress(String a) {
        return a != null && a.matches("[A-Za-z0-9.-]{1,253}(:[0-9]{1,5})?") && !a.startsWith("-") && !a.startsWith(".");
    }

    /** Bundled servers and the player's own (own ones first are not needed: the page sorts by players). */
    public List<Entry> all(List<String> custom) {
        List<Entry> out = new ArrayList<>(bundled());
        for (String a : custom) {
            String addr = a.strip();
            if (validAddress(addr) && out.stream().noneMatch(e -> e.key().equals(addr.toLowerCase(Locale.ROOT)))) {
                out.add(new Entry(addr, addr, List.of(), List.of(), true));
            }
        }
        return out;
    }

    public synchronized int rating(String address) {
        return ratings.getOrDefault(address.toLowerCase(Locale.ROOT), 0);
    }

    /** 1-5, or 0 to remove the rating. */
    public synchronized void rate(String address, int stars) {
        String k = address.toLowerCase(Locale.ROOT);
        if (stars <= 0) {
            ratings.remove(k);
        } else {
            ratings.put(k, Math.min(5, stars));
        }
        save();
    }

    private void load() {
        try {
            if (Files.isRegularFile(ratingsFile)) {
                JsonObject o = JsonParser.parseString(Files.readString(ratingsFile, StandardCharsets.UTF_8)).getAsJsonObject();
                for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                    int v = e.getValue().getAsInt();
                    if (v >= 1 && v <= 5) {
                        ratings.put(e.getKey(), v);
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            Log.warn("Server ratings unreadable, starting empty: " + e);
        }
    }

    private void save() {
        JsonObject o = new JsonObject();
        ratings.forEach(o::addProperty);
        try {
            Files.createDirectories(ratingsFile.getParent());
            Path tmp = ratingsFile.resolveSibling("ratings.json.tmp");
            Files.writeString(tmp, o.toString(), StandardCharsets.UTF_8);
            Files.move(tmp, ratingsFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Log.warn("Saving server ratings failed: " + e);
        }
    }
}
