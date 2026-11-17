package dev.lumora.client.render;

import dev.lumora.core.extras.light.LightProbe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * World access of the module "Light Block Overlay": walks a chunk column bottom up with one block state lookup per
 * position (the states below and at head height are carried along) and asks Minecraft's own spawn rules only where
 * the cheap checks pass. Light is read only for real spawn positions. Client thread only.
 */
public final class LightOverlayProbe implements LightProbe {
    /** Spawn rules are asked for a zombie (the usual two blocks high hostile mob). */
    private static final EntityType<?> MOB = net.minecraft.world.entity.EntityTypes.ZOMBIE;
    public static final LightOverlayProbe INSTANCE = new LightOverlayProbe();

    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

    private LightOverlayProbe() {
    }

    @Override
    public Object world() {
        return Minecraft.getInstance().level;
    }

    @Override
    public boolean player(int[] out) {
        Minecraft mc = Minecraft.getInstance();
        Entity e = mc.getCameraEntity() != null ? mc.getCameraEntity() : mc.player;
        if (e == null) {
            return false;
        }
        BlockPos b = e.blockPosition();
        out[0] = b.getX();
        out[1] = b.getY();
        out[2] = b.getZ();
        out[3] = e.getDirection().get2DDataValue(); // 0 south, 1 west, 2 north, 3 east
        return true;
    }

    @Override
    public boolean scan(int chunkX, int chunkZ, int minY, int maxY, boolean surfaces, Sink sink) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return false;
        }
        LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
        if (chunk == null) {
            return false;
        }
        BlockPos.MutableBlockPos p = pos;
        int bx = chunkX << 4;
        int bz = chunkZ << 4;
        for (int x = 0; x < 16; x++) {
            int wx = bx + x;
            for (int z = 0; z < 16; z++) {
                int wz = bz + z;
                BlockState below = chunk.getBlockState(p.set(wx, minY - 1, wz));
                BlockState feet = chunk.getBlockState(p.set(wx, minY, wz));
                for (int y = minY; y <= maxY; y++) {
                    BlockState head = chunk.getBlockState(p.set(wx, y + 1, wz));
                    if (surfaces ? !below.isAir() && open(level, feet, wx, y, wz) && sturdy(level, below, wx, y - 1, wz)
                            : !below.isAir() && free(level, feet, wx, y, wz) && free(level, head, wx, y + 1, wz)
                            && below.isValidSpawn(level, p.set(wx, y - 1, wz), MOB)) {
                        p.set(wx, y, wz);
                        sink.mark(x, y, z, level.getBrightness(LightLayer.BLOCK, p), level.getBrightness(LightLayer.SKY, p));
                    }
                    below = feet;
                    feet = head;
                }
            }
        }
        return true;
    }

    /** Light level mode: you can stand in this block (no collision, no liquid). */
    private boolean open(ClientLevel level, BlockState s, int x, int y, int z) {
        return s.isAir() || s.getFluidState().isEmpty() && s.getCollisionShape(level, pos.set(x, y, z)).isEmpty();
    }

    /** Light level mode: the block has a full top to stand on (no slab, fence, glass pane, liquid). */
    private boolean sturdy(ClientLevel level, BlockState s, int x, int y, int z) {
        return s.isFaceSturdy(level, pos.set(x, y, z), net.minecraft.core.Direction.UP);
    }

    /** A mob fits into this block: Minecraft's rule (no full block, liquid, rail ...) and nothing to stand on in it (carpet). */
    private boolean free(ClientLevel level, BlockState s, int x, int y, int z) {
        if (s.isAir()) {
            return true;
        }
        BlockPos.MutableBlockPos p = pos.set(x, y, z);
        return NaturalSpawner.isValidEmptySpawnBlock(level, p, s, s.getFluidState(), MOB)
                && s.getCollisionShape(level, p).isEmpty();
    }
}
