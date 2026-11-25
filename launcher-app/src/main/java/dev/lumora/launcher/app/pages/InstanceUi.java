package dev.lumora.launcher.app.pages;

import dev.lumora.launcher.app.AppContext;
import dev.lumora.launcher.app.ui.Theme;
import dev.lumora.launcher.app.ui.Ui;
import dev.lumora.launcher.core.i18n.Lang;
import dev.lumora.launcher.core.instance.Instance;
import dev.lumora.launcher.core.instance.InstanceKind;
import dev.lumora.launcher.core.stats.PlayStats;

import javax.swing.ImageIcon;
import javax.swing.JComponent;
import java.awt.Color;
import java.time.Instant;

/** Shared presentation of instances: colours per loader, icons, the one-line summary. */
final class InstanceUi {
    private InstanceUi() {
    }

    static Color kindColor(InstanceKind k) {
        return switch (k) {
            case LUMORA -> Theme.ACCENT;
            case VANILLA -> new Color(0x5BA84A);
            case FABRIC -> new Color(0xD8B47A);
            case QUILT -> new Color(0xA77BF3);
            case FORGE -> new Color(0x7F93B5);
            case NEOFORGE -> new Color(0xF09A3E);
        };
    }

    /** Own icon of the instance, else a tile with its first letter in the loader colour. */
    static JComponent icon(Instance i, int size) {
        if (i.iconFile() != null) {
            ImageIcon img = Ui.icon(i.iconFile(), size, size, false);
            if (img != null) {
                return Ui.imageTile(img, size, size);
            }
        }
        return Ui.letterTile(i.name, size, kindColor(i.kind()));
    }

    /** "Fabric 0.16.9 · 1.21.4" / "Lumora · 26.3". */
    static String summary(Instance i) {
        InstanceKind k = i.kind();
        String loader = k.label() + (k.modded() && i.loaderVersion != null ? " " + i.loaderVersion : "");
        return loader + " · " + i.minecraftVersion;
    }

    /** Where it came from: "Lumora", "Modpack", "Eigene Instanz". */
    static String origin(Instance i) {
        if (i.builtIn) {
            return Lang.t("instances.origin.builtin");
        }
        if ("mrpack".equals(i.source) || "modrinth".equals(i.source)) {
            return Lang.t("instances.origin.modpack");
        }
        return Lang.t("instances.origin.own");
    }

    /** "import:prism" -> "Prism Launcher". */
    static String importSource(String source) {
        String s = source == null ? "" : source.startsWith("import:") ? source.substring(7) : source;
        return s.matches("[a-z_]{1,24}") ? Lang.t("import.source." + s) : s;
    }

    /**
     * Group of an instance in the version menu and on the instance page: null for the player's own and the Lumora
     * versions, else where it was imported from ("CurseForge", "Prism Launcher", "Modrinth" for modpacks …).
     */
    static String importGroup(Instance i) {
        if (i.builtIn || i.source == null || "archive".equals(i.source)) {
            return null;
        }
        if (i.source.startsWith("import:")) {
            return importSource(i.source);
        }
        return switch (i.source) {
            case "curseforge" -> "CurseForge";
            case "modrinth", "mrpack" -> "Modrinth";
            default -> null;
        };
    }

    static String lastPlayed(AppContext ctx, Instance i) {
        PlayStats.Entry e = ctx.play.stats().get(i.key());
        Instant last = e == null || e.lastPlayed == 0 ? null : e.lastPlayedInstant();
        return last == null ? Lang.t("instances.never_played") : Lang.t("instances.last_played", PlayStats.ago(last, Instant.now()));
    }

    static long lastPlayedMillis(AppContext ctx, Instance i) {
        PlayStats.Entry e = ctx.play.stats().get(i.key());
        return e == null ? 0 : e.lastPlayed;
    }
}
