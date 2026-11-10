package dev.lumora.core.extras;

import dev.lumora.core.LumoraCore;
import dev.lumora.core.Platform;
import dev.lumora.core.addon.AddonCandidate;

import java.util.List;

/**
 * The built-in Lumora modules of the core (one addon each, so every module has its own on/off switch and settings in
 * the hub under Addons). Crosshair, toggle sprint and fullbright change how the game feels and start switched off.
 */
public final class Extras {
    public static final List<String> OFF_BY_DEFAULT = List.of("lumora-crosshair", "lumora-togglesprint", "lumora-fullbright",
            "lumora-attackindicator", "lumora-statusbars");

    private Extras() {
    }

    public static List<AddonCandidate> candidates(Platform platform) {
        return List.of(
                AddonCandidate.of(LumoraCore.builtInDescriptor("lumora-info", "Info-Widgets",
                        "Effekte, Pfeile & Items, Kompass, Speicher, Tag, Ressourcenpakete, Combo", InfoWidgetsModule.class),
                        () -> new InfoWidgetsModule(platform)),
                AddonCandidate.of(LumoraCore.builtInDescriptor("lumora-crosshair", "Fadenkreuz",
                        "Eigenes Fadenkreuz: Form, Größe, Abstand, Dicke, Farbe, Umrandung", CrosshairModule.class),
                        CrosshairModule::new),
                AddonCandidate.of(LumoraCore.builtInDescriptor("lumora-attackindicator", "Angriffsanzeige",
                        "Aufladen von Schlag, Bogen, Armbrust und Dreizack, Item-Cooldowns, Essen, Abbauen: Balken, Ring, Punkt, Levelleiste oder Bossleiste",
                        AttackIndicatorModule.class),
                        () -> new AttackIndicatorModule(platform::extras)),
                AddonCandidate.of(LumoraCore.builtInDescriptor("lumora-blockoverlay", "Block-Overlay",
                        "Umrandung des anvisierten Blocks: Farbe, Regenbogen, Dicke, Einblenden, Blockinfo", BlockOverlayModule.class),
                        () -> new BlockOverlayModule(platform)),
                AddonCandidate.of(LumoraCore.builtInDescriptor("lumora-buttons", "Button-Design",
                        "Knöpfe im Stil Blackout, Hyperium, Liquid, Lunar, PvPClient oder Rounded", ButtonStyleModule.class),
                        ButtonStyleModule::new),
                AddonCandidate.of(LumoraCore.builtInDescriptor("lumora-browser", "Server- & Weltliste",
                        "Eigene Mehrspieler- und Einzelspieler-Menüs: Suche, Filter, Ping, Server- und Weltkarten",
                        dev.lumora.core.browser.BrowserModule.class),
                        () -> new dev.lumora.core.browser.BrowserModule(platform)),
                AddonCandidate.of(LumoraCore.builtInDescriptor("lumora-connection", "Verbindung",
                        "Verbindungsbildschirm mit Phasen und Ping, Trennungsgrund mit Tipps, automatisch neu verbinden",
                        dev.lumora.core.connection.ConnectionModule.class),
                        () -> new dev.lumora.core.connection.ConnectionModule(platform)),
                AddonCandidate.of(LumoraCore.builtInDescriptor("lumora-togglesprint", "Toggle Sprint",
                        "Sprinten und Schleichen per Tastendruck umschalten", ToggleSprintModule.class),
                        () -> new ToggleSprintModule(platform)),
                AddonCandidate.of(LumoraCore.builtInDescriptor("lumora-fullbright", "Fullbright",
                        "Volle Helligkeit in Höhlen und nachts (nur für dich sichtbar)", FullbrightModule.class),
                        () -> new FullbrightModule(platform)),
                AddonCandidate.of(LumoraCore.builtInDescriptor("lumora-statusbars", "Hotbar & Leisten",
                        "Hotbar, Herzen, Rüstung, Hunger, Luft und Erfahrung als eigene Widgets: frei verschieben, Vanilla, kompakt, Balken oder Zahl",
                        StatusBarsModule.class),
                        () -> new StatusBarsModule(platform)),
                // elytras exist since 1.9: on older versions the hub shows the module locked with the reason
                AddonCandidate.of(LumoraCore.builtInDescriptor("lumora-elytratrails", "Elytra-Streifen",
                        "Dampfstreifen oder Wirbel hinter den Flügelspitzen gleitender Spieler: Farbe, Länge, Breite, ab welchem Tempo",
                        ElytraTrailsModule.class, dev.lumora.core.addon.VersionSupport.atLeast(ElytraTrailsModule.MIN_MINECRAFT,
                                platform.minecraftVersion())),
                        ElytraTrailsModule::new),
                AddonCandidate.of(LumoraCore.builtInDescriptor("lumora-chat", "Chat",
                        "Zeitstempel, Nachrichten kopieren, Größe ziehen, Hintergrund, längerer Verlauf, Ton bei Erwähnung", ChatModule.class),
                        () -> new ChatModule(platform)),
                AddonCandidate.of(LumoraCore.builtInDescriptor("lumora-tab", "Tab-Liste & Scoreboard",
                        "Ping als Zahl, Scoreboard verschieben, skalieren, ohne Zahlen oder ausblenden", TabModule.class),
                        TabModule::new));
    }
}
