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
            out.add(new Effect(I18n.translate(effect.getTranslationKey()), e.getAmplifier(), e.getDuration(), !effect.isNegative(),
                    e.isAmbient(), effect.hasIcon() ? EffectIcon.legacy(effect.getIconLevel()) : null));
        }
        return out;
    }

    @Override
    public EffectIcon effectIcon(String id) {
        // icon numbers in inventory.png, only for the menu sample
        return switch (id) {
            case "minecraft:speed" -> EffectIcon.legacy(0);
            case "minecraft:fire_resistance" -> EffectIcon.legacy(15);
            default -> null;
        };
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
    public HeldItem heldItem() {
        ClientPlayerEntity p = mc().player;
        net.minecraft.item.ItemStack s = p == null ? null : p.inventory.getMainHandStack();
        if (s == null || s.isEmpty()) {
            return null;
        }
        List<Enchant> en = new ArrayList<>();
        for (java.util.Map.Entry<net.minecraft.enchantment.Enchantment, Integer> e : net.minecraft.enchantment.EnchantmentHelper.get(s).entrySet()) {
            if (e.getKey() != null && en.size() < 16) {
                en.add(new Enchant(e.getKey().getTranslatedName(e.getValue()), e.getKey().isCursed()));
            }
        }
        return new HeldItem(heldItemId(), s.getCustomName(), s.getRarity().ordinal(), s.hasCustomName(), List.copyOf(en),
                p.inventory.selectedSlot);
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

    @Override
    public void flyMomentum(float keep) {
        ClientPlayerEntity p = mc().player;
        if (p == null || !p.abilities.flying) {
            return;
        }
        var o = mc().options;
        if (o.forwardKey.isPressed() || o.backKey.isPressed() || o.leftKey.isPressed() || o.rightKey.isPressed()) {
            return;
        }
        p.velocityX *= keep;
        p.velocityZ *= keep;
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
        if ("mention".equals(kind)) {
            mc().getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.method_14699(net.minecraft.sound.Sounds.BLOCK_NOTE_PLING, 1.5f, volume));
            return;
        }
        MinecraftClient.getInstance().getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.method_14699(
                "ready".equals(kind) ? net.minecraft.sound.Sounds.ENTITY_EXPERIENCE_ORB_PICKUP : net.minecraft.sound.Sounds.ENTITY_ARROW_HIT_PLAYER,
                "ready".equals(kind) ? 1.6f : 1.2f, volume));
    }

    @Override
    public Status status() {
        ClientPlayerEntity p = mc().player;
        if (p == null || mc().interactionManager == null || mc().world == null) {
            return Status.NONE;
        }
        String hearts = p.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.POISON) ? "poisoned" : p.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.WITHER) ? "withered" : "normal";
        return new Status(true, mc().interactionManager.hasStatusBars(), p.isSpectator(), p.getHealth(), p.getMaxHealth(),
                p.getAbsorption(), p.getArmorProtectionValue(), p.getHungerManager().getFoodLevel(), p.getAir(), 300,
                p.isSubmergedIn(net.minecraft.block.material.Material.WATER), p.experienceLevel, p.experienceProgress,
                mc().world.getLevelProperties().isHardcore(), hearts, p.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.HUNGER),
                p.inventory.selectedSlot);
    }

    /** Hotbar slot 0..8 or the offhand (9, none before 1.9); null or empty if nothing is there. */
    private static ItemStack slotStack(ClientPlayerEntity p, int slot) {
        return slot == 9 ? p.getOffHandStack() : p.inventory.getInvStack(slot >= 10 ? slot - 1 : slot);
    }

    @Override
    public String slotItem(int slot) {
        ClientPlayerEntity p = mc().player;
        if (p == null || slot < 0 || slot > 9) {
            return "";
        }
        ItemStack s = slotStack(p, slot);
        return s == null || s.isEmpty() ? "" : id(s);
    }

    @Override
    public void drawSlot(dev.lumora.api.render.HudGraphics g, int slot, int x, int y) {
        ClientPlayerEntity p = mc().player;
        if (p == null || slot < 0 || slot > 36) {
            return;
        }
        ItemStack s = slotStack(p, slot);
        if (s == null || s.isEmpty()) {
            return;
        }
        dev.lumora.client.compat.GuiGraphics n = g.unwrap(dev.lumora.client.compat.GuiGraphics.class).orElse(null);
        if (n != null) {
            n.renderItem(s, x, y);
            n.renderItemDecorations(s, x, y);
        }
    }

    // --- armor HUD (module "Armor HUD")

    private static final net.minecraft.entity.EquipmentSlot[] SLOTS = {
            net.minecraft.entity.EquipmentSlot.HEAD,
            net.minecraft.entity.EquipmentSlot.CHEST,
            net.minecraft.entity.EquipmentSlot.LEGS,
            net.minecraft.entity.EquipmentSlot.FEET,
            net.minecraft.entity.EquipmentSlot.MAINHAND};

    /** 0 helmet, 1 chestplate, 2 leggings, 3 boots, 4 main hand. */
    private static ItemStack equipmentStack(ClientPlayerEntity p, int piece) {
        return p.getStack(SLOTS[piece]);
    }

    private static ItemStack sampleStack(int piece) {
        switch (piece) {
            case 0: return new ItemStack(net.minecraft.item.Items.DIAMOND_HELMET);
            case 1: return new ItemStack(net.minecraft.item.Items.DIAMOND_CHESTPLATE);
            case 2: return new ItemStack(net.minecraft.item.Items.DIAMOND_LEGGINGS);
            case 3: return new ItemStack(net.minecraft.item.Items.DIAMOND_BOOTS);
            default: return new ItemStack(net.minecraft.item.Items.DIAMOND_SWORD);
        }
    }

    @Override
    public Equipment equipment(int piece) {
        ClientPlayerEntity p = mc().player;
        if (p == null || piece < 0 || piece > 4) {
            return Equipment.NONE;
        }
        ItemStack s = equipmentStack(p, piece);
        if (!(s != null && !s.isEmpty())) {
            return Equipment.NONE;
        }
        boolean damageable = s.isDamageable() && s.getMaxDamage() > 0;
        return new Equipment(true, damageable ? s.getMaxDamage() - s.getDamage() : 0, damageable ? s.getMaxDamage() : 0, s.getCount());
    }

    @Override
    public void drawEquipment(dev.lumora.api.render.HudGraphics g, int piece, boolean sample, int x, int y) {
        if (piece < 0 || piece > 4) {
            return;
        }
        ClientPlayerEntity p = mc().player;
        ItemStack s = sample || p == null ? sampleStack(piece) : equipmentStack(p, piece);
        if (!(s != null && !s.isEmpty())) {
            return;
        }
        dev.lumora.client.compat.GuiGraphics n = g.unwrap(dev.lumora.client.compat.GuiGraphics.class).orElse(null);
        if (n != null) {
            n.renderItem(s, x, y);
        }
    }

    // --- chat (module "Chat")

    private static net.minecraft.client.gui.hud.ChatHud chat() {
        return mc().inGameHud == null ? null : mc().inGameHud.getChatHud();
    }

    @Override
    public dev.lumora.core.chat.windows.ChatText chatText() {
        return McChatText.INSTANCE;
    }

    @Override
    public ChatView chatView() {
        net.minecraft.client.gui.hud.ChatHud chat = chat();
        if (chat == null) {
            return null;
        }
        dev.lumora.client.mixin.ChatHudAccessor a = (dev.lumora.client.mixin.ChatHudAccessor) chat;
        double scale = chat.getChatScale();
        int width = chat.getWidth();
        net.minecraft.client.util.Window w = new net.minecraft.client.util.Window(mc());
        int gw = w.getWidth();
        int gh = w.getHeight();
        int left = (int) Math.floor(2 - 2 * scale);
        int right = (int) Math.ceil(2 + (Math.ceil(width / scale) + 4) * scale);
        return new ChatView(left, right, gh - 40, 9 * scale, chat.getVisibleLineCount(), a.lumora$scroll(),
                a.lumora$visible().size(), width, chat.getHeight(), scale, gw, gh);
    }

    @Override
    public ChatMessage chatMessage(int line) {
        net.minecraft.client.gui.hud.ChatHud chat = chat();
        if (chat == null) {
            return null;
        }
        dev.lumora.client.mixin.ChatHudAccessor a = (dev.lumora.client.mixin.ChatHudAccessor) chat;
        List<net.minecraft.client.gui.hud.ChatHudLine> lines = a.lumora$visible();
        if (line < 0 || line >= lines.size()) {
            return null;
        }
        int time = lines.get(line).getCreationTick();
        int lo = line;
        while (lo > 0 && lines.get(lo - 1).getCreationTick() == time) {
            lo--;
        }
        int hi = line;
        while (hi + 1 < lines.size() && lines.get(hi + 1).getCreationTick() == time) {
            hi++;
        }
        // messages of the same tick: the one that contains the line under the mouse
        String part = lines.get(line).getText().asUnformattedString().replaceAll("\\s+", "");
        net.minecraft.text.Text found = null;
        for (net.minecraft.client.gui.hud.ChatHudLine m : a.lumora$all()) {
            if (m.getCreationTick() == time) {
                if (found == null) {
                    found = m.getText();
                }
                if (m.getText().asUnformattedString().replaceAll("\\s+", "").contains(part)) {
                    found = m.getText();
                    break;
                }
            }
        }
        if (found == null) {
            return null;
        }
        List<String> insertions = new ArrayList<>();
        List<String> commands = new ArrayList<>();
        List<String> links = new ArrayList<>();
        for (net.minecraft.text.Text part2 : found) {
            net.minecraft.text.Style style = part2.getStyle();
            if (style.getInsertion() != null) {
                insertions.add(style.getInsertion());
            }
            net.minecraft.text.ClickEvent c = style.getClickEvent();
            if (c != null && c.getAction() == net.minecraft.text.ClickEvent.Action.OPEN_URL) {
                links.add(c.getValue());
            } else if (c != null && (c.getAction() == net.minecraft.text.ClickEvent.Action.SUGGEST_COMMAND
                    || c.getAction() == net.minecraft.text.ClickEvent.Action.RUN_COMMAND)) {
                commands.add(c.getValue());
            }
        }
        return new ChatMessage(found.asUnformattedString(), insertions, commands, links, lo, hi);
    }

    @Override
    public void refreshChat() {
        net.minecraft.client.gui.hud.ChatHud chat = chat();
        if (chat != null) {
            chat.reset();
        }
    }

    @Override
    public void setChatInput(String text) {
        if (mc().currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen s) {
            ((dev.lumora.client.mixin.ChatScreenAccessor) s).lumora$input().setText(text);
        }
    }

    // --- chat input for Lumora API 1.9 (buttons and panels at the chat input, emoji suggestions)

    @Override
    public ChatInput chatInput() {
        if (mc().currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen s) {
            var in = ((dev.lumora.client.mixin.ChatScreenAccessor) s).lumora$input();
            String t = in.getText();
            return new ChatInput(t, Math.max(0, Math.min(t.length(), in.getCursor())));
        }
        return null;
    }

    @Override
    public void setChatInput(String text, int cursor) {
        if (mc().currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen s) {
            var in = ((dev.lumora.client.mixin.ChatScreenAccessor) s).lumora$input();
            in.setText(text);
            in.setCursor(Math.max(0, Math.min(in.getText().length(), cursor)));
        }
    }

    @Override
    public void setChatInputWidth(int width) {
        if (mc().currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen s) {
            var in = ((dev.lumora.client.mixin.ChatScreenAccessor) s).lumora$input();
            var w = (dev.lumora.client.mixin.TextFieldWidgetAccessor) in;
            if (w.lumora$width() != width) {
                w.lumora$setWidth(width);
                in.setCursor(in.getCursor());
            }
        }
    }

    @Override
    public dev.lumora.core.extras.light.LightProbe lightProbe() {
        return dev.lumora.client.render.LightOverlayProbe.INSTANCE;
    }

    @Override
    public int[] guiSize() {
        var screen = mc().currentScreen;
        return screen == null ? null : new int[]{screen.width, screen.height};
    }

    @Override
    public boolean mouseLeftDown() {
        return org.lwjgl.input.Mouse.isButtonDown(0);
    }

    @Override
    public boolean mouseMiddleDown() {
        return org.lwjgl.input.Mouse.isButtonDown(2);
    }

    @Override
    public void openChat(String text) {
        mc().setScreen(new net.minecraft.client.gui.screen.ChatScreen(text));
    }

    @Override
    public boolean sendCommand(String command) {
        ClientPlayerEntity p = mc().player;
        if (p == null) {
            return false;
        }
        p.sendChatMessage("/" + command);
        return true;
    }

    @Override
    public int latency(java.util.UUID player) {
        net.minecraft.client.network.ClientPlayNetworkHandler c = mc().getNetworkHandler();
        net.minecraft.client.network.PlayerListEntry e = c == null ? null : c.getPlayerListEntry(player);
        return e == null ? -1 : e.getLatency();
    }
    // --- camera perspective and player rotation (module "Freelook")

    @Override
    public int perspective() {
        return mc().options.perspective;
    }

    @Override
    public void setPerspective(int perspective) {
        if (perspective >= 0 && perspective <= 2) {
            mc().options.perspective = perspective;
        }
    }

    @Override
    public float[] playerRotation() {
        net.minecraft.entity.Entity p = mc().player;
        return p == null ? null : new float[]{p.yaw, p.pitch};
    }

    // --- module "Cosmetics & Emotes"

    /** The own player as in the inventory (SurvivalInventoryScreen.renderEntity), but turned freely. */
    @Override
    public boolean drawPlayerPreview(dev.lumora.api.render.HudGraphics g, int x, int y, int width, int height,
                                     float yaw, float pitch, float zoom) {
        net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
        net.minecraft.entity.player.ClientPlayerEntity p = mc.player;
        if (p == null || width < 8 || height < 8) {
            return false;
        }
        float size = height * 0.78f / 1.8f * zoom;
        float bodyYaw = p.bodyYaw;
        float playerYaw = p.yaw;
        float playerPitch = p.pitch;
        float prevHead = p.prevHeadYaw;
        float head = p.headYaw;
        g.enableScissor(x, y, x + width, y + height);
        com.mojang.blaze3d.platform.GlStateManager.enableColorMaterial();
        com.mojang.blaze3d.platform.GlStateManager.pushMatrix();
        dev.lumora.client.render.CosmeticsRender.preview = true;
        try {
            com.mojang.blaze3d.platform.GlStateManager.translate(x + width / 2.0F, y + height * 0.89F, 50.0F);
            com.mojang.blaze3d.platform.GlStateManager.scale(-size, size, size);
            com.mojang.blaze3d.platform.GlStateManager.rotate(180.0F, 0.0F, 0.0F, 1.0F);
            com.mojang.blaze3d.platform.GlStateManager.rotate(135.0F, 0.0F, 1.0F, 0.0F);
            net.minecraft.client.render.DiffuseLighting.enableNormally();
            com.mojang.blaze3d.platform.GlStateManager.rotate(-135.0F, 0.0F, 1.0F, 0.0F);
            com.mojang.blaze3d.platform.GlStateManager.rotate(-pitch, 1.0F, 0.0F, 0.0F);
            p.bodyYaw = yaw;
            p.yaw = yaw;
            p.pitch = 0;
            p.headYaw = yaw;
            p.prevHeadYaw = yaw;
            net.minecraft.client.render.entity.EntityRenderDispatcher dispatcher = mc.getEntityRenderManager();
            dispatcher.setYaw(180.0F);
            dispatcher.setRenderShadows(false);
            dispatcher.method_12446(p, 0.0, 0.0, 0.0, 0.0F, 1.0F, false);
            dispatcher.setRenderShadows(true);
        } finally {
            dev.lumora.client.render.CosmeticsRender.preview = false;
            p.bodyYaw = bodyYaw;
            p.yaw = playerYaw;
            p.pitch = playerPitch;
            p.prevHeadYaw = prevHead;
            p.headYaw = head;
            com.mojang.blaze3d.platform.GlStateManager.popMatrix();
            net.minecraft.client.render.DiffuseLighting.disable();
            com.mojang.blaze3d.platform.GlStateManager.disableRescaleNormal();
            com.mojang.blaze3d.platform.GlStateManager.activeTexture(com.mojang.blaze3d.platform.GLX.lightmapTextureUnit);
            com.mojang.blaze3d.platform.GlStateManager.disableTexture();
            com.mojang.blaze3d.platform.GlStateManager.activeTexture(com.mojang.blaze3d.platform.GLX.textureUnit);
            g.disableScissor();
        }
        return true;
    }
}
