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

import java.util.List;

/** Lumora chat module: timestamps and identical messages stacked into one line. */
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
}
