package dev.lumora.core.extras.anim;

import dev.lumora.api.config.SettingsGroup;
import dev.lumora.core.extras.AnimationsModule;
import dev.lumora.core.extras.AnimationsModuleAccess;
import dev.lumora.core.extras.AnimationsModule.Style;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** First-person item transforms of 1.7.10 and 1.8.9: the steps of the original code, which part decides, the left hand. */
class ItemPoseTest {
    private AnimationsModule module;

    /** Records the matrix steps. */
    static final class Recorder implements ItemPose.Matrix {
        final List<String> steps = new ArrayList<>();

        private void add(String op, float... v) {
            StringBuilder sb = new StringBuilder(op);
            for (float f : v) {
                sb.append(' ').append(Math.round(f * 1000) / 1000f);
            }
            steps.add(sb.toString());
        }

        @Override
        public void translate(float x, float y, float z) {
            add("t", x, y, z);
        }

        @Override
        public void rotateX(float d) {
            add("x", d);
        }

        @Override
        public void rotateY(float d) {
            add("y", d);
        }

        @Override
        public void rotateZ(float d) {
            add("z", d);
        }

        @Override
        public void scale(float x, float y, float z) {
            add("s", x, y, z);
        }
    }

    @AfterEach
    void off() {
        if (module != null) {
            AnimationsModuleAccess.setActive(module, false);
        }
    }

    @Test
    void holding18IsTransformFirstPersonItemThenTheModelValues() {
        Recorder r = new Recorder();
        ItemPose.apply(Style.LEGACY_1_8, r, true, 0, 0, ItemPose.Use.NONE, 0, 0, ItemPose.Kind.FLAT);
        assertEquals(List.of(
                "t 0.0 0.0 0.0", // doItemUsedTransformations without swing
                "t 0.56 -0.52 -0.72", "t 0.0 0.0 0.0", "y 45.0", "y 0.0", "z 0.0", "x 0.0", "s 0.4 0.4 0.4",
                "t 0.0 0.25 0.125", "y -135.0", "x 0.0", "z 25.0", "s 1.7 1.7 1.7", // "firstperson": Y, X, Z
                "s 0.5 0.5 0.5"), r.steps); // RenderItem.renderItem
    }

    @Test
    void blocking17KeepsTheSwingAnd18DoesNot() {
        Recorder r17 = new Recorder();
        ItemPose.apply(Style.LEGACY_1_7, r17, true, 0, 0.5F, ItemPose.Use.BLOCK, 10, 72000, ItemPose.Kind.FLAT);
        // 1.7: no swing move while using, but the swing rotation: sin(0.25 pi) * -20 ...
        assertEquals("y -14.142", r17.steps.get(2));
        assertEquals("t -0.5 0.2 0.0", r17.steps.get(6), "doBlockTransformations after scale 0.4");
        Recorder r18 = new Recorder();
        ItemPose.apply(Style.LEGACY_1_8, r18, true, 0, 0.5F, ItemPose.Use.BLOCK, 10, 72000, ItemPose.Kind.FLAT);
        assertEquals("y 0.0", r18.steps.get(3), "1.8 blocks without the swing");
    }

    @Test
    void theLeftHandIsMirroredAndOnlyLegacyChoicesDraw() {
        Recorder r = new Recorder();
        ItemPose.apply(Style.LEGACY_1_8, r, false, 0, 0, ItemPose.Use.NONE, 0, 0, ItemPose.Kind.BLOCK);
        assertEquals("t -0.56 -0.52 -0.72", r.steps.get(1));
        assertEquals("y -45.0", r.steps.get(3));

        module = new AnimationsModule();
        AnimationsModuleAccess.settings(module, new SettingsGroup("lumora-animations."));
        AnimationsModuleAccess.setActive(module, true);
        assertNull(ItemPose.select(ItemPose.Use.NONE, false, false, false), "default: Minecraft draws");
        AnimationsModuleAccess.set(module, "posture", "legacy_1_8");
        assertEquals(Style.LEGACY_1_8, ItemPose.select(ItemPose.Use.NONE, false, false, false));
        assertNull(ItemPose.select(ItemPose.Use.EAT_DRINK, false, false, false), "eating is its own part");
        AnimationsModuleAccess.set(module, "food", "legacy_1_7");
        assertEquals(Style.LEGACY_1_7, ItemPose.select(ItemPose.Use.EAT_DRINK, false, false, false));
        AnimationsModuleAccess.set(module, "sword", "legacy_1_7");
        assertEquals(Style.LEGACY_1_7, ItemPose.select(ItemPose.Use.NONE, true, false, true), "sword with a blocking shield");
        assertNull(ItemPose.select(ItemPose.Use.OTHER, false, false, false), "an item use 1.7 / 1.8 did not have");
        AnimationsModuleAccess.set(module, "posture", "modern");
        assertNull(ItemPose.select(ItemPose.Use.NONE, false, false, false));
    }
}
