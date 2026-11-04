package dev.lumora.client.api;

import dev.lumora.api.i18n.Translations;
import dev.lumora.api.resource.Resources;
import dev.lumora.client.LumoraClient;
import dev.lumora.client.hud.HudEditorScreen;
import dev.lumora.core.CoreLogger;
import dev.lumora.core.Platform;
import dev.lumora.core.input.KeyPlatform;
import dev.lumora.client.compat.Keys;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.util.Identifier;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.Display;

import java.io.IOException;
import java.util.Locale;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Minecraft 1.12.2 implementation of the core's {@link Platform} (key codes translated between GLFW and LWJGL 2, see {@link Keys}). */
final class McPlatform implements Platform {
    private final McGameContext game = new McGameContext();
    private final McWorld world = new McWorld();
    private final McChannels channels = new McChannels();
    private final CoreLogger logger = new CoreLogger() {
        @Override
        public void info(String message) {
            LumoraClient.LOG.info(message);
        }

        @Override
        public void warn(String message) {
            LumoraClient.LOG.warn(message);
        }

        @Override
        public void warn(String message, Throwable error) {
            LumoraClient.LOG.warn(message, error);
        }

        @Override
        public void error(String message, Throwable error) {
            LumoraClient.LOG.error(message, error);
        }
    };
    private final Translations translations = new Translations() {
        @Override
        public String translate(String key, Object... args) {
            return I18n.translate(key, args);
        }

        @Override
        public boolean has(String key) {
            // 1.12.2 has no lookup: an unknown key translates to itself
            return !I18n.translate(key).equals(key);
        }

        @Override
        public String language() {
            MinecraftClient mc = MinecraftClient.getInstance();
            return mc != null && mc.options != null ? mc.options.language.toLowerCase(Locale.ROOT) : "en_us";
        }
    };
    private final Resources resources = (namespace, path) -> {
        try {
            MinecraftClient.getInstance().getResourceManager().getResource(new Identifier(namespace, path));
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    };
    private final KeyPlatform keys = new KeyPlatform() {
        @Override
        public boolean isDown(int key) {
            int code = Keys.toLwjgl(key);
            return code != Keyboard.KEY_NONE && Keyboard.isKeyDown(code);
        }

        @Override
        public String keyName(int key) {
            int code = Keys.toLwjgl(key);
            return code == Keyboard.KEY_NONE ? "?" : GameOptions.getFormattedNameForKeyCode(code);
        }

        @Override
        public Map<Integer, String> minecraftBindings() {
            Map<Integer, String> out = new HashMap<>();
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.options == null) {
                return out;
            }
            // 1.12.2: positive codes are keyboard keys (LWJGL 2), negative ones mouse buttons
            for (KeyBinding kb : mc.options.allKeys) {
                int glfw = kb.getCode() > 0 ? Keys.toGlfw(kb.getCode()) : Keys.NONE;
                if (glfw != Keys.NONE) {
                    out.putIfAbsent(glfw, I18n.translate(kb.getTranslationKey()));
                }
            }
            return out;
        }

        @Override
        public boolean acceptsKeybinds() {
            MinecraftClient mc = MinecraftClient.getInstance();
            return mc.currentScreen == null && mc.player != null && Display.isActive();
        }
    };

    McGameContext gameContext() {
        return game;
    }

    @Override
    public String minecraftVersion() {
        return dev.lumora.client.platform.LoaderEnv.version("minecraft").orElse("unknown");
    }

    @Override
    public String clientVersion() {
        return dev.lumora.client.platform.LoaderEnv.version("lumora").orElse("dev");
    }

    @Override
    public Path configDir() {
        return dev.lumora.client.platform.LoaderEnv.configDir();
    }

    @Override
    public Path addonsDir() {
        // Lumora addons as plain jars, loaded by Lumora itself
        return dev.lumora.client.platform.LoaderEnv.gameDir().resolve("addons");
    }

    @Override
    public CoreLogger logger() {
        return logger;
    }

    @Override
    public McGameContext game() {
        return game;
    }

    @Override
    public Translations translations() {
        return translations;
    }

    @Override
    public Resources resources() {
        return resources;
    }

    @Override
    public KeyPlatform keys() {
        return keys;
    }

    @Override
    public void openHudEditor() {
        MinecraftClient mc = MinecraftClient.getInstance();
        dev.lumora.client.gui.AddonViewScreen.keepCurrent();
        mc.setScreen(new HudEditorScreen(mc.currentScreen));
    }

    @Override
    public boolean hudEditorOpen() {
        return MinecraftClient.getInstance().currentScreen instanceof HudEditorScreen;
    }

    @Override
    public java.util.Optional<dev.lumora.core.social.GameSession> session() {
        net.minecraft.client.util.Session u = MinecraftClient.getInstance().getSession();
        if (u == null || u.getUuid() == null) {
            return java.util.Optional.empty();
        }
        // the token is read only when the Mojang session join needs it
        return java.util.Optional.of(new dev.lumora.core.social.GameSession(u.getUsername(), u.getUuid(), u::getAccessToken));
    }

    @Override
    public void openSocial() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!(mc.currentScreen instanceof dev.lumora.client.gui.SocialScreen)) {
            mc.setScreen(new dev.lumora.client.gui.SocialScreen(mc.currentScreen));
        }
    }

    // --- Lumora API 1.1 ---

    @Override
    public dev.lumora.api.game.WorldAccess world() {
        return world;
    }

    @Override
    public dev.lumora.core.net.ChannelRegistry.Platform channels() {
        return channels;
    }

    @Override
    public dev.lumora.core.screen.ViewManager.Platform views() {
        return new dev.lumora.core.screen.ViewManager.Platform() {
            @Override
            public void show(dev.lumora.api.screen.AddonView view) {
                dev.lumora.client.gui.AddonViewScreen.show(view);
            }

            @Override
            public void close(dev.lumora.api.screen.AddonView view) {
                dev.lumora.client.gui.AddonViewScreen.close(view);
            }

            @Override
            public String clipboard() {
                String s = net.minecraft.client.gui.screen.Screen.getClipboard();
                return s == null ? "" : s;
            }

            @Override
            public void setClipboard(String text) {
                net.minecraft.client.gui.screen.Screen.setClipboard(text);
            }

            @Override
            public void openAddonSettings(String addonId) {
                var core = LumoraRuntime.core();
                var record = core.addonManager().record(addonId).orElse(null);
                if (record == null || record.configuration() == null) {
                    return;
                }
                net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
                dev.lumora.client.gui.AddonViewScreen.keepCurrent();
                mc.setScreen(dev.lumora.client.gui.SettingsScreen.create(mc.currentScreen, dev.lumora.client.compat.Component.literal(record.descriptor().name()),
                        record.configuration().settings(), core.keybindManager().of(addonId), record.configuration()::save));
            }
        };
    }

    private final McBrowsers browsers = new McBrowsers();

    @Override
    public dev.lumora.core.browser.BrowserPlatform browsers() {
        return browsers;
    }

    private final McExtras extras = new McExtras();

    @Override
    public dev.lumora.core.extras.GameExtras extras() {
        return extras;
    }

    @Override
    public boolean modLoaded(String modId) {
        return dev.lumora.client.platform.LoaderEnv.isLoaded(modId);
    }

    @Override
    public boolean uploadTexture(String path, int width, int height, int[] argb) {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, width, height, argb, 0, width);
        net.minecraft.client.texture.TextureManager tm = net.minecraft.client.MinecraftClient.getInstance().getTextureManager();
        net.minecraft.util.Identifier id = new net.minecraft.util.Identifier("lumora", path);
        tm.close(id);
        return tm.loadTexture(id, new net.minecraft.client.texture.NativeImageBackedTexture(img));
    }

    @Override
    public void releaseTexture(String path) {
        net.minecraft.client.MinecraftClient.getInstance().getTextureManager().close(new net.minecraft.util.Identifier("lumora", path));
    }
}
