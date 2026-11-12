package dev.lumora.core;

import dev.lumora.api.game.ConnectionInfo;
import dev.lumora.api.game.GameContext;
import dev.lumora.api.game.PlayerSnapshot;
import dev.lumora.api.i18n.Translations;
import dev.lumora.api.resource.Resources;
import dev.lumora.core.input.KeyPlatform;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Platform without Minecraft for the core tests. */
public final class FakePlatform implements Platform {
    public final Path dir;
    public final List<String> logs = new ArrayList<>();
    public final Set<Integer> down = new HashSet<>();
    public final Map<Integer, String> minecraftKeys = new HashMap<>();
    public boolean acceptKeys = true;
    public boolean editorOpen;
    public String mc = "26.1.2";
    /** Addon jar folder (null = none) and the parent loader of its class loaders. */
    public Path addons;
    public ClassLoader addonParent = FakePlatform.class.getClassLoader();
    /** Uploaded textures by path (uploadTexture); false = this "version" cannot upload. */
    public final Map<String, int[]> textures = new HashMap<>();
    public boolean textureSupport = true;
    public dev.lumora.core.extras.GameExtras extras = dev.lumora.core.extras.GameExtras.NONE;
    /** What game().inWorld() reports (tests of in-world widgets). */
    public boolean inWorld;
    /** The own player (game().player()), null for none. */
    public PlayerSnapshot player;
    /** What game().connection() reports. */
    public ConnectionInfo connection = ConnectionInfo.NONE;

    @Override
    public dev.lumora.core.extras.GameExtras extras() {
        return extras;
    }

    public FakePlatform(Path dir) {
        this.dir = dir;
    }

    @Override
    public String minecraftVersion() {
        return mc;
    }

    @Override
    public String clientVersion() {
        return "0.1.0";
    }

    @Override
    public Path configDir() {
        return dir;
    }

    @Override
    public boolean uploadTexture(String path, int width, int height, int[] argb) {
        if (!textureSupport) {
            return false;
        }
        if (argb.length != width * height) {
            throw new IllegalArgumentException("pixels");
        }
        textures.put(path, argb.clone());
        return true;
    }

    @Override
    public void releaseTexture(String path) {
        textures.remove(path);
    }

    @Override
    public Path addonsDir() {
        return addons;
    }

    @Override
    public ClassLoader addonParentLoader() {
        return addonParent;
    }

    @Override
    public CoreLogger logger() {
        return new CoreLogger() {
            @Override
            public void info(String message) {
                logs.add("INFO " + message);
            }

            @Override
            public void warn(String message) {
                logs.add("WARN " + message);
            }

            @Override
            public void warn(String message, Throwable error) {
                logs.add("WARN " + message + " " + error);
            }

            @Override
            public void error(String message, Throwable error) {
                logs.add("ERROR " + message + " " + error);
            }
        };
    }

    @Override
    public GameContext game() {
        return new GameContext() {
            @Override
            public boolean inWorld() {
                return inWorld;
            }

            @Override
            public Optional<PlayerSnapshot> player() {
                return Optional.ofNullable(player);
            }

            @Override
            public Optional<String> dimension() {
                return Optional.empty();
            }

            @Override
            public Optional<String> gameMode() {
                return Optional.empty();
            }

            @Override
            public ConnectionInfo connection() {
                return connection;
            }

            @Override
            public boolean screenOpen() {
                return false;
            }

            @Override
            public Optional<String> screen() {
                return Optional.empty();
            }

            @Override
            public boolean hudHidden() {
                return false;
            }

            @Override
            public boolean windowFocused() {
                return true;
            }

            @Override
            public int fps() {
                return 60;
            }

            @Override
            public long clientTicks() {
                return 0;
            }
        };
    }

    @Override
    public Translations translations() {
        return new Translations() {
            @Override
            public String translate(String key, Object... args) {
                return key;
            }

            @Override
            public boolean has(String key) {
                return false;
            }

            @Override
            public String language() {
                return "en_us";
            }
        };
    }

    @Override
    public Resources resources() {
        return (namespace, path) -> false;
    }

    @Override
    public KeyPlatform keys() {
        return new KeyPlatform() {
            @Override
            public boolean isDown(int key) {
                return down.contains(key);
            }

            @Override
            public String keyName(int key) {
                return "K" + key;
            }

            @Override
            public Map<Integer, String> minecraftBindings() {
                return minecraftKeys;
            }

            @Override
            public boolean acceptsKeybinds() {
                return acceptKeys;
            }
        };
    }

    @Override
    public void openHudEditor() {
        editorOpen = true;
    }

    @Override
    public boolean hudEditorOpen() {
        return editorOpen;
    }

    // --- Lumora API 1.1 ---

    public final Set<String> mcChannels = new HashSet<>();
    public final Set<String> serverChannels = new HashSet<>();
    public final List<String> sent = new ArrayList<>();
    public int maxChannelLength = 128;
    public final java.util.Deque<dev.lumora.api.screen.AddonView> screenStack = new java.util.ArrayDeque<>();
    public String clipboard = "";
    public final List<String> openedSettings = new ArrayList<>();
    public Optional<dev.lumora.core.social.GameSession> gameSession = Optional.empty();
    public final Set<String> mods = new HashSet<>();
    public dev.lumora.api.game.WorldAccess worldAccess = dev.lumora.api.game.WorldAccess.NONE;

    @Override
    public dev.lumora.core.net.ChannelRegistry.Platform channels() {
        return new dev.lumora.core.net.ChannelRegistry.Platform() {
            @Override
            public boolean register(String id) {
                if (id.length() > maxChannelLength) {
                    return false;
                }
                return mcChannels.add(id);
            }

            @Override
            public boolean canSend(String id) {
                return mcChannels.contains(id) && serverChannels.contains(id);
            }

            @Override
            public void send(String id, byte[] payload) {
                sent.add(id + ":" + new String(payload, java.nio.charset.StandardCharsets.UTF_8));
            }
        };
    }

    /** Like Minecraft: a shown view stays until closed; closing it calls removed(). */
    @Override
    public dev.lumora.core.screen.ViewManager.Platform views() {
        return new dev.lumora.core.screen.ViewManager.Platform() {
            @Override
            public void show(dev.lumora.api.screen.AddonView view) {
                screenStack.push(view);
            }

            @Override
            public void close(dev.lumora.api.screen.AddonView view) {
                if (screenStack.peek() == view) {
                    screenStack.pop().removed();
                }
            }

            @Override
            public String clipboard() {
                return clipboard;
            }

            @Override
            public void setClipboard(String text) {
                clipboard = text;
            }

            @Override
            public void openAddonSettings(String addonId) {
                openedSettings.add(addonId);
            }
        };
    }

    @Override
    public Optional<dev.lumora.core.social.GameSession> session() {
        return gameSession;
    }

    @Override
    public boolean modLoaded(String modId) {
        return mods.contains(modId);
    }

    @Override
    public dev.lumora.api.game.WorldAccess world() {
        return worldAccess;
    }

    public boolean logged(String part) {
        for (String l : logs) {
            if (l.contains(part)) {
                return true;
            }
        }
        return false;
    }
}
