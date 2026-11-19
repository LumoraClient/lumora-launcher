package dev.lumora.client.mixin;

import dev.lumora.core.extras.anim.HeartBlink;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Gui;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Lumora "Animations", "Hearts": 1.7 blinking of Minecraft's hearts (see {@link HeartBlink}). Only the two values
 * handed to {@code renderHearts} change: the white "lost" part and whether the hearts blink.
 */
@Mixin(Gui.class)
public abstract class AnimationsHeartMixin {
    private static final String HEARTS = "Lnet/minecraft/client/gui/Gui;renderHearts(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/entity/player/Player;IIIIFIIIZ)V";

    @ModifyArg(method = "renderPlayerHealth", at = @At(value = "INVOKE", target = HEARTS), index = 8)
    private int lumora$oldHealth(GuiGraphics graphics, Player player, int xLeft, int yLineBase, int rowHeight, int regenIndex,
                                 float maxHealth, int currentHealth, int oldHealth, int absorption, boolean blink) {
        return HeartBlink.legacy() ? HeartBlink.oldHealth(currentHealth) : oldHealth;
    }

    @ModifyArg(method = "renderPlayerHealth", at = @At(value = "INVOKE", target = HEARTS), index = 10)
    private boolean lumora$blink(GuiGraphics graphics, Player player, int xLeft, int yLineBase, int rowHeight, int regenIndex,
                                 float maxHealth, int currentHealth, int oldHealth, int absorption, boolean blink) {
        return HeartBlink.legacy() ? HeartBlink.blink(player.invulnerableTime) : blink;
    }
}
