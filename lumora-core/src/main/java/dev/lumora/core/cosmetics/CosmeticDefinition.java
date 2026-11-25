package dev.lumora.core.cosmetics;

import dev.lumora.core.cosmetics.anim.Animation;
import dev.lumora.core.cosmetics.model.GeoModel;

import java.util.Map;

/**
 * One cosmetic: id, texts (language keys for built-in ones, plain text for own ones), category and finer type,
 * the slot it occupies (equipping another one of the same slot takes this one off), rarity, the GeckoLib model and
 * texture, its animations by player state ({@code idle}, {@code walk}, {@code run}, {@code sneak}, {@code air},
 * {@code jump}, {@code fall}, {@code fly}, {@code swim}, {@code emote}) plus an optional overlay animation that
 * always runs on top, and how the preview shows it.
 *
 * @param texture   texture id {@code namespace:path} for built-in ones; for own ones {@code null} until uploaded
 * @param pixels    own texture as ARGB pixels ({@code null} for built-in ones)
 * @param previewYaw angle (degrees) the preview turns the player to, e.g. 180 to show wings from behind
 * @param variants   colours or styles to choose from (empty: only the texture); the first is the default
 */
public record CosmeticDefinition(String id, String name, String description, boolean translated, CosmeticCategory category,
                                 String type, String slot, Rarity rarity, boolean unlocked, GeoModel model,
                                 String texture, int[] pixels, int pixelWidth, int pixelHeight,
                                 Map<String, Animation> animations, Animation overlay, RenderMode render,
                                 float previewYaw, boolean custom, java.util.List<Variant> variants) {

    /** A colour or style of a cosmetic: id ({@code [a-z0-9_-]}), language key (built-in) or name, its texture. */
    public record Variant(String id, String name, String texture) {
    }

    /** The variant with this id, else the first one; {@code null} if the cosmetic has none. */
    public Variant variant(String id) {
        if (variants.isEmpty()) {
            return null;
        }
        for (Variant v : variants) {
            if (v.id().equals(id)) {
                return v;
            }
        }
        return variants.get(0);
    }

    /** {@code true} if it has any animation. */
    public boolean animated() {
        return overlay != null || !animations.isEmpty();
    }
}
