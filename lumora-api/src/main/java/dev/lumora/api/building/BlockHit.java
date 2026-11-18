package dev.lumora.api.building;

/**
 * The block under the crosshair: position, the clicked side ({@code down}, {@code up}, {@code north}, {@code south},
 * {@code west}, {@code east}), the exact point that was hit and the block itself.
 *
 * @since 1.10.0
 */
public record BlockHit(int x, int y, int z, String face, double hitX, double hitY, double hitZ, BlockInfo block) {
    /** The same click aimed at another block position (keeps side and the point relative to the block). */
    public BlockHit at(int nx, int ny, int nz, BlockInfo other) {
        return new BlockHit(nx, ny, nz, face, hitX - x + nx, hitY - y + ny, hitZ - z + nz, other);
    }
}
