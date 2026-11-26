package dev.lumora.core.extras;

import dev.lumora.core.LumoraCore;
import dev.lumora.core.Platform;
import dev.lumora.core.addon.AddonCandidate;

import java.util.List;

/**
 * The built-in Lumora modules of the core (one addon each, so every module has its own on/off switch and settings in
 * the hub under Addons). Names and descriptions shown in the hub come from the language files
 * ({@code lumora.module.<id>}); the ones here are only the fallback. Crosshair, toggle sprint and fullbright change
 * how the game feels and start switched off.
 */
public final class Extras {
    public static final List<String> OFF_BY_DEFAULT = List.of("lumora-crosshair", "lumora-togglesprint", "lumora-fullbright",
            "lumora-attackindicator", "lumora-statusbars", "lumora-widgetdesign", "lumora-armor",
            dev.lumora.core.camera.FreelookModule.ID, dev.lumora.core.extras.light.LightOverlayModule.ID,
            OffHandModule.ID, HiddenArmorModule.ID);

    private Extras() {
    }

    public static List<AddonCandidate> candidates(Platform platform) {
        return candidates(platform, () -> null);
    }

    public static List<AddonCandidate> candidates(Platform platform, java.util.function.Supplier<dev.lumora.core.social.LumoraSocial> social) {
        return candidates(platform, social, () -> null);
    }

    /**
     * The modules; {@code social} gives Lumora Social (cosmetics of other players) once it runs, {@code nameTags} the
     * core's name tag registry (the Lumora Badge before the name).
     */
    public static List<AddonCandidate> candidates(Platform platform, java.util.function.Supplier<dev.lumora.core.social.LumoraSocial> social,
                                                  java.util.function.Supplier<dev.lumora.core.render.NameTagRegistry> nameTags) {
        return List.of(
                AddonCandidate.of(LumoraCore.builtInDescriptor("lumora-info", "Info Widgets",
                        "Potion effects, item counter, compass, memory, day, resource packs, combo", InfoWidgetsModule.class),
                        () -> new InfoWidgetsModule(platform)),
                AddonCandidate.of(LumoraCore.builtInDescriptor("lumora-crosshair", "Crosshair",
                        "Custom crosshair: shape, size, gap, thickness, colour, outline", CrosshairModule.class),
                        CrosshairModule::new),
                AddonCandidate.of(LumoraCore.builtInDescriptor("lumora-attackindicator", "Attack Indicator",
                        "Charge of hits, bow, crossbow and trident, item cooldowns, eating, mining: bar, ring, dot, level bar or boss bar",
                        AttackIndicatorModule.class),
                        () -> new AttackIndicatorModule(platform::extras)),
                AddonCandidate.of(LumoraCore.builtInDescriptor("lumora-blockoverlay", "Block Overlay",
                        "Outline of the targeted block: colour, rainbow, thickness, fade in, block info", BlockOverlayModule.class),
                        () -> new BlockOverlayModule(platform)),
                AddonCandidate.of(LumoraCore.builtInDescriptor("lumora-widgetdesign", "Widget Design",
                        "One look for all widgets: background, border, corners, accent, colours, text shadow, format and size",
                        WidgetDesignModule.class),
                        WidgetDesignModule::new),
                AddonCandidate.of(LumoraCore.builtInDescriptor("lumora-buttons", "Button Design",
                        "Buttons in the style Blackout, Hyperium, Liquid, Lunar, PvPClient or Rounded", ButtonStyleModule.class),
                        ButtonStyleModule::new),
                AddonCandidate.of(LumoraCore.builtInDescriptor("lumora-browser", "Server & World List",
                        "Own multiplayer and singleplayer menus: search, filter, ping, server and world cards",
                        dev.lumora.core.browser.BrowserModule.class),
                        () -> new dev.lumora.core.browser.BrowserModule(platform)),
                AddonCandidate.of(LumoraCore.builtInDescriptor("lumora-connection", "Connection Screen",
                        "Connecting screen with phases and ping, disconnect reason with tips, automatic reconnect",
                        dev.lumora.core.connection.ConnectionModule.class),
                        () -> new dev.lumora.core.connection.ConnectionModule(platform)),
                AddonCandidate.of(LumoraCore.builtInDescriptor("lumora-togglesprint", "Toggle Sprint",
                        "Toggle sprinting and sneaking with a key press, fly speed and momentum", ToggleSprintModule.class),
                        () -> new ToggleSprintModule(platform)),
                AddonCandidate.of(LumoraCore.builtInDescriptor("lumora-fullbright", "Fullbright",
                        "Full brightness in caves and at night (only visible to you)", FullbrightModule.class),
                        () -> new FullbrightModule(platform)),
                AddonCandidate.of(LumoraCore.builtInDescriptor(dev.lumora.core.camera.FreelookModule.ID, "Freelook",
                        "Look all around the player in third person with a held key, without changing where you walk",
                        dev.lumora.core.camera.FreelookModule.class),
                        () -> new dev.lumora.core.camera.FreelookModule(platform::extras)),
                AddonCandidate.of(LumoraCore.builtInDescriptor(dev.lumora.core.extras.light.LightOverlayModule.ID, "Light Block Overlay",
                        "Marks blocks where hostile mobs can spawn: red now, yellow at night; cross, number, tile or marker",
                        dev.lumora.core.extras.light.LightOverlayModule.class),
                        () -> new dev.lumora.core.extras.light.LightOverlayModule(() -> platform.extras().lightProbe(),
                                platform.minecraftVersion())),
                AddonCandidate.of(LumoraCore.builtInDescriptor("lumora-statusbars", "Hotbar & Status Bars",
                        "Hotbar, hearts, armor, hunger, air and experience as separate widgets: move freely, vanilla, compact, bar or number",
                        StatusBarsModule.class),
                        () -> new StatusBarsModule(platform)),
                AddonCandidate.of(LumoraCore.builtInDescriptor("lumora-armor", "Armor HUD",
                        "Helmet, chestplate, leggings, boots and held item with durability, every piece moves on its own",
                        ArmorHudModule.class),
                        () -> new ArmorHudModule(platform)),
                // elytras exist since 1.9: on older versions the hub shows the module locked with the reason
                AddonCandidate.of(LumoraCore.builtInDescriptor("lumora-elytratrails", "Elytra Trails",
                        "Vapour trails or vortices behind the wing tips of gliding players: colour, length, width, from which speed",
                        ElytraTrailsModule.class, dev.lumora.core.addon.VersionSupport.atLeast(ElytraTrailsModule.MIN_MINECRAFT,
                                platform.minecraftVersion())),
                        ElytraTrailsModule::new),
                // the off hand exists since 1.9: on older versions the hub shows the module locked with the reason
                AddonCandidate.of(LumoraCore.builtInDescriptor(OffHandModule.ID, "Off Hand",
                        "Show both hands in first person, hide the off hand or hide a shield or a totem",
                        OffHandModule.class, dev.lumora.core.addon.VersionSupport.atLeast(OffHandModule.MIN_MINECRAFT,
                                platform.minecraftVersion())),
                        OffHandModule::new),
                // the animations differ from 1.8 only since 1.9: on older versions the hub shows the module locked with the reason
                AddonCandidate.of(LumoraCore.builtInDescriptor(AnimationsModule.ID, "Animations",
                        "Classic 1.7 animations, each part on its own: sneaking, hearts, items, bow, food, inventory",
                        AnimationsModule.class, dev.lumora.core.addon.VersionSupport.atLeast(AnimationsModule.MIN_MINECRAFT,
                                platform.minecraftVersion())),
                        () -> new AnimationsModule(platform.animations())),
                AddonCandidate.of(LumoraCore.builtInDescriptor(dev.lumora.core.cosmetics.CosmeticsModule.ID, "Cosmetics & Emotes",
                        "Wings, capes, hats, accessories, effects and companions on your player, emotes with menu and wheel (only you see them)",
                        dev.lumora.core.cosmetics.CosmeticsModule.class),
                        () -> new dev.lumora.core.cosmetics.CosmeticsModule(platform, social)),
                AddonCandidate.of(LumoraCore.builtInDescriptor(dev.lumora.core.social.LumoraBadgeModule.ID, "Lumora Badge",
                        "A Lumora star in front of the names of players who play with Lumora, above their heads and in the tab list",
                        dev.lumora.core.social.LumoraBadgeModule.class),
                        () -> new dev.lumora.core.social.LumoraBadgeModule(() -> {
                            dev.lumora.core.social.LumoraSocial ls = social.get();
                            return ls == null ? null : ls.client();
                        }, () -> platform == null ? null : platform.world(), nameTags)),
                AddonCandidate.of(LumoraCore.builtInDescriptor(HiddenArmorModule.ID, "Hidden Armor",
                        "Make each piece of your armor invisible or see-through; it shows again for a few seconds when you are hit",
                        HiddenArmorModule.class),
                        HiddenArmorModule::new),
                AddonCandidate.of(LumoraCore.builtInDescriptor(dev.lumora.core.perf.PerformanceModule.ID, "Performance",
                        "Entity culling, faster name tags, menus and startup; every optimization can be switched on its own",
                        dev.lumora.core.perf.PerformanceModule.class),
                        () -> new dev.lumora.core.perf.PerformanceModule(platform)),
                AddonCandidate.of(LumoraCore.builtInDescriptor("lumora-chat", "Chat",
                        "Timestamps, copy messages, drag size, background, longer history, sound on mention", ChatModule.class),
                        () -> new ChatModule(platform)),
                AddonCandidate.of(LumoraCore.builtInDescriptor(dev.lumora.core.chat.emoji.EmojiChatModule.ID, "Emoji Chat",
                        "Emojis in chat messages for :name:, an emoji picker at the chat input and suggestions while typing",
                        dev.lumora.core.chat.emoji.EmojiChatModule.class),
                        dev.lumora.core.chat.emoji.EmojiChatModule::new),
                AddonCandidate.of(LumoraCore.builtInDescriptor("lumora-tab", "Tab List & Scoreboard",
                        "Ping as a number, move, scale, hide the numbers of or hide the scoreboard", TabModule.class),
                        TabModule::new));
    }
}
