package dev.lumora.client.gui;

import dev.lumora.api.screen.AddonView;
import dev.lumora.client.api.LumoraRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Shows an addon menu ({@link AddonView}, Lumora API 1.1) on Minecraft 26.2. The screen only adapts
 * Minecraft's rendering and input; the view draws everything itself, including its background.
 * <p>
 * Minecraft calls {@link #removed()} whenever another screen replaces this one. The view is only told it was removed
 * when it really goes away: not while a screen opened from it (another addon view, the key settings, the HUD
 * editor) is shown on top and will return to it.
 */
public final class AddonViewScreen extends Screen {
    private final Screen parent;
    private final AddonView view;
    /** A screen that returns here is being opened on top. */
    private boolean keepView;
    private boolean closed;

    private AddonViewScreen(Screen parent, AddonView view) {
        super(Component.literal("Lumora"));
        this.parent = parent;
        this.view = view;
    }

    /** A screen showing {@code view} that returns to {@code parent} (Lumora server and world lists). */
    public static Screen create(Screen parent, AddonView view) {
        return new AddonViewScreen(parent, view);
    }

    /** Shows {@code view} on top of the current screen. */
    public static void show(AddonView view) {
        keepCurrent();
        Minecraft mc = Minecraft.getInstance();
        mc.gui.setScreen(new AddonViewScreen(mc.gui.screen(), view));
    }

    /** Closes {@code view} if it is the current screen. */
    public static void close(AddonView view) {
        if (Minecraft.getInstance().gui.screen() instanceof AddonViewScreen s && s.view == view) {
            s.onClose();
        }
    }

    /** Whether an addon menu is the current screen (smoke test). */
    public static boolean isShowing() {
        return Minecraft.getInstance().gui.screen() instanceof AddonViewScreen;
    }

    /** Call before opening a screen that returns to the current one (key settings, HUD editor). */
    public static void keepCurrent() {
        if (Minecraft.getInstance().gui.screen() instanceof AddonViewScreen s) {
            s.keepView = true;
        }
    }

    @Override
    protected void init() {
        keepView = false; // shown (again)
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        // the view draws its own background
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        view.render(LumoraRuntime.graphics(g), width, height, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        return view.mouseClicked(e.x(), e.y(), e.button());
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        return view.mouseReleased(e.x(), e.y(), e.button());
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        return view.mouseDragged(e.x(), e.y(), e.button());
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        return view.mouseScrolled(mx, my, sy);
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (e.key() == 256) {
            if (view.closeRequested()) {
                onClose();
            }
            return true;
        }
        return view.keyPressed(e.key(), e.hasControlDown(), e.hasShiftDown());
    }

    @Override
    public boolean charTyped(CharacterEvent e) {
        return Character.isBmpCodePoint(e.codepoint()) && view.charTyped((char) e.codepoint());
    }

    @Override
    public void onClose() {
        closed = true;
        minecraft.gui.setScreen(parent);
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
    public boolean isPauseScreen() {
        return false;
    }
}
