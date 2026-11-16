package dev.lumora.client.perf;

import dev.lumora.client.LumoraClient;
import dev.lumora.core.perf.OcclusionCuller;
import dev.lumora.core.perf.PerformanceEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ChunkStatus;

/**
 * Minecraft 1.19.4 side of the occlusion culler: the opaque full blocks of the client world (read on the culling thread)
 * and the rules for entities that are never culled because something of them can be seen through walls (name tags,
 * glowing outlines, leashes, fishing lines, players with their elytra trails).
 */
public final class Culling {
    private static OcclusionCuller culler;
    private static ClientLevel lastLevel;

    private Culling() {
    }

    /** Creates the culler (client start). */
    public static void init() {
        culler = new OcclusionCuller(Culling::section, msg -> LumoraClient.LOG.warn(msg));
        PerformanceEngine.culler(culler);
    }

    /** The culler while entity culling is switched on, otherwise null. */
    public static OcclusionCuller active() {
        return PerformanceEngine.entityCulling ? culler : null;
    }

    /** Start of a frame's entity selection (render thread). */
    public static void frame(double camX, double camY, double camZ) {
        OcclusionCuller c = culler;
        if (c == null || !PerformanceEngine.entityCulling) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level != lastLevel) {
            lastLevel = level;
            c.reset();
        }
        c.frame(camX, camY, camZ);
    }

    /** Blocks of a section changed (client thread). */
    public static void invalidate(int sx, int sy, int sz) {
        OcclusionCuller c = culler;
        if (c != null && PerformanceEngine.entityCulling) {
            c.invalidate(sx, sy, sz);
        }
    }

    /** Entities that are always drawn: parts of them can be seen through walls or outside their box. */
    public static boolean neverCull(Entity entity, Minecraft mc) {
        return entity instanceof Player || entity instanceof FishingHook
                || entity instanceof Mob m && m.isLeashed()
                || mc.shouldEntityAppearGlowing(entity);
    }

    /** Culling thread: the opaque full blocks of a section, or null if it is not loaded. */
    private static long[] section(int sx, int sy, int sz) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return null;
        }
        int index = level.getSectionIndexFromSectionY(sy);
        if (index < 0 || index >= level.getSectionsCount()) {
            return OcclusionCuller.EMPTY;
        }
        LevelChunk chunk = level.getChunkSource().getChunk(sx, sz, ChunkStatus.FULL, false);
        if (chunk == null) {
            return null;
        }
        LevelChunkSection section = chunk.getSection(index);
        if (section == null || section.hasOnlyAir() || !section.maybeHas(s -> s.isSolidRender(EmptyBlockGetter.INSTANCE, BlockPos.ZERO))) {
            return OcclusionCuller.EMPTY;
        }
        long[] bits = new long[64];
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    if (section.getBlockState(x, y, z).isSolidRender(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) {
                        int i = y << 8 | z << 4 | x;
                        bits[i >>> 6] |= 1L << (i & 63);
                    }
                }
            }
        }
        return bits;
    }
}
