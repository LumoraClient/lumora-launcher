package dev.lumora.client.compat;

import dev.lumora.client.gui.LumoraUi;

/**
 * Slider with the API of the modern {@code AbstractSliderButton}: {@link #value} in 0..1, {@link #applyValue()} when it
 * changes, {@link #updateMessage()} for the label. Drawn in the Lumora style (dark track, accent handle).
 */
public abstract class AbstractSliderButton extends AbstractWidget {
    private static final int HANDLE = 8;
    private static final int LEFT = 263;
    private static final int RIGHT = 262;
    protected double value;

    protected AbstractSliderButton(int x, int y, int width, int height, Component message, double value) {
        super(x, y, width, height, message);
        this.value = clamp(value);
    }

    protected abstract void updateMessage();

    protected abstract void applyValue();

    private static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }

    private void setValue(double v) {
        double old = value;
        value = clamp(v);
        if (old != value) {
            applyValue();
        }
        updateMessage();
    }

    private void setValueFromMouse(double mx) {
        setValue((mx - (getX() + HANDLE / 2.0)) / (width - HANDLE));
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float delta) {
        boolean hover = active && isHoveredOrFocused();
        LumoraUi.card(g, getX(), getY(), width, height, hover ? 0xE81D2431 : 0xDC10141A, LumoraUi.BORDER);
        int hx = getX() + (int) (value * (width - HANDLE));
        LumoraUi.card(g, hx, getY(), HANDLE, height, hover ? dev.lumora.client.gui.LumoraUi.ACCENT_HOVER : dev.lumora.client.gui.LumoraUi.ACCENT, LumoraUi.ACCENT);
        Font font = Font.get();
        g.drawCenteredString(font, getMessage(), getX() + width / 2, getY() + (height - 8) / 2, active ? LumoraUi.TEXT : LumoraUi.MUTED);
    }

    @Override
    protected void onClick(double mx, double my) {
        setValueFromMouse(mx);
    }

    @Override
    protected void onDrag(double mx, double my, double dx, double dy) {
        setValueFromMouse(mx);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == LEFT || keyCode == RIGHT) {
            setValue(value + (keyCode == LEFT ? -1.0 : 1.0) / (width - HANDLE));
            return true;
        }
        return false;
    }
}
