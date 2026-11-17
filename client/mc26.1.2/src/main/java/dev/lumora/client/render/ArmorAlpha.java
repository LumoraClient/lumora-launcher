package dev.lumora.client.render;

/**
 * Module "Hidden Armor": opacity of the armor piece Minecraft is drawing right now (1 outside of one), set by
 * HiddenArmorMixin around each piece and read where the piece picks its render type, colour and glint. Render
 * thread only.
 */
public final class ArmorAlpha {
    public static float current = 1f;

    private ArmorAlpha() {
    }

    /** {@code argb} with its alpha scaled by the current opacity. */
    public static int color(int argb) {
        if (current >= 1f) {
            return argb;
        }
        int alpha = Math.round(((argb >>> 24) & 0xFF) * current);
        return alpha << 24 | (argb & 0xFFFFFF);
    }

    /** The module's slot index of an equipment slot, -1 for slots without armor. */
    public static int slot(net.minecraft.world.entity.EquipmentSlot s) {
        return switch (s) {
            case HEAD -> dev.lumora.core.extras.HiddenArmorModule.HEAD;
            case CHEST -> dev.lumora.core.extras.HiddenArmorModule.CHEST;
            case LEGS -> dev.lumora.core.extras.HiddenArmorModule.LEGS;
            case FEET -> dev.lumora.core.extras.HiddenArmorModule.FEET;
            default -> -1;
        };
    }
}
