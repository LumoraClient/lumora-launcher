package dev.lumora.core;

import dev.lumora.api.game.GameContext;
import dev.lumora.api.i18n.Translations;
import dev.lumora.api.resource.Resources;
import dev.lumora.core.input.KeyPlatform;

import java.nio.file.Path;

/**
 * Everything the core needs from Minecraft, implemented once per supported version (client/mc&lt;version&gt;).
 * The core itself never touches Minecraft classes, which keeps it testable without a game.
 */
public interface Platform {
    String minecraftVersion();

    String clientVersion();

    /**
     * Lumora's settings folder ({@code config/} below the Lumora folder: shared by all versions or only this
     * instance's, not necessarily inside the game folder); the core writes below {@code config/lumora/}.
     */
    Path configDir();

    /** Minecraft's game folder (worlds, servers.dat, screenshots); default: the parent of {@link #configDir()}. */
    default Path gameDir() {
        Path config = configDir();
        return config == null ? null : config.toAbsolutePath().getParent();
    }

    /**
     * Folder of addon jars that Lumora loads itself (see {@link dev.lumora.core.addon.JarAddonLoader}), normally
     * {@code .minecraft/addons}; null = none (tests).
     */
    default Path addonsDir() {
        return null;
    }

    /** Parent of the addon jar class loaders: the game's class loader, which sees the Lumora API, Minecraft and mods. */
    default ClassLoader addonParentLoader() {
        return Platform.class.getClassLoader();
    }

    CoreLogger logger();

    GameContext game();

    Translations translations();

    Resources resources();

    KeyPlatform keys();

    /** Opens the HUD editor screen (client thread). */
    void openHudEditor();

    boolean hudEditorOpen();

    /**
     * The signed-in Minecraft account for Lumora Social (name, profile UUID, access token on demand). The token is
     * only read for the official Mojang session join. Empty when the version adapter does not provide it.
     */
    default java.util.Optional<dev.lumora.core.social.GameSession> session() {
        return java.util.Optional.empty();
    }

    /** Opens the Lumora Social screen (client thread); no-op if the adapter has none. */
    default void openSocial() {
    }

    // --- Lumora API 1.1; the defaults keep adapters that do not implement a part yet working ---

    /** Players, line of sight, action bar and click sound for {@code AddonContext.world()}. */
    default dev.lumora.api.game.WorldAccess world() {
        return dev.lumora.api.game.WorldAccess.NONE;
    }

    /** Minecraft networking for addon plugin channels. */
    default dev.lumora.core.net.ChannelRegistry.Platform channels() {
        return dev.lumora.core.net.ChannelRegistry.Platform.NONE;
    }

    /** Minecraft screens for addon menus. */
    default dev.lumora.core.screen.ViewManager.Platform views() {
        return dev.lumora.core.screen.ViewManager.Platform.NONE;
    }

    /** Whether a Fabric mod with this id is installed. */
    default boolean modLoaded(String modId) {
        return false;
    }

    /** Game details for the built-in Lumora modules (potions, items, combo, fullbright, toggle sprint). */
    default dev.lumora.core.extras.GameExtras extras() {
        return dev.lumora.core.extras.GameExtras.NONE;
    }

    /** Starting servers and worlds for the Lumora server and world lists; null if the adapter has none. */
    default dev.lumora.core.browser.BrowserPlatform browsers() {
        return null;
    }

    /**
     * Uploads pixels (ARGB, row by row) as the texture {@code lumora:<path>} so views can draw it with
     * {@code new TextureRef("lumora", path)}; an existing texture with that path is replaced. Render thread only.
     *
     * @return false if this Minecraft version cannot do it (then nothing was registered)
     */
    default boolean uploadTexture(String path, int width, int height, int[] argb) {
        return false;
    }

    /** Frees a texture from {@link #uploadTexture}; unknown paths are ignored. Render thread only. */
    default void releaseTexture(String path) {
    }
}
