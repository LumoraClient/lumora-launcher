package dev.lumora.client.mixin;

import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Input field of the chat ("Privat antworten" in the chat module's message menu). */
@Mixin(ChatScreen.class)
public interface ChatScreenAccessor {
    @Accessor("chatField")
    TextFieldWidget lumora$input();
}
