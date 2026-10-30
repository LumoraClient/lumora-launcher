package dev.lumora.core.notify;

import dev.lumora.api.notify.Notification;
import dev.lumora.api.notify.NotificationType;
import dev.lumora.api.notify.Notifications;
import dev.lumora.api.render.Colors;
import dev.lumora.api.render.HudGraphics;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

/**
 * Queue and renderer of notifications: at most {@value #MAX_VISIBLE} cards at the top right, sliding in and
 * fading out; up to {@value #MAX_QUEUED} wait, further ones are dropped (an addon cannot flood the screen).
 */
public final class NotificationCenter implements Notifications {
    static final int MAX_VISIBLE = 4;
    static final int MAX_QUEUED = 32;
    static final int WIDTH = 168;
    private static final long FADE_IN = 180;
    private static final long FADE_OUT = 320;

    private final Queue<Notification> queue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger queued = new AtomicInteger();
    private final List<Active> active = new ArrayList<>();
    private final LongSupplier clock;

    private static final class Active {
        final Notification notification;
        long start = -1;
        List<String> lines;

        Active(Notification n) {
            this.notification = n;
        }
    }

    public NotificationCenter(LongSupplier clock) {
        this.clock = clock;
    }

    @Override
    public void show(Notification notification) {
        if (queued.incrementAndGet() > MAX_QUEUED) {
            queued.decrementAndGet();
            return;
        }
        queue.add(notification);
    }

    /** Notifications currently on screen (client thread). */
    public int visibleCount() {
        return active.size();
    }

    public int queuedCount() {
        return queued.get();
    }

    /** Client thread, every frame (also outside a world it simply draws on top). */
    public void render(HudGraphics g, int screenWidth) {
        long now = clock.getAsLong();
        active.removeIf(a -> a.start >= 0 && now - a.start > a.notification.durationMillis() + FADE_OUT);
        while (active.size() < MAX_VISIBLE) {
            Notification n = queue.poll();
            if (n == null) {
                break;
            }
            queued.decrementAndGet();
            active.add(new Active(n));
        }
        int y = 6;
        for (int i = 0; i < active.size(); i++) {
            Active a = active.get(i);
            if (a.start < 0) {
                a.start = now;
            }
            long age = now - a.start;
            long duration = a.notification.durationMillis();
            if (age > duration + FADE_OUT) {
                active.remove(i--);
                continue;
            }
            if (a.lines == null) {
                a.lines = wrap(g, a.notification.message(), WIDTH - 16 - (a.notification.icon() != null ? 20 : 0), 3);
            }
            float in = Math.min(1f, age / (float) FADE_IN);
            float out = age > duration ? 1f - (age - duration) / (float) FADE_OUT : 1f;
            float alpha = Math.max(0f, Math.min(in, out));
            int h = 22 + a.lines.size() * 10;
            int slide = Math.round((1f - in) * 24);
            int x = screenWidth - WIDTH - 6 + slide;
            draw(g, a, x, y, h, alpha);
            y += Math.round((h + 4) * Math.min(1f, out * 1.5f));
        }
    }

    private static void draw(HudGraphics g, Active a, int x, int y, int h, float alpha) {
        Notification n = a.notification;
        int accent = color(n.type());
        g.card(x, y, WIDTH, h, 5, Colors.fade(Colors.withAlpha(Colors.SURFACE, 0xF0), alpha), Colors.fade(Colors.BORDER, alpha));
        g.fill(x + 1, y + 4, x + 3, y + h - 4, Colors.fade(accent, alpha));
        int tx = x + 9;
        if (n.icon() != null) {
            g.texture(n.icon(), tx, y + 6, 16, 16, 16, 16);
            tx += 20;
        }
        int textColor = Colors.fade(Colors.TEXT, alpha);
        if (Colors.alpha(textColor) > 8) {
            g.text(fit(g, n.title(), WIDTH - (tx - x) - 6), tx, y + 6, Colors.fade(accent == Colors.ACCENT ? Colors.ACCENT_HOVER : accent, alpha), false);
            int ly = y + 18;
            for (String line : a.lines) {
                g.text(line, tx, ly, textColor, false);
                ly += 10;
            }
        }
    }

    static int color(NotificationType type) {
        return switch (type) {
            case INFO -> Colors.ACCENT;
            case SUCCESS -> Colors.OK;
            case WARNING -> Colors.WARNING;
            case ERROR -> Colors.ERROR;
        };
    }

    static String fit(HudGraphics g, String text, int width) {
        if (g.textWidth(text) <= width) {
            return text;
        }
        int end = text.length();
        while (end > 0 && g.textWidth(text.substring(0, end) + "…") > width) {
            end--;
        }
        return text.substring(0, end) + "…";
    }

    /** Word wrap to at most {@code maxLines} lines; the last one is shortened with an ellipsis. */
    static List<String> wrap(HudGraphics g, String text, int width, int maxLines) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return lines;
        }
        String[] words = text.trim().split("\\s+");
        int i = 0;
        while (i < words.length && lines.size() < maxLines) {
            StringBuilder line = new StringBuilder(words[i++]);
            while (i < words.length && g.textWidth(line + " " + words[i]) <= width) {
                line.append(' ').append(words[i++]);
            }
            lines.add(fit(g, line.toString(), width));
        }
        if (i < words.length) {
            // more text than lines: mark the cut
            String last = lines.get(lines.size() - 1);
            if (!last.endsWith("…")) {
                int end = last.length();
                while (end > 0 && g.textWidth(last.substring(0, end) + "…") > width) {
                    end--;
                }
                lines.set(lines.size() - 1, last.substring(0, end) + "…");
            }
        }
        return lines;
    }
}
