package dev.lumora.core.cosmetics;

import dev.lumora.core.cosmetics.model.CosmeticMesh;

/**
 * Everything the version adapter draws for one player this frame: the emote pose and the cosmetic meshes with
 * texture and render mode. Owned by the core and reused; valid until the same player's next frame.
 */
public final class PlayerFrame {
    /** One cosmetic to draw. */
    public static final class Item {
        CosmeticMesh mesh;
        String texture;
        RenderMode render;
        String id;

        public CosmeticMesh mesh() {
            return mesh;
        }

        /** Texture id {@code namespace:path}. */
        public String texture() {
            return texture;
        }

        public RenderMode render() {
            return render;
        }

        public String id() {
            return id;
        }
    }

    final EmotePose pose = new EmotePose();
    private Item[] items = new Item[0];
    private int count;

    public EmotePose pose() {
        return pose;
    }

    /** Number of cosmetics to draw. */
    public int count() {
        return count;
    }

    public Item item(int index) {
        return items[index];
    }

    /** {@code true} if there is nothing to do for this player. */
    public boolean isEmpty() {
        return count == 0 && !pose.active();
    }

    void clearItems() {
        count = 0;
    }

    void add(String id, CosmeticMesh mesh, String texture, RenderMode render) {
        if (count == items.length) {
            items = java.util.Arrays.copyOf(items, Math.max(4, count * 2));
            for (int i = count; i < items.length; i++) {
                items[i] = new Item();
            }
        }
        Item it = items[count++];
        it.id = id;
        it.mesh = mesh;
        it.texture = texture;
        it.render = render;
    }
}
