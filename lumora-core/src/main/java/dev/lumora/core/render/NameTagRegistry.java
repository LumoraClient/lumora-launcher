package dev.lumora.core.render;

import dev.lumora.api.Registration;
import dev.lumora.api.render.NameTags;
import dev.lumora.core.CoreLogger;
import dev.lumora.core.RegistrationTracker;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Name tag icons of all addons. The version adapter's name tag and tab list mixins ask {@link #icon} for every drawn
 * name; the first decorator with an icon wins. A failing decorator is logged once and skipped from then on.
 */
public final class NameTagRegistry {
    private record Entry(String addon, NameTags.Decorator decorator) {}

    private final CoreLogger log;
    private final CopyOnWriteArrayList<Entry> entries = new CopyOnWriteArrayList<>();
    private final Set<NameTags.Decorator> broken = ConcurrentHashMap.newKeySet();

    public NameTagRegistry(CoreLogger log) {
        this.log = log;
    }

    /** Whether any decorator is registered (lets the mixins skip all work otherwise). */
    public boolean active() {
        return !entries.isEmpty();
    }

    public Optional<NameTags.Icon> icon(UUID player, NameTags.Place place) {
        if (player == null) {
            return Optional.empty();
        }
        for (Entry e : entries) {
            if (broken.contains(e.decorator)) {
                continue;
            }
            try {
                Optional<NameTags.Icon> icon = e.decorator.icon(player, place);
                if (icon != null && icon.isPresent()) {
                    return icon;
                }
            } catch (RuntimeException | LinkageError error) {
                broken.add(e.decorator);
                log.warn("Name tag decorator of " + e.addon + " failed and is skipped", error);
            }
        }
        return Optional.empty();
    }

    /** Lines above and below the name tag of {@code player} (API 1.7); the first decorator with lines wins. */
    public Optional<NameTags.Lines> lines(UUID player) {
        if (player == null) {
            return Optional.empty();
        }
        for (Entry e : entries) {
            if (broken.contains(e.decorator)) {
                continue;
            }
            try {
                Optional<NameTags.Lines> lines = e.decorator.lines(player);
                if (lines != null && lines.isPresent() && !lines.get().isEmpty()) {
                    return lines;
                }
            } catch (RuntimeException | LinkageError error) {
                broken.add(e.decorator);
                log.warn("Name tag decorator of " + e.addon + " failed and is skipped", error);
            }
        }
        return Optional.empty();
    }

    public NameTags scoped(String addon, RegistrationTracker tracker, Runnable guard) {
        return decorator -> {
            guard.run();
            if (decorator == null) {
                throw new IllegalArgumentException("decorator");
            }
            Entry entry = new Entry(addon, decorator);
            entries.add(entry);
            Registration r = RegistrationTracker.of(() -> {
                entries.remove(entry);
                broken.remove(decorator);
            });
            return tracker.track(r);
        };
    }
}
