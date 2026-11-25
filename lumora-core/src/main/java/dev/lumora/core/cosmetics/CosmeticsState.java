package dev.lumora.core.cosmetics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.lumora.core.io.AtomicFiles;
import dev.lumora.core.io.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What the player wears and likes, saved in {@code config/lumora/cosmetics.json}: equipped cosmetics, favourite
 * cosmetics and emotes and the emote wheel slots (emote id or empty per slot). Only this client knows it; nothing is
 * sent anywhere.
 */
public final class CosmeticsState {
    public static final int MAX_SLOTS = 10;
    /** Pages of the emote wheel, each with up to {@link #MAX_SLOTS} slots. */
    public static final int MAX_PAGES = 8;
    /** Largest cosmetic offset in pixels per axis (as the sync server allows). */
    public static final float MAX_OFFSET = 8;

    /** Options of one cosmetic: variant id ({@code null}: default) and offset in Bedrock pixels (y up). */
    public record Options(String variant, float x, float y, float z) {
        public static final Options DEFAULT = new Options(null, 0, 0, 0);

        public boolean hasOffset() {
            return x != 0 || y != 0 || z != 0;
        }

        public Options withVariant(String v) {
            return new Options(v, x, y, z);
        }

        public Options withOffset(float nx, float ny, float nz) {
            return new Options(variant, clamp(nx), clamp(ny), clamp(nz));
        }

        static float clamp(float v) {
            float c = Math.max(-MAX_OFFSET, Math.min(MAX_OFFSET, v));
            return Math.round(c * 2) / 2f;
        }
    }

    private final Path file;
    private final Set<String> equipped = new LinkedHashSet<>();
    private final Set<String> favorites = new LinkedHashSet<>();
    private final Set<String> emoteFavorites = new LinkedHashSet<>();
    private final String[] wheel = new String[MAX_PAGES * MAX_SLOTS];
    private final java.util.Map<String, Options> options = new java.util.LinkedHashMap<>();

    public CosmeticsState(Path file) {
        this.file = file;
    }

    public synchronized Set<String> equipped() {
        return new LinkedHashSet<>(equipped);
    }

    public synchronized boolean isEquipped(String id) {
        return equipped.contains(id);
    }

    /**
     * Puts on a cosmetic; others in the same slot come off. Returns the ids taken off.
     */
    public synchronized List<String> equip(CosmeticDefinition d, CosmeticRegistry registry) {
        List<String> off = new ArrayList<>();
        for (String id : new ArrayList<>(equipped)) {
            CosmeticDefinition other = registry.cosmetic(id);
            if (other != null && !id.equals(d.id()) && other.slot().equals(d.slot())) {
                equipped.remove(id);
                off.add(id);
            }
        }
        equipped.add(d.id());
        save();
        return off;
    }

    public synchronized void unequip(String id) {
        if (equipped.remove(id)) {
            save();
        }
    }

    /** Replaces everything worn (the preview's "apply"). */
    public synchronized void setEquipped(Collection<String> ids) {
        equipped.clear();
        equipped.addAll(ids);
        save();
    }

    public synchronized boolean isFavorite(String id) {
        return favorites.contains(id);
    }

    public synchronized void toggleFavorite(String id) {
        if (!favorites.remove(id)) {
            favorites.add(id);
        }
        save();
    }

    public synchronized boolean isEmoteFavorite(String id) {
        return emoteFavorites.contains(id);
    }

    public synchronized void toggleEmoteFavorite(String id) {
        if (!emoteFavorites.remove(id)) {
            emoteFavorites.add(id);
        }
        save();
    }

    /** Emote in wheel slot {@code index}, {@code null} if empty. */
    public synchronized String slot(int index) {
        return index < 0 || index >= wheel.length ? null : wheel[index];
    }

    /** Index of slot {@code i} on wheel page {@code page}. */
    public static int slotIndex(int page, int i) {
        return page * MAX_SLOTS + i;
    }

    /** Variant and offset chosen for a cosmetic ({@link Options#DEFAULT} if none). */
    public synchronized Options options(String id) {
        Options o = options.get(id);
        return o == null ? Options.DEFAULT : o;
    }

    /** Like {@link #setOptions} but without writing the file yet (while a slider is dragged); {@link #flush} writes. */
    public synchronized void setOptionsQuiet(String id, Options o) {
        if (o == null || o.equals(Options.DEFAULT)) {
            options.remove(id);
        } else {
            options.put(id, o);
        }
    }

    /** Writes the file now. */
    public synchronized void flush() {
        save();
    }

    public synchronized void setOptions(String id, Options o) {
        if (o == null || o.equals(Options.DEFAULT)) {
            if (options.remove(id) == null) {
                return;
            }
        } else if (o.equals(options.put(id, o))) {
            return;
        }
        save();
    }

    public synchronized void setSlot(int index, String emoteId) {
        if (index >= 0 && index < wheel.length) {
            wheel[index] = emoteId == null || emoteId.isEmpty() ? null : emoteId;
            save();
        }
    }

    /** Fills empty wheel slots with these emotes (first start). */
    public synchronized void fillEmptySlots(List<String> emoteIds) {
        boolean any = false;
        for (String w : wheel) {
            any |= w != null;
        }
        if (any) {
            return;
        }
        for (int i = 0; i < MAX_SLOTS && i < emoteIds.size(); i++) {
            wheel[i] = emoteIds.get(i);
        }
        save();
    }

    /** Forgets ids that no longer exist (removed own content). */
    public synchronized void prune(CosmeticRegistry registry) {
        boolean changed = equipped.removeIf(id -> registry.cosmetic(id) == null);
        for (int i = 0; i < wheel.length; i++) {
            if (wheel[i] != null && registry.emote(wheel[i]) == null) {
                wheel[i] = null;
                changed = true;
            }
        }
        if (changed) {
            save();
        }
    }

    public synchronized void load() {
        if (file == null || !Files.isRegularFile(file)) {
            return;
        }
        try {
            JsonObject o = Json.parse(new String(Files.readAllBytes(file), StandardCharsets.UTF_8)).getAsJsonObject();
            readSet(o, "equipped", equipped);
            readSet(o, "favorites", favorites);
            readSet(o, "emoteFavorites", emoteFavorites);
            options.clear();
            if (o.has("options") && o.get("options").isJsonObject()) {
                for (java.util.Map.Entry<String, JsonElement> e : o.getAsJsonObject("options").entrySet()) {
                    if (!e.getValue().isJsonObject()) {
                        continue;
                    }
                    JsonObject op = e.getValue().getAsJsonObject();
                    String variant = op.has("variant") && op.get("variant").isJsonPrimitive() ? op.get("variant").getAsString() : null;
                    float[] off = new float[3];
                    if (op.has("offset") && op.get("offset").isJsonArray()) {
                        JsonArray a = op.getAsJsonArray("offset");
                        for (int i = 0; i < 3 && i < a.size(); i++) {
                            off[i] = a.get(i).getAsFloat();
                        }
                    }
                    Options opt = new Options(variant, 0, 0, 0).withOffset(off[0], off[1], off[2]);
                    if (!opt.equals(Options.DEFAULT)) {
                        options.put(e.getKey(), opt);
                    }
                }
            }
            if (o.has("wheel") && o.get("wheel").isJsonArray()) {
                JsonArray a = o.getAsJsonArray("wheel");
                for (int i = 0; i < wheel.length && i < a.size(); i++) {
                    JsonElement e = a.get(i);
                    wheel[i] = e.isJsonPrimitive() && !e.getAsString().isEmpty() ? e.getAsString() : null;
                }
            }
        } catch (IOException | RuntimeException e) {
            // a broken file starts empty; the next change writes a good one
        }
    }

    private static void readSet(JsonObject o, String key, Set<String> into) {
        into.clear();
        if (o.has(key) && o.get(key).isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray(key)) {
                if (e.isJsonPrimitive()) {
                    into.add(e.getAsString());
                }
            }
        }
    }

    private void save() {
        if (file == null) {
            return;
        }
        JsonObject o = new JsonObject();
        o.add("equipped", array(equipped));
        o.add("favorites", array(favorites));
        o.add("emoteFavorites", array(emoteFavorites));
        JsonArray w = new JsonArray();
        for (String s : wheel) {
            w.add(new com.google.gson.JsonPrimitive(s == null ? "" : s));
        }
        o.add("wheel", w);
        JsonObject opts = new JsonObject();
        for (java.util.Map.Entry<String, Options> e : options.entrySet()) {
            JsonObject op = new JsonObject();
            if (e.getValue().variant() != null) {
                op.addProperty("variant", e.getValue().variant());
            }
            if (e.getValue().hasOffset()) {
                JsonArray a = new JsonArray();
                a.add(new com.google.gson.JsonPrimitive(e.getValue().x()));
                a.add(new com.google.gson.JsonPrimitive(e.getValue().y()));
                a.add(new com.google.gson.JsonPrimitive(e.getValue().z()));
                op.add("offset", a);
            }
            opts.add(e.getKey(), op);
        }
        o.add("options", opts);
        try {
            AtomicFiles.write(file, o.toString());
        } catch (IOException | RuntimeException e) {
            // not saved: the choice stays for this session
        }
    }

    private static JsonArray array(Set<String> s) {
        JsonArray a = new JsonArray();
        for (String id : s) {
            a.add(new com.google.gson.JsonPrimitive(id));
        }
        return a;
    }
}
