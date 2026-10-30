package dev.lumora.client.mixin;

import dev.lumora.core.extras.ChatModule;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.GuiMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
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
    private List<GuiMessage> allMessages;

    @Shadow
    private void refreshTrimmedMessages() {
        throw new AssertionError("mixin");
    }

    @ModifyVariable(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Component lumora$chat(Component message) {
        ChatModule c = ChatModule.active();
        if (c == null || message == null) {
            return message;
        }
        ChatModule.Line line = c.onMessage(message.getString());
        Component out = message;
        if (line.replacePrevious() && !allMessages.isEmpty()) {
            allMessages.remove(0); // newest first: the same message one line up
            refreshTrimmedMessages();
            out = message.copy().append(Component.literal(" (x" + line.count() + ")").withStyle(ChatFormatting.GRAY));
        }
        if (line.timestamp() != null) {
            int rgb = c.timestampColor() & 0xFFFFFF;
            MutableComponent stamp = Component.literal(line.timestamp() + " ").withStyle(s -> s.withColor(TextColor.fromRgb(rgb)));
            out = Component.empty().append(stamp).append(out);
        }
        return out;
    }
}
