package dev.lumora.core.building;

import dev.lumora.api.Registration;
import dev.lumora.api.building.BlockHit;
import dev.lumora.api.building.BlockInfo;
import dev.lumora.api.building.Building;
import dev.lumora.api.building.ItemSpec;
import dev.lumora.api.building.PickHandler;
import dev.lumora.api.building.UseHandler;
import dev.lumora.core.CoreLogger;
import dev.lumora.core.RegistrationTracker;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * The building tools of the Lumora API 1.10: hands each addon a {@link Building} that reads and acts through the
 * version adapter's {@link BuildingPlatform} and keeps the addon's click handlers and click delays, which go away when
 * the addon is disabled. The version adapter asks {@link #decideUse} before a right-click on a block,
 * {@link #handlePick} on pick-block and {@link #useDelay}/{@link #breakDelay} after a click.
 */
public final class BuildingApi {
    private static final class Owned<H> {
        final String owner;
        final H handler;

        Owned(String owner, H handler) {
            this.owner = owner;
            this.handler = handler;
        }

        String owner() {
            return owner;
        }

        H handler() {
            return handler;
        }
    }

    private final CoreLogger log;
    private final Supplier<BuildingPlatform> platform;
    private final List<Owned<UseHandler>> useHandlers = new CopyOnWriteArrayList<>();
    private final List<Owned<PickHandler>> pickHandlers = new CopyOnWriteArrayList<>();
    private final Map<String, Integer> useDelays = new ConcurrentHashMap<>();
    private final Map<String, Integer> breakDelays = new ConcurrentHashMap<>();
    /** Outlined blocks of all addons: x, y, z, ARGB (API 1.11). */
    private final List<int[]> highlights = new CopyOnWriteArrayList<>();
    /** Addons whose delays are removed again when they are disabled. */
    private final Set<String> delayOwners = ConcurrentHashMap.newKeySet();
    /** Handlers that threw once (logged once, then skipped). */
    private final Set<Object> broken = ConcurrentHashMap.newKeySet();

    public BuildingApi(CoreLogger log, Supplier<BuildingPlatform> platform) {
        this.log = log;
        this.platform = platform;
    }

    private BuildingPlatform platform() {
        BuildingPlatform p = platform.get();
        return p == null ? BuildingPlatform.NONE : p;
    }

    // --- asked by the version adapter

    /** Decision for a right-click on a block: the first handler that does not answer NORMAL wins. */
    public UseHandler.Decision decideUse(BlockHit hit, ItemSpec held) {
        for (Owned<UseHandler> o : useHandlers) {
            if (broken.contains(o)) {
                continue;
            }
            try {
                UseHandler.Decision d = o.handler().onUse(hit, held);
                if (d != null && d != UseHandler.Decision.NORMAL) {
                    return d;
                }
            } catch (RuntimeException | LinkageError e) {
                fail(o, e);
            }
        }
        return UseHandler.Decision.NORMAL;
    }

    /** @return true if an addon handled the pick-block (Minecraft's own pick is skipped) */
    public boolean handlePick(BlockHit hit) {
        for (Owned<PickHandler> o : pickHandlers) {
            if (broken.contains(o)) {
                continue;
            }
            try {
                if (o.handler().onPick(hit)) {
                    return true;
                }
            } catch (RuntimeException | LinkageError e) {
                fail(o, e);
            }
        }
        return false;
    }

    /** Shortest right-click delay any addon asked for, -1 if none (Minecraft's own). */
    public int useDelay() {
        return shortest(useDelays);
    }

    /** Shortest break delay any addon asked for, -1 if none (Minecraft's own). */
    public int breakDelay() {
        return shortest(breakDelays);
    }

    /** The outlined blocks to draw this frame (x, y, z, ARGB each); empty most of the time. */
    public List<int[]> highlights() {
        return highlights;
    }

    /** Whether any addon listens to right-clicks (the adapter skips building the hit otherwise). */
    public boolean hasUseHandlers() {
        return !useHandlers.isEmpty();
    }

    /** Whether any addon listens to pick-block. */
    public boolean hasPickHandlers() {
        return !pickHandlers.isEmpty();
    }

    static int shortest(Map<String, Integer> delays) {
        int best = -1;
        for (int d : delays.values()) {
            if (d >= 0 && (best < 0 || d < best)) {
                best = d;
            }
        }
        return best;
    }

    private void fail(Owned<?> o, Throwable e) {
        if (broken.add(o)) {
            log.warn("A building handler of " + o.owner() + " failed; it is skipped from now on", e);
        }
    }

    // --- per addon

    /** The {@link Building} of one addon; {@code guard} checks that the addon is active and has the permission. */
    public Building scoped(String addon, RegistrationTracker tracker, Runnable guard) {
        return new Building() {
            private void trackDelays() {
                if (delayOwners.add(addon)) {
                    tracker.track(RegistrationTracker.of(() -> {
                        useDelays.remove(addon);
                        breakDelays.remove(addon);
                        delayOwners.remove(addon);
                    }));
                }
            }

            @Override
            public boolean available() {
                return platform().available();
            }

            @Override
            public boolean creative() {
                return platform().creative();
            }

            @Override
            public Optional<BlockHit> target() {
                guard.run();
                return platform().target();
            }

            @Override
            public BlockInfo block(int x, int y, int z) {
                guard.run();
                return platform().block(x, y, z);
            }

            @Override
            public int selectedSlot() {
                return platform().selectedSlot();
            }

            @Override
            public void selectHotbar(int slot) {
                guard.run();
                if (slot >= 0 && slot <= 8) {
                    platform().selectHotbar(slot);
                }
            }

            @Override
            public ItemSpec hotbar(int slot) {
                return slot >= 0 && slot <= 8 ? platform().hotbar(slot) : ItemSpec.EMPTY;
            }

            @Override
            public ItemSpec mainHand() {
                return platform().mainHand();
            }

            @Override
            public boolean setCreativeSlot(int slot, ItemSpec item) {
                guard.run();
                return slot >= 0 && slot <= 8 && item != null && platform().setCreativeSlot(slot, item);
            }

            @Override
            public ItemSpec pick(BlockHit hit, boolean withState) {
                guard.run();
                return hit == null ? ItemSpec.EMPTY : platform().pick(hit, withState);
            }

            @Override
            public boolean breakBlock(BlockHit hit) {
                guard.run();
                return hit != null && platform().breakBlock(hit);
            }

            @Override
            public boolean place(BlockHit hit) {
                guard.run();
                return hit != null && platform().place(hit);
            }

            @Override
            public void setUseDelay(int ticks) {
                guard.run();
                trackDelays();
                useDelays.put(addon, ticks < 0 ? -1 : Math.min(ticks, 20));
            }

            @Override
            public void setBreakDelay(int ticks) {
                guard.run();
                trackDelays();
                breakDelays.put(addon, ticks < 0 ? -1 : Math.min(ticks, 20));
            }

            @Override
            public Registration onUse(UseHandler handler) {
                guard.run();
                Owned<UseHandler> o = new Owned<>(addon, handler);
                useHandlers.add(o);
                return tracker.track(RegistrationTracker.of(() -> {
                    useHandlers.remove(o);
                    broken.remove(o);
                }));
            }

            @Override
            public List<String> blockIds() {
                guard.run();
                return platform().blockIds();
            }

            @Override
            public int averageColor(String blockId) {
                guard.run();
                return blockId == null ? 0 : platform().averageColor(blockId);
            }

            @Override
            public List<ItemSpec> creativeItems(String search, boolean blocksOnly) {
                guard.run();
                return platform().creativeItems(search == null ? "" : search, blocksOnly);
            }

            @Override
            public Registration highlight(int x, int y, int z, int argb) {
                guard.run();
                int[] box = {x, y, z, argb};
                highlights.add(box);
                return tracker.track(RegistrationTracker.of(() -> highlights.remove(box)));
            }

            @Override
            public Registration onPick(PickHandler handler) {
                guard.run();
                Owned<PickHandler> o = new Owned<>(addon, handler);
                pickHandlers.add(o);
                return tracker.track(RegistrationTracker.of(() -> {
                    pickHandlers.remove(o);
                    broken.remove(o);
                }));
            }
        };
    }
}
