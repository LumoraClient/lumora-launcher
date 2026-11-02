package dev.lumora.core.connection;

import dev.lumora.api.addon.AddonContext;
import dev.lumora.api.addon.LumoraAddon;
import dev.lumora.api.config.BooleanSetting;
import dev.lumora.api.config.IntegerSetting;
import dev.lumora.api.config.SettingsGroup;
import dev.lumora.api.screen.AddonView;
import dev.lumora.core.Platform;

import java.util.List;

/**
 * Built-in module "Verbindung": Lumora's connecting and disconnect screens, automatic reconnect with growing pauses,
 * a watchdog for connections that hang and a stream mode that hides server addresses. The connection tracking itself
 * ({@link ConnectionTracker}, events for addons) always runs; this module only decides what the player sees.
 * <p>
 * The version adapters call {@link #connecting} where Minecraft opens its connect screen and {@link #disconnected}
 * where it opens its disconnect screen; a null result keeps Minecraft's screen.
 */
public final class ConnectionModule implements LumoraAddon {
    private static volatile ConnectionModule active;

    private final Platform platform;
    BooleanSetting screens;
    BooleanSetting autoReconnect;
    IntegerSetting reconnectDelay;
    IntegerSetting reconnectAttempts;
    IntegerSetting timeout;
    BooleanSetting hideAddress;
    BooleanSetting pingPreview;

    public ConnectionModule(Platform platform) {
        this.platform = platform;
    }

    @Override
    public void onInitialize(AddonContext context) {
        SettingsGroup s = context.configuration().settings();
        screens = (BooleanSetting) s.toggle("screens", true).name("lumora.extras.connection.screens");
        autoReconnect = (BooleanSetting) s.toggle("auto_reconnect", false).name("lumora.extras.connection.auto_reconnect");
        reconnectDelay = (IntegerSetting) s.slider("reconnect_delay", 5, 1, 30).name("lumora.extras.connection.reconnect_delay");
        reconnectAttempts = (IntegerSetting) s.slider("reconnect_attempts", 3, 1, 10).name("lumora.extras.connection.reconnect_attempts");
        timeout = (IntegerSetting) s.slider("timeout", 30, 10, 120).name("lumora.extras.connection.timeout").advanced();
        hideAddress = (BooleanSetting) s.toggle("hide_address", false).name("lumora.extras.connection.hide_address");
        pingPreview = (BooleanSetting) s.toggle("ping_preview", true).name("lumora.extras.connection.ping_preview");
    }

    @Override
    public void onEnable(AddonContext context) {
        active = this;
        ConnectionTracker t = ConnectionTracker.get();
        if (t != null) {
            t.onTimeout(this::timedOut);
        }
    }

    @Override
    public void onDisable(AddonContext context) {
        active = null;
        ConnectionTracker t = ConnectionTracker.get();
        if (t != null) {
            t.onTimeout(null);
            t.configure(new ReconnectPolicy(false, 5, 3), 0);
        }
    }

    public static ConnectionModule active() {
        return active;
    }

    ReconnectPolicy policy() {
        return new ReconnectPolicy(autoReconnect.get(), reconnectDelay.get(), reconnectAttempts.get());
    }

    boolean autoReconnect() {
        return autoReconnect.get();
    }

    void setAutoReconnect(boolean on) {
        autoReconnect.set(on);
        ConnectionTracker t = ConnectionTracker.get();
        if (t != null) {
            t.configure(policy(), timeout.get());
        }
    }

    boolean hideAddress() {
        return hideAddress.get();
    }

    boolean pingPreview() {
        return pingPreview.get();
    }

    // ---- entry points for the version adapters (client thread)

    /**
     * Minecraft opened its connect screen for {@code attempt}. Always tracked; returns Lumora's connecting view to
     * show instead (the adapter keeps ticking Minecraft's screen underneath), or null for Minecraft's own screen.
     */
    public static AddonView connecting(ConnectionAttempt attempt) {
        ConnectionTracker t = ConnectionTracker.get();
        if (t == null || attempt == null) {
            return null;
        }
        ConnectionModule m = active;
        if (m != null) {
            t.configure(m.policy(), m.timeout.get());
        } else {
            t.configure(new ReconnectPolicy(false, 5, 3), 0);
        }
        t.begin(attempt);
        if (m != null) {
            m.platform.logger().info("Connection started (attempt " + (t.attemptNumber() + 1) + ")");
        }
        return m != null && m.screens.get() ? new ConnectingView(m.platform, m, t, attempt) : null;
    }

    /**
     * Minecraft opens its disconnect screen.
     *
     * @param keys translation keys of the reason and its arguments; {@code text} the reason as plain text
     * @return Lumora's disconnect view to show instead, or null for Minecraft's screen
     */
    public static AddonView disconnected(List<String> keys, String text) {
        ConnectionTracker t = ConnectionTracker.get();
        if (t == null) {
            return null;
        }
        ConnectionTracker.Ended e = t.disconnected(keys == null ? List.of() : keys, text);
        ConnectionModule m = active;
        if (m != null && e != null) {
            // kind and phase only: no address and no server message in the log
            m.platform.logger().info("Connection ended: " + e.kind() + " during " + e.lastPhase()
                    + (e.reconnectIn() >= 0 ? ", reconnect in " + e.reconnectIn() + " ms" : ""));
        }
        return e != null && m != null && m.screens.get() ? new DisconnectedView(m.platform, m, t, e) : null;
    }

    /** The watchdog cancelled a hanging attempt: show why on top of the screen the attempt returned to. */
    private void timedOut(ConnectionTracker.Ended e) {
        ConnectionTracker t = ConnectionTracker.get();
        if (t != null && screens.get()) {
            platform.views().show(new DisconnectedView(platform, this, t, e));
        }
    }

    /** Smoke test: whether the module is on and shows its screens. */
    public static boolean enabled() {
        ConnectionModule m = active;
        return m != null && m.screens.get();
    }
}
