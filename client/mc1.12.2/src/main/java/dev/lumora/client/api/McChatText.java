package dev.lumora.client.api;

import com.mojang.blaze3d.platform.GlStateManager;
import dev.lumora.api.render.HudGraphics;
import dev.lumora.client.compat.GuiGraphics;
import dev.lumora.core.chat.windows.ChatText;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.Texts;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Legacy Minecraft for Lumora's chat windows: lines are Text pieces wrapped like Minecraft's chat, drawn as formatted
 * strings, hover texts and clicks go through the screen's own text handling, heads come from the tab list skins.
 */
final class McChatText implements ChatText {
    static final McChatText INSTANCE = new McChatText();

    private static MinecraftClient mc() {
        return MinecraftClient.getInstance();
    }

    private static GuiGraphics graphics(HudGraphics g) {
        return ((McHudGraphics) g).graphics();
    }

    @Override
    public List<Object> wrap(Object message, int width) {
        return new ArrayList<>(Texts.wrapLines((Text) message, width, mc().textRenderer, false, false));
    }

    @Override
    public void draw(HudGraphics g, Object line, int x, int y, int color, boolean shadow) {
        GlStateManager.enableBlend();
        mc().textRenderer.draw(((Text) line).asFormattedString(), (float) x, (float) y, color, shadow);
        // like Minecraft's chat: the text renderer turns the alpha test on, which would drop the see-through backgrounds
        GlStateManager.disableAlphaTest();
        GlStateManager.disableBlend();
    }

    @Override
    public Object styleAt(Object line, int x) {
        if (x < 0) {
            return null;
        }
        int w = 0;
        for (Text part : (Text) line) {
            if (part instanceof LiteralText literal) {
                w += mc().textRenderer.getStringWidth(Texts.getRenderChatMessage(literal.getRawString(), false));
                if (w > x) {
                    return part;
                }
            }
        }
        return null;
    }

    @Override
    public void hover(HudGraphics g, Object style, int mouseX, int mouseY) {
        if (style instanceof Text t && t.getStyle().getHoverEvent() != null && mc().currentScreen != null) {
            ((dev.lumora.client.mixin.ScreenTextInvoker) mc().currentScreen).lumora$hover(t, mouseX, mouseY);
        }
    }

    @Override
    public boolean click(Object style, boolean shift) {
        return style instanceof Text t && mc().currentScreen != null
                && ((dev.lumora.client.mixin.ScreenTextInvoker) mc().currentScreen).lumora$click(t);
    }

    @Override
    public Object head(String name) {
        if (mc().getNetworkHandler() == null || name == null) {
            return null;
        }
        PlayerListEntry info = mc().getNetworkHandler().getPlayerListEntry(name);
        if (info == null) {
            for (PlayerListEntry p : mc().getNetworkHandler().getPlayerList()) {
                if (p.getProfile() != null && name.equalsIgnoreCase(p.getProfile().getName())) {
                    info = p;
                    break;
                }
            }
        }
        return info == null ? null : info.getSkinTexture();
    }

    @Override
    public void drawHead(HudGraphics g, Object head, int x, int y, int size) {
        if (head instanceof Identifier skin) {
            GuiGraphics gg = graphics(g);
            gg.blit(skin, x, y, size, size, 8, 8, 8, 8, 64, 64);
            gg.blit(skin, x, y, size, size, 40, 8, 8, 8, 64, 64);
        }
    }

    @Override
    public Options options() {
        var o = mc().options;
        // legacy chat: no line spacing option, the line background is half the text alpha, the chat sits 28 pixels above the bottom
        return new Options(o.chatScale, 0, o.chatOpacity, 0.5, ChatHud.getWidth(o.chatWidth), ChatHud.getHeight(o.chatHeightFocused),
                ChatHud.getHeight(o.chatHeightUnfocused), o.chatVisibilityType == PlayerEntity.ChatVisibilityType.HIDDEN, 28);
    }

    @Override
    public Parts parts(Object message) {
        Text found = (Text) message;
        List<String> insertions = new ArrayList<>();
        List<String> commands = new ArrayList<>();
        List<String> links = new ArrayList<>();
        for (Text part : found) {
            Style style = part.getStyle();
            if (style.getInsertion() != null) {
                insertions.add(style.getInsertion());
            }
            ClickEvent c = style.getClickEvent();
            if (c != null && c.getAction() == ClickEvent.Action.OPEN_URL) {
                links.add(c.getValue());
            } else if (c != null && (c.getAction() == ClickEvent.Action.SUGGEST_COMMAND || c.getAction() == ClickEvent.Action.RUN_COMMAND)) {
                commands.add(c.getValue());
            }
        }
        return new Parts(found.asUnformattedString(), insertions, commands, links);
    }
}
