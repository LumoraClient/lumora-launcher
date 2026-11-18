package dev.lumora.api.building;

/**
 * What a block is like, for filters such as the colour picker's: a full cube (collision shape fills the block), solid,
 * opaque (hides what is behind it) and the same texture on all six sides.
 *
 * @since 1.12.0
 */
public record BlockTraits(boolean fullCube, boolean solid, boolean opaque, boolean sameTexture) {
    /** Unknown block or a version without the building tools. */
    public static final BlockTraits NONE = new BlockTraits(false, false, false, false);
}
