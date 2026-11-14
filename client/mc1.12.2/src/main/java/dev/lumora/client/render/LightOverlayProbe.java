package dev.lumora.client.render;

import dev.lumora.core.extras.light.LightProbe;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MobSpawnerHelper;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.LightType;
import net.minecraft.world.chunk.Chunk;

/**
 * World access of the module "Light Block Overlay": walks a chunk column bottom up with one block state lookup per
 * position (the states below and at head height are carried along) and asks Minecraft's own spawn rule
 * ({@link MobSpawnerHelper#isSpawnable}) only where the cheap checks pass. Light is read only for real spawn positions.
 * Client thread only.
 */
public final class LightOverlayProbe implements LightProbe {
    public static final LightOverlayProbe INSTANCE = new LightOverlayProbe();
    /** Lowest and highest block a mob can stand in (the world is 256 blocks high). */
    static final int MIN_Y = 1;
    static final int MAX_Y = 254;

    private final BlockPos.Mutable pos = new BlockPos.Mutable();

    private LightOverlayProbe() {
    }

    @Override
    public Object world() {
        return MinecraftClient.getInstance().world;
    }

    @Override
    public boolean player(int[] out) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Entity e = mc.getCameraEntity() != null ? mc.getCameraEntity() : mc.player;
        if (e == null) {
            return false;
        }
        out[0] = MathHelper.floor(e.x);
        out[1] = MathHelper.floor(e.y);
        out[2] = MathHelper.floor(e.z);
        out[3] = MathHelper.floor(e.yaw * 4.0 / 360.0 + 0.5) & 3; // 0 south, 1 west, 2 north, 3 east
        return true;
    }

    @Override
    public boolean scan(int chunkX, int chunkZ, int minY, int maxY, Sink sink) {
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) {
            return false;
        }
        Chunk chunk = world.getChunk(chunkX, chunkZ);
        if (chunk == null || chunk.isEmpty()) {
            return false;
        }
        int lo = Math.max(MIN_Y, minY);
        int hi = Math.min(MAX_Y, maxY);
        if (lo > hi) {
            return true;
        }
        BlockPos.Mutable p = pos;
        int bx = chunkX << 4;
        int bz = chunkZ << 4;
        for (int x = 0; x < 16; x++) {
            int wx = bx + x;
            for (int z = 0; z < 16; z++) {
                int wz = bz + z;
                BlockState below = chunk.getBlockState(p.setPosition(wx, lo - 1, wz));
                BlockState feet = chunk.getBlockState(p.setPosition(wx, lo, wz));
                for (int y = lo; y <= hi; y++) {
                    BlockState head = chunk.getBlockState(p.setPosition(wx, y + 1, wz));
                    if (below.getBlock() != Blocks.AIR && free(feet) && free(head)
                            && MobSpawnerHelper.isSpawnable(MobEntity.Location.ON_GROUND, world, p.setPosition(wx, y, wz))) {
                        p.setPosition(wx, y, wz);
                        sink.mark(x, y, z, world.getLightAtPos(LightType.BLOCK, p), world.getLightAtPos(LightType.SKY, p));
                    }
                    below = feet;
                    feet = head;
                }
            }
        }
        return true;
    }

    /** Quick check before Minecraft's rule: no full block in the way. */
    private static boolean free(BlockState s) {
        return s.getBlock() == Blocks.AIR || MobSpawnerHelper.method_11496(s); // Minecraft's "nothing in the way" check
    }
}
