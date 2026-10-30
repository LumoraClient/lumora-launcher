# LabyVoice auf der Lumora-API

LabyVoice (`../LabyVoice-26.1.2-SRC-0.7.1`, vorher 0.7.0 und 0.6.0) ist das erste echte Addon der Lumora-API. Dieses Dokument
beschreibt den Ausgangszustand, die Zuordnung zur API, die Änderungen und was bewusst unverändert blieb.

> **Nachfolger:** LabyVoice wird von **LumoraVoice 0.8.0** (`../LumoraVoice-SRC-0.8.0`) abgelöst, siehe
> [unten](#nachfolger-lumoravoice-080). Die folgenden Abschnitte beschreiben den Stand von LabyVoice 0.7.x.

## Nachfolger: LumoraVoice 0.8.0

LumoraVoice ist **eine** Addon-JAR für **alle 18 Profile** (1.8.9 bis 26.3). Sie enthält keine Minecraft-,
Fabric-API- oder Mixin-Klassen; alles Versionsabhängige liefert die **Lumora-API 1.1**, die jede Begleit-Mod
`client/mc<version>` implementiert ([API_REFERENCE.md](API_REFERENCE.md)).

- `lumora-addon.json`: `"api": "1.1.0"`, `"minecraft": ["*"]`, `"depends": { "lumora": ">=1.1.0" }`, Berechtigungen
  `hud`, `keybinds`, `notifications`, `game`, `tasks`, `screens`, `players`, `session`, `network`, `nametags`.
- `fabric.mod.json`: `"depends": { …, "lumora": "*" }` (anders als LabyVoice läuft LumoraVoice nur mit Lumora) und
  `"breaks": { "labyvoice": "*" }`: LabyVoice und LumoraVoice schließen sich aus, die LabyVoice-JAR muss aus dem
  `mods`-Ordner.
- **Einstellungen:** Gibt es beim ersten Start noch keine `config/lumoravoice.json`, aber eine `config/labyvoice.json`,
  wird diese übernommen (kopiert, Log „Settings taken over from labyvoice.json“). Die alte Datei bleibt liegen.

Was aus den versionsgebundenen Teilen von LabyVoice geworden ist:

| LabyVoice 0.7.x | LumoraVoice 0.8.0 über die Lumora-API 1.1 |
|---|---|
| Fabric-Mod nur für 26.1.2 (Loom, Java 25) | eine JAR, Java 21, alle Profile |
| `VoiceScreen` (Minecraft-Screen) | `AddonView`-Menüs über `AddonContext.screens()`; Hauptmenü per `registerMenu` (▶ im Lumora-Hub unter Addons) |
| Minecraft-Tasten (G, V, M, N, B, H, …) | Lumora-Keybinds (`AddonContext.keybinds()`); belegte Standardtasten starten unbelegt und lassen sich unter ⚙ setzen |
| Nametag-/Tab-Mixins | `AddonContext.nameTags()` mit eigener Bitmap-Schrift `assets/lumoravoice/font/nametags.json`; auf 1.8.9/1.12.2 Text (♪/×) |
| HUD-Widget `labyvoice:voice` | HUD-Widget `lumoravoice:voice` |
| `config/labyvoice.json` | `config/lumoravoice.json` über `AddonContext.configDirectory()` |

Außerdem nutzt LumoraVoice `world()` (Spielerpositionen, Sichtlinie, Tab-Liste), `session()` (Mojang-Join für die
Anmeldung beim Sprachdienst), `channels()` (Plugin-Kanäle von Simple Voice Chat) und `modLoaded("voicechat")`.

Grenzen laut LumoraVoice-README: Auf 1.8.9 und 1.12.2 nur JavaSound (kein OpenAL), Symbol am Namen als Text, kein
Simple Voice Chat (Kanalname `voicechat:request_secret` länger als 20 Zeichen, siehe
[COMPATIBILITY.md](COMPATIBILITY.md#lumora-api-11-je-version)). Ist die Simple-Voice-Chat-Mod installiert, überlässt
LumoraVoice ihr die SVC-Kanäle und nutzt Laby Voice.

**Geprüft:** Smoke-Test LumoraVoice 0.8.0 mit Lumora-API 1.1.0 auf allen 18 Profilen bestanden (Addon aktiv, Widget
`lumoravoice:voice` registriert/verschoben/gespeichert, beide LumoraVoice-Menüs über `AddonScreens` geöffnet,
Sprach-HUD in der Demo-Welt, Namensschild-Mixins angewendet; auf 26.1.2 zusätzlich Vorschau-Spieler mit Symbolen und
Kanalzeile), Nachweis `docs/test-evidence/smoke-summary-lumoravoice-2026-10-27.txt`. **Nicht geprüft:** ein echtes
Gespräch auf einem Server (Laby Voice oder Simple Voice Chat).

Der Hinweis zum Laby-Voice-Adapter unter [Grenzen und Hinweise](#grenzen-und-hinweise) gilt für LumoraVoice genauso.

## Ausgangszustand (0.6.0)

- Eigenständige **Fabric-Mod** für Minecraft 26.1.2 (Loom 1.18.2, Java 25), **keine LabyMod-Klassen**: Es gab
  nichts von LabyMod zu ersetzen. „Laby“ bezieht sich auf den eigenen, unabhängig geschriebenen Adapter für den
  Laby-Voice-Dienst; daneben gibt es einen Simple-Voice-Chat-Client.
- Ein HUD-Element (`VoiceHud.draw`, über Fabrics `HudElementRegistry` vor dem Chat), Position über vier Ecken
  (`hudCorner`), Größe `hudScale`, Deckkraft `hudOpacity` und neun Anzeigeoptionen in `VoiceConfig`.
- Weitere Anzeigen, die keine HUD-Widgets sind: Lautsprecher am Nametag (`NameIconMixin`, `VoiceRenderStateMixin`),
  Symbol in der Tab-Liste (`TabIconMixin`), Pegelwelle über dem Namen.
- 8 eigene Minecraft-Tasten (G, V, M, N, B, H, Flüstern, Blockieren), eigenes Einstellungsmenü `VoiceScreen`.

## Zuordnung

| Bestehende LabyVoice-Komponente | Bisherige Funktion | Neue Lumora-API | Erforderliche Änderungen |
|---|---|---|---|
| `ui/VoiceHud.draw` (Fabric-HUD-Element) | Status + Sprecherliste in einer Ecke zeichnen, Inhalt jedes Frame neu berechnet | `HudWidget` `labyvoice:voice` (`lumora/VoiceHudWidget`), registriert über `AddonContext.widgets()` | Aufgeteilt in `update()` (einmal pro Tick, aus `LabyVoice.tick`), `content()`, `drawContent()` (bei 0,0) und `draw()` (Eckplatzierung ohne Lumora). Mit Lumora ist die Eckanzeige aus (`managedExternally`). |
| `VoiceConfig.hudCorner`, `hudScale` | Ecke, Größe | Position/Größe/Ebene/Profil des Widgets (`hud-layout.json`) | `defaultPlacement()` übernimmt beim ersten Start Ecke und Größe. Ohne Lumora gelten die Felder weiter. |
| `hudOpacity`, `hudOnlySpeaking`, `showSpeakers`, `showIndicatorText`, `showDistance`, `showAudioWaves`, `coloredNames`, `hideInactive`, `blockHint`, `maxSpeakers` | Anzeigeoptionen | `SettingsGroup` des Widgets, **gebunden** (`Setting.bind`) an die `VoiceConfig`-Felder | Keine Kopie im Lumora-Layout; geänderte Werte schreibt das Addon höchstens 1×/s über `VoiceConfig.saveAsync` (neu, schreibt im Lumora-Worker). |
| `showHud` + Taste H | Anzeige ein/aus | `HudWidget.isVisible` | unverändert: H blendet aus, der Editor zeigt dann einen Platzhalter |
| `VoiceScreen` → Anzeige → „HUD-Position“, „HUD-Größe“ | Ecke wählen, Größe | `LumoraAPI.get().hudEditor().open()` | mit Lumora ersetzt durch „Position und Größe → HUD-Editor“ (über den Hook `VoiceHud.openExternalEditor`, damit `VoiceScreen` keine Lumora-Klasse kennt) |
| `LabyVoice.onInitializeClient` | Fabric-Einstieg | `LumoraAddon` (`lumora/LabyVoiceLumoraAddon`) + `lumora-addon.json` | Fabric-Einstieg bleibt; das Addon wird nur vom Lumora-Client geladen |
| Event-Anbindung (Fabric `END_CLIENT_TICK`, `CLIENT_STOPPING`, `ScreenEvents`) | Voice-Logik | – | bleibt bei Fabric: Die Voice-Logik muss auch ohne Lumora laufen |
| Tasten (`KeyMappingHelper`) | G/V/M/N/B/H/… | `KeybindRegistry` wäre möglich | bleiben Minecraft-Tasten: sie müssen ohne Lumora funktionieren und erscheinen so unter Optionen → Steuerung |
| Nametag-/Tab-Symbole (Mixins) | Welt-/Tab-Anzeige | – | keine HUD-Widgets, unverändert |
| Audio, Protokolle, Kanäle, Blockliste | Sprachfunktionen | – | unverändert |

## Ergebnis

- **Widgets:** 1 (`labyvoice:voice`, Kategorie „Sprache“, Name „LabyVoice“). LabyVoice hat nur dieses eine
  HUD-Element; Nametag- und Tab-Symbole sind Welt- bzw. Listenanzeigen.
- Geprüft im echten Spiel (Smoke-Test 26.1.2 mit LabyVoice 0.7.0 und Lumora, drei Läufe nacheinander): registriert, im Editor gelistet,
  mit der Editor-Logik verschoben und gespeichert, nach Neustart an derselben Stelle geladen
  (`LUMORA_SMOKE_LAYOUT loaded labyvoice:voice x=0.511 y=0.772`), im Spiel gezeichnet, Einstellungsbildschirm aus den
  gebundenen Optionen erzeugt.
- Erneut geprüft mit **LabyVoice 0.7.1** in der echten 26.1.2-Instanz: Smoke-Test bestanden, Addon aktiv, Widget
  verschoben/gespeichert und im Spiel gezeichnet, keine Probleme (`docs/test-evidence/smoke-26.1.2-labyvoice-0.7.1.json`).
- **Ausschalten** im Lumora-Hub (Addons → LabyVoice): Widget wird entfernt (`dispose`), Eckanzeige kehrt zurück,
  offene Änderungen werden gespeichert.
- **Ohne Lumora:** keine Lumora-Klasse wird geladen (`compileOnly`, Einstieg nur in `lumora-addon.json`,
  `fabric.mod.json` nur `suggests: lumora`). Die Unit-Tests (41) laufen ohne Lumora-Laufzeit.
- **Integrationsvariante:** LabyVoice bleibt eine separate Mod-JAR und wird als externes Addon über
  `lumora-addon.json` erkannt. Sie wird nicht in den Lumora-Client eingebaut und nicht automatisch vom Launcher
  installiert (eigene Mod im `mods`-Ordner).

## Build

```
LabyVoice/build.gradle
  repositories { maven { url = <relativ> ../lumora-client/build/repo } }   (überschreibbar: -Plumora.apiRepo=…)
  compileOnly 'dev.lumora:lumora-api:1.0.0'
  testImplementation 'dev.lumora:lumora-api:1.0.0'
```

Reihenfolge: `lumora-client> gradlew :lumora-api:publish`, dann `LabyVoice> gradlew build`. Keine zirkuläre
Abhängigkeit: LabyVoice → lumora-api; lumora-api kennt LabyVoice nicht; der Lumora-Client findet LabyVoice zur
Laufzeit über Fabric. Der Lumora-Smoke-Test schaltet LabyVoice für ein Bildschirmfoto per Reflection kurz ein (ohne
Compile-Abhängigkeit) und stellt den Wert danach wieder her.

## Grenzen und Hinweise

- LabyVoice gibt es nur für Minecraft 26.1.2 (`lumora-addon.json`: `"minecraft": ["26.1.2"]`). Auf 1.21.x lädt
  Fabric die Mod gar nicht.
- Das Widget zeichnet mit dem nativen 26.1.2-Renderer (`HudGraphics.unwrap`), damit LabyVoices eigene Schrift und
  Theme-Farben erhalten bleiben.
- Der Laby-Voice-Adapter spricht die Server von LabyMod (`voice.labymod.net`) mit einem eigenen, nicht von LabyMedia
  freigegebenen Client an. Daran hat diese Migration nichts geändert; ob das mit den Nutzungsbedingungen von LabyMod
  vereinbar ist, ist nicht geprüft (vgl. [LABYCONNECT.md](LABYCONNECT.md)). Simple Voice Chat ist davon nicht
  betroffen.
