package dev.lumora.core;

import dev.lumora.api.Registration;

import java.util.ArrayList;
import java.util.List;

/** Collects the registrations of one owner (an addon) and removes them all at once, newest first. */
public final class RegistrationTracker {
    private final List<Registration> registrations = new ArrayList<>();

    public synchronized <R extends Registration> R track(R registration) {
        if (registrations.size() >= 256) {
            registrations.removeIf(r -> !r.isActive());
        }
        registrations.add(registration);
        return registration;
    }

    public synchronized int size() {
        int n = 0;
        for (Registration r : registrations) {
            if (r.isActive()) {
                n++;
            }
        }
        return n;
    }

    /** Unregisters everything; a failing unregister is logged and does not stop the others. */
    public void closeAll(CoreLogger log, String owner) {
        List<Registration> copy;
        synchronized (this) {
            copy = new ArrayList<>(registrations);
            registrations.clear();
        }
        for (int i = copy.size() - 1; i >= 0; i--) {
            try {
                copy.get(i).unregister();
            } catch (RuntimeException | LinkageError e) {
                log.warn("Could not remove a registration of " + owner, e);
            }
        }
    }

    /** Simple registration backed by a flag and an action. */
    public static Registration of(Runnable onUnregister) {
        return new Registration() {
            private volatile boolean active = true;

            @Override
            public void unregister() {
                if (active) {
                    active = false;
                    onUnregister.run();
                }
            }

            @Override
            public boolean isActive() {
                return active;
            }
        };
    }
}
