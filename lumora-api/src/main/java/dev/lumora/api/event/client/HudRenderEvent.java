package dev.lumora.api.event.client;

import dev.lumora.api.event.LumoraEvent;
import dev.lumora.api.hud.RenderContext;
import dev.lumora.api.render.HudGraphics;

/**
 * Posted every frame after the widgets were drawn, for free-form overlays. The same instance is reused for every
 * frame: do not keep a reference. Only posted when someone listens.
 */
public final class HudRenderEvent implements LumoraEvent {
    private HudGraphics graphics;
    private RenderContext context;

    /** Internal: set by the renderer before posting. */
    public HudRenderEvent set(HudGraphics graphics, RenderContext context) {
        this.graphics = graphics;
        this.context = context;
        return this;
    }

    public HudGraphics graphics() {
        return graphics;
    }

    public RenderContext context() {
        return context;
    }
}
