package dev.lumora.core.connection;

import dev.lumora.api.render.HudGraphics;
import dev.lumora.core.Platform;
import dev.lumora.core.connection.WorldLoadAttempt.Stage;
import dev.lumora.core.social.GameSession;

import java.util.ArrayList;
import java.util.List;

/**
 * Lumora's screen while a singleplayer world opens, in the look of the connecting screen. Minecraft's own loading
 * screens keep running underneath and do the work; this view shows the world, an overall progress bar and every step
 * with its time: read the save (or create the world), data packs, start the server, prepare the spawn area, load the
 * terrain. Opening a world cannot be cancelled in Minecraft, so there is no cancel button.
 */
final class WorldLoadingView extends ConnectionView {
    private final WorldLoadAttempt attempt;
    private final WorldLoadTracker tracker;
    private float shownProgress;
    private boolean logged;

    WorldLoadingView(Platform platform, ConnectionModule module, WorldLoadAttempt attempt, WorldLoadTracker tracker) {
        super(platform, module);
        this.attempt = attempt;
        this.tracker = tracker;
    }

    /** The steps shown for this world: a new world is created instead of read, and has no data pack step. */
    private List<Stage> steps(boolean created) {
        List<Stage> out = new ArrayList<>();
        if (created) {
            out.add(Stage.CREATE);
        } else {
            out.add(Stage.READ);
            out.add(Stage.RESOURCES);
        }
        out.add(Stage.SERVER);
        out.add(Stage.SPAWN);
        out.add(Stage.TERRAIN);
        return out;
    }

    private String label(Stage s) {
        return switch (s) {
            case READ -> t("Weltdaten lesen", "Read world data");
            case CREATE -> t("Welt anlegen", "Create the world");
            case RESOURCES -> t("Datenpakete laden", "Load data packs");
            case SERVER -> t("Server starten", "Start the server");
            case SPAWN -> t("Spawnbereich vorbereiten", "Prepare the spawn area");
            case TERRAIN -> t("Gelände laden", "Load the terrain");
        };
    }

    /** Overall progress 0..1 over the steps of this world, with the reported progress of the current one. */
    static float overall(List<Stage> steps, Stage now, int progress) {
        int i = now == null ? -1 : steps.indexOf(now);
        if (i < 0) {
            // a stage that is not in the list (e.g. data packs while creating): position by order
            i = 0;
            for (int k = 0; k < steps.size(); k++) {
                if (now != null && steps.get(k).ordinal() < now.ordinal()) {
                    i = k + 1;
                }
            }
            i = Math.min(i, steps.size() - 1);
        }
        float within = progress >= 0 ? Math.min(100, progress) / 100f : 0f;
        return Math.min(1f, (i + within) / steps.size());
    }

    @Override
    void draw(HudGraphics g, int width, int height, int mx, int my) {
        boolean created = attempt.newWorld() || tracker.reached(Stage.CREATE);
        Stage reported = attempt.stage();
        if (created && (reported == Stage.READ || reported == Stage.RESOURCES)) {
            reported = Stage.CREATE; // a new world: reading its fresh data is part of creating it
        }
        Stage now = tracker.update(reported);
        List<Stage> steps = steps(created);
        int progress = attempt.progress();
        float target = overall(steps, now, progress);
        // smooth, never backwards
        shownProgress = Math.max(shownProgress, shownProgress + (target - shownProgress) * 0.15f);

        int cw = Math.min(320, width - 24);
        int ch = 10 + 11 + 10 + 6 + 6 + 8 + 6 + steps.size() * 12 + 24;
        int cx = width / 2;
        int x = cx - cw / 2;
        int y = Math.max(height >= ch + 60 ? 44 : 6, (height - ch) / 2 + 10);
        if (y + ch > height - 4) {
            y = Math.max(4, height - ch - 4);
        }
        if (y >= 40) {
            brand(g, cx, y - 40, created ? t("Welt wird erstellt", "Creating the world") : t("Welt wird geladen", "Loading the world"));
        }
        g.card(x, y, cw, ch, 6, SURFACE, BORDER);

        // world and overall progress
        int px = x + 12;
        int py = y + 10;
        String name = attempt.worldName();
        String title = name == null || name.isBlank() ? t("Einzelspieler-Welt", "Singleplayer world") : name;
        String percent = Math.round(shownProgress * 100) + " %";
        g.text("§l" + ConnectingView.fit(g, title, cw - 70), px, py, TEXT, false);
        g.rightText(percent, x + cw - 12, py, ACCENT_HOVER, false);
        py += 11;
        g.text(created ? t("Neue Welt  ·  Einzelspieler", "New world  ·  Singleplayer") : t("Einzelspieler", "Singleplayer"),
                px, py, MUTED, false);
        py += 14;
        int bw = cw - 24;
        g.roundedRect(px, py, bw, 6, 3, 0xFF2A2338);
        int fill = Math.round(bw * shownProgress);
        if (fill >= 6) {
            g.roundedRect(px, py, fill, 6, 3, ACCENT);
        }
        py += 14;
        g.fill(px, py, x + cw - 12, py + 1, BORDER);
        py += 6;

        // steps
        for (Stage s : steps) {
            boolean current = s == now;
            boolean done = now != null && s.ordinal() < now.ordinal();
            boolean skipped = done && !tracker.reached(s);
            int iconX = px + 4;
            if (current) {
                spinner(g, iconX + 2, py + 4, ACCENT_HOVER);
            } else if (skipped) {
                g.fill(iconX - 1, py + 3, iconX + 5, py + 5, DIM);
            } else if (done) {
                g.text("✔", iconX - 2, py, OK, false);
            } else {
                g.roundedRect(iconX - 1, py + 1, 6, 6, 3, 0xFF3A3150);
            }
            int color = current ? TEXT : done && !skipped ? MUTED : DIM;
            String label = label(s) + (current && progress >= 0 && (s == Stage.SPAWN || s == Stage.TERRAIN) ? "  " + progress + " %" : "");
            g.text(skipped ? label + t(" (nicht nötig)", " (not needed)") : label, px + 16, py, color, false);
            long ms = tracker.millis(s);
            if ((current || done) && !skipped && ms >= 0) {
                g.rightText(duration(ms), x + cw - 12, py, current ? ACCENT_HOVER : DIM, false);
            }
            py += 12;
        }

        // footer: account and elapsed time
        int fy = y + ch - 18;
        String account = platform.session().map(GameSession::name).orElse("");
        String foot = (account.isEmpty() ? "" : t("als ", "as ") + account + "  ·  ") + duration(tracker.elapsedMillis());
        g.text(ConnectingView.fit(g, foot, cw - 24), px, fy, DIM, false);
    }

    @Override
    public boolean closeRequested() {
        return false; // Minecraft cannot cancel opening a world
    }

    @Override
    public void removed() {
        tracker.finish();
        if (!logged) {
            logged = true;
            // stages and times only, no world name in the log
            platform.logger().info("World load: " + tracker.summary());
        }
    }
}
