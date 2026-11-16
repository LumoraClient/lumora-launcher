package dev.lumora.core.perf;

/**
 * The optimizations of Lumora's performance engine. Each one can be switched on and off on its own (all on by default);
 * a Minecraft version adapter declares which ones it implements ({@code Platform.optimizations()}).
 */
public enum Optimization {
    /** Entities and block entities completely hidden behind opaque blocks are not drawn. */
    ENTITY_CULLING("entity_culling"),
    /** Entities, name tags and Lumora's texts above players without repeated per-frame work. */
    RENDERING("rendering"),
    /** HUD and Lumora menus only recompute their content when it changed. */
    SMART_HUD("smart_hud"),
    /** Values that stay the same from one frame to the next are not computed again every frame. */
    FRAME("frame"),
    /** The cloud geometry is not rebuilt when only its colour changed. */
    CLOUDS("clouds"),
    /** Less work while the client starts. */
    STARTUP("startup");

    private final String key;

    Optimization(String key) {
        this.key = key;
    }

    /** Setting key and suffix of the translation keys ({@code lumora.performance.<key>}). */
    public String key() {
        return key;
    }
}
