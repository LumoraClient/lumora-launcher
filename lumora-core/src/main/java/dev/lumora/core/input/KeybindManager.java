package dev.lumora.core.input;

import dev.lumora.core.io.Json;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.lumora.api.i18n.Translations;
import dev.lumora.api.input.Keybind;
import dev.lumora.api.input.KeybindRegistry;
import dev.lumora.api.input.Keys;
import dev.lumora.core.CoreLogger;
import dev.lumora.core.RegistrationTracker;
import dev.lumora.core.io.AtomicFiles;
import dev.lumora.core.io.DebouncedFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.function.LongSupplier;

/**
 * Runtime keybinds. Keys are polled once per tick (edge detected, so one press = one action). Keys chosen by the
 * user are stored in {@code config/lumora/keybinds.json} - also for keybinds whose addon is currently disabled.
 */
public final class KeybindManager {
    private final KeyPlatform keys;
    private final Translations translations;
    private final CoreLogger log;
    private final Map<String, Integer> stored = new LinkedHashMap<>();
    private final List<Bind> binds = new CopyOnWriteArrayList<>();
    private final DebouncedFile file;
    private final LongSupplier ticks;

    public KeybindManager(KeyPlatform keys, Translations translations, CoreLogger log, Path path, Executor io, LongSupplier ticks) {
        this.keys = keys;
        this.translations = translations;
        this.log = log;
        this.ticks = ticks;
        this.file = new DebouncedFile(path, this::serialize, io, log);
        load(path);
    }

    private void load(Path path) {
        if (!Files.isRegularFile(path)) {
            return;
        }
        try {
            JsonObject o = Json.parse(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                try {
                    int k = e.getValue().getAsInt();
                    if (Keys.isValid(k)) {
                        stored.put(e.getKey(), k);
                    }
                } catch (RuntimeException ignored) {
                    // single bad entry: skip it
                }
            }
        } catch (IOException | RuntimeException e) {
            log.warn("Invalid " + path + ", keybinds use their defaults (backup: " + AtomicFiles.backup(path, "corrupt") + ")");
        }
    }

    private synchronized String serialize() {
        JsonObject o = new JsonObject();
        for (Map.Entry<String, Integer> e : stored.entrySet()) {
            o.addProperty(e.getKey(), e.getValue());
        }
        return o.toString();
    }

    final class Bind implements Keybind {
        final String id;
        final String nameKey;
        final int defaultKey;
        final Runnable onPress;
        final String owner;
        volatile int key;
        volatile boolean active = true;
        boolean wasDown;
        boolean down;

        Bind(String id, String nameKey, int defaultKey, Runnable onPress, String owner) {
            this.id = id;
            this.nameKey = nameKey;
            this.defaultKey = defaultKey;
            this.onPress = onPress;
            this.owner = owner;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String nameKey() {
            return nameKey;
        }

        @Override
        public int defaultKey() {
            return defaultKey;
        }

        @Override
        public int key() {
            return key;
        }

        @Override
        public boolean setKey(int newKey) {
            if (newKey != Keys.UNBOUND && !Keys.isBindable(newKey)) {
                return false;
            }
            if (newKey == key) {
                return true;
            }
            if (conflict(newKey, this).isPresent()) {
                return false;
            }
            key = newKey;
            synchronized (KeybindManager.this) {
                if (newKey == defaultKey) {
                    stored.remove(id);
                } else {
                    stored.put(id, newKey);
                }
            }
            file.markDirty(ticks.getAsLong());
            return true;
        }

        @Override
        public Optional<String> conflictWith(int k) {
            return conflict(k, this);
        }

        @Override
        public boolean isDown() {
            return down;
        }

        @Override
        public void unregister() {
            active = false;
            binds.remove(this);
        }

        @Override
        public boolean isActive() {
            return active;
        }
    }

    /** Registers on behalf of {@code owner}; see {@link KeybindRegistry#register}. */
    public Keybind register(String owner, String localId, String nameKey, int defaultKey, Runnable onPress) {
        Objects.requireNonNull(onPress, "onPress");
        if (localId == null || !localId.matches("[a-z0-9_.-]{1,48}")) {
            throw new IllegalArgumentException("invalid keybind id: " + localId);
        }
        if (!Keys.isValid(defaultKey) || defaultKey == Keys.ESCAPE) {
            throw new IllegalArgumentException("invalid default key for " + localId + ": " + defaultKey);
        }
        String id = owner + ":" + localId;
        for (Bind b : binds) {
            if (b.id.equals(id)) {
                throw new IllegalArgumentException("duplicate keybind: " + id);
            }
        }
        Bind bind = new Bind(id, nameKey, defaultKey, onPress, owner);
        Integer saved;
        synchronized (this) {
            saved = stored.get(id);
        }
        int wanted = saved != null ? saved : defaultKey;
        Optional<String> clash = conflict(wanted, bind);
        if (clash.isPresent()) {
            log.warn("Keybind " + id + ": key " + keys.keyName(wanted) + " is already used by \"" + clash.get() + "\" - starting unbound");
            wanted = Keys.UNBOUND;
        }
        bind.key = wanted;
        binds.add(bind);
        return bind;
    }

    Optional<String> conflict(int key, Bind self) {
        if (key == Keys.UNBOUND) {
            return Optional.empty();
        }
        for (Bind b : binds) {
            if (b != self && b.active && b.key == key) {
                return Optional.of(translations.translate(b.nameKey));
            }
        }
        String mc = keys.minecraftBindings().get(key);
        return Optional.ofNullable(mc);
    }

    /** Client thread, every tick. */
    public void tick() {
        boolean accept = keys.acceptsKeybinds();
        for (Bind b : binds) {
            boolean d = accept && b.key != Keys.UNBOUND && keys.isDown(b.key);
            b.down = d;
            if (d && !b.wasDown) {
                try {
                    b.onPress.run();
                } catch (RuntimeException | LinkageError e) {
                    log.warn("Keybind " + b.id + " failed", e);
                }
            }
            b.wasDown = d;
        }
        file.tick(ticks.getAsLong());
    }

    public void flush() {
        if (file.dirty()) {
            file.saveNow();
        }
    }

    /** All active keybinds (for the settings screens). */
    public List<Keybind> all() {
        return List.copyOf(binds);
    }

    public List<Keybind> of(String owner) {
        List<Keybind> out = new ArrayList<>();
        for (Bind b : binds) {
            if (b.owner.equals(owner)) {
                out.add(b);
            }
        }
        return out;
    }

    public String keyName(int key) {
        return key == Keys.UNBOUND ? translations.translateOr("lumora.key.unbound", "-") : keys.keyName(key);
    }

    /** The addon's view; registrations are tracked and need the KEYBINDS permission (checked by the guard). */
    public KeybindRegistry scoped(String owner, RegistrationTracker tracker, Runnable guard) {
        return new KeybindRegistry() {
            @Override
            public Keybind register(String id, String nameKey, int defaultKey, Runnable onPress) {
                guard.run();
                return tracker.track(KeybindManager.this.register(owner, id, nameKey, defaultKey, onPress));
            }

            @Override
            public List<Keybind> keybinds() {
                return of(owner);
            }

            @Override
            public String keyName(int key) {
                return KeybindManager.this.keyName(key);
            }
        };
    }
}
