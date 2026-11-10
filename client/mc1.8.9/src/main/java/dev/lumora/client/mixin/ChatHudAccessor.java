package dev.lumora.client.mixin;

import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** Chat lines for the chat module's message menu and resize frame. */
@Mixin(ChatHud.class)
public interface ChatHudAccessor {
    @Accessor("visibleMessages")
    List<ChatHudLine> lumora$visible();

    @Accessor("messages")
    List<ChatHudLine> lumora$all();

    @Accessor("scrolledLines")
    int lumora$scroll();
}
