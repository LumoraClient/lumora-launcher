package dev.lumora.client.mixin;

import dev.lumora.core.extras.ChatModule;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
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
    private List<GuiMessage> allMessages;

    @Shadow
    private void refreshTrimmedMessages() {
        throw new AssertionError("mixin");
    }

    @ModifyVariable(method = "addMessage", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Component lumora$chat(Component message) {
        // every message for addons (Lumora API 1.5), also while the chat module is off
        dev.lumora.core.chat.ChatApi.received(message == null ? null : message.getString());
        ChatModule c = ChatModule.active();
        if (c == null || message == null) {
            return dev.lumora.client.render.ChatEmojis.decorate(message);
        }
        ChatModule.Line line = c.onMessage(message.getString());
        Component shown = lumora$decorated(c, message, line);
        ChatModule.shown(shown, line.replacePrevious());
        return shown;
    }

    private Component lumora$decorated(ChatModule c, Component message, ChatModule.Line line) {
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
        return dev.lumora.client.render.ChatEmojis.decorate(out);
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

    @ModifyConstant(method = {"addMessageToDisplayQueue", "addMessageToQueue"}, constant = @Constant(intValue = 100))
    private int lumora$history(int max) {
        return ChatModule.history(max);
    }

    @Redirect(method = "extractRenderState(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;"))
    private Object lumora$opacity(net.minecraft.client.OptionInstance<?> option) {
        Object value = option.get();
        if (value instanceof Double d && option == net.minecraft.client.Minecraft.getInstance().options.textBackgroundOpacity()) {
            return ChatModule.backgroundOpacity(d);
        }
        return value;
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V", at = @At("HEAD"), cancellable = true)
    private void lumora$drawStart(net.minecraft.client.gui.GuiGraphicsExtractor graphics, net.minecraft.client.gui.Font font, int ticks,
                                  int mouseX, int mouseY, ChatComponent.DisplayMode mode, boolean insertions, CallbackInfo ci) {
        ChatModule.renderingChat(true);
        if (ChatModule.drawsChat()) {
            // Lumora's chat windows (tabs, heads) instead of Minecraft's chat
            try {
                ChatModule.renderChat(dev.lumora.client.api.LumoraRuntime.graphics(graphics), graphics.guiWidth(), graphics.guiHeight(), mode.foreground);
            } finally {
                ChatModule.renderingChat(false);
            }
            ci.cancel();
        }
    }

    /** Lumora chat windows: Minecraft's own line positions are not on screen, so clicks find nothing there. */
    @Inject(method = "captureClickableText", at = @At("HEAD"), cancellable = true)
    private void lumora$clickable(net.minecraft.client.gui.ActiveTextCollector collector, int screenHeight, int ticks,
                                  ChatComponent.DisplayMode mode, CallbackInfo ci) {
        if (ChatModule.drawsChat()) {
            ci.cancel();
        }
    }

    @Inject(method = "clearMessages", at = @At("HEAD"))
    private void lumora$cleared(boolean history, CallbackInfo ci) {
        ChatModule.chatCleared();
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V", at = @At("RETURN"))
    private void lumora$drawEnd(CallbackInfo ci) {
        ChatModule.renderingChat(false);
    }
}
