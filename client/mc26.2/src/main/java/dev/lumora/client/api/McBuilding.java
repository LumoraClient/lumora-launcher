package dev.lumora.client.api;

import dev.lumora.api.building.BlockHit;
import dev.lumora.api.building.BlockInfo;
import dev.lumora.api.building.ItemSpec;
import dev.lumora.api.building.UseHandler;
import dev.lumora.core.building.BuildingApi;
import dev.lumora.core.building.BuildingPlatform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Building tools of the Lumora API 1.10 for this Minecraft version: reads blocks and the hotbar and acts only with the
 * packets a player would send (creative slot, break, use item on, hotbar slot, sneak input). Every action checks
 * creative mode. The click hooks ({@code BuildingUseMixin}, {@code BuildingClientMixin}) ask {@link BuildingApi} through
 * {@link #decide} and {@link #pick(BlockHitResult)}.
 */
public final class McBuilding implements BuildingPlatform {
    public static final McBuilding INSTANCE = new McBuilding();

    /** Set while Lumora itself right-clicks ({@link #place}): the use handlers are not asked again. */
    private static boolean internal;
    /** Set during a right-click that counts as sneaking (Interaction Control): {@code Player#isSecondaryUseActive}. */
    private static boolean forceSecondary;
    /** Whether a block class reacts to right-clicks by itself (overrides useWithoutItem or useItemOn). */
    private static final Map<Class<?>, Boolean> INTERACTIVE = new ConcurrentHashMap<>();

    private McBuilding() {
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    private static BuildingApi api() {
        var core = LumoraRuntime.core();
        return core == null ? null : core.building();
    }

    // --- reading

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public boolean creative() {
        return mc().player != null && mc().gameMode != null && mc().gameMode.getPlayerMode() == GameType.CREATIVE;
    }

    @Override
    public Optional<BlockHit> target() {
        if (mc().level != null && mc().hitResult instanceof BlockHitResult b && b.getType() == HitResult.Type.BLOCK) {
            return Optional.of(hit(mc().level, b));
        }
        return Optional.empty();
    }

    @Override
    public BlockInfo block(int x, int y, int z) {
        Level level = mc().level;
        return level == null ? BlockInfo.AIR : info(level.getBlockState(new BlockPos(x, y, z)));
    }

    static BlockHit hit(Level level, BlockHitResult b) {
        BlockPos p = b.getBlockPos();
        Vec3 at = b.getLocation();
        return new BlockHit(p.getX(), p.getY(), p.getZ(), b.getDirection().getSerializedName(), at.x, at.y, at.z,
                info(level.getBlockState(p)));
    }

    static BlockInfo info(BlockState state) {
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        Map<String, String> props = new LinkedHashMap<>();
        Map<String, List<String>> values = new LinkedHashMap<>();
        for (Property<?> p : state.getProperties()) {
            props.put(p.getName(), value(state, p));
            values.put(p.getName(), values(p));
        }
        return new BlockInfo(id, props, values, interactive(state.getBlock()), state.hasBlockEntity());
    }

    private static <T extends Comparable<T>> String value(BlockState state, Property<T> p) {
        return p.getName(state.getValue(p));
    }

    private static <T extends Comparable<T>> List<String> values(Property<T> p) {
        List<String> out = new ArrayList<>();
        for (T v : p.getPossibleValues()) {
            out.add(p.getName(v));
        }
        return out;
    }

    /** Doors, chests, levers, crafting tables …: the block class itself handles right-clicks. */
    static boolean interactive(Block block) {
        return INTERACTIVE.computeIfAbsent(block.getClass(), c -> {
            for (Class<?> k = c; k != null && k != BlockBehaviour.class && k != Object.class; k = k.getSuperclass()) {
                for (Method m : k.getDeclaredMethods()) {
                    if (m.getName().equals("useWithoutItem") || m.getName().equals("useItemOn")) {
                        return true;
                    }
                }
            }
            return false;
        });
    }

    // --- items

    static ItemSpec spec(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return ItemSpec.EMPTY;
        }
        BlockItemStateProperties state = stack.get(DataComponents.BLOCK_STATE);
        return new ItemSpec(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount(),
                state == null ? Map.of() : state.properties(), stack.getItem() instanceof BlockItem,
                stack.getHoverName().getString());
    }

    static ItemStack stack(ItemSpec spec) {
        if (spec == null || spec.isEmpty()) {
            return ItemStack.EMPTY;
        }
        Identifier id = Identifier.tryParse(spec.id());
        Item item = id == null ? null : BuiltInRegistries.ITEM.getValue(id);
        if (item == null) {
            return ItemStack.EMPTY;
        }
        ItemStack s = new ItemStack(item, Math.max(1, Math.min(spec.count(), item.getDefaultMaxStackSize())));
        if (!spec.blockState().isEmpty() && item instanceof BlockItem) {
            s.set(DataComponents.BLOCK_STATE, new BlockItemStateProperties(spec.blockState()));
        }
        return s;
    }

    @Override
    public int selectedSlot() {
        LocalPlayer p = mc().player;
        return p == null ? 0 : p.getInventory().getSelectedSlot();
    }

    @Override
    public void selectHotbar(int slot) {
        LocalPlayer p = mc().player;
        if (p != null && slot >= 0 && slot <= 8) {
            p.getInventory().setSelectedSlot(slot);
        }
    }

    @Override
    public ItemSpec hotbar(int slot) {
        LocalPlayer p = mc().player;
        return p == null || slot < 0 || slot > 8 ? ItemSpec.EMPTY : spec(p.getInventory().getItem(slot));
    }

    @Override
    public ItemSpec mainHand() {
        LocalPlayer p = mc().player;
        return p == null ? ItemSpec.EMPTY : spec(p.getMainHandItem());
    }

    @Override
    public boolean setCreativeSlot(int slot, ItemSpec item) {
        LocalPlayer p = mc().player;
        if (!creative() || p == null || slot < 0 || slot > 8) {
            return false;
        }
        ItemStack s = stack(item);
        p.getInventory().setItem(slot, s);
        mc().gameMode.handleCreativeModeItemAdd(s, 36 + slot);
        return true;
    }

    @Override
    public ItemSpec pick(BlockHit hit, boolean withState) {
        Level level = mc().level;
        if (level == null) {
            return ItemSpec.EMPTY;
        }
        BlockPos pos = new BlockPos(hit.x(), hit.y(), hit.z());
        BlockState state = level.getBlockState(pos);
        ItemSpec spec = spec(state.getCloneItemStack(level, pos, false));
        if (!withState || spec.isEmpty() || !spec.block()) {
            return spec;
        }
        Map<String, String> keep = new LinkedHashMap<>();
        for (Property<?> p : state.getProperties()) {
            List<String> v = values(p);
            // the other half of doors, beds and tall plants and the water inside are not part of the block to place
            boolean twoPart = v.size() == 2 && (v.containsAll(List.of("upper", "lower")) || v.containsAll(List.of("head", "foot")));
            if (!twoPart && !p.getName().equals("waterlogged")) {
                keep.put(p.getName(), value(state, p));
            }
        }
        return keep.isEmpty() ? spec : spec.withBlockState(keep);
    }

    // --- acting

    @Override
    public boolean breakBlock(BlockHit hit) {
        LocalPlayer p = mc().player;
        if (!creative() || p == null) {
            return false;
        }
        Direction face = Direction.byName(hit.face());
        mc().gameMode.startDestroyBlock(new BlockPos(hit.x(), hit.y(), hit.z()), face == null ? Direction.UP : face);
        p.swing(InteractionHand.MAIN_HAND);
        return true;
    }

    @Override
    public boolean place(BlockHit hit) {
        LocalPlayer p = mc().player;
        if (!creative() || p == null) {
            return false;
        }
        Direction face = Direction.byName(hit.face());
        BlockHitResult b = new BlockHitResult(new Vec3(hit.hitX(), hit.hitY(), hit.hitZ()), face == null ? Direction.UP : face,
                new BlockPos(hit.x(), hit.y(), hit.z()), false);
        internal = true;
        try {
            InteractionResult r = mc().gameMode.useItemOn(p, InteractionHand.MAIN_HAND, b);
            if (r.consumesAction()) {
                p.swing(InteractionHand.MAIN_HAND);
                return true;
            }
            return false;
        } finally {
            internal = false;
        }
    }

    // --- lists and colours (API 1.11)

    /** Average texture colour per block id, computed once (the textures stay until a resource reload). */
    private static final Map<String, Integer> COLORS = new ConcurrentHashMap<>();
    private static volatile List<String> blockIds;

    @Override
    public List<String> blockIds() {
        List<String> ids = blockIds;
        if (ids == null) {
            List<String> out = new ArrayList<>();
            for (Block block : BuiltInRegistries.BLOCK) {
                if (block.asItem() != net.minecraft.world.item.Items.AIR) {
                    out.add(BuiltInRegistries.BLOCK.getKey(block).toString());
                }
            }
            out.sort(null);
            ids = List.copyOf(out);
            blockIds = ids;
        }
        return ids;
    }

    @Override
    public int averageColor(String blockId) {
        return COLORS.computeIfAbsent(blockId, McBuilding::computeColor);
    }

    /** Forgets the colours (resource packs changed). */
    public static void resourcesReloaded() {
        COLORS.clear();
    }

    private static int computeColor(String id) {
        Identifier rid = Identifier.tryParse(id);
        if (rid == null || !BuiltInRegistries.BLOCK.containsKey(rid)) {
            return 0;
        }
        try {
            BlockState state = BuiltInRegistries.BLOCK.getValue(rid).defaultBlockState();
            var sprite = mc().getModelManager().getBlockStateModelSet().getParticleMaterial(state).sprite();
            var contents = sprite.contents();
            var image = ((dev.lumora.client.mixin.SpriteContentsAccessor) contents).lumora$image();
            long r = 0;
            long g = 0;
            long b = 0;
            int opaque = 0;
            int total = 0;
            for (int y = 0; y < contents.height(); y++) {
                for (int x = 0; x < contents.width(); x++) {
                    int argb = image.getPixel(x, y);
                    total++;
                    if ((argb >>> 24) < 128) {
                        continue;
                    }
                    opaque++;
                    r += (argb >> 16) & 0xFF;
                    g += (argb >> 8) & 0xFF;
                    b += argb & 0xFF;
                }
            }
            if (opaque == 0) {
                return 0;
            }
            int alpha = Math.max(1, opaque * 255 / total);
            return alpha << 24 | (int) (r / opaque) << 16 | (int) (g / opaque) << 8 | (int) (b / opaque);
        } catch (RuntimeException e) {
            return 0;
        }
    }

    @Override
    public List<ItemSpec> creativeItems(String search, boolean blocksOnly) {
        String s = search.trim().toLowerCase(java.util.Locale.ROOT);
        var features = mc().level == null ? null : mc().level.enabledFeatures();
        List<ItemSpec> out = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == net.minecraft.world.item.Items.AIR || (blocksOnly && !(item instanceof BlockItem))
                    || (features != null && !item.isEnabled(features))) {
                continue;
            }
            ItemSpec spec = spec(new ItemStack(item));
            if (s.isEmpty() || spec.id().contains(s) || spec.name().toLowerCase(java.util.Locale.ROOT).contains(s)) {
                out.add(spec);
            }
        }
        return out;
    }

    // --- hooks (called by the mixins)

    /** Asked at the start of {@code MultiPlayerGameMode#useItemOn}: what the addons want with this click. */
    public static UseHandler.Decision decide(LocalPlayer player, InteractionHand hand, BlockHitResult b) {
        BuildingApi api = api();
        if (internal || api == null || !api.hasUseHandlers() || hand != InteractionHand.MAIN_HAND || mc().level == null) {
            return UseHandler.Decision.NORMAL;
        }
        return api.decideUse(hit(mc().level, b), spec(player.getMainHandItem()));
    }

    /** The click counts as sneaking: the server learns of the sneak input before the click, Minecraft locally too. */
    public static void beginSneakClick(LocalPlayer player) {
        Input last = player.getLastSentInput();
        player.connection.send(new ServerboundPlayerInputPacket(new Input(last.forward(), last.backward(), last.left(),
                last.right(), last.jump(), true, last.sprint())));
        forceSecondary = true;
    }

    /** Ends a sneak click: the server gets the real input back. */
    public static void endSneakClick(LocalPlayer player) {
        if (forceSecondary) {
            forceSecondary = false;
            player.connection.send(new ServerboundPlayerInputPacket(player.getLastSentInput()));
        }
    }

    public static boolean forceSecondary() {
        return forceSecondary;
    }

    /** Asked at the start of pick-block: true if an addon handled it. */
    public static boolean pick(BlockHitResult b) {
        BuildingApi api = api();
        return api != null && api.hasPickHandlers() && mc().level != null && api.handlePick(hit(mc().level, b));
    }

    /** Right-click delay after a click: an addon may shorten it in creative mode, -1 = leave it. */
    public static int useDelay(int current) {
        BuildingApi api = api();
        int d = api == null ? -1 : api.useDelay();
        return d >= 0 && d < current && INSTANCE.creative() ? d : current;
    }

    /** Break delay after breaking: an addon may shorten it in creative mode. */
    public static int breakDelay(int current) {
        BuildingApi api = api();
        int d = api == null ? -1 : api.breakDelay();
        return d >= 0 && d < current && INSTANCE.creative() ? d : current;
    }
}
