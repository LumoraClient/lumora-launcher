package dev.lumora.launcher.app.pages;

import dev.lumora.launcher.app.ui.Card;
import dev.lumora.launcher.app.ui.Icons;
import dev.lumora.launcher.app.ui.Markdown;
import dev.lumora.launcher.app.ui.RemoteImages;
import dev.lumora.launcher.app.ui.TabBar;
import dev.lumora.launcher.app.ui.Theme;
import dev.lumora.launcher.app.ui.Ui;
import dev.lumora.launcher.core.content.DependencyResolver;
import dev.lumora.launcher.core.i18n.Lang;
import dev.lumora.launcher.core.instance.Instance;
import dev.lumora.launcher.core.instance.InstanceKind;
import dev.lumora.launcher.core.platform.ModPlatformProvider;
import dev.lumora.launcher.core.platform.ModrinthProvider;
import dev.lumora.launcher.core.platform.PlatformModel;
import dev.lumora.launcher.core.platform.PlatformModel.Project;
import dev.lumora.launcher.core.platform.PlatformModel.ProjectSummary;
import dev.lumora.launcher.core.platform.PlatformModel.ProjectVersion;
import dev.lumora.launcher.core.platform.ProjectType;
import dev.lumora.launcher.core.util.Log;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * Detail page of a project: banner, icon, authors, platform, statistics, then tabs for the description (Markdown shown
 * safely, without loading anything by itself), the screenshot gallery as a carousel, all versions with Minecraft
 * versions and loaders, the dependencies of the version that fits the instance, the change log and the project links.
 * Without pictures a calm placeholder is shown instead of broken images.
 */
final class ProjectView extends JPanel {
    private final PageHost host;
    private final ExecutorService io;
    private final RemoteImages images;
    private final ProjectSummary summary;
    private final ProjectType type;
    private final Instance target;
    private final Runnable back;
    private final Runnable install;
    private Project project;
    private List<ProjectVersion> versions = List.of();
    private List<String> authors = List.of();
    private final JPanel body = new JPanel(new BorderLayout());
    private String tab = "description";
    private int galleryIndex;

    ProjectView(PageHost host, ExecutorService io, RemoteImages images, ProjectSummary summary, ProjectType type, Instance target,
                Runnable back, Runnable install) {
        super(new BorderLayout(0, 12));
        this.host = host;
        this.io = io;
        this.images = images;
        this.summary = summary;
        this.type = type;
        this.target = target;
        this.back = back;
        this.install = install;
        setOpaque(false);
        body.setOpaque(false);
        add(header(summary, null), BorderLayout.NORTH);
        add(body, BorderLayout.CENTER);
        body.add(Ui.label(Lang.t("common.loading"), 14, Font.PLAIN, Theme.MUTED), BorderLayout.NORTH);
        load();
    }

    private ModPlatformProvider provider() throws IOException {
        return host.ctx().repository.require(summary.platform());
    }

    private void load() {
        io.submit(() -> {
            try {
                ModPlatformProvider p = provider();
                Project pr = p.project(summary.id());
                List<ProjectVersion> vs = p.versions(summary.id(), List.of(), List.of());
                List<String> people = List.of();
                if (p instanceof ModrinthProvider m) {
                    try {
                        people = m.authors(summary.id());
                    } catch (IOException e) {
                        people = summary.author() == null ? List.of() : List.of(summary.author());
                    }
                }
                List<String> who = people;
                SwingUtilities.invokeLater(() -> {
                    project = pr;
                    versions = vs;
                    authors = who;
                    removeAll();
                    add(header(pr.summary(), pr), BorderLayout.NORTH);
                    add(body, BorderLayout.CENTER);
                    showTab();
                    revalidate();
                    repaint();
                });
            } catch (IOException | RuntimeException e) {
                Log.warn("Project page failed: " + e.getMessage());
                SwingUtilities.invokeLater(() -> {
                    body.removeAll();
                    JPanel col = Ui.column();
                    col.add(Ui.wrapText(Lang.t("project.error", e.getMessage()), 13, Theme.FAIL));
                    col.add(Box.createVerticalStrut(8));
                    col.add(Ui.button(Lang.t("common.retry"), Icons.Kind.REFRESH, this::load));
                    body.add(col, BorderLayout.NORTH);
                    body.revalidate();
                });
            }
        });
    }

    // ---------------------------------------------------------------- header

    private JComponent header(ProjectSummary s, Project pr) {
        JPanel head = Ui.column();
        JButton backButton = Ui.button(Lang.t("project.back"), null, back);
        backButton.putClientProperty("JButton.buttonType", "borderless");
        head.add(backButton);
        head.add(Box.createVerticalStrut(6));
        Banner banner = new Banner();
        banner.setAlignmentX(Component.LEFT_ALIGNMENT);
        String bannerUrl = s.featuredImage();
        if (pr != null && !pr.gallery().isEmpty()) {
            // the original picture of the featured (else first) gallery image, not the small preview
            bannerUrl = pr.gallery().stream().filter(PlatformModel.GalleryImage::featured).findFirst().orElse(pr.gallery().get(0)).large();
        }
        if (bannerUrl != null) {
            images.load(bannerUrl, 1200, 200, false, img -> {
                banner.image = img;
                banner.repaint();
            });
        }
        head.add(banner);
        head.add(Box.createVerticalStrut(10));
        JPanel row = new JPanel(new BorderLayout(14, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel iconHolder = new JPanel(new BorderLayout());
        iconHolder.setOpaque(false);
        iconHolder.add(Ui.letterTile(s.title(), 80, Theme.ACCENT_2));
        images.load(s.iconUrl(), 80, 80, false, img -> {
            iconHolder.removeAll();
            iconHolder.add(Ui.imageTile(img, 80, 80));
            iconHolder.revalidate();
            iconHolder.repaint();
        });
        row.add(iconHolder, BorderLayout.WEST);
        JPanel text = Ui.column();
        text.add(Box.createVerticalStrut(6));
        text.add(Ui.heading(s.title()));
        String by = !authors.isEmpty() ? String.join(", ", authors.subList(0, Math.min(4, authors.size()))) : s.author();
        JPanel meta = Ui.row(8);
        if (by != null) {
            meta.add(Ui.label(Lang.t("discover.by", by), 13, Font.PLAIN, Theme.MUTED));
        }
        meta.add(Ui.chip(s.platform().label(), Theme.ACCENT_2, Theme.ACCENT_TEXT));
        meta.add(Ui.label("⬇ " + Ui.count(s.downloads()) + "   ♥ " + Ui.count(s.follows()), 13, Font.BOLD, Theme.MUTED));
        text.add(meta);
        row.add(text, BorderLayout.CENTER);
        JPanel actions = Ui.row(8);
        JPanel actionsWrap = Ui.column();
        actionsWrap.add(Box.createVerticalStrut(10));
        boolean fav = host.ctx().config.favoriteProjects.contains(s.key());
        JButton star = Ui.button(Lang.t(fav ? "discover.unfavorite" : "discover.favorite"), null, null);
        star.setIcon(Icons.of(Icons.Kind.R_STAR, 15, fav ? Theme.WARN : Theme.MUTED));
        star.addActionListener(e -> {
            List<String> favs = host.ctx().config.favoriteProjects;
            if (!favs.remove(s.key())) {
                favs.add(0, s.key());
            }
            host.saveConfig();
            boolean now = favs.contains(s.key());
            star.setText(Lang.t(now ? "discover.unfavorite" : "discover.favorite"));
            star.setIcon(Icons.of(Icons.Kind.R_STAR, 15, now ? Theme.WARN : Theme.MUTED));
        });
        actions.add(star);
        String page = pr != null ? pr.pageUrl() : ModrinthProvider.pageUrl(type, s.slug());
        actions.add(Ui.button(Lang.t("project.open_page"), Icons.Kind.GLOBE, () -> host.browse(page)));
        actions.add(Ui.primary(type == ProjectType.MODPACK ? Lang.t("discover.install_pack") : Lang.t("discover.install"), Icons.Kind.DOWNLOAD, install));
        actionsWrap.add(actions);
        row.add(actionsWrap, BorderLayout.EAST);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        head.add(row);
        if (s.description() != null) {
            head.add(Box.createVerticalStrut(8));
            head.add(Ui.wrapText(s.description(), 13.5f, Theme.TEXT));
        }
        head.add(Box.createVerticalStrut(10));
        List<String> ids = List.of("description", "gallery", "versions", "dependencies", "changelog", "info");
        TabBar tabs = new TabBar(ids, ids.stream().map(i -> Lang.t("project.tab." + i)).toList(), tab, id -> {
            tab = id;
            showTab();
        });
        tabs.setAlignmentX(Component.LEFT_ALIGNMENT);
        head.add(tabs);
        return head;
    }

    /** Wide picture at the top; without one a calm gradient in the accent colour. */
    private static final class Banner extends JComponent {
        ImageIcon image;

        Banner() {
            setPreferredSize(new Dimension(800, 150));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.clip(new java.awt.geom.RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 14, 14));
            g.setPaint(new java.awt.GradientPaint(0, 0, Theme.alpha(Theme.ACCENT_DEEP, 140), getWidth(), getHeight(), Theme.alpha(Theme.ACCENT_2, 60)));
            g.fillRect(0, 0, getWidth(), getHeight());
            if (image != null) {
                int iw = image.getIconWidth();
                int ih = image.getIconHeight();
                double scale = Math.max((double) getWidth() / iw, (double) getHeight() / ih);
                int w = (int) (iw * scale);
                int h = (int) (ih * scale);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(image.getImage(), (getWidth() - w) / 2, (getHeight() - h) / 2, w, h, null);
            }
            g.setPaint(new java.awt.GradientPaint(0, getHeight() * 0.4f, new Color(0, 0, 0, 0), 0, getHeight(), Theme.alpha(Theme.BG, 230)));
            g.fillRect(0, 0, getWidth(), getHeight());
            g.dispose();
        }
    }

    // ---------------------------------------------------------------- tabs

    private void showTab() {
        body.removeAll();
        if (project == null) {
            body.add(Ui.label(Lang.t("common.loading"), 14, Font.PLAIN, Theme.MUTED), BorderLayout.NORTH);
        } else {
            JComponent c = switch (tab) {
                case "gallery" -> gallery();
                case "versions" -> versionsTab();
                case "dependencies" -> dependencies();
                case "changelog" -> changelog();
                case "info" -> info();
                default -> html(Markdown.toHtml(project.body()));
            };
            body.add(c, BorderLayout.CENTER);
        }
        body.revalidate();
        body.repaint();
    }

    private JComponent html(String html) {
        JEditorPane pane = new JEditorPane();
        pane.setEditable(false);
        pane.setOpaque(false);
        HTMLEditorKit kit = new HTMLEditorKit();
        kit.getStyleSheet().addRule(Markdown.css());
        pane.setEditorKit(kit);
        pane.setText("<html><body>" + (html.isBlank() ? "<p>" + Lang.t("project.no_description") + "</p>" : html) + "</body></html>");
        pane.setCaretPosition(0);
        pane.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED && e.getDescription() != null && e.getDescription().startsWith("https://")) {
                host.browse(e.getDescription());
            }
        });
        JPanel holder = new JPanel(new BorderLayout());
        holder.setOpaque(false);
        holder.add(pane, BorderLayout.CENTER);
        return Ui.scroll(holder);
    }

    private JComponent gallery() {
        List<PlatformModel.GalleryImage> pics = project.gallery();
        if (pics.isEmpty()) {
            return placeholder(Lang.t("project.gallery.empty"));
        }
        galleryIndex = Math.max(0, Math.min(galleryIndex, pics.size() - 1));
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setOpaque(false);
        Banner big = new Banner();
        big.setPreferredSize(new Dimension(820, 430));
        big.setMaximumSize(new Dimension(Integer.MAX_VALUE, 430));
        PlatformModel.GalleryImage current = pics.get(galleryIndex);
        images.load(current.large(), 1100, 600, true, img -> {
            big.image = img;
            big.repaint();
        });
        p.add(big, BorderLayout.CENTER);
        JPanel controls = new JPanel(new BorderLayout(10, 0));
        controls.setOpaque(false);
        JButton prev = Ui.button("‹", null, () -> {
            galleryIndex = (galleryIndex - 1 + pics.size()) % pics.size();
            showTab();
        });
        JButton next = Ui.button("›", null, () -> {
            galleryIndex = (galleryIndex + 1) % pics.size();
            showTab();
        });
        controls.add(prev, BorderLayout.WEST);
        JPanel caption = Ui.column();
        String title = current.title() != null && !current.title().isBlank() ? current.title() : Lang.t("project.gallery.image", galleryIndex + 1, pics.size());
        caption.add(Ui.label(title + "  (" + (galleryIndex + 1) + "/" + pics.size() + ")", 13.5f, Font.BOLD, Theme.TEXT));
        if (current.description() != null && !current.description().isBlank()) {
            caption.add(Ui.wrapText(current.description(), 12.5f, Theme.MUTED));
        }
        controls.add(caption, BorderLayout.CENTER);
        controls.add(next, BorderLayout.EAST);
        p.add(controls, BorderLayout.SOUTH);
        // thumbnails
        JPanel thumbs = Ui.row(6);
        for (int i = 0; i < pics.size() && i < 12; i++) {
            int idx = i;
            JPanel holder = new JPanel(new BorderLayout());
            holder.setOpaque(false);
            holder.setBorder(BorderFactory.createLineBorder(i == galleryIndex ? Theme.ACCENT : Theme.BORDER, 2));
            holder.add(Ui.imageTile(null, 96, 54));
            images.load(pics.get(i).url(), 96, 54, false, img -> {
                holder.removeAll();
                holder.add(Ui.imageTile(img, 96, 54));
                holder.revalidate();
                holder.repaint();
            });
            holder.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            holder.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    galleryIndex = idx;
                    showTab();
                }
            });
            thumbs.add(holder);
        }
        JPanel top = Ui.column();
        top.add(thumbs);
        top.add(Box.createVerticalStrut(8));
        p.add(top, BorderLayout.NORTH);
        return p;
    }

    private JComponent placeholder(String text) {
        Card c = new Card(new BorderLayout()).calm();
        JLabel l = Ui.label(text, 14, Font.PLAIN, Theme.MUTED);
        l.setIcon(Icons.of(Icons.Kind.IMAGE, 22, Theme.MUTED));
        l.setIconTextGap(10);
        c.add(l, BorderLayout.CENTER);
        c.setPreferredSize(new Dimension(600, 160));
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.add(c, BorderLayout.NORTH);
        return wrap;
    }

    private JComponent versionsTab() {
        JPanel col = Ui.column();
        DateTimeFormatter fmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Lang.locale()).withZone(java.time.ZoneId.systemDefault());
        List<String> accepted = type == ProjectType.MOD ? target.kind().modLoaders(target.minecraftVersion) : List.of();
        if (versions.isEmpty()) {
            col.add(Ui.label(Lang.t("project.versions.empty"), 13, Font.PLAIN, Theme.MUTED));
        }
        int shown = 0;
        for (ProjectVersion v : versions) {
            if (++shown > 150) {
                col.add(Ui.label(Lang.t("project.versions.more", versions.size() - 150), 12.5f, Font.PLAIN, Theme.MUTED));
                break;
            }
            boolean fits = v.gameVersions().contains(target.minecraftVersion)
                    && (accepted.isEmpty() || v.loaders().stream().anyMatch(accepted::contains));
            Card c = new Card(new BorderLayout(12, 0)).padding(8, 12, 8, 12);
            if (fits) {
                c.outline(Theme.alpha(Theme.OK, 140));
            }
            JPanel t = Ui.column();
            JPanel line = Ui.row(8);
            line.add(Ui.label(v.number(), 14, Font.BOLD, Theme.TEXT));
            if (v.channel() != PlatformModel.Channel.RELEASE) {
                line.add(Ui.chip(Lang.t("version.channel." + v.channel().name().toLowerCase(java.util.Locale.ROOT)), Theme.WARN, Theme.WARN));
            }
            if (fits) {
                line.add(Ui.chip(Lang.t("project.versions.fits", target.name), Theme.OK, Theme.OK));
            }
            t.add(line);
            t.add(Ui.label(loaderNames(v.loaders()) + " · " + summarize(v.gameVersions()) + " · "
                    + (v.published() == null ? "" : fmt.format(v.published())) + " · ⬇ " + Ui.count(v.downloads()), 12, Font.PLAIN, Theme.MUTED));
            c.add(t, BorderLayout.CENTER);
            if (type != ProjectType.MODPACK) {
                c.add(Ui.button(Lang.t("discover.install"), Icons.Kind.DOWNLOAD, () -> InstallDialog.show(host, io, List.of(summary), type, target, () -> { })),
                        BorderLayout.EAST);
            } else {
                c.add(Ui.button(Lang.t("discover.install_pack"), Icons.Kind.DOWNLOAD, () -> ModpackInstall.start(host, io, summary, v)), BorderLayout.EAST);
            }
            c.setAlignmentX(Component.LEFT_ALIGNMENT);
            c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
            col.add(c);
            col.add(Box.createVerticalStrut(6));
        }
        return Ui.scroll(col);
    }

    private ProjectVersion fitting() {
        List<String> accepted = type == ProjectType.MOD ? target.kind().modLoaders(target.minecraftVersion) : List.of();
        List<ProjectVersion> fit = versions.stream().filter(v -> v.gameVersions().contains(target.minecraftVersion)
                && (accepted.isEmpty() || v.loaders().stream().anyMatch(accepted::contains))).toList();
        ProjectVersion best = DependencyResolver.best(fit);
        return best != null ? best : DependencyResolver.best(versions);
    }

    private JComponent dependencies() {
        JPanel col = Ui.column();
        ProjectVersion v = fitting();
        if (v == null) {
            return placeholder(Lang.t("project.versions.empty"));
        }
        col.add(Ui.wrapText(Lang.t("project.deps.for", v.number(), target.name), 13, Theme.MUTED));
        col.add(Box.createVerticalStrut(8));
        List<PlatformModel.Dependency> deps = v.dependencies().stream().filter(d -> d.kind() != PlatformModel.DependencyKind.EMBEDDED).toList();
        if (deps.isEmpty()) {
            col.add(Ui.label(Lang.t("project.deps.none"), 13, Font.PLAIN, Theme.TEXT));
            return Ui.scroll(col);
        }
        JPanel list = Ui.column();
        col.add(list);
        list.add(Ui.label(Lang.t("common.loading"), 13, Font.PLAIN, Theme.MUTED));
        io.submit(() -> {
            List<String> ids = deps.stream().map(PlatformModel.Dependency::projectId).filter(java.util.Objects::nonNull).distinct().toList();
            Map<String, ProjectSummary> names = new java.util.HashMap<>();
            try {
                for (ProjectSummary s : provider().projects(ids)) {
                    names.put(s.id(), s);
                }
            } catch (IOException | RuntimeException e) {
                // ids are shown instead
            }
            SwingUtilities.invokeLater(() -> {
                list.removeAll();
                list.add(Ui.label(project.summary().title(), 14, Font.BOLD, Theme.TEXT));
                for (PlatformModel.Dependency d : deps) {
                    ProjectSummary s = d.projectId() == null ? null : names.get(d.projectId());
                    String name = s != null ? s.title() : d.projectId() != null ? d.projectId() : d.fileName();
                    JPanel row = Ui.row(8);
                    row.setBorder(BorderFactory.createEmptyBorder(4, 18, 4, 0));
                    row.add(Ui.label("├", 13, Font.PLAIN, Theme.MUTED));
                    row.add(Ui.label(name == null ? "?" : name, 13.5f, Font.PLAIN, Theme.TEXT));
                    String key = switch (d.kind()) {
                        case REQUIRED -> "project.deps.required";
                        case OPTIONAL -> "project.deps.optional";
                        case INCOMPATIBLE -> "project.deps.incompatible";
                        default -> "project.deps.embedded";
                    };
                    row.add(Ui.chip(Lang.t(key), d.kind() == PlatformModel.DependencyKind.INCOMPATIBLE ? Theme.FAIL
                            : d.kind() == PlatformModel.DependencyKind.REQUIRED ? Theme.ACCENT : Theme.MUTED, Theme.TEXT));
                    list.add(row);
                }
                list.add(Box.createVerticalStrut(8));
                list.add(Ui.wrapText(Lang.t("project.deps.hint"), 12.5f, Theme.MUTED));
                list.revalidate();
                list.repaint();
            });
        });
        return Ui.scroll(col);
    }

    /** CurseForge release notes, loaded once per page for the newest files (the API gives them per file). */
    private String curseForgeLog;

    private JComponent changelog() {
        if (summary.platform() == dev.lumora.launcher.core.platform.Platform.CURSEFORGE) {
            if (curseForgeLog == null) {
                curseForgeLog = "";
                List<ProjectVersion> newest = versions.subList(0, Math.min(8, versions.size()));
                io.submit(() -> {
                    StringBuilder sb = new StringBuilder();
                    try {
                        var cf = (dev.lumora.launcher.core.platform.CurseForgeProvider) provider();
                        for (ProjectVersion v : newest) {
                            String log = cf.changelog(v.projectId(), v.id());
                            if (log != null && !log.isBlank()) {
                                sb.append("<h3>").append(escape(v.number())).append("</h3>").append(Markdown.toHtml(log));
                            }
                        }
                    } catch (IOException | RuntimeException e) {
                        Log.warn("CurseForge change log failed: " + e.getMessage());
                    }
                    SwingUtilities.invokeLater(() -> {
                        curseForgeLog = sb.toString();
                        if ("changelog".equals(tab)) {
                            showTab();
                        }
                    });
                });
                return Ui.scroll(Ui.label(Lang.t("common.loading"), 13, Font.PLAIN, Theme.MUTED));
            }
            return curseForgeLog.isEmpty() ? placeholder(Lang.t("project.changelog.empty")) : html(curseForgeLog);
        }
        StringBuilder html = new StringBuilder();
        int n = 0;
        for (ProjectVersion v : versions) {
            if (v.changelog() == null || v.changelog().isBlank()) {
                continue;
            }
            if (++n > 15) {
                break;
            }
            html.append("<h3>").append(escape(v.number())).append("</h3>").append(Markdown.toHtml(v.changelog()));
        }
        if (n == 0) {
            return placeholder(Lang.t("project.changelog.empty"));
        }
        return html(html.toString());
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private JComponent info() {
        JPanel col = Ui.column();
        ProjectSummary s = project.summary();
        DateTimeFormatter fmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Lang.locale()).withZone(java.time.ZoneId.systemDefault());
        addInfo(col, Lang.t("project.info.license"), project.licenseName() != null ? project.licenseName() : s.license());
        addInfo(col, Lang.t("project.info.loaders"), loaderNames(s.loaders()));
        addInfo(col, Lang.t("project.info.versions"), summarize(s.gameVersions()));
        addInfo(col, Lang.t("project.info.client"), side(s.client()));
        addInfo(col, Lang.t("project.info.server"), side(s.server()));
        addInfo(col, Lang.t("project.info.downloads"), Ui.count(s.downloads()));
        if (s.created() != null) {
            addInfo(col, Lang.t("project.info.published"), fmt.format(s.created()));
        }
        if (s.updated() != null) {
            addInfo(col, Lang.t("project.info.updated"), fmt.format(s.updated()));
        }
        addInfo(col, Lang.t("project.info.categories"), String.join(", ", s.categories()));
        col.add(Ui.caption(Lang.t("project.links")));
        JPanel links = Ui.row(8);
        links.add(Ui.button(s.platform().label(), Icons.Kind.GLOBE, () -> host.browse(project.pageUrl())));
        for (Map.Entry<String, String> l : project.links().entrySet()) {
            links.add(Ui.button(Lang.t("project.link." + l.getKey()), Icons.Kind.GLOBE, () -> host.browse(l.getValue())));
        }
        for (String d : project.donations()) {
            links.add(Ui.button(Lang.t("project.link.donate"), Icons.Kind.R_HEART, () -> host.browse(d)));
        }
        col.add(links);
        return Ui.scroll(col);
    }

    private static void addInfo(JPanel col, String label, String value) {
        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel l = Ui.label(label, 13, Font.BOLD, Theme.MUTED);
        l.setPreferredSize(new Dimension(200, 22));
        row.add(l, BorderLayout.WEST);
        row.add(Ui.wrapText(value == null || value.isBlank() ? "–" : value, 13, Theme.TEXT), BorderLayout.CENTER);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height + 4));
        col.add(row);
        col.add(Box.createVerticalStrut(4));
    }

    private static String side(PlatformModel.Side s) {
        return Lang.t("project.side." + s.name().toLowerCase(java.util.Locale.ROOT));
    }

    static String loaderNames(List<String> loaders) {
        List<String> out = new ArrayList<>();
        for (String l : loaders) {
            InstanceKind k = InstanceKind.of(l);
            out.add(k != null ? k.label() : l.isEmpty() ? l : Character.toUpperCase(l.charAt(0)) + l.substring(1));
        }
        return out.isEmpty() ? "–" : String.join(", ", out);
    }

    static String summarize(List<String> versions) {
        List<String> releases = versions.stream().filter(v -> v.matches("\\d+(\\.\\d+)+")).toList();
        List<String> list = releases.isEmpty() ? versions : releases;
        if (list.isEmpty()) {
            return "–";
        }
        if (list.size() <= 4) {
            return String.join(", ", list);
        }
        return list.get(0) + " – " + list.get(list.size() - 1) + " (" + list.size() + ")";
    }
}
