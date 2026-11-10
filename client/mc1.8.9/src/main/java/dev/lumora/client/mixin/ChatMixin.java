package dev.lumora.client.mixin;

import dev.lumora.core.extras.ChatModule;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** Lumora chat module: timestamps (grey, legacy chat has no RGB) and identical messages stacked into one line, chat size, background, text shadow, fade time and history. */
@Mixin(ChatHud.class)
public abstract class ChatMixin {
    @Shadow
    @Final
    private List<ChatHudLine> messages;

    @Shadow
    public void reset() {
        throw new AssertionError("mixin");
    }

    @ModifyVariable(method = "addMessage(Lnet/minecraft/text/Text;)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Text lumora$chat(Text message) {
        ChatModule c = ChatModule.active();
        if (c == null || message == null) {
            return message;
        }
        ChatModule.Line line = c.onMessage(message.asUnformattedString());
        Text out = message;
        if (line.replacePrevious() && !messages.isEmpty()) {
            messages.remove(0); // newest first: the same message one line up
            reset();
            out = message.copy().append(new LiteralText(" (x" + line.count() + ")").setStyle(new Style().setFormatting(Formatting.GRAY)));
        }
        if (line.timestamp() != null) {
            out = new LiteralText("").append(new LiteralText(line.timestamp() + " ").setStyle(new Style().setFormatting(Formatting.GRAY))).append(out);
        }
        return out;
    }

    // --- Lumora chat module: size, background, text shadow, fade time, history

    @Shadow
    public abstract boolean isChatFocused();

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

    @ModifyConstant(method = "addMessage(Lnet/minecraft/text/Text;IIZ)V", constant = @Constant(intValue = 100))
    private int lumora$history(int max) {
        return ChatModule.history(max);
    }

    /** Line background: Minecraft uses half the text alpha (chat opacity and fading); Lumora's opacity replaces the half. */
    @ModifyArg(method = "render(I)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/ChatHud;fill(IIIII)V", ordinal = 0), index = 4)
    private int lumora$background(int color) {
        int alpha = Math.min(255, (int) Math.round((color >>> 24) * 2 * ChatModule.backgroundOpacity(0.5)));
        return (alpha << 24) | (color & 0xFFFFFF);
    }

    @ModifyConstant(method = "render(I)V", constant = @Constant(intValue = 200))
    private int lumora$visible(int ticks) {
        return ChatModule.fadeTicks(ticks);
    }

    @ModifyConstant(method = "render(I)V", constant = @Constant(doubleValue = 200.0))
    private double lumora$fade(double ticks) {
        return ChatModule.fadeTicks(ticks);
    }

    @Redirect(method = "render(I)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/font/TextRenderer;drawWithShadow(Ljava/lang/String;FFI)I"))
    private int lumora$text(net.minecraft.client.font.TextRenderer font, String text, float x, float y, int color) {
        return font.draw(text, x, y, color, ChatModule.textShadow());
    }
}
