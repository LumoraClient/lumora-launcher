package dev.lumora.client.gui;

import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.network.chat.TextComponent;
import com.mojang.realmsclient.RealmsMainScreen;
import dev.lumora.client.LumoraClient;
import dev.lumora.client.integration.RuntimeInfo;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.lumora.client.compat.GuiGraphics;
import com.mojang.math.Vector3f;
import net.minecraft.Util;
import net.minecraft.util.Mth;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.AccessibilityOptionsScreen;
import net.minecraft.client.gui.screens.LanguageSelectScreen;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main menu with the vanilla layout (logo at the top, splash text, centred button column, language and
 * accessibility icons, version and copyright at the bottom) in the Lumora launcher look: glass buttons,
 * GLSL aurora sky over the landscape and the glowing LUMORA wordmark instead of the Minecraft logo.
 * Button positions follow the vanilla TitleScreen (first row at height / 4 + 48, 24 px apart).
 */
public final class LumoraTitleScreen extends Screen {
    private static final Logger LOG = LoggerFactory.getLogger("Lumora");
    private static final Component COPYRIGHT = new TextComponent("Copyright Mojang AB. Do not distribute!");

    /** Set by "Klassisches Menü" (Lumora hub); the replacement mixin then leaves the vanilla title screen alone. */
    public static volatile boolean classicUntilRestart;
    /** One-shot exception, e.g. to reach the vanilla demo-world button. */
    private static volatile boolean vanillaOnce;

    /** Splash text; Minecraft 1.18.2 has no SplashRenderer yet, see {@link #renderSplash}. */
    private String splash;
    private long openedAt;

    public LumoraTitleScreen() {
        super(new TranslatableComponent("lumora.menu.title"));
    }

    public static boolean shouldReplace() {
        if (vanillaOnce) {
            vanillaOnce = false;
            return false;
        }
        return !classicUntilRestart && LumoraClient.config() != null && LumoraClient.config().customMainMenu;
    }

    static Screen vanillaTitle() {
        vanillaOnce = true;
        return new TitleScreen();
    }

    @Override
    protected void init() {
        if (splash == null) {
            splash = minecraft.getSplashManager().getSplash();
        }
        openedAt = System.currentTimeMillis();
        int cx = width / 2;
        int l = height / 4 + 48;

        addRenderableWidget(new LumoraButton(cx - 100, l, 200, 20, new TranslatableComponent("menu.singleplayer"),
                LumoraButton.Style.PRIMARY, null, () -> minecraft.setScreen(singleplayer())));
        addRenderableWidget(new LumoraButton(cx - 100, l + 24, 200, 20, new TranslatableComponent("menu.multiplayer"),
                LumoraButton.Style.SECONDARY, null, () -> minecraft.setScreen(new JoinMultiplayerScreen(this))));
        if (FabricLoader.getInstance().isModLoaded("modmenu")) {
            addRenderableWidget(new LumoraButton(cx - 100, l + 48, 98, 20, new TranslatableComponent("menu.online"),
                    LumoraButton.Style.SECONDARY, null, () -> minecraft.setScreen(new RealmsMainScreen(this))));
            addRenderableWidget(new LumoraButton(cx + 2, l + 48, 98, 20, new TranslatableComponent("lumora.menu.mods"),
                    LumoraButton.Style.SECONDARY, null, this::openModMenu));
        } else {
            addRenderableWidget(new LumoraButton(cx - 100, l + 48, 200, 20, new TranslatableComponent("menu.online"),
                    LumoraButton.Style.SECONDARY, null, () -> minecraft.setScreen(new RealmsMainScreen(this))));
        }

        int row = l + 72 + 12;
        addRenderableWidget(new LumoraIconButton(cx - 124, row, new TranslatableComponent("options.language"),
                new ResourceLocation("icon/language"),
                () -> minecraft.setScreen(new LanguageSelectScreen(this, minecraft.options, minecraft.getLanguageManager()))));
        addRenderableWidget(new LumoraButton(cx - 100, row, 98, 20, new TranslatableComponent("menu.options"),
                LumoraButton.Style.SECONDARY, null, () -> minecraft.setScreen(new OptionsScreen(this, minecraft.options))));
        addRenderableWidget(new LumoraButton(cx + 2, row, 98, 20, new TranslatableComponent("menu.quit"),
                LumoraButton.Style.SECONDARY, null, () -> minecraft.stop()));
        addRenderableWidget(new LumoraIconButton(cx + 104, row, new TranslatableComponent("options.accessibility"),
                new ResourceLocation("icon/accessibility"),
                () -> minecraft.setScreen(new AccessibilityOptionsScreen(this, minecraft.options))));

        // Lumora hub (client info, protocol, benchmark, classic menu) - small pill in the top-left corner
        addRenderableWidget(new LumoraButton(6, 6, 74, 18, new TranslatableComponent("lumora.menu.hub"),
                LumoraButton.Style.SECONDARY, "✦", () -> minecraft.setScreen(new LumoraHubScreen(this))));
        addRenderableWidget(new LumoraButton(84, 6, 84, 18, SocialScreen.label(), LumoraButton.Style.SECONDARY, "☻",
                () -> minecraft.setScreen(new SocialScreen(this))));
    }

    private Screen singleplayer() {
        // Demo accounts have no world list; the vanilla title screen offers the official demo world.
        return minecraft.isDemo() ? vanillaTitle() : new SelectWorldScreen(this);
    }

    private void openModMenu() {
        try {
            Screen mods = (Screen) Class.forName("com.terraformersmc.modmenu.gui.ModsScreen")
                    .getConstructor(Screen.class).newInstance(this);
            minecraft.setScreen(mods);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            LOG.warn("Mod Menu screen not available", e);
        }
    }

    /** Background of this screen; Minecraft 1.18.2 has no Screen#renderBackground(g, x, y, delta) yet, see render. */
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        LumoraUi.background(g, width, height);
        float fadeIn = Math.min(1f, (System.currentTimeMillis() - openedAt) / 900f);
        LumoraShaders.sky(g, width, height, 0.35f + 0.65f * fadeIn);
        if (!LumoraShaders.enabled()) {
            g.fill(0, 0, width, height, 0x40000000);
        }

        // logo: glowing LUMORA wordmark where the vanilla logo sits (y = 30)
        Component word = new TextComponent("LUMORA").withStyle(Style.EMPTY.withBold(true));
        int textW = font.width(word);
        float scale = Math.min(5f, (width - 60f) / textW);
        int logoW = Math.round(textW * scale);
        int logoH = Math.round(9 * scale);
        int lx = (width - logoW) / 2;
        int ly = 30;
        // subtle teal haze behind the wordmark (dark accent so the filled glow stays soft)
        LumoraShaders.glow(g, lx + 8, ly + 4, logoW - 16, logoH - 8, 0x0B4652, 22);
        LumoraUi.bigText(g, word, lx + Math.max(1, Math.round(scale / 2)), ly + Math.max(1, Math.round(scale / 2)), scale,
                LumoraUi.alpha(LumoraUi.ACCENT_DARK, 0xD0));
        LumoraUi.bigText(g, word, lx, ly, scale, 0xFFFFFFFF);
        // "edition" line like vanilla's Java Edition badge
        String edition = "CLIENT · " + SharedConstants.getCurrentVersion().getName();
        int ew = font.width(edition) + 14;
        LumoraShaders.glass(g, (width - ew) / 2, ly + logoH + 4, ew, 13, 0xB08B5CF6);
        g.drawCenteredString(font, edition, width / 2, ly + logoH + 7, 0xFFF1EBFF);

        // footer like vanilla: version bottom-left, copyright bottom-right
        String version = "Minecraft " + SharedConstants.getCurrentVersion().getName() + " · Fabric · Lumora "
                + RuntimeInfo.modVersion("lumora").orElse("?") + (minecraft.isDemo() ? " · Demo" : "");
        if (font.width(version) + font.width(COPYRIGHT) + 12 > width) {
            version = "Minecraft " + SharedConstants.getCurrentVersion().getName() + (minecraft.isDemo() ? " Demo" : "");
        }
        g.drawString(font, version, 2, height - 10, 0xFFFFFFFF, true);
        g.drawString(font, COPYRIGHT, width - font.width(COPYRIGHT) - 2, height - 10, 0xFFFFFFFF, true);
    }

    /** The vanilla 1.18.2 splash: yellow, tilted -20 degrees, pulsing, right of the logo. */
    private void renderSplash(GuiGraphics g, int alphaMask) {
        if ((alphaMask & 0xFC000000) == 0) {
            return;
        }
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(width / 2f + 90, 70, 0);
        pose.mulPose(Vector3f.ZP.rotationDegrees(-20f));
        float scale = 1.8f - Mth.abs(Mth.sin((Util.getMillis() % 1000L) / 1000f * Mth.TWO_PI) * 0.1f);
        scale = scale * 100f / (font.width(splash) + 32);
        pose.scale(scale, scale, scale);
        g.drawCenteredString(font, splash, 0, -8, 0xFFFF00 | alphaMask);
        pose.popPose();
    }

    @Override
    public void render(PoseStack pose, int mouseX, int mouseY, float delta) {
        GuiGraphics g = GuiGraphics.of(pose);
        // Minecraft 1.18.2: Screen#render does not draw the background yet
        renderBackground(g, mouseX, mouseY, delta);
        super.render(g.pose(), mouseX, mouseY, delta);
        if (splash != null) { // 1.18.2 has no "hide splash texts" option
            float alpha = Math.min(1f, (System.currentTimeMillis() - openedAt) / 1000f);
            renderSplash(g, Math.round(alpha * 255) << 24);
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
