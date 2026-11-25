package dev.lumora.launcher.app.pages;

import dev.lumora.launcher.app.ui.Card;
import dev.lumora.launcher.app.ui.Icons;
import dev.lumora.launcher.app.ui.RemoteImages;
import dev.lumora.launcher.app.ui.Theme;
import dev.lumora.launcher.app.ui.Ui;
import dev.lumora.launcher.app.ui.WrapLayout;
import dev.lumora.launcher.core.content.ContentManifest;
import dev.lumora.launcher.core.content.CompatibilityChecker;
import dev.lumora.launcher.core.i18n.Lang;
import dev.lumora.launcher.core.instance.Instance;
import dev.lumora.launcher.core.instance.InstanceKind;
import dev.lumora.launcher.core.platform.ModPlatformProvider;
import dev.lumora.launcher.core.platform.ModRepository;
import dev.lumora.launcher.core.platform.Platform;
import dev.lumora.launcher.core.platform.PlatformModel;
import dev.lumora.launcher.core.platform.PlatformModel.ProjectSummary;
import dev.lumora.launcher.core.platform.ProjectType;
import dev.lumora.launcher.core.stats.PlayStats;
import dev.lumora.launcher.core.util.Log;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

/**
 * "Entdecken": mods, modpacks, resource packs, shaders and data packs from the content platforms in one place. Filters
 * (platform, loader, version, category, environment, open source) combine; with "Nur passende" the target instance sets
 * loader and version. Cards open a detail page; "Installieren" plans first (compatibility, dependencies) and installs
 * after a confirmation. View, card size and recent searches are remembered.
 */
public final class DiscoverPage extends JPanel {
    private static final int PAGE = 24;
    private static RemoteImages sharedImages;

    private final PageHost host;
    private final ExecutorService io;
    private final CardLayout cards = new CardLayout();
    private final JPanel resultsView = new JPanel(new BorderLayout(0, 12));
    private final JPanel filters = Ui.column();
    private final JPanel results = Ui.column();
    private final JTextField search = new JTextField();
    private final JLabel summary = Ui.label(" ", 13, Font.PLAIN, Theme.MUTED);
    private final Timer debounce;
    private final RemoteImages images;
    private ProjectType type = ProjectType.MOD;
    /** "popular", "trend", "new", "updated", "relevance", "favorites", "foryou". */
    private String mode = "popular";
    private Platform platform;
    private final Set<String> loaders = new LinkedHashSet<>();
    private String gameVersion;
    private PlatformModel.Environment environment = PlatformModel.Environment.ANY;
    private boolean openSource;
    private final Set<String> categories = new LinkedHashSet<>();
    private boolean onlyCompatible = true;
    private String targetKey;
    private int offset;
    private int total;
    private int generation;
    private final Map<String, ProjectSummary> picked = new LinkedHashMap<>();
    private Set<String> installedProjects = Set.of();
    private JComponent typeBar;
    private JComboBox<String> targetBox;
    private List<Instance> targetChoices = List.of();
    private ProjectView detail;

    public DiscoverPage(PageHost host, ExecutorService io) {
        this.host = host;
        this.io = io;
        this.images = images(host, io);
        setOpaque(false);
        setLayout(cards);
        resultsView.setOpaque(false);
        debounce = new Timer(400, e -> {
            offset = 0;
            mode = search.getText().isBlank() ? (mode.equals("relevance") ? "popular" : mode) : "relevance";
            rememberSearch();
            reload();
        });
        debounce.setRepeats(false);
        search.putClientProperty("JTextField.placeholderText", Lang.t("discover.search"));
        search.putClientProperty("JTextField.leadingIcon", Icons.of(Icons.Kind.SEARCH, 16, Theme.MUTED));
        search.putClientProperty("JTextField.showClearButton", true);
        search.setPreferredSize(new Dimension(320, Ui.CONTROL_HEIGHT));
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                debounce.restart();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                debounce.restart();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                debounce.restart();
            }
        });
        search.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                if (search.getText().isBlank()) {
                    showRecent();
                }
            }
        });
        add(resultsView, "results");
    }

    /** One image cache for all pages. */
    static synchronized RemoteImages images(PageHost host, ExecutorService io) {
        if (sharedImages == null) {
            sharedImages = new RemoteImages(host.ctx().downloader, io);
        }
        return sharedImages;
    }

    /** Called when the page is shown from the navigation. */
    public void refresh() {
        if (detail != null && detail.isShowing()) {
            return;
        }
        build();
        reload();
    }

    /** Shows one content type, filtered for {@code target} (null = the selected instance). */
    public void show(ProjectType t, Instance target) {
        type = t == null ? ProjectType.MOD : t;
        targetKey = target == null ? null : target.key();
        onlyCompatible = type != ProjectType.MODPACK;
        categories.clear();
        offset = 0;
        mode = search.getText().isBlank() ? "popular" : "relevance";
        cards.show(this, "results");
        build();
        reload();
    }

    private Instance target() {
        String key = targetKey != null ? targetKey : host.ctx().selectedInstance().key();
        return host.ctx().instances.find(host.ctx().config, key).orElse(host.ctx().selectedInstance());
    }

    // ---------------------------------------------------------------- layout

    private void build() {
        resultsView.removeAll();
        JPanel head = Ui.column();
        head.add(Ui.pageTitle(Lang.t("nav.browser"), Lang.t("discover.subtitle")));
        head.add(Box.createVerticalStrut(12));

        JPanel row1 = new JPanel(new BorderLayout(10, 0));
        row1.setOpaque(false);
        row1.setAlignmentX(Component.LEFT_ALIGNMENT);
        List<ProjectType> types = List.of(ProjectType.MOD, ProjectType.MODPACK, ProjectType.RESOURCE_PACK, ProjectType.SHADER, ProjectType.DATAPACK);
        typeBar = Ui.segmented(types.stream().map(ProjectType::id).toList(),
                types.stream().map(t -> Lang.t("discover.type." + t.id())).toList(), type.id(), id -> {
                    type = ProjectType.of(id);
                    categories.clear();
                    offset = 0;
                    onlyCompatible = type != ProjectType.MODPACK;
                    build();
                    reload();
                });
        row1.add(typeBar, BorderLayout.WEST);
        JPanel targetRow = Ui.row(8);
        if (type != ProjectType.MODPACK) {
            targetRow.add(Ui.label(Lang.t("discover.for"), 13, Font.BOLD, Theme.MUTED));
            targetChoices = host.ctx().instances.list(host.ctx().config);
            List<String> names = targetChoices.stream().map(i -> i.name + " (" + i.kind().label() + " " + i.minecraftVersion + ")").toList();
            targetBox = Ui.dropdown(names);
            Instance t = target();
            for (int i = 0; i < targetChoices.size(); i++) {
                if (targetChoices.get(i).key().equals(t.key())) {
                    targetBox.setSelectedIndex(i);
                }
            }
            targetBox.setPreferredSize(new Dimension(260, Ui.CONTROL_HEIGHT));
            targetBox.addActionListener(e -> {
                int i = targetBox.getSelectedIndex();
                if (i >= 0 && i < targetChoices.size() && !targetChoices.get(i).key().equals(targetKey)) {
                    targetKey = targetChoices.get(i).key();
                    offset = 0;
                    build();
                    reload();
                }
            });
            targetRow.add(targetBox);
            JCheckBox fit = new JCheckBox(Lang.t("discover.only_compatible"), onlyCompatible);
            fit.setOpaque(false);
            fit.setToolTipText(Lang.t("discover.only_compatible.tip"));
            fit.addActionListener(e -> {
                onlyCompatible = fit.isSelected();
                offset = 0;
                build();
                reload();
            });
            targetRow.add(fit);
        }
        row1.add(targetRow, BorderLayout.EAST);
        row1.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        head.add(row1);
        head.add(Box.createVerticalStrut(10));

        JPanel row2 = new JPanel(new BorderLayout(10, 0));
        row2.setOpaque(false);
        row2.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel left = Ui.row(8);
        left.add(search);
        List<String> modes = new ArrayList<>(List.of("popular", "trend", "new", "updated"));
        if (!search.getText().isBlank()) {
            modes.add(0, "relevance");
        }
        modes.add("favorites");
        if (type == ProjectType.MOD) {
            modes.add("foryou");
        }
        left.add(Ui.segmented(modes, modes.stream().map(m -> Lang.t("discover.mode." + m)).toList(), mode, id -> {
            mode = id;
            offset = 0;
            reload();
        }));
        row2.add(left, BorderLayout.WEST);
        JPanel right = Ui.row(8);
        List<String> views = List.of("list", "grid", "compact");
        right.add(Ui.segmented(views, views.stream().map(v -> Lang.t("view." + v)).toList(), host.ctx().config.discoverView, id -> {
            host.ctx().config.discoverView = id;
            host.saveConfig();
            reload();
        }));
        if ("grid".equals(host.ctx().config.discoverView)) {
            List<String> sizes = List.of("1", "2", "3");
            JComboBox<String> size = Ui.choice(sizes, List.of(Lang.t("view.size.small"), Lang.t("view.size.medium"), Lang.t("view.size.large")),
                    String.valueOf(host.ctx().config.discoverCardSize), id -> {
                        host.ctx().config.discoverCardSize = Integer.parseInt(id);
                        host.saveConfig();
                        reload();
                    });
            size.setToolTipText(Lang.t("view.size"));
            right.add(size);
        }
        row2.add(right, BorderLayout.EAST);
        row2.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        head.add(row2);
        head.add(Box.createVerticalStrut(8));
        summary.setAlignmentX(Component.LEFT_ALIGNMENT);
        head.add(summary);
        resultsView.add(head, BorderLayout.NORTH);

        buildFilters();
        javax.swing.JScrollPane fs = Ui.scroll(filters);
        fs.setPreferredSize(new Dimension(250, 10));
        fs.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 14));
        resultsView.add(fs, BorderLayout.WEST);
        results.setAlignmentX(Component.LEFT_ALIGNMENT);
        resultsView.add(Ui.scroll(results), BorderLayout.CENTER);
        resultsView.revalidate();
        resultsView.repaint();
    }

    private void buildFilters() {
        filters.removeAll();
        Instance t = target();
        boolean fixed = onlyCompatible && type != ProjectType.MODPACK;
        filters.add(Ui.caption(Lang.t("discover.filter.platform")));
        javax.swing.ButtonGroup pg = new javax.swing.ButtonGroup();
        JRadioButton all = radio(Lang.t("discover.filter.all"), platform == null, () -> platform = null);
        pg.add(all);
        filters.add(all);
        for (ModPlatformProvider p : host.ctx().repository.providers()) {
            JRadioButton r = radio(p.platform().label(), platform == p.platform(), () -> platform = p.platform());
            r.setEnabled(p.available());
            if (!p.available()) {
                r.setToolTipText(Lang.t("discover.curseforge.key"));
            }
            pg.add(r);
            filters.add(r);
        }
        if (host.ctx().repository.provider(Platform.CURSEFORGE).filter(ModPlatformProvider::available).isEmpty()) {
            filters.add(Ui.wrapText(Lang.t("discover.curseforge.key"), 11.5f, Theme.MUTED));
        }

        if (type == ProjectType.MOD || type == ProjectType.MODPACK) {
            filters.add(Ui.caption(Lang.t("discover.filter.loader")));
            if (fixed) {
                filters.add(Ui.wrapText(t.kind().modded() ? Lang.t("discover.filter.loader.fixed", String.join(", ",
                        t.kind().modLoaders(t.minecraftVersion).stream().map(this::label).toList())) : Lang.t("discover.filter.loader.none", t.kind().label()),
                        12.5f, Theme.MUTED));
            } else {
                for (String l : List.of("fabric", "quilt", "forge", "neoforge")) {
                    filters.add(check(label(l), loaders.contains(l), on -> {
                        if (on) {
                            loaders.add(l);
                        } else {
                            loaders.remove(l);
                        }
                    }));
                }
            }
        }
        filters.add(Ui.caption(Lang.t("discover.filter.version")));
        if (fixed) {
            filters.add(Ui.wrapText(Lang.t("discover.filter.version.fixed", t.minecraftVersion), 12.5f, Theme.MUTED));
        } else {
            JTextField version = new JTextField(gameVersion == null ? "" : gameVersion);
            version.putClientProperty("JTextField.placeholderText", Lang.t("discover.filter.version.any"));
            version.setMaximumSize(new Dimension(220, Ui.CONTROL_HEIGHT));
            version.setAlignmentX(Component.LEFT_ALIGNMENT);
            version.addActionListener(e -> {
                String v = version.getText().strip();
                gameVersion = v.matches("[0-9A-Za-z._-]{1,32}") ? v : null;
                offset = 0;
                reload();
            });
            filters.add(version);
        }
        if (type == ProjectType.MOD) {
            filters.add(Ui.caption(Lang.t("discover.filter.environment")));
            List<String> envs = List.of("ANY", "CLIENT", "SERVER", "BOTH");
            JComboBox<String> env = Ui.choice(envs, envs.stream().map(e -> Lang.t("discover.env." + e.toLowerCase(java.util.Locale.ROOT))).toList(),
                    environment.name(), id -> {
                        environment = PlatformModel.Environment.valueOf(id);
                        offset = 0;
                        reload();
                    });
            env.setAlignmentX(Component.LEFT_ALIGNMENT);
            filters.add(env);
        }
        filters.add(Box.createVerticalStrut(8));
        filters.add(check(Lang.t("discover.filter.open_source"), openSource, on -> openSource = on));
        filters.add(Ui.caption(Lang.t("discover.filter.categories")));
        if (type == ProjectType.MOD || type == ProjectType.MODPACK) {
            for (ModRepository.LumoraCategory c : ModRepository.LumoraCategory.values()) {
                filters.add(check(Lang.t(c.langKey()), categories.contains(c.id()), on -> {
                    if (on) {
                        categories.add(c.id());
                    } else {
                        categories.remove(c.id());
                    }
                }));
            }
        } else {
            JPanel holder = Ui.column();
            filters.add(holder);
            holder.add(Ui.label(Lang.t("common.loading"), 12, Font.PLAIN, Theme.MUTED));
            ProjectType forType = type;
            io.submit(() -> {
                List<PlatformModel.Category> cats;
                try {
                    cats = host.ctx().repository.require(Platform.MODRINTH).categories().stream().filter(c -> c.type() == forType).toList();
                } catch (IOException | RuntimeException e) {
                    cats = List.of();
                }
                List<PlatformModel.Category> list = cats;
                SwingUtilities.invokeLater(() -> {
                    holder.removeAll();
                    for (PlatformModel.Category c : list) {
                        String key = "discover.mcat." + c.id();
                        String text = Lang.has(key) ? Lang.t(key) : c.name().substring(0, 1).toUpperCase(java.util.Locale.ROOT) + c.name().substring(1).replace('-', ' ');
                        holder.add(check(text, categories.contains("m:" + c.id()), on -> {
                            if (on) {
                                categories.add("m:" + c.id());
                            } else {
                                categories.remove("m:" + c.id());
                            }
                        }));
                    }
                    holder.revalidate();
                });
            });
        }
        if (!categories.isEmpty() || !loaders.isEmpty() || gameVersion != null || openSource || environment != PlatformModel.Environment.ANY) {
            filters.add(Box.createVerticalStrut(10));
            filters.add(Ui.button(Lang.t("discover.filter.reset"), Icons.Kind.X, () -> {
                categories.clear();
                loaders.clear();
                gameVersion = null;
                openSource = false;
                environment = PlatformModel.Environment.ANY;
                offset = 0;
                build();
                reload();
            }));
        }
    }

    private String label(String loader) {
        InstanceKind k = InstanceKind.of(loader);
        return k != null ? k.label() : loader;
    }

    private JRadioButton radio(String text, boolean on, Runnable select) {
        JRadioButton r = new JRadioButton(text, on);
        r.setOpaque(false);
        r.setAlignmentX(Component.LEFT_ALIGNMENT);
        r.addActionListener(e -> {
            select.run();
            offset = 0;
            reload();
        });
        return r;
    }

    private JCheckBox check(String text, boolean on, java.util.function.Consumer<Boolean> change) {
        JCheckBox c = new JCheckBox(text, on);
        c.setOpaque(false);
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        c.addActionListener(e -> {
            change.accept(c.isSelected());
            offset = 0;
            buildFilters();
            filters.revalidate();
            filters.repaint();
            reload();
        });
        return c;
    }

    // ---------------------------------------------------------------- search

    private PlatformModel.SearchQuery query() {
        Instance t = target();
        boolean fixed = onlyCompatible && type != ProjectType.MODPACK;
        List<String> ls;
        String version;
        if (fixed) {
            ls = type == ProjectType.MOD ? t.kind().modLoaders(t.minecraftVersion) : type == ProjectType.SHADER ? List.of() : List.of();
            version = t.minecraftVersion;
        } else {
            ls = new ArrayList<>(loaders);
            version = gameVersion;
        }
        List<String> rawCats = categories.stream().filter(c -> c.startsWith("m:")).map(c -> c.substring(2)).toList();
        PlatformModel.Sort sort = switch (mode) {
            case "trend" -> PlatformModel.Sort.FOLLOWS;
            case "new" -> PlatformModel.Sort.NEWEST;
            case "updated" -> PlatformModel.Sort.UPDATED;
            case "relevance" -> PlatformModel.Sort.RELEVANCE;
            default -> PlatformModel.Sort.DOWNLOADS;
        };
        return new PlatformModel.SearchQuery(search.getText(), type, version, ls, rawCats, environment, openSource, sort, offset, PAGE);
    }

    private void reload() {
        int gen = ++generation;
        results.removeAll();
        results.add(Ui.label(Lang.t("common.loading"), 14, Font.PLAIN, Theme.MUTED));
        results.revalidate();
        results.repaint();
        Instance t = target();
        if (onlyCompatible && type == ProjectType.MOD && !t.kind().modded()) {
            results.removeAll();
            results.add(notice(Lang.t("compat.mods_unsupported", t.kind().label()), Lang.t("compat.mods_unsupported.hint")));
            summary.setText(" ");
            return;
        }
        if (onlyCompatible && type == ProjectType.SHADER && !CompatibilityChecker.supports(t.kind(), ProjectType.SHADER)) {
            results.removeAll();
            results.add(notice(Lang.t("compat.shaders_unsupported", t.kind().label()), Lang.t("compat.shaders_unsupported.hint")));
            summary.setText(" ");
            return;
        }
        PlatformModel.SearchQuery q = query();
        List<String> lumoraCats = categories.stream().filter(c -> !c.startsWith("m:")).toList();
        String currentMode = mode;
        io.submit(() -> {
            Set<String> installed = installed(t);
            try {
                PlatformModel.SearchPage page;
                String note = null;
                if (currentMode.equals("favorites")) {
                    page = favorites(q);
                } else if (currentMode.equals("foryou")) {
                    List<String> top = topCategories(t);
                    if (top.isEmpty()) {
                        note = Lang.t("discover.foryou.empty");
                        page = host.ctx().repository.search(platform, q, lumoraCats);
                    } else {
                        note = Lang.t("discover.foryou.because", String.join(", ", top));
                        PlatformModel.SearchQuery fq = new PlatformModel.SearchQuery(q.text(), q.type(), q.gameVersion(), q.loaders(), top,
                                q.environment(), q.openSourceOnly(), PlatformModel.Sort.DOWNLOADS, q.offset(), q.limit());
                        page = host.ctx().repository.search(platform, fq, lumoraCats);
                        page = new PlatformModel.SearchPage(page.hits().stream().filter(h -> !installed.contains(h.key())).toList(), page.offset(), page.total());
                    }
                } else {
                    page = host.ctx().repository.search(platform, q, lumoraCats);
                }
                PlatformModel.SearchPage done = page;
                String info = note;
                SwingUtilities.invokeLater(() -> {
                    if (gen != generation) {
                        return;
                    }
                    installedProjects = installed;
                    total = done.total();
                    showHits(done.hits(), info);
                });
            } catch (IOException | RuntimeException e) {
                Log.warn("Search failed: " + e.getMessage());
                SwingUtilities.invokeLater(() -> {
                    if (gen != generation) {
                        return;
                    }
                    results.removeAll();
                    results.add(notice(Lang.t("discover.error.title"), Lang.t("discover.error.text", e.getMessage())));
                    results.add(Box.createVerticalStrut(8));
                    results.add(Ui.button(Lang.t("common.retry"), Icons.Kind.REFRESH, this::reload));
                    summary.setText(" ");
                    results.revalidate();
                    results.repaint();
                });
            }
        });
    }

    private PlatformModel.SearchPage favorites(PlatformModel.SearchQuery q) throws IOException {
        List<ProjectSummary> all = new ArrayList<>();
        for (ModPlatformProvider p : host.ctx().repository.available()) {
            String prefix = p.platform().id() + ":";
            List<String> ids = host.ctx().config.favoriteProjects.stream().filter(f -> f.startsWith(prefix)).map(f -> f.substring(prefix.length())).toList();
            if (!ids.isEmpty()) {
                all.addAll(p.projects(ids));
            }
        }
        List<ProjectSummary> ofType = all.stream().filter(p -> p.type() == null || p.type() == type).toList();
        return new PlatformModel.SearchPage(ofType, 0, ofType.size());
    }

    /** Modrinth projects already in the target instance (manifest), for "Installiert" and "Für dich". */
    private Set<String> installed(Instance t) {
        Set<String> out = new HashSet<>();
        try {
            for (ContentManifest.Entry e : ContentManifest.load(host.ctx().layout(t).gameDir()).all().values()) {
                if (e.platform != null && e.projectId != null) {
                    out.add(e.platform + ":" + e.projectId);
                }
            }
        } catch (RuntimeException e) {
            // nothing known
        }
        return out;
    }

    /** The two Modrinth categories most common among the instance's installed mods ("Für dich"). */
    private List<String> topCategories(Instance t) throws IOException {
        List<String> ids = installed(t).stream().filter(k -> k.startsWith("modrinth:")).map(k -> k.substring("modrinth:".length()))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ProjectSummary s : host.ctx().repository.require(Platform.MODRINTH).projects(ids.subList(0, Math.min(50, ids.size())))) {
            for (String c : s.categories()) {
                if (!c.equals("library")) {
                    counts.merge(c, 1, Integer::sum);
                }
            }
        }
        return counts.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(2).map(Map.Entry::getKey).toList();
    }

    private void show(List<ProjectSummary> hits, String note) {
        results.removeAll();
        if (note != null) {
            results.add(Ui.label(note, 13, Font.PLAIN, Theme.ACCENT_TEXT));
            results.add(Box.createVerticalStrut(8));
        }
        if (!picked.isEmpty()) {
            JPanel bar = Ui.row(8);
            bar.add(Ui.primary(Lang.t("discover.install_picked", picked.size()), Icons.Kind.DOWNLOAD,
                    () -> InstallDialog.show(host, io, new ArrayList<>(picked.values()), type, target(), this::installedChanged)));
            bar.add(Ui.button(Lang.t("discover.clear_picked"), Icons.Kind.X, () -> {
                picked.clear();
                reload();
            }));
            results.add(bar);
            results.add(Box.createVerticalStrut(8));
        }
        if (hits.isEmpty()) {
            results.add(notice(Lang.t(mode.equals("favorites") ? "discover.favorites.empty" : "discover.empty.title"),
                    Lang.t(mode.equals("favorites") ? "discover.favorites.empty.text" : "discover.empty.text")));
        }
        String view = host.ctx().config.discoverView;
        JPanel holder;
        if ("grid".equals(view)) {
            holder = new JPanel(new WrapLayout(FlowLayout.LEFT, 12, 12));
            holder.setOpaque(false);
            holder.setBorder(BorderFactory.createEmptyBorder(0, -12, 0, 0));
        } else {
            holder = Ui.column();
        }
        holder.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (ProjectSummary h : hits) {
            holder.add(card(h, view));
            if (!"grid".equals(view)) {
                holder.add(Box.createVerticalStrut("compact".equals(view) ? 4 : 8));
            }
        }
        results.add(holder);
        if (!mode.equals("favorites") && total > PAGE) {
            results.add(Box.createVerticalStrut(10));
            JPanel pager = Ui.row(8);
            int pages = (int) Math.ceil(Math.min(total, 10_000) / (double) PAGE);
            int current = offset / PAGE + 1;
            JButton prev = Ui.button(Lang.t("common.previous"), null, () -> {
                offset = Math.max(0, offset - PAGE);
                reload();
            });
            prev.setEnabled(offset > 0);
            JButton nextB = Ui.button(Lang.t("common.next"), null, () -> {
                offset += PAGE;
                reload();
            });
            nextB.setEnabled(current < pages);
            pager.add(prev);
            pager.add(Ui.label(Lang.t("discover.page", current, pages), 13, Font.PLAIN, Theme.MUTED));
            pager.add(nextB);
            results.add(pager);
        }
        summary.setText(mode.equals("favorites") ? Lang.plural("discover.favorites.count", hits.size())
                : Lang.t("discover.summary", Ui.count(total), Lang.t("discover.type." + type.id())));
        results.revalidate();
        results.repaint();
    }

    private JComponent notice(String title, String text) {
        Card c = new Card(new BorderLayout(0, 6)).calm();
        JPanel col = Ui.column();
        col.add(Ui.label(title, 15, Font.BOLD, Theme.TEXT));
        col.add(Box.createVerticalStrut(4));
        col.add(Ui.wrapText(text, 13, Theme.MUTED));
        c.add(col, BorderLayout.CENTER);
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height + 10));
        return c;
    }

    // ---------------------------------------------------------------- cards

    private JComponent card(ProjectSummary h, String view) {
        boolean compact = "compact".equals(view);
        boolean grid = "grid".equals(view);
        int size = host.ctx().config.discoverCardSize;
        int iconSize = compact ? 32 : grid ? 40 + size * 8 : 56;
        Card c = new Card(new BorderLayout(12, 0));
        c.padding(compact ? 6 : 12, 12, compact ? 6 : 12, 12);
        JPanel iconHolder = new JPanel(new BorderLayout());
        iconHolder.setOpaque(false);
        iconHolder.add(Ui.letterTile(h.title(), iconSize, Theme.ACCENT_2));
        images.load(h.iconUrl(), iconSize, iconSize, false, img -> {
            iconHolder.removeAll();
            iconHolder.add(Ui.imageTile(img, iconSize, iconSize));
            iconHolder.revalidate();
            iconHolder.repaint();
        });
        JPanel west = Ui.row(8);
        if (type != ProjectType.MODPACK) {
            JCheckBox pick = new JCheckBox();
            pick.setOpaque(false);
            pick.setToolTipText(Lang.t("discover.pick"));
            pick.setSelected(picked.containsKey(h.key()));
            pick.addActionListener(e -> {
                if (pick.isSelected()) {
                    picked.put(h.key(), h);
                } else {
                    picked.remove(h.key());
                }
                show(currentHits(), null);
            });
            west.add(pick);
        }
        west.add(iconHolder);
        c.add(west, BorderLayout.WEST);
        JPanel t = Ui.column();
        JPanel titleRow = Ui.row(6);
        titleRow.add(Ui.label(h.title(), compact ? 13.5f : 15, Font.BOLD, Theme.TEXT));
        if (h.author() != null && !compact) {
            titleRow.add(Ui.label(Lang.t("discover.by", h.author()), 12.5f, Font.PLAIN, Theme.MUTED));
        }
        t.add(titleRow);
        if (!compact && h.description() != null) {
            String d = h.description().length() > 160 ? h.description().substring(0, 159) + "…" : h.description();
            t.add(Box.createVerticalStrut(2));
            if (grid) {
                javax.swing.JTextArea area = Ui.wrapText(d, 12.5f, Theme.MUTED);
                area.setColumns(28);
                t.add(area);
            } else {
                t.add(Ui.label(d, 12.5f, Font.PLAIN, Theme.MUTED));
            }
        }
        if (!compact) {
            t.add(Box.createVerticalStrut(6));
            JPanel meta = Ui.row(6);
            meta.add(stat(Icons.Kind.DOWNLOAD, Ui.count(h.downloads())));
            if (h.updated() != null) {
                meta.add(stat(Icons.Kind.CLOCK, PlayStats.ago(h.updated(), Instant.now())));
            }
            meta.add(Ui.chip(h.platform().label(), Theme.ACCENT_2, Theme.ACCENT_TEXT));
            if (host.ctx().config.discoverShowCategories) {
                for (String l : h.loaders().stream().limit(3).toList()) {
                    InstanceKind k = InstanceKind.of(l);
                    if (k != null) {
                        meta.add(Ui.chip(k.label(), InstanceUi.kindColor(k), InstanceUi.kindColor(k).brighter()));
                    }
                }
                for (String cat : h.categories().stream().limit(grid ? 1 : 3).toList()) {
                    String key = "discover.mcat." + cat;
                    meta.add(Ui.chip(Lang.has(key) ? Lang.t(key) : cat, Theme.MUTED, Theme.TEXT));
                }
            }
            t.add(meta);
        }
        c.add(t, BorderLayout.CENTER);
        JPanel east = Ui.row(6);
        boolean fav = host.ctx().config.favoriteProjects.contains(h.key());
        JButton star = Ui.button("", null, null);
        star.setIcon(Icons.of(Icons.Kind.R_STAR, 15, fav ? Theme.WARN : Theme.MUTED));
        star.setToolTipText(Lang.t(fav ? "discover.unfavorite" : "discover.favorite"));
        star.addActionListener(e -> {
            toggleFavorite(h);
            boolean now = host.ctx().config.favoriteProjects.contains(h.key());
            star.setIcon(Icons.of(Icons.Kind.R_STAR, 15, now ? Theme.WARN : Theme.MUTED));
            star.setToolTipText(Lang.t(now ? "discover.unfavorite" : "discover.favorite"));
        });
        east.add(star);
        boolean have = installedProjects.contains(h.key());
        JButton install = have ? Ui.button(Lang.t("discover.installed"), Icons.Kind.CHECK, () -> openProject(h))
                : Ui.primary(type == ProjectType.MODPACK ? Lang.t("discover.install_pack") : Lang.t("discover.install"), Icons.Kind.DOWNLOAD,
                () -> install(h));
        east.add(install);
        c.add(east, BorderLayout.EAST);
        c.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        MouseAdapter open = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    openProject(h);
                }
            }
        };
        c.addMouseListener(open);
        t.addMouseListener(open);
        if (grid) {
            int w = 300 + size * 60;
            c.setPreferredSize(new Dimension(w, Math.max(120, c.getPreferredSize().height)));
        } else {
            c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
            c.setAlignmentX(Component.LEFT_ALIGNMENT);
        }
        return c;
    }

    private final List<ProjectSummary> lastHits = new ArrayList<>();

    private List<ProjectSummary> currentHits() {
        return new ArrayList<>(lastHits);
    }

    private JComponent stat(Icons.Kind icon, String text) {
        JLabel l = Ui.label(text, 12, Font.BOLD, Theme.MUTED);
        l.setIcon(Icons.of(icon, 13, Theme.MUTED));
        l.setIconTextGap(4);
        return l;
    }

    private void toggleFavorite(ProjectSummary h) {
        List<String> favs = host.ctx().config.favoriteProjects;
        if (!favs.remove(h.key())) {
            favs.add(0, h.key());
        }
        host.saveConfig();
    }

    private void rememberSearch() {
        String s = search.getText().strip();
        if (s.length() < 2) {
            return;
        }
        List<String> recent = host.ctx().config.recentSearches;
        recent.removeIf(r -> r.equalsIgnoreCase(s));
        recent.add(0, s);
        while (recent.size() > 10) {
            recent.remove(recent.size() - 1);
        }
        host.saveConfig();
    }

    private void showRecent() {
        List<String> recent = host.ctx().config.recentSearches;
        if (recent.isEmpty()) {
            return;
        }
        JPopupMenu m = new JPopupMenu();
        JMenuItem title = new JMenuItem(Lang.t("discover.recent"));
        title.setEnabled(false);
        m.add(title);
        for (String r : recent) {
            JMenuItem it = new JMenuItem(r, Icons.of(Icons.Kind.CLOCK, 14, Theme.MUTED));
            it.addActionListener(e -> search.setText(r));
            m.add(it);
        }
        m.addSeparator();
        JMenuItem clear = new JMenuItem(Lang.t("discover.recent.clear"));
        clear.addActionListener(e -> {
            host.ctx().config.recentSearches.clear();
            host.saveConfig();
        });
        m.add(clear);
        m.setFocusable(false);
        m.show(search, 0, search.getHeight());
    }

    // ---------------------------------------------------------------- actions

    /** Opens the detail page of a Modrinth project by slug or id (loads its summary first). */
    public void openProject(String idOrSlug, ProjectType t) {
        type = t == null ? ProjectType.MOD : t;
        io.submit(() -> {
            try {
                List<ProjectSummary> found = host.ctx().repository.require(Platform.MODRINTH).projects(List.of(idOrSlug));
                if (!found.isEmpty()) {
                    SwingUtilities.invokeLater(() -> openProject(found.get(0)));
                }
            } catch (IOException | RuntimeException e) {
                Log.warn("Project " + idOrSlug + " could not be opened: " + e.getMessage());
            }
        });
    }

    void openProject(ProjectSummary h) {
        if (detail != null) {
            remove(detail);
        }
        detail = new ProjectView(host, io, images, h, type, target(), () -> {
            cards.show(this, "results");
            reload();
        }, () -> install(h));
        add(detail, "project");
        cards.show(this, "project");
        revalidate();
    }

    private void install(ProjectSummary h) {
        if (type == ProjectType.MODPACK) {
            ModpackInstall.start(host, io, h, null);
        } else {
            InstallDialog.show(host, io, List.of(h), type, target(), this::installedChanged);
        }
    }

    private void installedChanged() {
        picked.clear();
        reload();
    }

    /** Keeps the last result list for re-rendering after a pick. */
    private void remember(List<ProjectSummary> hits) {
        lastHits.clear();
        lastHits.addAll(hits);
    }

    /** Remembers hits before showing them (for the pick checkbox re-render). */
    private void showHits(List<ProjectSummary> hits, String note) {
        remember(hits);
        show(hits, note);
    }
}
