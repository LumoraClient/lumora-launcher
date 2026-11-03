package dev.lumora.client.gui;

import com.mojang.blaze3d.platform.GlStateManager;
import dev.lumora.client.LumoraClient;
import dev.lumora.client.compat.Component;
import dev.lumora.client.compat.Component.Style;
import dev.lumora.client.compat.GuiGraphics;
import dev.lumora.client.compat.Screen;
import dev.lumora.client.integration.RuntimeInfo;
import net.minecraft.client.gui.screen.SettingsScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.options.LanguageOptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.realms.RealmsBridge;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Main menu with the vanilla layout (logo at the top, splash text, centred button column, language icon, version and
 * copyright at the bottom) in the Lumora launcher look and the LUMORA wordmark instead of the Minecraft logo.
 * Button positions follow the vanilla TitleScreen (first row at height / 4 + 48, 24 px apart). Minecraft 1.12.2 has no
 * accessibility menu, so its icon is left out.
 */
public final class LumoraTitleScreen extends Screen {
    private static final Logger LOG = LogManager.getLogger("Lumora");
    private static final Component COPYRIGHT = Component.literal("Copyright Mojang AB. Do not distribute!");
    private static final Identifier SPLASHES = new Identifier("texts/splashes.txt");

    /** Set by "Klassisches Menü" (Lumora hub); the replacement mixin then leaves the vanilla title screen alone. */
    public static volatile boolean classicUntilRestart;
    /** One-shot exception, e.g. to reach the vanilla demo-world button. */
    private static volatile boolean vanillaOnce;

    private String splash;
    private long openedAt;

    public LumoraTitleScreen() {
        super(Component.translatable("lumora.menu.title"));
    }

    public static boolean shouldReplace() {
        if (vanillaOnce) {
            vanillaOnce = false;
            return false;
        }
        return !classicUntilRestart && LumoraClient.config() != null && LumoraClient.config().customMainMenu;
    }

    static net.minecraft.client.gui.screen.Screen vanillaTitle() {
        vanillaOnce = true;
        return new TitleScreen();
    }

    @Override
    public void init() {
        if (splash == null) {
            splash = randomSplash();
        }
        openedAt = System.currentTimeMillis();
        int cx = width / 2;
        int l = height / 4 + 48;

        addRenderableWidget(new LumoraButton(cx - 100, l, 200, 20, Component.translatable("menu.singleplayer"),
                LumoraButton.Style.PRIMARY, null, () -> minecraft.setScreen(singleplayer())));
        addRenderableWidget(new LumoraButton(cx - 100, l + 24, 200, 20, Component.translatable("menu.multiplayer"),
                LumoraButton.Style.SECONDARY, null, () -> minecraft.setScreen(new MultiplayerScreen(this))));
        if (dev.lumora.client.platform.LoaderEnv.isLoaded("modmenu")) {
            addRenderableWidget(new LumoraButton(cx - 100, l + 48, 98, 20, Component.translatable("menu.online"),
                    LumoraButton.Style.SECONDARY, null, this::openRealms));
            addRenderableWidget(new LumoraButton(cx + 2, l + 48, 98, 20, Component.translatable("lumora.menu.mods"),
                    LumoraButton.Style.SECONDARY, null, this::openModMenu));
        } else {
            addRenderableWidget(new LumoraButton(cx - 100, l + 48, 200, 20, Component.translatable("menu.online"),
                    LumoraButton.Style.SECONDARY, null, this::openRealms));
        }

        int row = l + 72 + 12;
        addRenderableWidget(new LumoraIconButton(cx - 124, row, Component.translatable("options.language"),
                new Identifier("icon/language"),
                () -> minecraft.setScreen(new LanguageOptionsScreen(this, minecraft.options, minecraft.getLanguageManager()))));
        addRenderableWidget(new LumoraButton(cx - 100, row, 98, 20, Component.translatable("menu.options"),
                LumoraButton.Style.SECONDARY, null, () -> minecraft.setScreen(new SettingsScreen(this, minecraft.options))));
        addRenderableWidget(new LumoraButton(cx + 2, row, 98, 20, Component.translatable("menu.quit"),
                LumoraButton.Style.SECONDARY, null, () -> minecraft.scheduleStop()));

        // Lumora hub (client info, protocol, benchmark, classic menu) - small pill in the top-left corner
        addRenderableWidget(new LumoraButton(6, 6, 74, 18, Component.translatable("lumora.menu.hub"),
                LumoraButton.Style.SECONDARY, "✦", () -> minecraft.setScreen(new LumoraHubScreen(this))));
        addRenderableWidget(new LumoraButton(84, 6, 84, 18, SocialScreen.label(), LumoraButton.Style.SECONDARY, "☻",
                () -> minecraft.setScreen(new SocialScreen(this))));
    }

    private net.minecraft.client.gui.screen.Screen singleplayer() {
        // Demo accounts have no world list; the vanilla title screen offers the official demo world.
        return minecraft.isDemo() ? vanillaTitle() : new SelectWorldScreen(this);
    }

    private void openRealms() {
        new RealmsBridge().switchToRealms(this);
    }

    private void openModMenu() {
        try {
            var mods = (net.minecraft.client.gui.screen.Screen) Class.forName("com.terraformersmc.modmenu.gui.ModsScreen")
                    .getConstructor(net.minecraft.client.gui.screen.Screen.class).newInstance(this);
            minecraft.setScreen(mods);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            LOG.warn("Mod Menu screen not available", e);
        }
    }

    /** A random line of the vanilla splash list (1.12.2 has no SplashManager: the title screen reads the file itself). */
    private String randomSplash() {
        try (var reader = new BufferedReader(new InputStreamReader(
                minecraft.getResourceManager().getResource(SPLASHES).getInputStream(), StandardCharsets.UTF_8))) {
            List<String> lines = new ArrayList<>();
            for (String line; (line = reader.readLine()) != null; ) {
                line = line.trim();
                // vanilla skips this entry (its hash collides with the "Notch" splash check)
                if (!line.isEmpty() && line.hashCode() != 125780783) {
                    lines.add(line);
                }
            }
            return lines.isEmpty() ? null : lines.get(ThreadLocalRandom.current().nextInt(lines.size()));
        } catch (Exception e) {
            LOG.debug("No splash texts", e);
            return null;
        }
    }

    /** Background of this screen (landscape, wordmark, edition badge, footer). */
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        LumoraUi.background(g, width, height);
        float fadeIn = Math.min(1f, (System.currentTimeMillis() - openedAt) / 900f);
        LumoraShaders.sky(g, width, height, 0.35f + 0.65f * fadeIn);
        if (!LumoraShaders.enabled()) {
            g.fill(0, 0, width, height, 0x40000000);
        }

        // logo: LUMORA wordmark where the vanilla logo sits (y = 30)
        Component word = Component.literal("LUMORA").withStyle(Style.EMPTY.withBold(true));
        int textW = font.width(word);
        float scale = Math.min(5f, (width - 60f) / textW);
        int logoW = Math.round(textW * scale);
        int logoH = Math.round(9 * scale);
        int lx = (width - logoW) / 2;
        int ly = 30;
        LumoraShaders.glow(g, lx + 8, ly + 4, logoW - 16, logoH - 8, 0x0B4652, 22);
        LumoraUi.bigText(g, word, lx + Math.max(1, Math.round(scale / 2)), ly + Math.max(1, Math.round(scale / 2)), scale,
                LumoraUi.alpha(LumoraUi.ACCENT_DARK, 0xD0));
        LumoraUi.bigText(g, word, lx, ly, scale, 0xFFFFFFFF);
        // "edition" line like vanilla's Java Edition badge
        String edition = "CLIENT · " + RuntimeInfo.MINECRAFT_VERSION;
        int ew = font.width(edition) + 14;
        LumoraShaders.glass(g, (width - ew) / 2, ly + logoH + 4, ew, 13, 0xB08B5CF6);
        g.drawCenteredString(font, edition, width / 2, ly + logoH + 7, 0xFFF1EBFF);

        // footer like vanilla: version bottom-left, copyright bottom-right
        String version = "Minecraft " + RuntimeInfo.MINECRAFT_VERSION + " · Legacy Fabric · Lumora "
                + RuntimeInfo.modVersion("lumora").orElse("?") + (minecraft.isDemo() ? " · Demo" : "");
        if (font.width(version) + font.width(COPYRIGHT) + 12 > width) {
            version = "Minecraft " + RuntimeInfo.MINECRAFT_VERSION + (minecraft.isDemo() ? " Demo" : "");
        }
        g.drawString(font, version, 2, height - 10, 0xFFFFFFFF, true);
        g.drawString(font, COPYRIGHT, width - font.width(COPYRIGHT) - 2, height - 10, 0xFFFFFFFF, true);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        // Minecraft 1.12.2: Screen#render does not draw the background
        renderBackground(g, mouseX, mouseY, delta);
        super.render(g, mouseX, mouseY, delta);
        if (splash != null) {
            float alpha = Math.min(1f, (System.currentTimeMillis() - openedAt) / 1000f);
            renderSplash(g, Math.round(alpha * 255));
        }
    }

    /** The vanilla splash: yellow, tilted -20 degrees, pulsing, right of the logo. */
    private void renderSplash(GuiGraphics g, int alpha) {
        if (alpha < 8) {
            return;
        }
        GlStateManager.pushMatrix();
        GlStateManager.translate(width / 2f + 123, 69, 0); // same spot as the SplashRenderer of newer versions
        GlStateManager.rotate(-20f, 0f, 0f, 1f);
        float scale = 1.8f - MathHelper.abs(MathHelper.sin((System.currentTimeMillis() % 1000L) / 1000f * (float) (Math.PI * 2)) * 0.1f);
        scale = scale * 100f / (font.width(splash) + 32);
        GlStateManager.scale(scale, scale, scale);
        g.drawCenteredString(font, splash, 0, -8, (alpha << 24) | 0xFFFF00);
        GlStateManager.popMatrix();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
