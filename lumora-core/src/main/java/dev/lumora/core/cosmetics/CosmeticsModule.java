package dev.lumora.core.cosmetics;

import dev.lumora.api.addon.AddonContext;
import dev.lumora.api.addon.LumoraAddon;
import dev.lumora.api.config.BooleanSetting;
import dev.lumora.api.config.IntegerSetting;
import dev.lumora.api.config.SettingsGroup;
import dev.lumora.api.event.client.ClientStoppingEvent;
import dev.lumora.api.event.client.ClientTickEvent;
import dev.lumora.api.event.client.WorldLeaveEvent;
import dev.lumora.api.i18n.Translations;
import dev.lumora.api.input.Keybind;
import dev.lumora.api.input.Keys;
import dev.lumora.core.Platform;
import dev.lumora.core.cosmetics.ui.CosmeticsView;
import dev.lumora.core.cosmetics.ui.EmoteWheelView;
import dev.lumora.core.cosmetics.ui.EmotesView;
import dev.lumora.core.cosmetics.ui.UiHost;
import dev.lumora.core.extras.GameExtras;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Built-in module "Cosmetics & Emotes": cosmetics worn on the player model (wings, capes, hats, accessories, effects,
 * companions; GeckoLib models with animations) and emotes (whole-body animations without any item), with a menu,
 * an emote menu, an emote wheel, keybinds and a live preview. Everything is local: other players do not see it in
 * this version, nothing is sent.
 */
public final class CosmeticsModule implements LumoraAddon {
    public static final String ID = "lumora-cosmetics";
    static final String K = "lumora.cosmetics.";
    /** Keybinds that play wheel slots 1..4 directly. */
    static final int SLOT_KEYS = 4;
    static final List<String> DEFAULT_WHEEL = List.of("wave", "groove", "clap", "point", "sit", "celebrate", "facepalm", "spin",
            "laugh", "shrug");

    private final Platform platform;
    private final java.util.function.Supplier<dev.lumora.core.social.LumoraSocial> social;
    BooleanSetting showOwn;
    BooleanSetting showOthers;
    dev.lumora.api.config.ChoiceSetting visibility;
    IntegerSetting wheelPages;
    private Keybind wheelKey;
    private CosmeticsSync sync;
    BooleanSetting animate;
    BooleanSetting stopOnMove;
    BooleanSetting emoteCamera;
    IntegerSetting wheelSlots;
    private Translations translations;
    private Cosmetics cosmetics;
    private CosmeticRegistry registry;
    private CosmeticsState state;
    private final List<Keybind> slotKeys = new ArrayList<>();
    /** Perspective before an emote switched to third person, -1 if none. */
    private int previousPerspective = -1;
    private AddonContext context;
    private static volatile CosmeticsModule active;

    public CosmeticsModule(Platform platform) {
        this(platform, () -> null);
    }

    public CosmeticsModule(Platform platform, java.util.function.Supplier<dev.lumora.core.social.LumoraSocial> social) {
        this.platform = platform;
        this.social = social == null ? () -> null : social;
    }

    @Override
    public void onInitialize(AddonContext context) {
        this.context = context;
        translations = context.translations();
        SettingsGroup s = context.configuration().settings();
        s.button("open_menu", this::openMenu).name(K + "setting.open_menu").section(K + "section.general");
        s.button("open_emotes", this::openEmotes).name(K + "setting.open_emotes");
        showOwn = s.toggle("show_own", true).name(K + "setting.show_own").description(K + "setting.show_own.description");
        animate = s.toggle("animate", true).name(K + "setting.animate").description(K + "setting.animate.description");
        stopOnMove = s.toggle("stop_on_move", true).name(K + "setting.stop_on_move").description(K + "setting.stop_on_move.description")
                .section(K + "section.emotes");
        emoteCamera = s.toggle("emote_camera", true).name(K + "setting.emote_camera").description(K + "setting.emote_camera.description");
        wheelSlots = s.slider("wheel_slots", 6, 4, CosmeticsState.MAX_SLOTS).name(K + "setting.wheel_slots")
                .description(K + "setting.wheel_slots.description");
        wheelPages = s.slider("wheel_pages", 3, 1, CosmeticsState.MAX_PAGES).name(K + "setting.wheel_pages")
                .description(K + "setting.wheel_pages.description");
        showOthers = s.toggle("show_others", true).name(K + "setting.show_others").description(K + "setting.show_others.description")
                .section(K + "section.multiplayer");
        visibility = s.selection("visibility", "all", "all", "friends", "none").name(K + "setting.visibility")
                .description(K + "setting.visibility.description");
        showOthers.onChange(v -> applyOptions());
        visibility.onChange(v -> applyOptions());
        showOwn.onChange(v -> applyOptions());
        animate.onChange(v -> applyOptions());
        stopOnMove.onChange(v -> applyOptions());
        emoteCamera.onChange(v -> {
            if (!v) {
                restoreCamera();
            }
        });

        Path config = platform == null ? null : platform.configDir();
        Path lumora = config == null ? null : config.resolve("lumora");
        registry = new CosmeticRegistry(msg -> context.logger().warn("Cosmetics: " + msg));
        registry.loadBuiltIn();
        registry.loadCustom(lumora);
        state = new CosmeticsState(lumora == null ? null : lumora.resolve("cosmetics.json"));
        state.load();
        state.prune(registry);
        List<String> wheel = new ArrayList<>();
        for (String id : DEFAULT_WHEEL) {
            if (registry.emote(id) != null) {
                wheel.add(id);
            }
        }
        state.fillEmptySlots(wheel);
        cosmetics = new Cosmetics(registry, state);
        cosmetics.uploader((key, w, h, argb) -> {
            String path = "cosmetics/custom/" + key;
            return platform != null && platform.uploadTexture(path, w, h, argb) ? "lumora:" + path : null;
        });
        sync = new CosmeticsSync(cosmetics, () -> {
            dev.lumora.core.social.LumoraSocial ls = social.get();
            return ls == null ? null : ls.client();
        }, () -> platform == null ? null : platform.world());
        cosmetics.remote(sync);
        cosmetics.emoteListener(sync::ownEmote);
        cosmetics.events(e -> {
            if ("sound".equals(e.kind()) && platform != null) {
                extras().playSoundEvent(e.value(), 0.6f);
            }
        });
        applyOptions();
    }

    @Override
    public void onEnable(AddonContext context) {
        active = this;
        Cosmetics.set(cosmetics);
        context.keybinds().register("menu", K + "key.menu", Keys.J, this::openMenu);
        wheelKey = context.keybinds().register("wheel", K + "key.wheel", Keys.R, this::openWheel);
        context.keybinds().register("emotes", K + "key.emotes", Keys.UNBOUND, this::openEmotes);
        context.keybinds().register("stop", K + "key.stop", Keys.UNBOUND, () -> cosmetics.stopEmote());
        slotKeys.clear();
        for (int i = 0; i < SLOT_KEYS; i++) {
            int slot = i;
            slotKeys.add(context.keybinds().register("slot_" + (i + 1), K + "key.slot." + (i + 1), Keys.UNBOUND, () -> playSlot(slot)));
        }
        context.events().subscribe(ClientTickEvent.class, e -> tick());
        context.events().subscribe(WorldLeaveEvent.class, e -> {
            cosmetics.reset();
            sync.reset();
            restoreCamera();
        });
        context.events().subscribe(ClientStoppingEvent.class, e -> restoreCamera());
    }

    @Override
    public void onDisable(AddonContext context) {
        active = null;
        cosmetics.stopEmote();
        cosmetics.reset();
        restoreCamera();
        Cosmetics.set(null);
    }

    private void applyOptions() {
        if (cosmetics != null) {
            cosmetics.options(showOwn.get(), showOthers == null || showOthers.get(), animate.get(), stopOnMove.get());
            if (sync != null && visibility != null) {
                sync.configure(true, visibility.get());
            }
        }
    }

    private GameExtras extras() {
        return platform.extras();
    }

    /** Once per client tick: third-person camera while the own emote plays. */
    void tick() {
        if (platform == null) {
            return;
        }
        sync.tick();
        GameExtras x = extras();
        boolean emoting = cosmetics.currentEmote() != null;
        if (emoting && emoteCamera.get() && previousPerspective < 0 && x.perspective() == 0) {
            previousPerspective = 0;
            x.setPerspective(1);
        } else if (!emoting && previousPerspective >= 0) {
            restoreCamera();
        }
    }

    private void restoreCamera() {
        if (previousPerspective >= 0 && platform != null) {
            extras().setPerspective(previousPerspective);
        }
        previousPerspective = -1;
    }

    private void playSlot(int slot) {
        EmoteDefinition e = registry.emote(state.slot(slot));
        if (e != null) {
            cosmetics.playEmote(e);
        }
    }

    // --- views

    private UiHost host() {
        return new UiHost() {
            @Override
            public String tr(String key, Object... args) {
                return translations == null ? key : translations.translate(key, args);
            }

            @Override
            public String clipboard() {
                return platform == null ? "" : platform.views().clipboard();
            }

            @Override
            public void close(dev.lumora.api.screen.AddonView view) {
                if (platform != null) {
                    platform.views().close(view);
                }
            }

            @Override
            public void show(dev.lumora.api.screen.AddonView view) {
                if (platform != null) {
                    platform.views().show(view);
                }
            }

            @Override
            public boolean drawPreview(dev.lumora.api.render.HudGraphics g, int x, int y, int w, int h, float yaw, float pitch, float zoom) {
                return platform != null && extras().drawPlayerPreview(g, x, y, w, h, yaw, pitch, zoom);
            }

            @Override
            public boolean inWorld() {
                return platform != null && extras().playerRotation() != null;
            }

            @Override
            public int wheelSlots() {
                return wheelSlots.get();
            }

            @Override
            public int wheelPages() {
                return wheelPages.get();
            }

            @Override
            public boolean wheelKeyDown() {
                return platform != null && wheelKey != null && wheelKey.key() != Keys.UNBOUND && platform.keys().isDown(wheelKey.key());
            }

            @Override
            public void openSettings() {
                if (platform != null) {
                    platform.views().openAddonSettings(ID);
                }
            }
        };
    }

    /** The running module, {@code null} while switched off. */
    public static CosmeticsModule active() {
        return active;
    }

    /** Opens the cosmetics menu. */
    public void openMenu() {
        if (platform != null) {
            platform.views().show(new CosmeticsView(cosmetics, host()));
        }
    }

    /** Opens the emote menu. */
    public void openEmotes() {
        if (platform != null) {
            platform.views().show(new EmotesView(cosmetics, host()));
        }
    }

    /** Opens the emote wheel editor (all pages; a slot opens the emote list to swap). */
    public void openWheelEditor() {
        if (platform != null) {
            platform.views().show(new EmoteWheelView(cosmetics, host(), true, 0));
        }
    }

    /** Opens the emote list for one wheel slot. */
    public void openEmotePicker(int page, int slot) {
        if (platform != null) {
            platform.views().show(new dev.lumora.core.cosmetics.ui.EmotePickerView(cosmetics, host(), page, slot));
        }
    }

    /** Opens the emote wheel (only in a world). */
    public void openWheel() {
        if (platform != null && extras().playerRotation() != null) {
            platform.views().show(new EmoteWheelView(cosmetics, host()));
        }
    }
}
