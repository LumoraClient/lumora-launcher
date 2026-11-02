package dev.lumora.client.api;

import dev.lumora.client.mixin.ConnectScreenAccessor;
import dev.lumora.core.connection.ConnectionAttempt;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConnectScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientLoginNetworkHandler;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * One attempt of Minecraft's connect screen for Lumora's connection tracker (1.12.2): server entry, how far the
 * connection is (by its packet listener; this version has no status line, encryption is read from the connection),
 * the resolved address, and cancelling like the screen's own button. Only reads Minecraft's state.
 */
public final class McConnection implements ConnectionAttempt {
    private final ConnectScreen screen;

    public McConnection(ConnectScreen screen) {
        this.screen = screen;
    }

    private ConnectScreenAccessor acc() {
        return (ConnectScreenAccessor) screen;
    }

    public Screen parent() {
        return acc().lumora$parent();
    }

    @Override
    public String serverName() {
        ServerInfo d = MinecraftClient.getInstance().getCurrentServerEntry();
        return d == null || d.name == null ? "" : d.name;
    }

    @Override
    public String serverAddress() {
        ServerInfo d = MinecraftClient.getInstance().getCurrentServerEntry();
        return d == null || d.address == null ? "" : d.address;
    }

    @Override
    public String stage() {
        ClientConnection c = acc().lumora$connection();
        if (c == null) {
            return "";
        }
        PacketListener l = c.getPacketListener();
        if (l instanceof ClientLoginNetworkHandler) {
            return "login";
        }
        if (l instanceof ClientPlayNetworkHandler) {
            return "play";
        }
        return c.isOpen() ? "login" : "closed";
    }

    @Override
    public String statusKey() {
        ClientConnection c = acc().lumora$connection();
        return c != null && c.isEncrypted() && c.getPacketListener() instanceof ClientLoginNetworkHandler ? "connect.encrypting" : "";
    }

    @Override
    public String remoteAddress() {
        ClientConnection c = acc().lumora$connection();
        return c == null ? "" : format(c.getAddress());
    }

    @Override
    public void cancel() {
        ConnectScreenAccessor a = acc();
        a.lumora$setCancelled(true);
        ClientConnection c = a.lumora$connection();
        if (c != null) {
            c.disconnect(new TranslatableText("connect.aborted"));
        }
        MinecraftClient.getInstance().setScreen(a.lumora$parent());
    }

    /** Translation keys of a disconnect reason and of its arguments (for the classification). */
    public static List<String> keys(Text c) {
        List<String> out = new ArrayList<>();
        collect(c, out, 0);
        return out;
    }

    private static void collect(Text c, List<String> out, int depth) {
        if (c == null || depth > 8 || out.size() > 32) {
            return;
        }
        if (c instanceof TranslatableText t) {
            out.add(t.getKey());
            for (Object arg : t.getArgs()) {
                if (arg instanceof Text at) {
                    collect(at, out, depth + 1);
                }
            }
        }
        for (Text s : c.getSiblings()) {
            collect(s, out, depth + 1);
        }
    }

    static String format(SocketAddress a) {
        if (a instanceof InetSocketAddress i) {
            InetAddress ip = i.getAddress();
            return (ip != null ? ip.getHostAddress() : i.getHostString()) + ":" + i.getPort();
        }
        return a == null ? "" : a.toString();
    }
}
