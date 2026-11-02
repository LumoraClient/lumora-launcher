package dev.lumora.loader.impl;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.launch.platform.container.ContainerHandleURI;
import org.spongepowered.asm.launch.platform.container.IContainerHandle;
import org.spongepowered.asm.logging.ILogger;
import org.spongepowered.asm.logging.LoggerAdapterConsole;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.mixin.transformer.IMixinTransformerFactory;
import org.spongepowered.asm.service.IAdviceProvider;
import org.spongepowered.asm.service.IClassBytecodeProvider;
import org.spongepowered.asm.service.IClassProvider;
import org.spongepowered.asm.service.IClassTracker;
import org.spongepowered.asm.service.IFeatureValidator;
import org.spongepowered.asm.service.IMixinAuditTrail;
import org.spongepowered.asm.service.IMixinInternal;
import org.spongepowered.asm.service.IMixinService;
import org.spongepowered.asm.service.ITransformer;
import org.spongepowered.asm.service.ITransformerProvider;
import org.spongepowered.asm.util.ReEntranceLock;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mixin service of the Lumora Loader: tells Mixin how to read class bytes ({@link GameClassLoader#rawBytes}), how to
 * load classes and where resources (the mixin configs) come from. Found by Mixin through
 * {@code META-INF/services/org.spongepowered.asm.service.IMixinService}.
 */
public final class LumoraMixinService implements IMixinService, IClassProvider, IClassBytecodeProvider, IClassTracker,
        ITransformerProvider {
    private static volatile GameClassLoader loader;
    private static volatile IMixinTransformer transformer;

    private final ReEntranceLock lock = new ReEntranceLock(1);
    private final Map<String, ILogger> loggers = new ConcurrentHashMap<>();

    /** Set by {@link Boot} before Mixin is bootstrapped. */
    static void loader(GameClassLoader l) {
        loader = l;
    }

    static IMixinTransformer transformer() {
        return transformer;
    }

    private static GameClassLoader game() {
        GameClassLoader l = loader;
        if (l == null) {
            throw new IllegalStateException("Lumora Loader: Mixin used before the game class loader exists");
        }
        return l;
    }

    // --- IMixinService

    @Override
    public String getName() {
        return "Lumora Loader";
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public void prepare() {
    }

    @Override
    public MixinEnvironment.Phase getInitialPhase() {
        return MixinEnvironment.Phase.PREINIT;
    }

    @Override
    public void offer(IMixinInternal internal) {
        if (internal instanceof IMixinTransformerFactory factory) {
            transformer = factory.createTransformer();
        }
    }

    @Override
    public void init() {
    }

    @Override
    public void beginPhase() {
    }

    @Override
    public void checkEnv(Object bootSource) {
    }

    @Override
    public ReEntranceLock getReEntranceLock() {
        return lock;
    }

    @Override
    public IClassProvider getClassProvider() {
        return this;
    }

    @Override
    public IClassBytecodeProvider getBytecodeProvider() {
        return this;
    }

    @Override
    public ITransformerProvider getTransformerProvider() {
        return this;
    }

    @Override
    public IClassTracker getClassTracker() {
        return this;
    }

    @Override
    public IMixinAuditTrail getAuditTrail() {
        return null;
    }

    @Override
    public IFeatureValidator getFeatureValidator() {
        return IFeatureValidator.ALLOW_ALL;
    }

    @Override
    public IAdviceProvider getAdviceProvider() {
        return IAdviceProvider.GENERIC;
    }

    @Override
    public Collection<String> getPlatformAgents() {
        return List.of("org.spongepowered.asm.launch.platform.MixinPlatformAgentDefault");
    }

    @Override
    public IContainerHandle getPrimaryContainer() {
        try {
            URL self = LumoraMixinService.class.getProtectionDomain().getCodeSource().getLocation();
            return new ContainerHandleURI(Path.of(self.toURI()).toUri());
        } catch (URISyntaxException | RuntimeException e) {
            return new ContainerHandleURI(Path.of(".").toUri());
        }
    }

    @Override
    public Collection<IContainerHandle> getMixinContainers() {
        return Collections.emptyList();
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        return game().getResourceAsStream(name);
    }

    @Override
    public String getSideName() {
        return "CLIENT";
    }

    @Override
    public MixinEnvironment.CompatibilityLevel getMinCompatibilityLevel() {
        return MixinEnvironment.CompatibilityLevel.JAVA_8;
    }

    @Override
    public MixinEnvironment.CompatibilityLevel getMaxCompatibilityLevel() {
        return MixinEnvironment.CompatibilityLevel.JAVA_25;
    }

    @Override
    public ILogger getLogger(String name) {
        return loggers.computeIfAbsent(name, LoggerAdapterConsole::new);
    }

    // --- IClassProvider

    @Override
    @Deprecated
    public URL[] getClassPath() {
        return new URL[0];
    }

    @Override
    public Class<?> findClass(String name) throws ClassNotFoundException {
        return game().loadClass(name);
    }

    @Override
    public Class<?> findClass(String name, boolean initialize) throws ClassNotFoundException {
        return Class.forName(name, initialize, game());
    }

    @Override
    public Class<?> findAgentClass(String name, boolean initialize) throws ClassNotFoundException {
        return Class.forName(name, initialize, LumoraMixinService.class.getClassLoader());
    }

    // --- IClassBytecodeProvider

    @Override
    public ClassNode getClassNode(String name) throws ClassNotFoundException, IOException {
        return getClassNode(name, true, 0);
    }

    @Override
    public ClassNode getClassNode(String name, boolean runTransformers) throws ClassNotFoundException, IOException {
        return getClassNode(name, runTransformers, 0);
    }

    @Override
    public ClassNode getClassNode(String name, boolean runTransformers, int readerFlags) throws ClassNotFoundException, IOException {
        byte[] bytes = game().rawBytes(name.replace('/', '.'));
        if (bytes == null) {
            throw new ClassNotFoundException(name);
        }
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, readerFlags);
        return node;
    }

    // --- IClassTracker

    @Override
    public void registerInvalidClass(String className) {
    }

    @Override
    public boolean isClassLoaded(String className) {
        return game().isClassLoaded(className);
    }

    @Override
    public String getClassRestrictions(String className) {
        return "";
    }

    // --- ITransformerProvider (no other transformers)

    @Override
    public Collection<ITransformer> getTransformers() {
        return Collections.emptyList();
    }

    @Override
    public Collection<ITransformer> getDelegatedTransformers() {
        return Collections.emptyList();
    }

    @Override
    public void addTransformerExclusion(String name) {
    }
}
