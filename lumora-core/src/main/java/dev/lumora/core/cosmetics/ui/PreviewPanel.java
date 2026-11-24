package dev.lumora.core.cosmetics.ui;

import dev.lumora.api.render.HudGraphics;

/**
 * The live character preview: the own player with the preview selection, turned by dragging (360°), zoomed with the
 * mouse wheel. Only drawn on this client.
 */
final class PreviewPanel {
    static final float MIN_ZOOM = 0.6f;
    static final float MAX_ZOOM = 2.6f;
    float yaw;
    float pitch;
    float zoom = 1;
    private boolean dragging;
    private double lastX;
    private double lastY;
    private int x;
    private int y;
    private int w;
    private int h;

    void reset(float defaultYaw) {
        yaw = defaultYaw;
        pitch = 0;
        zoom = 1;
    }

    /** Turns to show a cosmetic from its best side (e.g. wings from behind). */
    void face(float defaultYaw) {
        yaw = defaultYaw;
    }

    void draw(HudGraphics g, UiHost host, int x, int y, int w, int h, int mx, int my) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        g.card(x, y, w, h, 6, 0xFF0E1219, Ui.BORDER);
        // floor shadow
        g.fill(x + w / 2 - w / 4, y + h - 22, x + w / 2 + w / 4, y + h - 20, 0x40000000);
        boolean drawn = host.drawPreview(g, x + 2, y + 2, w - 4, h - 16, yaw, pitch, zoom);
        if (!drawn) {
            int ty = y + h / 2 - 12;
            for (String line : Ui.wrap(g, host.tr("lumora.cosmetics.preview.no_world"), w - 16, 4)) {
                g.centeredText(line, x + w / 2, ty, Ui.MUTED, false);
                ty += g.lineHeight() + 1;
            }
        }
        g.centeredText(Ui.fit(g, host.tr("lumora.cosmetics.preview.hint"), w - 8), x + w / 2, y + h - 12,
                Ui.hover(mx, my, x, y, w, h) ? Ui.MUTED : Ui.DIM, false);
    }

    boolean press(double mx, double my, int button) {
        if (button == 0 && mx >= x && mx < x + w && my >= y && my < y + h) {
            dragging = true;
            lastX = mx;
            lastY = my;
            return true;
        }
        return false;
    }

    boolean drag(double mx, double my) {
        if (!dragging) {
            return false;
        }
        yaw += (float) (mx - lastX) * 2f;
        pitch = Math.max(-35, Math.min(35, pitch + (float) (my - lastY)));
        yaw = ((yaw % 360) + 360) % 360;
        lastX = mx;
        lastY = my;
        return true;
    }

    boolean release() {
        boolean was = dragging;
        dragging = false;
        return was;
    }

    boolean scroll(double mx, double my, double delta) {
        if (mx >= x && mx < x + w && my >= y && my < y + h) {
            zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom + (float) delta * 0.15f));
            return true;
        }
        return false;
    }
}
