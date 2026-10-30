package dev.lumora.launcher.core.mojang;

import dev.lumora.launcher.core.download.Downloader;
import dev.lumora.launcher.core.util.Hashing;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Asset index document (assets/indexes/&lt;id&gt;.json) and conversion into verified download tasks. */
public final class AssetIndex {
    public static final String RESOURCES = "https://resources.download.minecraft.net/";
    private static final Pattern SHA1 = Pattern.compile("^[0-9a-f]{40}$");

    public Map<String, AssetObject> objects;

    public static final class AssetObject {
        public String hash;
        public long size;
    }

    public List<Downloader.Task> tasks(Path assetsDir) throws IOException {
        List<Downloader.Task> out = new ArrayList<>();
        if (objects == null) {
            throw new IOException("Asset index has no objects");
        }
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (AssetObject o : objects.values()) {
            if (o.hash == null || !SHA1.matcher(o.hash).matches()) {
                throw new IOException("Asset index contains an invalid hash: " + o.hash);
            }
            if (!seen.add(o.hash)) {
                continue;
            }
            String rel = o.hash.substring(0, 2) + "/" + o.hash;
            out.add(new Downloader.Task(URI.create(RESOURCES + rel), assetsDir.resolve("objects").resolve(rel),
                    Hashing.Expected.sha1(o.hash), o.size));
        }
        return out;
    }
}
