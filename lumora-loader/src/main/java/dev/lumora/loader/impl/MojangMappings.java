package dev.lumora.loader.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
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

    private MojangMappings() {
    }

    private record Member(String owner, String name, String obf, String type, String args, boolean method) {
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
