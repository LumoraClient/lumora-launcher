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
    private void refreshTrimmedMessage() {
        throw new AssertionError("mixin");
    }

    @ModifyVariable(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
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
            refreshTrimmedMessage();
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

    @ModifyConstant(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;ILnet/minecraft/client/GuiMessageTag;Z)V", constant = @Constant(intValue = 100))
    private int lumora$history(int max) {
        return ChatModule.history(max);
    }

    @Redirect(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;III)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;"))
    private Object lumora$opacity(net.minecraft.client.OptionInstance<?> option) {
        Object value = option.get();
        if (value instanceof Double d && option == net.minecraft.client.Minecraft.getInstance().options.textBackgroundOpacity()) {
            return ChatModule.backgroundOpacity(d);
        }
        return value;
    }

    @ModifyConstant(method = "getTimeFactor", constant = @Constant(doubleValue = 200.0))
    private static double lumora$fade(double ticks) {
        return ChatModule.fadeTicks(ticks);
    }

    @ModifyConstant(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;III)V", constant = @Constant(intValue = 200))
    private int lumora$visible(int ticks) {
        return ChatModule.fadeTicks(ticks);
    }

    @Redirect(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;III)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;drawShadow(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/util/FormattedCharSequence;FFI)I"))
    private int lumora$text(net.minecraft.client.gui.Font font, com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.util.FormattedCharSequence text, float x, float y, int color) {
        return ChatModule.textShadow() ? font.drawShadow(pose, text, x, y, color) : font.draw(pose, text, x, y, color);
    }

    // --- Lumora chat windows (tabs, windows, heads) instead of Minecraft's chat

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;III)V", at = @At("HEAD"), cancellable = true)
    private void lumora$windows(com.mojang.blaze3d.vertex.PoseStack pose, int ticks, int mouseX, int mouseY, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (ChatModule.drawsChat()) {
            dev.lumora.client.compat.GuiGraphics g = dev.lumora.client.compat.GuiGraphics.of(pose);
            ChatModule.renderChat(dev.lumora.client.api.LumoraRuntime.graphics(g), g.guiWidth(), g.guiHeight(), isChatFocused());
            ci.cancel();
        }
    }

    /** Lumora chat windows: Minecraft's own lines are not on screen, so neither hover texts nor clicks find them. */
    @Inject(method = "getClickedComponentStyleAt", at = @At("HEAD"), cancellable = true)
    private void lumora$noStyle(double x, double y, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<net.minecraft.network.chat.Style> cir) {
        if (ChatModule.drawsChat()) {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "handleChatQueueClicked", at = @At("HEAD"), cancellable = true)
    private void lumora$noQueue(double x, double y, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        if (ChatModule.drawsChat()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "clearMessages", at = @At("HEAD"), cancellable = true)
    private void lumora$cleared(boolean history, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (ChatModule.chatCleared(history)) {
            ci.cancel(); // "Keep when leaving": the chat stays
        }
    }
}
