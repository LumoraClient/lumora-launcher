package dev.lumora.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import dev.lumora.core.extras.ElytraTrailsModule;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Lumora module "Elytra-Streifen": the trails of one player, built by the core module and drawn by ElytraTrailMixin. */
public final class ElytraTrails {
    private ElytraTrails() {
    }

    /** Draw calls with trails and vertices handed to the renderer (smoke test). */
    public static int draws;
    public static int emitted;

    /** Trails of a player for this frame, relative to its interpolated position ({@code EMPTY} if none). */
    public static ElytraTrailsModule.Mesh mesh(Player p, float partial) {
        ElytraTrailsModule m = ElytraTrailsModule.active();
        if (m == null) {
            return ElytraTrailsModule.Mesh.EMPTY;
        }
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        Vec3 v = p.getDeltaMovement();
        return m.mesh(p.getId(), p == mc.player, p.isFallFlying(),
                Mth.lerp(partial, p.xOld, p.getX()), Mth.lerp(partial, p.yOld, p.getY()), Mth.lerp(partial, p.zOld, p.getZ()),
                p.getViewYRot(partial), p.getViewXRot(partial), v.x, v.y, v.z, cam.x, cam.y, cam.z, System.currentTimeMillis());
    }

    /** Adds the trails to the frame's translucent quads (POSITION_COLOR, both sides). */
    public static void draw(ElytraTrailsModule.Mesh mesh, PoseStack pose, MultiBufferSource buffers) {
        if (!mesh.isEmpty()) {
            draws++;
        }
        if (!mesh.isEmpty()) {
            vertices(mesh, pose.last(), buffers.getBuffer(RenderType.debugQuads()));
        }
    }

    public static void vertices(ElytraTrailsModule.Mesh mesh, PoseStack.Pose p, VertexConsumer vc) {
        emitted += mesh.vertices();
        float[] xyz = mesh.xyz();
        int[] col = mesh.colors();
        for (int i = 0; i < mesh.vertices(); i++) {
            vc.addVertex(p, xyz[i * 3], xyz[i * 3 + 1], xyz[i * 3 + 2]).setColor(col[i]);
        }
    }
}
