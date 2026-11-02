package dev.lumora.client.api;

import dev.lumora.client.mixin.ConnectScreenAccessor;
import dev.lumora.core.connection.ConnectionAttempt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.chat.Component;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * One attempt of Minecraft's connect screen for Lumora's connection tracker (1.20.4): server entry, how far the
 * connection is (by its packet listener and the status line), the resolved address, and cancelling like the
 * screen's own button. Only reads Minecraft's state; never sends anything itself.
 */
public final class McConnection implements ConnectionAttempt {
    /** Server of the connect screen being opened right now (set by ConnectStartMixin, taken by the next attempt). */
    private static String pendingName = "";
    private static String pendingAddress = "";

    private final ConnectScreen screen;
    private final String name;
    private final String address;

    public McConnection(ConnectScreen screen) {
        this.screen = screen;
        this.name = pendingName;
        this.address = pendingAddress;
        pendingName = "";
        pendingAddress = "";
    }

    /** Called where Minecraft starts connecting (client thread), before the connect screen is shown. */
    public static void starting(String name, String address) {
        pendingName = name == null ? "" : name;
        pendingAddress = address == null ? "" : address;
    }

    private ConnectScreenAccessor acc() {
        return (ConnectScreenAccessor) screen;
    }

    public Screen parent() {
        return acc().lumora$parent();
    }

    @Override
    public String serverName() {
        if (!name.isEmpty()) {
            return name;
        }
        ServerData d = Minecraft.getInstance().getCurrentServer();
        return d == null || d.name == null ? "" : d.name;
    }

    @Override
    public String serverAddress() {
        if (!address.isEmpty()) {
            return address;
        }
        ServerData d = Minecraft.getInstance().getCurrentServer();
        return d == null || d.ip == null ? "" : d.ip;
    }

    @Override
    public String stage() {
        Connection c = acc().lumora$connection();
        if (c == null) {
            return "";
        }
        PacketListener l = c.getPacketListener();
        if (l instanceof ClientHandshakePacketListenerImpl) {
            return "login";
        }
        if (l instanceof net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl) {
            return "configuration";
        }
        if (l instanceof ClientPacketListener) {
            return "play";
        }
        return c.isConnected() ? "login" : "closed";
    }

    @Override
    public String statusKey() {
        Component s = acc().lumora$status();
        if (s == null) {
            return "";
        }
        return s.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t ? t.getKey() : "";
    }

    @Override
    public String remoteAddress() {
        Connection c = acc().lumora$connection();
        return c == null ? "" : format(c.getRemoteAddress());
    }

    @Override
    public void cancel() {
        ConnectScreenAccessor a = acc();
        a.lumora$setAborted(true);
        if (a.lumora$channelFuture() != null) {
            a.lumora$channelFuture().cancel(true);
            a.lumora$setChannelFuture(null);
        }
        Connection c = a.lumora$connection();
        if (c != null) {
            c.disconnect(Component.translatable("connect.aborted"));
        }
        Minecraft.getInstance().setScreen(a.lumora$parent());
    }

    /** Translation keys of a disconnect reason and of its arguments (for the classification). */
    public static List<String> keys(Component c) {
        List<String> out = new ArrayList<>();
        collect(c, out, 0);
        return out;
    }

    private static void collect(Component c, List<String> out, int depth) {
        if (c == null || depth > 8 || out.size() > 32) {
            return;
        }
        if (c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t) {
            out.add(t.getKey());
            for (Object arg : t.getArgs()) {
                if (arg instanceof Component ac) {
                    collect(ac, out, depth + 1);
                }
            }
        }
        for (Component s : c.getSiblings()) {
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
