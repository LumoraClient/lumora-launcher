package dev.lumora.client.platform;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * The client's own game hooks, fired by its mixins in {@code dev.lumora.client.mixin.platform}. They replace what a
 * mod loader's API provided before (lifecycle, tick, screen and HUD events, key mappings), at the same places in the
 * game:
 * <ul>
 *   <li>started - {@code Minecraft.run()} when the game thread is set (before the first frame),</li>
 *   <li>stopping - {@code Minecraft.destroy()},</li>
 *   <li>end of tick - {@code Minecraft.tick()} return,</li>
 *   <li>screen init - end of {@code Screen.init(Minecraft, int, int)} (also on resize, which calls it again),</li>
 *   <li>after screen - end of {@code Screen.renderWithTooltip},</li>
 *   <li>HUD - end of {@code Hud.extractRenderState} (on top of everything else),</li>
 *   <li>mouse click - start of {@code ChatScreen.mouseClicked} (the only screen Lumora needs it for).</li>
 * </ul>
 * Register from the entry point; the lists are read on the client thread.
 */
public final class Hooks {
    public interface ScreenInit {
        void afterInit(Minecraft mc, Screen screen, int width, int height);
    }

    public interface ScreenExtract {
        void afterExtract(Screen screen, GuiGraphics g, int mouseX, int mouseY, float delta);
    }

    public interface HudLayer {
        void extract(GuiGraphics g, float delta);
    }

    /** False consumes the click (the screen does not see it). */
    public interface ChatClick {
        boolean allow(Screen screen, double x, double y, int button);
    }

    private static final List<Consumer<Minecraft>> STARTED = new CopyOnWriteArrayList<>();
    private static final List<Consumer<Minecraft>> STOPPING = new CopyOnWriteArrayList<>();
    private static final List<Consumer<Minecraft>> END_TICK = new CopyOnWriteArrayList<>();
    private static final List<ScreenInit> SCREEN_INIT = new CopyOnWriteArrayList<>();
    private static final List<ScreenExtract> SCREEN_EXTRACT = new CopyOnWriteArrayList<>();
    private static final List<HudLayer> HUD = new CopyOnWriteArrayList<>();
    private static final List<ChatClick> CHAT_CLICK = new CopyOnWriteArrayList<>();
    private static final List<KeyMapping> KEYS = new ArrayList<>();
    private static boolean started;

    private Hooks() {
    }

    public static void onStarted(Consumer<Minecraft> l) {
        STARTED.add(l);
    }

    public static void onStopping(Consumer<Minecraft> l) {
        STOPPING.add(l);
    }

    public static void onEndTick(Consumer<Minecraft> l) {
        END_TICK.add(l);
    }

    public static void afterScreenInit(ScreenInit l) {
        SCREEN_INIT.add(l);
    }

    public static void afterScreenExtract(ScreenExtract l) {
        SCREEN_EXTRACT.add(l);
    }

    public static void hudLayer(HudLayer l) {
        HUD.add(l);
    }

    public static void chatClick(ChatClick l) {
        CHAT_CLICK.add(l);
    }

    /** A key mapping in Options > Controls (added to {@code Options.keyMappings} before the options are read). */
    public static synchronized KeyMapping registerKey(KeyMapping key) {
        KEYS.add(key);
        return key;
    }

    /** Adds a widget to a screen (from a screen-init hook). */
    public static <T extends GuiEventListener & net.minecraft.client.gui.components.Renderable
            & net.minecraft.client.gui.narration.NarratableEntry> T addWidget(Screen screen, T widget) {
        ((dev.lumora.client.mixin.platform.ScreenWidgetsInvoker) screen).lumora$addRenderableWidget(widget);
        return widget;
    }

    // --- called by the mixins

    public static void fireStarted(Minecraft mc) {
        if (!started) {
            started = true;
            STARTED.forEach(l -> l.accept(mc));
        }
    }

    public static void fireStopping(Minecraft mc) {
        STOPPING.forEach(l -> l.accept(mc));
    }

    public static void fireEndTick(Minecraft mc) {
        END_TICK.forEach(l -> l.accept(mc));
    }

    public static void fireScreenInit(Screen screen, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        SCREEN_INIT.forEach(l -> l.afterInit(mc, screen, width, height));
    }

    public static void fireScreenExtract(Screen screen, GuiGraphics g, int mx, int my, float delta) {
        SCREEN_EXTRACT.forEach(l -> l.afterExtract(screen, g, mx, my, delta));
    }

    public static void fireHud(GuiGraphics g, float delta) {
        HUD.forEach(l -> l.extract(g, delta));
    }

    /** @return false when a hook consumed the click */
    public static boolean fireChatClick(Screen screen, double x, double y, int button) {
        for (ChatClick l : CHAT_CLICK) {
            if (!l.allow(screen, x, y, button)) {
                return false;
            }
        }
        return true;
    }

    /** {@code keyMappings} with Lumora's keys appended (each once). */
    public static synchronized KeyMapping[] withKeys(KeyMapping[] vanilla) {
        List<KeyMapping> all = new ArrayList<>(List.of(vanilla));
        for (KeyMapping k : KEYS) {
            if (!all.contains(k)) {
                all.add(k);
            }
        }
        return all.toArray(KeyMapping[]::new);
    }
}
