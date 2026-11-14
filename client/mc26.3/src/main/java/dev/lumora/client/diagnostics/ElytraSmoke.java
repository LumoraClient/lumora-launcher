package dev.lumora.client.diagnostics;

import dev.lumora.core.extras.ElytraTrailsModule;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Smoke test of the elytra trails: puts on an elytra (integrated server), lifts the player 40 blocks, glides with the
 * camera in front of the player and reports whether trails were built.
 */
final class ElytraSmoke {
    /** Status taken just before turning (gliding, number of trails). */
    static String lastStatus = "not run";

    private ElytraSmoke() {
    }

    static void giveElytra(Minecraft mc, boolean on) {
        var server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) {
            return;
        }
        var uuid = mc.player.getUUID();
        server.execute(() -> {
            var p = server.getPlayerList().getPlayer(uuid);
            if (p != null) {
                p.setItemSlot(EquipmentSlot.CHEST, on ? new ItemStack(Items.ELYTRA) : ItemStack.EMPTY);
            }
        });
    }

    /** Where the player stood before the launch (null: not launched). */
    private static double[] ground;

    /** Lifts the player and looks slightly down; the camera goes in front of the player. */
    static void launch(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null) {
            return;
        }
        ground = new double[]{p.getX(), p.getY(), p.getZ()};
        p.setPos(p.getX(), p.getY() + 40, p.getZ());
        p.setXRot(15f);
        p.xRotO = 15f;
        p.setDeltaMovement(p.getLookAngle().scale(1.6));
        mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
    }

    /** Starts gliding (as if jump was pressed in the air) at full speed. */
    static void glide(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || p.isFallFlying()) {
            return;
        }
        p.connection.send(new ServerboundPlayerCommandPacket(p, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
        p.startFallFlying();
        p.setDeltaMovement(p.getLookAngle().scale(1.6));
    }

    /** Turns 90 degrees while gliding: the camera in front of the player now sees the trails from the side. */
    static void turn(Minecraft mc) {
        lastStatus = status(mc);
        LocalPlayer p = mc.player;
        if (p == null) {
            return;
        }
        p.setYRot(p.getYRot() + 90f);
        p.yRotO = p.getYRot();
    }

    static String status(Minecraft mc) {
        ElytraTrailsModule m = ElytraTrailsModule.active();
        return "gliding=" + (mc.player != null && mc.player.isFallFlying()) + " trails=" + (m == null ? -1 : m.activeTrails()) + " vertices=" + (m == null ? -1 : m.lastVertices())
                + " draws=" + dev.lumora.client.render.ElytraTrails.draws + " emitted=" + dev.lumora.client.render.ElytraTrails.emitted;
    }

    /** Changes a setting of the elytra trails module (smoke test only). */
    static void setting(String key, String value) {
        ApiSmoke.core().addonManager().record("lumora-elytratrails").filter(r -> r.configuration() != null)
                .flatMap(r -> r.configuration().settings().get(key)).ifPresent(s -> s.decode(value));
    }

    static void reset(Minecraft mc) {
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        if (mc.player != null) {
            mc.player.stopFallFlying();
        }
        giveElytra(mc, false);
        // back on the ground: stopping the glide 40 blocks up would kill the test player before the chat steps
        double[] g = ground;
        ground = null;
        var server = mc.getSingleplayerServer();
        if (g != null && server != null && mc.player != null) {
            var uuid = mc.player.getUUID();
            mc.player.setDeltaMovement(0, 0, 0);
            server.execute(() -> {
                var sp = server.getPlayerList().getPlayer(uuid);
                if (sp != null) {
                    sp.stopFallFlying();
                    sp.setDeltaMovement(0, 0, 0);
                    sp.teleportTo(g[0], g[1], g[2]);
                    sp.resetFallDistance();
                }
            });
        }
    }
}
