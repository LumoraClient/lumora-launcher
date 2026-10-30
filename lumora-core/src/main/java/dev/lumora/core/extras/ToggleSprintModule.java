package dev.lumora.core.extras;

import dev.lumora.api.addon.AddonContext;
import dev.lumora.api.addon.LumoraAddon;
import dev.lumora.api.config.BooleanSetting;
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
 */
public final class ToggleSprintModule implements LumoraAddon {
    private final Platform platform;
    BooleanSetting sprint;
    BooleanSetting sneak;
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
        SettingsGroup s = context.configuration().settings();
        sprint = (BooleanSetting) s.toggle("sprint", true).name("lumora.extras.togglesprint.sprint");
        sneak = (BooleanSetting) s.toggle("sneak", false).name("lumora.extras.togglesprint.sneak");
    }

    @Override
    public void onEnable(AddonContext context) {
        context.events().subscribe(ClientTickEvent.class, e -> tick());
        context.events().subscribe(WorldLeaveEvent.class, e -> release());
        context.widgets().register(new StatusWidget());
    }

    @Override
    public void onDisable(AddonContext context) {
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
