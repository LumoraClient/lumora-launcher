package dev.lumora.client.mixin;

import dev.lumora.core.extras.ChatModule;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.GuiMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.TextComponent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** Lumora chat module: timestamps, stacked messages, chat size, background, text shadow, fade time and history. */
@Mixin(ChatComponent.class)
public abstract class ChatMixin {
    @Shadow
    @Final
    private List<GuiMessage<Component>> allMessages;

    @Shadow
    public void rescaleChat() {
        throw new AssertionError("mixin");
    }

    @ModifyVariable(method = "addMessage(Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Component lumora$chat(Component message) {
        ChatModule c = ChatModule.active();
        if (c == null || message == null) {
            return message;
        }
        ChatModule.Line line = c.onMessage(message.getString());
        Component out = message;
        if (line.replacePrevious() && !allMessages.isEmpty()) {
            allMessages.remove(0); // newest first: the same message one line up
            rescaleChat();
            out = message.copy().append(new TextComponent(" (x" + line.count() + ")").withStyle(ChatFormatting.GRAY));
        }
        if (line.timestamp() != null) {
            int rgb = c.timestampColor() & 0xFFFFFF;
            MutableComponent stamp = new TextComponent(line.timestamp() + " ").withStyle(s -> s.withColor(TextColor.fromRgb(rgb)));
            out = new TextComponent("").append(stamp).append(out);
        }
        return out;
    }

    // --- Lumora chat module: size, background, text shadow, fade time, history

    @Shadow
    private boolean isChatFocused() {
        throw new AssertionError("mixin");
    }

    @Inject(method = "getWidth()I", at = @At("HEAD"), cancellable = true)
    private void lumora$width(CallbackInfoReturnable<Integer> cir) {
        int w = ChatModule.width();
        if (w > 0) {
            cir.setReturnValue(w);
        }
    }

    @Inject(method = "getHeight()I", at = @At("HEAD"), cancellable = true)
    private void lumora$height(CallbackInfoReturnable<Integer> cir) {
        int h = ChatModule.height(isChatFocused());
        if (h > 0) {
            cir.setReturnValue(h);
        }
    }

    @ModifyConstant(method = "addMessage(Lnet/minecraft/network/chat/Component;IIZ)V", constant = @Constant(intValue = 100))
    private int lumora$history(int max) {
        return ChatModule.history(max);
    }

    @Redirect(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;I)V", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Options;textBackgroundOpacity:D", opcode = org.objectweb.asm.Opcodes.GETFIELD))
    private double lumora$opacity(net.minecraft.client.Options options) {
        return ChatModule.backgroundOpacity(options.textBackgroundOpacity);
    }

    @ModifyConstant(method = "getTimeFactor", constant = @Constant(doubleValue = 200.0))
    private static double lumora$fade(double ticks) {
        return ChatModule.fadeTicks(ticks);
    }

    @ModifyConstant(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;I)V", constant = @Constant(intValue = 200))
    private int lumora$visible(int ticks) {
        return ChatModule.fadeTicks(ticks);
    }

    @Redirect(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;I)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;drawShadow(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/util/FormattedCharSequence;FFI)I"))
    private int lumora$text(net.minecraft.client.gui.Font font, com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.util.FormattedCharSequence text, float x, float y, int color) {
        return ChatModule.textShadow() ? font.drawShadow(pose, text, x, y, color) : font.draw(pose, text, x, y, color);
    }
}
