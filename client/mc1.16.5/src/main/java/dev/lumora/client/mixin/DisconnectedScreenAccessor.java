package dev.lumora.client.mixin;

import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lumora disconnect screen: reason and return screen of Minecraft's disconnect screen. */
@Mixin(DisconnectedScreen.class)
public interface DisconnectedScreenAccessor {
    @Accessor("parent")
    Screen lumora$parent();

    @Accessor("reason")
    net.minecraft.network.chat.Component lumora$reason();
}
