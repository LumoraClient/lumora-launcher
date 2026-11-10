package dev.lumora.client.mixin;

import dev.lumora.core.extras.ChatModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Lumora chat module: chat text without shadow (the chat draws through the GUI's text collector). */
@Mixin(targets = "net.minecraft.client.gui.GuiGraphics$RenderingTextCollector")
public abstract class ChatTextShadowMixin {
    @ModifyArg(method = "accept(Lnet/minecraft/client/gui/TextAlignment;IILnet/minecraft/client/gui/ActiveTextCollector$Parameters;Lnet/minecraft/util/FormattedCharSequence;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/state/GuiTextRenderState;<init>(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;Lorg/joml/Matrix3x2fc;IIIIZZLnet/minecraft/client/gui/navigation/ScreenRectangle;)V"),
            index = 7)
    private boolean lumora$shadow(boolean shadow) {
        return ChatModule.textShadow(shadow);
    }
}
