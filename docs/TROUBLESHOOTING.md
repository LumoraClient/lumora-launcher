# Fehlerbehebung

Erste Anlaufstellen:
- Launcher-Reiter **Diagnose** → *Systemprüfung*, *Log-Ordner öffnen*, *Spiel-Diagnose öffnen*
- `%APPDATA%\LumoraClient\logs\launcher.log` (Launcher)
- `%APPDATA%\LumoraClient\instances\<profil>\logs\lumora-game-output.log` und `latest.log` (Spiel)
- `%APPDATA%\LumoraClient\instances\<profil>\crash-reports\` (Absturzberichte)
- `%APPDATA%\LumoraClient\diagnostics\game-<profil>-latest.json` (Renderer, GPU, Mods, Protokoll)

Alle Logs werden vor dem Schreiben geschwärzt (Tokens, Bearer-Header, JWTs). Sie können für Support-Anfragen
weitergegeben werden. Bitte trotzdem vorher kurz prüfen.

## Start und Grafik

| Symptom | Ursache | Lösung |
|---|---|---|
| „OpenGL nicht verfügbar“ (GLFW 65542) | kein Hersteller-Grafiktreiber installiert | Treiber des GPU-Herstellers installieren |
| `OutOfMemoryError` | zu wenig Heap | *Einstellungen → Max. Arbeitsspeicher* erhöhen |
| „Inkompatible Mod-Kombination“ vor dem Start | Lockfile-Update oder manuell in `mods` gelegte JARs verletzen `depends`/`breaks` | genannte Mod entfernen bzw. Erweiterung abwählen. Die Meldung nennt Mod und Regel |
| „Das Profil … läuft bereits“ | Instanz ist gesperrt (zweites Launcher-Fenster oder hängender Prozess) | Spiel beenden. Die Sperre verschwindet mit dem Prozess |
| „Bundled component … is missing or corrupt“ | Installation beschädigt oder verändert | Launcher neu installieren bzw. entpacken |

### Bekannte, harmlose Log-Meldungen

- `Realms authentication error … Failed to parse into SignedJWT: 0`: tritt nur im Demo-/Smoke-Test-Modus
  ohne Konto auf.

## Abstürze

Stürzt Minecraft ab, zeigt der Launcher sofort eine Zusammenfassung (Beschreibung, Ausnahme, wahrscheinliche Ursache,
Tipp) und sammelt alle Berichte auf der Seite **Abstürze**:

- **Wahrscheinliche Ursache**: Der Launcher vergleicht den Stacktrace und die im Bericht genannten Mixin-Konfigurationen
  mit den Mods im `mods`-Ordner. Das ist ein Hinweis, kein Beweis. Eigene Mods lassen sich direkt deaktivieren
  (Umbenennen in `.jar.disabled`, nichts wird gelöscht), Erweiterungen ausschalten, bei Kern-Mods hilft „Reparieren & starten“.
- **Für Support kopieren**: Bericht in die Zwischenablage, ohne Tokens, Benutzerordner und Windows-Benutzernamen.
- **Diagnosepaket**: ZIP mit Crash-Report, Spiel- und Launcher-Log, Modliste und Systemprüfung, ebenso anonymisiert.
- Endet das Spiel mit Fehlercode, aber ohne Crash-Report von Minecraft (z. B. Fehler beim Laden der Mods), legt der
  Launcher selbst `crash-reports/lumora-exit-*.txt` mit dem Ende der Spielausgabe an.

## Anmeldung

| Meldung | Bedeutung | Lösung |
|---|---|---|
| Browser zeigt „invalid_request: The provided value for the input parameter 'redirect_uri' is not valid“ | In der App-Registrierung fehlt die Umleitungs-URI `http://localhost` (Plattform *Mobile und Desktopanwendungen*) | URI eintragen, oder im Launcher „Mit Code anmelden“ wählen |
| Browser öffnet sich nicht | kein Standardbrowser oder blockiert | im Anmeldefenster „Browser erneut öffnen“ oder „Mit Code anmelden“ |
| „Es ist keine Azure-App-ID … konfiguriert“ | Client-ID fehlt | siehe [BUILD.md](BUILD.md#microsoft-anmeldung-einrichten-einmalig-für-verteiler) |
| „… nicht akzeptiert (AADSTS700016 / invalid_client)“ | Client-ID existiert nicht oder ist nicht für persönliche Konten freigegeben | App-Registrierung prüfen: Kontotyp *Persönliche Microsoft-Konten*, *Öffentliche Clientflows* = Ja |
| „Microsoft meldet einen vorübergehenden Fehler (AADSTS70012)“ | Microsoft antwortet so auch bei **unbekannten** Client-IDs (beobachtet am 2026-10-15) | Client-ID prüfen. Bei korrekter ID später erneut versuchen |
| „Minecraft Services hat die Anmeldung abgelehnt (HTTP 403)“ | Azure-App nicht für die Minecraft-API freigeschaltet („Invalid app registration, see https://aka.ms/AppRegInfo“) | Freischaltung laut aka.ms/AppRegInfo beantragen |
| „… kein Xbox-Profil“ (XErr 2148916233) | Microsoft-Konto ohne Xbox-Profil | einmal auf xbox.com anmelden |
| „Kinderkonto“ (XErr 2148916238) | Konto unter 18 ohne Familienzuordnung | Erwachsener fügt Konto einer Microsoft-Familie hinzu |
| „… keine Lizenz für Minecraft: Java Edition“ | Konto besitzt das Spiel nicht | richtiges Konto verwenden |
| „Für dieses Konto existiert kein Minecraft-Profil“ | Spielername noch nicht festgelegt | im offiziellen Launcher/minecraft.net festlegen |
| „Die gespeicherte Anmeldung ist abgelaufen“ | Refresh-Token ungültig (z. B. Passwortänderung) | erneut anmelden. Der Launcher fragt automatisch |

Die Sitzung wird unter Windows per DPAPI an den Windows-Benutzer gebunden. Nach einem Benutzerwechsel oder
dem Kopieren des Ordners auf einen anderen PC ist eine neue Anmeldung nötig. Unter Linux/macOS ist die
Datei nur durch Dateirechte (0600) geschützt, nicht durch den System-Schlüsselbund.

## Downloads

| Symptom | Lösung |
|---|---|
| „Checksum mismatch …“ | Datei wurde unterwegs verändert (Proxy, Virenscanner, defekter Cache). Der Launcher versucht es erneut. Bleibt es dabei: Proxy/Virenscanner prüfen. Die Datei wird nie ungeprüft verwendet |
| „Untrusted download source refused“ | ein Manifest verweist auf einen nicht freigegebenen Host. Das ist bewusst gesperrt |
| „N of M downloads failed“ | Netzwerkproblem. Erneut auf *Spielen* klicken: Bereits geprüfte Dateien bleiben erhalten, nur der Rest wird geladen |
| Beschädigte Installation vermutet | *Diagnose → Installation reparieren* (prüft alle Dateien per Hash) |

## Windows-spezifisch

- **SmartScreen** („Der Computer wurde durch Windows geschützt“): Die EXE ist **nicht code-signiert**.
  Prüfsumme mit `SHA256SUMS.txt` vergleichen, dann *Weitere Informationen → Trotzdem ausführen*.
  Für eine öffentliche Verteilung siehe [RELEASE.md](RELEASE.md#code-signierung).
- **„Current working directory has a path longer than allowed for a Win32 working directory“**: Der
  jpackage-Starter läuft nicht aus Ordnern mit Pfaden über 260 Zeichen. Den Launcher in einen kürzeren
  Pfad entpacken bzw. installieren (Standard des Installers: `%LOCALAPPDATA%\Programs\LumoraClient`).
- Einstellungsdatei beschädigt: Der Launcher sichert sie als `launcher.json.corrupt-<Zeit>`, startet mit
  Standardwerten und meldet das.

## Oberfläche im Spiel (Lumora-Design)

Einstellungen in `<Lumora-Ordner>/config/lumora-client.json` (`data/shared`, `data/<version>` oder die eigene Instanz):

| Option | Standard | Wirkung |
|---|---|---|
| `customMainMenu` | `true` | Lumora-Hauptmenü statt Vanilla-Titelbildschirm (im Menü auch per „Klassisch“ bis zum Neustart abschaltbar) |
| `menuBackground` | `true` | Lumora-Landschaft statt Minecraft-Panorama hinter allen Menüs |
| `menuShaders` | `"auto"` | GLSL-Effekte (Polarlicht, Glas-Panels, Leuchten). `auto`/`on` = an (alle Versionen), `off` = aus |
| `menuTransitions` | `"auto"` | Weiche Überblendung (ca. 0,2 s) beim Wechsel zwischen Menüs. `auto`/`on` = an (alle Versionen), `off` = aus |
| `swingAnimation` | `"smooth"` | Schlaganimation der Hand in der Ego-Perspektive (portiert aus LiquidBounce, rein kosmetisch): `vanilla`, `smooth`, `spin`, `slap`. Auch im Lumora-Hub umschaltbar |
| `transitionStyle` / `transitionMillis` / `transitionInGame` | `"fade"` / `220` / `false` | Menü-Übergang: `fade` (Überblenden), `slide` (Gleiten) oder `off`; Dauer 80–1000 ms; `true` blendet auch beim Öffnen/Schließen von Menüs im Spiel (ESC, Inventar, Chat). Alles auch im Hub unter „Einstellungen“ |
| `customLoadingScreen` | `true` | Lumora-Ladebildschirm statt „Mojang Studios“ (Start, F3+T, Ressourcenpaket-Wechsel) |
| `lumoraFont` | `false` | Schrift Inter (eingebautes Paket „Lumora Schrift“) statt der Minecraft-Schrift. Fehlende Zeichen kommen weiter aus der Minecraft-Schrift |
| `zoomEnabled` | `true` | Zoom an/aus (Taste **C**, änderbar unter *Optionen → Steuerung → Lumora Client*) |
| `zoomFactor` | `4.0` | Zoomstärke 1,5–50 (im Hub 2/3/4/6/8/10×, per Mausrad während des Zooms bis 50×) |
| `zoomMode` | `"hold"` | `hold` = zoomen, solange die Taste gedrückt ist; `toggle` = Taste schaltet um |
| `zoomSmooth` | `true` | Weiches Hinein- und Herauszoomen statt Sprung |
| `zoomScroll` | `true` | Mausrad ändert während des Zooms die Stärke (statt des Hotbar-Platzes) |
| `zoomSensitivity` | `true` | Langsamere Maus beim Zoomen, damit das Zielen ruhig bleibt |
| `zoomCinematic` | `false` | Filmische Kamera (weich nachziehend), nur während des Zooms |
| `zoomVignette` | `true` | Dunkler Rand während des Zooms |
| `pauseMenuButton` | `true` | Button „✦ Lumora“ oben links im ESC-Menü, öffnet den Lumora-Hub |

Das Theme für alle Vanilla-Menüs (Buttons, Regler, Textfelder, Tabs, Listen) ist das eingebaute Ressourcenpaket
**„Lumora Theme“** – abschaltbar unter *Optionen → Ressourcenpakete*.

Kompiliert ein Shader nicht (z. B. Treiberproblem), steht im Spiel-Log
`Lumora shader … could not be compiled - using plain menu rendering` und die Menüs werden ohne Effekte gezeichnet.

## Mod-Hub und HUD

- **Rechte Umschalttaste** öffnet im Spiel den Lumora-Hub (änderbar unter *Optionen → Steuerung → Lumora Client*).
  Im Hauptmenü und im ESC-Menü führt der Button „✦ Lumora“ dorthin.
- HUD-Widgets (FPS, CPS, Koordinaten, Tastenanzeige, Ping, Uhrzeit, Tempo, Rüstung, Server und die Widgets von
  Addons) werden im Tab „Module“ ein- und ausgeschaltet. „HUD bearbeiten“ öffnet den Editor, Bedienung siehe
  [HUD_EDITOR.md](HUD_EDITOR.md).
- **Addons** (Tab „Addons“): Zustand, an/aus, ⚙ Einstellungen und Tasten. Steht dort „Fehler“, nennt die zweite
  Zeile den Grund (z. B. fehlende Abhängigkeit, falsche Minecraft-Version, Ausnahme beim Start); Details im Spiel-Log
  unter `Addon <id>`. Ein Widget, das beim Zeichnen abstürzt, wird ausgeschaltet (Hinweis oben rechts, ⚠ im Editor);
  wieder Einschalten versucht es erneut.
- **Zoom** (Taste **C**): Der Tab „Zoom“ im Hub stellt Stärke, Halten/Umschalten, weiche Animation, Mausrad,
  langsamere Maus, filmische Kamera und dunklen Rand ein. Geändert wird nur das eigene Sichtfeld (wie bei OptiFine).
  C ist in Vanilla auch „Schnellleiste speichern“ (Kreativmodus, C + Zahlentaste); bei Bedarf umbelegen.
- Gespeichert wird in `<Lumora-Ordner>/config/lumora/` (gemeinsam: `data/shared`, sonst `data/<version>` bzw. die eigene Instanz) (`hud-layout.json`, `keybinds.json`, `addons.json`,
  `addons/<id>.json`). Löschen setzt den jeweiligen Teil zurück; eine kaputte Datei wird als `*.corrupt-<zeit>`
  gesichert und durch Standardwerte ersetzt. Eine alte `config/lumora-hud.json` wird beim ersten Start übernommen.
- Die Anzeigen nutzen nur Daten, die der Client ohnehin hat. Es wird nichts an den Server gesendet oder verändert.

## Ressourcenpakete

- Die eingebauten Pakete lädt der Launcher je Version von Modrinth (SHA-512) nach `instances/<version>/resourcepacks` (nur eigene Instanzen; den Standard-Ordner `.minecraft` lässt er unverändert)
  und aktiviert sie einmalig in `options.txt`. Schaltest du eines im Spiel aus, bleibt es aus. Auf der Mods-Seite
  des Launchers kannst du jedes ganz abwählen (Datei wird dann entfernt).
- Fresh Animations braucht EMF + ETF, Dramatic Skys braucht Nuit – diese Mods kommen automatisch mit.
- **Fresh Food fehlt in 1.21.11 absichtlich**: seine Shader für 1.21.9–1.21.11 lassen sich dort nicht kompilieren,
  Minecraft würde daraufhin *alle* Ressourcenpakete abschalten.
- Ältere, von Hand kopierte Versionen derselben Pakete bleiben im Ordner liegen, werden aber in `options.txt`
  durch die neue Version ersetzt.
