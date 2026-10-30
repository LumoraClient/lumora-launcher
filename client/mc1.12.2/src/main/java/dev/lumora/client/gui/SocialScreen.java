package dev.lumora.client.gui;

import dev.lumora.client.api.LumoraRuntime;
import dev.lumora.core.social.SocialView;
import dev.lumora.client.compat.Component;
import dev.lumora.client.compat.GuiGraphics;
import dev.lumora.client.compat.Screen;

/**
 * Lumora Social (friends, groups, chat) in the game. The screen only adapts Minecraft 1.12.2's rendering and
 * input; layout, drawing and logic are the version-independent {@link SocialView} of lumora-core.
 */
public final class SocialScreen extends Screen {
    private final net.minecraft.client.gui.screen.Screen parent;
    private final SocialView view;

    public SocialScreen(net.minecraft.client.gui.screen.Screen parent) {
        super(Component.literal("Lumora Social"));
        this.parent = parent;
        this.view = new SocialView(LumoraRuntime.core().social(), new SocialView.Host() {
            @Override
            public void close() {
                onClose();
            }

            @Override
            public String clipboard() {
                String s = net.minecraft.client.gui.screen.Screen.getClipboard();
                return s == null ? "" : s;
            }
        });
    }

    /** "Freunde" with the number of unread messages and requests, for the menu buttons. */
    public static Component label() {
        var core = LumoraRuntime.core();
        int n = core == null || core.social() == null ? 0 : core.social().badge();
        Component c = Component.translatable("lumora.menu.social");
        return n > 0 ? Component.literal(c.getString() + " (" + n + ")") : c;
    }

    /** For the smoke test and notifications: open on a conversation. */
    public SocialView view() {
        return view;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        if (minecraft.world == null) {
            LumoraUi.background(g, width, height, LumoraUi.BACKGROUND_MENU);
        }
        super.render(g, mouseX, mouseY, delta);
        view.render(LumoraRuntime.graphics(g), width, height, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        return view.mouseClicked(mx, my, button) || super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sy) {
        return view.mouseScrolled(mx, my, sy);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // GLFW modifier bit 2 = control
        return view.keyPressed(keyCode, (modifiers & 2) != 0) || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return view.charTyped(chr) || super.charTyped(chr, modifiers);
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
