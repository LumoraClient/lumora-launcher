package dev.lumora.core.extras.light;

/**
 * The finished geometry of one chunk column for the version renderers: line segments and quads, built once when the
 * chunk is scanned or a setting changes, never per frame. x and z are relative to the chunk corner
 * ({@link #originX}, {@link #originZ}) so that floats stay exact far away from the world origin; y is the world height.
 * The renderer subtracts the camera position in double precision once per chunk.
 */
public final class LightMesh {
    public final int originX;
    public final int originZ;
    /** Segments: x1, y1, z1, x2, y2, z2 each. */
    public float[] lines = new float[48];
    /** ARGB per segment. */
    public int[] lineColors = new int[8];
    public int lineCount;
    /** Quads: four corners of x, y, z each (counter-clockwise seen from above / outside). */
    public float[] quads = new float[48];
    /** ARGB per quad. */
    public int[] quadColors = new int[4];
    public int quadCount;

    LightMesh(int originX, int originZ) {
        this.originX = originX;
        this.originZ = originZ;
    }

    void clear() {
        lineCount = 0;
        quadCount = 0;
    }

    boolean isEmpty() {
        return lineCount == 0 && quadCount == 0;
    }

    void line(float x1, float y1, float z1, float x2, float y2, float z2, int color) {
        if ((lineCount + 1) * 6 > lines.length) {
            lines = java.util.Arrays.copyOf(lines, lines.length * 2);
            lineColors = java.util.Arrays.copyOf(lineColors, lineColors.length * 2);
        }
        int i = lineCount * 6;
        lines[i] = x1;
        lines[i + 1] = y1;
        lines[i + 2] = z1;
        lines[i + 3] = x2;
        lines[i + 4] = y2;
        lines[i + 5] = z2;
        lineColors[lineCount++] = color;
    }

    void quad(float[] corners, int color) {
        if ((quadCount + 1) * 12 > quads.length) {
            quads = java.util.Arrays.copyOf(quads, quads.length * 2);
            quadColors = java.util.Arrays.copyOf(quadColors, quadColors.length * 2);
        }
        System.arraycopy(corners, 0, quads, quadCount * 12, 12);
        quadColors[quadCount++] = color;
    }
}
