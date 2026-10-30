package dev.lumora.client.api;

import dev.lumora.api.game.WorldAccess;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.LiteralText;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/** Minecraft 1.8.9 implementation of {@link WorldAccess} (live reads on the client thread; Yarn names). */
final class McWorld implements WorldAccess {
    @Override
    public Optional<ViewPoint> viewPoint() {
        MinecraftClient mc = MinecraftClient.getInstance();
        var p = mc.player;
        if (p == null || mc.world == null) {
            return Optional.empty();
        }
        Vec3d eye = p.getCameraPosVec(1f);
        Vec3d look = p.getRotationVector(1f);
        return Optional.of(new ViewPoint(p.getUuid(), eye.x, eye.y, eye.z, look.x, look.y, look.z, p.isTouchingWater()));
    }

    @Override
    public List<PlayerInfo> players(int limit) {
        MinecraftClient mc = MinecraftClient.getInstance();
        var self = mc.player;
        if (self == null || mc.world == null || limit <= 0) {
            return List.of();
        }
        List<PlayerEntity> others = new ArrayList<>();
        for (PlayerEntity other : mc.world.playerEntities) {
            if (other != self) {
                others.add(other);
            }
        }
        others.sort(Comparator.comparingDouble(o -> o.squaredDistanceTo(self.x, self.y, self.z)));
        List<PlayerInfo> out = new ArrayList<>(Math.min(limit, others.size()));
        for (PlayerEntity other : others) {
            if (out.size() >= limit) {
                break;
            }
            Vec3d eye = other.getCameraPosVec(1f);
            boolean sky = mc.world.hasDirectSunlight(new BlockPos((int) Math.floor(other.x), (int) Math.floor(other.y), (int) Math.floor(other.z)));
            // team colors are chat formatting codes on this version; the speaker list uses its default color
            out.add(new PlayerInfo(other.getUuid(), other.getGameProfile().getName(), eye.x, eye.y, eye.z, other.isTouchingWater(), sky, OptionalInt.empty()));
        }
        return out;
    }

    @Override
    public List<TabEntry> tabList() {
        var handler = MinecraftClient.getInstance().getNetworkHandler();
        if (handler == null) {
            return List.of();
        }
        List<TabEntry> out = new ArrayList<>();
        for (var info : handler.getPlayerList()) {
            out.add(new TabEntry(info.getProfile().getId(), info.getProfile().getName()));
        }
        return out;
    }

    @Override
    public Optional<TabEntry> crosshairPlayer() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.targetedEntity instanceof PlayerEntity target && target != mc.player) {
            return Optional.of(new TabEntry(target.getUuid(), target.getGameProfile().getName()));
        }
        return Optional.empty();
    }

    @Override
    public boolean lineOfSightBlocked(double x1, double y1, double z1, double x2, double y2, double z2) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) {
            return false;
        }
        BlockHitResult hit = mc.world.rayTrace(new Vec3d(x1, y1, z1), new Vec3d(x2, y2, z2), false, true, false);
        return hit != null && hit.type == BlockHitResult.Type.BLOCK;
    }

    @Override
    public int seaLevel() {
        var world = MinecraftClient.getInstance().world;
        return world == null ? 63 : world.getSeaLevel();
    }

    @Override
    public String remoteIp() {
        MinecraftClient mc = MinecraftClient.getInstance();
        var handler = mc.getNetworkHandler();
        if (handler == null || mc.isInSingleplayer()) {
            return "";
        }
        if (handler.getClientConnection().getAddress() instanceof InetSocketAddress remote) {
            return remote.getAddress() != null ? remote.getAddress().getHostAddress() : remote.getHostString();
        }
        return "";
    }

    @Override
    public void actionBar(String message) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.inGameHud.setOverlayMessage(new LiteralText(message), false);
        }
    }

    @Override
    public void playClickSound() {
        MinecraftClient.getInstance().getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(new net.minecraft.util.Identifier("gui.button.press"), 1.0f));
    }
}
