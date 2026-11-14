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

import net.minecraft.client.GuiMessage;
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
            } else if (anim.equals("trident")) {
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
        if ("mention".equals(kind)) {
            mc().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), 1.5f, volume));
            return;
        }
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                "ready".equals(kind) ? net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP : net.minecraft.sounds.SoundEvents.ARROW_HIT_PLAYER,
                "ready".equals(kind) ? 1.6f : 1.2f, volume));
    }

    @Override
    public Status status() {
        LocalPlayer p = mc().player;
        if (p == null || mc().gameMode == null) {
            return Status.NONE;
        }
        String hearts = p.hasEffect(net.minecraft.world.effect.MobEffects.POISON) ? "poisoned"
                : p.hasEffect(net.minecraft.world.effect.MobEffects.WITHER) ? "withered" : p.isFullyFrozen() ? "frozen" : "normal";
        return new Status(true, mc().gameMode.canHurtPlayer(), p.isSpectator(), p.getHealth(), p.getMaxHealth(),
                p.getAbsorptionAmount(), p.getArmorValue(), p.getFoodData().getFoodLevel(), p.getAirSupply(), p.getMaxAirSupply(),
                p.isEyeInFluid(net.minecraft.tags.FluidTags.WATER), p.experienceLevel, p.experienceProgress,
                p.level().getLevelData().isHardcore(), hearts, p.hasEffect(net.minecraft.world.effect.MobEffects.HUNGER),
                p.getInventory().getSelectedSlot());
    }

    /** Hotbar slot 0..8 or the offhand (9). */
    private static ItemStack slotStack(LocalPlayer p, int slot) {
        return slot == 9 ? p.getOffhandItem() : p.getInventory().getItem(slot);
    }

    @Override
    public String slotItem(int slot) {
        LocalPlayer p = mc().player;
        if (p == null || slot < 0 || slot > 9) {
            return "";
        }
        ItemStack s = slotStack(p, slot);
        return s.isEmpty() ? "" : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
    }

    @Override
    public void drawSlot(dev.lumora.api.render.HudGraphics g, int slot, int x, int y) {
        LocalPlayer p = mc().player;
        if (p == null || slot < 0 || slot > 9) {
            return;
        }
        ItemStack s = slotStack(p, slot);
        if (s.isEmpty()) {
            return;
        }
        net.minecraft.client.gui.GuiGraphics n = g.unwrap(net.minecraft.client.gui.GuiGraphics.class).orElse(null);
        if (n != null) {
            n.renderItem(s, x, y);
            n.renderItemDecorations(mc().font, s, x, y);
        }
    }

    // --- armor HUD (module "Armor HUD")

    private static final net.minecraft.world.entity.EquipmentSlot[] SLOTS = {
            net.minecraft.world.entity.EquipmentSlot.HEAD,
            net.minecraft.world.entity.EquipmentSlot.CHEST,
            net.minecraft.world.entity.EquipmentSlot.LEGS,
            net.minecraft.world.entity.EquipmentSlot.FEET,
            net.minecraft.world.entity.EquipmentSlot.MAINHAND};

    /** 0 helmet, 1 chestplate, 2 leggings, 3 boots, 4 main hand. */
    private static ItemStack equipmentStack(LocalPlayer p, int piece) {
        return p.getItemBySlot(SLOTS[piece]);
    }

    private static ItemStack sampleStack(int piece) {
        switch (piece) {
            case 0: return new ItemStack(net.minecraft.world.item.Items.DIAMOND_HELMET);
            case 1: return new ItemStack(net.minecraft.world.item.Items.DIAMOND_CHESTPLATE);
            case 2: return new ItemStack(net.minecraft.world.item.Items.DIAMOND_LEGGINGS);
            case 3: return new ItemStack(net.minecraft.world.item.Items.DIAMOND_BOOTS);
            default: return new ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD);
        }
    }

    @Override
    public Equipment equipment(int piece) {
        LocalPlayer p = mc().player;
        if (p == null || piece < 0 || piece > 4) {
            return Equipment.NONE;
        }
        ItemStack s = equipmentStack(p, piece);
        if (!(s != null && !s.isEmpty())) {
            return Equipment.NONE;
        }
        boolean damageable = s.isDamageableItem() && s.getMaxDamage() > 0;
        return new Equipment(true, damageable ? s.getMaxDamage() - s.getDamageValue() : 0, damageable ? s.getMaxDamage() : 0, s.getCount());
    }

    @Override
    public void drawEquipment(dev.lumora.api.render.HudGraphics g, int piece, boolean sample, int x, int y) {
        if (piece < 0 || piece > 4) {
            return;
        }
        LocalPlayer p = mc().player;
        ItemStack s = sample || p == null ? sampleStack(piece) : equipmentStack(p, piece);
        if (!(s != null && !s.isEmpty())) {
            return;
        }
        net.minecraft.client.gui.GuiGraphics n = g.unwrap(net.minecraft.client.gui.GuiGraphics.class).orElse(null);
        if (n != null) {
            n.renderItem(s, x, y);
        }
    }

    // --- chat (module "Chat")

    private static net.minecraft.client.gui.components.ChatComponent chat() {
        return mc().gui == null ? null : mc().gui.getChat();
    }

    @Override
    public ChatView chatView() {
        net.minecraft.client.gui.components.ChatComponent chat = chat();
        if (chat == null) {
            return null;
        }
        dev.lumora.client.mixin.ChatComponentAccessor a = (dev.lumora.client.mixin.ChatComponentAccessor) chat;
        double scale = a.lumora$scale();
        int width = a.lumora$width();
        int gw = mc().getWindow().getGuiScaledWidth();
        int gh = mc().getWindow().getGuiScaledHeight();
        int left = 0;
        int right = (int) Math.ceil((Math.ceil(width / scale) + 12) * scale);
        return new ChatView(left, right, gh - 40, a.lumora$lineHeight() * scale, chat.getLinesPerPage(), a.lumora$scroll(),
                a.lumora$trimmed().size(), width, a.lumora$height(), scale, gw, gh);
    }

    @Override
    public ChatMessage chatMessage(int line) {
        net.minecraft.client.gui.components.ChatComponent chat = chat();
        if (chat == null) {
            return null;
        }
        dev.lumora.client.mixin.ChatComponentAccessor a = (dev.lumora.client.mixin.ChatComponentAccessor) chat;
        List<GuiMessage.Line> lines = a.lumora$trimmed();
        if (line < 0 || line >= lines.size()) {
            return null;
        }
        int lo = line;
        while (lo > 0 && !lines.get(lo).endOfEntry()) {
            lo--;
        }
        int hi = line;
        while (hi + 1 < lines.size() && !lines.get(hi + 1).endOfEntry()) {
            hi++;
        }
        // the n-th message of that tick (lines and messages are both newest first)
        int time = lines.get(lo).addedTime();
        int n = 0;
        for (int i = 0; i < lo; i++) {
            if (lines.get(i).endOfEntry() && lines.get(i).addedTime() == time) {
                n++;
            }
        }
        GuiMessage first = null;
        GuiMessage found = null;
        for (GuiMessage m : a.lumora$all()) {
            if (m.addedTime() == time) {
                if (first == null) {
                    first = m;
                }
                if (n-- == 0) {
                    found = m;
                    break;
                }
            }
        }
        if (found == null) {
            found = first;
        }
        return found == null ? null : chatMessage(found.content(), lo, hi);
    }

    private static ChatMessage chatMessage(net.minecraft.network.chat.Component content, int lo, int hi) {
        List<String> insertions = new ArrayList<>();
        List<String> commands = new ArrayList<>();
        List<String> links = new ArrayList<>();
        content.<Object>visit((style, text) -> {
            if (style.getInsertion() != null) {
                insertions.add(style.getInsertion());
            }
            net.minecraft.network.chat.ClickEvent c = style.getClickEvent();
            if (c instanceof net.minecraft.network.chat.ClickEvent.OpenUrl u) {
                links.add(u.uri().toString());
            } else if (c instanceof net.minecraft.network.chat.ClickEvent.SuggestCommand s) {
                commands.add(s.command());
            } else if (c instanceof net.minecraft.network.chat.ClickEvent.RunCommand r) {
                commands.add(r.command());
            }
            return java.util.Optional.empty();
        }, net.minecraft.network.chat.Style.EMPTY);
        return new ChatMessage(content.getString(), insertions, commands, links, lo, hi);
    }

    @Override
    public void refreshChat() {
        net.minecraft.client.gui.components.ChatComponent chat = chat();
        if (chat != null) {
            chat.rescaleChat();
        }
    }

    @Override
    public void setChatInput(String text) {
        if (mc().screen instanceof net.minecraft.client.gui.screens.ChatScreen s) {
            ((dev.lumora.client.mixin.ChatScreenAccessor) s).lumora$input().setValue(text);
        }
    }

    @Override
    public boolean mouseLeftDown() {
        return mc().mouseHandler.isLeftPressed();
    }

    @Override
    public boolean mouseMiddleDown() {
        return mc().mouseHandler.isMiddlePressed();
    }

    @Override
    public int permissionLevel() {
        if (mc().player == null) {
            return 0;
        }
        net.minecraft.server.permissions.PermissionSet p = mc().player.permissions();
        return p.hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_OWNER) ? 4
                : p.hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_ADMIN) ? 3
                : p.hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER) ? 2
                : p.hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_MODERATOR) ? 1 : 0;
    }

    @Override
    public void openChat(String text) {
        mc().setScreen(new net.minecraft.client.gui.screens.ChatScreen(text, false));
    }

    @Override
    public boolean sendCommand(String command) {
        net.minecraft.client.multiplayer.ClientPacketListener c = mc().getConnection();
        if (c == null || mc().player == null) {
            return false;
        }
        c.sendCommand(command);
        return true;
    }

    @Override
    public int latency(java.util.UUID player) {
        net.minecraft.client.multiplayer.ClientPacketListener c = mc().getConnection();
        net.minecraft.client.multiplayer.PlayerInfo i = c == null ? null : c.getPlayerInfo(player);
        return i == null ? -1 : i.getLatency();
    }
}
