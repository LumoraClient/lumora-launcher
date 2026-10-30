package dev.lumora.core;

import dev.lumora.api.Lumora;
import dev.lumora.api.LumoraAPI;
import dev.lumora.api.Version;
import dev.lumora.api.addon.AddonDescriptor;
import dev.lumora.api.addon.AddonInfo;
import dev.lumora.api.addon.AddonPermission;
import dev.lumora.api.addon.LumoraAddon;
import dev.lumora.api.event.EventBus;
import dev.lumora.api.event.client.ClientStartedEvent;
import dev.lumora.api.event.client.ClientStoppingEvent;
import dev.lumora.api.event.client.ClientTickEvent;
import dev.lumora.api.event.client.PlayerChangedEvent;
import dev.lumora.api.event.client.ResourcesReloadedEvent;
import dev.lumora.api.event.client.ScreenChangedEvent;
import dev.lumora.api.event.client.WorldJoinEvent;
import dev.lumora.api.event.client.WorldLeaveEvent;
import dev.lumora.api.game.ConnectionInfo;
import dev.lumora.api.game.GameContext;
import dev.lumora.api.hud.HudEditor;
import dev.lumora.api.hud.RenderContext;
import dev.lumora.api.hud.WidgetInfo;
import dev.lumora.api.i18n.Translations;
import dev.lumora.api.notify.Notifications;
import dev.lumora.api.render.HudGraphics;
import dev.lumora.api.resource.Resources;
import dev.lumora.core.addon.AddonCandidate;
import dev.lumora.core.addon.AddonManager;
import dev.lumora.core.event.SimpleEventBus;
import dev.lumora.core.hud.HudEditorModel;
import dev.lumora.core.hud.HudRenderer;
import dev.lumora.core.hud.WidgetRegistryImpl;
import dev.lumora.core.input.KeybindManager;
import dev.lumora.core.notify.NotificationCenter;
import dev.lumora.core.task.TaskService;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.function.LongSupplier;

/**
 * The Lumora client framework without Minecraft: owns all services and is driven by the version adapter through
 * the hook methods ({@link #tick()}, {@link #renderHud}, {@link #worldJoin}, ...). Client thread unless noted.
 * <p>
 * Files (below {@code config/lumora/}): {@code hud-layout.json}, {@code keybinds.json}, {@code addons.json}
 * (switched-off addons) and {@code addons/<id>.json} (addon settings).
 */
public final class LumoraCore implements Lumora {
    private final Platform platform;
    private final CoreLogger log;
    private final TaskService tasks;
    private final SimpleEventBus events;
    private final NotificationCenter notifications;
    private final KeybindManager keybinds;
    private final WidgetRegistryImpl widgets;
    private final HudRenderer renderer;
    private final AddonManager addons;
    private final HudEditor hudEditor;
    private final dev.lumora.core.net.ChannelRegistry channels;
    private final dev.lumora.core.render.NameTagRegistry nameTags;
    private final dev.lumora.core.screen.ViewManager views;
    private final dev.lumora.core.session.SessionServiceImpl session;
    private HudEditorModel editor;
    private dev.lumora.core.social.LumoraSocial social;
    private boolean started;
    private boolean stopped;

    public LumoraCore(Platform platform) {
        this(platform, System::currentTimeMillis);
    }

    public LumoraCore(Platform platform, LongSupplier clock) {
        this.platform = platform;
        this.log = platform.logger();
        Path dir = platform.configDir().resolve("lumora");
        this.tasks = new TaskService(log);
        this.events = new SimpleEventBus(log);
        this.notifications = new NotificationCenter(clock);
        this.events.onListenerRemoved((owner, error) -> notifications.warning(
                platform.translations().translateOr("lumora.addon.listener.removed", "Addon listener removed"),
                owner + ": " + error.getClass().getSimpleName()));
        this.keybinds = new KeybindManager(platform.keys(), platform.translations(), log, dir.resolve("keybinds.json"),
                tasks.io(), tasks::currentTick);
        this.widgets = new WidgetRegistryImpl(events, platform.translations(), log, notifications, dir.resolve("hud-layout.json"),
                platform.configDir().resolve("lumora-hud.json"), tasks.io(), tasks::onClientThread, tasks::currentTick);
        this.renderer = new HudRenderer(widgets, events);
        this.channels = new dev.lumora.core.net.ChannelRegistry(platform.channels(), log);
        this.nameTags = new dev.lumora.core.render.NameTagRegistry(log);
        this.views = new dev.lumora.core.screen.ViewManager(platform.views(), log);
        this.session = new dev.lumora.core.session.SessionServiceImpl(platform::session);
        this.addons = new AddonManager(this, dir.resolve("addons.json"), dir.resolve("addons"));
        this.hudEditor = new HudEditor() {
            @Override
            public void open() {
                platform.openHudEditor();
            }

            @Override
            public boolean isOpen() {
                return platform.hudEditorOpen();
            }

            @Override
            public String activeProfile() {
                return widgets.activeProfile();
            }

            @Override
            public List<String> profiles() {
                return widgets.layout().profileIds();
            }

            @Override
            public boolean activateProfile(String profile) {
                return widgets.activateProfile(profile);
            }
        };
    }

    /** Descriptor for an addon that ships inside the Lumora client jar (always all permissions). */
    public static AddonDescriptor builtInDescriptor(String id, String name, String description, Class<? extends LumoraAddon> entry) {
        return new AddonDescriptor(id, name, Version.parse(LumoraAPI.API_VERSION), description, List.of("Lumora"),
                Version.parse(LumoraAPI.API_VERSION), List.of("*"), entry.getName(), List.of(),
                EnumSet.allOf(AddonPermission.class), true, "built-in");
    }

    /** Makes this instance available through {@link LumoraAPI#get()} (once per process). */
    public void installGlobal() {
        LumoraAPI.install(this);
    }

    /** Validates, initializes and enables the addons (built-in ones included in the list). */
    public void loadAddons(List<AddonCandidate> candidates) {
        // the core's own modules (crosshair, toggle sprint, fullbright, chat, tab list, info widgets) come first
        List<AddonCandidate> all = new java.util.ArrayList<>(candidates);
        if (candidates.stream().noneMatch(c -> c.descriptor() != null && c.descriptor().id().equals("lumora-info"))) {
            all.addAll(Math.min(1, all.size()), dev.lumora.core.extras.Extras.candidates(platform));
        }
        addons.offByDefault(dev.lumora.core.extras.Extras.OFF_BY_DEFAULT);
        addons.load(all);
        addons.initializeAll();
        addons.enableAll();
    }

    // --- hooks called by the version adapter

    public void clientStarted() {
        if (!started) {
            started = true;
            keybinds.register("lumora", "social", "lumora.key.social", dev.lumora.api.input.Keys.U, platform::openSocial);
            social = dev.lumora.core.social.LumoraSocial.start(this, platform);
            events.post(new ClientStartedEvent());
        }
    }

    public void tick() {
        tasks.tick();
        keybinds.tick();
        if (social != null) {
            social.tick();
        }
        widgets.tick();
        addons.tick();
        if (events.hasListeners(ClientTickEvent.class)) {
            events.post(new ClientTickEvent(tasks.currentTick()));
        }
    }

    public void worldJoin(ConnectionInfo connection) {
        events.post(new WorldJoinEvent(connection));
    }

    public void worldLeave() {
        events.post(new WorldLeaveEvent());
    }

    public void playerChanged(PlayerChangedEvent.Reason reason, String dimension) {
        events.post(new PlayerChangedEvent(reason, dimension));
    }

    public void screenChanged(String previous, String current) {
        events.post(new ScreenChangedEvent(previous, current));
    }

    public void resourcesReloaded() {
        events.post(new ResourcesReloadedEvent());
    }

    /** In-game HUD: widgets, then notifications on top. */
    public void renderHud(HudGraphics g, RenderContext ctx) {
        renderer.render(g, ctx);
    }

    /** Notifications only (also drawn over menus by the adapter). */
    public void renderNotifications(HudGraphics g, int screenWidth) {
        notifications.render(g, screenWidth);
    }

    public boolean hudClick(double x, double y, int button) {
        return renderer.click(x, y, button);
    }

    /** Game is closing: event, addons off, files written, threads stopped. Safe to call twice. */
    public void stopping() {
        if (stopped) {
            return;
        }
        stopped = true;
        events.post(new ClientStoppingEvent());
        if (editor != null && !editor.isClosed()) {
            editor.close(true);
        }
        if (social != null) {
            social.stop();
        }
        addons.shutdown();
        if (widgets.dirty()) {
            widgets.saveNow();
        }
        keybinds.flush();
        tasks.shutdown();
    }

    // --- editor

    /** Starts an editor session (the screen calls {@link HudEditorModel#close} when it closes). */
    public HudEditorModel openEditor() {
        if (editor != null && !editor.isClosed()) {
            editor.close(true);
        }
        editor = new HudEditorModel(widgets, renderer, events);
        return editor;
    }

    // --- internals for the adapter and the addon context

    public CoreLogger logger() {
        return log;
    }

    public SimpleEventBus eventBus() {
        return events;
    }

    public WidgetRegistryImpl widgetRegistry() {
        return widgets;
    }

    public HudRenderer renderer() {
        return renderer;
    }

    public KeybindManager keybindManager() {
        return keybinds;
    }

    public TaskService taskService() {
        return tasks;
    }

    /** Lumora Social (friends, groups, chat); null before the client started. */
    public dev.lumora.core.social.LumoraSocial social() {
        return social;
    }

    public NotificationCenter notificationCenter() {
        return notifications;
    }

    public AddonManager addonManager() {
        return addons;
    }

    /** Plugin channels; the version adapter delivers received payloads here. */
    public dev.lumora.core.net.ChannelRegistry channelRegistry() {
        return channels;
    }

    /** Name tag icons; asked by the version adapter's name tag and tab list mixins. */
    public dev.lumora.core.render.NameTagRegistry nameTags() {
        return nameTags;
    }

    public dev.lumora.core.screen.ViewManager views() {
        return views;
    }

    public dev.lumora.core.session.SessionServiceImpl session() {
        return session;
    }

    public Platform platform() {
        return platform;
    }

    // --- Lumora

    @Override
    public String apiVersion() {
        return LumoraAPI.API_VERSION;
    }

    @Override
    public String clientVersion() {
        return platform.clientVersion();
    }

    @Override
    public String minecraftVersion() {
        return platform.minecraftVersion();
    }

    @Override
    public EventBus events() {
        return events;
    }

    @Override
    public GameContext game() {
        return platform.game();
    }

    @Override
    public Translations translations() {
        return platform.translations();
    }

    @Override
    public Resources resources() {
        return platform.resources();
    }

    @Override
    public Notifications notifications() {
        return notifications;
    }

    @Override
    public List<AddonInfo> addons() {
        return addons.addons();
    }

    @Override
    public Optional<AddonInfo> addon(String id) {
        return addons.record(id).map(r -> r);
    }

    @Override
    public List<WidgetInfo> widgets() {
        return widgets.all();
    }

    @Override
    public HudEditor hudEditor() {
        return hudEditor;
    }
}
