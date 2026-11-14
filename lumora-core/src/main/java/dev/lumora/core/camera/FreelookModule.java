package dev.lumora.core.camera;

import dev.lumora.api.addon.AddonContext;
import dev.lumora.api.addon.LumoraAddon;
import dev.lumora.api.config.ChoiceSetting;
import dev.lumora.api.config.SettingsGroup;
import dev.lumora.api.event.client.ClientStoppingEvent;
import dev.lumora.api.event.client.ClientTickEvent;
import dev.lumora.api.event.client.WorldLeaveEvent;
import dev.lumora.api.input.Keybind;
import dev.lumora.api.input.Keys;
import dev.lumora.core.extras.GameExtras;

import java.util.function.Supplier;

/**
 * Built-in module "Freelook": while the key is held (or after a press in toggle mode) the camera switches to third
 * person and the mouse turns only the camera, all the way around the player; the player keeps walking and looking
 * where it did. Releasing the key brings back the previous perspective and view. F5 cannot switch away meanwhile.
 * <p>
 * Idea after the LabyMod addon "BetterPerspective" (raik0707) and LabyMod's camera lock; own code, see
 * THIRD_PARTY_NOTICES. The camera itself is {@link Freelook}, read by the mixins of every Minecraft version.
 */
public final class FreelookModule implements LumoraAddon {
    public static final String ID = "lumora-freelook";
    static final String K = "lumora.freelook.";
    /** Perspective values of {@link GameExtras#perspective()}. */
    static final int BEHIND = 1;
    static final int FRONT = 2;

    private final Supplier<GameExtras> extras;
    ChoiceSetting mode;
    ChoiceSetting view;
    private Keybind key;
    private boolean wasDown;
    private boolean toggled;
    /** Perspective before the free camera started, restored afterwards; -1 if none. */
    private int previous = -1;

    public FreelookModule(Supplier<GameExtras> extras) {
        this.extras = extras;
    }

    @Override
    public void onInitialize(AddonContext context) {
        SettingsGroup s = context.configuration().settings();
        mode = s.selection("mode", "hold", "hold", "toggle").name(K + "setting.mode")
                .description(K + "setting.mode.description").section(K + "section.general");
        view = s.selection("view", "behind", "behind", "front").name(K + "setting.view")
                .description(K + "setting.view.description");
    }

    @Override
    public void onEnable(AddonContext context) {
        key = context.keybinds().register("look", K + "key", Keys.LEFT_ALT, () -> {
        });
        context.events().subscribe(ClientTickEvent.class, e -> tick(key != null && key.isDown()));
        context.events().subscribe(WorldLeaveEvent.class, e -> stop());
        context.events().subscribe(ClientStoppingEvent.class, e -> stop());
    }

    @Override
    public void onDisable(AddonContext context) {
        stop();
        key = null;
    }

    /** Once per client tick with the state of the key (only down while playing, not in menus). */
    void tick(boolean down) {
        boolean want;
        if ("toggle".equals(mode.get())) {
            if (down && !wasDown) {
                toggled = !toggled;
            }
            want = toggled;
        } else {
            toggled = false;
            want = down;
        }
        wasDown = down;
        GameExtras x = extras.get();
        if (want && !Freelook.active()) {
            start(x);
        } else if (!want && Freelook.active()) {
            stop();
        } else if (Freelook.active()) {
            // F5 or a changed setting: keep the chosen third person view while looking around
            int target = target();
            if (x.perspective() != target) {
                x.setPerspective(target);
            }
        }
    }

    private void start(GameExtras x) {
        float[] r = x.playerRotation();
        if (r == null) {
            toggled = false;
            return; // not in a world
        }
        previous = x.perspective();
        Freelook.start(r[0], r[1]);
        x.setPerspective(target());
    }

    /** Ends the free camera and brings back the perspective from before. */
    void stop() {
        toggled = false;
        if (!Freelook.active()) {
            return;
        }
        Freelook.stop();
        if (previous >= 0) {
            extras.get().setPerspective(previous);
        }
        previous = -1;
    }

    private int target() {
        return "front".equals(view.get()) ? FRONT : BEHIND;
    }
}
