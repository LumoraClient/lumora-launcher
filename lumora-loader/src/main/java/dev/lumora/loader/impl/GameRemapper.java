package dev.lumora.loader.impl;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Turns the obfuscated Minecraft client jar into one with Mojang's names ({@link MojangMappings}), so the Lumora client
 * (compiled against those names) and its mixins apply without a mod loader. Done once per game jar and mappings: the
 * result is cached next to the game jar under a name derived from both hashes.
 * <p>
 * Members are looked up through the class hierarchy of the jar (a call on a subclass may name a method declared in
 * its superclass or an interface); lambdas get the name of their functional interface's method. Signature files of
 * the original jar are dropped, the remapped classes would not match them.
 */
final class GameRemapper extends Remapper {
    /** Bump when the output changes, so old cache files are not used. */
    static final int FORMAT = 2;

    record Info(String superName, String[] interfaces) {
    }

    private final MojangMappings mappings;
    private final Map<String, Info> hierarchy;
    /** obfuscated owner -> obfuscated method name -> Mojang names (for lambdas, which carry no owner) */
    private final Map<String, Map<String, Set<String>>> methodsByName = new HashMap<>();

    GameRemapper(MojangMappings mappings, Map<String, Info> hierarchy) {
        super(Opcodes.ASM9);
        this.mappings = mappings;
        this.hierarchy = hierarchy;
        for (Map.Entry<String, String> e : mappings.methods.entrySet()) {
            String key = e.getKey();
            int dot = key.indexOf('.');
            String owner = key.substring(0, dot);
            String name = key.substring(dot + 1, key.indexOf(':', dot));
            methodsByName.computeIfAbsent(owner, k -> new HashMap<>()).computeIfAbsent(name, k -> new HashSet<>()).add(e.getValue());
        }
    }

    @Override
    public String map(String internalName) {
        return mappings.classes.getOrDefault(internalName, internalName);
    }

    @Override
    public String mapMethodName(String owner, String name, String descriptor) {
        if (name.startsWith("<") || owner.startsWith("[")) {
            return name;
        }
        String found = walk(owner, c -> mappings.methods.get(c + "." + name + ":" + descriptor));
        return found != null ? found : name;
    }

    @Override
    public String mapFieldName(String owner, String name, String descriptor) {
        String found = walk(owner, c -> mappings.fields.get(c + "." + name + ":" + descriptor));
        return found != null ? found : name;
    }

    @Override
    public String mapRecordComponentName(String owner, String name, String descriptor) {
        return mapFieldName(owner, name, descriptor);
    }

    @Override
    public String mapInvokeDynamicMethodName(String name, String descriptor, org.objectweb.asm.Handle bootstrap, Object... args) {
        // LambdaMetafactory: the first bootstrap argument is the exact type of the interface method
        if (args.length > 0 && args[0] instanceof Type sam && sam.getSort() == Type.METHOD
                && Type.getReturnType(descriptor).getSort() == Type.OBJECT) {
            return mapMethodName(Type.getReturnType(descriptor).getInternalName(), name, sam.getDescriptor());
        }
        return mapInvokeDynamicMethodName(name, descriptor);
    }

    @Override
    @SuppressWarnings("deprecation")
    public String mapInvokeDynamicMethodName(String name, String descriptor) {
        // the functional interface is the call site's return type; its method has the lambda's name
        Type ret = Type.getReturnType(descriptor);
        if (ret.getSort() != Type.OBJECT) {
            return name;
        }
        Set<String> names = new HashSet<>();
        walk(ret.getInternalName(), c -> {
            Map<String, Set<String>> byName = methodsByName.get(c);
            Set<String> s = byName == null ? null : byName.get(name);
            if (s != null) {
                names.addAll(s);
            }
            return null;
        });
        return names.size() == 1 ? names.iterator().next() : name;
    }

    private interface Lookup {
        String find(String owner);
    }

    /** First hit along owner, its superclasses and interfaces (breadth-first, only classes of the game jar). */
    private String walk(String owner, Lookup lookup) {
        Deque<String> todo = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();
        todo.add(owner);
        while (!todo.isEmpty()) {
            String c = todo.poll();
            if (!seen.add(c)) {
                continue;
            }
            String hit = lookup.find(c);
            if (hit != null) {
                return hit;
            }
            Info info = hierarchy.get(c);
            if (info != null) {
                if (info.superName() != null) {
                    todo.add(info.superName());
                }
                for (String i : info.interfaces()) {
                    todo.add(i);
                }
            }
        }
        return null;
    }

    /** The remapped jar for {@code game}, created in {@code cacheDir} when missing. */
    static Path remapCached(Path game, Path mappingsFile, Path cacheDir) throws IOException {
        String key = hash(game, mappingsFile);
        String base = game.getFileName().toString().replaceFirst("\\.jar$", "");
        Path out = cacheDir.resolve(base + "-lumora-" + key + ".jar");
        if (Files.isRegularFile(out) && Files.size(out) > 0) {
            return out;
        }
        Files.createDirectories(cacheDir);
        long start = System.nanoTime();
        MojangMappings m;
        try (var r = Files.newBufferedReader(mappingsFile, StandardCharsets.UTF_8)) {
            m = MojangMappings.readAny(r);
        }
        Path tmp = Files.createTempFile(cacheDir, base, ".part");
        try {
            int classes = remap(game, m, tmp);
            Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            System.out.println("[Lumora Loader] Minecraft jar mapped to Mojang names: " + classes + " classes, "
                    + (System.nanoTime() - start) / 1_000_000 + " ms -> " + out.getFileName());
        } finally {
            Files.deleteIfExists(tmp);
        }
        // older results for the same jar are no longer needed
        try (var s = Files.list(cacheDir)) {
            for (Path p : s.toList()) {
                String n = p.getFileName().toString();
                if (n.startsWith(base + "-lumora-") && n.endsWith(".jar") && !p.equals(out)) {
                    Files.deleteIfExists(p);
                }
            }
        } catch (IOException ignored) {
            // a still open old file stays until next time
        }
        return out;
    }

    static int remap(Path game, MojangMappings m, Path out) throws IOException {
        Map<String, byte[]> classes = new LinkedHashMap<>();
        Map<String, byte[]> resources = new LinkedHashMap<>();
        try (InputStream in = Files.newInputStream(game); ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                if (e.isDirectory()) {
                    continue;
                }
                String name = e.getName();
                byte[] data = zip.readAllBytes();
                if (name.endsWith(".class")) {
                    classes.put(name.substring(0, name.length() - 6), data);
                } else if (!isSignature(name)) {
                    resources.put(name, data);
                }
            }
        }
        Map<String, Info> hierarchy = new HashMap<>();
        for (Map.Entry<String, byte[]> c : classes.entrySet()) {
            ClassReader r = new ClassReader(c.getValue());
            hierarchy.put(r.getClassName(), new Info(r.getSuperName(), r.getInterfaces()));
        }
        GameRemapper remapper = new GameRemapper(m, hierarchy);
        try (OutputStream o = Files.newOutputStream(out); ZipOutputStream zip = new ZipOutputStream(o)) {
            for (Map.Entry<String, byte[]> r : resources.entrySet()) {
                zip.putNextEntry(new ZipEntry(r.getKey()));
                zip.write(r.getKey().equals("META-INF/MANIFEST.MF") ? stripDigests(r.getValue()) : r.getValue());
                zip.closeEntry();
            }
            List<String> written = new ArrayList<>();
            for (Map.Entry<String, byte[]> c : classes.entrySet()) {
                ClassReader reader = new ClassReader(c.getValue());
                ClassWriter writer = new ClassWriter(0);
                ClassVisitor v = new ClassRemapper(m.widenAccess ? new PublicAccess(writer) : writer, remapper);
                reader.accept(v, 0);
                String name = remapper.map(c.getKey()) + ".class";
                zip.putNextEntry(new ZipEntry(name));
                zip.write(writer.toByteArray());
                zip.closeEntry();
                written.add(name);
            }
            return written.size();
        }
    }

    private static boolean isSignature(String name) {
        String n = name.toUpperCase(java.util.Locale.ROOT);
        return n.startsWith("META-INF/") && (n.endsWith(".SF") || n.endsWith(".RSA") || n.endsWith(".DSA") || n.endsWith(".EC"));
    }

    /** The manifest without per-entry digests (they belonged to the signature). */
    private static byte[] stripDigests(byte[] manifest) {
        String text = new String(manifest, StandardCharsets.UTF_8);
        int section = text.indexOf("\r\n\r\n");
        int lf = text.indexOf("\n\n");
        int cut = section >= 0 ? section : lf;
        return (cut >= 0 ? text.substring(0, cut) + "\r\n\r\n" : text).getBytes(StandardCharsets.UTF_8);
    }

    private static String hash(Path game, Path mappingsFile) throws IOException {
        try {
            MessageDigest d = MessageDigest.getInstance("SHA-256");
            d.update(("format " + FORMAT + "\n").getBytes(StandardCharsets.UTF_8));
            for (Path p : List.of(game, mappingsFile)) {
                try (InputStream in = Files.newInputStream(p)) {
                    byte[] buf = new byte[1 << 16];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        d.update(buf, 0, n);
                    }
                }
            }
            return HexFormat.of().formatHex(d.digest()).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }
}
