package dev.lumora.launcher.core.mojang;

/** group:artifact:version[:classifier][@extension] */
public record MavenCoordinate(String group, String artifact, String version, String classifier, String extension) {

    public static MavenCoordinate parse(String name) {
        if (name == null) {
            throw new IllegalArgumentException("library name is null");
        }
        String ext = "jar";
        String coords = name;
        int at = name.indexOf('@');
        if (at >= 0) {
            ext = name.substring(at + 1);
            coords = name.substring(0, at);
        }
        String[] p = coords.split(":");
        if (p.length < 3 || p.length > 4) {
            throw new IllegalArgumentException("Invalid maven coordinate: " + name);
        }
        for (String part : p) {
            if (part.isEmpty() || part.contains("/") || part.contains("\\") || part.contains("..")) {
                throw new IllegalArgumentException("Invalid maven coordinate: " + name);
            }
        }
        return new MavenCoordinate(p[0], p[1], p[2], p.length == 4 ? p[3] : null, ext);
    }

    public String path() {
        return group.replace('.', '/') + "/" + artifact + "/" + version + "/" + fileName();
    }

    public String fileName() {
        return artifact + "-" + version + (classifier != null ? "-" + classifier : "") + "." + extension;
    }

    /** {@code group:artifact:version[:classifier]}. */
    public String coordinates() {
        return group + ":" + artifact + ":" + version + (classifier != null ? ":" + classifier : "");
    }

    /** Same artifact with another classifier (native jars: "natives-windows"). */
    public MavenCoordinate withClassifier(String newClassifier) {
        return new MavenCoordinate(group, artifact, version, newClassifier, extension);
    }

    /** Identity used to de-duplicate libraries between a child profile (Fabric) and its parent (vanilla). */
    public String dedupeKey() {
        return group + ":" + artifact + (classifier != null ? ":" + classifier : "");
    }
}
