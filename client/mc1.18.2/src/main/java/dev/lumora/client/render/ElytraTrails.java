package dev.lumora.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Matrix4f;
import net.minecraft.client.renderer.GameRenderer;
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

    /** Draws the trails right away (POSITION_COLOR quads, blended, both sides, no depth writes). */
    public static void draw(ElytraTrailsModule.Mesh mesh, PoseStack pose) {
        if (!mesh.isEmpty()) {
            draws++;
        }
        if (mesh.isEmpty()) {
            return;
        }
        Matrix4f mat = pose.last().pose();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.disableTexture();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        BufferBuilder vc = Tesselator.getInstance().getBuilder();
        vc.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float[] xyz = mesh.xyz();
        int[] col = mesh.colors();
        emitted += mesh.vertices();
        for (int i = 0; i < mesh.vertices(); i++) {
            int c = col[i];
            vc.vertex(mat, xyz[i * 3], xyz[i * 3 + 1], xyz[i * 3 + 2])
                    .color((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, c >>> 24).endVertex();
        }
        Tesselator.getInstance().end();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.enableTexture();
    }
}
