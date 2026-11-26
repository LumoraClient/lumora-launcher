package dev.lumora.core.browser;

import dev.lumora.api.addon.AddonContext;
import dev.lumora.api.addon.LumoraAddon;
import dev.lumora.api.config.BooleanSetting;
import dev.lumora.api.config.SettingsGroup;
import dev.lumora.api.game.ConnectionInfo;
import dev.lumora.api.render.HudGraphics;
import dev.lumora.core.Platform;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

/**
 * Built-in module "Server Info": a card for the server you play on at the top of the pause menu, like a row of the
 * server list: icon, name, message of the day, players and a ping signal, and for known servers (server mappings) the
 * banner and the game modes.
 * <p>
 * The server is pinged when the pause menu shows it (at most every {@link #REFRESH_MS}, on a background thread, the
 * same status request the server list sends); nothing is pinged while the pause menu is closed or in singleplayer.
 * The version adapters call {@link #renderPause} after the pause screen was drawn.
 */
public final class ServerInfoModule implements LumoraAddon {
    public static final String ID = "lumora-serverinfo";
    static final String K = "lumora.serverinfo.";
    static final long REFRESH_MS = 20_000;
    static final int ICON = 32;
    static final int BASE_H = 44;
    /** Room for the Lumora buttons at the top left of the pause menu (hub, friends, screenshots). */
    static final int PILLS_RIGHT = 196;
    private static final ExecutorService PING = Icons.pool("Lumora server info", 1);

    private static volatile ServerInfoModule active;

    private final Platform platform;
    BooleanSetting pauseMenu;
    BooleanSetting banner;
    BooleanSetting gamemodes;

    // the server shown (render thread) and its status (written by the ping thread)
    private String address = "";
    private String name = "";
    private byte[] savedIcon;
    private volatile ServerPinger.Status status;
    private volatile boolean pinging;
    private long lastPing;
    private ServerMappings mappings;

    public ServerInfoModule(Platform platform) {
        this.platform = platform;
    }

    @Override
    public void onInitialize(AddonContext context) {
        settings(context.configuration().settings());
    }

    void settings(SettingsGroup s) {
        pauseMenu = s.toggle("pause_menu", true).name(K + "setting.pause_menu").description(K + "setting.pause_menu.description")
                .section(K + "section.pause");
        banner = s.toggle("banner", true).name(K + "setting.banner").description(K + "setting.banner.description").requires(pauseMenu);
        gamemodes = s.toggle("gamemodes", true).name(K + "setting.gamemodes").description(K + "setting.gamemodes.description")
                .requires(pauseMenu);
    }

    @Override
    public void onEnable(AddonContext context) {
        active = this;
    }

    @Override
    public void onDisable(AddonContext context) {
        if (active == this) {
            active = null;
        }
    }

    /**
     * After the pause menu was drawn ({@code width} x {@code height} in GUI pixels): the card of the current server at
     * the top, centered, right of the Lumora buttons when the screen is narrow. Nothing in singleplayer.
     */
    public static void renderPause(HudGraphics g, int width, int height) {
        ServerInfoModule m = active;
        if (m == null || m.pauseMenu == null || !m.pauseMenu.get()) {
            return;
        }
        try {
            m.render(g, width);
        } catch (RuntimeException e) {
            // a broken card must never break the pause menu
        }
    }

    private void render(HudGraphics g, int width) {
        ConnectionInfo c = platform.game() == null ? null : platform.game().connection();
        if (c == null || !c.multiplayer() || c.address() == null || c.address().isBlank()) {
            return;
        }
        select(c.address().strip());
        long now = System.currentTimeMillis();
        if (!pinging && now - lastPing >= REFRESH_MS) {
            ping(now);
        }
        ServerMappings.Mapping map = mapping();
        List<ServerMappings.Mode> modes = gamemodes.get() && map != null
                ? ServerMappings.modesFor(map, platform.minecraftVersion()) : List.of();
        int h = BASE_H + (modes.isEmpty() ? 0 : ServerBrowserView.CHIP_H + 4);
        int w = Math.min(320, width - 12);
        int x = (width - w) / 2;
        if (x < PILLS_RIGHT) {
            // narrow screen: right of the Lumora buttons
            x = PILLS_RIGHT;
            w = Math.min(w, width - 6 - x);
        }
        if (w < 150) {
            return;
        }
        draw(g, x, 6, w, h, map, modes);
    }

    private void draw(HudGraphics g, int x, int y, int w, int h, ServerMappings.Mapping map, List<ServerMappings.Mode> modes) {
        g.roundedRect(x, y, w, h, 6, 0xF0101622);
        ServerBrowserView.Picture back = map != null && banner.get() ? ServerBrowserView.picture(platform, mappings, map, true) : null;
        if (back != null) {
            int band = Math.max(1, Math.min(back.height(), Math.round((float) back.width() * h / Math.max(1, w))));
            g.texture(back.ref(), x + 1, y + 1, w - 2, h - 2, 0, (back.height() - band) / 2f, back.width(), band,
                    back.width(), back.height());
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xA0070B14);
        }
        g.roundedRect(x, y, w, 1, 0, 0x30FFFFFF);

        // icon: the mapping's, the server's favicon, the one saved in the server list, or the first letter
        int ix = x + 6;
        int iy = y + 6;
        ServerBrowserView.Picture mapIcon = map != null && banner.get() ? ServerBrowserView.picture(platform, mappings, map, false) : null;
        ServerPinger.Status s = status;
        byte[] png = s != null && s.favicon() != null ? s.favicon() : savedIcon;
        String title = ServerBrowserView.displayName(name, address, map);
        if (mapIcon != null) {
            g.texture(mapIcon.ref(), ix, iy, ICON, ICON, 0, 0, mapIcon.width(), mapIcon.height(), mapIcon.width(), mapIcon.height());
        } else if (png != null) {
            byte[] data = png;
            int[] px = Icons.get("info:" + address.toLowerCase(Locale.ROOT) + ":" + Arrays.hashCode(data), ICON, () -> data);
            if (px != null) {
                Icons.draw(g, px, ICON, ix, iy);
            }
        } else {
            g.roundedRect(ix, iy, ICON, ICON, 5, 0xFF213552);
            String l = title == null || title.isEmpty() ? "?" : title.substring(0, 1).toUpperCase(Locale.ROOT);
            g.centeredText("§l" + l, ix + ICON / 2 + 1, iy + 12, BrowserView.TEXT, false);
        }

        int right = x + w - 7;
        int tx = ix + ICON + 8;
        int colW = 0;
        if (s != null && s.ok()) {
            bars(g, right - 11, y + 6, s.pingMs());
            String players = "§f" + s.online() + "§7/" + s.max();
            g.rightText(players, right - 15, y + 7, BrowserView.TEXT, false);
            colW = g.textWidth(players) + 18;
        }
        int room = Math.max(20, right - colW - 6 - tx);
        g.text(BrowserView.fit(g, "§l" + (title == null || title.isEmpty() ? address : title), room), tx, y + 7, BrowserView.TEXT, false);
        g.enableScissor(tx, y, right, y + h);
        if (s == null) {
            g.text(t("Server wird abgefragt…", "Pinging server…"), tx, y + 19, BrowserView.DIM, false);
        } else if (!s.ok()) {
            g.text(s.error() == null ? "" : s.error(), tx, y + 19, BrowserView.FAIL, false);
            g.text(address, tx, y + 29, BrowserView.DIM, false);
        } else {
            String[] lines = s.motd().split("\n", -1);
            String l1 = lines.length > 0 ? lines[0].strip() : "";
            String l2 = lines.length > 1 ? ServerBrowserView.carry(lines[0]) + lines[1].strip() : "";
            g.text(l1, tx, y + 19, BrowserView.MUTED, false);
            g.text(l2, tx, y + 29, BrowserView.MUTED, false);
        }
        g.disableScissor();
        if (!modes.isEmpty()) {
            ServerBrowserView.chips(g, modes, tx, y + BASE_H - 2, right);
        }
    }

    /** Five signal bars like the server list: more and greener the faster the server answers. */
    static void bars(HudGraphics g, int x, int y, long pingMs) {
        int level = level(pingMs);
        int color = level >= 4 ? 0xFF4ADE80 : level == 3 ? 0xFFFACC15 : level == 2 ? 0xFFFB923C : 0xFFF2555A;
        for (int i = 0; i < 5; i++) {
            int bh = 2 + i * 2;
            g.fill(x + i * 2 + i, y + 10 - bh, x + i * 2 + i + 2, y + 10, i < level ? color : 0x60FFFFFF);
        }
    }

    /** Bars for a ping: 5 under 80 ms, 4 under 150, 3 under 300, 2 under 600, else 1 (0 when unknown). */
    static int level(long pingMs) {
        if (pingMs < 0) {
            return 0;
        }
        return pingMs < 80 ? 5 : pingMs < 150 ? 4 : pingMs < 300 ? 3 : pingMs < 600 ? 2 : 1;
    }

    /** A new server: its saved name and icon from the server list, a fresh ping. */
    private void select(String addr) {
        if (addr.equalsIgnoreCase(address)) {
            return;
        }
        address = addr;
        name = "";
        savedIcon = null;
        status = null;
        lastPing = 0;
        try {
            for (Servers.Entry e : new Servers(platform.gameDir().resolve("servers.dat")).load().entries()) {
                if (e.address != null && e.address.strip().equalsIgnoreCase(addr)) {
                    name = e.name == null ? "" : e.name;
                    savedIcon = e.cachedIcon();
                    break;
                }
            }
        } catch (RuntimeException ignored) {
            // no server list: the address is the name
        }
    }

    private void ping(long now) {
        BrowserPlatform game = platform.browsers();
        if (game == null) {
            return;
        }
        pinging = true;
        lastPing = now;
        String addr = address;
        int protocol = game.protocolVersion();
        PING.execute(() -> {
            try {
                ServerPinger.Status result = ServerPinger.ping(addr, protocol);
                if (addr.equals(address)) {
                    status = result;
                }
            } finally {
                pinging = false;
            }
        });
    }

    private ServerMappings.Mapping mapping() {
        if (!banner.get() && !gamemodes.get() || platform.configDir() == null) {
            return null;
        }
        if (mappings == null) {
            mappings = ServerMappings.get(platform.configDir());
            mappings.start();
        }
        ServerMappings.Mapping m = mappings.find(address);
        return m == null || m.dir().isEmpty() ? null : m;
    }

    private String t(String german, String english) {
        String lang = platform.translations() == null ? "" : platform.translations().language();
        return lang.startsWith("de") ? german : english;
    }
}
