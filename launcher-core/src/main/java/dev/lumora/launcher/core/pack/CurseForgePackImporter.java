package dev.lumora.launcher.core.pack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.lumora.launcher.core.i18n.Lang;
import dev.lumora.launcher.core.instance.InstanceKind;
import dev.lumora.launcher.core.platform.CurseForgeProvider;
import dev.lumora.launcher.core.platform.PlatformModel.ProjectFile;
import dev.lumora.launcher.core.platform.PlatformModel.ProjectSummary;
import dev.lumora.launcher.core.platform.PlatformModel.ProjectVersion;
import dev.lumora.launcher.core.platform.ProjectType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;

/**
 * CurseForge modpacks (a zip with {@code manifest.json}, "minecraftModpack"): the manifest names project and file ids,
 * which are resolved through the CurseForge API (the player's own key). The result is the same preview the .mrpack
 * import shows and is imported by {@link MrpackImporter#importPack}. Files whose author does not allow downloads through
 * other apps are listed by name and page and left out; the player can add them by hand later.
 */
public final class CurseForgePackImporter {
    private CurseForgePackImporter() {
    }

    /** Whether a zip is a CurseForge modpack (manifest.json of type minecraftModpack). */
    public static boolean isCurseForgePack(Path file) {
        try (ZipFile zip = new ZipFile(file.toFile())) {
            byte[] raw = SafeZip.read(zip, "manifest.json", 4 * 1024 * 1024);
            if (raw == null) {
                return false;
            }
            JsonObject o = JsonParser.parseString(new String(raw, StandardCharsets.UTF_8)).getAsJsonObject();
            return o.has("minecraft") && (!o.has("manifestType") || "minecraftModpack".equals(o.get("manifestType").getAsString()));
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /** "forge-47.2.0" → kind and Maven version as the loaders install it. */
    static Object[] loader(String id, String mc) {
        String lower = id == null ? "" : id.toLowerCase(java.util.Locale.ROOT);
        int dash = lower.indexOf('-');
        String name = dash < 0 ? lower : lower.substring(0, dash);
        String version = dash < 0 ? null : id.substring(dash + 1);
        InstanceKind kind = switch (name) {
            case "forge" -> InstanceKind.FORGE;
            case "neoforge" -> InstanceKind.NEOFORGE;
            case "fabric" -> InstanceKind.FABRIC;
            case "quilt" -> InstanceKind.QUILT;
            default -> null;
        };
        if ((kind == InstanceKind.FABRIC || kind == InstanceKind.QUILT) && version != null && !mc.isEmpty() && version.endsWith("-" + mc)) {
            version = version.substring(0, version.length() - mc.length() - 1); // CurseForge app: "fabric-0.18.4-1.20.1"
        }
        if (kind == InstanceKind.FORGE && version != null && !version.startsWith(mc + "-")) {
            version = mc + "-" + version;
        }
        if (kind == InstanceKind.NEOFORGE && "1.20.1".equals(mc) && version != null && !version.startsWith(mc + "-")) {
            version = mc + "-" + version; // NeoForge for 1.20.1 still uses Forge's artifact naming
        }
        return new Object[]{kind, version};
    }

    public static MrpackImporter.Preview analyze(Path file, CurseForgeProvider cf) throws IOException {
        List<String> problems = new ArrayList<>();
        try (ZipFile zip = new ZipFile(file.toFile())) {
            byte[] raw = SafeZip.read(zip, "manifest.json", 4 * 1024 * 1024);
            if (raw == null) {
                throw new IOException(Lang.t("cfpack.error.no_manifest"));
            }
            JsonObject o;
            try {
                o = JsonParser.parseString(new String(raw, StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (JsonParseException | IllegalStateException e) {
                throw new IOException(Lang.t("mrpack.error.index", e.getMessage()), e);
            }
            JsonObject mcObj = o.getAsJsonObject("minecraft");
            String mc = mcObj == null ? null : str(mcObj, "version");
            if (mc == null || !mc.matches("[0-9A-Za-z._-]{1,32}")) {
                problems.add(Lang.t("mrpack.error.no_minecraft"));
            }
            InstanceKind kind = InstanceKind.VANILLA;
            String loaderVersion = null;
            if (mcObj != null && mcObj.has("modLoaders") && mcObj.get("modLoaders").isJsonArray()) {
                for (JsonElement l : mcObj.getAsJsonArray("modLoaders")) {
                    JsonObject lo = l.getAsJsonObject();
                    boolean primary = !lo.has("primary") || lo.get("primary").getAsBoolean();
                    Object[] r = loader(str(lo, "id"), mc);
                    if (primary && r[0] != null) {
                        kind = (InstanceKind) r[0];
                        loaderVersion = (String) r[1];
                    } else if (primary) {
                        problems.add(Lang.t("mrpack.error.dependency", String.valueOf(str(lo, "id"))));
                    }
                }
            }
            if (loaderVersion != null && !loaderVersion.matches("[A-Za-z0-9._+-]{1,80}")) {
                problems.add(Lang.t("mrpack.error.loader_version", loaderVersion));
                loaderVersion = null;
            }
            String overrides = o.has("overrides") ? str(o, "overrides") : "overrides";
            if (overrides == null || overrides.isBlank()) {
                overrides = "overrides";
            }
            overrides = SafeZip.checkedName(overrides).replaceAll("/+$", "");
            List<Long> fileIds = new ArrayList<>();
            Map<Long, Boolean> required = new HashMap<>();
            if (o.has("files") && o.get("files").isJsonArray()) {
                for (JsonElement f : o.getAsJsonArray("files")) {
                    JsonObject fo = f.getAsJsonObject();
                    long fileId = fo.get("fileID").getAsLong();
                    fileIds.add(fileId);
                    required.put(fileId, !fo.has("required") || fo.get("required").getAsBoolean());
                }
            }
            List<MrpackImporter.PackFile> files = new ArrayList<>();
            List<String> manual = new ArrayList<>();
            Map<String, Integer> folders = new LinkedHashMap<>();
            long bytes = 0;
            if (!fileIds.isEmpty()) {
                if (cf == null || !cf.available()) {
                    problems.add(Lang.t("cfpack.error.no_key"));
                } else {
                    List<ProjectVersion> resolved = cf.files(fileIds);
                    Map<String, ProjectSummary> projects = new HashMap<>();
                    for (ProjectSummary s : cf.projects(resolved.stream().map(ProjectVersion::projectId).distinct().toList())) {
                        projects.put(s.id(), s);
                    }
                    for (ProjectVersion v : resolved) {
                        ProjectFile pf = v.primaryFile();
                        ProjectSummary s = projects.get(v.projectId());
                        ProjectType type = s == null ? ProjectType.MOD : s.type();
                        String folder = type.folder() == null ? "mods" : type.folder();
                        String title = s != null ? s.title() : pf.fileName();
                        if (pf.url() == null) {
                            String page = s != null ? cf.projectPage(type, s.slug()) : "https://www.curseforge.com/minecraft";
                            manual.add(title + " (" + pf.fileName() + ") – " + page);
                            continue;
                        }
                        String path = folder + "/" + SafeZip.checkedName(pf.fileName().replace('/', '_'));
                        boolean optional = !required.getOrDefault(Long.parseLong(v.id()), true);
                        files.add(new MrpackImporter.PackFile(path, pf.url(), pf.sha1(), null, pf.size(), optional,
                                "curseforge", v.projectId(), v.id()));
                        bytes += Math.max(0, pf.size());
                        folders.merge(folder, 1, Integer::sum);
                    }
                    if (resolved.size() < fileIds.size()) {
                        problems.add(Lang.t("cfpack.error.missing", fileIds.size() - resolved.size()));
                    }
                }
            }
            int overrideFiles = 0;
            long overrideBytes = 0;
            for (var en = zip.entries(); en.hasMoreElements(); ) {
                var e = en.nextElement();
                String n = SafeZip.checkedName(e.getName());
                if (!e.isDirectory() && n.startsWith(overrides + "/")) {
                    overrideFiles++;
                    overrideBytes += Math.max(0, e.getSize());
                }
            }
            byte[] icon = null;
            try {
                icon = SafeZip.read(zip, overrides + "/icon.png", 1024 * 1024);
            } catch (IOException e) {
                // no or too large icon
            }
            String name = str(o, "name");
            return new MrpackImporter.Preview(file, name == null || name.isBlank() ? file.getFileName().toString() : name.strip(), str(o, "version"),
                    str(o, "author") == null ? null : Lang.t("cfpack.by", str(o, "author")), mc, kind, loaderVersion, files, folders,
                    overrideFiles, bytes, overrideBytes, icon, problems, 0, overrides, manual, "curseforge",
                    str(o, "author") == null || str(o, "author").isBlank() ? null : str(o, "author").strip());
        } catch (java.util.zip.ZipException e) {
            throw new IOException(Lang.t("mrpack.error.zip"), e);
        } catch (IllegalStateException | ClassCastException | UnsupportedOperationException | NumberFormatException | NullPointerException e) {
            throw new IOException(Lang.t("mrpack.error.index", e.getMessage()), e);
        }
    }

    private static String str(JsonObject o, String k) {
        JsonElement e = o == null ? null : o.get(k);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }
}
