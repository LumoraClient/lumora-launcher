package dev.lumora.core.camera;

import dev.lumora.api.config.SettingsGroup;
import dev.lumora.core.FakePlatform;
import dev.lumora.core.LumoraCore;
import dev.lumora.core.extras.Extras;
import dev.lumora.core.extras.GameExtras;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FreelookTest {
    /** Perspective of the fake game options and the player's rotation (null outside a world). */
    int perspective;
    float[] rotation = {30f, 10f};
    final GameExtras extras = (GameExtras) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{GameExtras.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "perspective" -> perspective;
                case "setPerspective" -> {
                    perspective = (Integer) args[0];
                    yield null;
                }
                case "playerRotation" -> rotation;
                default -> method.isDefault() ? InvocationHandler.invokeDefault(proxy, method, args) : null;
            });
    FreelookModule module;

    @TempDir
    Path dir;
    LumoraCore core;

    @BeforeEach
    void setUp() {
        module = new FreelookModule(() -> extras);
        SettingsGroup s = new SettingsGroup("test.");
        module.mode = s.selection("mode", "hold", "hold", "toggle");
        module.view = s.selection("view", "behind", "behind", "front");
    }

    @AfterEach
    void tearDown() {
        Freelook.stop();
        if (core != null) {
            core.stopping();
        }
    }

    @Test
    void mouseTurnsTheCameraOnlyWhileActive() {
        assertFalse(Freelook.turn(100, 0), "off: the player turns as usual");
        Freelook.start(30f, 10f);
        assertTrue(Freelook.turn(100, -20));
        assertEquals(45f, Freelook.yaw(), 1e-4, "100 units right = 15 degrees, like Entity#turn");
        assertEquals(7f, Freelook.pitch(), 1e-4, "20 units up = 3 degrees");
        Freelook.turn(0, 10_000);
        assertEquals(90f, Freelook.pitch(), "pitch stops straight down");
        Freelook.turn(0, -20_000);
        assertEquals(-90f, Freelook.pitch(), "and straight up");
        Freelook.turn(2400, 0);
        assertEquals(405f, Freelook.yaw(), 1e-3, "yaw goes all the way around");
    }

    @Test
    void holdingTheKeyLooksAroundAndReleasingBringsBackTheView() {
        perspective = 0;
        module.tick(true);
        assertTrue(Freelook.active());
        assertEquals(1, perspective, "third person behind the player");
        assertEquals(30f, Freelook.yaw());
        assertEquals(10f, Freelook.pitch(), "starts where the player looks");
        Freelook.turn(600, 0);
        module.tick(true);
        assertEquals(120f, Freelook.yaw(), 1e-4);
        module.tick(false);
        assertFalse(Freelook.active());
        assertEquals(0, perspective, "first person again");
    }

    @Test
    void f5CannotSwitchAwayWhileLookingAround() {
        perspective = 0;
        module.tick(true);
        perspective = 2; // the player pressed F5
        module.tick(true);
        assertEquals(1, perspective);
        module.view.set("front");
        module.tick(true);
        assertEquals(2, perspective, "a changed setting applies at once");
        module.tick(false);
        assertEquals(0, perspective);
    }

    @Test
    void toggleModeSwitchesOnPress() {
        module.mode.set("toggle");
        perspective = 1;
        module.tick(true);
        assertTrue(Freelook.active());
        module.tick(false);
        assertTrue(Freelook.active(), "stays on after releasing the key");
        module.tick(true);
        assertFalse(Freelook.active(), "the next press switches it off");
        assertEquals(1, perspective, "back to the player's own third person view");
        module.tick(false);
        assertFalse(Freelook.active());
    }

    @Test
    void outsideAWorldNothingHappens() {
        rotation = null;
        module.mode.set("toggle");
        module.tick(true);
        assertFalse(Freelook.active());
        module.tick(false);
        rotation = new float[]{0f, 0f};
        module.tick(true);
        assertTrue(Freelook.active(), "a press in the world starts it, no stale toggle state");
    }

    @Test
    void leavingTheWorldOrSwitchingTheModuleOffEndsIt() {
        perspective = 0;
        module.tick(true);
        module.stop();
        assertFalse(Freelook.active());
        assertEquals(0, perspective);
        module.stop();
        assertEquals(0, perspective, "a second stop changes nothing");
    }

    @Test
    void moduleIsBuiltInAndStartsOff() {
        assertTrue(Extras.OFF_BY_DEFAULT.contains(FreelookModule.ID));
        core = new LumoraCore(new FakePlatform(dir));
        core.loadAddons(List.of());
        assertTrue(core.addons().stream().anyMatch(a -> a.id().equals(FreelookModule.ID)));
        assertTrue(core.addonManager().setWanted(FreelookModule.ID, true).isEmpty(), "can be switched on");
    }
}
