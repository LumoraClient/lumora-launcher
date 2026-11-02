package dev.lumora.loader.impl;

import dev.lumora.loader.api.ClientEntrypoint;
import dev.lumora.loader.api.LoadedJar;
import dev.lumora.loader.api.LumoraLoader;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Start sequence of the Lumora Loader. The launcher passes, as system properties:
 * <ul>
 *   <li>{@code lumora.loader.game} - the Minecraft client jar (not on the class path, so only this loader defines its
 *       classes),</li>
 *   <li>{@code lumora.loader.jars} - the Lumora client jar(s), separated by the path separator,</li>
 *   <li>{@code lumora.loader.target} - Minecraft's main class (default {@code net.minecraft.client.main.Main}).</li>
 * </ul>
 * Order: read the descriptors, create the {@link GameClassLoader}, bootstrap Mixin with the jars' mixin configs and go
 * to the DEFAULT phase, then run the entry points and finally Minecraft's {@code main} with the original arguments.
 */
public final class Boot {
    static final String TARGET = "net.minecraft.client.main.Main";

    private Boot() {
    }

    public static void main(String[] args) throws Throwable {
        long start = System.nanoTime();
        Path game = existing(require("lumora.loader.game"), "Minecraft jar");
        String mappings = System.getProperty("lumora.loader.mappings");
        if (mappings != null && !mappings.isBlank()) {
            // obfuscated Minecraft: Mojang's names first (the Lumora client is compiled against them), cached
            game = GameRemapper.remapCached(game, existing(mappings, "Mojang mappings"), game.toAbsolutePath().getParent());
        }
        // the client jar already uses the game's runtime names; refmaps (intermediary names) must not apply
        System.setProperty("mixin.env.disableRefMap", "true");
        List<JarDescriptor> jars = new ArrayList<>();
        String list = System.getProperty("lumora.loader.jars", "");
        for (String p : list.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            if (!p.isBlank()) {
                jars.add(JarDescriptor.read(existing(p.strip(), "Lumora jar")));
            }
        }
        String target = System.getProperty("lumora.loader.target", TARGET);
        Path gameDir = Path.of(argument(args, "--gameDir", "."));
        String minecraft = argument(args, "--version", "");

        List<URL> urls = new ArrayList<>();
        urls.add(game.toUri().toURL());
        List<LoadedJar> loaded = new ArrayList<>();
        for (JarDescriptor d : jars) {
            urls.add(d.file().toUri().toURL());
            loaded.add(new LoadedJar(d.id(), d.name(), d.version(), d.file()));
        }
        GameClassLoader loader = new GameClassLoader(urls.toArray(URL[]::new), Boot.class.getClassLoader());
        Thread.currentThread().setContextClassLoader(loader);
        LumoraLoader.init(gameDir, minecraft, loaded);

        // Mixin: service = LumoraMixinService (META-INF/services), configs from the jars, then INIT -> DEFAULT
        LumoraMixinService.loader(loader);
        MixinBootstrap.init();
        int configs = 0;
        for (JarDescriptor d : jars) {
            for (String c : d.mixins()) {
                Mixins.addConfiguration(c);
                configs++;
            }
        }
        gotoPhase(MixinEnvironment.Phase.INIT);
        gotoPhase(MixinEnvironment.Phase.DEFAULT);
        IMixinTransformer transformer = LumoraMixinService.transformer();
        if (transformer == null) {
            throw new IllegalStateException("Lumora Loader: Mixin did not provide a transformer");
        }
        MixinEnvironment env = MixinEnvironment.getDefaultEnvironment();
        loader.transformer(new GameClassLoader.Transformer() {
            @Override
            public byte[] transform(String name, byte[] bytes) {
                return transformer.transformClassBytes(name, name, bytes);
            }

            @Override
            public byte[] generate(String name) {
                return transformer.generateClass(MixinEnvironment.getCurrentEnvironment(), name);
            }
        });
        System.out.println("[Lumora Loader] " + LumoraLoader.VERSION + ": Minecraft " + minecraft + ", " + jars.size()
                + " jar(s), " + configs + " mixin config(s), side " + env.getSide() + " ("
                + (System.nanoTime() - start) / 1_000_000 + " ms)");

        for (JarDescriptor d : jars) {
            for (String e : d.entrypoints()) {
                runEntrypoint(loader, d, e);
            }
        }

        Class<?> main = Class.forName(target, false, loader);
        Method m = main.getMethod("main", String[].class);
        try {
            m.invoke(null, (Object) args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private static void runEntrypoint(ClassLoader loader, JarDescriptor d, String className) throws Throwable {
        Object instance;
        try {
            instance = Class.forName(className, true, loader).getDeclaredConstructor().newInstance();
        } catch (InvocationTargetException e) {
            throw new IllegalStateException(d.id() + ": entry point " + className + " failed", e.getCause());
        }
        if (!(instance instanceof ClientEntrypoint entry)) {
            throw new IllegalStateException(d.id() + ": " + className + " does not implement " + ClientEntrypoint.class.getName());
        }
        entry.onInitializeClient();
    }

    /** MixinEnvironment.gotoPhase is not public (Mixin expects the platform to drive the phases). */
    private static void gotoPhase(MixinEnvironment.Phase phase) throws ReflectiveOperationException {
        Method m = MixinEnvironment.class.getDeclaredMethod("gotoPhase", MixinEnvironment.Phase.class);
        m.setAccessible(true);
        m.invoke(null, phase);
    }

    static String argument(String[] args, String name, String fallback) {
        for (int i = 0; i + 1 < args.length; i++) {
            if (args[i].equals(name)) {
                return args[i + 1];
            }
        }
        return fallback;
    }

    private static String require(String property) {
        String v = System.getProperty(property);
        if (v == null || v.isBlank()) {
            throw new IllegalStateException("Lumora Loader: system property " + property + " is missing (start the game from the Lumora launcher)");
        }
        return v;
    }

    private static Path existing(String path, String what) throws IOException {
        Path p = Path.of(path);
        if (!Files.isRegularFile(p)) {
            throw new IOException("Lumora Loader: " + what + " not found: " + p);
        }
        return p;
    }
}
