package dev.lumora.client.compat;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.util.Identifier;

/**
 * Widget base for the Lumora screens on Minecraft 1.12.2, with the API of the modern {@code AbstractWidget}: 1.12.2 only
 * has ButtonWidget, which its screen dispatches through ids. These widgets handle their own input; {@link Screen}
 * routes mouse and keyboard events to them (key codes as GLFW codes, see {@link Keys}).
 */
public abstract class AbstractWidget {
    private int x;
    private int y;
    protected int width;
    protected int height;
    private Component message;
    private Tooltip tooltip;
    public boolean active = true;
    public boolean visible = true;
    protected boolean isHovered;
    private boolean focused;

    protected AbstractWidget(int x, int y, int width, int height, Component message) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.message = message;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public void setX(int x) {
        this.x = x;
    }

    public void setY(int y) {
        this.y = y;
    }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getRight() {
        return x + width;
    }

    public int getBottom() {
        return y + height;
    }

    public Component getMessage() {
        return message;
    }

    public void setMessage(Component message) {
        this.message = message;
    }

    public void setTooltip(Tooltip tooltip) {
        this.tooltip = tooltip;
    }

    public Tooltip getTooltip() {
        return tooltip;
    }

    public boolean isHovered() {
        return isHovered;
    }

    public boolean isFocused() {
        return focused;
    }

    public void setFocused(boolean focused) {
        this.focused = focused;
    }

    public boolean isHoveredOrFocused() {
        return isHovered || focused;
    }

    public boolean isMouseOver(double mx, double my) {
        return active && visible && mx >= x && my >= y && mx < x + width && my < y + height;
    }

    public final void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        if (!visible) {
            return;
        }
        isHovered = mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
        renderWidget(g, mouseX, mouseY, delta);
    }

    protected abstract void renderWidget(GuiGraphics g, int mouseX, int mouseY, float delta);

    // --- input (screen coordinates); return true when the event was used

    public boolean mouseClicked(double mx, double my, int button) {
        if (!active || !visible || button != 0 || !isMouseOver(mx, my)) {
            return false;
        }
        playDownSound();
        onClick(mx, my);
        return true;
    }

    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0) {
            onRelease(mx, my);
            return true;
        }
        return false;
    }

    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (button == 0 && active && visible) {
            onDrag(mx, my, dx, dy);
            return true;
        }
        return false;
    }

    public boolean mouseScrolled(double mx, double my, double amount) {
        return false;
    }

    /** GLFW key code. */
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    public boolean charTyped(char chr, int modifiers) {
        return false;
    }

    /**
     * Raw 1.12.2 keyboard event, called by {@link Screen} for the focused widget. Default: {@link #keyPressed} with the
     * GLFW code, then {@link #charTyped} for printable characters. Text fields use the raw event (see EditBox).
     */
    public boolean legacyKeyTyped(char chr, int lwjglKey) {
        if (keyPressed(Keys.toGlfw(lwjglKey), 0, 0)) {
            return true;
        }
        return chr >= 32 && chr != 127 && charTyped(chr, 0);
    }

    public void tick() {
    }

    protected void onClick(double mx, double my) {
    }

    protected void onRelease(double mx, double my) {
    }

    protected void onDrag(double mx, double my, double dx, double dy) {
    }

    public void playDownSound() {
        MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.method_12521(net.minecraft.sound.Sounds.UI_BUTTON_CLICK, 1.0f));
    }
}
