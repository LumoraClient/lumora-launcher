package dev.lumora.api.game;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * Live access to the loaded world and the players in it (permission {@code players}), for addons like voice chat or
 * minimaps. Unlike {@link GameContext} these are live reads: call them on the client thread (ticks, events, rendering).
 * Outside a world every method returns empty values.
 *
 * @since 1.1.0
 */
public interface WorldAccess {
    /** Own eye position and normalized view direction. */
    Optional<ViewPoint> viewPoint();

    /** Other players of the loaded world (never the own player), nearest first, at most {@code limit}. */
    List<PlayerInfo> players(int limit);

    /** Players in the tab list, own player included. */
    List<TabEntry> tabList();

    /** The player under the crosshair, if any. */
    Optional<TabEntry> crosshairPlayer();

    /** Whether solid blocks are on the straight line between the two points. */
    boolean lineOfSightBlocked(double x1, double y1, double z1, double x2, double y2, double z2);

    /** Sea level of the loaded world (63 when unknown). */
    int seaLevel();

    /** IP address the game is connected to (SRV records resolved); empty in singleplayer and menus. */
    String remoteIp();

    /** Short message above the hotbar. */
    void actionBar(String message);

    /** The vanilla button click sound. */
    void playClickSound();

    /** Eye position and normalized view direction of the own player. */
    record ViewPoint(UUID id, double x, double y, double z, double lookX, double lookY, double lookZ, boolean inWater) {}

    /**
     * Another player with its eye position.
     *
     * @param skyVisible whether the block at the player's position sees the sky
     * @param teamColor  RGB color of the player's team, if it has one
     */
    record PlayerInfo(UUID id, String name, double x, double y, double z, boolean inWater, boolean skyVisible, OptionalInt teamColor) {}

    record TabEntry(UUID id, String name) {}

    /** For clients without world access: everything empty. */
    WorldAccess NONE = new WorldAccess() {
        @Override public Optional<ViewPoint> viewPoint() { return Optional.empty(); }
        @Override public List<PlayerInfo> players(int limit) { return List.of(); }
        @Override public List<TabEntry> tabList() { return List.of(); }
        @Override public Optional<TabEntry> crosshairPlayer() { return Optional.empty(); }
        @Override public boolean lineOfSightBlocked(double x1, double y1, double z1, double x2, double y2, double z2) { return false; }
        @Override public int seaLevel() { return 63; }
        @Override public String remoteIp() { return ""; }
        @Override public void actionBar(String message) { }
        @Override public void playClickSound() { }
    };
}
