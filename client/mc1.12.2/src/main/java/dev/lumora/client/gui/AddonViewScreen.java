package dev.lumora.client.gui;

import dev.lumora.api.screen.AddonView;
import dev.lumora.client.api.LumoraRuntime;
import dev.lumora.client.compat.Component;
import dev.lumora.client.compat.GuiGraphics;
import dev.lumora.client.compat.Screen;
import net.minecraft.client.MinecraftClient;

/**
 * Shows an addon menu ({@link AddonView}, Lumora API 1.1) on Minecraft 1.12.2. The screen only adapts
 * Minecraft's rendering and input; the view draws everything itself, including its background.
 * <p>
 * Minecraft calls {@link #removed()} whenever another screen replaces this one. The view is only told it was removed
 * when it really goes away: not while a screen opened from it (another addon view, the key settings, the HUD
 * editor) is shown on top and will return to it.
 */
public final class AddonViewScreen extends Screen {
    private final net.minecraft.client.gui.screen.Screen parent;
    private final AddonView view;
    /** A screen that returns here is being opened on top. */
    private boolean keepView;
    private boolean closed;
    /** Ticks the screen this one replaces (Minecraft's connect screen keeps running underneath). */
    private Runnable ticker;

    private AddonViewScreen(net.minecraft.client.gui.screen.Screen parent, AddonView view) {
        super(Component.literal("Lumora"));
        this.parent = parent;
        this.view = view;
    }

    /** A screen showing {@code view} that returns to {@code parent} (Lumora server and world lists). */
    public static net.minecraft.client.gui.screen.Screen create(net.minecraft.client.gui.screen.Screen parent, AddonView view) {
        return new AddonViewScreen(parent, view);
    }

    /**
     * A screen showing {@code view} that also ticks the Minecraft screen it stands in for ({@code tick}): Lumora's
     * connecting screen keeps Minecraft's connect screen, which drives the connection, running underneath.
     */
    public static net.minecraft.client.gui.screen.Screen connecting(net.minecraft.client.gui.screen.Screen parent, AddonView view, Runnable tick) {
        AddonViewScreen s = new AddonViewScreen(parent, view);
        s.ticker = tick;
        return s;
    }

    /** Shows {@code view} on top of the current screen. */
    public static void show(AddonView view) {
        keepCurrent();
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.setScreen(new AddonViewScreen(mc.currentScreen, view));
    }

    /** Closes {@code view} if it is the current screen. */
    public static void close(AddonView view) {
        if (MinecraftClient.getInstance().currentScreen instanceof AddonViewScreen s && s.view == view) {
            s.onClose();
        }
    }

    /** Whether an addon menu is the current screen (smoke test). */
    public static boolean isShowing() {
        return MinecraftClient.getInstance().currentScreen instanceof AddonViewScreen;
    }

    /** Call before opening a screen that returns to the current one (key settings, HUD editor). */
    public static void keepCurrent() {
        if (MinecraftClient.getInstance().currentScreen instanceof AddonViewScreen s) {
            s.keepView = true;
        }
    }

    @Override
    public void init() {
        keepView = false; // shown (again)
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        view.render(LumoraRuntime.graphics(g), width, height, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        return view.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        return view.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return view.mouseDragged(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        return view.mouseScrolled(mx, my, delta);
    }


    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            if (view.closeRequested()) {
                onClose();
            }
            return true;
        }
        // GLFW modifier bits: 1 = shift, 2 = control
        return view.keyPressed(keyCode, (modifiers & 2) != 0, (modifiers & 1) != 0);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return view.charTyped(chr);
    }

    @Override
    public void onClose() {
        closed = true;
        minecraft.setScreen(parent);
    }

    @Override
    public void removed() {
        if (keepView) {
            return;
        }
        view.removed();
        if (!closed) {
            // replaced by something else (disconnect, death screen, ...): the views below are gone as well
            Object below = parent;
            while (below instanceof AddonViewScreen s) {
                s.view.removed();
                below = s.parent;
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (ticker != null) {
            ticker.run();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
