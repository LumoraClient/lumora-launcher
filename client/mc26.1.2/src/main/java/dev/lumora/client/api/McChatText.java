package dev.lumora.client.api;

import dev.lumora.api.render.HudGraphics;
import dev.lumora.core.chat.windows.ChatText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.ComponentRenderUtils;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.ChatVisiblity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Minecraft 26.3 for Lumora's chat windows: lines are FormattedCharSequences, drawn through the GUI's text collector
 * (in the open chat with Minecraft's own hover tooltips and hand cursor), heads from the tab list skins.
 */
final class McChatText implements ChatText {
    static final McChatText INSTANCE = new McChatText();

    /** A tab list skin: texture and whether the hat layer shows. */
    private record Head(Identifier texture, boolean hat) {
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    private static GuiGraphicsExtractor graphics(HudGraphics g) {
        return ((McHudGraphics) g).graphics();
    }

    @Override
    public List<Object> wrap(Object message, int width) {
        return new ArrayList<>(ComponentRenderUtils.wrapComponents((Component) message, width, mc().font));
    }

    @Override
    public void draw(HudGraphics g, Object line, int x, int y, int color, boolean shadow) {
        GuiGraphicsExtractor gg = graphics(g);
        FormattedCharSequence seq = (FormattedCharSequence) line;
        if (mc().screen instanceof ChatScreen) {
            // open chat: Minecraft's hover tooltips and the hand cursor over click events
            ActiveTextCollector text = gg.textRenderer(GuiGraphicsExtractor.HoveredTextEffects.TOOLTIP_AND_CURSOR);
            float opacity = (color >>> 24) / 255f;
            text.accept(TextAlignment.LEFT, x, y, text.defaultParameters().withOpacity(opacity), seq);
        } else {
            gg.text(mc().font, seq, x, y, color, shadow);
        }
    }

    @Override
    public Object styleAt(Object line, int x) {
        if (x < 0) {
            return null;
        }
        Style[] found = {null};
        int[] pos = {0};
        ((FormattedCharSequence) line).accept((index, style, codepoint) -> {
            int w = mc().font.width(FormattedCharSequence.codepoint(codepoint, style));
            if (x < pos[0] + w) {
                found[0] = style;
                return false;
            }
            pos[0] += w;
            return true;
        });
        return found[0];
    }

    @Override
    public void hover(HudGraphics g, Object style, int mouseX, int mouseY) {
        // drawn by the text collector in draw()
    }

    @Override
    public boolean click(Object style, boolean shift) {
        if (style instanceof Style s && mc().screen instanceof ChatScreen screen) {
            return ((dev.lumora.client.mixin.ChatScreenAccessor) screen).lumora$clicked(s, shift);
        }
        return false;
    }

    @Override
    public Object head(String name) {
        if (mc().getConnection() == null || name == null) {
            return null;
        }
        PlayerInfo info = mc().getConnection().getPlayerInfoIgnoreCase(name);
        return info == null ? null : new Head(info.getSkin().body().texturePath(), info.showHat());
    }

    @Override
    public void drawHead(HudGraphics g, Object head, int x, int y, int size) {
        if (head instanceof Head h) {
            PlayerFaceExtractor.extractRenderState(graphics(g), h.texture(), x, y, size, h.hat(), false, -1);
        }
    }

    @Override
    public void layer(HudGraphics g) {
        graphics(g).nextStratum();
    }

    @Override
    public Options options() {
        var o = mc().options;
        return new Options(o.chatScale().get(), o.chatLineSpacing().get(), o.chatOpacity().get(), o.textBackgroundOpacity().get(),
                ChatComponent.getWidth(o.chatWidth().get()), ChatComponent.getHeight(o.chatHeightFocused().get()),
                ChatComponent.getHeight(o.chatHeightUnfocused().get()), o.chatVisibility().get() == ChatVisiblity.HIDDEN);
    }

    @Override
    public Parts parts(Object message) {
        Component content = (Component) message;
        List<String> insertions = new ArrayList<>();
        List<String> commands = new ArrayList<>();
        List<String> links = new ArrayList<>();
        content.<Object>visit((style, text) -> {
            if (style.getInsertion() != null) {
                insertions.add(style.getInsertion());
            }
            ClickEvent c = style.getClickEvent();
            if (c instanceof ClickEvent.OpenUrl u) {
                links.add(u.uri().toString());
            } else if (c instanceof ClickEvent.SuggestCommand s) {
                commands.add(s.command());
            } else if (c instanceof ClickEvent.RunCommand r) {
                commands.add(r.command());
            }
            return Optional.empty();
        }, Style.EMPTY);
        return new Parts(content.getString(), insertions, commands, links);
    }
}
