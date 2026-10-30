package dev.lumora.core.addon;

import dev.lumora.core.io.Json;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.lumora.api.addon.AddonConfiguration;
import dev.lumora.api.config.SettingsGroup;
import dev.lumora.api.event.config.SettingChangedEvent;
import dev.lumora.core.CoreLogger;
import dev.lumora.core.config.SettingsBinder;
import dev.lumora.core.event.SimpleEventBus;
import dev.lumora.core.io.AtomicFiles;
import dev.lumora.core.io.DebouncedFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.LongSupplier;

/**
 * Addon-wide settings in {@code config/lumora/addons/<id>.json}. Values of settings the addon no longer defines
 * are kept, so a downgrade or a temporarily removed setting does not lose them.
 */
public final class AddonConfigurationImpl implements AddonConfiguration {
    private final SettingsGroup settings;
    private final Map<String, String> values = new LinkedHashMap<>();
    private final DebouncedFile file;
    private final SettingsBinder binder;
    private final LongSupplier ticks;

    public AddonConfigurationImpl(String addonId, Path path, SimpleEventBus events, Executor io, CoreLogger log, LongSupplier ticks) {
        this.settings = new SettingsGroup(addonId + ".setting.");
        this.ticks = ticks;
        load(path, log);
        this.file = new DebouncedFile(path, this::serialize, io, log);
        this.binder = new SettingsBinder(settings, () -> values, s -> {
            file.markDirty(ticks.getAsLong());
            events.post(new SettingChangedEvent(addonId, s));
        });
    }

    private void load(Path path, CoreLogger log) {
        if (!Files.isRegularFile(path)) {
            return;
        }
        try {
            JsonElement root = Json.parse(Files.readString(path, StandardCharsets.UTF_8));
            if (root instanceof JsonObject o) {
                for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                    if (e.getValue() instanceof JsonPrimitive p) {
                        values.put(e.getKey(), p.getAsString());
                    }
                }
                return;
            }
        } catch (IOException | RuntimeException e) {
            // handled below
        }
        log.warn("Invalid addon settings " + path + ", using defaults (backup: " + AtomicFiles.backup(path, "corrupt") + ")");
    }

    private String serialize() {
        JsonObject o = new JsonObject();
        for (Map.Entry<String, String> e : values.entrySet()) {
            o.addProperty(e.getKey(), e.getValue());
        }
        return new GsonBuilder().setPrettyPrinting().create().toJson(o);
    }

    @Override
    public SettingsGroup settings() {
        return settings;
    }

    @Override
    public void save() {
        file.saveAsync();
    }

    public void tick() {
        file.tick(ticks.getAsLong());
    }

    public void flush() {
        if (file.dirty()) {
            file.saveNow();
        }
    }

    /** Stored text values (for tests). */
    public Map<String, String> values() {
        return values;
    }

    public void close() {
        binder.close();
    }
}
