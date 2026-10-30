package dev.lumora.client.hud;

import net.minecraft.client.Minecraft;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Clicks per second of the attack and use buttons, sampled every rendered frame from the key states (no input is
 * intercepted or changed). At normal frame rates every click is seen.
 */
public final class ClickTracker {
    private static final Deque<Long> LEFT = new ArrayDeque<>();
    private static final Deque<Long> RIGHT = new ArrayDeque<>();
    private static boolean leftDown;
    private static boolean rightDown;

    private ClickTracker() {
    }

    public static void update(Minecraft mc) {
        long now = System.currentTimeMillis();
        boolean l = mc.options.keyAttack.isDown();
        boolean r = mc.options.keyUse.isDown();
        if (l && !leftDown) {
            LEFT.addLast(now);
        }
        if (r && !rightDown) {
            RIGHT.addLast(now);
        }
        leftDown = l;
        rightDown = r;
        prune(LEFT, now);
        prune(RIGHT, now);
    }

    private static void prune(Deque<Long> q, long now) {
        while (!q.isEmpty() && now - q.peekFirst() > 1000) {
            q.removeFirst();
        }
    }

    public static int left() {
        prune(LEFT, System.currentTimeMillis());
        return LEFT.size();
    }

    public static int right() {
        prune(RIGHT, System.currentTimeMillis());
        return RIGHT.size();
    }
}
