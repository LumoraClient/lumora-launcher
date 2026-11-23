package dev.lumora.client.api;

import dev.lumora.api.render.HudGraphics;
import dev.lumora.core.chat.windows.ChatText;
import dev.lumora.core.extras.GameExtras;
import net.minecraft.client.Minecraft;
import dev.lumora.client.compat.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.ComponentRenderUtils;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.ChatVisiblity;

import java.util.ArrayList;
import java.util.List;

/**
 * Minecraft 1.18.2 for Lumora's chat windows: lines are FormattedCharSequences drawn through Lumora's GuiGraphics over the PoseStack, hover texts
 * and clicks go through Minecraft's own screen code, heads come from the tab list skins.
 */
final class McChatText implements ChatText {
    static final McChatText INSTANCE = new McChatText();

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    private static GuiGraphics graphics(HudGraphics g) {
        return ((McHudGraphics) g).graphics();
    }

    @Override
    public List<Object> wrap(Object message, int width) {
        return new ArrayList<>(ComponentRenderUtils.wrapComponents((Component) message, width, mc().font));
    }

    @Override
    public void draw(HudGraphics g, Object line, int x, int y, int color, boolean shadow) {
        graphics(g).drawString(mc().font, (FormattedCharSequence) line, x, y, color, shadow);
    }

    @Override
    public Object styleAt(Object line, int x) {
        return x < 0 ? null : mc().font.getSplitter().componentStyleAtWidth((FormattedCharSequence) line, x);
    }

    @Override
    public void hover(HudGraphics g, Object style, int mouseX, int mouseY) {
        if (style instanceof Style s && s.getHoverEvent() != null) {
            if (mc().screen != null) {
                ((dev.lumora.client.mixin.ScreenHoverInvoker) mc().screen).lumora$hover(graphics(g).pose(), s, mouseX, mouseY);
            }
        }
    }

    @Override
    public boolean click(Object style, boolean shift) {
        // Minecraft's own handling: click events, with shift the insertion
        return style instanceof Style s && mc().screen instanceof ChatScreen screen && screen.handleComponentClicked(s);
    }

    @Override
    public Object head(String name) {
        if (mc().getConnection() == null || name == null) {
            return null;
        }
        PlayerInfo info = mc().getConnection().getPlayerInfo(name);
        if (info == null) {
            for (PlayerInfo p : mc().getConnection().getOnlinePlayers()) {
                if (name.equalsIgnoreCase(p.getProfile().getName())) {
                    info = p;
                    break;
                }
            }
        }
        return info == null ? null : info.getSkinLocation();
    }

    @Override
    public void drawHead(HudGraphics g, Object head, int x, int y, int size) {
        if (head instanceof net.minecraft.resources.ResourceLocation skin) {
            GuiGraphics gg = graphics(g);
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            gg.blit(skin, x, y, size, size, 8, 8, 8, 8, 64, 64);
            gg.blit(skin, x, y, size, size, 40, 8, 8, 8, 64, 64);
        }
    }

    @Override
    public Options options() {
        var o = mc().options;
        return new Options(o.chatScale, o.chatLineSpacing, o.chatOpacity, o.textBackgroundOpacity,
                ChatComponent.getWidth(o.chatWidth), ChatComponent.getHeight(o.chatHeightFocused),
                ChatComponent.getHeight(o.chatHeightUnfocused), o.chatVisibility == ChatVisiblity.HIDDEN);
    }

    @Override
    public Parts parts(Object message) {
        GameExtras.ChatMessage m = McExtras.chatMessage((Component) message, 0, 0);
        return new Parts(m.text(), m.insertions(), m.commands(), m.links());
    }
}
