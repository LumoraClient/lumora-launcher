package dev.lumora.launcher.app.pages;

import dev.lumora.launcher.app.ui.Card;
import dev.lumora.launcher.app.ui.Theme;
import dev.lumora.launcher.app.ui.Ui;
import dev.lumora.launcher.core.content.DependencyResolver;
import dev.lumora.launcher.core.download.Downloader;
import dev.lumora.launcher.core.i18n.Lang;
import dev.lumora.launcher.core.platform.Platform;
import dev.lumora.launcher.core.platform.PlatformModel.ProjectFile;
import dev.lumora.launcher.core.platform.PlatformModel.ProjectSummary;
import dev.lumora.launcher.core.platform.PlatformModel.ProjectVersion;
import dev.lumora.launcher.core.util.FileUtil;
import dev.lumora.launcher.core.util.Log;

import javax.swing.JDialog;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * A modpack from Entdecken: downloads the version's .mrpack from Modrinth (checked against its SHA-512) into the
 * download cache and hands it to the import preview, which creates a new instance.
 */
final class ModpackInstall {
    private ModpackInstall() {
    }

    /** @param version a chosen version, or null for the newest release */
    static void start(PageHost host, ExecutorService io, ProjectSummary pack, ProjectVersion version) {
        JDialog wait = new JDialog(host.frame(), Lang.t("modpack.loading.title"), java.awt.Dialog.ModalityType.MODELESS);
        Card c = new Card(new BorderLayout(0, 10)).fill(Theme.SURFACE).outline(null).radius(0).padding(18, 22, 18, 22);
        c.add(Ui.label(Lang.t("modpack.loading", pack.title()), 14, java.awt.Font.PLAIN, Theme.TEXT), BorderLayout.NORTH);
        JProgressBar bar = new JProgressBar(0, 1000);
        bar.setIndeterminate(true);
        c.add(bar, BorderLayout.CENTER);
        wait.setContentPane(c);
        wait.setSize(420, 130);
        wait.setLocationRelativeTo(host.frame());
        wait.setVisible(true);
        io.submit(() -> {
            try {
                ProjectVersion v = version;
                if (v == null) {
                    List<ProjectVersion> all = host.ctx().repository.require(pack.platform()).versions(pack.id(), List.of(), List.of());
                    v = DependencyResolver.best(all);
                }
                if (v == null) {
                    throw new IOException(Lang.t("modpack.no_version"));
                }
                // Modrinth: the .mrpack of the version; CurseForge: the pack zip (manifest.json)
                String ext = pack.platform() == Platform.CURSEFORGE ? ".zip" : ".mrpack";
                ProjectFile f = v.files().stream().filter(x -> x.fileName().toLowerCase(java.util.Locale.ROOT).endsWith(ext)).findFirst()
                        .orElse(v.primaryFile());
                if (f == null || !f.fileName().toLowerCase(java.util.Locale.ROOT).endsWith(ext)) {
                    throw new IOException(Lang.t("modpack.no_file"));
                }
                if (f.url() == null) {
                    throw new IOException(Lang.t("content.error.no_download", pack.title()));
                }
                String hash = f.sha512() != null ? f.sha512() : f.sha1();
                Path target = FileUtil.resolveInside(host.ctx().paths.modCache(), "modpacks/" + hash.substring(0, 32) + ext);
                host.ctx().downloader.download(new Downloader.Task(URI.create(f.url()), target,
                        dev.lumora.launcher.core.content.ContentInstaller.expected(f), f.size()), null);
                ProjectVersion chosen = v;
                SwingUtilities.invokeLater(() -> {
                    wait.dispose();
                    MrpackDialog.show(host, io, target, pack.id(), chosen.id(), host::openInstance);
                });
            } catch (IOException | RuntimeException e) {
                Log.warn("Modpack download failed: " + e.getMessage());
                SwingUtilities.invokeLater(() -> {
                    wait.dispose();
                    host.error(Lang.t("modpack.failed"), Lang.t("modpack.failed.text", pack.title(), e.getMessage()));
                });
            }
        });
    }
}
