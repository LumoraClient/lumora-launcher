package dev.lumora.api.resource;

import dev.lumora.api.render.TextureRef;

/**
 * Resources of the active resource packs and addon jars. An addon jar's {@code assets/<namespace>/...} folder is part
 * of the always active resource pack "Lumora-Addons", so textures and translations ({@code assets/<namespace>/lang/*.json})
 * work without any registration.
 */
public interface Resources {
    /** Reference to {@code assets/<namespace>/<path>}; create once and keep. */
    default TextureRef texture(String namespace, String path) {
        return new TextureRef(namespace, path);
    }

    /** Whether the resource exists in the active packs (in-memory lookup, no disk access). */
    boolean exists(String namespace, String path);
}
