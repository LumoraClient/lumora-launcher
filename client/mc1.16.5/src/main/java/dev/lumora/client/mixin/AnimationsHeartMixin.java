package dev.lumora.client.mixin;

import dev.lumora.core.extras.anim.HeartBlink;
import net.minecraft.client.gui.Gui;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Lumora "Animations", "Hearts": 1.7 blinking of Minecraft's hearts (see {@link HeartBlink}). 1.16.5 draws the hearts
 * inside {@code renderPlayerHealth}, so only its two reads change: {@code healthBlinkTime} (whether the hearts blink)
 * and {@code displayHealth} (the white "lost" part).
 */
@Mixin(Gui.class)
public abstract class AnimationsHeartMixin {
    @Shadow
    private int tickCount;
    @Shadow
    private long healthBlinkTime;
    @Shadow
    private int displayHealth;

    @Shadow
    private Player getCameraPlayer() {
        throw new AssertionError("mixin");
    }

    @Redirect(method = "renderPlayerHealth", at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/Gui;healthBlinkTime:J",
            opcode = Opcodes.GETFIELD))
    private long lumora$blink(Gui gui) {
        Player player = this.getCameraPlayer();
        if (!HeartBlink.legacy() || player == null) {
            return this.healthBlinkTime;
        }
        // Minecraft blinks while healthBlinkTime > tickCount and (healthBlinkTime - tickCount) / 3 % 2 == 1
        return HeartBlink.blink(player.invulnerableTime) ? this.tickCount + 3L : 0L;
    }

    @Redirect(method = "renderPlayerHealth", at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/Gui;displayHealth:I",
            opcode = Opcodes.GETFIELD))
    private int lumora$oldHealth(Gui gui) {
        Player player = this.getCameraPlayer();
        if (!HeartBlink.legacy() || player == null) {
            return this.displayHealth;
        }
        return HeartBlink.oldHealth(Mth.ceil(player.getHealth()));
    }
}
