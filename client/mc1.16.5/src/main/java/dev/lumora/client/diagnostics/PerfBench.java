package dev.lumora.client.diagnostics;

import dev.lumora.client.LumoraClient;
import dev.lumora.client.api.LumoraRuntime;
import dev.lumora.client.gui.LumoraHubScreen;
import dev.lumora.client.perf.FrameStats;
import dev.lumora.core.perf.OcclusionCuller;
import dev.lumora.core.perf.Optimization;
import dev.lumora.core.perf.PerformanceEngine;
import dev.lumora.core.perf.PerformanceModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Performance comparison in the smoke test world (only with {@code -Dlumora.perfBench=true}): a stone wall with 225
 * zombies without AI and about 100 chests behind it, the world frozen ({@code /tick freeze}). Frame times are recorded
 * with every optimization off (the code paths from before the engine), all on, and each one on its own; then with the
 * hub open, and with the wall removed (culling with nothing to hide). Screenshots with entity culling off and on (open
 * view, view through a window in the wall) are written for a pixel comparison, and the module and each switch are
 * turned off and on a few times to check that the engine follows.
 */
final class PerfBench {
    private static final int WARMUP_TICKS = 40;
    private static final double SECONDS = 8;

    private record Step(String name, Consumer<Minecraft> setup) {
    }

    private final List<Step> steps = new ArrayList<>();
    private final Map<String, String> results = new LinkedHashMap<>();
    private final List<String> problems = new ArrayList<>();
    private final String shotPrefix;
    private int px;
    private int py;
    private int pz;
    private int index = -1;
    private int rebuilds;
    private int ticks;
    private boolean recording;
    private boolean done;

    PerfBench(String shotPrefix) {
        this.shotPrefix = shotPrefix;
    }

    boolean done() {
        return done;
    }

    Map<String, String> results() {
        return results;
    }

    List<String> problems() {
        return problems;
    }

    private static void command(Minecraft mc, String cmd) {
        var server = mc.getSingleplayerServer();
        if (server == null) {
            return;
        }
        server.execute(() -> server.getCommands().performCommand(server.createCommandSourceStack().withSuppressedOutput(), cmd));
    }

    private static void module(boolean on) {
        LumoraRuntime.core().addonManager().setWanted(PerformanceModule.ID, on);
    }

    /** Module on with only the given optimizations switched on. */
    private static void only(Optimization... on) {
        module(true);
        var settings = LumoraRuntime.core().addonManager().record(PerformanceModule.ID).orElseThrow().configuration().settings();
        for (Optimization o : Optimization.values()) {
            boolean want = false;
            for (Optimization x : on) {
                want |= x == o;
            }
            boolean w = want;
            settings.get(o.key()).ifPresent(s -> s.decode(Boolean.toString(w)));
        }
    }

    private void build(Minecraft mc) {
        var p = mc.player.blockPosition();
        px = p.getX();
        py = p.getY();
        pz = p.getZ();
        for (String rule : new String[]{"doDaylightCycle false", "doWeatherCycle false", "doMobSpawning false"}) {
            command(mc, "gamerule " + rule);
        }
        command(mc, "time set noon");
        command(mc, "weather clear");
        command(mc, "gamemode creative @p");
        // flat area: stone floor, air above (in slices: /fill is limited to 32768 blocks)
        for (int x = px - 4; x <= px + 60; x += 8) {
            command(mc, String.format("fill %d %d %d %d %d %d stone", x, py - 1, pz - 20, x + 7, py - 1, pz + 20));
            command(mc, String.format("fill %d %d %d %d %d %d air", x, py, pz - 20, x + 7, py + 24, pz + 20));
        }
        wall(mc, false);
        int zombies = 0;
        for (int x = px + 12; x <= px + 40; x += 2) {
            for (int z = pz - 14; z <= pz + 14; z += 2) {
                command(mc, String.format("summon husk %d %d %d {NoAI:1b,Silent:1b,PersistenceRequired:1b}", x, py, z));
                zombies++;
            }
        }
        for (int x = px + 13; x <= px + 41; x += 3) {
            for (int z = pz - 15; z <= pz + 15; z += 3) {
                command(mc, String.format("setblock %d %d %d chest", x, py, z));
            }
        }
        command(mc, String.format("tp @p %d %d %d -90 0", px + 2, py, pz));
        command(mc, "tick freeze");
        results.put("scene", zombies + " husks, chests every 3 blocks, wall 6 blocks in front of the camera");
    }

    private void wall(Minecraft mc, boolean window) {
        command(mc, String.format("fill %d %d %d %d %d %d stone", px + 8, py, pz - 20, px + 8, py + 14, pz + 20));
        if (window) {
            command(mc, String.format("fill %d %d %d %d %d %d air", px + 8, py + 1, pz - 1, px + 8, py + 2, pz + 1));
        }
    }

    private void noWall(Minecraft mc) {
        command(mc, String.format("fill %d %d %d %d %d %d air", px + 8, py, pz - 20, px + 8, py + 14, pz + 20));
    }

    private void plan() {
        // not counted: JIT and section meshes warm up (the first measurement would be too low)
        steps.add(new Step("warmup", mc -> module(false)));
        steps.add(new Step("wall/all-off", mc -> module(false)));
        steps.add(new Step("wall/all-on", mc -> only(Optimization.values())));
        steps.add(new Step("wall/entity_culling", mc -> only(Optimization.ENTITY_CULLING)));
        steps.add(new Step("wall/rendering", mc -> only(Optimization.RENDERING)));
        steps.add(new Step("wall/frame", mc -> only(Optimization.FRAME)));
        steps.add(new Step("wall/smart_hud", mc -> only(Optimization.SMART_HUD)));
        steps.add(new Step("wall/all-off-again", mc -> module(false)));
        steps.add(new Step("hub/all-off", mc -> {
            module(false);
            mc.setScreen(LumoraHubScreen.withTab(null, 0));
        }));
        steps.add(new Step("hub/smart_hud+frame", mc -> only(Optimization.SMART_HUD, Optimization.FRAME)));
        steps.add(new Step("hub/all-off-again", mc -> module(false)));
        steps.add(new Step("open/all-off", mc -> {
            mc.setScreen(null);
            noWall(mc);
        }));
        steps.add(new Step("open/entity_culling", mc -> only(Optimization.ENTITY_CULLING)));
        // alternating once more: the difference between two runs with the same settings shows the noise
        steps.add(new Step("open/all-off-2", mc -> module(false)));
        steps.add(new Step("open/entity_culling-2", mc -> only(Optimization.ENTITY_CULLING)));
        if (PerformanceEngine.isSupported(Optimization.CLOUDS)) {
            // sunset at ten times the tick rate: the cloud colour changes all the time; looking up at the clouds
            steps.add(new Step("clouds/all-off", mc -> {
                module(false);
                mc.player.yRot = 90f;
                mc.player.yRotO = 90f;
                mc.player.xRot = -35f;
                mc.player.xRotO = -35f;
                command(mc, "tick unfreeze");
                command(mc, "time set 12000");
                command(mc, "gamerule doDaylightCycle true");
            }));
            steps.add(new Step("clouds/clouds", mc -> {
                only(Optimization.CLOUDS);
                command(mc, "time set 12000");
            }));
            steps.add(new Step("clouds/all-off-2", mc -> {
                module(false);
                command(mc, "time set 12000");
            }));
            steps.add(new Step("clouds/clouds-2", mc -> {
                only(Optimization.CLOUDS);
                command(mc, "time set 12000");
            }));
        }
    }

    void tick(Minecraft mc) {
        if (done || mc.player == null) {
            return;
        }
        ticks++;
        if (index == -1) {
            if (ticks == 1) {
                mc.options.pauseOnLostFocus = false;
                build(mc);
                plan();
            } else if (ticks >= 100) { // fills, entities and section meshes settle
                if (mc.level.getBlockState(new net.minecraft.core.BlockPos(px + 8, py + 1, pz)).isAir() && rebuilds < 3) {
                    rebuilds++;
                    for (int x = px - 4; x <= px + 60; x += 8) {
                        command(mc, String.format("fill %d %d %d %d %d %d stone", x, py - 1, pz - 20, x + 7, py - 1, pz + 20));
                    }
                    wall(mc, false);
                    ticks = 40;
                    return;
                }
                results.put("scene rebuilds", Integer.toString(rebuilds));
                next(mc);
            }
            return;
        }
        if (index < steps.size()) {
            if (steps.get(index).name().startsWith("clouds/")) {
                command(mc, "time add 10"); // ten times the normal day speed: the cloud colour keeps changing
            }
            if (!recording && ticks >= WARMUP_TICKS) {
                cloudBuilds = dev.lumora.client.perf.CloudStats.builds();
                FrameStats.INSTANCE.start(SECONDS);
                recording = true;
            } else if (recording && FrameStats.INSTANCE.isFinished()) {
                FrameStats.Summary s = FrameStats.INSTANCE.finish();
                String culling = "";
                OcclusionCuller c = PerformanceEngine.culler();
                if (c != null && PerformanceEngine.entityCulling) {
                    culling = String.format(" | culled %d of %d, pass %.2f ms", c.lastHidden(), c.lastChecked(), c.lastPassNanos() / 1e6);
                }
                String clouds = steps.get(index).name().startsWith("clouds/")
                        ? " | cloud builds " + (dev.lumora.client.perf.CloudStats.builds() - cloudBuilds) : "";
                String line = (s == null ? "no frames" : s.format()) + culling + clouds;
                results.put(steps.get(index).name(), line);
                LumoraClient.LOG.info("LUMORA_PERF {} {}", steps.get(index).name(), line);
                if (steps.get(index).name().equals("wall/entity_culling") && (c == null || c.lastHidden() < c.lastChecked() * 9 / 10 || c.lastChecked() < 150)) {
                    results.put("wall/debug", wallDebug(mc));
                    problems.add("entity culling hid only " + (c == null ? 0 : c.lastHidden() + " of " + c.lastChecked()) + " targets behind the wall");
                }
                if (steps.get(index).name().equals("open/entity_culling")) {
                    // nothing opaque between camera and targets except the floor: log what was hidden (the screenshots decide)
                    results.put("open/hidden", hiddenTargets(mc));
                }
                recording = false;
                next(mc);
            }
            return;
        }
        afterMeasurements(mc);
    }

    /** Entities and chests the culler currently reports hidden, with their positions relative to the camera spot. */
    private String hiddenTargets(Minecraft mc) {
        StringBuilder sb = new StringBuilder();
        for (var e : mc.level.entitiesForRendering()) {
            int s = ((dev.lumora.core.perf.Cullable) e).lumora$cull();
            if ((s & 1) != 0) {
                sb.append(net.minecraft.core.Registry.ENTITY_TYPE.getKey(e.getType()).getPath()).append(String.format("@%.1f,%.1f,%.1f ", e.getX() - px, e.getY() - py, e.getZ() - pz));
            }
        }
        for (int x = px + 13; x <= px + 41; x += 3) {
            for (int z = pz - 15; z <= pz + 15; z += 3) {
                var be = mc.level.getBlockEntity(new net.minecraft.core.BlockPos(x, py, z));
                if (be != null && (((dev.lumora.core.perf.Cullable) be).lumora$cull() & 1) != 0) {
                    sb.append("chest@").append(x - px).append(',').append(z - pz).append(' ');
                }
            }
        }
        return sb.length() == 0 ? "none" : sb.toString().strip();
    }

    /** Camera and a few targets the culler kept visible behind the wall (diagnosis). */
    private String wallDebug(Minecraft mc) {
        var cam = mc.gameRenderer.getMainCamera().getPosition();
        StringBuilder sb = new StringBuilder(String.format(java.util.Locale.ROOT, "camera %.2f,%.2f,%.2f rel %.2f,%.2f,%.2f; visible:", cam.x, cam.y, cam.z, cam.x - px, cam.y - py, cam.z - pz));
        int n = 0;
        for (var e : mc.level.entitiesForRendering()) {
            int s = ((dev.lumora.core.perf.Cullable) e).lumora$cull();
            if ((s & 1) == 0 && s != 0 && n++ < 6) {
                sb.append(String.format(java.util.Locale.ROOT, " %s@%.1f,%.1f,%.1f", net.minecraft.core.Registry.ENTITY_TYPE.getKey(e.getType()).getPath(), e.getX() - px, e.getY() - py, e.getZ() - pz));
            }
        }
        sb.append(String.format(java.util.Locale.ROOT, "; wall block opaque: %s", mc.level.getBlockState(new net.minecraft.core.BlockPos(px + 8, py + 1, pz)).isSolidRender(mc.level, new net.minecraft.core.BlockPos(px + 8, py + 1, pz))));
        return sb.toString();
    }

    private void next(Minecraft mc) {
        index++;
        ticks = 0;
        if (index < steps.size()) {
            steps.get(index).setup().accept(mc);
        }
    }

    // screenshots (culling off/on, open view and window) and the switching test, after the measurements
    private int extra;
    private int cloudBuilds;

    private void afterMeasurements(Minecraft mc) {
        switch (extra) {
            case 0 -> {
                module(false);
                // back to a frozen noon (after the cloud measurement), camera level again
                command(mc, "gamerule doDaylightCycle false");
                command(mc, "time set noon");
                command(mc, "tick freeze");
                mc.player.yRot = -90f;
                mc.player.yRotO = -90f;
                mc.player.xRot = 0f;
                mc.player.xRotO = 0f;
                extra++;
                ticks = 0;
            }
            case 1 -> {
                if (ticks >= 30) {
                    shot(mc, "open-off");
                    only(Optimization.ENTITY_CULLING);
                    extra++;
                    ticks = 0;
                }
            }
            case 2 -> {
                if (ticks >= 30) {
                    shot(mc, "open-on");
                    module(false);
                    wall(mc, true);
                    extra++;
                    ticks = 0;
                }
            }
            case 3 -> {
                if (ticks >= 60) {
                    shot(mc, "window-off");
                    only(Optimization.ENTITY_CULLING);
                    extra++;
                    ticks = 0;
                }
            }
            case 4 -> {
                if (ticks >= 30) {
                    shot(mc, "window-on");
                    OcclusionCuller c = PerformanceEngine.culler();
                    results.put("window", c == null ? "no culler" : "culled " + c.lastHidden() + " of " + c.lastChecked());
                    extra++;
                    ticks = 0;
                }
            }
            case 5 -> {
                if (!PerformanceEngine.isSupported(Optimization.CLOUDS)) {
                    extra = 8;
                    return;
                }
                // clouds at a frozen sunset colour: off and on for a pixel comparison
                module(false);
                command(mc, "time set 12800");
                mc.player.yRot = 90f;
                mc.player.yRotO = 90f;
                mc.player.xRot = -35f;
                mc.player.xRotO = -35f;
                extra++;
                ticks = 0;
            }
            case 6 -> {
                if (ticks >= 40) {
                    shot(mc, "clouds-off");
                    only(Optimization.CLOUDS);
                    extra++;
                    ticks = 0;
                }
            }
            case 7 -> {
                if (ticks >= 2) {
                    shot(mc, "clouds-on");
                    module(false);
                    extra = 20;
                    ticks = 0;
                }
            }
            case 20 -> {
                if (ticks >= 2) {
                    shot(mc, "clouds-off-2");
                    mc.player.yRot = -90f;
                    mc.player.yRotO = -90f;
                    mc.player.xRot = 0f;
                    mc.player.xRotO = 0f;
                    extra = 8;
                    ticks = 0;
                }
            }
            case 8 -> {
                switching();
                command(mc, "tick unfreeze");
                only(Optimization.values());
                done = true;
            }
            default -> {
            }
        }
    }

    /** The module and each switch off and on a few times: the engine flags must follow every time. */
    private void switching() {
        int checks = 0;
        for (int round = 0; round < 5; round++) {
            module(false);
            for (Optimization o : Optimization.values()) {
                checks++;
                if (PerformanceEngine.isActive(o)) {
                    problems.add("switching: " + o + " still on with the module off (round " + round + ")");
                }
            }
            only(Optimization.values());
            for (Optimization o : Optimization.values()) {
                checks++;
                if (PerformanceEngine.isActive(o) != PerformanceEngine.isSupported(o)) {
                    problems.add("switching: " + o + " is " + PerformanceEngine.isActive(o) + " with everything on (round " + round + ")");
                }
            }
            for (Optimization o : Optimization.values()) {
                only(o);
                for (Optimization x : Optimization.values()) {
                    checks++;
                    boolean want = x == o && PerformanceEngine.isSupported(o);
                    if (PerformanceEngine.isActive(x) != want) {
                        problems.add("switching: only " + o + " -> " + x + " is " + PerformanceEngine.isActive(x));
                    }
                }
            }
        }
        results.put("switching", checks + " checks, " + problems.stream().filter(p -> p.startsWith("switching")).count() + " wrong");
    }

    private void shot(Minecraft mc, String name) {
        String file = shotPrefix + "-perf-" + name + ".png";
        Screenshot.grab(mc.gameDirectory, file, mc.getWindow().getWidth(), mc.getWindow().getHeight(), mc.getMainRenderTarget(),
                msg -> LumoraClient.LOG.info("Perf bench screenshot: {}", msg.getString()));
    }
}
