package dev.lumora.client.mixin;

import dev.lumora.core.extras.ChatModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Lumora chat module: how long messages stay in the closed chat (Minecraft: 200 ticks). */
@Mixin(targets = "net.minecraft.client.gui.components.ChatComponent$AlphaCalculator")
public interface ChatFadeMixin {
    // dev jar: method_75805, game (Mojang names at runtime): lambda$timeBased$1
    @ModifyConstant(method = {"method_75805", "lambda$timeBased$1"}, constant = @Constant(doubleValue = 200.0))
    private static double lumora$fade(double ticks) {
        return ChatModule.fadeTicks(ticks);
    }
}
