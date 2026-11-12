package dev.lumora.core.hub;

import dev.lumora.api.game.ConnectionInfo;
import dev.lumora.api.game.PlayerSnapshot;
import dev.lumora.api.game.WorldAccess;
import dev.lumora.core.LumoraCore;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Notes where each new screenshot was taken, for the automatic albums of the screenshot manager: every two seconds in a
 * world it looks for image files that appeared in the screenshots folder since the last look and records the server
 * (empty in singleplayer), the own account and the players nearby (at most {@link #MAX_PLAYERS}, nearest first, within
 * {@link #RANGE} blocks). Screenshots taken before Lumora started are left alone. Version independent: no hook into
 * Minecraft's screenshot code.
 */
public final class ScreenshotWatcher {
    static final int MAX_PLAYERS = 8;
    static final double RANGE = 48;
    private static final int EVERY_TICKS = 40;

    private final LumoraCore core;
    private long since;
    private long folderStamp = Long.MIN_VALUE;
    private int ticks;

    public ScreenshotWatcher(LumoraCore core) {
        this.core = core;
        this.since = System.currentTimeMillis();
    }

    /** Every client tick (cheap: one look at the folder's time stamp every two seconds while in a world). */
    public void tick() {
        if (++ticks < EVERY_TICKS) {
            return;
        }
        ticks = 0;
        if (!core.platform().game().inWorld()) {
            return;
        }
        check();
    }

    /** Looks for new screenshots now (tests). */
    void check() {
        ScreenshotLibrary lib = ScreenshotView.library(core);
        Path dir = lib.directory();
        long stamp;
        try {
            stamp = Files.isDirectory(dir) ? Files.getLastModifiedTime(dir).toMillis() : 0;
        } catch (IOException e) {
            return;
        }
        if (stamp == folderStamp) {
            return;
        }
        folderStamp = stamp;
        List<String> fresh = new ArrayList<>();
        long newest = since;
        try (DirectoryStream<Path> s = Files.newDirectoryStream(dir, "*.{png,PNG,jpg,JPG,jpeg,JPEG}")) {
            for (Path p : s) {
                long m = Files.getLastModifiedTime(p).toMillis();
                String name = p.getFileName().toString();
                if (m > since && !lib.hasMeta(name)) {
                    fresh.add(name);
                    newest = Math.max(newest, m);
                }
            }
        } catch (IOException e) {
            return;
        }
        if (fresh.isEmpty()) {
            return;
        }
        since = newest;
        ScreenshotLibrary.Meta here = here();
        for (String name : fresh) {
            lib.record(name, here);
        }
    }

    /** Server, account and players nearby right now. */
    ScreenshotLibrary.Meta here() {
        ConnectionInfo c = core.platform().game().connection();
        String server = c != null && c.multiplayer() ? host(c.address()) : "";
        Optional<PlayerSnapshot> self = core.platform().game().player();
        String account = self.map(PlayerSnapshot::name).orElseGet(() -> core.platform().session()
                .map(dev.lumora.core.social.GameSession::name).orElse(""));
        List<String> players = new ArrayList<>();
        if (self.isPresent()) {
            PlayerSnapshot me = self.get();
            List<WorldAccess.PlayerInfo> near = new ArrayList<>();
            for (WorldAccess.PlayerInfo p : core.platform().world().players(64)) {
                if (p.id().equals(me.id()) || p.name() == null || p.name().isBlank()) {
                    continue;
                }
                if (distance(p, me) <= RANGE) {
                    near.add(p);
                }
            }
            near.sort(Comparator.comparingDouble(p -> distance(p, me)));
            for (WorldAccess.PlayerInfo p : near) {
                if (players.size() >= MAX_PLAYERS) {
                    break;
                }
                if (!players.contains(p.name())) {
                    players.add(p.name());
                }
            }
        }
        return new ScreenshotLibrary.Meta(server, account, players);
    }

    private static double distance(WorldAccess.PlayerInfo p, PlayerSnapshot me) {
        double dx = p.x() - me.x();
        double dy = p.y() - me.y();
        double dz = p.z() - me.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** "play.example.net:25565" → "play.example.net" (the default port says nothing). */
    static String host(String address) {
        if (address == null) {
            return "";
        }
        String a = address.strip().toLowerCase(java.util.Locale.ROOT);
        return a.endsWith(":25565") ? a.substring(0, a.length() - 6) : a;
    }
}
