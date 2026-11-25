package dev.lumora.core.cosmetics.model;

import dev.lumora.core.cosmetics.AttachPoint;

/**
 * The quads of one cosmetic for one frame, grouped by the model part they hang on. Coordinates are in Minecraft's
 * model space relative to that part's pivot, in blocks (pixels / 16, y down), ready to be drawn after the part's own
 * {@code translateAndRotate}. Per vertex: x, y, z, u, v, nx, ny, nz; four vertices per quad. Reused every frame.
 */
public final class CosmeticMesh {
    public static final int STRIDE = 8;

    final float[][] data;
    final int[] vertices;

    CosmeticMesh(int[] quadsPerAttach) {
        int n = AttachPoint.values().length;
        data = new float[n][];
        vertices = new int[n];
        for (int i = 0; i < n; i++) {
            data[i] = new float[quadsPerAttach[i] * 4 * STRIDE];
        }
    }

    /** Number of vertices on this part (a multiple of 4). */
    public int vertices(AttachPoint point) {
        return vertices[point.ordinal()];
    }

    /** Vertex data on this part ({@link #STRIDE} floats per vertex, only the first {@link #vertices} are valid). */
    public float[] data(AttachPoint point) {
        return data[point.ordinal()];
    }

    /** Moves every vertex (blocks, Minecraft model space); used for the player's offset of a cosmetic. */
    public void translate(float dx, float dy, float dz) {
        for (int p = 0; p < data.length; p++) {
            float[] d = data[p];
            for (int v = 0; v < vertices[p]; v++) {
                int o = v * STRIDE;
                d[o] += dx;
                d[o + 1] += dy;
                d[o + 2] += dz;
            }
        }
    }

    public boolean isEmpty() {
        for (int v : vertices) {
            if (v > 0) {
                return false;
            }
        }
        return true;
    }

    /** All vertices on all parts (diagnostics). */
    public int totalVertices() {
        int t = 0;
        for (int v : vertices) {
            t += v;
        }
        return t;
    }
}
