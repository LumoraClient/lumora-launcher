package dev.lumora.client.mixin;

import dev.lumora.core.extras.anim.HeartBlink;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Lumora "Animations", "Hearts": 1.7 blinking of Minecraft's hearts (see {@link HeartBlink}). Only the two values
 * handed to {@code extractHearts} change: the white "lost" part and whether the hearts blink.
 */
@Mixin(Hud.class)
public abstract class AnimationsHeartMixin {
    private static final String HEARTS = "Lnet/minecraft/client/gui/Hud;extractHearts(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/entity/player/Player;IIIIFIIIZ)V";

    @ModifyArg(method = "extractPlayerHealth", at = @At(value = "INVOKE", target = HEARTS), index = 8)
    private int lumora$oldHealth(GuiGraphicsExtractor graphics, Player player, int xLeft, int yLineBase, int rowHeight, int regenIndex,
                                 float maxHealth, int currentHealth, int oldHealth, int absorption, boolean blink) {
        return HeartBlink.legacy() ? HeartBlink.oldHealth(currentHealth) : oldHealth;
    }

    @ModifyArg(method = "extractPlayerHealth", at = @At(value = "INVOKE", target = HEARTS), index = 10)
    private boolean lumora$blink(GuiGraphicsExtractor graphics, Player player, int xLeft, int yLineBase, int rowHeight, int regenIndex,
                                 float maxHealth, int currentHealth, int oldHealth, int absorption, boolean blink) {
        return HeartBlink.legacy() ? HeartBlink.blink(player.invulnerableTime) : blink;
    }
}
