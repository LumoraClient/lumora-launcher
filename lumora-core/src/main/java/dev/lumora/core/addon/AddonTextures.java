package dev.lumora.core.addon;

import dev.lumora.api.render.DynamicTextures;
import dev.lumora.api.render.TextureRef;
import dev.lumora.core.Platform;
import dev.lumora.core.RegistrationTracker;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * One addon's runtime textures (Lumora API 1.8): uploaded through {@link Platform#uploadTexture} as
 * {@code lumora:addon/<addon id>/<name>}, at most {@link DynamicTextures#MAX_TEXTURES} at a time, all freed when the
 * addon is switched off.
 */
final class AddonTextures implements DynamicTextures {
    private static final Pattern NAME = Pattern.compile("[a-z0-9_.-]{1,32}");

    private final Platform platform;
    private final String prefix;
    private final RegistrationTracker tracker;
    private final Runnable check;
    private final Set<String> held = new LinkedHashSet<>();
    private boolean cleanupTracked;

    AddonTextures(Platform platform, String addonId, RegistrationTracker tracker, Runnable check) {
        this.platform = platform;
        this.prefix = "addon/" + addonId.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_") + "/";
        this.tracker = tracker;
        this.check = check;
    }

    @Override
    public synchronized TextureRef upload(String name, int width, int height, int[] argb) {
        check.run();
        if (name == null || !NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("invalid texture name: " + name);
        }
        if (width < 1 || height < 1 || width > MAX_SIZE || height > MAX_SIZE) {
            throw new IllegalArgumentException("texture size must be 1.." + MAX_SIZE + ": " + width + "x" + height);
        }
        if (argb == null || argb.length != width * height) {
            throw new IllegalArgumentException("expected " + width * height + " pixels");
        }
        if (!held.contains(name) && held.size() >= MAX_TEXTURES) {
            return null;
        }
        String path = prefix + name;
        if (!platform.uploadTexture(path, width, height, argb)) {
            return null;
        }
        held.add(name);
        if (!cleanupTracked) {
            // one registration frees everything when the addon is switched off
            cleanupTracked = true;
            tracker.track(RegistrationTracker.of(this::releaseAll));
        }
        return new TextureRef("lumora", path);
    }

    @Override
    public synchronized void release(String name) {
        if (name != null && held.remove(name)) {
            platform.releaseTexture(prefix + name);
        }
    }

    private synchronized void releaseAll() {
        for (String name : held) {
            platform.releaseTexture(prefix + name);
        }
        held.clear();
        cleanupTracked = false;
    }

    /** Names currently held (tests). */
    synchronized Set<String> held() {
        return Set.copyOf(held);
    }
}
