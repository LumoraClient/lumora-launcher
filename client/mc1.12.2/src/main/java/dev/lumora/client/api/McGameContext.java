package dev.lumora.client.api;

import dev.lumora.api.game.ConnectionInfo;
import dev.lumora.api.game.GameContext;
import dev.lumora.api.game.PlayerSnapshot;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.ClientPlayerEntity;
import org.lwjgl.opengl.Display;

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

    void update(MinecraftClient mc, long tick) {
        ticks = tick;
        fps = MinecraftClient.getCurrentFps();
        focused = Display.isActive();
        hudHidden = mc.options.hudHidden;
        String name = mc.currentScreen == null ? "" : mc.currentScreen.getClass().getSimpleName();
        if (!name.equals(lastScreenName)) {
            lastScreenName = name;
            screen = name.isEmpty() ? Optional.empty() : Optional.of(name);
        }
        ClientPlayerEntity p = mc.player;
        inWorld = p != null && mc.world != null;
        if (!inWorld) {
            player = Optional.empty();
            dimension = Optional.empty();
            gameMode = Optional.empty();
            connection = ConnectionInfo.NONE;
            lastServer = "";
            return;
        }
        player = Optional.of(new PlayerSnapshot(p.getUuid(), p.getGameProfile().getName(), p.x, p.y, p.z,
                p.yaw, p.pitch, p.getHealth(), p.getMaxHealth(), p.getHungerManager().getFoodLevel(), p.getArmorProtectionValue(),
                p.experienceLevel, p.onGround, p.isSprinting(), p.isSneaking(),
                Math.sqrt(p.velocityX * p.velocityX + p.velocityZ * p.velocityZ) * 20));
        String dim = dimensionId(mc.world.dimension.getDimensionType().getId());
        if (dimension.isEmpty() || !dimension.get().equals(dim)) {
            dimension = Optional.of(dim);
        }
        String mode = mc.interactionManager != null ? mc.interactionManager.method_9667().getGameModeName() : null;
        gameMode = Optional.ofNullable(mode);
        int ping = -1;
        if (mc.getNetworkHandler() != null) {
            PlayerListEntry info = mc.getNetworkHandler().getPlayerListEntry(p.getUuid());
            ping = info != null ? info.getLatency() : -1;
        }
        ConnectionInfo.Kind kind = mc.isInSingleplayer() ? ConnectionInfo.Kind.SINGLEPLAYER : ConnectionInfo.Kind.MULTIPLAYER;
        String address = kind == ConnectionInfo.Kind.MULTIPLAYER && mc.getCurrentServerEntry() != null ? mc.getCurrentServerEntry().address : "";
        if (cachedConnection.kind() != kind || cachedConnection.ping() != ping || !address.equals(lastServer)) {
            lastServer = address;
            cachedConnection = new ConnectionInfo(kind, address, ping);
        }
        connection = cachedConnection;
    }

    /** The modern dimension ids for the three 1.12.2 dimensions (DimensionType#getId: 0, -1, 1). */
    private static String dimensionId(int type) {
        return switch (type) {
            case -1 -> "minecraft:the_nether";
            case 1 -> "minecraft:the_end";
            case 0 -> "minecraft:overworld";
            default -> "minecraft:dimension_" + type;
        };
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
