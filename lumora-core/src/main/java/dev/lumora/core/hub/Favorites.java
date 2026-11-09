package dev.lumora.core.hub;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.lumora.core.io.AtomicFiles;
import dev.lumora.core.io.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Executor;

/** Module cards marked with the heart in the hub (card ids like "addon:lumora-crosshair"), kept in favorites.json. */
public final class Favorites {
    private final Path file;
    private final Executor io;
    private final Set<String> ids = new LinkedHashSet<>();

    /**
     * @param file null keeps the favorites in memory only
     * @param io   where the file is written (the IO thread; tests run it directly)
     */
    public Favorites(Path file, Executor io) {
        this.file = file;
        this.io = io;
        load();
    }

    public synchronized boolean contains(String id) {
        return ids.contains(id);
    }

    public synchronized Set<String> all() {
        return Set.copyOf(ids);
    }

    public synchronized boolean isEmpty() {
        return ids.isEmpty();
    }

    /** Marks or unmarks a card; returns whether it is a favorite now. */
    public boolean toggle(String id) {
        boolean now;
        String json;
        synchronized (this) {
            now = ids.add(id) || !ids.remove(id);
            JsonArray a = new JsonArray();
            ids.forEach(s -> a.add(new JsonPrimitive(s)));
            json = a.toString();
        }
        if (file != null) {
            io.execute(() -> {
                try {
                    Files.createDirectories(file.getParent());
                    AtomicFiles.write(file, json);
                } catch (IOException ignored) {
                    // kept in memory; written again with the next change
                }
            });
        }
        return now;
    }

    private void load() {
        if (file == null || !Files.isRegularFile(file)) {
            return;
        }
        try {
            JsonElement e = Json.parse(Files.readString(file, StandardCharsets.UTF_8));
            if (e != null && e.isJsonArray()) {
                for (JsonElement x : e.getAsJsonArray()) {
                    if (x.isJsonPrimitive()) {
                        ids.add(x.getAsString());
                    }
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // unreadable: start without favorites
        }
    }
}
