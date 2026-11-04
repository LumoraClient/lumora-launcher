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

    @Override
    public Progress progress(float partialTick) {
        Minecraft mc = mc();
        LocalPlayer p = mc.player;
        if (p == null) {
            return Progress.NONE;
        }
        float attack = p.isSpectator() ? -1 : p.getAttackStrengthScale(partialTick);
        boolean target = mc.crosshairPickEntity instanceof LivingEntity le && le.isAlive();
        List<Cooldown> cooldowns = new ArrayList<>();
        java.util.Set<net.minecraft.world.item.Item> seen = new java.util.HashSet<>();
        for (int i = 0; i < 10; i++) {
            net.minecraft.world.item.ItemStack s = i < 9 ? p.getInventory().getItem(i) : p.getOffhandItem();
            if (s.isEmpty() || !seen.add(s.getItem())) {
                continue;
            }
            float r = p.getCooldowns().getCooldownPercent(s, partialTick);
            if (r > 0) {
                cooldowns.add(new Cooldown(BuiltInRegistries.ITEM.getKey(s.getItem()).toString(), s.getHoverName().getString(), r));
            }
        }
        String use = "";
        float useProgress = 0;
        if (p.isUsingItem()) {
            net.minecraft.world.item.ItemStack s = p.getUseItem();
            int ticks = p.getTicksUsingItem();
            String anim = s.getUseAnimation().name().toLowerCase(java.util.Locale.ROOT);
            if (anim.equals("bow")) {
                use = "bow";
                useProgress = net.minecraft.world.item.BowItem.getPowerForTime(ticks);
            } else if (anim.equals("crossbow")) {
                use = "crossbow";
                useProgress = ticks / (float) Math.max(1, net.minecraft.world.item.CrossbowItem.getChargeDuration(s, p));
            } else if (anim.equals("spear")) {
                use = "spear";
                useProgress = ticks / 10f;
            } else if (anim.equals("eat") || anim.equals("drink")) {
                use = anim;
                useProgress = ticks / (float) Math.max(1, s.getUseDuration(p));
            }
        }
        float breaking = mc.gameMode != null && mc.gameMode.isDestroying()
                ? ((dev.lumora.client.mixin.BreakingProgressAccessor) mc.gameMode).lumora$breakingProgress() : -1;
        float sleep = p.isSleeping() ? Math.min(1f, p.getSleepTimer() / 100f) : -1;
        return new Progress(attack, target, mc.options.getCameraType().isFirstPerson(), cooldowns, use, useProgress,
                breaking, sleep, mc.options.attackIndicator().get().ordinal());
    }

    @Override
    public void playSound(String kind, float volume) {
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                "ready".equals(kind) ? net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP : net.minecraft.sounds.SoundEvents.ARROW_HIT_PLAYER,
                "ready".equals(kind) ? 1.6f : 1.2f, volume));
    }
}
