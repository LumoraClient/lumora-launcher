package dev.lumora.launcher.core.install;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import dev.lumora.launcher.core.profile.ProfileCatalog;
import dev.lumora.launcher.core.util.FileUtil;
import dev.lumora.launcher.core.util.Json;
import dev.lumora.launcher.core.util.Log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Turns the launcher's resource packs on in Minecraft's options.txt ("resourcePacks":[...]).
 * <ul>
 *   <li>A pack file is activated once, when it first appears (new pack or new version). If the player later
 *       switches it off in the game, it stays off - the launcher remembers what it activated
 *       (resourcepacks/.lumora-packs.json).</li>
 *   <li>Packs are placed directly above "vanilla"/"fabric", i.e. below the player's own packs and Lumora's theme.</li>
 *   <li>Entries of older, manually installed copies of the same pack ("replaces" prefix) are replaced, the files
 *       themselves are left untouched.</li>
 *   <li>Packs the launcher removed (switched off in the launcher, or replaced by an update) leave the list.</li>
 * </ul>
 */
public final class ResourcePackOptions {
    public static final String STATE_FILE = ".lumora-packs.json";
    private static final String KEY = "resourcePacks:";
    static final String LUMORA_THEME = "lumora:lumora_theme";

    static final class State {
        List<String> activated = new ArrayList<>();
        /** The launcher switched the Lumora theme on once (versions where Fabric does not enable it by itself). */
        boolean themeActivated;
    }

    private ResourcePackOptions() {
    }

    public static void apply(Path gameDir, List<ProfileCatalog.Artifact> enabledPacks) throws IOException {
        apply(gameDir, enabledPacks, false);
    }

    /**
     * @param enableTheme also switch the built-in Lumora theme on, once: Fabric API for 26.3 does not activate
     *                    "default enabled" built-in packs by itself. If the player turns it off later, it stays off.
     */
    public static void apply(Path gameDir, List<ProfileCatalog.Artifact> enabledPacks, boolean enableTheme) throws IOException {
        Path options = gameDir.resolve("options.txt");
        Path stateFile = gameDir.resolve("resourcepacks").resolve(STATE_FILE);
        State state = readState(stateFile);
        List<String> lines = Files.isRegularFile(options)
                ? new ArrayList<>(Files.readAllLines(options, StandardCharsets.UTF_8)) : new ArrayList<>();
        int idx = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith(KEY)) {
                idx = i;
                break;
            }
        }
        List<String> packs = idx >= 0 ? parse(lines.get(idx).substring(KEY.length())) : new ArrayList<>(List.of("vanilla"));
        List<String> before = List.copyOf(packs);

        // Minecraft clears the whole list after a resource loading error ("removing all selected resourcepacks").
        // That is no choice of the player: if none of our packs and not even the Lumora theme is left, start over.
        if (!state.activated.isEmpty() && packs.stream().noneMatch(state.activated::contains) && !packs.contains(LUMORA_THEME)) {
            Log.info("All resource packs were removed by the game, activating them again");
            state.activated.clear();
            state.themeActivated = false;
        }
        Set<String> wanted = new LinkedHashSet<>();
        enabledPacks.forEach(a -> wanted.add("file/" + a.fileName));
        // packs the launcher put there earlier but no longer installs
        for (String old : List.copyOf(state.activated)) {
            if (!wanted.contains(old)) {
                packs.remove(old);
                state.activated.remove(old);
            }
        }
        int insertAt = 0;
        for (int i = 0; i < packs.size(); i++) {
            if (packs.get(i).equals("vanilla") || packs.get(i).equals("fabric")) {
                insertAt = i + 1;
            }
        }
        for (ProfileCatalog.Artifact a : enabledPacks) {
            String entry = "file/" + a.fileName;
            if (state.activated.contains(entry)) {
                continue; // activated before: respect what the player chose in the game since
            }
            if (a.replaces != null && !a.replaces.isBlank()) {
                packs.removeIf(p -> p.startsWith("file/" + a.replaces) && !p.equals(entry));
            }
            if (!packs.contains(entry)) {
                packs.add(Math.min(insertAt, packs.size()), entry);
                insertAt++;
            }
            state.activated.add(entry);
        }
        if (enableTheme && !state.themeActivated) {
            if (!packs.contains(LUMORA_THEME)) {
                packs.add(LUMORA_THEME); // last = on top of every other pack, like Fabric would place it
            }
            state.themeActivated = true;
        }
        if (!packs.equals(before) || idx < 0) {
            String line = KEY + Json.GSON.toJson(packs).replaceAll("\\s+", "");
            if (idx >= 0) {
                lines.set(idx, line);
            } else {
                lines.add(line);
            }
            FileUtil.writeAtomically(options, (String.join("\n", lines) + "\n").getBytes(StandardCharsets.UTF_8));
            Log.info("Resource packs in options.txt: " + packs);
        }
        Files.createDirectories(stateFile.getParent());
        Json.write(stateFile, state);
    }

    static List<String> parse(String json) {
        List<String> out = new ArrayList<>();
        try {
            JsonArray a = Json.GSON.fromJson(json, JsonArray.class);
            if (a != null) {
                for (JsonElement e : a) {
                    if (e.isJsonPrimitive()) {
                        out.add(e.getAsString());
                    }
                }
            }
        } catch (JsonParseException | IllegalStateException e) {
            Log.warn("Unreadable resourcePacks line in options.txt, keeping vanilla only: " + e.getMessage());
            out.add("vanilla");
        }
        return out;
    }

    private static State readState(Path file) {
        if (!Files.isRegularFile(file)) {
            return new State();
        }
        try {
            State s = Json.read(file, State.class);
            if (s == null || s.activated == null) {
                return new State();
            }
            s.activated.removeIf(x -> x == null || !x.startsWith("file/"));
            return s;
        } catch (IOException | RuntimeException e) {
            return new State();
        }
    }
}
