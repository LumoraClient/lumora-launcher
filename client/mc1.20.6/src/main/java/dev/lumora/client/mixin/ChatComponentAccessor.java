package dev.lumora.client.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

/** Chat lines and size for the chat module's message menu and resize frame. */
@Mixin(ChatComponent.class)
public interface ChatComponentAccessor {
    @Accessor("trimmedMessages")
    List<GuiMessage.Line> lumora$trimmed();

    @Accessor("allMessages")
    List<GuiMessage> lumora$all();

    @Accessor("chatScrollbarPos")
    int lumora$scroll();

    @Invoker("getWidth")
    int lumora$width();

    @Invoker("getHeight")
    int lumora$height();

    @Invoker("getScale")
    double lumora$scale();

    @Invoker("getLineHeight")
    int lumora$lineHeight();
}
