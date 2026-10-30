package dev.lumora.api.addon;

import dev.lumora.api.Version;

import java.util.List;
import java.util.Set;

/**
 * Validated metadata of an addon ({@code lumora-addon.json}):
 * <pre>{@code
 * {
 *   "schema": 1,
 *   "id": "session-timer",                  // [a-z0-9_-], 2-64 chars, unique
 *   "name": "Session Timer",
 *   "version": "1.0.0",
 *   "description": "...",
 *   "authors": ["..."],
 *   "api": "1.0.0",                         // minimum Lumora API version (same major)
 *   "minecraft": ["26.1.2", "1.21.x"],      // supported versions, "*" = all
 *   "entrypoint": "com.example.MyAddon",    // implements LumoraAddon
 *   "depends": { "other-addon": ">=1.0" },  // "lumora" = the Lumora API itself
 *   "optional": { "third": "*" },
 *   "permissions": ["hud", "keybinds", "notifications", "tasks", "game"]
 * }
 * }</pre>
 */
public record AddonDescriptor(
        String id,
        String name,
        Version version,
        String description,
        List<String> authors,
        Version apiVersion,
        List<String> minecraftVersions,
        String entrypoint,
        List<AddonDependency> dependencies,
        Set<AddonPermission> permissions,
        boolean builtIn,
        String source) {

    public AddonDescriptor {
        authors = List.copyOf(authors);
        minecraftVersions = List.copyOf(minecraftVersions);
        dependencies = List.copyOf(dependencies);
        permissions = Set.copyOf(permissions);
    }

    /** {@code true} if this addon declares support for the given Minecraft version. */
    public boolean supportsMinecraft(String minecraft) {
        for (String pattern : minecraftVersions) {
            if (pattern.equals("*") || pattern.equals(minecraft)) {
                return true;
            }
            if ((pattern.endsWith(".x") || pattern.endsWith(".*"))
                    && (minecraft + ".").startsWith(pattern.substring(0, pattern.length() - 1))) {
                return true;
            }
        }
        return false;
    }
}
