package dev.lumora.voice.ui;

import dev.lumora.api.input.Keys;
import dev.lumora.api.render.HudGraphics;
import java.util.function.*;

/**
 * A setting row (toggle, slider, action, info) or a small button, keyboard accessible: Tab moves the focus, Enter or
 * Space activates, Left/Right moves a slider. Sliders only react inside their actual track.
 */
public final class Control {
    private enum Style { ROW, NAV, PRIMARY, SECONDARY, LIST, MEMBER }
    static final long TOOLTIP_DELAY_MILLIS = 2000;
    /** Head size of a member line. */
    static final int MEMBER_HEAD = 16;
    private java.util.UUID head;

    private Style style = Style.ROW;
    private final String label, description;
    private final Supplier<String> value;
    private final Runnable action;
    private Runnable save = () -> {};
    private BooleanSupplier selected;
    private DoubleSupplier number;
    private DoubleConsumer setNumber;
    private double min, max, step;
    private boolean dragging, compact;
    int x, y, width = 100, height = 48;
    boolean visible = true, active = true, focused;

    public Control(String label, String description, Supplier<String> value, Runnable action) {
        this.label = label; this.description = description; this.value = value; this.action = action;
    }

    public Control toggle(BooleanSupplier value) { selected = value; return this; }

    public Control slider(DoubleSupplier get, DoubleConsumer set, double min, double max, double step, Runnable save) {
        number = get; setNumber = set; this.min = min; this.max = max; this.step = step; this.save = save; return this;
    }

    /** Small button; default is the dark SECONDARY style of the launcher. */
    public Control compact() { compact = true; style = Style.SECONDARY; height = 28; return this; }

    /** Sidebar entry: highlight and accent bar while selected. */
    public Control nav() { compact(); style = Style.NAV; return this; }

    /** Main action in the accent color (like "Spielen" in the launcher). */
    public Control primary() { compact(); style = Style.PRIMARY; return this; }

    /** Entry of a list in a small dialog: label on the left, value (what a click does) on the right. */
    public Control listItem() { if (style != Style.MEMBER) { compact(); style = Style.LIST; } return this; }

    /**
     * Member line under a channel or group: indented, with the player's head ({@code id}, {@code null} for none) and
     * name, in the accent color while {@code talking}; only shows, a click does nothing.
     */
    public static Control member(java.util.UUID id, String name, BooleanSupplier talking, Supplier<String> value) {
        var control = new Control(name, "", value, () -> {});
        control.compact(); control.style = Style.MEMBER; control.selected = talking; control.active = false; control.head = id;
        return control;
    }

    /** Whether this is a member line ({@link #member}). */
    public boolean isMember() { return style == Style.MEMBER; }

    public Control bounds(int x, int y, int width, int height) { this.x = x; this.y = y; this.width = width; this.height = height; return this; }

    public Control inactive() { active = false; return this; }

    public String label() { return label; }
    public String description() { return description; }
    public String value() { return value.get(); }
    public boolean isSlider() { return number != null; }
    public boolean isToggle() { return selected != null; }
    public int x() { return x; }
    public int y() { return y; }
    public int right() { return x + width; }
    public int bottom() { return y + height; }
    public boolean visible() { return visible; }
    String searchText() { return label + " " + description; }

    boolean contains(double mx, double my) { return visible && mx >= x && my >= y && mx < x + width && my < y + height; }
    boolean focusable() { return visible && active; }

    private int controlWidth() { return Math.min(154, Math.max(68, width / 3)); }
    private int controlX() { return right() - controlWidth() - 12; }

    void render(HudGraphics g, int mouseX, int mouseY) {
        if (!visible) return;
        boolean hover = (contains(mouseX, mouseY) || focused) && active, on = selected != null && selected.getAsBoolean();
        if (compact) {
            String text = Ui.fit(g, label, width - 20);
            int ty = y + (height - 9) / 2;
            switch (style) {
                case NAV -> {
                    if (on || hover) g.roundedRect(x, y, width, height, 6, on ? Theme.color("panel_selected") : Ui.alpha(Theme.color("panel_selected"), 0x8C));
                    if (on) g.roundedRect(x, y + 6, 3, height - 12, 1, Ui.accent());
                    if (focused && !on) g.roundedRect(x, y + 6, 2, height - 12, 1, Ui.alpha(Ui.accent(), 0x80));
                    Ui.label(g, text, x + 12, ty, on || hover ? Ui.text() : Ui.muted());
                }
                case PRIMARY -> {
                    if (hover) Ui.glow(g, x, y, width, height, 6, Ui.accent(), 4);
                    g.roundedRect(x, y + 2, width, height - 2, 6, Ui.alpha(Theme.color("accent_dark"), active ? 0xFF : 0x60));
                    g.roundedRect(x, y, width, height - 2, 6, !active ? Theme.color("panel_selected") : hover ? Theme.color("accent_hover") : Ui.accent());
                    Ui.centered(g, text, x + width / 2, ty - 1, active ? Theme.color("accent_text") : Ui.muted());
                }
                case MEMBER -> {
                    int left = x + 16, top = y + (height - MEMBER_HEAD) / 2;
                    if (head != null) {
                        // light frame while the player talks, like the group screen of Simple Voice Chat
                        if (on) g.fill(left - 1, top - 1, left + MEMBER_HEAD + 1, top + MEMBER_HEAD + 1, 0xFFFFFFFF);
                        dev.lumora.voice.render.SkinFaces.draw(g, head, label, left, top, MEMBER_HEAD);
                        left += MEMBER_HEAD + 6;
                    }
                    String current = Ui.fit(g, value.get(), width / 3);
                    int valueWidth = current.isEmpty() ? 0 : g.textWidth(current) + 12;
                    Ui.label(g, Ui.fit(g, label, right() - left - valueWidth - 10), left, ty, on ? Ui.accent() : head == null ? Ui.muted() : Ui.text());
                    if (!current.isEmpty()) Ui.right(g, current, right() - 10, ty, Ui.muted());
                }
                case LIST -> {
                    g.card(x, y, width, height, 6, hover ? Theme.color("panel_hover") : Theme.color("panel"),
                            focused ? Theme.color("focus_outline") : hover ? Ui.accent() : Ui.border());
                    String current = Ui.fit(g, value.get(), width / 2 - 12);
                    int valueWidth = current.isEmpty() ? 0 : g.textWidth(current) + 12;
                    if (on) g.roundedRect(x, y + 5, 3, height - 10, 1, Ui.accent());
                    Ui.label(g, Ui.fit(g, label, width - valueWidth - 22), x + 10, ty, active ? Ui.text() : Ui.muted());
                    if (!current.isEmpty()) Ui.right(g, current, right() - 10, ty, active ? Ui.accent() : Ui.muted());
                }
                default -> {
                    g.card(x, y, width, height, 6, hover ? Theme.color("panel_hover") : Theme.color("panel"), hover ? Ui.accent() : Ui.border());
                    Ui.centered(g, text, x + width / 2, ty, on ? Ui.accent() : Ui.text());
                }
            }
            return;
        }
        g.card(x, y, width, height, 8, hover ? Theme.color("panel_hover") : Theme.color("panel"),
                focused ? Theme.color("focus_outline") : hover ? Theme.color("border_hover") : Ui.border());
        int labelWidth = width - controlWidth() - 34;
        Ui.label(g, Ui.fit(g, label, labelWidth), x + 12, y + 10, Ui.text());
        Ui.label(g, Ui.fit(g, description, labelWidth), x + 12, y + 28, Ui.muted());
        String current = Ui.fit(g, value.get(), controlWidth());
        if (number != null) {
            Ui.right(g, current, right() - 12, y + 9, Ui.accent());
            int sx = controlX(), length = controlWidth();
            g.roundedRect(sx, y + 31, length, 4, 2, Theme.color("slider_track"));
            int filled = (int) Math.round(Math.clamp((number.getAsDouble() - min) / (max - min), 0, 1) * length);
            g.roundedRect(sx, y + 31, filled, 4, 2, Ui.accent());
            int knob = sx + Math.clamp(filled - 5, -5, length - 5);
            if (hover || dragging) g.roundedRect(knob - 3, y + 25, 16, 16, 8, Ui.alpha(Ui.accent(), 0x48));
            g.roundedRect(knob, y + 28, 10, 10, 5, Theme.color("slider_knob"));
        } else if (selected != null) {
            int sx = right() - 46;
            g.roundedRect(sx, y + 15, 32, 18, 9, on ? Theme.color("toggle_on") : Theme.color("toggle_off"));
            g.roundedRect(sx + (on ? 17 : 3), y + 18, 12, 12, 6, on ? Theme.color("toggle_knob_on") : Theme.color("toggle_knob_off"));
        } else Ui.right(g, current, right() - 12, y + 20, active ? Ui.accent() : Ui.text());
    }

    /** Tooltip with the description after hovering a compact control for two seconds (rows show it inline). */
    void tooltip(HudGraphics g, int mouseX, int mouseY, int screenWidth, int screenHeight) {
        if (!compact || description.isEmpty()) return;
        int w = Math.min(g.textWidth(description) + 12, screenWidth - 8), h = 17;
        int tx = Math.min(mouseX + 10, screenWidth - w - 4), ty = Math.min(mouseY + 12, screenHeight - h - 4);
        g.card(tx, ty, w, h, 4, Theme.color("tooltip"), Ui.border());
        Ui.label(g, Ui.fit(g, description, w - 12), tx + 6, ty + 4, Ui.text());
    }

    private void slide(double mouseX) {
        double fraction = Math.clamp((mouseX - controlX()) / controlWidth(), 0, 1);
        setNumber.accept(Math.clamp(min + Math.round(fraction * (max - min) / step) * step, min, max));
    }

    /** Mouse press inside this control (already checked by the view). */
    void click(double mouseX) {
        if (number == null) action.run();
        else if (mouseX >= controlX() - 5) { dragging = true; slide(mouseX); }
    }

    void drag(double mouseX) { if (dragging) slide(mouseX); }

    boolean release() {
        if (!dragging) return false;
        dragging = false; save.run(); return true;
    }

    boolean keyPressed(int key) {
        if (number != null && (key == Keys.LEFT || key == Keys.RIGHT)) {
            setNumber.accept(Math.clamp(number.getAsDouble() + (key == Keys.LEFT ? -step : step), min, max)); save.run(); return true;
        }
        if (key == Keys.ENTER || key == Keys.SPACE) { if (number == null) action.run(); return true; }
        return false;
    }
}
