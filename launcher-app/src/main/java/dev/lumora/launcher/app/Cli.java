package dev.lumora.launcher.app;

import dev.lumora.launcher.core.PlayService;
import dev.lumora.launcher.core.diagnostics.CrashAnalyzer;
import dev.lumora.launcher.core.diagnostics.SystemCheck;
import dev.lumora.launcher.core.install.ProgressListener;
import dev.lumora.launcher.core.launch.GameProcess;
import dev.lumora.launcher.core.profile.ProfileCatalog;
import dev.lumora.launcher.core.util.Log;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Headless commands for CI, support and automated verification:
 * <pre>
 *   --system-check                     print the environment check
 *   --install-only [--profile id] [--repair]   download and verify everything, do not start
 *   --launch-test [--profile id] [--loader lumora|vanilla] [--timeout seconds]
 *       start test without the Lumora report: waits for the sound engine, runs 15 s, stops the game
 *   --smoke-test [--profile id] [--timeout seconds]
 *       install, start the profile in the official demo mode (no account needed), wait until the companion
 *       mod reports the title screen, then exit. Exit code 0 = passed.
 * </pre>
 */
final class Cli {
    private Cli() {
    }

    static int run(String[] args) {
        List<String> a = List.of(args);
        try {
            AppContext ctx = new AppContext();
            ProfileCatalog.Profile profile = ctx.catalog.profile(option(a, "--profile", ctx.config.selectedProfile))
                    .orElseThrow(() -> new IllegalArgumentException("Unbekanntes Profil. Verfügbar: "
                            + ctx.catalog.profiles().stream().map(p -> p.id).toList()));
            if (a.contains("--system-check")) {
                System.out.print(SystemCheck.run(ctx.os, ctx.paths).render());
                return 0;
            }
            if (a.contains("--install-only")) {
                boolean machine = a.contains("--machine-progress");
                var prepared = ctx.installer.prepare(profile, ctx.config, a.contains("--repair"),
                        machine ? machineProgress() : consoleProgress());
                dev.lumora.launcher.core.install.GameThemeSeeder.seed(prepared.gameDir(), ctx.config);
                System.out.println("OK: profile " + profile.id + " installed, java=" + prepared.javaExecutable());
                System.out.flush();
                return 0;
            }
            if (a.contains("--ui-shot")) {
                return uiShot(ctx, Path.of(option(a, "--ui-shot", "ui-shots")), List.of(option(a, "--pages", "home").split(",")));
            }
            if (a.contains("--launch-test")) {
                return launchTest(ctx, profile, dev.lumora.launcher.core.profile.Flavor.of(option(a, "--loader", "lumora")),
                        Integer.parseInt(option(a, "--timeout", "600")));
            }
            if (a.contains("--smoke-test")) {
                return smokeTest(ctx, profile, Integer.parseInt(option(a, "--timeout", "600")), a.contains("--crash-test"));
            }
            System.err.println("Unbekannte Option. Siehe docs/BUILD.md");
            return 2;
        } catch (Exception e) {
            Log.error("CLI command failed", e);
            System.err.println("FEHLER: " + e.getMessage());
            return 1;
        } finally {
            Log.close();
        }
    }

    /**
     * @param crashTest the companion mod crashes the game on purpose once the main menu is reached; the test then
     *                  checks that the launcher finds and analyzes the crash report (crash page pipeline end to end)
     */
    private static int smokeTest(AppContext ctx, ProfileCatalog.Profile profile, int timeoutSeconds, boolean crashTest) throws Exception {
        java.time.Instant started = java.time.Instant.now();
        if (crashTest) {
            // only in this CLI process, never saved to the launcher config
            ctx.config.extraJvmArgs = new java.util.ArrayList<>(ctx.config.extraJvmArgs);
            ctx.config.extraJvmArgs.add("-Dlumora.smokeTest.crash=true");
        }
        Path report = ctx.paths.diagnostics().resolve("smoke-" + profile.id + ".json");
        Files.deleteIfExists(report);
        PlayService.Listener listener = new PlayService.Listener() {
            @Override
            public void state(PlayService.State state, String message) {
                System.out.println("[state] " + state + " " + message);
            }

            @Override
            public void gameOutput(String line) {
                System.out.println("[game] " + line);
            }

            @Override
            public void stage(String description) {
                System.out.println("[stage] " + description);
            }

            @Override
            public void progress(double fraction, String detail) {
            }
        };
        GameProcess game = ctx.play.play(profile, ctx.config, PlayService.Mode.SMOKE_TEST_DEMO, false,
                (auth, cancelled) -> {
                    throw new dev.lumora.launcher.core.auth.AuthException(
                            dev.lumora.launcher.core.auth.AuthException.Reason.NOT_CONFIGURED, "Smoke test does not sign in");
                }, () -> false, report, listener);
        GameProcess.Exit exit;
        try {
            exit = game.onExit().get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            System.out.println("SMOKE TEST FAILED: timeout after " + timeoutSeconds + " s, stopping game");
            game.stop(java.time.Duration.ofSeconds(20));
            return 1;
        }
        boolean reportOk = Files.isRegularFile(report);
        System.out.println("Game exit code: " + exit.code());
        if (crashTest) {
            return crashTestResult(ctx, profile, exit, started);
        }
        if (reportOk) {
            System.out.println("Report " + report + ":");
            System.out.println(Files.readString(report));
        }
        if (exit.crashed() || !reportOk) {
            for (CrashAnalyzer.Hint h : CrashAnalyzer.analyze(exit.outputLog(), exit.crashReport())) {
                System.out.println("HINT: " + h.title() + " - " + h.advice());
            }
            System.out.println("SMOKE TEST FAILED (see " + exit.outputLog() + ")");
            return 1;
        }
        String verdict = smokeVerdict(report);
        if (!verdict.startsWith("PASSED")) {
            System.out.println("SMOKE TEST FAILED: " + verdict);
            return 1;
        }
        System.out.println("SMOKE TEST PASSED");
        return 0;
    }

    /**
     * Developer option: opens the launcher window (test data folder recommended: LUMORA_HOME), shows the given pages
     * one after another and saves a PNG of each into {@code dir}, then closes. Used for the documentation images.
     */
    private static int uiShot(AppContext ctx, Path dir, List<String> pages) throws Exception {
        LauncherWindow[] w = new LauncherWindow[1];
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            dev.lumora.launcher.app.ui.Theme.install(ctx.config.accentColor, ctx.config.pixelStyle, ctx.config.reduceMotion);
            w[0] = new LauncherWindow(ctx);
            w[0].show(false);
        });
        Thread.sleep(2500);
        for (String page : pages) {
            javax.swing.SwingUtilities.invokeAndWait(() -> w[0].showPage(page.strip()));
            Thread.sleep(2500);
            Path file = dir.resolve("launcher-" + page.strip().replace('/', '-') + ".png");
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                try {
                    w[0].capture(file);
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            });
            System.out.println("UI SHOT " + file.toAbsolutePath());
        }
        javax.swing.SwingUtilities.invokeAndWait(() -> w[0].window().dispose());
        return 0;
    }

    /** Log lines that every supported Minecraft version prints once the game is up (sound engine ready). */
    static final java.util.regex.Pattern STARTED = java.util.regex.Pattern.compile("Sound engine started|OpenAL initialized");
    /** How long the game must keep running after the marker. */
    static final int STABLE_SECONDS = 15;

    /**
     * Start test that works without the Lumora mod (any version): install, start in demo mode, wait for the
     * "sound engine started" log line, keep the game running for {@value #STABLE_SECONDS} s, then stop it.
     * Passed = the marker appeared and the game did not exit or crash on its own.
     */
    private static int launchTest(AppContext ctx, ProfileCatalog.Profile profile, dev.lumora.launcher.core.profile.Flavor flavor,
                                  int timeoutSeconds) throws Exception {
        java.util.concurrent.CompletableFuture<String> marker = new java.util.concurrent.CompletableFuture<>();
        java.util.List<String> loaded = new java.util.concurrent.CopyOnWriteArrayList<>();
        PlayService.Listener listener = new PlayService.Listener() {
            @Override
            public void state(PlayService.State state, String message) {
                System.out.println("[state] " + state + " " + message);
            }

            @Override
            public void gameOutput(String line) {
                if (line.contains("Loading ") && line.contains(" mods")) {
                    loaded.add(line);
                }
                if (STARTED.matcher(line).find()) {
                    marker.complete(line);
                }
            }

            @Override
            public void stage(String description) {
                System.out.println("[stage] " + description);
            }

            @Override
            public void progress(double fraction, String detail) {
            }
        };
        Path report = ctx.paths.diagnostics().resolve("launch-" + flavor.instanceId(profile.id) + ".json");
        GameProcess game = ctx.play.play(profile, flavor, ctx.config, PlayService.Mode.SMOKE_TEST_DEMO, false,
                (auth, cancelled) -> {
                    throw new dev.lumora.launcher.core.auth.AuthException(
                            dev.lumora.launcher.core.auth.AuthException.Reason.NOT_CONFIGURED, "Launch test does not sign in");
                }, () -> false, report, null, listener);
        String line;
        try {
            line = java.util.concurrent.CompletableFuture.anyOf(marker, game.onExit()).get(timeoutSeconds, TimeUnit.SECONDS) instanceof String s ? s : null;
        } catch (TimeoutException e) {
            System.out.println("LAUNCH TEST FAILED: no start marker after " + timeoutSeconds + " s");
            game.stop(java.time.Duration.ofSeconds(20));
            return 1;
        }
        if (line == null) {
            GameProcess.Exit exit = game.onExit().get();
            System.out.println("LAUNCH TEST FAILED: game exited (code " + exit.code() + ") before it was up, see " + exit.outputLog());
            for (CrashAnalyzer.Hint h : CrashAnalyzer.analyze(exit.outputLog(), exit.crashReport())) {
                System.out.println("HINT: " + h.title() + " - " + h.advice());
            }
            return 1;
        }
        System.out.println("Marker: " + line.strip());
        loaded.forEach(l -> System.out.println("Mods: " + l.strip()));
        try {
            GameProcess.Exit early = game.onExit().get(STABLE_SECONDS, TimeUnit.SECONDS);
            System.out.println("LAUNCH TEST FAILED: game exited within the " + STABLE_SECONDS + " s window (code " + early.code() + "), see " + early.outputLog());
            return 1;
        } catch (TimeoutException stillRunning) {
            game.stop(java.time.Duration.ofSeconds(20));
        }
        System.out.println("LAUNCH TEST PASSED: Minecraft " + profile.minecraftVersion + " (" + flavor.label()
                + (flavor == dev.lumora.launcher.core.profile.Flavor.VANILLA ? ""
                : ", Lumora Loader") + ") started and ran " + STABLE_SECONDS + " s");
        return 0;
    }

    /** The "smokeTest" verdict the client wrote into its report (the client checks menus, world and the Lumora API). */
    private static String smokeVerdict(Path report) {
        try {
            var root = com.google.gson.JsonParser.parseString(Files.readString(report)).getAsJsonObject();
            return root.has("smokeTest") ? root.get("smokeTest").getAsString() : "no verdict in report";
        } catch (IOException | RuntimeException e) {
            return "unreadable report: " + e.getMessage();
        }
    }

    private static int crashTestResult(AppContext ctx, ProfileCatalog.Profile profile, GameProcess.Exit exit,
                                       java.time.Instant started) {
        if (!exit.crashed()) {
            System.out.println("CRASH TEST FAILED: the game did not crash (exit code 0)");
            return 1;
        }
        var reports = dev.lumora.launcher.core.crash.CrashReports.list(ctx.paths, List.of(profile.id)).stream()
                .filter(r -> !r.time().isBefore(started.minusSeconds(5))).toList();
        if (reports.isEmpty()) {
            System.out.println("CRASH TEST FAILED: no crash report found");
            return 1;
        }
        var r = reports.getFirst();
        System.out.println("Crash report: " + r.file() + " (" + r.source() + ")");
        System.out.println("Description: " + r.description());
        System.out.println("Exception: " + r.exception());
        r.suspects().forEach(s -> System.out.println("Suspect: " + s.name() + " (" + s.modId() + ", score " + s.score() + ") - " + s.reason()));
        r.hints().forEach(h -> System.out.println("Hint: " + h.title()));
        boolean ok = r.exception() != null && r.exception().contains("intentional crash")
                && !r.suspects().isEmpty() && r.suspects().getFirst().modId().equals("lumora");
        System.out.println(ok ? "CRASH TEST PASSED" : "CRASH TEST FAILED: analysis did not find the intentional crash");
        return ok ? 0 : 1;
    }

    private static ProgressListener consoleProgress() {
        return new ProgressListener() {
            private long last;

            @Override
            public void stage(String description) {
                System.out.println("[stage] " + description);
            }

            @Override
            public void progress(double fraction, String detail) {
                long now = System.currentTimeMillis();
                if (now - last > 2000 && fraction >= 0) {
                    last = now;
                    System.out.printf("  %3.0f%% %s%n", fraction * 100, detail);
                }
            }
        };
    }

    /**
     * Progress for the Windows installer (packaging/windows/setup), which runs {@code --install-only} for every chosen
     * version: UTF-8 lines "@stage text" and "@progress 0.42 detail", at most about seven per second.
     */
    private static ProgressListener machineProgress() {
        java.io.PrintStream out = new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out), true,
                java.nio.charset.StandardCharsets.UTF_8);
        System.setOut(out);
        return new ProgressListener() {
            private long last;

            @Override
            public void stage(String description) {
                out.println("@stage " + description.replace('\n', ' '));
            }

            @Override
            public void progress(double fraction, String detail) {
                long now = System.currentTimeMillis();
                if (now - last > 140 || fraction >= 1) {
                    last = now;
                    out.println(String.format(java.util.Locale.ROOT, "@progress %.4f %s", fraction,
                            detail == null ? "" : detail.replace('\n', ' ')));
                }
            }
        };
    }

    private static String option(List<String> a, String name, String def) {
        int i = a.indexOf(name);
        return i >= 0 && i + 1 < a.size() ? a.get(i + 1) : def;
    }
}
