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
            "lumora-attackindicator", "lumora-statusbars", "lumora-widgetdesign", "lumora-armor");

    private Extras() {
    }

    public static List<AddonCandidate> candidates(Platform platform) {
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
                        "Toggle sprinting and sneaking with a key press", ToggleSprintModule.class),
                        () -> new ToggleSprintModule(platform)),
                AddonCandidate.of(LumoraCore.builtInDescriptor("lumora-fullbright", "Fullbright",
                        "Full brightness in caves and at night (only visible to you)", FullbrightModule.class),
                        () -> new FullbrightModule(platform)),
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
                AddonCandidate.of(LumoraCore.builtInDescriptor("lumora-chat", "Chat",
                        "Timestamps, copy messages, drag size, background, longer history, sound on mention", ChatModule.class),
                        () -> new ChatModule(platform)),
                AddonCandidate.of(LumoraCore.builtInDescriptor("lumora-tab", "Tab List & Scoreboard",
                        "Ping as a number, move, scale, hide the numbers of or hide the scoreboard", TabModule.class),
                        TabModule::new));
    }
}
