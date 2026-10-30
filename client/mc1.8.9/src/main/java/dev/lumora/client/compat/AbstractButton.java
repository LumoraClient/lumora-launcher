package dev.lumora.client.compat;

/** Clickable widget (modern {@code AbstractButton}): a click or Enter / Space while focused calls {@link #onPress}. */
public abstract class AbstractButton extends AbstractWidget {
    private static final int ENTER = 257;
    private static final int SPACE = 32;
    private static final int KP_ENTER = 335;

    protected AbstractButton(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
    }

    public abstract void onPress();

    @Override
    protected void onClick(double mx, double my) {
        onPress();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (active && visible && (keyCode == ENTER || keyCode == SPACE || keyCode == KP_ENTER)) {
            playDownSound();
            onPress();
            return true;
        }
        return false;
    }
}
