package dev.lumora.loader.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mojang's official client mappings (ProGuard format, {@code downloads.client_mappings} of a version JSON), read in
 * the direction the game needs at run time: obfuscated name -> Mojang name.
 * <pre>
 * net.minecraft.client.Minecraft -> fgo:
 *     int frames -> a
 *     12:34:void tick() -> b
 * </pre>
 * Members are keyed by their obfuscated owner, name and obfuscated descriptor, so two fields or methods with the same
 * obfuscated name but different types stay apart.
 */
final class MojangMappings {
    /** obfuscated internal name -> Mojang internal name */
    final Map<String, String> classes = new HashMap<>();
    /** "owner.name:desc" (all obfuscated) -> Mojang name */
    final Map<String, String> fields = new HashMap<>();
    final Map<String, String> methods = new HashMap<>();
    /**
     * Community names (Yarn) put classes into other packages than Mojang's original ones, so package-private and
     * protected access between them would fail; such mappings make every non-private class and member public.
     */
    boolean widenAccess;

    private MojangMappings() {
    }

    private record Member(String owner, String name, String obf, String type, String args, boolean method) {
    }

    /** ProGuard (Mojang) or Tiny v2 (first line "tiny	2	..."), detected from the content. */
    static MojangMappings readAny(Reader in) throws IOException {
        BufferedReader r = new BufferedReader(in);
        r.mark(256);
        String first = r.readLine();
        r.reset();
        return first != null && first.startsWith("tiny\t2\t") ? readTiny(r) : read(r);
    }

    /**
     * Tiny v2 with the namespaces {@code official} and {@code named} (e.g. Loom's merged Legacy Fabric mappings
     * "official intermediary named"); descriptors are in the first namespace, which must be {@code official}.
     */
    static MojangMappings readTiny(Reader in) throws IOException {
        MojangMappings m = new MojangMappings();
        m.widenAccess = true;
        BufferedReader r = new BufferedReader(in);
        String header = r.readLine();
        if (header == null || !header.startsWith("tiny\t2\t")) {
            throw new IOException("not a Tiny v2 file");
        }
        String[] h = header.split("\t");
        List<String> ns = java.util.Arrays.asList(h).subList(3, h.length);
        int from = ns.indexOf("official");
        int to = ns.indexOf("named");
        if (from != 0 || to < 0) {
            throw new IOException("Tiny mappings need \"official\" first and \"named\": " + ns);
        }
        String owner = null;
        String line;
        while ((line = r.readLine()) != null) {
            if (line.isEmpty()) {
                continue;
            }
            String[] p = line.split("\t", -1);
            if (!line.startsWith("\t") && p[0].equals("c")) {
                owner = p[1 + from];
                String named = p.length > 1 + to && !p[1 + to].isEmpty() ? p[1 + to] : owner;
                m.classes.put(owner, named);
            } else if (owner != null && line.startsWith("\t") && !line.startsWith("\t\t") && p.length >= 4 + to) {
                // "\tf\tdesc\tofficial\t...\tnamed" / "\tm\tdesc\tofficial\t...\tnamed"
                String kind = p[1];
                String desc = p[2];
                String obf = p[3 + from];
                String named = p[3 + to];
                if (named.isEmpty() || named.equals(obf)) {
                    continue;
                }
                if (kind.equals("f")) {
                    m.fields.put(owner + "." + obf + ":" + desc, named);
                } else if (kind.equals("m") && !obf.startsWith("<")) {
                    m.methods.put(owner + "." + obf + ":" + desc, named);
                }
            }
        }
        return m;
    }

    static MojangMappings read(Reader in) throws IOException {
        MojangMappings m = new MojangMappings();
        Map<String, String> namedToObf = new HashMap<>();
        java.util.List<Member> members = new java.util.ArrayList<>();
        String owner = null;
        BufferedReader r = new BufferedReader(in);
        String line;
        int n = 0;
        while ((line = r.readLine()) != null) {
            n++;
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int arrow = line.indexOf(" -> ");
            if (arrow < 0) {
                throw new IOException("mappings line " + n + ": no \" -> \"");
            }
            if (!line.startsWith(" ")) {
                // class: "a.b.Named -> obf:"
                if (!line.endsWith(":")) {
                    throw new IOException("mappings line " + n + ": class line without ':'");
                }
                String named = line.substring(0, arrow).replace('.', '/');
                String obf = line.substring(arrow + 4, line.length() - 1).replace('.', '/');
                m.classes.put(obf, named);
                namedToObf.put(named, obf);
                owner = obf;
                continue;
            }
            if (owner == null) {
                throw new IOException("mappings line " + n + ": member before any class");
            }
            String left = line.substring(0, arrow).strip();
            String obf = line.substring(arrow + 4).strip();
            // optional "12:34:" line numbers before methods
            int colon;
            while ((colon = left.indexOf(':')) >= 0 && colon < left.indexOf(' ') && left.substring(0, colon).chars().allMatch(Character::isDigit)) {
                left = left.substring(colon + 1);
            }
            int space = left.indexOf(' ');
            String type = left.substring(0, space);
            String rest = left.substring(space + 1);
            int paren = rest.indexOf('(');
            if (paren >= 0) {
                members.add(new Member(owner, rest.substring(0, paren), obf, type, rest.substring(paren + 1, rest.lastIndexOf(')')), true));
            } else {
                members.add(new Member(owner, rest, obf, type, null, false));
            }
        }
        for (Member mem : members) {
            String typeDesc = descriptor(mem.type(), namedToObf);
            if (mem.method()) {
                StringBuilder d = new StringBuilder("(");
                if (!mem.args().isEmpty()) {
                    for (String a : mem.args().split(",")) {
                        d.append(descriptor(a.strip(), namedToObf));
                    }
                }
                d.append(')').append(typeDesc);
                if (!mem.name().equals("<init>") && !mem.name().equals("<clinit>")) {
                    m.methods.put(mem.owner() + "." + mem.obf() + ":" + d, mem.name());
                }
            } else {
                m.fields.put(mem.owner() + "." + mem.obf() + ":" + typeDesc, mem.name());
            }
        }
        return m;
    }

    /** Java source type ("int[]", "net.minecraft.Foo") -> descriptor with obfuscated class names. */
    static String descriptor(String javaType, Map<String, String> namedToObf) {
        int dims = 0;
        String t = javaType;
        while (t.endsWith("[]")) {
            dims++;
            t = t.substring(0, t.length() - 2);
        }
        String base = switch (t) {
            case "void" -> "V";
            case "boolean" -> "Z";
            case "byte" -> "B";
            case "char" -> "C";
            case "short" -> "S";
            case "int" -> "I";
            case "long" -> "J";
            case "float" -> "F";
            case "double" -> "D";
            default -> {
                String internal = t.replace('.', '/');
                yield "L" + namedToObf.getOrDefault(internal, internal) + ";";
            }
        };
        return "[".repeat(dims) + base;
    }
}
