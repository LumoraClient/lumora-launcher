package dev.lumora.core.extras.light;

/**
 * Read access to the client world for the module "Light Block Overlay", implemented once per Minecraft version.
 * Everything is called on the client thread.
 */
public interface LightProbe {
    /** A spawn surface found by {@link #scan}: x and z inside the chunk (0..15), y is the block the mob stands in. */
    interface Sink {
        void mark(int x, int y, int z, int blockLight, int skyLight);
    }

    /** The loaded world (another object after a world or dimension change), or null without a world. */
    Object world();

    /**
     * Block position of the camera entity (x, y, z) and its facing (0 south, 1 west, 2 north, 3 east) written to
     * {@code out}; false without one.
     */
    boolean player(int[] out);

    /**
     * Reports every position in the chunk column between {@code minY} and {@code maxY} where a zombie-sized hostile mob
     * could spawn by its blocks: the block below has a spawnable top, the block itself and the one above are free.
     * False if the chunk is not loaded.
     */
    boolean scan(int chunkX, int chunkZ, int minY, int maxY, Sink sink);
}
