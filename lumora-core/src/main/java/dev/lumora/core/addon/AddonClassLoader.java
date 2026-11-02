package dev.lumora.core.addon;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import java.util.function.Function;

/**
 * Class loader of one addon jar from the {@code addons/} folder (see {@link JarAddonLoader}).
 * <p>
 * Lookup order for a class: already loaded, the game's class loader (Lumora API, Lumora core, Minecraft, Fabric and
 * the libraries of the game), the addon's own jar, then the jars of the addons it declares in {@code depends} /
 * {@code optional}. Parent first means an addon can never replace a game or Lumora class. In addition the addon may not
 * define classes in the packages of Java, Minecraft, Fabric or Lumora ({@link #isProtected}), so it cannot reach
 * package-private code there either.
 * <p>
 * Resources are looked up in the addon's own jar first, so {@code getResource("lumora-addon.json")} and the addon's
 * own files are never answered by another mod.
 */
final class AddonClassLoader extends URLClassLoader {
    static {
        ClassLoader.registerAsParallelCapable();
    }

    /** Packages an addon jar must not define classes in. */
    private static final List<String> PROTECTED = List.of("java.", "javax.", "jdk.", "sun.", "com.sun.",
            "dev.lumora.api.", "dev.lumora.core.", "dev.lumora.client.", "dev.lumora.social.",
            "net.minecraft.", "com.mojang.", "net.fabricmc.", "org.spongepowered.", "org.lwjgl.");

    private final String addonId;
    private final List<String> peers;
    private final Function<String, AddonClassLoader> lookup;

    /**
     * @param peers  ids of the addons this one depends on (required and optional); their own classes are visible
     * @param lookup the class loader of an addon id from the same folder, or null
     */
    AddonClassLoader(String addonId, URL jar, ClassLoader parent, List<String> peers, Function<String, AddonClassLoader> lookup) {
        super("lumora-addon-" + addonId, new URL[]{jar}, parent);
        this.addonId = addonId;
        this.peers = List.copyOf(peers);
        this.lookup = lookup;
    }

    String addonId() {
        return addonId;
    }

    static boolean isProtected(String className) {
        for (String p : PROTECTED) {
            if (className.startsWith(p)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> c = findLoadedClass(name);
            if (c == null) {
                try {
                    c = getParent().loadClass(name);
                } catch (ClassNotFoundException notInGame) {
                    // the addon's own class, or one of a dependency
                }
            }
            if (c == null) {
                c = own(name);
            }
            for (int i = 0; c == null && i < peers.size(); i++) {
                AddonClassLoader peer = lookup.apply(peers.get(i));
                if (peer != null && peer != this) {
                    c = peer.own(name);
                }
            }
            if (c == null) {
                throw new ClassNotFoundException(name + " (Addon " + addonId + ")");
            }
            if (resolve) {
                resolveClass(c);
            }
            return c;
        }
    }

    /** A class from this addon's jar only (no parent, no peers), or null. Peers call this, so there is no recursion. */
    Class<?> own(String name) {
        if (isProtected(name)) {
            return null;
        }
        synchronized (getClassLoadingLock(name)) {
            Class<?> c = findLoadedClass(name);
            if (c != null) {
                return c;
            }
            try {
                return findClass(name);
            } catch (ClassNotFoundException e) {
                return null;
            }
        }
    }

    @Override
    public URL getResource(String name) {
        URL own = findResource(name);
        return own != null ? own : super.getResource(name);
    }

    @Override
    public String toString() {
        return "AddonClassLoader[" + addonId + "]";
    }
}
