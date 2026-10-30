package dev.lumora.api.addon;

import java.util.Optional;

/** Read-only view of a known addon. */
public interface AddonInfo {
    AddonDescriptor descriptor();

    AddonState state();

    /** Reason for {@link AddonState#FAILED} (or why it cannot be enabled). */
    Optional<String> error();

    /** The user's choice (stored in {@code config/lumora/addons.json}); a failed addon can still be wanted. */
    boolean wanted();

    default String id() {
        return descriptor().id();
    }
}
