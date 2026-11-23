package dev.lumora.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Hover text of chat text (Lumora chat windows draw the chat themselves). */
@Mixin(Screen.class)
public interface ScreenHoverInvoker {
    @Invoker("renderComponentHoverEffect")
    void lumora$hover(PoseStack pose, Style style, int x, int y);
}
