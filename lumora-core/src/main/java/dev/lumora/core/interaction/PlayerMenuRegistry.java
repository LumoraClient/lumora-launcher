package dev.lumora.core.interaction;

import dev.lumora.api.Registration;
import dev.lumora.api.interaction.PlayerMenu;
import dev.lumora.api.interaction.PlayerMenuEntry;
import dev.lumora.api.interaction.PlayerTarget;
import dev.lumora.core.CoreLogger;
import dev.lumora.core.RegistrationTracker;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Entries of the player menu: Lumora's own (owner {@code lumora}) and those of addons. A failing condition or label is
 * logged once and the entry is left out from then on, so one broken addon never breaks the menu.
 */
public final class PlayerMenuRegistry {
    /** An entry with the addon that added it. */
    public record Owned(String owner, PlayerMenuEntry entry) {
        /** {@code owner:id}. */
        public String key() {
            return owner + ":" + entry.id();
        }
    }

    private final CoreLogger log;
    private final CopyOnWriteArrayList<Owned> entries = new CopyOnWriteArrayList<>();
    private final Set<String> broken = ConcurrentHashMap.newKeySet();

    public PlayerMenuRegistry(CoreLogger log) {
        this.log = log;
    }

    /** Adds one of Lumora's own entries. */
    public void builtin(PlayerMenuEntry entry) {
        add("lumora", entry);
    }

    private Owned add(String owner, PlayerMenuEntry entry) {
        if (entry == null) {
            throw new IllegalArgumentException("entry");
        }
        for (Owned o : entries) {
            if (o.owner.equals(owner) && o.entry.id().equals(entry.id())) {
                throw new IllegalArgumentException("player menu entry " + owner + ":" + entry.id() + " already exists");
            }
        }
        Owned o = new Owned(owner, entry);
        entries.add(o);
        return o;
    }

    public PlayerMenu scoped(String addon, RegistrationTracker tracker, Runnable guard) {
        return new PlayerMenu() {
            @Override
            public Registration add(PlayerMenuEntry entry) {
                guard.run();
                Owned o = PlayerMenuRegistry.this.add(addon, entry);
                return tracker.track(RegistrationTracker.of(() -> {
                    entries.remove(o);
                    broken.remove(o.key());
                }));
            }

            @Override
            public List<PlayerMenuEntry> entries() {
                List<PlayerMenuEntry> l = new ArrayList<>();
                for (Owned o : entries) {
                    if (o.owner.equals(addon)) {
                        l.add(o.entry);
                    }
                }
                return l;
            }
        };
    }

    /** All entries shown for {@code target}: Lumora's first, then by order, addon and id. */
    public List<Owned> visible(PlayerTarget target) {
        List<Owned> l = new ArrayList<>();
        for (Owned o : entries) {
            if (broken.contains(o.key())) {
                continue;
            }
            try {
                if (o.entry.visibleFor(target)) {
                    l.add(o);
                }
            } catch (RuntimeException | LinkageError e) {
                fail(o, e);
            }
        }
        l.sort(Comparator.comparing((Owned o) -> !o.owner.equals("lumora")).thenComparingInt(o -> o.entry.order())
                .thenComparing(o -> o.owner).thenComparing(o -> o.entry.id()));
        return l;
    }

    /** The label, or the id if the label fails. */
    public String label(Owned o, PlayerTarget target) {
        try {
            return o.entry.label(target);
        } catch (RuntimeException | LinkageError e) {
            fail(o, e);
            return o.entry.id();
        }
    }

    public boolean enabled(Owned o, PlayerTarget target) {
        try {
            return o.entry.enabledFor(target);
        } catch (RuntimeException | LinkageError e) {
            fail(o, e);
            return false;
        }
    }

    /** Runs the entry; a failure is logged. */
    public void select(Owned o, PlayerTarget target) {
        try {
            o.entry.select(target);
        } catch (RuntimeException | LinkageError e) {
            log.warn("Player menu entry " + o.key() + " failed", e);
        }
    }

    private void fail(Owned o, Throwable e) {
        if (broken.add(o.key())) {
            log.warn("Player menu entry " + o.key() + " failed and is left out", e);
        }
    }
}
