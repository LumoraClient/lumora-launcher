package dev.lumora.core.addon;

import dev.lumora.api.addon.AddonDescriptor;
import dev.lumora.api.addon.LumoraAddon;

import java.nio.file.Path;

/**
 * A found addon before validation: its descriptor (or the reason it could not be read) and how to create the
 * entry point. The Fabric adapter builds these from {@code lumora-addon.json} files of mods, {@link JarAddonLoader}
 * from the jars in the {@code addons/} folder ({@code jar} set); built-in addons pass a factory.
 */
public record AddonCandidate(AddonDescriptor descriptor, String error, String source, Factory factory, Path jar) {
    @FunctionalInterface
    public interface Factory {
        LumoraAddon create() throws ReflectiveOperationException;
    }

    public AddonCandidate(AddonDescriptor descriptor, String error, String source, Factory factory) {
        this(descriptor, error, source, factory, null);
    }

    public static AddonCandidate of(AddonDescriptor descriptor, Factory factory) {
        return new AddonCandidate(descriptor, null, descriptor.source(), factory, null);
    }

    /** An addon from its own jar in the addon folder (loaded by Lumora, not by a mod loader). */
    public static AddonCandidate fromJar(AddonDescriptor descriptor, Factory factory, Path jar) {
        return new AddonCandidate(descriptor, null, descriptor.source(), factory, jar);
    }

    /** Metadata could not be read; the addon is listed as failed. */
    public static AddonCandidate broken(String source, String error) {
        return new AddonCandidate(null, error, source, null, null);
    }
}
