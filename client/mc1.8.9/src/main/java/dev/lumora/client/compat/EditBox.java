package dev.lumora.client.compat;

import dev.lumora.client.gui.LumoraUi;
import net.minecraft.client.gui.widget.TextFieldWidget;

import java.util.function.Consumer;

/**
 * Text field with the API of the modern {@code EditBox} (setValue / getValue / setResponder), on top of the 1.8.9
 * {@link TextFieldWidget}. The vanilla field draws text, cursor and selection; the frame is drawn in the Lumora style.
 */
public final class EditBox extends AbstractWidget {
    private final TextFieldWidget field;
    private Consumer<String> responder;

    public EditBox(Font font, int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
        // inner field without the vanilla border, inset like the vanilla bordered layout
        field = new TextFieldWidget(0, font.renderer(), x + 4, y + (height - 8) / 2, width - 8, 8);
        field.setHasBorder(false);
        field.setEditableColor(0xE0E0E0);
    }

    public void setMaxLength(int length) {
        field.setMaxLength(length);
    }

    public void setValue(String text) {
        field.setText(text);
    }

    public String getValue() {
        return field.getText();
    }

    public void setResponder(Consumer<String> responder) {
        this.responder = responder;
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        field.setFocused(focused);
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float delta) {
        LumoraUi.card(g, getX(), getY(), width, height, 0xFF0C0A12, isFocused() ? LumoraUi.ACCENT : LumoraUi.BORDER);
        field.x = getX() + 4;
        field.y = getY() + (height - 8) / 2;
        field.render();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!visible || !isMouseOver(mx, my)) {
            return false;
        }
        field.mouseClicked((int) mx, (int) my, button);
        return true;
    }

    @Override
    public boolean legacyKeyTyped(char chr, int lwjglKey) {
        String before = field.getText();
        boolean used = field.keyPressed(chr, lwjglKey);
        if (!before.equals(field.getText()) && responder != null) {
            responder.accept(field.getText());
        }
        return used;
    }

    @Override
    public void tick() {
        field.tick();
    }
}
