# Lumora-API – Referenz (Version 1.2.0)

Modul `lumora-api` (Artefakt `dev.lumora:lumora-api:1.2.0`, reines Java 21, keine Abhängigkeiten, keine
Minecraft-Klassen). Die Implementierung steckt in `lumora-core` und den Minecraft-Adaptern unter `client/mc<version>`;
Addons sprechen nur mit diesen Schnittstellen. Javadoc: `gradlew :lumora-api:javadoc` (→ `lumora-api/build/docs/javadoc`).

**API 1.1.0** (`LumoraAPI.API_VERSION`) ist in `lumora-api`, `lumora-core` und allen 16 Modulen `client/mc<version>`
umgesetzt. Addons für 1.0 laden unverändert weiter: Beim Laden gilt „gleiche Hauptversion, nicht neuer“, ein Addon mit
`"api": "1.0.0"` läuft also auf 1.1. Neu in 1.1:

| Dienst | Berechtigung | Inhalt |
|---|---|---|
| `AddonContext.world()` → `game.WorldAccess` | `players` | eigene Augenposition und Blickrichtung, andere Spieler, Tab-Liste, Spieler im Fadenkreuz, Sichtlinie, Meeresspiegel, verbundene IP, Actionbar, Klickgeräusch |
| `AddonContext.session()` → `session.SessionService` | `session` | Profil (UUID, Name) und Mojang-Session-Join für einen Server-Hash; das Token sieht das Addon nie |
| `AddonContext.channels()` → `net.PluginChannels` | `network` | Plugin-Kanäle `namespace:path` mit Rohbytes, ein Addon pro Kanal |
| `AddonContext.screens()` → `screen.AddonScreens` | `screens` | Vollbild-Menüs (`AddonView`), Zurück, Zwischenablage, Addon-Einstellungen im Hub, Hauptmenü mit ▶-Knopf im Hub (`registerMenu`) |
| `AddonContext.nameTags()` → `render.NameTags` | `nametags` | Symbol hinter Spielernamen in Namensschild und Tab-Liste, optional eine Zeile unter dem Namensschild |
| `AddonContext.configDirectory()`, `modLoaded(id)` | – | `config`-Ordner des Spiels; ob eine Fabric-Mod installiert ist |

Die neuen Methoden von `AddonContext` sind `default`-Methoden, die `UnsupportedOperationException("requires Lumora API
1.1")` werfen; ältere Implementierungen kompilieren so weiter. Im Lumora-Client sind sie alle umgesetzt.

Grundregeln:
- Alles läuft auf dem **Client-Thread** (Tick und Rendering), außer es ist anders angegeben. Thread-sicher sind:
  `EventBus.subscribe/unsubscribe`, `Notifications.show`, `TaskScheduler.onClientThread/supplyAsync`.
- Was ein Addon über seinen `AddonContext` registriert, gehört ihm und wird beim Ausschalten entfernt.
- Berechtigungen begrenzen, was die API herausgibt. Sie sind **keine Sandbox**: Ein Addon ist normaler Java-Code mit
  den Rechten jeder anderen Mod.

## dev.lumora.api

| Typ | Zweck |
|---|---|
| `LumoraAPI` | `API_VERSION`, `get()` (wirft, wenn Lumora fehlt), `isAvailable()`. `install()` ist intern (einmal durch den Core). |
| `Lumora` | Lesezugriff: `apiVersion()`, `clientVersion()`, `minecraftVersion()`, `events()` (globaler Bus ohne Besitzer), `game()`, `translations()`, `resources()`, `notifications()`, `addons()`, `addon(id)`, `widgets()`, `hudEditor()`. |
| `Registration` | `unregister()` (idempotent), `isActive()`. |
| `Version` | SemVer `major.minor.patch[-pre][+build]`, `parse`, `tryParse`, `compareTo`. |
| `VersionRange` | `*`, `1.2.3`, `>=1.2`, `>1`, `<=2`, `<2`, `1.2.x`, `^1.2.0`, `~1.2.0`, kombinierbar mit Leerzeichen; `matches(Version)`. |

## dev.lumora.api.addon

| Typ | Zweck |
|---|---|
| `LumoraAddon` | Einstieg. `onInitialize(ctx)` einmal (Einstellungen definieren), `onEnable(ctx)` (registrieren), `onDisable(ctx)` (eigene Ressourcen freigeben). Öffentlicher Konstruktor ohne Parameter. |
| `AddonContext` | `descriptor()`, `state()`, `logger()`, `lumora()`, `events()`, `widgets()` (HUD), `keybinds()` (KEYBINDS), `tasks()` (TASKS), `notifications()` (NOTIFICATIONS), `game()` (GAME), `translations()`, `resources()`, `configuration()`, `hasPermission(p)`. Seit 1.1: `world()` (PLAYERS), `session()` (SESSION), `channels()` (NETWORK), `screens()` (SCREENS), `nameTags()` (NAMETAGS), `configDirectory()` (`config`-Ordner des Spiels für eigene Dateien, ohne Berechtigung), `modLoaded(modId)` (ob eine Fabric-Mod mit dieser ID installiert ist, ohne Berechtigung). Ohne Berechtigung: `AddonPermissionException`. Registrieren außerhalb von `onEnable`/aktiv: `IllegalStateException`. |
| `AddonDescriptor` | Geprüfte Metadaten (record): `id`, `name`, `version`, `description`, `authors`, `apiVersion`, `minecraftVersions`, `entrypoint`, `dependencies`, `permissions`, `builtIn`, `source`; `supportsMinecraft(v)`. |
| `AddonDependency` | `id`, `versions` (`VersionRange`), `optional`. Die ID `lumora` steht für die API selbst. |
| `AddonPermission` | `HUD("hud")`, `KEYBINDS("keybinds")`, `NOTIFICATIONS("notifications")`, `TASKS("tasks")`, `GAME("game")`; seit 1.1 `SCREENS("screens")`, `PLAYERS("players")`, `SESSION("session")`, `NETWORK("network")`, `NAMETAGS("nametags")`. `byId(id)` → Berechtigung oder `null`. |
| `AddonState` | `LOADED`, `RESOLVED`, `INITIALIZED`, `ENABLING`, `ENABLED`, `DISABLED`, `UNLOADED`, `FAILED`. |
| `AddonInfo` | `descriptor()`, `state()`, `error()`, `wanted()` (Wahl des Nutzers), `id()`. |
| `AddonConfiguration` | `settings()` (`SettingsGroup`, gespeichert in `config/lumora/addons/<id>.json`), `save()`. |
| `AddonLogger` | `info`, `warn`, `error` mit Präfix `[addon-id]`. |

### lumora-addon.json

```json
{
  "schema": 1,
  "id": "session-timer",
  "name": "Session Timer",
  "version": "1.0.0",
  "description": "…",
  "authors": ["…"],
  "api": "1.0.0",
  "minecraft": ["*"],
  "entrypoint": "dev.lumora.example.timer.SessionTimerAddon",
  "depends": { "lumora": ">=1.0.0" },
  "optional": { "other-addon": "^2.0" },
  "permissions": ["hud", "keybinds", "notifications", "game"]
}
```

Ein Addon, das Dienste von 1.1 nutzt, gibt `"api": "1.1.0"` und `"depends": { "lumora": ">=1.1.0" }` an und nennt
die neuen Berechtigungen, z. B. LumoraVoice: `["hud", "keybinds", "notifications", "game", "tasks", "screens",
"players", "session", "network", "nametags"]`.

Pflicht: `id` (`[a-z][a-z0-9_-]{1,63}`), `version`, `api`, `entrypoint`. Geprüft beim Laden: Schema, Feldtypen,
Versionen, Berechtigungen, doppelte IDs, Selbstabhängigkeit; danach API-Version (gleiche Hauptversion, nicht neuer),
Minecraft-Version, fehlende oder unpassende Abhängigkeiten (Fehler pflanzen sich fort) und Zyklen.
Die Datei liegt im Wurzelverzeichnis der Mod-JAR neben `fabric.mod.json`.

## dev.lumora.api.event

`EventBus`: `subscribe(type, listener)`, `subscribe(type, priority, listener)`, `subscribe(type, priority,
receiveCancelled, listener)` → `Registration`; `post(event)`; `hasListeners(type)`. Zustellung an Listener genau
dieser Klasse, Reihenfolge nach `EventPriority` (`HIGHEST` … `LOWEST`, gleiche Priorität = Anmeldereihenfolge).
Eine Ausnahme im Listener wird protokolliert, die übrigen laufen weiter; nach 20 Fehlern in Folge wird der Listener
entfernt (Hinweis im Spiel). `Cancellable`-Events erreichen spätere Listener nach dem Abbruch nur mit
`receiveCancelled = true`.

| Event | Wann (Quelle) |
|---|---|
| `client.ClientStartedEvent` | einmal nach dem Laden der Addons (Fabric `CLIENT_STARTED`) |
| `client.ClientStoppingEvent` | beim Beenden, danach werden Addons ausgeschaltet (`CLIENT_STOPPING`) |
| `client.ClientTickEvent(tick)` | Ende jedes Client-Ticks (`END_CLIENT_TICK`), nur wenn jemand zuhört |
| `client.WorldJoinEvent(connection)` | Welt/Server betreten (Tick-Vergleich: Spieler und Level vorhanden) |
| `client.WorldLeaveEvent` | Welt verlassen |
| `client.PlayerChangedEvent(reason, dimension)` | Respawn (neue Spieler-Instanz) oder Dimensionswechsel |
| `client.ScreenChangedEvent(previous, current)` | Menü geöffnet/geschlossen (Klassenname, `""` = keines) |
| `client.ResourcesReloadedEvent` | nach einem Ressourcen-Reload (Mixin auf `Minecraft.reloadResourcePacks()`) |
| `client.HudRenderEvent` | jedes Frame nach den Widgets (wiederverwendete Instanz, nur mit Zuhörern) |
| `addon.AddonEnabledEvent` / `AddonDisabledEvent` / `AddonFailedEvent` | Addon-Lebenszyklus |
| `hud.WidgetRegisteredEvent` / `WidgetUnregisteredEvent` | Widget registriert/entfernt |
| `hud.WidgetToggledEvent(widget, enabled, visible)` | an/aus, gezeigt/verborgen |
| `hud.WidgetRenderEvent` (abbrechbar) | vor jedem Widget im Spiel; Abbruch überspringt es für dieses Frame |
| `hud.HudEditorOpenedEvent` / `HudEditorClosedEvent(saved)` | Editor geöffnet/geschlossen |
| `hud.WidgetSelectedEvent` / `WidgetMovedEvent` / `WidgetScaledEvent` | Auswahl, Ende einer Verschiebung/Größenänderung |
| `hud.LayoutSavedEvent(profile)` / `LayoutProfileChangedEvent(old, new)` | Layout geschrieben, Profil gewechselt |
| `config.SettingChangedEvent(owner, setting)` | Einstellung geändert (`owner` = Widget-ID oder Addon-ID) |

## dev.lumora.api.hud

`HudWidget` (abstrakt):

| Methode | Bedeutung |
|---|---|
| `HudWidget(String id)` | lokale ID `[a-z0-9_-]{1,48}`; global `addon:id` |
| `id()`, `fullId()`, `addonId()`, `settings()` | nach der Registrierung gefüllt |
| `nameKey()`, `descriptionKey()` | Standard `<addon>.widget.<id>` / `+ .description` |
| `category()` | `WidgetCategory.GENERAL`, `PLAYER`, `WORLD`, `SERVER`, `COMBAT`, `VOICE`, `TIME` oder eigener Text |
| `defaultPlacement()` | `WidgetPlacement.at(x, y)[.scaled(s)][.enabledByDefault()]`; x/y = Anteil der freien Fläche 0..1 |
| `minScale()`, `maxScale()`, `defaultLayer()` | Grenzen (0,25 … 4) und Standard-Ebene |
| `configure(SettingsGroup)` | Einstellungen beschreiben (einmal, vor `initialize`) |
| `initialize()`, `onEnable()`, `onDisable()`, `dispose()` | Lebenszyklus |
| `tick()` | 20× pro Sekunde, nur eingeschaltet: Daten sammeln |
| `width(ctx)`, `height(ctx)` | Größe in unskalierten Pixeln |
| `render(HudGraphics, RenderContext)` | bei (0, 0) zeichnen; Position und Skalierung setzt Lumora |
| `isVisible(ctx)` | Laufzeitbedingung; der Editor zeigt dann einen Platzhalter |
| `mouseClicked(x, y, button)` | Klick bei offenem Chat (lokale, unskalierte Koordinaten) |

| Typ | Zweck |
|---|---|
| `WidgetRegistry` | `register(widget)` → `Registration` (doppelte ID oder doppelt registrierte Instanz: `IllegalArgumentException`), `all()`, `byCategory(c)`, `find(id)` |
| `WidgetInfo` | `id()`, `addonId()`, `widget()`, `category()`, `isEnabled()/setEnabled()`, `isShown()/setShown()`, `x()`, `y()`, `scale()`, `layer()`, `failure()` |
| `RenderContext` | `preview()` (keine Live-Daten → Beispielinhalt), `editing()`, `partialTick()`, `screenWidth()`, `screenHeight()`, `guiScale()`, `timeMillis()`, `game()`, `translations()` |
| `WidgetPlacement` | record `(x, y, scale, enabled)` |
| `HudEditor` | `open()`, `isOpen()`, `activeProfile()`, `profiles()`, `activateProfile(id)` |
| `WidgetHost` | intern (Core → Widget) |

## dev.lumora.api.render

`HudGraphics` (nur während des Aufrufs gültig): `textWidth`, `lineHeight`, `text(text, x, y, argb[, shadow])`,
`centeredText`, `rightText`, `fill(x1, y1, x2, y2, argb)`, `rect`, `outline`, `roundedRect`, `card`,
`texture(TextureRef, x, y, w, h, u, v, regionW, regionH, texW, texH)`, `texture(ref, x, y, w, h, texW, texH)`,
`push()`/`pop()`, `translate`, `scale`, `enableScissor`/`disableScissor`, `unwrap(Class)` (nativer Renderer der
Version, z. B. `GuiGraphicsExtractor` in 26.1.2, `GuiGraphics` in 1.21.x – Code damit läuft nur auf dieser Version).

`TextureRef(namespace, path)` – einmal anlegen und behalten. `Colors` – `argb`, `withAlpha`, `fade`, `mix`, `toHex`,
`parseHex` und die Lumora-Farben (`ACCENT`, `TEXT`, `MUTED`, `SURFACE`, `BORDER`, `OK`, `WARNING`, `ERROR`, …).

### NameTags (seit 1.1, Berechtigung `nametags`)

Symbole hinter Spielernamen: am Namensschild über dem Spieler und am Namen in der Tab-Liste.

| Typ | Zweck |
|---|---|
| `NameTags` | `register(Decorator)` → `Registration` |
| `NameTags.Decorator` | `icon(UUID player, Place place)` → `Optional<Icon>`; leer = kein Symbol. Wird **jedes Frame** auf dem Render-Thread gefragt, also billig halten. Haben mehrere Addons einen Decorator, gewinnt der erste mit Symbol; ein Decorator, der eine Ausnahme wirft, wird einmal protokolliert und danach übersprungen. |
| `NameTags.Place` | `WORLD` (Namensschild), `TAB_LIST` |
| `NameTags.Icon` | record `(font, glyph, color, fallback, subtitle)`, siehe unten |

`Icon`-Felder:

| Feld | Bedeutung |
|---|---|
| `font` | Schrift-ID `namespace:path` einer Schrift, die das Addon mitbringt (`assets/<namespace>/font/<path>.json`, meist ein Bitmap-Provider, der das Zeichen auf eine Symbol-Textur abbildet); muss `[a-z0-9_.-]+:[a-z0-9_./-]+` entsprechen |
| `glyph` | Zeichen, das mit dieser Schrift gezeichnet wird (nicht leer); erscheint nach einem Leerzeichen hinter dem Namen |
| `color` | RGB-Tönung des Zeichens (Weiß lässt eine farbige Textur unverändert) und Farbe des Fallbacks |
| `fallback` | reiner Text für Versionen ohne eigene Schriften (1.8.9, 1.12.2), z. B. `"♪"`; dort in der nächstliegenden Chatfarbe. `null` → `""` (kein Symbol) |
| `subtitle` | kleine Zeile unter dem Namensschild (nur `WORLD`), sonst `""`; `null` → `""`. Wird nur auf 26.x und 1.21.11 gezeichnet, siehe [COMPATIBILITY.md](COMPATIBILITY.md#lumora-api-11-je-version) |

Ungültige `font` oder leeres `glyph`: `IllegalArgumentException` beim Erzeugen.

## dev.lumora.api.config

`Setting<T>`: `key()`, `type()`, `get()`, `set(v)` (validiert, `false` wenn abgelehnt/unverändert), `reset()`,
`defaultValue()`, `encode()`/`decode(text)`, `bind(getter, setter)` (Wert lebt beim Besitzer, Lumora speichert ihn
nicht), `isBound()`, `name(key)`, `description(key)`, `advanced()`, `onChange(listener)`.

| Klasse | `SettingType` | Validierung |
|---|---|---|
| `BooleanSetting` | BOOLEAN | – |
| `IntegerSetting` | INTEGER / SLIDER | klemmt auf min..max |
| `DecimalSetting` | DECIMAL / SLIDER | klemmt, rundet auf `step`, lehnt NaN/∞ ab |
| `TextSetting` | TEXT | kürzt auf `maxLength`, entfernt Steuerzeichen |
| `ChoiceSetting` | CHOICE | nur bekannte Optionen; Label `nameKey + "." + option`; `cycle(±1)` |
| `ColorSetting` | COLOR | `#AARRGGBB`; ohne Alpha immer deckend |
| `KeybindSetting` | KEYBIND | nur gültige GLFW-Codes oder `Keys.UNBOUND` |

`SettingsGroup`: `toggle`, `integer`, `slider(int…)`, `decimal`, `slider(double…)`, `text`, `choice`, `color`,
`keybind`, `add`, `get`, `all`, `isEmpty`, `resetAll`. Doppelte Schlüssel: `IllegalArgumentException`.
`onAdd(...)` ist intern.

## dev.lumora.api.input

`KeybindRegistry.register(id, nameKey, defaultKey, onPress)` → `Keybind` (`id()`, `nameKey()`, `defaultKey()`,
`key()`, `setKey(k)` – abgelehnt bei Konflikt, `conflictWith(k)`, `isDown()`, `unregister()`), `keybinds()`,
`keyName(k)`. Feuert einmal pro Tastendruck, nur im Spiel (kein Menü, Fenster aktiv). Ist die Standardtaste schon von
Minecraft oder einem anderen Keybind belegt, startet der Keybind ohne Taste. Vom Nutzer gewählte Tasten:
`config/lumora/keybinds.json`. `Keys`: GLFW-Konstanten, `UNBOUND`, `isValid`, `isBindable` (Escape nicht).
Grenze: Lumora-Keybinds erscheinen in den Lumora-Einstellungen, nicht unter Optionen → Steuerung.

## dev.lumora.api.notify

`Notifications.show(Notification)`, `info/success/warning/error(title, message)`. `Notification.builder(title)
.message(…).type(NotificationType…).icon(TextureRef).duration(ms).build()`. Höchstens 4 sichtbar, 32 in der
Warteschlange (weitere werden verworfen).

## dev.lumora.api.game

`GameContext` (einmal pro Tick aktualisiert): `inWorld()`, `player()` → `PlayerSnapshot` (UUID, Name, Position,
Blick, Leben, Hunger, Rüstung, Level, Boden/Sprint/Schleichen, Tempo, `facing()`), `dimension()`, `gameMode()`,
`connection()` → `ConnectionInfo(kind NONE/SINGLEPLAYER/MULTIPLAYER, address, ping)`, `screenOpen()`, `screen()`,
`hudHidden()`, `windowFocused()`, `fps()`, `clientTicks()`. Ohne Welt sind die Optionals leer.

### WorldAccess (seit 1.1, `AddonContext.world()`, Berechtigung `players`)

Live-Zugriff auf die geladene Welt und ihre Spieler, z. B. für Sprachchat oder Minikarten. Anders als `GameContext`
liest `WorldAccess` live: nur auf dem Client-Thread aufrufen (Ticks, Events, Rendering). Ohne Welt liefern alle
Methoden leere Werte.

| Methode | Ergebnis |
|---|---|
| `viewPoint()` | `Optional<ViewPoint>`: eigene Augenposition und normierte Blickrichtung |
| `players(limit)` | andere Spieler der Welt (nie der eigene), nächste zuerst, höchstens `limit` → `List<PlayerInfo>` |
| `tabList()` | Spieler der Tab-Liste, eigener eingeschlossen → `List<TabEntry>` |
| `crosshairPlayer()` | `Optional<TabEntry>` des Spielers im Fadenkreuz |
| `lineOfSightBlocked(x1, y1, z1, x2, y2, z2)` | ob feste Blöcke auf der geraden Linie zwischen den Punkten liegen |
| `seaLevel()` | Meeresspiegel der Welt (63, wenn unbekannt) |
| `remoteIp()` | IP-Adresse der Verbindung (SRV-Einträge aufgelöst); leer im Einzelspieler und in Menüs |
| `actionBar(message)` | kurze Nachricht über der Hotbar |
| `playClickSound()` | Vanilla-Klickgeräusch |

Records: `ViewPoint(id, x, y, z, lookX, lookY, lookZ, inWater)`, `PlayerInfo(id, name, x, y, z, inWater, skyVisible,
teamColor)` (Augenposition; `skyVisible` = Block an der Position sieht den Himmel; `teamColor` = `OptionalInt` mit der
RGB-Teamfarbe), `TabEntry(id, name)`. `WorldAccess.NONE` ist die leere Implementierung.

## dev.lumora.api.connection und event.connection (seit 1.2)

Lumora verfolgt jede Serververbindung, auch wenn das Modul „Verbindung“ aus ist. Die Events kommen auf dem
Client-Thread über den normalen Event-Bus (`context.events().subscribe(...)`) und brauchen keine Berechtigung.

| Event | Wann | Inhalt |
|---|---|---|
| `ServerConnectingEvent` | Minecraft beginnt zu verbinden (Serverliste, Direktverbindung, Quick Play, Transfer, Neuverbinden) | `connection()`, `attempt()` (0 = erster Versuch, sonst automatisches Neuverbinden) |
| `ConnectionPhaseEvent` | nächste Phase erreicht | `connection()` (mit neuer `phase()`), `previous()`, `previousMillis()` |
| `ServerJoinedEvent` | Welt des Servers erreicht | `connection()`, `connectMillis()` |
| `ServerDisconnectedEvent` | Verbindung beendet oder gescheitert | `connection()`, `lastPhase()`, `kind()` (`DisconnectKind`), `message()`, `reconnectIn()` (ms, −1 = kein automatisches Neuverbinden) |

`ServerConnection(name, address, phase, startedMillis, joinedMillis)`. Phasen (`ConnectionPhase`), in dieser Reihenfolge:
`CONNECTING`, `LOGIN`, `AUTHENTICATING`, `ENCRYPTING`, `CONFIGURING`, `JOINING`, `PLAYING`, `DISCONNECTED`.
Offline-Server überspringen Kontoprüfung und Verschlüsselung; vor 1.20.2 gibt es keine Konfiguration.

`DisconnectKind`: `USER`, `TIMEOUT`, `REFUSED`, `UNKNOWN_HOST`, `LOST`, `AUTH`, `BANNED`, `WHITELIST`, `FULL`, `OUTDATED`,
`THROTTLED`, `SHUTDOWN`, `DUPLICATE_LOGIN`, `KICKED`, `PROTOCOL`, `OTHER`. `retryable()` sagt, ob ein neuer Versuch ohne
Änderung helfen kann.

```java
context.events().subscribe(ServerDisconnectedEvent.class, e -> {
    if (e.kind() == DisconnectKind.FULL) {
        context.notifications().info("Server voll", "Lumora versucht es gleich noch einmal");
    }
});
```

## dev.lumora.api.session (seit 1.1)

`SessionService` (`AddonContext.session()`, Berechtigung `session`): das angemeldete Minecraft-Konto. Das Zugriffstoken
sieht das Addon nie.

- `profile()` → `Optional<Profile(id, name)>`; leer ohne Sitzung (z. B. Demo oder offline).
- `joinServer(serverHash)` → `POST sessionserver.mojang.com/session/minecraft/join` für den Hash, derselbe Aufruf, den
  das Spiel vor dem Betreten eines Online-Mode-Servers macht. Dienste wie Sprachchat prüfen das Konto danach bei Mojang.
  **Blockiert**, solange die Anfrage läuft: auf einem Hintergrund-Thread aufrufen (z. B. `tasks().runAsync`). Ohne
  Sitzung oder bei Ablehnung durch Mojang: `Exception` mit lesbarer Meldung.

## dev.lumora.api.net (seit 1.1)

`PluginChannels` (`AddonContext.channels()`, Berechtigung `network`): Minecraft-Plugin-Kanäle `namespace:path` mit
rohen Nutzdaten. Das Format der Bytes ist Sache des Addons; Lumora transportiert sie nur, deshalb läuft derselbe
Addon-Code auf jeder Version.

- `register(id, Consumer<byte[]> receiver)` → `PluginChannel`. Nur in `onEnable`, vor dem Betreten eines Servers. Der
  `receiver` bekommt jede Nachricht des Servers auf diesem Kanal, auf dem Client-Thread. Ein Addon pro Kanal.
  Ungültige ID: `IllegalArgumentException`; Kanal gehört schon einem anderen Addon: `IllegalStateException`.
- `PluginChannel`: `id()`, `serverSupports()` (ob der aktuelle Server den Kanal angekündigt hat – Mod oder
  Bukkit/Paper-Plugin), `send(byte[])` (ohne Verbindung oder ohne Unterstützung wird ignoriert).
- Kann eine Version einen Kanal nicht anlegen (1.8.9/1.12.2: Name länger als 20 Zeichen), wirft `register` nicht;
  `serverSupports()` bleibt dann `false` und das Log meldet „not available in this Minecraft version“. Technik je
  Version: [COMPATIBILITY.md](COMPATIBILITY.md#lumora-api-11-je-version).

## dev.lumora.api.screen (seit 1.1)

`AddonScreens` (`AddonContext.screens()`, Berechtigung `screens`) öffnet Addon-Menüs. Menüs stapeln sich: `open` zeigt
eine Ansicht über dem aktuellen Bildschirm, `back` oder Escape kehrt zum vorherigen zurück.

| Methode | Bedeutung |
|---|---|
| `open(AddonView)` | Ansicht zeigen (Client-Thread) |
| `back()` | oberste Ansicht dieses Addons schließen, falls sie gezeigt wird |
| `clipboard()` | Text der System-Zwischenablage (leer, wenn keiner) |
| `openAddonSettings()` | Lumora-Hub-Einstellungen dieses Addons (Addon-Einstellungen und Tasten) |
| `registerMenu(Supplier<AddonView>)` | Hauptmenü des Addons: Der Lumora-Hub (Tab **Addons**) zeigt dafür einen **▶-Knopf** neben ⚙, damit das Menü auch ohne belegte Taste erreichbar ist. Der `Supplier` erzeugt bei jedem Öffnen eine neue Ansicht. Wird beim Ausschalten entfernt. |

`AddonView` ist ein Vollbild-Menü, gezeichnet mit `HudGraphics` und bedient ohne Minecraft-Klassen. Lumora zeigt es in
einem Bildschirm der laufenden Minecraft-Version und leitet Zeichnen und Eingaben weiter: GUI-Koordinaten, Maustaste
0 = links, Tastencodes aus `input.Keys` (auch auf Versionen mit LWJGL 2).

| Methode | Bedeutung |
|---|---|
| `render(graphics, width, height, mouseX, mouseY)` | zeichnen (Pflicht) |
| `mouseClicked`, `mouseReleased`, `mouseDragged(x, y, button)` | Maus; `true` = verarbeitet |
| `mouseScrolled(x, y, delta)` | `delta > 0` scrollt nach oben |
| `keyPressed(key, ctrl, shift)`, `charTyped(ch)` | Tastatur |
| `closeRequested()` | Escape gedrückt; `false` hält die Ansicht offen (Standard `true`) |
| `removed()` | Ansicht wird nicht mehr gezeigt (geschlossen, vom Spiel ersetzt oder Addon ausgeschaltet) |

## dev.lumora.api.resource / i18n / task

- `Resources.texture(ns, path)`, `exists(ns, path)` (Speicherabfrage, kein Plattenzugriff). Texturen und Sprachdateien
  eines Addons liegen wie bei jeder Mod in `assets/<namespace>/…` seiner JAR.
- `Translations.translate(key, args…)`, `has(key)`, `language()`, `translateOr(key, fallback)` – Minecrafts
  Sprachsystem.
- `TaskScheduler.onClientThread(r)`, `delay(ticks, r)`, `repeat(ticks, r)` (→ `Registration`),
  `supplyAsync(supplier)` / `runAsync(r)` auf dem gemeinsamen Worker-Pool (2 Threads). Beim Ausschalten des Addons
  werden Timer beendet und laufende Aufgaben abgebrochen.

## Speicherorte (in `.minecraft/config/lumora/`)

| Datei | Inhalt |
|---|---|
| `hud-layout.json` | Profile, Widget-Zustände (an, gezeigt, x, y, Größe, Ebene, eigene Einstellungen), Raster/Einrasten |
| `keybinds.json` | geänderte Tasten (`addon:id` → GLFW-Code) |
| `addons.json` | ausgeschaltete Addons |
| `addons/<id>.json` | Addon-Einstellungen |

Alle Dateien werden verzögert (2 s nach der letzten Änderung) und atomar geschrieben, beim Beenden sofort.
Kaputte Dateien werden als `<name>.corrupt-<zeit>` gesichert, dann gelten Standardwerte.

Eigene Dateien eines Addons (seit 1.1): `AddonContext.configDirectory()` liefert den `config`-Ordner des Spiels
(`.minecraft/config`, Fabrics Config-Verzeichnis). Was das Addon dort schreibt, verwaltet es selbst; Lumora speichert
es nicht verzögert und sichert es nicht. Beispiel: LumoraVoice schreibt `config/lumoravoice.json`.
