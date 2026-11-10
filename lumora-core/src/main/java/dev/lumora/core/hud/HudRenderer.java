package dev.lumora.core.hud;

import dev.lumora.api.event.client.HudRenderEvent;
import dev.lumora.api.event.hud.WidgetRenderEvent;
import dev.lumora.api.hud.RenderContext;
import dev.lumora.api.render.HudGraphics;
import dev.lumora.core.event.SimpleEventBus;

import java.util.List;

/**
 * Draws the widgets of the active profile. Per frame and widget: one size query, one transform, one render call -
 * no lookups, no allocations (the reusable events are only posted when someone listens). A widget that throws is
 * switched off instead of breaking the HUD.
 */
public final class HudRenderer {
    private final WidgetRegistryImpl registry;
    private final SimpleEventBus events;
    private final WidgetRenderEvent widgetEvent = new WidgetRenderEvent();
    private final HudRenderEvent hudEvent = new HudRenderEvent();

    public HudRenderer(WidgetRegistryImpl registry, SimpleEventBus events) {
        this.registry = registry;
        this.events = events;
    }

    /** In-game HUD pass (client thread). */
    public void render(HudGraphics g, RenderContext ctx) {
        List<WidgetEntry> list = registry.entries();
        boolean listeners = events.hasListeners(WidgetRenderEvent.class);
        for (int i = 0; i < list.size(); i++) {
            WidgetEntry e = list.get(i);
            e.bounds.valid = false;
            if (!e.active() || !visible(e, ctx)) {
                continue;
            }
            if (listeners && events.post(widgetEvent.set(e)).isCancelled()) {
                continue;
            }
            draw(g, ctx, e);
        }
        if (events.hasListeners(HudRenderEvent.class)) {
            events.post(hudEvent.set(g, ctx));
        }
    }

    boolean visible(WidgetEntry e, RenderContext ctx) {
        try {
            return e.widget.isVisible(ctx);
        } catch (RuntimeException | LinkageError ex) {
            registry.fail(e, "isVisible", ex);
            return false;
        }
    }

    /** Computes the screen rectangle of a widget for the current content; {@code false} if the widget failed. */
    public boolean layout(WidgetEntry e, RenderContext ctx) {
        int w;
        int h;
        try {
            w = Math.max(1, e.widget.width(ctx));
            h = Math.max(1, e.widget.height(ctx));
        } catch (RuntimeException | LinkageError ex) {
            registry.fail(e, "size", ex);
            e.bounds.valid = false;
            return false;
        }
        place(e.bounds, e.state, w, h, ctx.screenWidth(), ctx.screenHeight());
        return true;
    }

    /** Position to pixels: at its anchor and offsets, or (no anchor) x = fraction * (screen - scaled width). */
    public static void place(Bounds b, WidgetState s, int rawW, int rawH, int screenW, int screenH) {
        b.rawWidth = rawW;
        b.rawHeight = rawH;
        b.width = Math.max(1, Math.round(rawW * s.scale));
        b.height = Math.max(1, Math.round(rawH * s.scale));
        if (s.anchor != null) {
            int[] p = Anchor.place(s.anchor, s.offsetX, s.offsetY, b.width, b.height, screenW, screenH);
            b.x = p[0];
            b.y = p[1];
        } else {
            b.x = Math.round(s.x * Math.max(0, screenW - b.width));
            b.y = Math.round(s.y * Math.max(0, screenH - b.height));
        }
        b.valid = true;
    }

    /** Lays out and draws one widget at its place (also used by the editor). */
    public void draw(HudGraphics g, RenderContext ctx, WidgetEntry e) {
        if (!layout(e, ctx)) {
            return;
        }
        float s = e.state.scale;
        g.push();
        try {
            g.translate(e.bounds.x, e.bounds.y);
            g.scale(s, s);
            e.widget.render(g, ctx);
        } catch (RuntimeException | LinkageError ex) {
            registry.fail(e, "render", ex);
        } finally {
            g.pop();
        }
    }

    /** Click while the chat is open: the topmost widget under the cursor that was drawn this frame gets it. */
    public boolean click(double mx, double my, int button) {
        List<WidgetEntry> list = registry.entries();
        for (int i = list.size() - 1; i >= 0; i--) {
            WidgetEntry e = list.get(i);
            if (e.active() && e.bounds.contains(mx, my)) {
                float s = e.state.scale;
                try {
                    if (e.widget.mouseClicked((mx - e.bounds.x) / s, (my - e.bounds.y) / s, button)) {
                        return true;
                    }
                } catch (RuntimeException | LinkageError ex) {
                    registry.fail(e, "mouseClicked", ex);
                }
            }
        }
        return false;
    }
}
