package dev.lumora.client.api;

import com.mojang.blaze3d.platform.InputConstants;
import dev.lumora.client.mixin.OptionInstanceAccessor;
import dev.lumora.core.extras.GameExtras;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** {@link GameExtras} for this Minecraft version (built-in Lumora modules: potions, items, combo, fullbright, toggle sprint). */
final class McExtras implements GameExtras {
    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    @Override
    public List<Effect> effects() {
        LocalPlayer p = mc().player;
        List<Effect> out = new ArrayList<>();
        if (p == null) {
            return out;
        }
        for (MobEffectInstance e : p.getActiveEffects()) {
            out.add(new Effect(e.getEffect().value().getDisplayName().getString(), e.getAmplifier(),
                    e.isInfiniteDuration() ? -1 : e.getDuration(), e.getEffect().value().isBeneficial()));
        }
        return out;
    }

    @Override
    public int count(String itemId) {
        LocalPlayer p = mc().player;
        if (p == null || itemId.isEmpty()) {
            return 0;
        }
        int n = 0;
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && BuiltInRegistries.ITEM.getKey(s.getItem()).toString().equals(itemId)) {
                n += s.getCount();
            }
        }
        return n;
    }

    @Override
    public String heldItemId() {
        LocalPlayer p = mc().player;
        return p == null || p.getMainHandItem().isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(p.getMainHandItem().getItem()).toString();
    }

    @Override
    public String heldItemName() {
        LocalPlayer p = mc().player;
        return p == null || p.getMainHandItem().isEmpty() ? "" : p.getMainHandItem().getHoverName().getString();
    }

    @Override
    public long dayTime() {
        return mc().level == null ? -1 : mc().level.getDayTime();
    }

    @Override
    public List<String> resourcePacks() {
        List<String> out = new ArrayList<>();
        for (Pack p : mc().getResourcePackRepository().getSelectedPacks()) {
            String id = p.getId();
            // player-chosen packs: files and built-in packs like the Lumora theme (not vanilla, Fabric or mod resources)
            if (id.startsWith("file/") || id.contains(":")) {
                out.add(p.getTitle().getString());
            }
        }
        return out;
    }

    @Override
    public int targetEntityId() {
        Entity e = mc().crosshairPickEntity;
        return e == null ? -1 : e.getId();
    }

    @Override
    public int hurtTime(int entityId) {
        if (mc().level == null) {
            return -1;
        }
        Entity e = mc().level.getEntity(entityId);
        return e instanceof LivingEntity le ? le.hurtTime : -1;
    }

    @Override
    public int ownHurtTime() {
        return mc().player == null ? 0 : mc().player.hurtTime;
    }

    @Override
    public double gamma() {
        return mc().options.gamma().get();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void setGamma(double value) {
        // above the slider's range on purpose: OptionInstance#set would reject it
        ((OptionInstanceAccessor<Double>) (Object) mc().options.gamma()).lumora$setValue(value);
    }

    private static int key(KeyMapping km) {
        InputConstants.Key k = InputConstants.getKey(km.saveString());
        return k.getType() == InputConstants.Type.KEYSYM && !km.isUnbound() ? k.getValue() : -1;
    }

    @Override
    public int sprintKey() {
        return key(mc().options.keySprint);
    }

    @Override
    public int sneakKey() {
        return key(mc().options.keyShift);
    }

    @Override
    public void holdSprint(boolean down) {
        mc().options.keySprint.setDown(down);
    }

    @Override
    public void holdSneak(boolean down) {
        mc().options.keyShift.setDown(down);
    }
    private net.minecraft.core.BlockPos targetPos() {
        return mc().level != null && mc().hitResult instanceof net.minecraft.world.phys.BlockHitResult b
                && b.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK ? b.getBlockPos() : null;
    }

    @Override
    public long targetBlockPos() {
        net.minecraft.core.BlockPos p = targetPos();
        return p == null ? Long.MIN_VALUE : p.asLong();
    }

    @Override
    public String targetBlockName() {
        net.minecraft.core.BlockPos p = targetPos();
        return p == null ? "" : mc().level.getBlockState(p).getBlock().getName().getString();
    }

    @Override
    public String targetBlockId() {
        net.minecraft.core.BlockPos p = targetPos();
        return p == null ? "" : net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(mc().level.getBlockState(p).getBlock()).toString();
    }
}
