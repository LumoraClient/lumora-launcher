package dev.lumora.core.extras;

import dev.lumora.api.config.SettingsGroup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ToggleSprintFlyTest {
    ToggleSprintModule module;

    @BeforeEach
    void setUp() {
        module = new ToggleSprintModule(null);
        module.settings(new SettingsGroup("test."));
    }

    @AfterEach
    void tearDown() {
        module.setActive(false);
    }

    @Test
    void flyingStaysAsUsualByDefaultAndWhileTheModuleIsOff() {
        assertEquals(1.0, module.flySpeed.get());
        assertEquals(100, module.flyMomentum.get());
        assertEquals(1f, module.flyMomentum());
        module.flySpeed.set(3.0);
        assertEquals(1f, ToggleSprintModule.flySpeed(), "module off: normal speed");
        module.setActive(true);
        assertEquals(3f, ToggleSprintModule.flySpeed());
        module.setActive(false);
        assertEquals(1f, ToggleSprintModule.flySpeed());
    }

    @Test
    void sliderRangesAreRespected() {
        module.setActive(true);
        module.flySpeed.set(99.0);
        assertEquals((float) ToggleSprintModule.MAX_FLY_SPEED, ToggleSprintModule.flySpeed(), "at most 5x");
        module.flySpeed.set(0.1);
        assertEquals(1f, ToggleSprintModule.flySpeed(), "never slower than normal");
        module.flyMomentum.set(0);
        assertEquals(0f, module.flyMomentum());
        module.flyMomentum.set(40);
        assertEquals(0.4f, module.flyMomentum(), 1e-6);
    }
}
