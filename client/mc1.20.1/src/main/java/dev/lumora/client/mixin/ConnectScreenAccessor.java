package dev.lumora.client.mixin;

import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lumora connection screens: state of Minecraft's connect screen (read) and its cancel flag (write). */
@Mixin(ConnectScreen.class)
public interface ConnectScreenAccessor {
    @Accessor("connection")
    Connection lumora$connection();

    @Accessor("aborted")
    void lumora$setAborted(boolean aborted);

    @Accessor("status")
    Component lumora$status();

    @Accessor("parent")
    Screen lumora$parent();

    @Accessor("channelFuture")
    io.netty.channel.ChannelFuture lumora$channelFuture();

    @Accessor("channelFuture")
    void lumora$setChannelFuture(io.netty.channel.ChannelFuture future);
}
