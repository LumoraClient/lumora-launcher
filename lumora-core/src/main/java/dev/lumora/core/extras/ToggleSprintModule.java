package dev.lumora.core.extras;

import dev.lumora.api.addon.AddonContext;
import dev.lumora.api.addon.LumoraAddon;
import dev.lumora.api.config.BooleanSetting;
import dev.lumora.api.config.DecimalSetting;
import dev.lumora.api.config.IntegerSetting;
import dev.lumora.api.config.SettingsGroup;
import dev.lumora.api.event.client.ClientTickEvent;
import dev.lumora.api.event.client.WorldLeaveEvent;
import dev.lumora.api.hud.RenderContext;
import dev.lumora.api.hud.WidgetCategory;
import dev.lumora.api.hud.WidgetPlacement;
import dev.lumora.core.Platform;
import dev.lumora.core.input.KeyPlatform;

import java.util.List;

/**
 * Built-in module "Toggle Sprint": a press of the vanilla sprint (or sneak) key switches it on until the next press,
 * so the key does not have to be held. Shows the state as a HUD widget. Only the player's own movement keys are
 * held down, exactly as if the player kept pressing them.
 * <p>
 * Flying (creative or spectator, wherever the game lets the player fly): "Fly Speed" multiplies the flying speed
 * where Minecraft adds it to the movement (horizontal and up/down; the stored ability value stays as the server sent
 * it), "Fly Momentum" is how much of the horizontal speed is kept per tick after letting go of the movement keys
 * (100 % as usual, 0 % stops at once).
 */
public final class ToggleSprintModule implements LumoraAddon {
    static final String K = "lumora.extras.togglesprint.";
    /** Highest fly speed: more is corrected by servers ("moved too quickly"). */
    static final double MAX_FLY_SPEED = 5.0;

    private static volatile ToggleSprintModule active;

    private final Platform platform;
    BooleanSetting sprint;
    BooleanSetting sneak;
    DecimalSetting flySpeed;
    IntegerSetting flyMomentum;
    private final Toggle sprintToggle = new Toggle();
    private final Toggle sneakToggle = new Toggle();

    public ToggleSprintModule(Platform platform) {
        this.platform = platform;
    }

    /** Edge detection of a physical key: a press flips the state. */
    static final class Toggle {
        boolean on;
        private boolean wasDown;

        /** @return the new state */
        boolean update(boolean down) {
            if (down && !wasDown) {
                on = !on;
            }
            wasDown = down;
            return on;
        }

        void reset() {
            on = false;
            wasDown = false;
        }
    }

    @Override
    public void onInitialize(AddonContext context) {
        settings(context.configuration().settings());
    }

    void settings(SettingsGroup s) {
        sprint = s.toggle("sprint", true).name("lumora.extras.togglesprint.sprint").section("lumora.extras.togglesprint.section.general");
        sneak = s.toggle("sneak", false).name("lumora.extras.togglesprint.sneak");
        flySpeed = s.slider("fly_speed", 1.0, 1.0, MAX_FLY_SPEED, 0.25).unit("x").name(K + "fly_speed")
                .description(K + "fly_speed.description").section(K + "section.fly");
        flyMomentum = s.slider("fly_momentum", 100, 0, 100).unit(" %").name(K + "fly_momentum")
                .description(K + "fly_momentum.description");
    }

    /**
     * Factor for the own player's flying speed: 1 while the module is off. Read by the version mixins where Minecraft
     * adds the flying speed to the movement.
     */
    public static float flySpeed() {
        ToggleSprintModule m = active;
        return m == null || m.flySpeed == null ? 1f : (float) Math.max(1.0, Math.min(MAX_FLY_SPEED, m.flySpeed.get()));
    }

    /** Publishes this module for the fly speed mixins (on) or takes it back (off). */
    void setActive(boolean on) {
        if (on) {
            active = this;
        } else if (active == this) {
            active = null;
        }
    }

    /** Share of the horizontal flying speed kept per tick without movement keys, 0..1 (1 = as usual). */
    float flyMomentum() {
        return flyMomentum == null ? 1f : Math.max(0, Math.min(100, flyMomentum.get())) / 100f;
    }

    @Override
    public void onEnable(AddonContext context) {
        setActive(true);
        context.events().subscribe(ClientTickEvent.class, e -> tick());
        context.events().subscribe(WorldLeaveEvent.class, e -> release());
        context.widgets().register(new StatusWidget());
    }

    @Override
    public void onDisable(AddonContext context) {
        setActive(false);
        release();
    }

    private void release() {
        GameExtras x = platform.extras();
        if (sprintToggle.on) {
            x.holdSprint(false);
        }
        if (sneakToggle.on) {
            x.holdSneak(false);
        }
        sprintToggle.reset();
        sneakToggle.reset();
    }

    private void tick() {
        if (!platform.game().inWorld()) {
            return;
        }
        KeyPlatform keys = platform.keys();
        GameExtras x = platform.extras();
        float keep = flyMomentum();
        if (keep < 1f) {
            x.flyMomentum(keep);
        }
        // keys only count while playing (not while typing in chat or a menu is open)
        boolean playing = keys.acceptsKeybinds();
        if (sprint.get()) {
            int k = x.sprintKey();
            boolean before = sprintToggle.on;
            boolean on = sprintToggle.update(playing && k >= 0 && keys.isDown(k));
            if (on || before) {
                x.holdSprint(on);
            }
        } else if (sprintToggle.on) {
            x.holdSprint(false);
            sprintToggle.reset();
        }
        if (sneak.get()) {
            int k = x.sneakKey();
            boolean before = sneakToggle.on;
            boolean on = sneakToggle.update(playing && k >= 0 && keys.isDown(k));
            if (on || before) {
                x.holdSneak(on);
            }
        } else if (sneakToggle.on) {
            x.holdSneak(false);
            sneakToggle.reset();
        }
    }

    /** "[Sprinten (umgeschaltet)]" like the reference clients. */
    final class StatusWidget extends Panels.Text {
        StatusWidget() {
            super("togglesprint");
        }

        @Override
        boolean inWorld() {
            return platform.game().inWorld();
        }

        @Override
        public WidgetPlacement defaultPlacement() {
            return WidgetPlacement.at(0.005f, 0.97f).enabledByDefault();
        }

        @Override
        public String category() {
            return WidgetCategory.PLAYER;
        }

        @Override
        List<String[]> lines(boolean preview) {
            String t;
            if (preview) {
                t = platform.translations().translateOr("lumora.extras.togglesprint.sprinting", "Sprinten") + " ("
                        + platform.translations().translateOr("lumora.extras.togglesprint.toggled", "umgeschaltet") + ")";
            } else if (sneakToggle.on) {
                t = platform.translations().translateOr("lumora.extras.togglesprint.sneaking", "Schleichen") + " ("
                        + platform.translations().translateOr("lumora.extras.togglesprint.toggled", "umgeschaltet") + ")";
            } else if (sprintToggle.on) {
                t = platform.translations().translateOr("lumora.extras.togglesprint.sprinting", "Sprinten") + " ("
                        + platform.translations().translateOr("lumora.extras.togglesprint.toggled", "umgeschaltet") + ")";
            } else {
                return List.of();
            }
            return List.<String[]>of(new String[]{"", "[" + t + "]"});
        }

        @Override
        public boolean isVisible(RenderContext ctx) {
            return ctx.editing() || ctx.preview() || sprintToggle.on || sneakToggle.on;
        }
    }
}
