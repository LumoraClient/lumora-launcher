package dev.lumora.launcher.app.social;

import dev.lumora.launcher.app.ui.Card;
import dev.lumora.launcher.app.ui.Icons;
import dev.lumora.launcher.app.ui.Theme;
import dev.lumora.launcher.app.ui.ToggleSwitch;
import dev.lumora.launcher.app.ui.Ui;
import dev.lumora.launcher.core.i18n.Lang;
import dev.lumora.social.Rules;
import dev.lumora.social.model.Friend;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

/** Small modal dialogs of the social page in the launcher's look. */
final class SocialDialogs {
    private SocialDialogs() {
    }

    /** Values of the group dialog. */
    record GroupValues(String name, String description, String icon, int color) {
    }

    /** Values of the role dialog. */
    record RoleValues(String name, String icon, int color, boolean manage) {
    }

    static JButton primary(String text) {
        JButton b = new JButton(text);
        if (!Theme.pixelStyle) {
            b.putClientProperty("JButton.buttonType", "roundRect");
        }
        b.setBackground(Theme.ACCENT_PRESSED);
        b.setForeground(Color.WHITE);
        b.setFont(Theme.font(Font.BOLD, 13f));
        b.setFocusable(false);
        return b;
    }

    static JButton secondary(String text) {
        JButton b = new JButton(text);
        if (!Theme.pixelStyle) {
            b.putClientProperty("JButton.buttonType", "roundRect");
        }
        b.setFocusable(false);
        return b;
    }

    static JButton danger(String text) {
        JButton b = secondary(text);
        b.setForeground(Theme.FAIL);
        return b;
    }

    static JTextField field(String value, int max, String placeholder) {
        JTextField f = new JTextField(value == null ? "" : value);
        f.putClientProperty("JTextField.placeholderText", placeholder);
        f.setFont(Theme.font(Font.PLAIN, 14f));
        limit(f.getDocument(), max);
        f.setAlignmentX(Component.LEFT_ALIGNMENT);
        f.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        return f;
    }

    static void limit(javax.swing.text.Document d, int max) {
        if (d instanceof AbstractDocument ad) {
            ad.setDocumentFilter(new DocumentFilter() {
                @Override
                public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException {
                    replace(fb, offset, 0, string, attr);
                }

                @Override
                public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
                    String t = text == null ? "" : text;
                    int room = max - (fb.getDocument().getLength() - length);
                    if (room <= 0) {
                        return;
                    }
                    super.replace(fb, offset, length, t.length() > room ? t.substring(0, room) : t, attrs);
                }
            });
        }
    }

    static JLabel caption(String text) {
        JLabel l = Ui.label(text.toUpperCase(dev.lumora.launcher.core.i18n.Lang.locale()), 11.5f, Font.BOLD, Theme.MUTED);
        l.setBorder(BorderFactory.createEmptyBorder(12, 0, 6, 0));
        return l;
    }

    /**
     * A dialog whose primary action runs {@code submit}; the dialog stays open with the error shown when the returned
     * future fails (e.g. "Spieler unbekannt"), closes when it completes.
     */
    private static JDialog dialog(Component parent, String title, String subtitle, JComponent body, String action,
                                  java.util.function.Supplier<CompletableFuture<?>> submit) {
        Window w = parent instanceof Window win ? win : javax.swing.SwingUtilities.getWindowAncestor(parent);
        JDialog d = new JDialog(w, title, java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        Card c = new Card(new BorderLayout(0, 12)).fill(Theme.SURFACE).outline(null).radius(0).padding(20, 22, 18, 22);
        JPanel head = Ui.column();
        head.add(Ui.heading(title));
        if (subtitle != null) {
            head.add(Box.createVerticalStrut(4));
            head.add(Ui.wrapText(subtitle, 13, Theme.MUTED));
        }
        c.add(head, BorderLayout.NORTH);
        c.add(body, BorderLayout.CENTER);
        JLabel error = Ui.label(" ", 12.5f, Font.PLAIN, Theme.FAIL);
        JButton cancel = secondary(Lang.t("common.cancel"));
        JButton ok = primary(action);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(cancel);
        buttons.add(ok);
        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        south.add(error, BorderLayout.NORTH);
        south.add(buttons, BorderLayout.SOUTH);
        c.add(south, BorderLayout.SOUTH);
        cancel.addActionListener(e -> d.dispose());
        ok.addActionListener(e -> {
            ok.setEnabled(false);
            error.setText(" ");
            CompletableFuture<?> f = submit.get();
            if (f == null) {
                ok.setEnabled(true);
                return;
            }
            f.whenComplete((r, ex) -> javax.swing.SwingUtilities.invokeLater(() -> {
                if (ex == null) {
                    d.dispose();
                } else {
                    Throwable cause = ex instanceof java.util.concurrent.CompletionException && ex.getCause() != null ? ex.getCause() : ex;
                    error.setText(cause.getMessage() == null ? Lang.t("common.failed") : cause.getMessage());
                    ok.setEnabled(true);
                }
            }));
        });
        d.setContentPane(c);
        JRootPane root = d.getRootPane();
        root.setDefaultButton(ok);
        root.registerKeyboardAction(e -> d.dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        d.pack();
        d.setMinimumSize(new Dimension(440, d.getHeight()));
        d.setSize(Math.max(460, d.getWidth()), d.getHeight());
        d.setLocationRelativeTo(w);
        return d;
    }

    static void addFriend(Component parent, Function<String, CompletableFuture<?>> send) {
        JPanel body = Ui.column();
        body.add(caption(Lang.t("social.minecraft_name")));
        JTextField name = field("", 16, Lang.t("social.minecraft_name.placeholder"));
        body.add(name);
        body.add(Box.createVerticalStrut(6));
        body.add(Ui.wrapText(Lang.t("social.add_friend.hint"), 12.5f, Theme.MUTED));
        JDialog d = dialog(parent, Lang.t("social.add_friend"), null, body, Lang.t("social.add_friend.send"), () -> send.apply(name.getText().strip()));
        d.setVisible(true);
    }

    static void group(Component parent, String title, GroupValues initial, String action,
                      Function<GroupValues, CompletableFuture<?>> save) {
        JPanel body = Ui.column();
        body.add(caption(Lang.t("social.field.name")));
        JTextField name = field(initial.name(), Rules.GROUP_NAME_MAX, Lang.t("social.group.name.placeholder"));
        body.add(name);
        body.add(caption(Lang.t("social.field.description")));
        JTextArea desc = new JTextArea(initial.description(), 3, 30);
        desc.setLineWrap(true);
        desc.setWrapStyleWord(true);
        desc.setFont(Theme.font(Font.PLAIN, 14f));
        limit(desc.getDocument(), Rules.GROUP_DESCRIPTION_MAX);
        JScrollPane ds = new JScrollPane(desc);
        ds.setAlignmentX(Component.LEFT_ALIGNMENT);
        ds.setPreferredSize(new Dimension(380, 78));
        body.add(ds);
        String[] icon = {initial.icon()};
        int[] color = {initial.color()};
        body.add(caption(Lang.t("social.field.icon_color")));
        JComponent[] previewRef = new JComponent[1];
        body.add(iconPicker(icon, color, () -> previewRef[0].repaint()));
        body.add(Box.createVerticalStrut(8));
        body.add(colorPicker(color, () -> previewRef[0].repaint()));
        previewRef[0] = body;
        JDialog d = dialog(parent, title, null, body, action, () -> {
            String n = name.getText().strip();
            if (n.isEmpty()) {
                return CompletableFuture.failedFuture(new IllegalArgumentException(Lang.t("social.group.name_required")));
            }
            return save.apply(new GroupValues(n, desc.getText(), icon[0], color[0]));
        });
        d.setVisible(true);
    }

    static void role(Component parent, String title, RoleValues initial, boolean fixedManage, String action,
                     Function<RoleValues, CompletableFuture<?>> save) {
        JPanel body = Ui.column();
        body.add(caption(Lang.t("social.field.name")));
        JTextField name = field(initial.name(), Rules.ROLE_NAME_MAX, Lang.t("social.role.name.placeholder"));
        body.add(name);
        String[] icon = {initial.icon()};
        int[] color = {initial.color()};
        JComponent[] ref = new JComponent[1];
        body.add(caption(Lang.t("social.field.icon_color")));
        body.add(iconPicker(icon, color, () -> ref[0].repaint()));
        body.add(Box.createVerticalStrut(8));
        body.add(colorPicker(color, () -> ref[0].repaint()));
        ref[0] = body;
        ToggleSwitch manage = new ToggleSwitch(initial.manage());
        if (!fixedManage) {
            body.add(caption(Lang.t("social.field.rights")));
            JPanel row = new JPanel(new BorderLayout(12, 0));
            row.setOpaque(false);
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            row.add(Ui.wrapText(Lang.t("social.role.manage"),
                    12.5f, Theme.MUTED), BorderLayout.CENTER);
            row.add(manage, BorderLayout.EAST);
            body.add(row);
        }
        JDialog d = dialog(parent, title, null, body, action, () -> {
            String n = name.getText().strip();
            if (n.isEmpty()) {
                return CompletableFuture.failedFuture(new IllegalArgumentException(Lang.t("social.role.name_required")));
            }
            return save.apply(new RoleValues(n, icon[0], color[0], manage.isOn()));
        });
        d.setVisible(true);
    }

    static void pickFriend(Component parent, String title, List<Friend> friends, Function<Friend, CompletableFuture<?>> pick) {
        JPanel list = Ui.column();
        Friend[] chosen = new Friend[1];
        List<SocialRow> rows = new java.util.ArrayList<>();
        Runnable[] rebuild = new Runnable[1];
        rebuild[0] = () -> {
            list.removeAll();
            rows.clear();
            for (Friend f : friends) {
                SocialRow r = new SocialRow((g, x, y, s) -> SocialPaint.face(g, f.profile.uuid, f.profile.name, x, y, s),
                        f.profile.name, SocialPaint.presenceText(f.presence), Theme.MUTED, () -> {
                    chosen[0] = f;
                    rebuild[0].run();
                }).selected(f == chosen[0]);
                rows.add(r);
                list.add(r);
            }
            list.revalidate();
            list.repaint();
        };
        rebuild[0].run();
        JScrollPane sp = new JScrollPane(list);
        sp.setPreferredSize(new Dimension(400, Math.min(320, 60 * Math.max(1, friends.size()) + 4)));
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.getViewport().setOpaque(false);
        sp.setOpaque(false);
        JComponent body = friends.isEmpty()
                ? Ui.wrapText(Lang.t("social.pick.none"), 13, Theme.MUTED)
                : sp;
        JDialog d = dialog(parent, title, Lang.t("social.pick.friends_only"), body, Lang.t("common.add"), () -> {
            if (chosen[0] == null) {
                return CompletableFuture.failedFuture(new IllegalArgumentException(Lang.t("social.pick.required")));
            }
            return pick.apply(chosen[0]);
        });
        d.setVisible(true);
    }

    static void server(Component parent, String current, Consumer<String> save) {
        JPanel body = Ui.column();
        body.add(caption(Lang.t("social.field.server")));
        JTextField addr = field(current, 200, Lang.t("social.server.placeholder"));
        body.add(addr);
        body.add(Box.createVerticalStrut(8));
        body.add(Ui.wrapText(Lang.t("social.server.hint"), 12.5f, Theme.MUTED));
        JDialog d = dialog(parent, Lang.t("settings.chat.server.title"), null, body, Lang.t("common.save"), () -> {
            String a = addr.getText().strip();
            if (a.isEmpty()) {
                save.accept(null);
                return CompletableFuture.completedFuture(null);
            }
            try {
                String uri = dev.lumora.social.Endpoint.parse(a).toString();
                save.accept(uri);
                return CompletableFuture.completedFuture(null);
            } catch (IllegalArgumentException e) {
                return CompletableFuture.failedFuture(e);
            }
        });
        d.setVisible(true);
    }

    static boolean confirm(Component parent, String title, String text, String action) {
        boolean[] ok = {false};
        JDialog d = dialog(parent, title, null, Ui.wrapText(text, 13.5f, Theme.TEXT), action, () -> {
            ok[0] = true;
            return CompletableFuture.completedFuture(null);
        });
        d.setVisible(true);
        return ok[0];
    }

    /** Row of the symbol choices; the selected one is outlined. */
    static JComponent iconPicker(String[] icon, int[] color, Runnable changed) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setBorder(BorderFactory.createEmptyBorder(0, -6, 0, 0));
        for (String key : Rules.ICONS) {
            JComponent b = new JComponent() {
                @Override
                protected void paintComponent(Graphics g0) {
                    Graphics2D g = (Graphics2D) g0.create();
                    SocialPaint.quality(g);
                    boolean sel = key.equals(icon[0]);
                    Color c = SocialPaint.rgb(color[0]);
                    g.setColor(sel ? Theme.alpha(c, 70) : Theme.SURFACE_2);
                    Theme.fillRound(g, 0, 0, 34, 34, 12, 12);
                    if (sel) {
                        g.setColor(c);
                        g.setStroke(new BasicStroke(1.5f));
                        Theme.drawRound(g, 1, 1, 32, 32, 12, 12);
                    }
                    Icons.of(Icons.symbol(key), 18, sel ? c.brighter() : Theme.MUTED).paintIcon(this, g, 8, 8);
                    g.dispose();
                }
            };
            b.setPreferredSize(new Dimension(34, 34));
            b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            b.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    icon[0] = key;
                    p.repaint();
                    changed.run();
                }
            });
            p.add(b);
        }
        return p;
    }

    static JComponent colorPicker(int[] color, Runnable changed) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setBorder(BorderFactory.createEmptyBorder(0, -8, 0, 0));
        for (int rgb : Rules.COLORS) {
            JComponent b = new JComponent() {
                @Override
                protected void paintComponent(Graphics g0) {
                    Graphics2D g = (Graphics2D) g0.create();
                    SocialPaint.quality(g);
                    g.setColor(SocialPaint.rgb(rgb));
                    g.fillOval(3, 3, 22, 22);
                    if (rgb == color[0]) {
                        g.setColor(Theme.TEXT);
                        g.setStroke(new BasicStroke(2f));
                        g.drawOval(0, 0, 27, 27);
                    }
                    g.dispose();
                }
            };
            b.setPreferredSize(new Dimension(28, 28));
            b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            b.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    color[0] = rgb;
                    p.repaint();
                    changed.run();
                }
            });
            p.add(b);
        }
        return p;
    }
}
