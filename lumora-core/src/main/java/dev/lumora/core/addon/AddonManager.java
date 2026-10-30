package dev.lumora.core.addon;

import dev.lumora.core.io.Json;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.lumora.api.LumoraAPI;
import dev.lumora.api.Version;
import dev.lumora.api.VersionRange;
import dev.lumora.api.addon.AddonDependency;
import dev.lumora.api.addon.AddonDescriptor;
import dev.lumora.api.addon.AddonInfo;
import dev.lumora.api.addon.AddonState;
import dev.lumora.api.addon.LumoraAddon;
import dev.lumora.api.event.addon.AddonDisabledEvent;
import dev.lumora.api.event.addon.AddonEnabledEvent;
import dev.lumora.api.event.addon.AddonFailedEvent;
import dev.lumora.core.CoreLogger;
import dev.lumora.core.LumoraCore;
import dev.lumora.core.io.AtomicFiles;
import dev.lumora.core.io.DebouncedFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Discovers, validates, orders and runs addons. Order of work in {@link #load}: duplicate ids, API and Minecraft
 * version, required dependencies (present, not failed, matching version; repeated until nothing changes, so
 * failures propagate), dependency cycles, then a topological order (dependencies first). All lifecycle calls
 * happen on the client thread; an exception in one addon marks only that addon (and what requires it) as failed.
 * <p>
 * The dependency id {@code "lumora"} stands for the Lumora API itself and is checked against
 * {@link LumoraAPI#API_VERSION}.
 */
public final class AddonManager {
    public static final String API_ID = "lumora";

    private final LumoraCore core;
    private final CoreLogger log;
    private final Path addonConfigDir;
    private final Set<String> disabledByUser = new LinkedHashSet<>();
    /** Built-in modules that start switched off; once seen, the player's choice counts (state file "seen"). */
    private final Set<String> offByDefault = new LinkedHashSet<>();
    private final Set<String> seen = new LinkedHashSet<>();
    private final DebouncedFile stateFile;
    private final Map<String, AddonRecord> byId = new LinkedHashMap<>();
    private final List<AddonRecord> order = new ArrayList<>();
    private int broken;

    public AddonManager(LumoraCore core, Path stateFile, Path addonConfigDir) {
        this.core = core;
        this.log = core.logger();
        this.addonConfigDir = addonConfigDir;
        loadState(stateFile);
        this.stateFile = new DebouncedFile(stateFile, this::serializeState, core.taskService().io(), log);
    }

    private void loadState(Path file) {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            JsonElement root = Json.parse(Files.readString(file, StandardCharsets.UTF_8));
            if (root instanceof JsonObject o && o.get("disabled") instanceof JsonArray a) {
                for (JsonElement e : a) {
                    disabledByUser.add(e.getAsString());
                }
            }
            if (root instanceof JsonObject o && o.get("seen") instanceof JsonArray s) {
                for (JsonElement e : s) {
                    seen.add(e.getAsString());
                }
            }
        } catch (IOException | RuntimeException e) {
            log.warn("Invalid " + file + ", all addons enabled (backup: " + AtomicFiles.backup(file, "corrupt") + ")");
        }
    }

    private synchronized String serializeState() {
        JsonObject o = new JsonObject();
        JsonArray a = new JsonArray();
        for (String id : disabledByUser) {
            a.add(new JsonPrimitive(id)); // JsonArray#add(String) needs Gson 2.4+, 1.8.9 ships 2.2.4
        }
        o.add("disabled", a);
        JsonArray s = new JsonArray();
        for (String id : seen) {
            s.add(new JsonPrimitive(id));
        }
        o.add("seen", s);
        return o.toString();
    }

    // --- loading

    /** Built-in modules that are switched off the first time they appear (call before {@link #load}). */
    public synchronized void offByDefault(java.util.Collection<String> ids) {
        offByDefault.addAll(ids);
    }

    /** Registers and validates all candidates and computes the load order. Does not create instances yet. */
    public void load(List<AddonCandidate> candidates) {
        List<AddonRecord> found = new ArrayList<>();
        for (AddonCandidate c : candidates) {
            if (c.descriptor() == null) {
                AddonRecord r = new AddonRecord(placeholder(c.source()), c);
                fail(r, c.error(), null);
                found.add(r);
                continue;
            }
            AddonRecord r = new AddonRecord(c.descriptor(), c);
            String cid = c.descriptor().id();
            if (offByDefault.contains(cid) && seen.add(cid)) {
                disabledByUser.add(cid); // first time: off, later the player decides
                stateFile.markDirty(core.taskService().currentTick());
            }
            r.wanted = !disabledByUser.contains(cid);
            AddonRecord existing = byId.get(c.descriptor().id());
            if (existing != null || c.descriptor().id().equals(API_ID) && !c.descriptor().builtIn()) {
                fail(r, "Die ID \"" + c.descriptor().id() + "\" ist bereits vergeben"
                        + (existing != null ? " (" + existing.descriptor.source() + ")" : ""), null);
            } else {
                byId.put(c.descriptor().id(), r);
            }
            found.add(r);
        }
        Version api = Version.parse(LumoraAPI.API_VERSION);
        String mc = core.minecraftVersion();
        for (AddonRecord r : found) {
            if (r.state == AddonState.FAILED) {
                continue;
            }
            Version need = r.descriptor.apiVersion();
            if (need.major() != api.major() || need.compareTo(api) > 0) {
                fail(r, "Benötigt Lumora-API " + need + ", vorhanden ist " + api, null);
            } else if (!r.descriptor.supportsMinecraft(mc)) {
                fail(r, "Unterstützt Minecraft " + mc + " nicht (" + String.join(", ", r.descriptor.minecraftVersions()) + ")", null);
            }
        }
        resolveDependencies(found, api);
        failCycles(found);
        sort(found);
    }

    private AddonDescriptor placeholder(String source) {
        String name = source == null ? "?" : source.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        return new AddonDescriptor("invalid-" + (++broken), name, Version.parse("0.0.0"), "", List.of(), Version.parse("0.0.0"),
                List.of("*"), "", List.of(), Set.of(), false, source == null ? "?" : source);
    }

    private void resolveDependencies(List<AddonRecord> all, Version api) {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (AddonRecord r : all) {
                if (r.state == AddonState.FAILED) {
                    continue;
                }
                for (AddonDependency d : r.descriptor.dependencies()) {
                    String problem = dependencyProblem(d, api);
                    if (problem != null && !d.optional()) {
                        fail(r, problem, null);
                        changed = true;
                        break;
                    }
                    if (problem != null && byId.containsKey(d.id())) {
                        log.warn("Addon " + r.descriptor.id() + ": optional dependency " + d.id() + " ignored (" + problem + ")");
                    }
                }
            }
        }
        for (AddonRecord r : all) {
            if (r.state != AddonState.FAILED) {
                r.state = AddonState.RESOLVED;
            }
        }
    }

    private String dependencyProblem(AddonDependency d, Version api) {
        if (d.id().equals(API_ID)) {
            return d.versions().matches(api) ? null : "Benötigt Lumora-API " + d.versions() + ", vorhanden ist " + api;
        }
        AddonRecord t = byId.get(d.id());
        if (t == null) {
            return "Benötigt das Addon \"" + d.id() + "\" (" + d.versions() + "), es ist nicht installiert";
        }
        if (t.state == AddonState.FAILED) {
            return "Benötigt das Addon \"" + d.id() + "\", das nicht geladen werden konnte";
        }
        if (!d.versions().matches(t.descriptor.version())) {
            return "Benötigt \"" + d.id() + "\" " + d.versions() + ", installiert ist " + t.descriptor.version();
        }
        return null;
    }

    /** Edges used for ordering: required dependencies and optional ones that are present. */
    private List<AddonRecord> edges(AddonRecord r) {
        List<AddonRecord> out = new ArrayList<>();
        for (AddonDependency d : r.descriptor.dependencies()) {
            AddonRecord t = d.id().equals(API_ID) ? null : byId.get(d.id());
            if (t != null && t != r && t.state != AddonState.FAILED && (!d.optional() || d.versions().matches(t.descriptor.version()))) {
                out.add(t);
            }
        }
        return out;
    }

    private void failCycles(List<AddonRecord> all) {
        Map<AddonRecord, Integer> mark = new LinkedHashMap<>();
        for (AddonRecord r : all) {
            if (r.state != AddonState.FAILED && !mark.containsKey(r)) {
                visit(r, mark, new ArrayList<>());
            }
        }
        // anything that requires a failed (cyclic) addon fails too
        resolveDependencies(all, Version.parse(LumoraAPI.API_VERSION));
    }

    private void visit(AddonRecord r, Map<AddonRecord, Integer> mark, List<AddonRecord> path) {
        mark.put(r, 1);
        path.add(r);
        for (AddonRecord t : edges(r)) {
            Integer m = mark.get(t);
            if (m == null) {
                visit(t, mark, path);
            } else if (m == 1) {
                List<AddonRecord> cycle = path.subList(path.indexOf(t), path.size());
                StringBuilder b = new StringBuilder();
                for (AddonRecord c : cycle) {
                    b.append(c.descriptor.id()).append(" → ");
                }
                b.append(t.descriptor.id());
                for (AddonRecord c : new ArrayList<>(cycle)) {
                    fail(c, "Zyklische Abhängigkeit: " + b, null);
                }
            }
        }
        path.remove(path.size() - 1);
        mark.put(r, 2);
    }

    /** Kahn's algorithm; ties keep discovery order. Failed addons go to the end (still listed). */
    private void sort(List<AddonRecord> all) {
        List<AddonRecord> pending = new ArrayList<>();
        for (AddonRecord r : all) {
            if (r.state != AddonState.FAILED) {
                pending.add(r);
            }
        }
        List<AddonRecord> sorted = new ArrayList<>();
        while (!pending.isEmpty()) {
            AddonRecord next = null;
            for (AddonRecord r : pending) {
                if (sorted.containsAll(edges(r))) {
                    next = r;
                    break;
                }
            }
            if (next == null) {
                // cannot happen after failCycles; keep going defensively
                next = pending.get(0);
            }
            pending.remove(next);
            sorted.add(next);
        }
        order.clear();
        order.addAll(sorted);
        for (AddonRecord r : all) {
            if (r.state == AddonState.FAILED) {
                order.add(r);
            }
        }
    }

    // --- lifecycle

    /** Creates instances and calls {@code onInitialize}, in load order. */
    public void initializeAll() {
        for (AddonRecord r : List.copyOf(order)) {
            if (r.state == AddonState.RESOLVED) {
                initialize(r);
            }
        }
    }

    /** Enables every addon the user wants, dependencies first. */
    public void enableAll() {
        for (AddonRecord r : List.copyOf(order)) {
            if (r.wanted && (r.state == AddonState.INITIALIZED || r.state == AddonState.DISABLED)) {
                enable(r);
            }
        }
    }

    private boolean initialize(AddonRecord r) {
        String id = r.descriptor.id();
        if (r.configuration == null) {
            r.configuration = new AddonConfigurationImpl(id, addonConfigDir.resolve(id + ".json"), core.eventBus(),
                    core.taskService().io(), log, core.taskService()::currentTick);
        }
        r.context = new AddonContextImpl(r, core);
        LumoraAddon instance;
        try {
            instance = r.candidate.factory().create();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            fail(r, "Einstiegspunkt " + r.descriptor.entrypoint() + " konnte nicht erstellt werden: " + e, e);
            return false;
        }
        r.instance = instance;
        try {
            instance.onInitialize(r.context);
        } catch (Exception | LinkageError e) {
            fail(r, "Fehler in onInitialize: " + e, e);
            r.instance = null;
            return false;
        }
        r.state = AddonState.INITIALIZED;
        return true;
    }

    private boolean enable(AddonRecord r) {
        if (r.state == AddonState.ENABLED) {
            return true;
        }
        if (r.state == AddonState.FAILED || r.state == AddonState.ENABLING) {
            return false;
        }
        if (r.state == AddonState.UNLOADED && !initialize(r)) {
            return false;
        }
        for (AddonDependency d : r.descriptor.dependencies()) {
            AddonRecord t = byId.get(d.id());
            if (d.optional() || t == null || d.id().equals(API_ID)) {
                continue;
            }
            if (t.state != AddonState.ENABLED) {
                if (!t.wanted) {
                    r.error = "Benötigt das ausgeschaltete Addon \"" + t.descriptor.name() + "\"";
                    r.disabledByDependency = true;
                    return false;
                }
                if (!enable(t)) {
                    r.error = "Benötigt \"" + t.descriptor.name() + "\", das nicht gestartet werden konnte";
                    return false;
                }
            }
        }
        r.state = AddonState.ENABLING;
        try {
            r.instance.onEnable(r.context);
        } catch (Exception | LinkageError e) {
            r.tracker.closeAll(log, r.descriptor.id());
            fail(r, "Fehler in onEnable: " + e, e);
            return false;
        }
        r.state = AddonState.ENABLED;
        r.error = null;
        r.disabledByDependency = false;
        log.info("Addon " + r.descriptor.id() + " " + r.descriptor.version() + " enabled");
        core.eventBus().post(new AddonEnabledEvent(r));
        // bring back addons that were switched off only because this one was off
        for (AddonRecord d : List.copyOf(order)) {
            if (d.disabledByDependency && d.wanted && d.state == AddonState.DISABLED && requires(d, r)) {
                enable(d);
            }
        }
        return true;
    }

    private static boolean requires(AddonRecord r, AddonRecord target) {
        for (AddonDependency d : r.descriptor.dependencies()) {
            if (!d.optional() && !d.id().equals(API_ID) && d.id().equals(target.descriptor.id())) {
                return true;
            }
        }
        return false;
    }

    private void disable(AddonRecord r, boolean becauseOfDependency) {
        if (r.state != AddonState.ENABLED) {
            return;
        }
        for (int i = order.size() - 1; i >= 0; i--) {
            AddonRecord d = order.get(i);
            if (d != r && d.state == AddonState.ENABLED && requires(d, r)) {
                disable(d, true);
            }
        }
        try {
            r.instance.onDisable(r.context);
        } catch (Exception | LinkageError e) {
            log.warn("Addon " + r.descriptor.id() + " failed in onDisable (registrations are removed anyway)", e);
        }
        r.tracker.closeAll(log, r.descriptor.id());
        r.state = AddonState.DISABLED;
        r.disabledByDependency = becauseOfDependency;
        log.info("Addon " + r.descriptor.id() + " disabled");
        core.eventBus().post(new AddonDisabledEvent(r));
    }

    private void fail(AddonRecord r, String reason, Throwable error) {
        r.state = AddonState.FAILED;
        r.error = reason;
        if (error != null) {
            log.error("Addon " + r.descriptor.id() + ": " + reason, error);
        } else {
            log.warn("Addon " + r.descriptor.id() + " (" + r.descriptor.source() + "): " + reason);
        }
        core.eventBus().post(new AddonFailedEvent(r, reason));
    }

    // --- user actions

    /**
     * Switches an addon on or off and remembers the choice. Returns the reason if it could not be enabled.
     * Turning off also turns off addons that require it (they come back with it).
     */
    public Optional<String> setWanted(String id, boolean wanted) {
        AddonRecord r = byId.get(id);
        if (r == null) {
            return Optional.of("Unbekanntes Addon " + id);
        }
        r.wanted = wanted;
        synchronized (this) {
            if (wanted) {
                disabledByUser.remove(id);
            } else {
                disabledByUser.add(id);
            }
        }
        stateFile.markDirty(core.taskService().currentTick());
        if (!wanted) {
            disable(r, false);
            return Optional.empty();
        }
        if (r.state == AddonState.FAILED) {
            return Optional.of(r.error != null ? r.error : "Addon ist fehlgeschlagen");
        }
        return enable(r) ? Optional.empty() : Optional.ofNullable(r.error);
    }

    /** Disables the addon and drops its instance; enabling it again creates a new one. */
    public void unload(String id) {
        AddonRecord r = byId.get(id);
        if (r == null || r.state == AddonState.FAILED) {
            return;
        }
        disable(r, false);
        if (r.state == AddonState.DISABLED || r.state == AddonState.INITIALIZED) {
            r.instance = null;
            r.context = null;
            r.state = AddonState.UNLOADED;
        }
    }

    /** Client thread, every tick: debounced saves. */
    public void tick() {
        long t = core.taskService().currentTick();
        stateFile.tick(t);
        for (AddonRecord r : order) {
            if (r.configuration != null) {
                r.configuration.tick();
            }
        }
    }

    /** Disables everything (newest first) without changing the user's choices; writes pending settings. */
    public void shutdown() {
        for (int i = order.size() - 1; i >= 0; i--) {
            disable(order.get(i), false);
        }
        for (AddonRecord r : order) {
            if (r.configuration != null) {
                r.configuration.flush();
            }
        }
        if (stateFile.dirty()) {
            stateFile.saveNow();
        }
    }

    public List<AddonInfo> addons() {
        return List.copyOf(order);
    }

    public Optional<AddonRecord> record(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    /** Checks a version requirement against an installed addon (for addons that want to adapt to each other). */
    public boolean isEnabled(String id, VersionRange range) {
        AddonRecord r = byId.get(id);
        return r != null && r.state == AddonState.ENABLED && range.matches(r.descriptor.version());
    }
}
