package dev.lumora.client.api;

import dev.lumora.api.game.WorldAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/** Minecraft 26.2 implementation of {@link WorldAccess} (live reads on the client thread). */
final class McWorld implements WorldAccess {
    @Override
    public Optional<ViewPoint> viewPoint() {
        Minecraft mc = Minecraft.getInstance();
        var p = mc.player;
        if (p == null || mc.level == null) {
            return Optional.empty();
        }
        Vec3 eye = p.getEyePosition(1f);
        Vec3 look = p.getViewVector(1);
        return Optional.of(new ViewPoint(p.getUUID(), eye.x, eye.y, eye.z, look.x, look.y, look.z, p.isInWater()));
    }

    @Override
    public List<PlayerInfo> players(int limit) {
        Minecraft mc = Minecraft.getInstance();
        var self = mc.player;
        if (self == null || mc.level == null || limit <= 0) {
            return List.of();
        }
        List<Player> others = new ArrayList<>();
        for (Player other : mc.level.players()) {
            if (other != self) {
                others.add(other);
            }
        }
        others.sort(Comparator.comparingDouble(self::distanceToSqr));
        List<PlayerInfo> out = new ArrayList<>(Math.min(limit, others.size()));
        for (Player other : others) {
            if (out.size() >= limit) {
                break;
            }
            Vec3 eye = other.getEyePosition(1f);
            var team = other.getTeam();
            Integer color = team == null ? null : team.getColor().map(net.minecraft.world.scores.TeamColor::rgb).orElse(null);
            out.add(new PlayerInfo(other.getUUID(), other.getName().getString(), eye.x, eye.y, eye.z, other.isInWater(),
                    mc.level.canSeeSky(other.blockPosition()), color == null ? OptionalInt.empty() : OptionalInt.of(color)));
        }
        return out;
    }

    @Override
    public List<TabEntry> tabList() {
        var connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            return List.of();
        }
        List<TabEntry> out = new ArrayList<>();
        for (var info : connection.getOnlinePlayers()) {
            out.add(new TabEntry(info.getProfile().id(), info.getProfile().name()));
        }
        return out;
    }

    @Override
    public Optional<TabEntry> crosshairPlayer() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.crosshairPickEntity instanceof Player target && target != mc.player) {
            return Optional.of(new TabEntry(target.getUUID(), target.getName().getString()));
        }
        return Optional.empty();
    }

    @Override
    public boolean lineOfSightBlocked(double x1, double y1, double z1, double x2, double y2, double z2) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return false;
        }
        var hit = mc.level.clip(new ClipContext(new Vec3(x1, y1, z1), new Vec3(x2, y2, z2), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        return hit.getType() == HitResult.Type.BLOCK;
    }

    @Override
    public int seaLevel() {
        var level = Minecraft.getInstance().level;
        return level == null ? 63 : level.getSeaLevel();
    }

    @Override
    public String remoteIp() {
        var connection = Minecraft.getInstance().getConnection();
        if (connection == null || Minecraft.getInstance().hasSingleplayerServer()) {
            return "";
        }
        if (connection.getConnection().getRemoteAddress() instanceof InetSocketAddress remote) {
            return remote.getAddress() != null ? remote.getAddress().getHostAddress() : remote.getHostString();
        }
        return "";
    }

    @Override
    public void actionBar(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.gui.hud.setOverlayMessage(Component.literal(message), false);
        }
    }

    @Override
    public void playClickSound() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1));
    }
}
