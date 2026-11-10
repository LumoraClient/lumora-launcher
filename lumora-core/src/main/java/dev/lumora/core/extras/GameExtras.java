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

    /** An item on cooldown (ender pearl, chorus fruit, wind charge, shield after an axe hit …): id, name, remaining 0..1. */
    record Cooldown(String itemId, String name, float remaining) {
    }

    /**
     * Progress shown by the attack indicator. {@code attack}: attack strength 0..1, -1 without attack cooldown (1.8.9);
     * {@code target}: a living entity is under the crosshair; {@code use}: what the used item does ("bow", "crossbow",
     * "spear", "eat", "drink") or ""; {@code useProgress} 0..1; {@code breaking}: block breaking 0..1 or -1;
     * {@code sleep}: falling asleep 0..1 or -1; {@code vanillaMode}: Minecraft's own attack indicator option (0 off,
     * 1 crosshair, 2 hotbar); {@code firstPerson}: the crosshair is shown (first person camera).
     */
    record Progress(float attack, boolean target, boolean firstPerson, List<Cooldown> cooldowns, String use,
                    float useProgress, float breaking, float sleep, int vanillaMode) {
        public static final Progress NONE = new Progress(-1, false, true, List.of(), "", 0, -1, -1, 1);
    }

    /** Attack strength, cooldowns and use progress of the own player ({@link Progress#NONE} without a player). */
    default Progress progress(float partialTick) {
        return Progress.NONE;
    }

    /**
     * Plays a short UI sound: "ready" (attack ready), "cooldown" (an item cooldown ended) or "mention" (the own name in
     * the chat); volume 0..1.
     */
    default void playSound(String kind, float volume) {
    }

    /**
     * What the hotbar and the status bars show (module "Hotbar & Leisten").
     *
     * @param present    a player exists
     * @param survival   health, armor, food, air and experience are shown (not in creative or spectator)
     * @param spectator  spectator mode (no hotbar)
     * @param hearts     heart type: "normal", "poisoned", "withered" or "frozen"
     * @param hunger     the hunger effect is active (greenish food icons)
     * @param underwater the eyes are in water (air bubbles shown)
     * @param selected   selected hotbar slot 0..8
     */
    record Status(boolean present, boolean survival, boolean spectator, float health, float maxHealth, float absorption,
                  int armor, int food, int air, int maxAir, boolean underwater, int xpLevel, float xpProgress,
                  boolean hardcore, String hearts, boolean hunger, int selected) {
        public static final Status NONE = new Status(false, false, false, 0, 20, 0, 0, 0, 300, 300, false, 0, 0, false,
                "normal", false, 0);
    }

    /** Values for the hotbar and status bars ({@link Status#NONE} without a player). */
    default Status status() {
        return Status.NONE;
    }

    /**
     * Draws the item of a slot at (x, y) as Minecraft's hotbar does (item, count, durability bar).
     *
     * @param slot 0..8 hotbar, 9 offhand
     */
    default void drawSlot(dev.lumora.api.render.HudGraphics g, int slot, int x, int y) {
    }

    /** Registry id of the item in a slot (0..8 hotbar, 9 offhand), "" if empty. */
    default String slotItem(int slot) {
        return "";
    }

    /**
     * Where Minecraft's chat window is (module "Chat"), in GUI pixels.
     *
     * @param left      left edge of the chat background
     * @param right     right edge of the chat background
     * @param bottom    lower edge of the newest line
     * @param rowHeight height of one chat line on screen (line height times chat scale)
     * @param rows      lines that fit in the window (open or closed, whichever is shown)
     * @param scroll    lines scrolled up
     * @param lines     wrapped lines in the chat (index 0 is the newest)
     * @param width     Minecraft's chat width (as returned by its getWidth)
     * @param height    Minecraft's chat height (as returned by its getHeight)
     * @param scale     chat scale
     */
    record ChatView(int left, int right, int bottom, double rowHeight, int rows, int scroll, int lines, int width,
                    int height, double scale, int guiWidth, int guiHeight) {
        public int top() {
            return (int) Math.floor(bottom - rows * rowHeight);
        }
    }

    /**
     * One chat message (all its wrapped lines).
     *
     * @param text       plain text (with Lumora's own timestamp, if any)
     * @param insertions shift-click insertions of the message parts (servers put the sender's name there)
     * @param commands   commands of its click events (e.g. "/msg Name ")
     * @param links      URLs of its click events
     * @param firstLine  index of its lowest (newest) wrapped line
     * @param lastLine   index of its highest wrapped line
     */
    record ChatMessage(String text, List<String> insertions, List<String> commands, List<String> links, int firstLine,
                       int lastLine) {
    }

    /** Chat window position and size; null if there is no chat (module "Chat"). */
    default ChatView chatView() {
        return null;
    }

    /** The message a wrapped chat line belongs to (0 = newest line); null if there is none. */
    default ChatMessage chatMessage(int line) {
        return null;
    }

    /** Wraps the chat lines again (after the chat width changed). */
    default void refreshChat() {
    }

    /** Replaces the text in the open chat's input field. */
    default void setChatInput(String text) {
    }

    /** Left mouse button held (dragging the chat edges). */
    default boolean mouseLeftDown() {
        return false;
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
