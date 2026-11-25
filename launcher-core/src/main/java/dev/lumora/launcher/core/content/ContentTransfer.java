package dev.lumora.launcher.core.content;

import dev.lumora.launcher.core.i18n.Lang;
import dev.lumora.launcher.core.instance.InstanceKind;
import dev.lumora.launcher.core.platform.ProjectType;
import dev.lumora.launcher.core.util.FileUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Copies chosen files (mods, packs) from one instance into another, with their origin, so the target can update them
 * too. Nothing is overwritten and nothing incompatible is copied: a mod for another loader or content the target kind
 * cannot use is skipped with a reason.
 */
public final class ContentTransfer {
    public record Result(List<String> copied, List<String> skipped) {
    }

    private ContentTransfer() {
    }

    public static Result copy(List<InstalledContent.Item> items, Path fromGame, InstanceKind targetKind, String targetMc, Path toGame) throws IOException {
        List<String> copied = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        ContentManifest from = ContentManifest.load(fromGame);
        ContentManifest to = ContentManifest.load(toGame);
        for (InstalledContent.Item item : items) {
            ProjectType t = item.type();
            if (!CompatibilityChecker.supports(targetKind, t)) {
                skipped.add(Lang.t("transfer.skip.type", item.displayName(), targetKind.label()));
                continue;
            }
            if (t == ProjectType.MOD && item.mod() != null
                    && item.mod().loaders().stream().noneMatch(targetKind.modLoaders(targetMc)::contains)) {
                skipped.add(Lang.t("transfer.skip.loader", item.displayName(), targetKind.label()));
                continue;
            }
            Path dest = FileUtil.resolveInside(toGame, t.folder() + "/" + item.fileName());
            String base = item.fileName().endsWith(InstalledContent.DISABLED)
                    ? item.fileName().substring(0, item.fileName().length() - InstalledContent.DISABLED.length()) : item.fileName();
            if (Files.exists(dest) || Files.exists(toGame.resolve(t.folder()).resolve(base))
                    || Files.exists(toGame.resolve(t.folder()).resolve(base + InstalledContent.DISABLED))) {
                skipped.add(Lang.t("transfer.skip.exists", item.displayName()));
                continue;
            }
            Files.createDirectories(dest.getParent());
            Path tmp = dest.resolveSibling(dest.getFileName() + ".lumora-part");
            Files.copy(item.file(), tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            FileUtil.moveReplacing(tmp, dest);
            from.get(item.key()).ifPresent(e -> to.put(item.key(), e));
            copied.add(item.displayName());
        }
        if (!copied.isEmpty()) {
            to.save();
        }
        return new Result(copied, skipped);
    }
}
