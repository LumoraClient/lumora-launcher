package dev.lumora.core.addon;

import dev.lumora.api.Lumora;
import dev.lumora.api.addon.AddonConfiguration;
import dev.lumora.api.addon.AddonContext;
import dev.lumora.api.addon.AddonDescriptor;
import dev.lumora.api.addon.AddonLogger;
import dev.lumora.api.addon.AddonPermission;
import dev.lumora.api.addon.AddonPermissionException;
import dev.lumora.api.addon.AddonState;
import dev.lumora.api.event.EventBus;
import dev.lumora.api.game.GameContext;
import dev.lumora.api.hud.WidgetRegistry;
import dev.lumora.api.i18n.Translations;
import dev.lumora.api.input.KeybindRegistry;
import dev.lumora.api.notify.Notifications;
import dev.lumora.api.resource.Resources;
import dev.lumora.api.task.TaskScheduler;
import dev.lumora.core.CoreLogger;
import dev.lumora.core.LumoraCore;
import dev.lumora.core.event.OwnedEventBus;

/**
 * The context handed to one addon. Service views are created once; each registration first checks that the addon
 * is being enabled or enabled, then that it declared the permission.
 */
final class AddonContextImpl implements AddonContext {
    private final AddonRecord record;
    private final LumoraCore core;
    private final AddonLogger logger;
    private final EventBus events;
    private final WidgetRegistry widgets;
    private final KeybindRegistry keybinds;
    private final TaskScheduler tasks;
    private final Notifications notifications;
    private final dev.lumora.api.net.PluginChannels channels;
    private final dev.lumora.api.render.NameTags nameTags;
    private final dev.lumora.api.screen.AddonScreens screens;

    AddonContextImpl(AddonRecord record, LumoraCore core) {
        this.record = record;
        this.core = core;
        String id = record.descriptor.id();
        this.logger = logger(core.logger(), id);
        Runnable active = this::requireActive;
        this.events = new OwnedEventBus(core.eventBus(), id, record.tracker, active);
        this.widgets = core.widgetRegistry().scoped(id, record.tracker, () -> {
            requireActive();
            require(AddonPermission.HUD);
        });
        this.keybinds = core.keybindManager().scoped(id, record.tracker, () -> {
            requireActive();
            require(AddonPermission.KEYBINDS);
        });
        this.tasks = core.taskService().scoped(id, record.tracker, () -> {
            requireActive();
            require(AddonPermission.TASKS);
        });
        this.notifications = n -> {
            requireActive();
            require(AddonPermission.NOTIFICATIONS);
            core.notificationCenter().show(n);
        };
        this.channels = core.channelRegistry().scoped(id, record.tracker, () -> {
            requireActive();
            require(AddonPermission.NETWORK);
        });
        this.nameTags = core.nameTags().scoped(id, record.tracker, () -> {
            requireActive();
            require(AddonPermission.NAMETAGS);
        });
        this.screens = core.views().scoped(id, record.tracker, () -> {
            requireActive();
            require(AddonPermission.SCREENS);
        });
    }

    private static AddonLogger logger(CoreLogger log, String id) {
        String p = "[" + id + "] ";
        return new AddonLogger() {
            @Override
            public void info(String message) {
                log.info(p + message);
            }

            @Override
            public void warn(String message) {
                log.warn(p + message);
            }

            @Override
            public void warn(String message, Throwable error) {
                log.warn(p + message, error);
            }

            @Override
            public void error(String message, Throwable error) {
                log.error(p + message, error);
            }
        };
    }

    private void requireActive() {
        AddonState s = record.state;
        if (s != AddonState.ENABLING && s != AddonState.ENABLED) {
            throw new IllegalStateException("Addon " + record.descriptor.id() + " is " + s + "; registrations are only allowed in/after onEnable");
        }
    }

    private void require(AddonPermission p) {
        if (!record.descriptor.permissions().contains(p) && !record.descriptor.builtIn()) {
            throw new AddonPermissionException(record.descriptor.id(), p);
        }
    }

    @Override
    public AddonDescriptor descriptor() {
        return record.descriptor;
    }

    @Override
    public AddonState state() {
        return record.state;
    }

    @Override
    public AddonLogger logger() {
        return logger;
    }

    @Override
    public Lumora lumora() {
        return core;
    }

    @Override
    public EventBus events() {
        return events;
    }

    @Override
    public WidgetRegistry widgets() {
        require(AddonPermission.HUD);
        return widgets;
    }

    @Override
    public KeybindRegistry keybinds() {
        require(AddonPermission.KEYBINDS);
        return keybinds;
    }

    @Override
    public TaskScheduler tasks() {
        require(AddonPermission.TASKS);
        return tasks;
    }

    @Override
    public Notifications notifications() {
        require(AddonPermission.NOTIFICATIONS);
        return notifications;
    }

    @Override
    public GameContext game() {
        require(AddonPermission.GAME);
        return core.game();
    }

    @Override
    public Translations translations() {
        return core.translations();
    }

    @Override
    public Resources resources() {
        return core.resources();
    }

    @Override
    public AddonConfiguration configuration() {
        return record.configuration;
    }

    @Override
    public boolean hasPermission(AddonPermission permission) {
        return record.descriptor.builtIn() || record.descriptor.permissions().contains(permission);
    }

    @Override
    public dev.lumora.api.game.WorldAccess world() {
        require(AddonPermission.PLAYERS);
        return core.platform().world();
    }

    @Override
    public dev.lumora.api.session.SessionService session() {
        require(AddonPermission.SESSION);
        return core.session();
    }

    @Override
    public dev.lumora.api.net.PluginChannels channels() {
        require(AddonPermission.NETWORK);
        return channels;
    }

    @Override
    public dev.lumora.api.screen.AddonScreens screens() {
        require(AddonPermission.SCREENS);
        return screens;
    }

    @Override
    public dev.lumora.api.render.NameTags nameTags() {
        require(AddonPermission.NAMETAGS);
        return nameTags;
    }

    @Override
    public java.nio.file.Path configDirectory() {
        return core.platform().configDir();
    }

    @Override
    public boolean modLoaded(String modId) {
        return core.platform().modLoaded(modId);
    }
}
