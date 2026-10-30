package dev.lumora.client.compat;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen base for the Lumora screens on Minecraft 1.12.2, with the API of the modern {@code Screen}: widgets handle their
 * own input ({@link #addRenderableWidget}), {@code render(GuiGraphics, ...)}, {@code mouseClicked(double, double, int)},
 * {@code keyPressed(int, int, int)} with GLFW key codes, {@code onClose()} and focus. The 1.12.2 screen events
 * (mouseClicked(int, int, int), keyPressed(char, int), handleMouse for the wheel) are translated here.
 */
public abstract class Screen extends net.minecraft.client.gui.screen.Screen {
    private static final int ESCAPE = 256;
    private static final int TAB = 258;
    private static final int TOOLTIP_WIDTH = 200;

    protected MinecraftClient minecraft;
    protected Font font;
    protected final Component title;
    private final List<AbstractWidget> widgets = new ArrayList<>();
    private AbstractWidget focused;
    private AbstractWidget dragging;
    private int lastDragX;
    private int lastDragY;

    protected Screen(Component title) {
        this.title = title;
    }

    public Component getTitle() {
        return title;
    }

    /** Called by Minecraft when the screen opens and on every resize: widgets are rebuilt by {@link #init()}. */
    @Override
    public void init(MinecraftClient client, int width, int height) {
        this.minecraft = client;
        this.font = Font.get();
        widgets.clear();
        focused = null;
        dragging = null;
        super.init(client, width, height);
    }

    /** Builds the widgets (like the modern {@code init()}). */
    @Override
    public void init() {
    }

    protected <T extends AbstractWidget> T addRenderableWidget(T widget) {
        widgets.add(widget);
        return widget;
    }

    protected <T extends AbstractWidget> T addWidget(T widget) {
        return addRenderableWidget(widget);
    }

    public List<AbstractWidget> children() {
        return widgets;
    }

    protected void clearWidgets() {
        widgets.clear();
        focused = null;
        dragging = null;
    }

    protected void rebuildWidgets() {
        clearWidgets();
        init();
    }

    public AbstractWidget getFocused() {
        return focused;
    }

    public void setFocused(AbstractWidget widget) {
        if (focused != null && focused != widget) {
            focused.setFocused(false);
        }
        focused = widget;
        if (widget != null) {
            widget.setFocused(true);
        }
    }

    // --- rendering

    @Override
    public final void render(int mouseX, int mouseY, float delta) {
        GuiGraphics g = GuiGraphics.create();
        render(g, mouseX, mouseY, delta);
        for (int i = widgets.size() - 1; i >= 0; i--) {
            AbstractWidget w = widgets.get(i);
            if (w.visible && w.isHovered() && w.getTooltip() != null) {
                renderTooltip(font.split(w.getTooltip().text(), TOOLTIP_WIDTH), mouseX, mouseY);
                break;
            }
        }
    }

    /** Draws the widgets; screens draw their background first and call super (like the modern Screen#render). */
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        for (AbstractWidget w : widgets) {
            w.render(g, mouseX, mouseY, delta);
        }
        super.render(mouseX, mouseY, delta);
    }

    // --- mouse

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        lastDragX = mouseX;
        lastDragY = mouseY;
        if (!mouseClicked((double) mouseX, (double) mouseY, button)) {
            super.mouseClicked(mouseX, mouseY, button);
        }
    }

    public boolean mouseClicked(double mx, double my, int button) {
        for (int i = widgets.size() - 1; i >= 0; i--) {
            AbstractWidget w = widgets.get(i);
            if (w.mouseClicked(mx, my, button)) {
                setFocused(w);
                if (button == 0) {
                    dragging = w;
                }
                return true;
            }
        }
        setFocused(null);
        return false;
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int button) {
        mouseReleased((double) mouseX, (double) mouseY, button);
        super.mouseReleased(mouseX, mouseY, button);
    }

    public boolean mouseReleased(double mx, double my, int button) {
        if (dragging != null && button == 0) {
            AbstractWidget w = dragging;
            dragging = null;
            return w.mouseReleased(mx, my, button);
        }
        return false;
    }

    @Override
    protected void mouseDragged(int mouseX, int mouseY, int button, long heldMillis) {
        double dx = mouseX - lastDragX;
        double dy = mouseY - lastDragY;
        lastDragX = mouseX;
        lastDragY = mouseY;
        mouseDragged((double) mouseX, (double) mouseY, button, dx, dy);
    }

    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return dragging != null && dragging.mouseDragged(mx, my, button, dx, dy);
    }

    /** 1.12.2 screens get no wheel callback: the wheel of the current mouse event is turned into {@link #mouseScrolled}. */
    @Override
    public void handleMouse() {
        super.handleMouse();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0 && minecraft != null) {
            int mx = Mouse.getEventX() * width / minecraft.width;
            int my = height - Mouse.getEventY() * height / minecraft.height - 1;
            mouseScrolled(mx, my, wheel > 0 ? 1 : -1);
        }
    }

    public boolean mouseScrolled(double mx, double my, double amount) {
        for (int i = widgets.size() - 1; i >= 0; i--) {
            AbstractWidget w = widgets.get(i);
            if (w.visible && w.isMouseOver(mx, my) && w.mouseScrolled(mx, my, amount)) {
                return true;
            }
        }
        return false;
    }

    // --- keyboard (GLFW codes, see Keys)

    @Override
    protected void keyPressed(char chr, int lwjglKey) {
        if (focused != null && focused.legacyKeyTyped(chr, lwjglKey)) {
            return;
        }
        if (keyPressed(Keys.toGlfw(lwjglKey), 0, modifiers())) {
            return;
        }
        if (chr >= 32 && chr != 127) {
            charTyped(chr, modifiers());
        }
    }

    /** GLFW modifier bits (shift 1, control 2, alt 4) of the current keyboard state. */
    private static int modifiers() {
        int m = 0;
        if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) {
            m |= 1;
        }
        if (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL)) {
            m |= 2;
        }
        if (Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU)) {
            m |= 4;
        }
        return m;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == ESCAPE && shouldCloseOnEsc()) {
            onClose();
            return true;
        }
        if (keyCode == TAB) {
            moveFocus((modifiers & 1) != 0 ? -1 : 1);
            return true;
        }
        return focused != null && focused.keyPressed(keyCode, scanCode, modifiers);
    }

    public boolean charTyped(char chr, int modifiers) {
        return focused != null && focused.charTyped(chr, modifiers);
    }

    private void moveFocus(int direction) {
        List<AbstractWidget> candidates = new ArrayList<>();
        for (AbstractWidget w : widgets) {
            if (w.active && w.visible) {
                candidates.add(w);
            }
        }
        if (candidates.isEmpty()) {
            return;
        }
        int i = candidates.indexOf(focused);
        int next = i < 0 ? (direction > 0 ? 0 : candidates.size() - 1) : Math.floorMod(i + direction, candidates.size());
        setFocused(candidates.get(next));
    }

    // --- lifecycle

    @Override
    public void tick() {
        for (AbstractWidget w : widgets) {
            w.tick();
        }
    }

    public void onClose() {
        minecraft.setScreen(null);
    }

    public boolean shouldCloseOnEsc() {
        return true;
    }

    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public boolean shouldPauseGame() {
        return isPauseScreen();
    }
}
