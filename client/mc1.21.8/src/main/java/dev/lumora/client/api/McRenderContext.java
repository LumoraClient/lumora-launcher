package dev.lumora.client.api;

import dev.lumora.api.game.GameContext;
import dev.lumora.api.hud.RenderContext;
import dev.lumora.api.i18n.Translations;

/** Reused per frame; {@link #set} fills it before each render pass. */
public final class McRenderContext implements RenderContext {
    private final GameContext game;
    private final Translations translations;
    private boolean preview;
    private boolean editing;
    private float partialTick;
    private int width;
    private int height;
    private int guiScale;
    private long time;

    McRenderContext(GameContext game, Translations translations) {
        this.game = game;
        this.translations = translations;
    }

    public McRenderContext set(boolean preview, boolean editing, float partialTick, int width, int height, int guiScale) {
        this.preview = preview;
        this.editing = editing;
        this.partialTick = partialTick;
        this.width = width;
        this.height = height;
        this.guiScale = guiScale;
        this.time = System.currentTimeMillis();
        return this;
    }

    @Override
    public boolean preview() {
        return preview;
    }

    @Override
    public boolean editing() {
        return editing;
    }

    @Override
    public float partialTick() {
        return partialTick;
    }

    @Override
    public int screenWidth() {
        return width;
    }

    @Override
    public int screenHeight() {
        return height;
    }

    @Override
    public int guiScale() {
        return guiScale;
    }

    @Override
    public long timeMillis() {
        return time;
    }

    @Override
    public GameContext game() {
        return game;
    }

    @Override
    public Translations translations() {
        return translations;
    }
}
