package dev.lumora.core.cosmetics;

import dev.lumora.core.cosmetics.anim.Animation;
import dev.lumora.core.cosmetics.anim.AnimationController;
import dev.lumora.core.cosmetics.anim.AnimationParser;
import dev.lumora.core.cosmetics.anim.Easing;
import dev.lumora.core.cosmetics.anim.Molang;
import dev.lumora.core.cosmetics.anim.PoseBuffer;
import dev.lumora.core.cosmetics.model.CosmeticMesh;
import dev.lumora.core.cosmetics.model.GeoModel;
import dev.lumora.core.cosmetics.model.MeshBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CosmeticsTest {
    private static final double EPS = 1e-4;

    @Test
    void molangEvaluatesMathQueriesAndVariables() {
        Molang.Scope s = new Molang.Scope();
        s.set(Molang.Query.ANIM_TIME, 0.5);
        assertEquals(1, Molang.compile("math.sin(query.anim_time * 180)").eval(s), EPS);
        assertEquals(7, Molang.compile("1 + 2 * 3").eval(null), EPS);
        assertEquals(-4, Molang.compile("-(2 + 2)").eval(null), EPS);
        assertEquals(2, Molang.compile("q.anim_time > 0.2 ? 2 : 3").eval(s), EPS);
        assertEquals(6, Molang.compile("v.a = 3; v.a * 2").eval(new Molang.Scope()), EPS);
        assertEquals(5, Molang.compile("v.unset ?? 5").eval(new Molang.Scope()), EPS);
        assertEquals(10, Molang.compile("math.clamp(20, 0, 10)").eval(null), EPS);
        assertEquals(0.7, Molang.compile("(query.anim_time + 1.2) % 1").eval(s), EPS);
        assertTrue(Molang.compile("2 * 3 + 1").constant(), "constants are folded");
        assertThrows(IllegalArgumentException.class, () -> Molang.compile("1 + (2"));
    }

    @Test
    void easingsMatchGeckoLib() {
        assertEquals(0.5, Easing.LINEAR.interpolate(0.5, 0, 1, Double.NaN), EPS);
        assertEquals(1 - Math.cos(0.25 * Math.PI), Easing.EASE_IN_SINE.curve(0.5, Double.NaN), EPS);
        assertEquals(0.5, Easing.EASE_IN_OUT_SINE.curve(0.5, Double.NaN), EPS);
        assertEquals(0.5, Easing.STEP.curve(0.6, Double.NaN), EPS);
        assertEquals(Easing.EASE_OUT_BOUNCE, Easing.of("easeOutBounce"));
        assertEquals(Easing.LINEAR, Easing.of("unknown"));
        assertEquals(10, Easing.catmullRom(1, 0, 5, 10, 15), EPS);
    }

    private static final String ANIM = """
            {"format_version": "1.8.0", "animations": {
              "animation.test.a": {"loop": true, "animation_length": 2, "bones": {
                "right_arm": {"rotation": {"0": [0, 0, 0], "1": {"vector": [-90, 0, 0], "easing": "linear"}, "2": [0, 0, 0]}},
                "rightArm": {"position": [0, "math.sin(query.anim_time * 90) * 2", 0]},
                "head": {"rotation": {"0.5": {"pre": [10, 0, 0], "post": [20, 0, 0]}}}
              }, "sound_effects": {"0.5": {"effect": "clap"}}},
              "animation.test.hold": {"loop": "hold_on_last_frame", "bones": {"root": {"position": {"0": [0, 0, 0], "1": [0, -10, 0]}}}}
            }}""";

    @Test
    void parsesGeckoLibAnimations() {
        Map<String, Animation> all = AnimationParser.parse(ANIM);
        Animation a = all.get("animation.test.a");
        assertEquals(2, a.length(), EPS);
        assertEquals(Animation.Loop.LOOP, a.loop());
        assertEquals(1, a.events().size());
        assertEquals("clap", a.events().get(0).value());
        assertEquals(Animation.Loop.HOLD_ON_LAST_FRAME, all.get("animation.test.hold").loop());
        assertEquals(1, all.get("animation.test.hold").length(), EPS, "length from the last keyframe");
        assertThrows(IllegalArgumentException.class, () -> AnimationParser.parse("{\"x\": 1}"));
    }

    @Test
    void controllerInterpolatesAndFades() {
        Animation a = AnimationParser.parse(ANIM).get("animation.test.a");
        Molang.Scope scope = new Molang.Scope();
        AnimationController c = new AnimationController(scope);
        PoseBuffer buf = new PoseBuffer("right_arm", "head", "body");
        c.play(a, 0, 0, false);
        c.sample(0.5, buf, 1);
        assertEquals(-45, buf.value(0, PoseBuffer.ROT, 0), EPS);
        assertEquals(1, buf.weight(0, PoseBuffer.ROT), EPS);
        assertEquals(0, buf.weight(2, PoseBuffer.ROT), EPS, "bones the animation does not move stay free");
        // fade-in: half way through a 0.2 s transition the weight is 0.5
        buf.clear();
        c.reset();
        c.play(a, 10, 0.2, true);
        c.sample(10.1, buf, 1);
        assertEquals(0.5, buf.weight(0, PoseBuffer.ROT), EPS);
        // stopping fades out
        c.stop(11, 0.4);
        buf.clear();
        c.sample(11.1, buf, 1);
        assertEquals(0.75, buf.weight(0, PoseBuffer.ROT), EPS);
        assertTrue(c.active(11.1));
        assertFalse(c.active(11.5));
    }

    private static final String HAT = """
            {"format_version": "1.12.0", "minecraft:geometry": [{
              "description": {"identifier": "geometry.test", "texture_width": 64, "texture_height": 32},
              "bones": [
                {"name": "head", "pivot": [0, 24, 0], "cubes": [{"origin": [-4, 32, -4], "size": [8, 2, 8], "uv": [0, 0]}]},
                {"name": "pom", "parent": "head", "pivot": [0, 34, 0], "cubes": [{"origin": [-1, 34, -1], "size": [2, 2, 2], "uv": [0, 10]}]},
                {"name": "aura", "pivot": [0, 0, 0], "cubes": [{"origin": [6, 0, -1], "size": [2, 1, 2], "uv": [0, 20]}]}
              ]}]}""";

    @Test
    void meshIsInMinecraftPartSpace() {
        GeoModel m = GeoModel.parse(HAT, AttachPoint.ROOT);
        assertEquals(64, m.textureWidth());
        assertEquals(32, m.textureHeight());
        CosmeticMesh mesh = new MeshBuilder(m).build(null);
        // head cube: 2 bones x 6 faces x 4 vertices on the head, the aura on the root
        assertEquals(48, mesh.vertices(AttachPoint.HEAD));
        assertEquals(24, mesh.vertices(AttachPoint.ROOT));
        float[] b = bounds(mesh, AttachPoint.HEAD);
        // Bedrock y 32..36 above the neck (24) = 8..12 px up = Minecraft y -0.75..-0.5 blocks (y down)
        assertEquals(-4 / 16f, b[0], EPS);
        assertEquals(4 / 16f, b[1], EPS);
        assertEquals(-12 / 16f, b[2], EPS);
        assertEquals(-8 / 16f, b[3], EPS);
        assertEquals(-4 / 16f, b[4], EPS);
        assertEquals(4 / 16f, b[5], EPS);
        // the root model space: feet at y = 24 px, Bedrock x keeps its sign (x 6..8)
        float[] r = bounds(mesh, AttachPoint.ROOT);
        assertEquals(6 / 16f, r[0], EPS);
        assertEquals(8 / 16f, r[1], EPS);
        assertEquals(23 / 16f, r[2], EPS);
        assertEquals(24 / 16f, r[3], EPS);
    }

    @Test
    void animatedBoneMovesItsChildren() {
        GeoModel m = GeoModel.parse(HAT, AttachPoint.ROOT);
        PoseBuffer pose = new PoseBuffer(m.boneNames());
        AnimationController c = new AnimationController(new Molang.Scope());
        Animation lift = AnimationParser.parse("""
                {"animations": {"a": {"loop": true, "bones": {"head": {"position": [0, 3, 0]}}}}}""").get("a");
        c.play(lift, 0, 0, false);
        c.sample(0, pose, 1);
        float[] b = bounds(new MeshBuilder(m).build(pose), AttachPoint.HEAD);
        assertEquals(-15 / 16f, b[2], EPS, "3 px up: the pom (child) follows");
    }

    private static float[] bounds(CosmeticMesh mesh, AttachPoint p) {
        float[] d = mesh.data(p);
        float[] b = {Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE};
        for (int v = 0; v < mesh.vertices(p); v++) {
            for (int a = 0; a < 3; a++) {
                float x = d[v * CosmeticMesh.STRIDE + a];
                b[a * 2] = Math.min(b[a * 2], x);
                b[a * 2 + 1] = Math.max(b[a * 2 + 1], x);
            }
        }
        return b;
    }

    @Test
    void emotePoseUsesMinecraftPartAngles() {
        Animation a = AnimationParser.parse("""
                {"animations": {"zombie": {"loop": true, "bones": {
                  "rightArm": {"rotation": [-90, 0, 0]},
                  "root": {"position": [0, -10, 0], "rotation": [0, 90, 0]}
                }}}}""").get("zombie");
        Animation canon = CosmeticRegistry.canonical(a);
        assertTrue(canon.boneIndex("right_arm") >= 0);
        PoseBuffer buf = new PoseBuffer("head", "body", "right_arm", "left_arm", "right_leg", "left_leg", "root");
        AnimationController c = new AnimationController(new Molang.Scope());
        c.play(canon, 0, 0, false);
        c.sample(0, buf, 1);
        EmotePose pose = new EmotePose();
        pose.from(buf);
        int arm = AttachPoint.RIGHT_ARM.ordinal();
        assertEquals(-Math.PI / 2, pose.rotation(arm, 0, 0.3f), EPS, "Bedrock -90 = Minecraft's zombie arm");
        assertEquals(0.3f, pose.rotation(AttachPoint.LEFT_ARM.ordinal(), 0, 0.3f), EPS, "other parts keep Minecraft's motion");
        assertTrue(pose.hasRoot());
        assertEquals(-10 / 16f, pose.rootY(), EPS);
        assertEquals(-Math.PI / 2, pose.rootRotY(), EPS);
    }

    @Test
    void bodyCarriesHeadAndArms() {
        Animation a = AnimationParser.parse("""
                {"animations": {"bow": {"loop": true, "bones": {"body": {"rotation": [90, 0, 0]}}}}}""").get("bow");
        PoseBuffer buf = new PoseBuffer("head", "body", "right_arm", "left_arm", "right_leg", "left_leg", "root");
        AnimationController c = new AnimationController(new Molang.Scope());
        c.play(a, 0, 0, false);
        c.sample(0, buf, 1);
        EmotePose pose = new EmotePose();
        pose.from(buf);
        int arm = AttachPoint.RIGHT_ARM.ordinal();
        assertEquals(Math.PI / 2, pose.rotation(arm, 0, 0), EPS);
        // the body bends at the hip (12 px below the neck): the arm pivot (2 px below the neck, 10 above the hip)
        // turns 90 degrees about x to 10 px in front of the hip: offset (0, +10, -10)
        assertEquals(10, pose.position(arm, 1, 0), EPS);
        assertEquals(-10, pose.position(arm, 2, 0), EPS);
        // the body itself keeps its hip in place
        int body = AttachPoint.BODY.ordinal();
        assertEquals(12, pose.position(body, 1, 0), EPS);
        assertEquals(-12, pose.position(body, 2, 0), EPS);
    }

    @Test
    void builtInContentLoadsWithoutWarnings() {
        List<String> warnings = new ArrayList<>();
        CosmeticRegistry r = new CosmeticRegistry(warnings::add);
        r.loadBuiltIn();
        assertEquals(List.of(), warnings);
        assertTrue(r.cosmetics().size() >= 14, "start set");
        assertTrue(r.emotes().size() >= 15, "start set");
        for (CosmeticCategory c : CosmeticCategory.values()) {
            assertTrue(r.cosmetics().stream().anyMatch(d -> d.category() == c), "something in " + c);
        }
        for (EmoteCategory c : EmoteCategory.values()) {
            if (c != EmoteCategory.SPECIAL) {
                assertTrue(r.emotes().stream().anyMatch(d -> d.category() == c), "an emote in " + c);
            }
        }
        // every cosmetic builds a mesh in every player state
        PlayerInput in = new PlayerInput();
        Cosmetics cos = new Cosmetics(r, new CosmeticsState(null));
        List<String> all = r.cosmetics().stream().map(CosmeticDefinition::id).toList();
        for (String state : List.of("idle", "walk", "air", "sneak")) {
            in.reset();
            in.self = true;
            in.id = UUID.randomUUID();
            in.groundSpeed = state.equals("walk") ? 0.15f : 0;
            in.onGround = !state.equals("air");
            in.sneaking = state.equals("sneak");
            for (String id : all) {
                cos.setEquipped(List.of(id));
                PlayerFrame f = cos.frame(in);
                assertNotNull(f, id);
                assertEquals(1, f.count(), id);
                assertTrue(f.item(0).mesh().totalVertices() > 0 || id.equals("sparkle_trail"), id + " in " + state);
            }
        }
        assertTrue(r.emote("wave").animation().length() > 1);
        assertFalse(r.emote("wave").stopOnMove(), "waving while walking");
        assertTrue(r.emote("sit").stopOnMove());
    }

    @Test
    void equippingReplacesTheSameSlotAndSaves(@TempDir Path dir) throws Exception {
        CosmeticRegistry r = new CosmeticRegistry(null);
        r.loadBuiltIn();
        Path file = dir.resolve("cosmetics.json");
        CosmeticsState state = new CosmeticsState(file);
        Cosmetics c = new Cosmetics(r, state);
        c.equip(r.cosmetic("top_hat"));
        c.equip(r.cosmetic("angel_wings"));
        c.equip(r.cosmetic("crown"));
        assertEquals(List.of("angel_wings", "crown"), List.copyOf(state.equipped()));
        state.setSlot(0, "wave");
        state.toggleFavorite("crown");
        CosmeticsState again = new CosmeticsState(file);
        again.load();
        assertTrue(again.isEquipped("crown"));
        assertFalse(again.isEquipped("top_hat"));
        assertEquals("wave", again.slot(0));
        assertTrue(again.isFavorite("crown"));
        assertTrue(Files.size(file) > 0);
    }

    @Test
    void ownEmotesAndCosmeticsFromTheConfigFolder(@TempDir Path dir) throws Exception {
        Files.createDirectories(dir.resolve("emotes"));
        Files.writeString(dir.resolve("emotes/mine.animation.json"), ANIM);
        Path hat = Files.createDirectories(dir.resolve("cosmetics/pom_hat"));
        Files.writeString(hat.resolve("pom_hat.geo.json"), HAT);
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(64, 32, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        javax.imageio.ImageIO.write(img, "png", hat.resolve("pom_hat.png").toFile());
        Files.writeString(hat.resolve("cosmetic.json"), "{\"name\": \"Bommelmütze\", \"category\": \"hats\", \"attach\": \"head\"}");
        List<String> warnings = new ArrayList<>();
        CosmeticRegistry r = new CosmeticRegistry(warnings::add);
        r.loadCustom(dir);
        assertEquals(List.of(), warnings);
        assertEquals(2, r.emotes().size());
        EmoteDefinition e = r.emote("custom:mine/animation.test.a");
        assertNotNull(e);
        assertEquals("A", e.name());
        CosmeticDefinition d = r.cosmetic("custom:pom_hat");
        assertNotNull(d);
        assertEquals("Bommelmütze", d.name());
        assertEquals(CosmeticCategory.HATS, d.category());
        assertNull(d.texture(), "own textures are uploaded on first use");
        // the texture is uploaded once when it is first drawn
        Cosmetics c = new Cosmetics(r, new CosmeticsState(null));
        int[] uploads = {0};
        c.uploader((key, w, h, argb) -> {
            uploads[0]++;
            return "lumora:cosmetics/custom/" + key;
        });
        c.setEquipped(List.of(d.id()));
        PlayerInput in = new PlayerInput();
        in.self = true;
        in.id = UUID.randomUUID();
        assertEquals("lumora:cosmetics/custom/custom_pom_hat", c.frame(in).item(0).texture());
        c.frame(in);
        assertEquals(1, uploads[0]);
    }

    @Test
    void otherPlayersWithoutDataCostNothingAndPreviewStaysLocal() {
        CosmeticRegistry r = new CosmeticRegistry(null);
        r.loadBuiltIn();
        Cosmetics c = new Cosmetics(r, new CosmeticsState(null));
        c.equip(r.cosmetic("halo"));
        PlayerInput in = new PlayerInput();
        in.id = UUID.randomUUID();
        assertNull(c.frame(in), "another player without Lumora data: nothing to draw");
        assertEquals(0, c.trackedPlayers());
        // preview: own selection, not equipped
        c.setPreview(List.of("crown"));
        c.previewEmote(r.emote("wave"));
        sleep(50);
        in.self = true;
        in.preview = true;
        PlayerFrame f = c.frame(in);
        assertEquals("crown", f.item(0).id());
        assertTrue(f.pose().active());
        assertEquals(List.of("halo"), List.copyOf(c.state().equipped()));
        assertNull(c.currentEmote(), "the preview emote is not the player's emote");
    }

    @Test
    void walkingEndsWholeBodyEmotes() {
        CosmeticRegistry r = new CosmeticRegistry(null);
        r.loadBuiltIn();
        Cosmetics c = new Cosmetics(r, new CosmeticsState(null));
        PlayerInput in = new PlayerInput();
        in.self = true;
        in.id = UUID.randomUUID();
        c.playEmote(r.emote("wave"));
        in.groundSpeed = 0.2f;
        sleep(350);
        c.frame(in);
        assertNotNull(c.currentEmote(), "waving goes on while walking");
        c.playEmote(r.emote("sit"));
        sleep(350);
        c.frame(in);
        assertNull(c.currentEmote(), "sitting ends when walking");
    }

    @Test
    void otherPlayersShowSyncedCosmeticsWithVariantAndOffset() {
        CosmeticRegistry r = new CosmeticRegistry(null);
        r.loadBuiltIn();
        Cosmetics c = new Cosmetics(r, new CosmeticsState(null));
        CosmeticsSync sync = new CosmeticsSync(c, () -> null, () -> null);
        c.remote(sync);
        UUID other = UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e");
        PlayerInput in = new PlayerInput();
        in.id = other;
        assertNull(c.frame(in), "no data: drawn as Minecraft draws it");
        // a push from the server: top hat in brown, 2 px higher; an unknown id and a bad one are skipped
        sync.loadout("0f8fad5bd9cb469fa16570867728950e", dev.lumora.social.Js.object(
                "{\"l\":[{\"id\":\"top_hat\",\"variant\":\"brown\",\"offset\":[0,2,0]},{\"id\":\"no_such_thing\"},{\"id\":\"X!\"}]}")
                .getAsJsonArray("l"));
        PlayerFrame f = c.frame(in);
        assertNotNull(f);
        assertEquals(1, f.count());
        assertEquals("lumora:textures/cosmetics/top_hat_brown.png", f.item(0).texture());
        float top = Float.MAX_VALUE;
        CosmeticMesh mesh = f.item(0).mesh();
        for (int v = 0; v < mesh.vertices(AttachPoint.HEAD); v++) {
            top = Math.min(top, mesh.data(AttachPoint.HEAD)[v * CosmeticMesh.STRIDE + 1]);
        }
        // hat top (flare) at Bedrock y 41.6 = 17.6 px above the neck; offset 2 px up
        assertEquals(-19.6f / 16f, top, EPS);
        assertEquals(1, c.trackedPlayers());
        // the option to hide other players' cosmetics
        c.options(true, false, true, true);
        assertNull(c.frame(in));
        c.options(true, true, true, true);
        // an emote arrives and plays; the same emote again restarts it; "" stops it
        sync.emote("0f8fad5bd9cb469fa16570867728950e", "wave");
        sleep(50);
        assertTrue(c.frame(in).pose().active(), "the other player waves");
        sync.emote("0f8fad5bd9cb469fa16570867728950e", "");
        assertNull(sync.emote(other));
        // leaving the world forgets it
        sync.reset();
        assertTrue(sync.loadout(other).isEmpty());
    }

    @Test
    void ownLoadoutForTheServerSkipsOwnFilesAndCarriesOptions(@TempDir Path dir) throws Exception {
        CosmeticRegistry r = new CosmeticRegistry(null);
        r.loadBuiltIn();
        CosmeticsState state = new CosmeticsState(dir.resolve("c.json"));
        Cosmetics c = new Cosmetics(r, state);
        c.equip(r.cosmetic("angel_wings"));
        state.setOptions("angel_wings", CosmeticsState.Options.DEFAULT.withVariant("gold").withOffset(0, 0, 1.24f));
        CosmeticsSync sync = new CosmeticsSync(c, () -> null, () -> null);
        com.google.gson.JsonArray own = sync.ownLoadout();
        assertEquals(1, own.size());
        assertEquals("gold", own.get(0).getAsJsonObject().get("variant").getAsString());
        assertEquals(1.0, own.get(0).getAsJsonObject().getAsJsonArray("offset").get(2).getAsDouble(), EPS, "half pixels");
        // options and wheel pages are saved
        state.setSlot(CosmeticsState.slotIndex(2, 3), "clap");
        CosmeticsState again = new CosmeticsState(dir.resolve("c.json"));
        again.load();
        assertEquals("gold", again.options("angel_wings").variant());
        assertEquals(1.0f, again.options("angel_wings").z(), EPS);
        assertEquals("clap", again.slot(CosmeticsState.slotIndex(2, 3)));
        assertEquals(CosmeticsState.Options.DEFAULT, again.options("crown"));
        // the own frame uses the variant texture
        PlayerInput in = new PlayerInput();
        in.self = true;
        in.id = UUID.randomUUID();
        assertEquals("lumora:textures/cosmetics/angel_wings_gold.png", c.frame(in).item(0).texture());
        // variants of the start set exist as textures
        for (CosmeticDefinition d : r.cosmetics()) {
            for (CosmeticDefinition.Variant v : d.variants()) {
                String path = "/assets/" + v.texture().replace("lumora:", "lumora/");
                assertNotNull(CosmeticsTest.class.getResourceAsStream(path), path);
            }
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
