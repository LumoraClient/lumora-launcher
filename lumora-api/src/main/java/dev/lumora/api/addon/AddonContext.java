package dev.lumora.api.addon;

import dev.lumora.api.Lumora;
import dev.lumora.api.event.EventBus;
import dev.lumora.api.game.GameContext;
import dev.lumora.api.hud.WidgetRegistry;
import dev.lumora.api.i18n.Translations;
import dev.lumora.api.input.KeybindRegistry;
import dev.lumora.api.notify.Notifications;
import dev.lumora.api.resource.Resources;
import dev.lumora.api.task.TaskScheduler;

/**
 * What an addon may use. Every registration made through this context belongs to the addon and is removed when
 * it is disabled. Services that need a permission throw {@link AddonPermissionException} if the permission is not
 * declared in {@code lumora-addon.json}; registering while the addon is not being enabled/enabled throws
 * {@link IllegalStateException}.
 * <p>
 * Note: permissions describe and limit what the API hands out. They are not a sandbox - an addon is ordinary Java
 * code running in the game process with the same rights as any other mod.
 */
public interface AddonContext {
    AddonDescriptor descriptor();

    /** Current lifecycle state of this addon. */
    AddonState state();

    AddonLogger logger();

    /** Read-only access to the client (version, other addons, widgets). */
    Lumora lumora();

    /** Event bus; listeners are owned by this addon. */
    EventBus events();

    /** HUD widgets ({@link AddonPermission#HUD}). */
    WidgetRegistry widgets();

    /** Lumora keybinds ({@link AddonPermission#KEYBINDS}). */
    KeybindRegistry keybinds();

    /** Timed and background work ({@link AddonPermission#TASKS}). */
    TaskScheduler tasks();

    /** Notifications ({@link AddonPermission#NOTIFICATIONS}). */
    Notifications notifications();

    /** Player, world and connection information ({@link AddonPermission#GAME}). */
    GameContext game();

    Translations translations();

    Resources resources();

    /** Addon-wide settings, stored in {@code config/lumora/addons/<id>.json} and editable in the Lumora hub. */
    AddonConfiguration configuration();

    boolean hasPermission(AddonPermission permission);

    // --- since 1.1.0; the defaults only exist so older implementations still compile ---

    /** Players, line of sight and in-game messages (permission {@code players}). @since 1.1.0 */
    default dev.lumora.api.game.WorldAccess world() {
        throw new UnsupportedOperationException("requires Lumora API 1.1");
    }

    /** Account profile and Mojang session join (permission {@code session}). @since 1.1.0 */
    default dev.lumora.api.session.SessionService session() {
        throw new UnsupportedOperationException("requires Lumora API 1.1");
    }

    /** Plugin channels (permission {@code network}). @since 1.1.0 */
    default dev.lumora.api.net.PluginChannels channels() {
        throw new UnsupportedOperationException("requires Lumora API 1.1");
    }

    /** Full-screen addon menus (permission {@code screens}). @since 1.1.0 */
    default dev.lumora.api.screen.AddonScreens screens() {
        throw new UnsupportedOperationException("requires Lumora API 1.1");
    }

    /** Icons next to player names (permission {@code nametags}). @since 1.1.0 */
    default dev.lumora.api.render.NameTags nameTags() {
        throw new UnsupportedOperationException("requires Lumora API 1.1");
    }

    /** The game's {@code config} folder (for the addon's own files). @since 1.1.0 */
    default java.nio.file.Path configDirectory() {
        throw new UnsupportedOperationException("requires Lumora API 1.1");
    }

    /** Whether a Fabric mod with this id is installed. @since 1.1.0 */
    default boolean modLoaded(String modId) {
        throw new UnsupportedOperationException("requires Lumora API 1.1");
    }
}
