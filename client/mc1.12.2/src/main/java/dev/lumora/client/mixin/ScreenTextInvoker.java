package dev.lumora.client.mixin;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Hover text and click of chat text (Lumora chat windows draw the chat themselves). */
@Mixin(Screen.class)
public interface ScreenTextInvoker {
    @Invoker("renderTextHoverEffect")
    void lumora$hover(Text text, int x, int y);

    @Invoker("handleTextClick")
    boolean lumora$click(Text text);
}
