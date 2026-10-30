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

import java.util.List;

/** Lumora chat module: timestamps (grey, legacy chat has no RGB) and identical messages stacked into one line. */
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
}
