package dev.lumora.core.cosmetics;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.lumora.core.cosmetics.anim.Animation;
import dev.lumora.core.cosmetics.anim.AnimationParser;
import dev.lumora.core.cosmetics.model.GeoModel;
import dev.lumora.core.io.Json;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * All known cosmetics and emotes: the built-in ones from {@code assets/lumora/cosmetics/index.json} in the Lumora jar
 * and the player's own ones from the config folder:
 * <ul>
 * <li>{@code config/lumora/cosmetics/<folder>/} with a {@code .geo.json} model, a {@code .png} texture, optionally an
 * {@code .animation.json} file and a {@code cosmetic.json} with name, category, slot, attach point, animations;</li>
 * <li>{@code config/lumora/emotes/*.animation.json}: every animation in it becomes an emote (category "special").</li>
 * </ul>
 * New content needs only these files, no code. Broken files are skipped with a warning.
 */
public final class CosmeticRegistry {
    public static final String INDEX = "/assets/lumora/cosmetics/index.json";
    private static final String BASE = "/assets/lumora/cosmetics/";
    /** Largest own texture (the client's runtime texture limit). */
    static final int MAX_TEXTURE = 512;
    /** Largest own file read (models and animations are small text files). */
    static final long MAX_FILE = 2L * 1024 * 1024;

    private final Map<String, CosmeticDefinition> cosmetics = new LinkedHashMap<>();
    private final Map<String, EmoteDefinition> emotes = new LinkedHashMap<>();
    private final Consumer<String> warn;

    public CosmeticRegistry(Consumer<String> warn) {
        this.warn = warn == null ? s -> { } : warn;
    }

    public List<CosmeticDefinition> cosmetics() {
        return Collections.unmodifiableList(new ArrayList<>(cosmetics.values()));
    }

    public List<EmoteDefinition> emotes() {
        return Collections.unmodifiableList(new ArrayList<>(emotes.values()));
    }

    public CosmeticDefinition cosmetic(String id) {
        return id == null ? null : cosmetics.get(id);
    }

    public EmoteDefinition emote(String id) {
        return id == null ? null : emotes.get(id);
    }

    public void register(CosmeticDefinition d) {
        cosmetics.put(d.id(), d);
    }

    public void register(EmoteDefinition d) {
        emotes.put(d.id(), d);
    }

    // --- built-in

    /** Loads the built-in content from the jar. */
    public void loadBuiltIn() {
        String index = resource(INDEX);
        if (index == null) {
            warn.accept("cosmetics index missing: " + INDEX);
            return;
        }
        loadIndex(index, this::resource);
    }

    /** Reads an index; {@code files} gives the text of {@code models/x.geo.json} and {@code animations/x.animation.json}. */
    void loadIndex(String json, java.util.function.Function<String, String> files) {
        JsonObject root;
        try {
            root = Json.parse(json).getAsJsonObject();
        } catch (RuntimeException e) {
            warn.accept("cosmetics index unreadable: " + e.getMessage());
            return;
        }
        Map<String, Map<String, Animation>> animationFiles = new HashMap<>();
        java.util.function.Function<String, Map<String, Animation>> anim = name -> animationFiles.computeIfAbsent(name, n -> {
            String text = files.apply(BASE + "animations/" + n + ".animation.json");
            if (text == null) {
                warn.accept("animation file missing: " + n);
                return Map.of();
            }
            try {
                return AnimationParser.parse(text);
            } catch (IllegalArgumentException e) {
                warn.accept("animation file " + n + ": " + e.getMessage());
                return Map.of();
            }
        });
        if (root.has("cosmetics") && root.get("cosmetics").isJsonArray()) {
            for (JsonElement el : root.getAsJsonArray("cosmetics")) {
                try {
                    JsonObject o = el.getAsJsonObject();
                    String id = str(o, "id", null);
                    String modelName = str(o, "model", id);
                    String modelText = files.apply(BASE + "models/" + modelName + ".geo.json");
                    if (id == null || modelText == null) {
                        warn.accept("cosmetic without id or model: " + o);
                        continue;
                    }
                    AttachPoint attach = AttachPoint.of(str(o, "attach", "root"), AttachPoint.ROOT);
                    GeoModel model = GeoModel.parse(modelText, attach);
                    Map<String, Animation> available = o.has("animationFile") ? anim.apply(str(o, "animationFile", id)) : Map.of();
                    register(new CosmeticDefinition(id, "lumora.cosmetics.item." + id, "lumora.cosmetics.item." + id + ".description",
                            true, CosmeticCategory.of(str(o, "category", null)), str(o, "type", ""), str(o, "slot", id),
                            Rarity.of(str(o, "rarity", null)), !o.has("unlocked") || o.get("unlocked").getAsBoolean(), model,
                            "lumora:textures/cosmetics/" + str(o, "texture", id) + ".png", null, 0, 0,
                            stateAnimations(o, available, id), overlay(o, available, id), RenderMode.of(str(o, "render", null)),
                            o.has("previewYaw") ? o.get("previewYaw").getAsFloat() : 0, false, variants(o, str(o, "texture", id))));
                } catch (RuntimeException e) {
                    warn.accept("cosmetic skipped: " + e.getMessage());
                }
            }
        }
        if (root.has("emotes") && root.get("emotes").isJsonArray()) {
            for (JsonElement el : root.getAsJsonArray("emotes")) {
                try {
                    JsonObject o = el.getAsJsonObject();
                    String id = str(o, "id", null);
                    Animation a = anim.apply(str(o, "file", "emotes")).get(str(o, "animation", "animation.lumora." + id));
                    if (id == null || a == null) {
                        warn.accept("emote without animation: " + id);
                        continue;
                    }
                    register(new EmoteDefinition(id, "lumora.cosmetics.emote." + id, "lumora.cosmetics.emote." + id + ".description",
                            true, EmoteCategory.of(str(o, "category", null)), Rarity.of(str(o, "rarity", null)),
                            !o.has("unlocked") || o.get("unlocked").getAsBoolean(), canonical(a),
                            o.has("stopOnMove") ? o.get("stopOnMove").getAsBoolean() : movesLegs(a), str(o, "icon", ""), false));
                } catch (RuntimeException e) {
                    warn.accept("emote skipped: " + e.getMessage());
                }
            }
        }
    }

    /** Built-in variants: the first uses the base texture, the others {@code <texture>_<variant>.png}. */
    private static List<CosmeticDefinition.Variant> variants(JsonObject o, String texture) {
        List<CosmeticDefinition.Variant> out = new ArrayList<>();
        if (o.has("variants") && o.get("variants").isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray("variants")) {
                String v = e.isJsonObject() ? str(e.getAsJsonObject(), "id", null) : e.isJsonPrimitive() ? e.getAsString() : null;
                if (v == null || !v.matches("[a-z0-9_-]{1,32}")) {
                    continue;
                }
                String file = out.isEmpty() ? texture : texture + "_" + v;
                out.add(new CosmeticDefinition.Variant(v, "lumora.cosmetics.variant." + v, "lumora:textures/cosmetics/" + file + ".png"));
            }
        }
        return List.copyOf(out);
    }

    private Map<String, Animation> stateAnimations(JsonObject o, Map<String, Animation> available, String id) {
        Map<String, Animation> out = new LinkedHashMap<>();
        if (o.has("animations") && o.get("animations").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("animations").entrySet()) {
                Animation a = available.get(e.getValue().getAsString());
                if (a == null) {
                    warn.accept(id + ": animation " + e.getValue().getAsString() + " not found");
                } else {
                    out.put(e.getKey().toLowerCase(Locale.ROOT), a);
                }
            }
        }
        return out;
    }

    private Animation overlay(JsonObject o, Map<String, Animation> available, String id) {
        String name = str(o, "overlay", null);
        if (name == null) {
            return null;
        }
        Animation a = available.get(name);
        if (a == null) {
            warn.accept(id + ": overlay animation " + name + " not found");
        }
        return a;
    }

    // --- own content

    /** Loads the player's own cosmetics and emotes from {@code config/lumora} ({@code configDir} = that folder). */
    public void loadCustom(Path lumoraConfig) {
        if (lumoraConfig == null) {
            return;
        }
        Path cosmeticsDir = lumoraConfig.resolve("cosmetics");
        if (Files.isDirectory(cosmeticsDir)) {
            try (DirectoryStream<Path> dirs = Files.newDirectoryStream(cosmeticsDir)) {
                for (Path dir : dirs) {
                    if (Files.isDirectory(dir)) {
                        try {
                            loadCustomCosmetic(dir);
                        } catch (IOException | RuntimeException e) {
                            warn.accept("own cosmetic " + dir.getFileName() + " skipped: " + e.getMessage());
                        }
                    }
                }
            } catch (IOException e) {
                warn.accept("cosmetics folder unreadable: " + e.getMessage());
            }
        }
        Path emotesDir = lumoraConfig.resolve("emotes");
        if (Files.isDirectory(emotesDir)) {
            try (DirectoryStream<Path> files = Files.newDirectoryStream(emotesDir, "*.animation.json")) {
                for (Path f : files) {
                    try {
                        loadCustomEmotes(f.getFileName().toString().replace(".animation.json", ""), read(f));
                    } catch (IOException | RuntimeException e) {
                        warn.accept("own emotes " + f.getFileName() + " skipped: " + e.getMessage());
                    }
                }
            } catch (IOException e) {
                warn.accept("emotes folder unreadable: " + e.getMessage());
            }
        }
    }

    void loadCustomEmotes(String fileName, String json) {
        for (Map.Entry<String, Animation> e : AnimationParser.parse(json).entrySet()) {
            String anim = e.getKey();
            String shortName = anim.startsWith("animation.") ? anim.substring(anim.lastIndexOf('.') + 1) : anim;
            String id = "custom:" + safe(fileName) + "/" + safe(anim);
            Animation a = canonical(e.getValue());
            register(new EmoteDefinition(id, pretty(shortName), fileName, false, EmoteCategory.SPECIAL, Rarity.COMMON, true,
                    a, movesLegs(a), "", true));
        }
    }

    private void loadCustomCosmetic(Path dir) throws IOException {
        JsonObject o = new JsonObject();
        Path meta = dir.resolve("cosmetic.json");
        if (Files.isRegularFile(meta)) {
            o = Json.parse(read(meta)).getAsJsonObject();
        }
        Path model = file(dir, str(o, "model", null), ".geo.json");
        Path texture = file(dir, str(o, "texture", null), ".png");
        if (model == null || texture == null) {
            throw new IOException("needs a .geo.json model and a .png texture");
        }
        String folder = dir.getFileName().toString();
        String id = "custom:" + safe(str(o, "id", folder));
        AttachPoint attach = AttachPoint.of(str(o, "attach", "root"), AttachPoint.ROOT);
        GeoModel geo = GeoModel.parse(read(model), attach);
        int[] size = new int[2];
        int[] pixels = pixels(texture, size);
        Map<String, Animation> available = Map.of();
        Path animFile = file(dir, str(o, "animationFile", null), ".animation.json");
        if (animFile != null) {
            available = AnimationParser.parse(read(animFile));
        }
        Map<String, Animation> states = stateAnimations(o, available, id);
        if (states.isEmpty() && !available.isEmpty() && !o.has("animations")) {
            states = Map.of("idle", available.values().iterator().next()); // one animation file, no mapping: always play it
        }
        register(new CosmeticDefinition(id, str(o, "name", pretty(folder)), str(o, "description", ""), false,
                CosmeticCategory.of(str(o, "category", null)), str(o, "type", ""), str(o, "slot", id), Rarity.of(str(o, "rarity", null)),
                true, geo, null, pixels, size[0], size[1], states, overlay(o, available, id), RenderMode.of(str(o, "render", null)),
                o.has("previewYaw") ? o.get("previewYaw").getAsFloat() : 0, true, List.of()));
    }

    private static Path file(Path dir, String named, String suffix) throws IOException {
        if (named != null) {
            Path p = dir.resolve(named).normalize();
            if (!p.startsWith(dir) || !Files.isRegularFile(p)) {
                throw new IOException("file not found: " + named);
            }
            return p;
        }
        try (DirectoryStream<Path> s = Files.newDirectoryStream(dir, "*" + suffix)) {
            for (Path p : s) {
                return p;
            }
        }
        return null;
    }

    private static int[] pixels(Path png, int[] size) throws IOException {
        if (Files.size(png) > MAX_FILE) {
            throw new IOException("texture too large");
        }
        java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(png.toFile());
        if (img == null) {
            throw new IOException("not a PNG image");
        }
        if (img.getWidth() > MAX_TEXTURE || img.getHeight() > MAX_TEXTURE) {
            throw new IOException("texture larger than " + MAX_TEXTURE + " px");
        }
        size[0] = img.getWidth();
        size[1] = img.getHeight();
        return img.getRGB(0, 0, size[0], size[1], null, 0, size[0]);
    }

    private static String read(Path p) throws IOException {
        if (Files.size(p) > MAX_FILE) {
            throw new IOException("file too large: " + p.getFileName());
        }
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }

    // --- helpers

    /** Emote bones under the names of other rigs ({@code rightArm}, {@code torso}, {@code bipedHead}) get Lumora's names. */
    static Animation canonical(Animation a) {
        Map<String, Animation.BoneTrack> bones = new LinkedHashMap<>();
        boolean changed = false;
        for (int i = 0; i < a.boneCount(); i++) {
            String name = a.boneName(i);
            AttachPoint p = AttachPoint.byBone(name);
            String canon = p == null ? name : p.boneName();
            changed |= !canon.equals(name);
            bones.put(canon, a.bone(i));
        }
        return changed ? new Animation(a.name(), a.length(), a.loop(), bones, a.events()) : a;
    }

    /** Emotes that move the legs or the whole player end when the player walks. */
    static boolean movesLegs(Animation a) {
        return a.boneIndex("right_leg") >= 0 || a.boneIndex("left_leg") >= 0 || a.boneIndex("root") >= 0;
    }

    private static String safe(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.\\-/]", "_");
    }

    static String pretty(String s) {
        String t = s.replace('_', ' ').replace('-', ' ').trim();
        return t.isEmpty() ? s : Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }

    private static String str(JsonObject o, String key, String fallback) {
        if (o != null && o.has(key) && o.get(key).isJsonPrimitive()) {
            return o.get(key).getAsString();
        }
        return fallback;
    }

    private String resource(String path) {
        try (InputStream in = CosmeticRegistry.class.getResourceAsStream(path)) {
            if (in == null) {
                return null;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        } catch (IOException e) {
            warn.accept("cannot read " + path + ": " + e.getMessage());
            return null;
        }
    }
}
