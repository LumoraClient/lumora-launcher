package dev.lumora.client.api;

import dev.lumora.client.compat.Keys;
import dev.lumora.core.extras.GameExtras;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.resource.ResourcePackLoader;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.ClientPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** {@link GameExtras} for Minecraft 1.12.2 (Legacy Fabric, Yarn names, LWJGL 2 key codes). */
final class McExtras implements GameExtras {
    private static MinecraftClient mc() {
        return MinecraftClient.getInstance();
    }

    @Override
    public List<Effect> effects() {
        ClientPlayerEntity p = mc().player;
        List<Effect> out = new ArrayList<>();
        if (p == null) {
            return out;
        }
        for (StatusEffectInstance e : p.getStatusEffectInstances()) {
            StatusEffect effect = e.getStatusEffect();
            if (effect == null) {
                continue;
            }
            out.add(new Effect(I18n.translate(effect.getTranslationKey()), e.getAmplifier(), e.getDuration(), !effect.isNegative()));
        }
        return out;
    }

    private static String id(ItemStack s) {
        Object key = Item.REGISTRY.getIdentifier(s.getItem());
        return key == null ? "" : key.toString();
    }

    @Override
    public int count(String itemId) {
        ClientPlayerEntity p = mc().player;
        if (p == null || itemId.isEmpty()) {
            return 0;
        }
        int n = 0;
        for (int i = 0; i < p.inventory.getInvSize(); i++) {
            ItemStack s = p.inventory.getInvStack(i);
            if (!s.isEmpty() && id(s).equals(itemId)) {
                n += s.getCount();
            }
        }
        return n;
    }

    @Override
    public String heldItemId() {
        ClientPlayerEntity p = mc().player;
        ItemStack s = p == null ? null : p.inventory.getMainHandStack();
        return s == null || s.isEmpty() ? "" : id(s);
    }

    @Override
    public String heldItemName() {
        ClientPlayerEntity p = mc().player;
        ItemStack s = p == null ? null : p.inventory.getMainHandStack();
        return s == null || s.isEmpty() ? "" : s.getCustomName();
    }

    @Override
    public long dayTime() {
        return mc().world == null ? -1 : mc().world.getTimeOfDay();
    }

    @Override
    public List<String> resourcePacks() {
        List<String> out = new ArrayList<>();
        for (ResourcePackLoader.Entry e : mc().getResourcePackLoader().getSelectedResourcePacks()) {
            out.add(e.getName());
        }
        return out;
    }

    @Override
    public int targetEntityId() {
        Entity e = mc().targetedEntity;
        return e == null ? -1 : e.getEntityId();
    }

    @Override
    public int hurtTime(int entityId) {
        if (mc().world == null) {
            return -1;
        }
        Entity e = mc().world.getEntityById(entityId);
        return e instanceof LivingEntity le ? le.hurtTime : -1;
    }

    @Override
    public int ownHurtTime() {
        return mc().player == null ? 0 : mc().player.hurtTime;
    }

    @Override
    public double gamma() {
        return mc().options.gamma;
    }

    @Override
    public void setGamma(double value) {
        mc().options.gamma = (float) value; // plain field: values above the slider work
    }

    /** GLFW code of a binding; mouse buttons (negative LWJGL codes) and unbound keys are -1. */
    private static int key(KeyBinding k) {
        int code = k.getCode();
        if (code <= 0) {
            return -1;
        }
        int glfw = Keys.toGlfw(code);
        return glfw <= 0 ? -1 : glfw;
    }

    @Override
    public int sprintKey() {
        return key(mc().options.sprintKey);
    }

    @Override
    public int sneakKey() {
        return key(mc().options.sneakKey);
    }

    @Override
    public void holdSprint(boolean down) {
        KeyBinding.setKeyPressed(mc().options.sprintKey.getCode(), down);
    }

    @Override
    public void holdSneak(boolean down) {
        KeyBinding.setKeyPressed(mc().options.sneakKey.getCode(), down);
    }
    private net.minecraft.util.math.BlockPos targetPos() {
        net.minecraft.util.hit.BlockHitResult r = mc().result;
        return mc().world != null && r != null && r.type == net.minecraft.util.hit.BlockHitResult.Type.BLOCK ? r.getBlockPos() : null;
    }

    @Override
    public long targetBlockPos() {
        net.minecraft.util.math.BlockPos p = targetPos();
        return p == null ? Long.MIN_VALUE : GameExtras.pack(p.getX(), p.getY(), p.getZ());
    }

    @Override
    public String targetBlockName() {
        net.minecraft.util.math.BlockPos p = targetPos();
        return p == null ? "" : mc().world.getBlockState(p).getBlock().getTranslatedName();
    }

    @Override
    public String targetBlockId() {
        net.minecraft.util.math.BlockPos p = targetPos();
        if (p == null) {
            return "";
        }
        Object id = net.minecraft.block.Block.REGISTRY.getIdentifier(mc().world.getBlockState(p).getBlock());
        return id == null ? "" : id.toString();
    }

    @Override
    public Progress progress(float partialTick) {
        MinecraftClient mc = mc();
        ClientPlayerEntity p = mc.player;
        if (p == null) {
            return Progress.NONE;
        }
        float attack = p.isSpectator() ? -1 : p.method_13275(partialTick);
        boolean target = mc.targetedEntity instanceof LivingEntity le && le.isAlive();
        List<Cooldown> cooldowns = new ArrayList<>();
        java.util.Set<Item> seen = new java.util.HashSet<>();
        for (int i = 0; i < 10; i++) {
            ItemStack s = i < 9 ? p.inventory.getInvStack(i) : p.getOffHandStack();
            if (s.isEmpty() || !seen.add(s.getItem())) {
                continue;
            }
            float r = p.getItemCooldownManager().getCooldownProgress(s.getItem(), partialTick);
            if (r > 0) {
                cooldowns.add(new Cooldown(id(s), s.getCustomName(), r));
            }
        }
        String use = "";
        float useProgress = 0;
        if (p.method_13061()) {
            ItemStack s = p.method_13064();
            int ticks = p.method_13066();
            String anim = s.getUseAction().name().toLowerCase(java.util.Locale.ROOT);
            if (anim.equals("bow")) {
                use = "bow";
                useProgress = net.minecraft.item.BowItem.method_11363(ticks);
            } else if (anim.equals("eat") || anim.equals("drink")) {
                use = anim;
                useProgress = ticks / (float) Math.max(1, s.getMaxUseTime());
            }
        }
        float breaking = mc.interactionManager != null && mc.interactionManager.isBreakingBlock()
                ? ((dev.lumora.client.mixin.BreakingProgressAccessor) mc.interactionManager).lumora$breakingProgress() : -1;
        float sleep = p.isSleeping() ? Math.min(1f, p.getSleepTimer() / 100f) : -1;
        return new Progress(attack, target, mc.options.perspective == 0, cooldowns, use, useProgress, breaking, sleep,
                mc.options.field_13290);
    }

    @Override
    public void playSound(String kind, float volume) {
        MinecraftClient.getInstance().getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.method_14699(
                "ready".equals(kind) ? net.minecraft.sound.Sounds.ENTITY_EXPERIENCE_ORB_PICKUP : net.minecraft.sound.Sounds.ENTITY_ARROW_HIT_PLAYER,
                "ready".equals(kind) ? 1.6f : 1.2f, volume));
    }
}
