package dev.lumora.api.addon;

/** Thrown when an addon uses a service whose permission it did not declare. */
public final class AddonPermissionException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public AddonPermissionException(String addonId, AddonPermission permission) {
        super("Addon '" + addonId + "' needs the permission \"" + permission.id() + "\" in lumora-addon.json");
    }
}
