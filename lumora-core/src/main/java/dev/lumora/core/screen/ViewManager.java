package dev.lumora.core.screen;

import dev.lumora.api.render.HudGraphics;
import dev.lumora.api.screen.AddonScreens;
import dev.lumora.api.screen.AddonView;
import dev.lumora.core.CoreLogger;
import dev.lumora.core.RegistrationTracker;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Addon menus. The version adapter shows an {@link AddonView} in a generic Minecraft screen ({@link Platform#show})
 * and calls {@link AddonView#removed()} when that screen goes away. The core wraps each view so it knows which views
 * of an addon are open, closes them when the addon is disabled and keeps a failing view from crashing the game.
 */
public final class ViewManager {
    /** What the version adapter does with Minecraft's screens. */
    public interface Platform {
        /** Shows {@code view} on top of the current screen (client thread). */
        void show(AddonView view);

        /** If {@code view} is the current screen, returns to the screen below it. */
        void close(AddonView view);

        String clipboard();

        /** Lumora hub settings of the addon (addon settings and keybinds). */
        void openAddonSettings(String addonId);

        Platform NONE = new Platform() {
            @Override public void show(AddonView view) { }
            @Override public void close(AddonView view) { }
            @Override public String clipboard() { return ""; }
            @Override public void openAddonSettings(String addonId) { }
        };
    }

    private final Platform platform;
    private final CoreLogger log;
    /** Registered main menus per addon (hub open button). */
    private final java.util.Map<String, Runnable> menus = new java.util.concurrent.ConcurrentHashMap<>();

    public ViewManager(Platform platform, CoreLogger log) {
        this.platform = platform;
        this.log = log;
    }

    public AddonScreens scoped(String addon, RegistrationTracker tracker, Runnable guard) {
        Deque<Tracked> open = new ArrayDeque<>();
        java.util.concurrent.atomic.AtomicBoolean tracked = new java.util.concurrent.atomic.AtomicBoolean();
        Runnable closeAll = () -> {
            // addon disabled: close its views, topmost first; the next open() registers the cleanup again
            tracked.set(false);
            Tracked[] views;
            synchronized (open) {
                views = open.toArray(new Tracked[0]);
            }
            for (Tracked t : views) {
                platform.close(t);
            }
        };
        return new AddonScreens() {
            @Override
            public void registerMenu(java.util.function.Supplier<AddonView> menu) {
                guard.run();
                if (menu == null) {
                    throw new IllegalArgumentException("menu");
                }
                AddonScreens self = this;
                menus.put(addon, () -> self.open(menu.get()));
                tracker.track(RegistrationTracker.of(() -> menus.remove(addon)));
            }

            @Override
            public void open(AddonView view) {
                guard.run();
                if (view == null) {
                    throw new IllegalArgumentException("view");
                }
                if (tracked.compareAndSet(false, true)) {
                    // only addons that open menus get a cleanup registration
                    tracker.track(RegistrationTracker.of(closeAll));
                }
                Tracked shown = new Tracked(addon, view, open, log);
                synchronized (open) {
                    open.push(shown);
                }
                platform.show(shown);
            }

            @Override
            public void back() {
                Tracked top;
                synchronized (open) {
                    top = open.peek();
                }
                if (top != null) {
                    platform.close(top);
                }
            }

            @Override
            public String clipboard() {
                String s = platform.clipboard();
                return s == null ? "" : s;
            }

            @Override
            public void openAddonSettings() {
                guard.run();
                platform.openAddonSettings(addon);
            }
        };
    }

    /** Whether {@code addon} registered a main menu (hub open button). */
    public boolean hasMenu(String addon) {
        return menus.containsKey(addon);
    }

    /** Opens the main menu of {@code addon} (client thread); false if it has none or it failed. */
    public boolean openMenu(String addon) {
        Runnable menu = menus.get(addon);
        if (menu == null) {
            return false;
        }
        try {
            menu.run();
            return true;
        } catch (RuntimeException | LinkageError e) {
            log.warn("Menu of " + addon + " could not be opened", e);
            return false;
        }
    }

    /** Delegates to the addon's view; exceptions are logged once per kind of call and treated as "not handled". */
    static final class Tracked implements AddonView {
        private final String addon;
        private final AddonView view;
        private final Deque<Tracked> open;
        private final CoreLogger log;
        private boolean failed;

        Tracked(String addon, AddonView view, Deque<Tracked> open, CoreLogger log) {
            this.addon = addon;
            this.view = view;
            this.open = open;
            this.log = log;
        }

        AddonView view() {
            return view;
        }

        private boolean call(String what, java.util.function.BooleanSupplier action) {
            try {
                return action.getAsBoolean();
            } catch (RuntimeException | LinkageError e) {
                if (!failed) {
                    failed = true;
                    log.warn("Menu of " + addon + " failed in " + what, e);
                }
                return false;
            }
        }

        @Override
        public void render(HudGraphics graphics, int width, int height, int mouseX, int mouseY) {
            call("render", () -> {
                view.render(graphics, width, height, mouseX, mouseY);
                return true;
            });
        }

        @Override
        public boolean mouseClicked(double x, double y, int button) {
            return call("mouseClicked", () -> view.mouseClicked(x, y, button));
        }

        @Override
        public boolean mouseReleased(double x, double y, int button) {
            return call("mouseReleased", () -> view.mouseReleased(x, y, button));
        }

        @Override
        public boolean mouseDragged(double x, double y, int button) {
            return call("mouseDragged", () -> view.mouseDragged(x, y, button));
        }

        @Override
        public boolean mouseScrolled(double x, double y, double delta) {
            return call("mouseScrolled", () -> view.mouseScrolled(x, y, delta));
        }

        @Override
        public boolean keyPressed(int key, boolean ctrl, boolean shift) {
            return call("keyPressed", () -> view.keyPressed(key, ctrl, shift));
        }

        @Override
        public boolean charTyped(char ch) {
            return call("charTyped", () -> view.charTyped(ch));
        }

        @Override
        public boolean closeRequested() {
            try {
                return view.closeRequested();
            } catch (RuntimeException | LinkageError e) {
                return true; // a broken view must stay closable
            }
        }

        @Override
        public void removed() {
            synchronized (open) {
                open.remove(this);
            }
            call("removed", () -> {
                view.removed();
                return true;
            });
        }
    }
}
