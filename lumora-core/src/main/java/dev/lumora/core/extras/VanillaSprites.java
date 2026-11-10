package dev.lumora.core.extras;

import dev.lumora.api.render.HudGraphics;
import dev.lumora.api.render.TextureRef;
import dev.lumora.core.addon.VersionSupport;

import java.util.HashMap;
import java.util.Map;

/**
 * Minecraft's own HUD graphics (hearts, armor, food, air, hotbar, experience bar) drawn from the game's textures:
 * from 1.20.2 on every icon is its own file under {@code textures/gui/sprites/hud/}, before that they are regions of
 * {@code textures/gui/icons.png} and {@code textures/gui/widgets.png}.
 */
final class VanillaSprites {
    static final TextureRef ICONS = new TextureRef("minecraft", "textures/gui/icons.png");
    static final TextureRef WIDGETS = new TextureRef("minecraft", "textures/gui/widgets.png");

    /** Single sprite files (1.20.2+). */
    final boolean sprites;
    /** Empty air bubbles exist (1.21.4+). */
    final boolean emptyAir;
    /** Frozen hearts exist (1.17+). */
    final boolean frozen;
    /** The offhand slot exists (1.9+). */
    final boolean offhand;
    private final Map<String, TextureRef> cache = new HashMap<>();

    VanillaSprites(String minecraft) {
        String mc = minecraft == null || minecraft.isEmpty() ? "26.3" : minecraft;
        sprites = VersionSupport.compare(mc, "1.20.2") >= 0;
        emptyAir = VersionSupport.compare(mc, "1.21.4") >= 0;
        frozen = VersionSupport.compare(mc, "1.17") >= 0;
        offhand = VersionSupport.compare(mc, "1.9") >= 0;
    }

    private TextureRef sprite(String name) {
        return cache.computeIfAbsent(name, n -> new TextureRef("minecraft", "textures/gui/sprites/hud/" + n + ".png"));
    }

    private void icon(HudGraphics g, String sprite, int u, int v, int x, int y) {
        if (sprites) {
            g.texture(sprite(sprite), x, y, 9, 9, 9, 9);
        } else {
            g.texture(ICONS, x, y, 9, 9, u, v, 9, 9, 256, 256);
        }
    }

    /**
     * One heart.
     *
     * @param type "container", "normal", "poisoned", "withered", "frozen" or "absorbing"
     */
    void heart(HudGraphics g, int x, int y, String type, boolean half, boolean hardcore) {
        String t = "frozen".equals(type) && !frozen ? "normal" : type;
        String name;
        int index;
        switch (t) {
            case "container" -> {
                name = hardcore ? "heart/container_hardcore" : "heart/container";
                index = 0;
            }
            case "poisoned" -> {
                name = "heart/poisoned_" + (hardcore ? "hardcore_" : "") + (half ? "half" : "full");
                index = 4;
            }
            case "withered" -> {
                name = "heart/withered_" + (hardcore ? "hardcore_" : "") + (half ? "half" : "full");
                index = 6;
            }
            case "absorbing" -> {
                name = "heart/absorbing_" + (hardcore ? "hardcore_" : "") + (half ? "half" : "full");
                index = 8;
            }
            case "frozen" -> {
                name = "heart/frozen_" + (hardcore ? "hardcore_" : "") + (half ? "half" : "full");
                index = 9;
            }
            default -> {
                name = "heart/" + (hardcore ? "hardcore_" : "") + (half ? "half" : "full");
                index = 2;
            }
        }
        // icons.png: hearts in row 0 (hardcore row 5), x = 16 + (index * 2 + half) * 9
        int u = 16 + (index * 2 + ("container".equals(t) ? 0 : half ? 1 : 0)) * 9;
        icon(g, name, u, hardcore ? 45 : 0, x, y);
    }

    /** One armor icon: 2 = full, 1 = half, 0 = empty. */
    void armor(HudGraphics g, int x, int y, int amount) {
        String name = amount >= 2 ? "armor_full" : amount == 1 ? "armor_half" : "armor_empty";
        icon(g, name, amount >= 2 ? 34 : amount == 1 ? 25 : 16, 9, x, y);
    }

    /** One food icon: 2 = full, 1 = half, 0 = empty; {@code hunger}: the greenish icons of the hunger effect. */
    void food(HudGraphics g, int x, int y, int amount, boolean hunger) {
        String base = amount >= 2 ? "food_full" : amount == 1 ? "food_half" : "food_empty";
        int u = amount >= 2 ? (hunger ? 88 : 52) : amount == 1 ? (hunger ? 97 : 61) : (hunger ? 133 : 16);
        icon(g, hunger ? base + "_hunger" : base, u, 27, x, y);
    }

    /** One air bubble: 2 = full, 1 = bursting, 0 = empty (only drawn where Minecraft has the sprite). */
    void air(HudGraphics g, int x, int y, int amount) {
        if (amount <= 0) {
            if (emptyAir) {
                g.texture(sprite("air_empty"), x, y, 9, 9, 9, 9);
            }
            return;
        }
        icon(g, amount >= 2 ? "air" : "air_bursting", amount >= 2 ? 16 : 25, 18, x, y);
    }

    /** The hotbar strip (182 x 22); with {@code slot} &ge; 0 only that slot's 22 px wide part (for a vertical bar). */
    void hotbar(HudGraphics g, int x, int y, int slot) {
        int u = slot < 0 ? 0 : slot * 20;
        int w = slot < 0 ? 182 : 22;
        if (sprites) {
            g.texture(sprite("hotbar"), x, y, w, 22, u, 0, w, 22, 182, 22);
        } else {
            g.texture(WIDGETS, x, y, w, 22, u, 0, w, 22, 256, 256);
        }
    }

    /** Frame around the selected slot (24 wide, 23 or 24 high). */
    void selection(HudGraphics g, int x, int y) {
        if (sprites) {
            g.texture(sprite("hotbar_selection"), x, y, 24, 23, 24, 23);
        } else {
            g.texture(WIDGETS, x, y, 24, 24, 0, 22, 24, 24, 256, 256);
        }
    }

    /** The offhand slot (29 x 24), opening to the left or right. */
    void offhand(HudGraphics g, int x, int y, boolean left) {
        if (!offhand) {
            return;
        }
        if (sprites) {
            g.texture(sprite(left ? "hotbar_offhand_left" : "hotbar_offhand_right"), x, y, 29, 24, 29, 24);
        } else {
            g.texture(WIDGETS, x, y, 29, 24, left ? 24 : 53, 22, 29, 24, 256, 256);
        }
    }

    /** Experience bar (182 x 5) filled to {@code progress} (0..1). */
    void experience(HudGraphics g, int x, int y, float progress) {
        int filled = Math.round(Math.max(0, Math.min(1, progress)) * 183);
        if (sprites) {
            g.texture(sprite("experience_bar_background"), x, y, 182, 5, 182, 5);
            if (filled > 0) {
                g.texture(sprite("experience_bar_progress"), x, y, Math.min(182, filled), 5, 0, 0, Math.min(182, filled), 5, 182, 5);
            }
        } else {
            g.texture(ICONS, x, y, 182, 5, 0, 64, 182, 5, 256, 256);
            if (filled > 0) {
                g.texture(ICONS, x, y, Math.min(182, filled), 5, 0, 69, Math.min(182, filled), 5, 256, 256);
            }
        }
    }
}
