package dev.lumora.voice.ui;

import dev.lumora.api.input.Keys;
import dev.lumora.api.render.HudGraphics;
import java.util.ArrayList;
import java.util.List;

/** Base of the small centered dialogs (block a player, create or join a channel). */
abstract class Dialog implements View {
    protected final ViewHost host;
    protected final List<Control> controls = new ArrayList<>();
    protected final List<TextField> fields = new ArrayList<>();
    protected int x, y, w, width, height;
    protected String error = "", info = "";
    private final int dialogHeight;
    private final String title;
    private Control focus, dragging;

    Dialog(ViewHost host, String title, int dialogHeight) {
        this.host = host; this.title = title; this.dialogHeight = dialogHeight;
    }

    /** Height of the dialog card; {@link #height} (GUI height) is already set when it is asked. */
    protected int dialogHeight() { return dialogHeight; }

    /** Title next to the accent bar (a dialog that switches its subject overrides it). */
    protected String title() { return title; }

    /** Creates fields and buttons for the current size ({@link #x}, {@link #y}, {@link #w} are set). */
    protected abstract void layout();

    protected abstract void submit();

    /** Dialog content between title and buttons. */
    protected abstract void drawContent(HudGraphics g);

    private void resize(int newWidth, int newHeight) {
        width = newWidth; height = newHeight;
        w = Math.min(380, width - 32); x = (width - w) / 2; y = Math.max(12, (height - dialogHeight()) / 2);
        var values = fields.stream().map(TextField::value).toList();
        controls.clear();
        int focused = -1;
        for (int i = 0; i < fields.size(); i++) if (fields.get(i).focused()) focused = i;
        fields.clear();
        layout();
        for (int i = 0; i < values.size() && i < fields.size(); i++) fields.get(i).setValue(values.get(i));
        if (!fields.isEmpty()) fields.get(Math.max(0, focused)).focus(true);
    }

    @Override
    public void render(HudGraphics g, int newWidth, int newHeight, int mouseX, int mouseY) {
        if (newWidth != width || newHeight != height) resize(newWidth, newHeight);
        Ui.backdrop(g, width, height, host.inWorld());
        Ui.glow(g, x, y, w, dialogHeight(), 10, 0xFF000000, 8);
        g.card(x, y, w, dialogHeight(), 10, Theme.color("dialog"), Ui.border());
        g.roundedRect(x + 18, y + 14, 3, 14, 1, Ui.accent());
        Ui.bigText(g, Ui.fit(g, title(), (int) ((w - 44) / 1.25f)), x + 26, y + 15, 1.25f, Ui.text());
        drawContent(g);
        for (Control control : controls) control.render(g, mouseX, mouseY);
    }

    /** Input card with the field inside. */
    protected void drawField(HudGraphics g, TextField field, int cardX, int cardY, int cardW, String placeholder) {
        g.card(cardX, cardY, cardW, 20, 5, Theme.color("input"), field.focused() ? Ui.accent() : Ui.border());
        if (field.value().isEmpty() && !field.focused()) Ui.label(g, placeholder, field.x, field.y, Ui.muted());
        else field.render(g, Ui.text(), System.currentTimeMillis());
    }

    protected void drawMessage(HudGraphics g, int messageY) {
        if (!error.isEmpty()) Ui.label(g, Ui.fit(g, error, w - 36), x + 18, messageY, Theme.color("error"));
        else if (!info.isEmpty()) Ui.label(g, Ui.fit(g, info, w - 36), x + 18, messageY, Ui.accent());
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return false;
        boolean hit = false;
        for (TextField field : fields) { boolean inside = field.contains(mx, my); field.focus(inside); hit |= inside; }
        if (hit) return true;
        for (Control control : List.copyOf(controls)) {
            if (control.contains(mx, my) && control.active) { control.click(mx); if (control.isSlider()) dragging = control; return true; }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button) {
        if (dragging == null) return false;
        dragging.drag(mx);
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        var slider = dragging;
        dragging = null;
        return slider != null && slider.release();
    }

    @Override
    public boolean keyPressed(int key, boolean ctrl, boolean shift) {
        if (key == Keys.ENTER) { submit(); return true; }
        if (key == Keys.TAB) { cycleFocus(shift ? -1 : 1); return true; }
        for (TextField field : fields) if (field.focused()) return field.keyPressed(key, ctrl);
        return focus != null && focus.keyPressed(key);
    }

    @Override
    public boolean charTyped(char ch) {
        for (TextField field : fields) if (field.focused()) return field.charTyped(ch);
        return false;
    }

    /** Tab order: text fields, then buttons. */
    private void cycleFocus(int direction) {
        var order = new ArrayList<Object>(fields);
        for (Control control : controls) if (control.focusable()) order.add(control);
        if (order.isEmpty()) return;
        int index = -1;
        for (int i = 0; i < order.size(); i++) {
            Object item = order.get(i);
            if (item instanceof TextField field && field.focused() || item == focus) index = i;
        }
        Object next = order.get(Math.floorMod(index + direction, order.size()));
        fields.forEach(field -> field.focus(field == next));
        if (focus != null) focus.focused = false;
        focus = next instanceof Control control ? control : null;
        if (focus != null) focus.focused = true;
    }
}
