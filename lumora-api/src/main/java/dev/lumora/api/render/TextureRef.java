package dev.lumora.api.render;

import java.util.Objects;

/**
 * Reference to a texture in a resource pack or mod jar ({@code assets/<namespace>/<path>}), e.g.
 * {@code new TextureRef("session-timer", "textures/gui/clock.png")}. Cheap to keep; create it once, not per frame.
 */
public final class TextureRef {
    private final String namespace;
    private final String path;

    public TextureRef(String namespace, String path) {
        if (namespace == null || !namespace.matches("[a-z0-9_.-]+")) {
            throw new IllegalArgumentException("invalid namespace: " + namespace);
        }
        if (path == null || !path.matches("[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("invalid path: " + path);
        }
        this.namespace = namespace;
        this.path = path;
    }

    public String namespace() {
        return namespace;
    }

    public String path() {
        return path;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof TextureRef t && t.namespace.equals(namespace) && t.path.equals(path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespace, path);
    }

    @Override
    public String toString() {
        return namespace + ":" + path;
    }
}
