package dev.lumora.core.addon;

import dev.lumora.api.addon.AddonDescriptor;
import dev.lumora.api.addon.AddonInfo;
import dev.lumora.api.addon.AddonState;
import dev.lumora.api.addon.LumoraAddon;
import dev.lumora.core.RegistrationTracker;

import java.util.Optional;

/** Everything the manager knows about one addon. */
public final class AddonRecord implements AddonInfo {
    final AddonDescriptor descriptor;
    final AddonCandidate candidate;
    volatile AddonState state = AddonState.LOADED;
    volatile String error;
    volatile boolean wanted = true;
    /** Switched off because a required addon was switched off; comes back with it. */
    boolean disabledByDependency;
    LumoraAddon instance;
    AddonContextImpl context;
    AddonConfigurationImpl configuration;
    final RegistrationTracker tracker = new RegistrationTracker();

    AddonRecord(AddonDescriptor descriptor, AddonCandidate candidate) {
        this.descriptor = descriptor;
        this.candidate = candidate;
    }

    @Override
    public AddonDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public AddonState state() {
        return state;
    }

    @Override
    public Optional<String> error() {
        return Optional.ofNullable(error);
    }

    @Override
    public boolean wanted() {
        return wanted;
    }

    public AddonConfigurationImpl configuration() {
        return configuration;
    }

    /** Active registrations (diagnostics/tests). */
    public int registrations() {
        return tracker.size();
    }

    @Override
    public String toString() {
        return descriptor.id() + " " + descriptor.version() + " [" + state + "]";
    }
}
