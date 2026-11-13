package dev.lumora.api.render;

/**
 * Textures an addon makes at runtime from pixels, for example an album cover or a picture it downloaded, drawn like
 * any other texture with {@link HudGraphics#texture}. Each addon has its own names; its textures are freed when it is
 * switched off. No permission needed.
 * <pre>{@code
 * TextureRef cover = context.textures().upload("cover", 128, 128, argbPixels); // in a widget's render
 * if (cover != null) {
 *     g.texture(cover, 0, 0, 32, 32, 128, 128);
 * }
 * }</pre>
 *
 * @since 1.8.0
 */
public interface DynamicTextures {
    /** Largest width and height of one texture. */
    int MAX_SIZE = 512;
    /** How many textures one addon may hold at the same time. */
    int MAX_TEXTURES = 32;

    /**
     * Uploads pixels (ARGB, row by row, {@code width * height} values) as this addon's texture {@code name}
     * ({@code [a-z0-9_.-]}, 1 to 32 characters); an earlier texture with that name is replaced. Call it on the render
     * thread, e.g. in a widget's {@code render}, and only when the picture changed (uploading costs time).
     *
     * @return the reference to draw, or {@code null} if this Minecraft version cannot make textures or the addon
     * already holds {@link #MAX_TEXTURES} others (then draw something else)
     * @throws IllegalArgumentException for an invalid name, a size outside 1..{@link #MAX_SIZE} or too few pixels
     */
    TextureRef upload(String name, int width, int height, int[] argb);

    /** Frees the texture {@code name} (render thread); unknown names are ignored. */
    void release(String name);

    /** Never makes textures (versions or tests without support). */
    DynamicTextures NONE = new DynamicTextures() {
        @Override
        public TextureRef upload(String name, int width, int height, int[] argb) {
            return null;
        }

        @Override
        public void release(String name) {
        }
    };
}
