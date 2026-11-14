package dev.lumora.client.api;

import com.mojang.blaze3d.platform.InputConstants;
import dev.lumora.api.i18n.Translations;
import dev.lumora.api.resource.Resources;
import dev.lumora.client.LumoraClient;
import dev.lumora.client.hud.HudEditorScreen;
import dev.lumora.core.CoreLogger;
import dev.lumora.core.Platform;
import dev.lumora.core.input.KeyPlatform;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Minecraft 26.3 implementation of the core's {@link Platform}. */
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
            return I18n.get(key, args);
        }

        @Override
        public boolean has(String key) {
            return net.minecraft.locale.Language.getInstance().has(key);
        }

        @Override
        public String language() {
            Minecraft mc = Minecraft.getInstance();
            return mc != null && mc.options != null ? mc.options.languageCode : "en_us";
        }
    };
    private final Resources resources = (namespace, path) -> {
        try {
            return Minecraft.getInstance().getResourceManager().getResource(Identifier.fromNamespaceAndPath(namespace, path)).isPresent();
        } catch (RuntimeException e) {
            return false;
        }
    };
    private final KeyPlatform keys = new KeyPlatform() {
        @Override
        public boolean isDown(int key) {
            int sdl = SdlKeys.toSdl(key);
            return sdl != SdlKeys.NONE && InputConstants.isKeyDown(sdl);
        }

        @Override
        public String keyName(int key) {
            int sdl = SdlKeys.toSdl(key);
            return sdl == SdlKeys.NONE ? "?" : InputConstants.Type.KEYBOARD.getOrCreate(sdl).getDisplayName().getString();
        }

        @Override
        public Map<Integer, String> minecraftBindings() {
            Map<Integer, String> out = new HashMap<>();
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.options == null) {
                return out;
            }
            for (KeyMapping km : mc.options.keyMappings) {
                if (km.isUnbound()) {
                    continue;
                }
                // F3 debug combinations (own key mappings since 1.21.9) only fire with F3 held: no conflict for addon keys
                if (km.getCategory() == KeyMapping.Category.DEBUG) {
                    continue;
                }
                InputConstants.Key k = InputConstants.getKey(km.saveString());
                int glfw = k.getType() == InputConstants.Type.KEYBOARD ? SdlKeys.toGlfw(k.getValue()) : SdlKeys.NONE;
                if (glfw != SdlKeys.NONE) {
                    out.putIfAbsent(glfw, Component.translatable(km.getName()).getString());
                }
            }
            return out;
        }

        @Override
        public boolean acceptsKeybinds() {
            Minecraft mc = Minecraft.getInstance();
            return mc.gui.screen() == null && mc.player != null && mc.isWindowActive();
        }
    };

    McGameContext gameContext() {
        return game;
    }

    @Override
    public String minecraftVersion() {
        // the running version (one mod build can serve several patch versions, e.g. 26.1 - 26.1.2)
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
    public Path gameDir() {
        return dev.lumora.client.platform.LoaderEnv.gameDir();
    }

    @Override
    public Path addonsDir() {
        // Lumora addons as plain jars, loaded by Lumora itself
        return dev.lumora.client.platform.LoaderEnv.lumoraDir().resolve("addons");
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
        Minecraft mc = Minecraft.getInstance();
        dev.lumora.client.gui.AddonViewScreen.keepCurrent();
        mc.gui.setScreen(new HudEditorScreen(mc.gui.screen()));
    }

    @Override
    public boolean hudEditorOpen() {
        return Minecraft.getInstance().gui.screen() instanceof HudEditorScreen;
    }

    @Override
    public java.util.Optional<dev.lumora.core.social.GameSession> session() {
        net.minecraft.client.User u = Minecraft.getInstance().getUser();
        String uuid = u == null ? null : u.getProfileId() == null ? null : u.getProfileId().toString();
        if (uuid == null) {
            return java.util.Optional.empty();
        }
        // the token is read only when the Mojang session join needs it
        return java.util.Optional.of(new dev.lumora.core.social.GameSession(u.getName(), uuid, u::getAccessToken));
    }

    @Override
    public void openSocial() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof dev.lumora.client.gui.SocialScreen)) {
            mc.gui.setScreen(new dev.lumora.client.gui.SocialScreen(mc.gui.screen()));
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
                String s = net.minecraft.client.Minecraft.getInstance().keyboardHandler.getClipboard();
                return s == null ? "" : s;
            }

            @Override
            public void setClipboard(String text) {
                net.minecraft.client.Minecraft.getInstance().keyboardHandler.setClipboard(text);
            }

            @Override
            public void openAddonSettings(String addonId) {
                var core = LumoraRuntime.core();
                var record = core.addonManager().record(addonId).orElse(null);
                if (record == null || record.configuration() == null) {
                    return;
                }
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                dev.lumora.client.gui.AddonViewScreen.keepCurrent();
                mc.gui.setScreen(dev.lumora.client.gui.SettingsScreen.create(mc.gui.screen(), net.minecraft.network.chat.Component.literal(record.descriptor().name()),
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
        com.mojang.blaze3d.platform.NativeImage img = new com.mojang.blaze3d.platform.NativeImage(width, height, false);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                img.setPixel(x, y, argb[y * width + x]);
            }
        }
        net.minecraft.client.renderer.texture.TextureManager tm = net.minecraft.client.Minecraft.getInstance().getTextureManager();
        net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.fromNamespaceAndPath("lumora", path);
        tm.release(id);
        tm.register(id, new net.minecraft.client.renderer.texture.DynamicTexture(() -> "lumora " + path, img));
        return true;
    }

    @Override
    public void releaseTexture(String path) {
        net.minecraft.client.Minecraft.getInstance().getTextureManager().release(net.minecraft.resources.Identifier.fromNamespaceAndPath("lumora", path));
    }
}
