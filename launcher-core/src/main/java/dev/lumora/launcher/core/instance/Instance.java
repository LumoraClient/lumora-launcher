package dev.lumora.launcher.core.instance;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * One instance: a Minecraft version with a kind ({@link InstanceKind}), its own game folder and optional own launch
 * settings. Two sources:
 * <ul>
 *   <li><b>built-in</b>: one Lumora instance per Lumora version (and Vanilla where the player used it). They keep the
 *       folders of {@link dev.lumora.launcher.core.InstanceLayout} and the launcher's global settings; nothing moves.</li>
 *   <li><b>own</b>: created by the player (any kind), stored as {@code custom-instances/<id>/instance.json}.</li>
 * </ul>
 * The non-transient fields are the {@code instance.json} format. Null settings mean "as in the launcher settings".
 */
public final class Instance {
    public static final int FORMAT = 1;
    /** Prefix of the selection key of own instances ({@code custom:<id>}); built-in instances use their instance id. */
    public static final String CUSTOM_PREFIX = "custom:";

    public int format = FORMAT;
    public String id;
    public String name;
    public String kind;
    public String minecraftVersion;
    /** Loader version; null = the loader's recommended version at install time (then written here). */
    public String loaderVersion;
    /** Keep {@link #loaderVersion} even if a newer recommended one appears. */
    public boolean loaderPinned;
    /** Icon file name inside the instance folder (e.g. {@code icon.png}), null = generated icon. */
    public String icon;
    /** Own Java executable, null = the version's official runtime (or the launcher setting). */
    public String javaPath;
    public Integer minMemoryMb;
    public Integer maxMemoryMb;
    public Integer windowWidth;
    public Integer windowHeight;
    public List<String> jvmArgs = new ArrayList<>();
    /** Where it came from: null (created by hand), "mrpack", "modrinth". */
    public String source;
    /** Modpack project and version id when {@link #source} is a platform. */
    public String sourceProject;
    public String sourceVersion;
    /** Free-form group name for the instance page. */
    public String group;
    /** ISO-8601 creation time. */
    public String created;

    /** Built-in instance: the Lumora profile id behind it (null for own instances). */
    public transient String profileId;
    public transient boolean builtIn;
    /** Own instances: the instance folder (instance.json, icon, minecraft/). */
    public transient Path dir;

    public InstanceKind kind() {
        InstanceKind k = InstanceKind.of(kind);
        return k != null ? k : InstanceKind.VANILLA;
    }

    /** Selection key stored in the launcher settings. */
    public String key() {
        return builtIn ? id : CUSTOM_PREFIX + id;
    }

    /** Game folder of an own instance ({@code --gameDir}); built-in instances use their InstanceLayout. */
    public Path gameDir() {
        return dir == null ? null : dir.resolve("minecraft");
    }

    public Path iconFile() {
        return dir == null || icon == null ? null : dir.resolve(icon);
    }

    public Instance copy() {
        Instance c = new Instance();
        c.format = format;
        c.id = id;
        c.name = name;
        c.kind = kind;
        c.minecraftVersion = minecraftVersion;
        c.loaderVersion = loaderVersion;
        c.loaderPinned = loaderPinned;
        c.icon = icon;
        c.javaPath = javaPath;
        c.minMemoryMb = minMemoryMb;
        c.maxMemoryMb = maxMemoryMb;
        c.windowWidth = windowWidth;
        c.windowHeight = windowHeight;
        c.jvmArgs = jvmArgs == null ? new ArrayList<>() : new ArrayList<>(jvmArgs);
        c.source = source;
        c.sourceProject = sourceProject;
        c.sourceVersion = sourceVersion;
        c.group = group;
        c.created = created;
        c.profileId = profileId;
        c.builtIn = builtIn;
        c.dir = dir;
        return c;
    }

    @Override
    public String toString() {
        return key() + " (" + kind + " " + minecraftVersion + ")";
    }
}
