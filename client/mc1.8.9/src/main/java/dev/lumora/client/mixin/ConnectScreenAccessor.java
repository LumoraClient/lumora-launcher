package dev.lumora.client.mixin;

import net.minecraft.client.gui.screen.ConnectScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.network.ClientConnection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lumora connection screens: state of Minecraft's connect screen (read) and its cancel flag (write). */
@Mixin(ConnectScreen.class)
public interface ConnectScreenAccessor {
    @Accessor("connection")
    ClientConnection lumora$connection();

    @Accessor("connectingCancelled")
    void lumora$setCancelled(boolean cancelled);

    @Accessor("parent")
    Screen lumora$parent();
}
