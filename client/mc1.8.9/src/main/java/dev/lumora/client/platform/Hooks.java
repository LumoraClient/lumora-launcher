package dev.lumora.client.platform;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * The client's own game hooks on Minecraft 1.8.9 / 1.12.2, fired by the mixins in
 * {@code dev.lumora.client.mixin.platform}. They replace the Legacy Fabric API events at the same places:
 * <ul>
 *   <li>started - {@code MinecraftClient.run()} right after {@code initializeGame()},</li>
 *   <li>stopping - {@code MinecraftClient.stop()},</li>
 *   <li>end of tick - {@code MinecraftClient.tick()} return,</li>
 *   <li>HUD - end of {@code InGameHud.render(float)},</li>
 *   <li>key bindings - appended to {@code GameOptions.allKeys} before options.txt is read.</li>
 * </ul>
 * Screens are hooked by the client's own mixins on these versions.
 */
public final class Hooks {
    public interface HudLayer {
        void render(MinecraftClient mc, float delta);
    }

    private static final List<Consumer<MinecraftClient>> STARTED = new CopyOnWriteArrayList<>();
    private static final List<Consumer<MinecraftClient>> STOPPING = new CopyOnWriteArrayList<>();
    private static final List<Consumer<MinecraftClient>> END_TICK = new CopyOnWriteArrayList<>();
    private static final List<HudLayer> HUD = new CopyOnWriteArrayList<>();
    private static final List<KeyBinding> KEYS = new ArrayList<>();
    private static boolean started;

    private Hooks() {
    }

    public static void onStarted(Consumer<MinecraftClient> l) {
        STARTED.add(l);
    }

    public static void onStopping(Consumer<MinecraftClient> l) {
        STOPPING.add(l);
    }

    public static void onEndTick(Consumer<MinecraftClient> l) {
        END_TICK.add(l);
    }

    public static void hudLayer(HudLayer l) {
        HUD.add(l);
    }

    /** A key binding in Options > Controls (added to {@code GameOptions.allKeys} before the options are read). */
    public static synchronized KeyBinding registerKey(KeyBinding key) {
        KEYS.add(key);
        return key;
    }

    // --- called by the mixins

    public static void fireStarted(MinecraftClient mc) {
        if (!started) {
            started = true;
            STARTED.forEach(l -> l.accept(mc));
        }
    }

    public static void fireStopping(MinecraftClient mc) {
        STOPPING.forEach(l -> l.accept(mc));
    }

    public static void fireEndTick(MinecraftClient mc) {
        END_TICK.forEach(l -> l.accept(mc));
    }

    public static void fireHud(MinecraftClient mc, float delta) {
        HUD.forEach(l -> l.render(mc, delta));
    }

    /** {@code allKeys} with Lumora's keys appended (each once). */
    public static synchronized KeyBinding[] withKeys(KeyBinding[] vanilla) {
        List<KeyBinding> all = new ArrayList<>(List.of(vanilla));
        for (KeyBinding k : KEYS) {
            if (!all.contains(k)) {
                all.add(k);
            }
        }
        return all.toArray(new KeyBinding[0]);
    }
}
