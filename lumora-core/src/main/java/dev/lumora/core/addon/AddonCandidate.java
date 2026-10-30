package dev.lumora.core.addon;

import dev.lumora.api.addon.AddonDescriptor;
import dev.lumora.api.addon.LumoraAddon;

/**
 * A found addon before validation: its descriptor (or the reason it could not be read) and how to create the
 * entry point. The Fabric adapter builds these from {@code lumora-addon.json} files; built-in addons pass a factory.
 */
public record AddonCandidate(AddonDescriptor descriptor, String error, String source, Factory factory) {
    @FunctionalInterface
    public interface Factory {
        LumoraAddon create() throws ReflectiveOperationException;
    }

    public static AddonCandidate of(AddonDescriptor descriptor, Factory factory) {
        return new AddonCandidate(descriptor, null, descriptor.source(), factory);
    }

    /** Metadata could not be read; the addon is listed as failed. */
    public static AddonCandidate broken(String source, String error) {
        return new AddonCandidate(null, error, source, null);
    }
}
