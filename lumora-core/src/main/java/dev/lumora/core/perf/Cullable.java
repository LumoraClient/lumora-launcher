package dev.lumora.core.perf;

/**
 * An entity or block entity the occlusion culler decides about. The version adapters add this to Minecraft's entity
 * and block entity classes (one int field); the culling thread writes, the render thread reads. The result and the
 * number of the pass that computed it share one int, so the render thread never sees one without the other:
 * {@code pass << 1 | (hidden ? 1 : 0)}, 0 = never checked.
 */
public interface Cullable {
    int lumora$cull();

    void lumora$cull(int state);
}
