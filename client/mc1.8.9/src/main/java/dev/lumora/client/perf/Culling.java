package dev.lumora.client.perf;

import dev.lumora.client.LumoraClient;
import dev.lumora.core.perf.OcclusionCuller;
import dev.lumora.core.perf.PerformanceEngine;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;

/**
 * Minecraft 1.8.9 side of the occlusion culler: the opaque full blocks of the client world (read on the culling
 * thread) and the rules for entities that are never culled because something of them can be seen through walls (name
 * tags, leashes, fishing lines, players). In third person the camera is behind the player, away from
 * the position the culler would test from: culling pauses there.
 */
public final class Culling {
    private static OcclusionCuller culler;
    private static ClientWorld lastWorld;
    private static boolean paused;

    private Culling() {
    }

    /** Creates the culler (client start). */
    public static void init() {
        culler = new OcclusionCuller(Culling::section, msg -> LumoraClient.LOG.warn(msg));
        PerformanceEngine.culler(culler);
    }

    /** The culler while entity culling is switched on (and the camera is the player's eyes), otherwise null. */
    public static OcclusionCuller active() {
        return PerformanceEngine.entityCulling && !paused ? culler : null;
    }

    /** Start of a frame's entity pass (render thread): the camera entity and the frame's tick delta. */
    public static void frame(Entity camera, float tickDelta) {
        OcclusionCuller c = culler;
        if (c == null || !PerformanceEngine.entityCulling || camera == null) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        paused = mc.options.perspective != 0;
        if (paused) {
            return;
        }
        ClientWorld world = mc.world;
        if (world != lastWorld) {
            lastWorld = world;
            c.reset();
        }
        double x = camera.prevTickX + (camera.x - camera.prevTickX) * tickDelta;
        double y = camera.prevTickY + (camera.y - camera.prevTickY) * tickDelta + camera.getEyeHeight();
        double z = camera.prevTickZ + (camera.z - camera.prevTickZ) * tickDelta;
        c.frame(x, y, z);
    }

    /** A block changed (client thread). */
    public static void invalidate(int blockX, int blockY, int blockZ) {
        OcclusionCuller c = culler;
        if (c != null && PerformanceEngine.entityCulling) {
            c.invalidate(blockX >> 4, blockY >> 4, blockZ >> 4);
        }
    }

    /** Entities that are always drawn: parts of them can be seen through walls or outside their box. */
    public static boolean neverCull(Entity entity) {
        return entity instanceof PlayerEntity || entity instanceof FishingBobberEntity || entity.ignoreCameraFrustum
                || entity instanceof MobEntity m && m.isLeashed();
    }

    /** Culling thread: the opaque full blocks of a section, or null if it is not loaded. */
    private static long[] section(int sx, int sy, int sz) {
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) {
            return null;
        }
        if (sy < 0 || sy >= 16) {
            return OcclusionCuller.EMPTY;
        }
        Chunk chunk = world.getChunk(sx, sz);
        if (chunk == null || chunk.isEmpty()) {
            return null;
        }
        ChunkSection section = chunk.getBlockStorage()[sy];
        if (section == null || section.isEmpty()) {
            return OcclusionCuller.EMPTY;
        }
        long[] bits = new long[64];
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    BlockState state = section.getBlockState(x, y, z);
                    if (state.getBlock().hasTransparency()) { // 1.8 name of "opaque full cube"
                        int i = y << 8 | z << 4 | x;
                        bits[i >>> 6] |= 1L << (i & 63);
                    }
                }
            }
        }
        return bits;
    }
}
