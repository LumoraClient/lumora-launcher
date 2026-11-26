package dev.lumora.voice.ui;

import dev.lumora.api.render.HudGraphics;
import java.util.List;
import java.util.Objects;

/**
 * A small dialog with a paged list (audio devices, voice channels, block list): entries, page arrows next to the
 * title, a message line and a button row ("Fertig" on the left, the dialog's own buttons on the right). As many rows
 * as fit into the GUI height make one page (two to six), so the buttons stay on screen at every GUI scale. The list is
 * rebuilt whenever {@link #state()} changes, e.g. when the voice service announces new channels.
 */
abstract class ListDialog extends Dialog {
    static final int PAGE = 6, MIN_PAGE = 2, ROW = 26;
    /** Title, message line and button row around the rows. */
    private static final int FRAME = 112;
    private int page, startIndex = -1;
    private Object shownState;
    private boolean laidOut;
    private final String closeLabel;

    ListDialog(ViewHost host, String title) { this(host, title, "Fertig"); }

    ListDialog(ViewHost host, String title, String closeLabel) {
        super(host, title, FRAME + PAGE * ROW);
        this.closeLabel = closeLabel;
    }

    /** Entries for the current state, top to bottom; the dialog sets their bounds and list style. */
    protected abstract List<Control> entries();

    /** Shown instead of the list when there are no entries. */
    protected abstract String emptyText();

    /** Buttons of the bottom row, right of the close button (the first one is drawn rightmost). */
    protected abstract List<Control> buttons();

    /** Changes when the entries have to be rebuilt; {@code null} = only on resize and {@link #refresh()}. */
    protected Object state() { return null; }

    /** Opens on the page that shows entry {@code index} (e.g. the selected device). */
    protected final void startAt(int index) { startIndex = index; }

    /** Rows per page for the current GUI height. */
    int pageSize() {
        if (height <= 0) return PAGE;
        return Math.clamp((height - 24 - FRAME) / ROW, MIN_PAGE, PAGE);
    }

    @Override
    protected int dialogHeight() { return FRAME + pageSize() * ROW; }

    int page() { return page; }

    int pages() { return pages(entries().size()); }

    private int pages(int count) { return Math.max(1, (count + pageSize() - 1) / pageSize()); }

    /** Rebuilds the list (after an entry changed something). */
    protected final void refresh() {
        controls.clear();
        layout();
    }

    @Override
    protected final void layout() {
        laidOut = true;
        shownState = state();
        List<Control> items = entries();
        int size = pageSize(), pages = pages(items.size());
        if (startIndex >= 0) { page = startIndex / size; startIndex = -1; }
        page = Math.clamp(page, 0, pages - 1);
        int top = y + 44;
        for (int i = page * size; i < Math.min(items.size(), (page + 1) * size); i++) {
            controls.add(items.get(i).listItem().bounds(x + 18, top, w - 36, ROW - 4));
            top += ROW;
        }
        if (pages > 1) {
            controls.add(new Control("›", "Nächste Seite", () -> "", () -> turn(1)).compact().bounds(x + w - 40, y + 12, 22, 18));
            controls.add(new Control("‹", "Vorherige Seite", () -> "", () -> turn(-1)).compact().bounds(x + w - 66, y + 12, 22, 18));
        }
        int bottom = y + dialogHeight() - 34, inner = w - 36;
        controls.add(new Control(closeLabel, "Fenster schließen", () -> "", host::back).compact().bounds(x + 18, bottom, 84, 24));
        List<Control> own = buttons();
        if (own.isEmpty()) return;
        int buttonWidth = Math.min(118, (inner - 84 - 8 * own.size()) / own.size());
        int right = x + w - 18;
        for (Control button : own) {
            right -= buttonWidth;
            controls.add(button.bounds(right, bottom, buttonWidth, 24));
            right -= 8;
        }
    }

    private void turn(int direction) {
        page = Math.floorMod(page + direction, pages());
        refresh();
    }

    @Override
    public void render(HudGraphics g, int width, int height, int mouseX, int mouseY) {
        if (laidOut && !Objects.equals(state(), shownState)) refresh();
        super.render(g, width, height, mouseX, mouseY);
    }

    @Override
    protected void submit() { }

    @Override
    protected void drawContent(HudGraphics g) {
        if (entries().isEmpty()) Ui.label(g, Ui.fit(g, emptyText(), w - 36), x + 18, y + 50, Ui.muted());
        int pages = pages();
        if (pages > 1) Ui.right(g, (page + 1) + " / " + pages, x + w - 72, y + 17, Ui.muted());
        drawMessage(g, y + dialogHeight() - 52);
    }
}
