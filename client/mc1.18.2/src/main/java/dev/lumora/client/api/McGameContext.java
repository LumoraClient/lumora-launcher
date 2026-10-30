package dev.lumora.client.api;

import dev.lumora.api.game.ConnectionInfo;
import dev.lumora.api.game.GameContext;
import dev.lumora.api.game.PlayerSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/** Game state snapshot, refreshed once per tick on the client thread; reads are free. */
final class McGameContext implements GameContext {
    private volatile Optional<PlayerSnapshot> player = Optional.empty();
    private volatile Optional<String> dimension = Optional.empty();
    private volatile Optional<String> gameMode = Optional.empty();
    private volatile Optional<String> screen = Optional.empty();
    private volatile ConnectionInfo connection = ConnectionInfo.NONE;
    private volatile boolean inWorld;
    private volatile boolean hudHidden;
    private volatile boolean focused = true;
    private volatile int fps;
    private volatile long ticks;
    private String lastScreenName = "";
    private String lastServer = "";
    private ConnectionInfo cachedConnection = ConnectionInfo.NONE;

    void update(Minecraft mc, long tick) {
        ticks = tick;
        fps = dev.lumora.client.mixin.MinecraftFpsAccessor.lumora$fps();
        focused = mc.isWindowActive();
        hudHidden = mc.options.hideGui;
        String name = mc.screen == null ? "" : mc.screen.getClass().getSimpleName();
        if (!name.equals(lastScreenName)) {
            lastScreenName = name;
            screen = name.isEmpty() ? Optional.empty() : Optional.of(name);
        }
        LocalPlayer p = mc.player;
        inWorld = p != null && mc.level != null;
        if (!inWorld) {
            player = Optional.empty();
            dimension = Optional.empty();
            gameMode = Optional.empty();
            connection = ConnectionInfo.NONE;
            lastServer = "";
            return;
        }
        Vec3 v = p.getDeltaMovement();
        player = Optional.of(new PlayerSnapshot(p.getUUID(), p.getName().getString(), p.getX(), p.getY(), p.getZ(),
                p.getYRot(), p.getXRot(), p.getHealth(), p.getMaxHealth(), p.getFoodData().getFoodLevel(), p.getArmorValue(),
                p.experienceLevel, p.isOnGround(), p.isSprinting(), p.isShiftKeyDown(), Math.sqrt(v.x * v.x + v.z * v.z) * 20));
        String dim = mc.level.dimension().location().toString();
        if (dimension.isEmpty() || !dimension.get().equals(dim)) {
            dimension = Optional.of(dim);
        }
        String mode = mc.gameMode != null ? mc.gameMode.getPlayerMode().getName() : null;
        gameMode = Optional.ofNullable(mode);
        int ping = -1;
        if (mc.getConnection() != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(p.getUUID());
            ping = info != null ? info.getLatency() : -1;
        }
        ConnectionInfo.Kind kind = mc.hasSingleplayerServer() ? ConnectionInfo.Kind.SINGLEPLAYER : ConnectionInfo.Kind.MULTIPLAYER;
        String address = kind == ConnectionInfo.Kind.MULTIPLAYER && mc.getCurrentServer() != null ? mc.getCurrentServer().ip : "";
        if (cachedConnection.kind() != kind || cachedConnection.ping() != ping || !address.equals(lastServer)) {
            lastServer = address;
            cachedConnection = new ConnectionInfo(kind, address, ping);
        }
        connection = cachedConnection;
    }

    @Override
    public boolean inWorld() {
        return inWorld;
    }

    @Override
    public Optional<PlayerSnapshot> player() {
        return player;
    }

    @Override
    public Optional<String> dimension() {
        return dimension;
    }

    @Override
    public Optional<String> gameMode() {
        return gameMode;
    }

    @Override
    public ConnectionInfo connection() {
        return connection;
    }

    @Override
    public boolean screenOpen() {
        return screen.isPresent();
    }

    @Override
    public Optional<String> screen() {
        return screen;
    }

    @Override
    public boolean hudHidden() {
        return hudHidden;
    }

    @Override
    public boolean windowFocused() {
        return focused;
    }

    @Override
    public int fps() {
        return fps;
    }

    @Override
    public long clientTicks() {
        return ticks;
    }
}
