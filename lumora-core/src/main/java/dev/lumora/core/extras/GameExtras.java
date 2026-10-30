package dev.lumora.core.extras;

import java.util.List;

/**
 * Game details for the built-in Lumora modules (potion HUD, item counter, combo counter, fullbright, toggle sprint),
 * supplied once per Minecraft version (client/mc&lt;version&gt;/api/McExtras). Everything is read on the client thread;
 * {@link #NONE} is used where a version adapter does not provide something.
 */
public interface GameExtras {
    /** An active status effect: translated name, amplifier (0 = level I), remaining ticks (-1 = infinite). */
    record Effect(String name, int amplifier, int durationTicks, boolean beneficial) {
    }

    List<Effect> effects();

    /** Number of items with this id (e.g. "minecraft:arrow") in the player's whole inventory, offhand included. */
    int count(String itemId);

    /** Registry id of the main-hand item, "" when empty. */
    String heldItemId();

    /** Display name of the main-hand item, "" when empty. */
    String heldItemName();

    /** World time in ticks (day = time / 24000), -1 without a world. */
    long dayTime();

    /** Names of the resource packs the player selected (without vanilla, Fabric and mod resources). */
    List<String> resourcePacks();

    /** Network id of the entity under the crosshair, -1 if none. */
    int targetEntityId();

    /** hurtTime of an entity in the client world (10 right after a hit, then counting down), -1 if unknown. */
    int hurtTime(int entityId);

    /** hurtTime of the local player, 0 without a player. */
    int ownHurtTime();

    /** Current brightness option (vanilla 0..1). */
    double gamma();

    /** Sets the brightness, also above vanilla's range (fullbright). */
    void setGamma(double value);

    /** GLFW key of the vanilla sprint binding, -1 when it is a mouse button or unbound. */
    int sprintKey();

    /** GLFW key of the vanilla sneak binding, -1 when it is a mouse button or unbound. */
    int sneakKey();

    /** Holds the vanilla sprint key down (toggle sprint) or releases it. */
    void holdSprint(boolean down);

    /** Holds the vanilla sneak key down (toggle sneak) or releases it. */
    void holdSneak(boolean down);

    /** Position of the block under the crosshair packed as x/y/z (any unique long), {@link Long#MIN_VALUE} if none. */
    default long targetBlockPos() {
        return Long.MIN_VALUE;
    }

    /** Display name of the block under the crosshair, "" if none. */
    default String targetBlockName() {
        return "";
    }

    /** Registry id of the block under the crosshair ("minecraft:stone"), "" if none. */
    default String targetBlockId() {
        return "";
    }

    /** Packs a block position into one long (26 bits x/z, 12 bits y), like Minecraft's BlockPos#asLong. */
    static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    GameExtras NONE = new GameExtras() {
        @Override
        public List<Effect> effects() {
            return List.of();
        }

        @Override
        public int count(String itemId) {
            return 0;
        }

        @Override
        public String heldItemId() {
            return "";
        }

        @Override
        public String heldItemName() {
            return "";
        }

        @Override
        public long dayTime() {
            return -1;
        }

        @Override
        public List<String> resourcePacks() {
            return List.of();
        }

        @Override
        public int targetEntityId() {
            return -1;
        }

        @Override
        public int hurtTime(int entityId) {
            return -1;
        }

        @Override
        public int ownHurtTime() {
            return 0;
        }

        @Override
        public double gamma() {
            return 0.5;
        }

        @Override
        public void setGamma(double value) {
        }

        @Override
        public int sprintKey() {
            return -1;
        }

        @Override
        public int sneakKey() {
            return -1;
        }

        @Override
        public void holdSprint(boolean down) {
        }

        @Override
        public void holdSneak(boolean down) {
        }
    };
}
