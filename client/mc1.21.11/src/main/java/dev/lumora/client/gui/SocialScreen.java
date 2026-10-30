package dev.lumora.client.gui;

import dev.lumora.client.api.LumoraRuntime;
import dev.lumora.core.social.SocialView;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Lumora Social (friends, groups, chat) in the game. The screen only adapts Minecraft 1.21.11's rendering and
 * input; layout, drawing and logic are the version-independent {@link SocialView} of lumora-core.
 */
public final class SocialScreen extends Screen {
    private final Screen parent;
    private final SocialView view;

    public SocialScreen(Screen parent) {
        super(Component.literal("Lumora Social"));
        this.parent = parent;
        this.view = new SocialView(LumoraRuntime.core().social(), new SocialView.Host() {
            @Override
            public void close() {
                onClose();
            }

            @Override
            public String clipboard() {
                String s = minecraft.keyboardHandler.getClipboard();
                return s == null ? "" : s;
            }
        });
    }

    /** "Freunde" with the number of unread messages and requests, for the menu buttons. */
    public static Component label() {
        var core = LumoraRuntime.core();
        int n = core == null || core.social() == null ? 0 : core.social().badge();
        var c = Component.translatable("lumora.menu.social");
        return n > 0 ? c.append(" (" + n + ")") : c;
    }

    /** For the smoke test and notifications: open on a conversation. */
    public SocialView view() {
        return view;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        if (minecraft.level == null) {
            LumoraUi.background(g, width, height, LumoraUi.BACKGROUND_MENU);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        view.render(LumoraRuntime.graphics(g), width, height, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        return view.mouseClicked(e.x(), e.y(), e.button()) || super.mouseClicked(e, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        return view.mouseScrolled(mx, my, sy);
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        return view.keyPressed(e.key(), e.hasControlDown()) || super.keyPressed(e);
    }

    @Override
    public boolean charTyped(CharacterEvent e) {
        return view.charTyped((char) e.codepoint()) || super.charTyped(e);
    }


    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void removed() {
        view.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
