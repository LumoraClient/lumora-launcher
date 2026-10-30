package dev.lumora.api.addon;

import dev.lumora.api.VersionRange;

/** Dependency on another addon (or on {@code "lumora"}, the API itself). Optional dependencies only order the load. */
public record AddonDependency(String id, VersionRange versions, boolean optional) {
}
