package dev.lumora.client.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import dev.lumora.core.extras.BlockOverlayModule;
import net.minecraft.block.BlockState;
import net.minecraft.block.material.Material;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lumora Block-Overlay (legacy): instead of the vanilla outline the box of {@link BlockOverlayModule#frame} (filled box
 * and/or outline, slide, fade, only the face looked at), drawn with the same GL state as vanilla.
 */
@Mixin(WorldRenderer.class)
public abstract class BlockOutlineMixin {
    @Shadow
    private ClientWorld world;

    @Inject(method = "drawBlockOutline", at = @At("HEAD"), cancellable = true)
    private void lumora$overlay(PlayerEntity player, BlockHitResult hit, int mode, float tickDelta, CallbackInfo ci) {
        BlockOverlayModule m = BlockOverlayModule.active();
        if (m == null) {
            return;
        }
        ci.cancel();
        if (mode != 0 || hit.type != BlockHitResult.Type.BLOCK || world == null) {
            return;
        }
        BlockPos pos = hit.getBlockPos();
        BlockState state = world.getBlockState(pos);
        if (state.getMaterial() == Material.AIR || !world.getWorldBorder().contains(pos)) {
            return;
        }
        Box b = state.method_11722(world, pos); // selection box
        if (b == null) {
            return;
        }
        int side = hit.direction == null ? -1 : hit.direction.ordinal();
        BlockOverlayModule.Frame f = m.frame(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ, side,
                MinecraftClient.getInstance().options.perspective == 0);
        if (f == null) {
            return;
        }
        double cx = player.prevTickX + (player.x - player.prevTickX) * tickDelta;
        double cy = player.prevTickY + (player.y - player.prevTickY) * tickDelta;
        double cz = player.prevTickZ + (player.z - player.prevTickZ) * tickDelta;
        Box box = new Box(f.minX() - cx, f.minY() - cy, f.minZ() - cz, f.maxX() - cx, f.maxY() - cy, f.maxZ() - cz);
        GlStateManager.enableBlend();
        GlStateManager.blendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableTexture();
        GlStateManager.disableCull();
        GlStateManager.depthMask(false);
        if (f.fill() != 0) {
            lumora$faces(box, f.fill());
        }
        int o = f.outline();
        if (o != 0) {
            GlStateManager.method_12304(m.lineWidth()); // glLineWidth
            WorldRenderer.drawBox(box, ((o >> 16) & 0xFF) / 255f, ((o >> 8) & 0xFF) / 255f, (o & 0xFF) / 255f, (o >>> 24) / 255f);
        }
        GlStateManager.depthMask(true);
        GlStateManager.enableCull();
        GlStateManager.enableTexture();
        GlStateManager.disableBlend();
    }

    /** The 6 faces of the box as translucent quads. */
    @Unique
    private static void lumora$faces(Box b, int c) {
        double[][] q = {
                {b.minX, b.minY, b.minZ, b.maxX, b.minY, b.minZ, b.maxX, b.minY, b.maxZ, b.minX, b.minY, b.maxZ},
                {b.minX, b.maxY, b.minZ, b.minX, b.maxY, b.maxZ, b.maxX, b.maxY, b.maxZ, b.maxX, b.maxY, b.minZ},
                {b.minX, b.minY, b.minZ, b.minX, b.maxY, b.minZ, b.maxX, b.maxY, b.minZ, b.maxX, b.minY, b.minZ},
                {b.minX, b.minY, b.maxZ, b.maxX, b.minY, b.maxZ, b.maxX, b.maxY, b.maxZ, b.minX, b.maxY, b.maxZ},
                {b.minX, b.minY, b.minZ, b.minX, b.minY, b.maxZ, b.minX, b.maxY, b.maxZ, b.minX, b.maxY, b.minZ},
                {b.maxX, b.minY, b.minZ, b.maxX, b.maxY, b.minZ, b.maxX, b.maxY, b.maxZ, b.maxX, b.minY, b.maxZ}};
        float r = ((c >> 16) & 0xFF) / 255f;
        float g = ((c >> 8) & 0xFF) / 255f;
        float bl = (c & 0xFF) / 255f;
        float a = (c >>> 24) / 255f;
        Tessellator t = Tessellator.getInstance();
        BufferBuilder vc = t.getBuffer();
        vc.begin(GL11.GL_QUADS, VertexFormats.POSITION_COLOR);
        for (double[] f : q) {
            for (int i = 0; i < 12; i += 3) {
                vc.vertex(f[i], f[i + 1], f[i + 2]).color(r, g, bl, a).next();
            }
        }
        t.draw();
    }
}
