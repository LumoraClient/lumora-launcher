# Architektur

## Überblick

```
LumoraLauncher.exe (jpackage, eigene jlink-Java-Runtime)
 └─ launcher-app        Swing/FlatLaf-GUI + CLI (--smoke-test, --install-only, --system-check)
     └─ launcher-core   versionsunabhängige Logik, kein Minecraft-Code
         ├─ config/     launcher.json: laden, validieren, reparieren, Backup bei Korruption
         ├─ profile/    profiles.json: Lockfile aller Laufzeitprofile + Artefakte (SHA-512)
         ├─ mojang/     Versions-Manifest, Versions-JSON, Regeln, Bibliotheken, Assets
         ├─ java/       offizielle Mojang-Java-Runtime (all.json → Manifest → Dateien, SHA-1)
         ├─ download/   verifizierte Downloads (Allow-List, Hash-Pflicht, Retry, atomare Dateien)
         ├─ mods/       verifizierte Ablage der Lumora-JAR und der Ressourcenpakete in der Instanz
         ├─ auth/       Microsoft → Xbox Live → XSTS → Minecraft Services, DPAPI-Session-Speicher
         ├─ launch/     Startbefehl aus Versions-JSON, Prozessüberwachung, Mehrfachstart-Sperre
         ├─ install/    ClientInstaller: orchestriert alle Schritte idempotent
         ├─ library/    Welten, servers.dat (NBT), Screenshots, Ressourcenpakete, Server-Status (Server List Ping)
         ├─ stats/      Spielzeit je Version (config/playstats.json)
         ├─ history/    Verbindungsverlauf: Anmeldungen, Spielstarts, Server (config/connections.json)
         ├─ diagnostics/ Systemprüfung, Crash-Analyse
         └─ update/     Update-Prüfung über die Modrinth-API (nur mit Bestätigung)

Minecraft-Prozess (separater Prozess, Mojang-Java 25 für 26.x, sonst 21)
 └─ Lumora Loader (dev.lumora.loader.Main, kein Mod-Loader)
     ├─ Minecraft <version> (Vanilla-JAR; 1.8.9 … 1.21.11 beim Nutzer auf lesbare Namen umbenannt)
     ├─ lumora-client-mc<version>   (16 Builds, 1.8.9 bis 26.3)
     └─ Lumora-Addons aus instances/<version>/addons
     (26.1 und 26.1.1 nutzen lumora-client-mc26.1.2; Details: COMPATIBILITY.md)
```

### Entscheidungen

| Entscheidung | Begründung |
|---|---|
| Eigener Lumora Loader statt Fabric | Lumora läuft nur auf Vanilla plus Lumora-Addons. Der Loader ist klein (Klassenlader, Mixin, Umbenennen der Spiel-JAR), es gibt keine Fremd-Mods und keine Abhängigkeitsauflösung mehr. Bis 0.2.2 lief alles über Fabric (siehe Git-Verlauf). |
| Ein Profil pro Minecraft-Version, Begleit-Mod pro Version portiert | Die Minecraft-APIs unterscheiden sich je Version (z. B. `GuiGraphicsExtractor` in 26.1, `GuiGraphics` in 1.21). Jede Portierung liegt unter `client/mc<version>` und wird gegen ihre Version kompiliert und getestet. Das frühere VulkanMod-Profil wurde entfernt, siehe [COMPATIBILITY.md](COMPATIBILITY.md). |
| Ressourcenpakete werden heruntergeladen, nicht gebündelt | Bezug aus der offiziellen Quelle (Modrinth-CDN) mit fest hinterlegtem SHA-512, keine Weiterverteilung. |
| Mojangs Namensliste nur beim Nutzer | Die verschleierten Versionen werden erst auf dem Rechner des Nutzers mit Mojangs `client_mappings` umbenannt. Lumora verteilt weder die Liste noch eine umbenannte Minecraft-JAR. |
| Launcher in Java statt nativ | Die Logik ist mit dem Mod-Ökosystem (JSON, Maven, Hashes) teilbar und gut testbar. jpackage liefert eine native EXE mit eigener Runtime, Nutzer brauchen kein Java. |
| Eigene Spielinstanz pro Profil | Unterschiedliche Mod-Sätze und Konfigurationen beeinflussen sich nicht. Bibliotheken, Assets und Runtimes werden geteilt. |

## Datenverzeichnis

```
%APPDATA%\LumoraClient\           (oder LUMORA_HOME / installDir aus den Einstellungen)
 ├─ config\launcher.json          Launcher-Einstellungen (validiert)
 ├─ config\profiles.override.json bestätigte Komponenten-Updates (validiert, sonst ignoriert)
 ├─ auth\session.bin              Sitzung, DPAPI-verschlüsselt (nur aktueller Windows-Benutzer)
 ├─ logs\launcher.log             Launcher-Log (rotierend, 3 Generationen, Tokens geschwärzt)
 ├─ versions\, libraries\, assets\, runtime\, cache\mods\   geteilte, hash-geprüfte Daten
 ├─ instances.1.2, instances.21.11, instances.21.8   Spielordner (mods, config, saves, logs)
 │    (alte Ordner vulkan/opengl: der zuletzt gespielte wird beim ersten Start zu 26.1.2, der andere bleibt liegen)
 └─ diagnostics\                  Berichte des Lumora-Mods (JSON)
```

## Ablauf „Spielen“

1. **INSTALLING**: `ClientInstaller.prepare`
   1. Mojang-Manifest → Versions-JSON (SHA-1 aus dem Manifest, Cache für Offline-Start); OS-Regeln werden angewandt (`ResolvedVersion.merge`)
   2. Client-JAR, Bibliotheken, Asset-Index und -Objekte, Log4j-Konfiguration: alles mit SHA-1
   3. Java-Runtime von Mojang (`java-runtime-epsilon` = Java 25 für 26.x, sonst `java-runtime-delta` = Java 21), jede Datei mit SHA-1
   4. Lumora-JAR (SHA-512 aus `bundled-mods.json`) → `instances/<profil>/.lumora/client`; Ressourcenpakete (SHA-512 aus dem Lockfile) → `resourcepacks`
   5. 1.16.5 bis 1.21.11: Mojangs `client_mappings` (SHA-1 aus dem Versions-JSON) → `versions/<v>/<v>-client-mappings.txt`
   6. Marker `.lumora-verified`: Folgestarts prüfen nur noch Größe (QUICK). „Installation reparieren“ prüft alles erneut per Hash (FULL)
2. **LOGGING_IN**: gespeicherte Sitzung gültig? → verwenden. Sonst Refresh-Token. Sonst interaktive Anmeldung:
   standardmäßig **im Browser** (OAuth 2.0 Authorization Code + PKCE S256, Weiterleitung auf `http://localhost:<zufälliger Port>`,
   nur Loopback, `state`-Prüfung, `LoopbackReceiver`). Auf Wunsch per **Device Code** („Mit Code anmelden“).
3. **LAUNCHING**: `LaunchCommandBuilder` erzeugt den Befehl aus den Argument-Templates der Versions-JSON (Regeln, Features, Platzhalter).
4. **PLAYING**: `GameProcess` hält eine Dateisperre auf der Instanz (kein Doppelstart), schreibt die Spielausgabe geschwärzt nach `logs/lumora-game-output.log` und erkennt bei Absturz den Crash-Report. `CrashAnalyzer` liefert Hinweise, etwa zu Speichermangel oder inkompatiblen Mods.

## Startarten

Der Launcher startet ein Profil als **Lumora** (Minecraft mit dem Lumora Client über den Lumora Loader) oder als **Vanilla** (reines Minecraft, eigener Spielordner `instances/<version>-vanilla`). Mod-Loader (Fabric, Quilt, Forge, NeoForge) gibt es nicht mehr, ebenso keine Seite „Mods“; „Entdecken“ zeigt nur Ressourcenpakete. Eine gespeicherte Auswahl eines Loaders startet Lumora. Alte Spielordner `instances/<version>-<loader>` bleiben unangetastet liegen.

Lumora-Addons lädt der Client selbst aus dem Ordner `addons` der Instanz (siehe Abschnitt Client-Framework).

## Lumora Loader (`lumora-loader`, ersetzt Fabric)

Alle 18 Profile (`"loader": "lumora"` in `profiles.json`) starten **ohne Mod-Loader**: nur Minecraft (Vanilla-Versions-JSON), der Lumora Client und die Lumora-Addons.

```
java -cp lumora-loader.jar;<Bibliotheken von Minecraft>  dev.lumora.loader.Main  <Minecraft-Argumente>
     -Dlumora.loader.game=versions/26.3/26.3.jar            (nicht auf dem Klassenpfad)
     -Dlumora.loader.jars=instances/26.3/.lumora/client/lumora-client-mc26.3.jar
     -Dlumora.loader.target=net.minecraft.client.main.Main
     -Dlumora.loader.mappings=versions/<v>/<v>-client-mappings.txt   (nur 1.16.5 bis 1.21.11)
```

1. **`Boot`** liest die `lumora.loader.json` jeder Lumora-JAR (`id`, `version`, `entrypoints`, `mixins`).
   - **Umbenennen** (`GameRemapper`): Bei verschleierten Versionen benennt der Loader die Minecraft-JAR einmal auf lesbare Namen um (ASM `ClassRemapper`, mit Vererbung und Lambdas) und legt das Ergebnis als `<name>-lumora-<hash>.jar` neben die Original-JAR. 1.16.5 bis 1.21.11 nutzen Mojangs `client_mappings` (ProGuard-Format), 1.8.9 und 1.12.2 die Datei `lumora/mappings.tiny` aus der Lumora-JAR (Intermediary + Yarn, CC0). Bei Yarn macht `PublicAccess` Felder und Methoden öffentlich, wie es Fabric dort tat. 26.1 und neuer sind nicht verschleiert.
2. **`GameClassLoader`** lädt die Minecraft-JAR und die Lumora-JAR selbst. Jede Klasse läuft vor dem Definieren durch den Mixin-Transformer, von Mixin erzeugte Klassen (`org.spongepowered.asm.synthetic.*`) entstehen dort. Java, die Bibliotheken des Spiels, der Loader, Mixin und ASM kommen vom übergeordneten Klassenlader.
3. **Mixin:**
   - **Dienst:** `LumoraMixinService` liefert die Klassen-Bytes, das Laden von Klassen und die Mixin-Konfigurationen, `LumoraGlobalPropertyService` ist der globale Speicher. Beide sind über `META-INF/services` angemeldet.
   - **Start:** `MixinBootstrap.init`, dann die Konfigurationen der JARs, dann die Phasen INIT und DEFAULT.
   - **Lieferung:** Mixin und ASM stecken in der Loader-JAR. Der ASM-Version im Manifest entnimmt Mixin, welche Java-Klassenversionen es unterstützt.
4. **Start:** Die Einstiegspunkte (`dev.lumora.loader.api.ClientEntrypoint`) laufen vor Minecraft. Danach folgt `net.minecraft.client.main.Main.main` mit den Original-Argumenten.

Was vorher die Fabric-API lieferte, bringt jeder Client selbst mit (`client/mc<version>/.../platform` und `mixin/platform`). Die Tabelle zeigt 26.3; ältere Versionen hängen sich an die dort passenden Stellen (z. B. `Gui.render` statt `Hud.extractRenderState`, `Pack.create` bis 1.18.2, String-Kanäle und Yarn-Namen bei 1.8.9/1.12.2):

| Bisher (Fabric) | Jetzt (Lumora) |
|---|---|
| Lebenszyklus, Tick (`ClientLifecycleEvents`, `ClientTickEvents`) | `MinecraftLifecycleMixin`: `run` (Spiel gestartet), `tick` (Ende), `exitWorldAndClose` |
| Bildschirm-Ereignisse, Knöpfe in fremden Menüs (`ScreenEvents`, `Screens`) | `ScreenHooksMixin` (`init`, nach dem Zeichnen), `ScreenWidgetsInvoker` |
| HUD-Ebene (`HudElementRegistry`) | `HudLayerMixin` am Ende von `Hud.extractRenderState` |
| Klicks im Chat (`ScreenMouseEvents`) | `ChatScreenClickMixin` |
| Tastenbelegungen (`KeyMappingHelper`) | `OptionsKeysMixin`: Lumoras Tasten werden vor dem Lesen von `options.txt` an `keyMappings` angehängt |
| Mod-Ressourcen, eingebaute Pakete (`ResourceLoader`) | `BuiltInPackSourceMixin` + `Packs`: Paket „lumora“ (eigene `assets/`, immer an) und „lumora:lumora_theme“ / „lumora:lumora_font“ (abschaltbar, dieselben IDs wie vorher) |
| Plugin-Kanäle (`PayloadTypeRegistry`, `ClientPlayNetworking`) | `Channels` + `DiscardedPayloadMixin` (Bytes registrierter Kanäle bleiben erhalten), `ClientCustomPayloadMixin`. Beim Betreten einer Welt meldet `minecraft:register` die Kanäle an. |
| `FabricLoader` (Ordner, Versionen, `isModLoaded`) | `LoaderEnv` über `dev.lumora.loader.api.LumoraLoader`. Andere Mods gibt es nicht, Sodium und ViaFabricPlus gelten als „nicht geladen“. |

Der Launcher legt die Lumora-JAR in `instances/<version>/.lumora/client` ab und lässt einen alten `mods`-Ordner unangetastet liegen. Fabric-Mods (Sodium, Lithium, ViaFabricPlus, Simple Voice Chat …) und Ressourcenpakete, die eine Mod brauchten, sind aus dem Katalog entfernt.

## Verbindungssystem im Client (`lumora-core/.../connection`)

Konzept nach LiquidBounce, eigene Umsetzung. Minecraft baut die Verbindung weiterhin selbst auf und macht auch Handshake, Login, Verschlüsselung und Sitzungsbeitritt bei Mojang. Lumora öffnet keine eigene Verbindung und fasst keine Tokens an.

- **Hook je Version** (`ConnectionScreenMixin` auf `setScreen`): Öffnet Minecraft seinen Verbindungs- oder Trennungsbildschirm, meldet der Hook das an `ConnectionModule.connecting` bzw. `.disconnected`. Den Verbindungsbildschirm von Minecraft tickt Lumoras Bildschirm weiter (`AddonViewScreen.connecting`), so läuft die Verbindung unverändert. `McConnection` liest den Zustand: Packet-Listener (Login, Konfiguration, Spiel), Statuszeile (`connect.authorizing`, `connect.encrypting` …) und aufgelöste Adresse. Abbrechen wirkt wie Minecrafts eigener Knopf.
- **`ConnectionTracker`** (immer aktiv) macht daraus Phasen, die nur vorwärts laufen: `CONNECTING → LOGIN → AUTHENTICATING → ENCRYPTING → CONFIGURING → JOINING → PLAYING → DISCONNECTED`. Er misst die Dauer jeder Phase und sendet Events für Addons (`ServerConnectingEvent`, `ConnectionPhaseEvent`, `ServerJoinedEvent`, `ServerDisconnectedEvent`). Ein **Wächter** bricht hängende Phasen vor der Konfiguration ab. Die Konfiguration selbst ist ausgenommen, weil Ressourcenpaket-Fragen dauern können. Verlässt der Spieler die Welt ohne Trennungsbildschirm, gilt das als eigener Wunsch. Kommt der Bildschirm doch noch, wird der echte Grund nachgereicht.
- **`DisconnectClassifier`** erkennt den Grund zuerst an den Übersetzungsschlüsseln, also unabhängig von der Sprache, dann an bekannten Texten von Serversoftware.
- **`ReconnectPolicy`**: Neuverbinden nur bei wiederholbaren Gründen, mit verdoppelter Pause bis 1 min und begrenzten Versuchen. Server, die drosseln, voll sind oder neu starten, bekommen Mindestpausen. Bei Sperre, Whitelist, falscher Version, Kontofehler oder Anmeldung an anderer Stelle wird nie automatisch neu verbunden.
- **`ConnectionModule`** (Modul „Verbindung“) steuert nur, was der Spieler sieht: Bildschirme, Auto-Reconnect, Zeitlimit, Stream-Modus und Ping-Vorschau. Ins Log kommen nur Grund und Phase, keine Adressen und keine Servernachricht.
- **Welt betreten** (`WorldLoadingView`, Schalter „Lumora-Ladebildschirm beim Betreten einer Welt“): Beim Öffnen einer Einzelspieler-Welt zeigt Lumora im selben Stil den Weltnamen, einen Gesamtfortschritt und jede Phase mit ihrer Dauer: Weltdaten lesen bzw. Welt anlegen, Datenpakete, Server starten, Spawnbereich vorbereiten (mit Prozent), Gelände laden. Minecraft macht die Arbeit selbst. `McWorldLoad` je Version nimmt jeden Ladebildschirm, den Minecraft währenddessen öffnet, als Bildschirm unter Lumoras (er wird weiter getickt und schließt sich selbst, wenn die Welt bereit ist):
  - 26.x und 1.21.11: `LevelLoadingScreen` mit `LevelLoadTracker` (Server-Phase und Fortschritt).
  - 1.16.5 bis 1.21.8: `LevelLoadingScreen` mit `StoringChunkProgressListener` für den Spawnbereich und `ReceivingLevelScreen` für das Gelände. In 1.18.2 bis 1.20.1 meldet Minecraft den Spawnpunkt nur dem sichtbaren Gelände-Bildschirm; `SpawnPositionMixin` reicht ihn weiter.
  - 1.8.9 und 1.12.2: Bis der Server läuft, zeichnet Minecraft direkt (`LoadingScreenRenderer`, ohne Bildschirm). Dort zeichnet Lumora seine Ansicht, danach läuft `DownloadingTerrainScreen` darunter.
  - „Vorbereiten…“ (`createWorld.preparing`) zählt nur, wenn es aus dem Menü „Welt erstellen“ kommt: Minecraft zeigt denselben Text auch beim bloßen Öffnen dieses Menüs. Portal- und Dimensionswechsel, Respawn und Server-Beitritte behalten Minecrafts Bildschirme. Ins Log kommen nur Phasen und Zeiten, kein Weltname.

## Sicherheit

- **Download-Allow-List** (`Downloader.DEFAULT_TRUSTED_HOSTS`): nur HTTPS zu Mojang- und Modrinth-Hosts (Fabric-Hosts sind entfernt). Jede Datei braucht einen erwarteten Hash. Erst nach erfolgreicher Prüfung wird die Datei an ihren Zielort verschoben.
- **Pfad-Traversal-Schutz** (`FileUtil.resolveInside`) für alle Pfade aus entfernten Manifesten.
- **Keine fest eingebauten Zugangsdaten.** Die Azure-Client-ID wird vom Verteiler oder Nutzer konfiguriert. Passwörter sieht der Launcher nie, die Anmeldung läuft auf Microsofts Webseite.
- **Token-Schutz**: `Redactor` schwärzt registrierte Geheimnisse und typische Muster (Bearer, JWT, `--accessToken`) in allen Logs. Die Sitzung liegt unter Windows DPAPI-verschlüsselt. Unter Linux/macOS gilt `0600`. Das ist kein Keyring, siehe TROUBLESHOOTING.
- Das Minecraft-Zugangstoken muss als Startargument `--accessToken` übergeben werden (Vorgabe von Minecraft). Andere Prozesse desselben Benutzers können es sehen. Das gilt für alle Launcher.
- Der Browser wird nur für Microsoft-Hosts automatisch geöffnet (`DeviceCodeDialog.isTrusted`).
- Smoke-Test-Modus: startet den **offiziellen Demo-Modus** (Feature `is_demo_user`). Er ist nur über die CLI erreichbar, nicht über die GUI, und umgeht keine Kaufprüfung.
- **Neuigkeiten** laden nur von `launchercontent.mojang.com` (HTTPS, keine Weiterleitungen, Größenlimit, Pfade geprüft; eigener HTTP-Client in `NewsFeed`, nicht über die Download-Allow-List). „Weiterlesen“ öffnet nur Links zu minecraft.net. Patch Notes werden ohne Bilder, Skripte, Styles und Links angezeigt.
- **Server-Liste**: Spielerzahl, Icon und Version kommen per Server List Ping direkt vom jeweiligen Server (wie die Mehrspieler-Liste von Minecraft). Verlauf und Bewertungen bleiben lokal (`<Datenordner>/servers/`). Es gibt keinen Statistik-Dienst.
- **Verbindungsverlauf** (`history/ConnectionHistory`, Konto → Verlauf): Anmeldungen, Erneuerungen, Abmeldungen und Spielstarts je Konto sowie die Server, mit denen sich Minecraft verbunden hat (erkannt an der Logzeile „Connecting to host, port“). Gespeichert werden nur Name, UUID, Version und Serveradresse, nie Tokens. Bleibt lokal in `config/connections.json`, lässt sich abschalten und leeren.
- **Zwei-Faktor-Authentifizierung**: Lumora hat kein eigenes Konto. Konto → Sicherheit öffnet die öffentlichen Microsoft-Kontoseiten (zusätzliche Sicherheitsoptionen, letzte Aktivität) im Browser. Die Sitzungstabelle zeigt die auf diesem PC angemeldeten Konten und meldet sie ab.
- **Autostart** schreibt nur den Wert `LumoraLauncher` unter `HKCU\…\Run` (aktueller Benutzer, keine Admin-Rechte) und nur, wenn der Nutzer den Schalter umlegt.
- **Discord Rich Presence** (`integrations/DiscordPresence`) spricht nur mit der Discord-App auf demselben PC (Named Pipe `\\.\pipe\discord-ipc-N`, unter Linux/macOS Unix-Socket), mit der vom Nutzer eingetragenen Anwendungs-ID. Gesendet werden nur „Im Launcher“ bzw. Version und Startart, keine Konto- oder Serverdaten. Aus = keine Verbindung.
- **Anonyme Statistiken**: Es gibt keinen Lumora-Statistikserver. Der Schalter ist standardmäßig aus und wird nur gespeichert, gesendet wird nichts.
- **Debug-Modus** erlaubt nur Loopback-Adressen (`localhost`, `127.0.0.1`, `[::1]`); ein JDWP-Port im Netzwerk wäre Codeausführung für jeden, der ihn erreicht. Ungültige Adressen setzt `LauncherConfig` zurück.
- **Speicher freigeben** (`storage/StorageUsage`) löscht nur in Kategorien, die der Launcher neu laden kann (Java, Download-Cache, Bibliotheken, Assets, Versionen, Logs, Absturzberichte), nur innerhalb des Datenordners und ohne Verknüpfungen nach außen zu folgen. Welten, Screenshots, eigene Mods und Konfigurationen werden nur angezeigt. Jede Aktion fragt nach, und sie ist gesperrt, solange Minecraft läuft.
- **Startart** Vanilla nutzt einen eigenen Instanzordner (`<Version>-vanilla`) und Mojangs Java für die Version; dort wird kein Lumora installiert und keine Dateien der Lumora-Instanz verändert.
- **Selbst-Update** (`update/LauncherUpdate`): fragt nur die offizielle GitHub-API (`api.github.com/repos/LumoraClient/lumora-launcher/releases/latest`) ab. Installiert wird eine EXE nur, wenn ihre SHA-256 in einer `SHA256SUMS.txt` steht, die mit dem Release-Schlüssel (Ed25519) signiert ist. Der öffentliche Schlüssel liegt im Launcher, der private nur beim Release-Ersteller (`~/.lumora-signing/`, nie im Repository). Ein übernommenes GitHub-Konto allein kann also kein Update verteilen. Ersetzt wird nur die portable EXE, deren Pfad der Starter übergibt; Entwürfe und Vorab-Versionen werden ignoriert.
- **Skin & Umhang** (`skin/SkinChanger`): nur `api.minecraftservices.com/minecraft/profile` (wie der offizielle Launcher), Zugriffstoken nur im Authorization-Header, nie im Log. Hochgeladen werden nur PNGs mit 64×64 oder 64×32 Pixeln bis 24 KB; Umhang-IDs werden geprüft.
- **Lumora-Module im Spiel**: Fadenkreuz, Fullbright, Scoreboard und Tab-Ping ändern nur die eigene Anzeige. Toggle Sprint hält nur die eigene Sprint- bzw. Schleichtaste gedrückt, wie es der Spieler auch selbst könnte. Fadenkreuz, Toggle Sprint und Fullbright sind beim ersten Start aus. Fullbright stellt die eigene Helligkeit beim Ausschalten und beim Beenden wieder her.
- **Server- und Weltliste** (`lumora-core/browser`): der Ping (Server List Ping, wie im Vanilla-Menü) geht nur an Server, die in der eigenen Liste stehen oder direkt eingegeben werden, kein Dienst dazwischen; Antworten sind auf 1 MB, Icons auf 512 KB begrenzt. `servers.dat` ist dieselbe Datei wie im Vanilla-Menü: geschrieben wird über eine temporäre Datei, die alte bleibt als `servers.dat_old`, unbekannte Einträge und versteckte (Quick-Play-)Server bleiben erhalten, eine beschädigte Datei wird nicht überschrieben, solange nichts gespeichert wird. Welten löschen nur nach Bestätigung und nur Ordner direkt unter `saves/` (keine Links, kein `..`). Verbinden, Welt starten und Welt erstellen laufen über die Minecraft-eigenen Abläufe der jeweiligen Version.
- **Grafik-Profile** (`perf/GraphicsPresets`) ändern in `options.txt` nur Schlüssel, die schon darin stehen, und sichern das Original einmal als `options.txt.lumora-backup`.
- **Lumora Social** meldet das Konto wie ein Online-Minecraft-Server an (Mojang `join` / `hasJoined`). Das Token geht nur an `sessionserver.mojang.com`; das Spiel bekommt vom Launcher nur die Server-Adresse (`-Dlumora.social.server`), nie ein Token. `ws://` ist nur im lokalen Netz erlaubt, sonst `wss://`. Details in [SOCIAL.md](SOCIAL.md).

## Lumora Social (Freunde, Gruppen, Chat)

```
 Launcher (SocialController/SocialPage)        Spiel (LumoraSocial + SocialView in lumora-core,
   │  SocialClient (lumora-social)                    SocialScreen je Version)
   │                                                  │  SocialClient (lumora-social, in der Mod gebündelt)
   └──────── WebSocket (wss:// bzw. ws:// im LAN) ───┴──► lumora-social-server (Hub, Store, eigener WebSocket-Server)
                                                                   │
   Mojang session/minecraft/join ◄── Client          Server ──► Mojang session/minecraft/hasJoined
```

- `lumora-social`: Datenmodell (Profil, Status, Freund, Anfrage, Gruppe, Rolle, Mitglied, Nachricht, Unterhaltung),
  `Rules` (Grenzen, Textbereinigung, Symbole, Farben), `Endpoint` (Adressprüfung), `SocialClient` (Verbindung,
  Anmeldung, Neuverbinden, lokaler Zustand, Ereignisse), `MojangSession`, `SkinFaces`. Java 21, gegen Gson 2.2.4
  kompiliert wie `lumora-core`.
- `lumora-social-server`: `WsServer` (RFC 6455, Grenzen, Ping), `Hub` (eine Sperre für den ganzen Zustand, alle
  Anfragen und Rechte), `Store` (`state.json` atomar, Nachrichten als JSON-Zeilen), `MojangVerifier`.
- Spiel: `LumoraCore` startet `LumoraSocial` beim Client-Start (Taste U), aktualisiert den Status alle 20 Ticks und
  zeigt Lumora-Benachrichtigungen. `SocialView` zeichnet mit `HudGraphics` und bekommt Eingaben als GLFW-Codes, dadurch
  ist die ganze Oberfläche versionsunabhängig. Pro Version: `SocialScreen` (Adapter), `McPlatform.session()` /
  `openSocial()`, Menü-Buttons.

## Der Lumora-Mod (client/mc26.1.2)

| Klasse | Aufgabe |
|---|---|
| `LumoraClient` | Einstieg (Lumora-Loader-Einstiegspunkt). Hängt sich über die eigenen Plattform-Hooks ein: Titelbildschirm-Button, Protokoll-Label im Multiplayer-Menü, Tastenbelegung F9, Lifecycle |
| `gui.LumoraHubScreen` | Verbindet den Hub (`lumora-core/hub/HubView`, in allen Versionen gleich) über `HubHost` mit `lumora-client.json` (Felder per Name), den Bildschirmen der Version (HUD-Editor, Einstellungen, Titelbildschirm) und den Systemdaten (Renderer, GPU, Protokoll, Benchmark, Diagnose) |
| `integration.RuntimeInfo` | Statusabfrage über `RenderSystem.tryGetDevice()`; Sodium und ViaFabricPlus gibt es ohne Mod-Loader nicht, sie gelten als „nicht geladen“ |
| `perf.FrameStats` + `mixin.MinecraftFrameMixin` | Frametime-Messung pro Frame (`Minecraft#runTick`, nur lesend), Auswertung: Ø-FPS, 1 %/0,1 % Lows, p50/p95/p99 |
| `diagnostics.Diagnostics` / `SmokeTest` | JSON-Berichte. Automatischer Starttest mit `-Dlumora.smokeTest` |
| `config.LumoraConfig` | `config/lumora-client.json`, validiert, Backup bei Korruption |

Bestehende Minecraft-Menüs werden nicht ersetzt. Es kommen nur Widgets über den eigenen Hook nach `Screen.init` hinzu.

## Client-Framework: Lumora-API

```
Addon-JAR (lumora-addon.json; in addons/)       z. B. LumoraVoice, LabyVoice,
                                                 examples/session-timer-addon
   │ kompiliert nur gegen
   ▼
lumora-api   (Java 21, keine Abhängigkeiten)        Schnittstellen, Events, HudWidget, Settings, HudGraphics
   ▲ implementiert von
lumora-core  (Java 21, Gson von Minecraft)          LumoraCore, AddonManager, JarAddonLoader,
   │                                                 SimpleEventBus, WidgetRegistryImpl,
   │                                                 LayoutStore/HudLayout, HudRenderer, HudEditorModel,
   │                                                 KeybindManager, TaskService, NotificationCenter
   │ Platform-Schnittstelle
   ▼
client/mc<version>/…/api   (je Minecraft-Version)   McPlatform, McGameContext, McHudGraphics, LumoraRuntime,
                                                     Bildschirme HudEditorScreen,
                                                     SettingsScreen, Hub-Host (LumoraHubScreen)
```

- `lumora-api` und `lumora-core` werden als Klassen in jede `lumora-client-mc<version>.jar` kopiert (Konfiguration
  `bundled`), nicht verschachtelt. Addons finden `dev.lumora.api.*` zur Laufzeit über den Klassenlader des Lumora Loaders (`GameClassLoader`).
- **Addon-Loader** (`lumora-core/.../addon/JarAddonLoader`): Lumora lädt Addon-JARs aus `.minecraft/addons` selbst,
  ohne Mod-Loader.
  - **Beim Start** wird nur die `lumora-addon.json` gelesen (höchstens 64 KB, JAR höchstens 128 MB). Die Einstiegsklasse lädt erst `AddonManager.initialize`, nachdem API-Version, Minecraft-Version und Abhängigkeiten geprüft sind.
  - **Class-Loader:** Jede JAR hat einen eigenen `AddonClassLoader`. Die Reihenfolge ist Spiel (`GameClassLoader`) → eigene JAR → JARs der Addons aus `depends`/`optional`.
  - **Gesperrte Pakete:** Klassen in den Paketen von Java, Minecraft, Fabric, Mixin, LWJGL und Lumora darf ein Addon nicht definieren.
  - **Ressourcen** kommen zuerst aus der eigenen JAR. Sprachdateien der JAR liefert `AddonTranslations` hinter den Texten des Spiels.
  - **Beim Beenden** werden die JARs geschlossen, unter Windows sind sie danach nicht mehr gesperrt.
  - **Ordner:** Den Pfad gibt `Platform.addonsDir()` vor. Jede der 16 Versionen gibt `<Spielordner>/addons` zurück (über `LumoraLoader`), im Hub öffnet ihn der Knopf „Addon-Ordner“.
- **Trennung**: Addons sehen nur `lumora-api`. Der Core enthält keine Minecraft-Klasse und ist mit einer
  `FakePlatform` vollständig unit-getestet (`lumora-core/src/test`). Was sich je Version unterscheidet (Renderer,
  Tasten, Fensterhandle, Hooks), steckt in den Adaptern unter `client/mc<version>/…/api`.
- **Lebenszyklus**: Spiel gestartet (Lifecycle-Hook) → Kandidaten (eingebautes Addon `lumora` mit den 9 HUD-Widgets +
  die JARs in `addons/` über `JarAddonLoader`) → `AddonManager.load` (Validierung, Abhängigkeiten, Zyklen, Reihenfolge) →
  `initializeAll` → `enableAll` → `ClientStartedEvent`. Beim Beenden: `ClientStoppingEvent`, Addons in umgekehrter
  Reihenfolge aus, offene Dateien sofort schreiben, Threads stoppen.
- **Besitz**: Jede Registrierung eines Addons (Listener, Widget, Keybind, Timer, Hintergrundaufgabe) landet in
  seinem `RegistrationTracker` und wird beim Ausschalten in umgekehrter Reihenfolge entfernt – auch wenn das Addon in
  `onDisable` eine Ausnahme wirft. Fehler eines Addons/Widgets/Listeners werden isoliert (Addon `FAILED`, Widget aus,
  Listener nach 20 Fehlern entfernt).
- **Threads**: alles auf dem Client-Thread; Dateien schreibt ein eigener IO-Thread (`Lumora-IO`, Reihenfolge
  garantiert), Hintergrundarbeit der Addons ein Pool aus 2 Daemon-Threads mit begrenzter Warteschlange.
- **Rendering**: eine HUD-Ebene (`lumora:hud`) zeichnet Zoom-Rand, Widgets (Reihenfolge nach Ebene, zwischengespeichert),
  Benachrichtigungen und die Menü-Überblendung; Benachrichtigungen zusätzlich über jedem Menü. Pro Widget und Frame:
  eine Größenabfrage, eine Transformation, ein `render` – keine Suche, keine Datei, keine Allokation.
- Details: [API_REFERENCE.md](API_REFERENCE.md), [HUD_EDITOR.md](HUD_EDITOR.md), [CREATE_ADDON.md](CREATE_ADDON.md).

## Erweiterbarkeit: weitere Minecraft-Versionen

1. Neues Modul `client/mc<version>` als Kopie der nächstneueren Version (Loom nur zum Kompilieren: ohne Mappings für
   26.x, Mojang-Mappings für 1.14.4–1.21.11 mit `remapJar.targetNamespace = named`, Legacy-Intermediary und Yarn für
   1.8.9/1.12.2, deren Namensliste als `lumora/mappings.tiny` in die JAR kommt), danach die API-Unterschiede anpassen. Hilfen in `.t/`: Port-Skripte je Versionssprung
   und `check-mixins.pl` (prüft alle Mixin-Ziele gegen das echte Minecraft-JAR, bevor das Spiel startet).
2. Versionen ohne `GuiGraphics` (bis 1.19.4) bekommen eine kleine Kompatibilitätsschicht im Paket
   `dev.lumora.client.compat`; für 1.8.9/1.12.2 bildet sie Screen, Widgets, Text und Grafik nach, sodass die
   Lumora-Bildschirme weitgehend unverändert bleiben.
3. Neues Profil in `profiles.json` mit eigenen Artefakten und Hashes (`javaComponent` setzen, wenn Minecraft älter
   als Java 21 ist), Modul in `lumoraClientVersions` (settings.gradle.kts); `launcher-app` bündelt es.

`launcher-core` unterstützt beide Startformate: `arguments` (ab 1.13) und `minecraftArguments` mit extrahierten
Natives (bis 1.12.2).
