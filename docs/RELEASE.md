# Release-Prozess

## Ablauf

1. Version in `gradle.properties` (`lumora.version`) erhöhen.
2. Falls Komponenten aktualisiert werden: `launcher-core/src/main/resources/lumora/profiles.json` anpassen.
   Version, URL, Größe und SHA-512 **aus der Modrinth-API** übernehmen, nie von Hand berechnen oder raten.
3. Prüfen:
   ```bat
   gradlew.bat clean build :launcher-core:integrationTest
   ```
4. Distribution bauen (Windows, optional mit Inno Setup):
   ```bat
   gradlew.bat :launcher-app:installerExe :launcher-app:releaseDist
   ```
5. Smoke-Test **auf einem Rechner mit GPU** für alle Versionen, aus dem entpackten ZIP:
   ```bat
   LumoraLauncher\LumoraLauncherCli.exe --smoke-test --profile 26.1.2
   LumoraLauncher\LumoraLauncherCli.exe --smoke-test --profile 1.21.11
   LumoraLauncher\LumoraLauncherCli.exe --smoke-test --profile 1.21.8
   ```
6. Manuelle Prüfungen (nicht automatisierbar), siehe Checkliste unten.
7. `build/release/` veröffentlichen: ZIP, ggf. Setup-EXE, `SHA256SUMS.txt`, `README.txt`, `licenses/`.
   Den Quellcode zum selben Commit bereitstellen (GPL-3.0).

## Inhalt von `build/release/`

```
build/release/
├── LumoraClient-<v>-windows-x64.zip     LumoraLauncher/ gepackt
├── LumoraClient-Setup-<v>.exe           nur wenn Inno Setup vorhanden war
├── LumoraLauncher/
│   ├── LumoraLauncher.exe               GUI (Doppelklick)
│   ├── LumoraLauncherCli.exe            Konsole: --smoke-test, --install-only, --system-check
│   ├── app/                             Launcher-JARs (+ LumoraLauncher.cfg)
│   ├── runtime/                         jlink-Java-25-Runtime (inkl. legal/)
│   ├── bundled/                         Lumora-Begleit-Mod + bundled-mods.json (SHA-512)
│   ├── licenses/                        GPL-3.0, Apache-2.0, THIRD_PARTY_NOTICES
│   └── README.txt
├── README.txt
├── SHA256SUMS.txt
└── licenses/
```

## Code-Signierung

Die erzeugten EXEs sind **nicht signiert**. Windows SmartScreen warnt deshalb beim ersten Start. Für eine
öffentliche Verteilung empfohlen:
- ein Authenticode-Zertifikat (OV/EV) und `signtool sign /fd SHA256 /tr <timestamp-url> /td SHA256` für
  `LumoraLauncher.exe`, `LumoraLauncherCli.exe` und die Setup-EXE, **vor** dem Erzeugen der `SHA256SUMS.txt`,
- oder das Signieren über einen CI-Dienst mit Hardware-Schlüssel.

## Manuelle Abnahme-Checkliste

- [ ] Frisches Windows-Benutzerkonto ohne Java: ZIP entpacken → `LumoraLauncher.exe` startet
- [ ] Erststart: Fortschrittsanzeige während der Downloads, danach Device-Code-Dialog
- [ ] Anmeldung mit einem Konto, das Minecraft besitzt → Spiel startet, Button zeigt „Läuft“
- [ ] Zweiter Klick auf „Spielen“ während das Spiel läuft → Hinweis statt Zweitstart
- [ ] Spiel schließen → Button wieder „Spielen“. Neustart des Launchers → Start ohne erneute Anmeldung
- [ ] Multiplayer: ViaFabricPlus-Versionsauswahl vorhanden, Lumora-Label zeigt die Version.
      Server einer älteren Version (z. B. 1.8.9 oder 1.20.1) betreten
- [ ] Titelbildschirm → „Lumora“ → Hub zeigt Renderer/GPU. F9-Benchmark in einer Welt → Toast + JSON
- [ ] Profil „OpenGL (Sodium)“: Start, Sodium-Videoeinstellungen sichtbar
- [ ] Fenstergröße ändern, Vollbild umschalten (F11), Ressourcenpakete neu laden (F3+T) in beiden Profilen
- [ ] Netzwerk während des Erststarts trennen → verständliche Fehlermeldung. Erneuter Klick setzt fort
- [ ] `launcher.json` mit Unsinn überschreiben → Launcher startet, meldet Wiederherstellung

## Teststatus dieses Standes

**0.3.13 (2026-11-05):**
- **Build:** `gradlew clean build` erfolgreich (ein Teil der Aufgaben aus dem Gradle-Cache), 429 Unit-Tests ohne Fehler (1 übersprungen). Neu: 1 für die Einstellungsseite (eine mit `advanced()` markierte Einstellung erscheint sofort, kein Schalter „Erweiterte Einstellungen“).
- **Smoke-Test** mit dem fertigen 0.3.13-Build (stumm, Test-Datenordner, Beispiel-Addon): 26.3, 1.21.11, 1.16.5 und 1.8.9 bestanden. Einstellungsseite der Angriffsanzeige in 26.3 und 1.8.9 angesehen: „Background“ (bisher erweitert) direkt sichtbar, kein Schalter am unteren Rand.
- **Nicht geprüft:** die Seiten von Verbindung, Block-Overlay und den HUD-Widgets mit den bisher versteckten Einstellungen im Spiel, ein echtes Selbst-Update von 0.3.12 auf 0.3.13.
- **Release:** `SHA256SUMS.txt.sig` mit dem Release-Schlüssel signiert und gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler.

**0.3.12 (2026-11-05):**
- **Build:** `gradlew clean build` erfolgreich (ein Teil der Aufgaben aus dem Gradle-Cache), 428 Unit-Tests ohne Fehler (1 übersprungen). Neu: 4 für den Einzel-Launcher (zweiter Start erreicht den ersten mit „zeigen“ und „nur prüfen“, ein „zeigen“ vor dem Fenster kommt später an, Schließen gibt Sperre und Port-Datei frei und ein zweiter Start wartet dann nicht, Anfragen ohne Token werden abgelehnt).
- **Launcher** (fertiges Abbild, Test-Datenordner, „Im Tray bleiben“ an): Fenster per Schließen in den Tray, zweiter Start endet nach etwa 1 s und das Fenster des ersten ist wieder sichtbar (gleiche Prozesse). Nach hartem Beenden mit liegengebliebenen `launcher.lock`/`launcher.port` startet der Launcher normal.
- **Smoke-Test** mit dem fertigen 0.3.12-Build (stumm, Test-Datenordner, Beispiel-Addon): 26.3, 1.21.11, 1.16.5 und 1.8.9 bestanden.
- **Nicht geprüft:** Start per Klick auf die Desktop-Verknüpfung (das Schließen wurde per Fensternachricht ausgelöst), Autostart mit `--tray` bei laufendem Launcher, ein echtes Selbst-Update von 0.3.11 auf 0.3.12 (ein laufender 0.3.11 kennt die Sperre noch nicht).
- **Release:** `SHA256SUMS.txt.sig` mit dem Release-Schlüssel signiert und gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler.

**0.3.11 (2026-11-05):**
- **Build:** `gradlew clean build` erfolgreich (ein Teil der Aufgaben aus dem Gradle-Cache), 424 Unit-Tests ohne Fehler (1 übersprungen). Neu: 6 für die Einstellungs-Arten der API 1.3 (Typen und konkrete Rückgabetypen, Sichtbarkeit mit fehlerhafter Bedingung, Enums, Schritte und Einheiten, Regenbogen gespeichert auch vor `chroma()`, Buttons nie gespeichert) und 6 für die Einstellungsseite (Abschnitte und Bedingungen, Klicks auf Häkchen, Knopfreihe und Button, Aufklapp-Liste mit Esc, Farbwähler mit Regenbogen und Esc, HSV-Umrechnung, Zoom-Settings an die Client-Einstellungen gebunden). Das Addon-Template baut mit der API 1.3.0.
- **Smoke-Test** mit dem fertigen 0.3.11-Build (stumm, Test-Datenordner, Beispiel-Addon): 26.3, 1.21.11, 1.16.5 und 1.8.9 bestanden. Einstellungsseite der Angriffsanzeige mit Abschnitten, Knopfreihe, Aufklapp-Liste (offen) und Farbwähler (offen) in 26.3 und 1.21.11 angesehen; vor dem Release dieselben Aufnahmen in 1.8.9, 1.16.5 und 1.21.4.
- **Nicht geprüft:** Ziehen im Farbwähler mit der echten Maus, die neue Zoom-Seite und die Seiten von Tab-Liste und Block-Overlay im Spiel, ein echtes Selbst-Update von 0.3.10 auf 0.3.11.
- **Release:** `SHA256SUMS.txt.sig` mit dem Release-Schlüssel signiert und gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler.

**0.3.10 (2026-11-05):**
- **Build:** `gradlew clean build` erfolgreich (ein Teil der Aufgaben aus dem Gradle-Cache), 412 Unit-Tests ohne Fehler (1 übersprungen); der Test der Weltliste prüft, dass es keinen Knopf „Klassisch“ mehr gibt.
- **Smoke-Test** mit dem fertigen 0.3.10-Build (stumm, Test-Datenordner, Beispiel-Addon): 26.3, 1.21.11, 1.16.5 und 1.8.9 bestanden. Mehrspieler- und Einzelspieler-Liste (26.3) sowie die Einzelspieler-Liste (1.8.9) ohne „Klassisch“ angesehen.
- **Nicht geprüft:** ein echtes Selbst-Update von 0.3.9 auf 0.3.10.
- **Release:** `SHA256SUMS.txt.sig` mit dem Release-Schlüssel signiert und gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler.

**0.3.9 (2026-11-05):**
- **Build:** `gradlew clean build` erfolgreich (ein Teil der Aufgaben aus dem Gradle-Cache), 412 Unit-Tests ohne Fehler (1 übersprungen). Neu: 4 für die Angriffsanzeige (Vorrang von Benutzen, Abbauen, Schlafen und Angriff, Ausblenden wenn bereit und volle Anzeige beim Zielen, Cooldowns, Töne einmal je Aufladen und bei abgelaufenem Cooldown, alle Stile, Positionen und Größen mit ausgeglichenem Zeichen-Stapel, Third Person und HUD-Editor ohne Anzeige).
- **Mixins:** In allen 15 Versionen mit eigener Angriffsanzeige (1.12.2 bis 26.3) liest Minecrafts Fadenkreuz- und Hotbar-Methode die Option an der umgeleiteten Stelle (per `javap` gezählt).
- **Smoke-Test** mit dem fertigen 0.3.9-Build (stumm, Test-Datenordner, Beispiel-Addon): 26.3, 1.21.11, 1.16.5 und 1.8.9 bestanden. In 26.3, 1.21.11 und 1.16.5 alle vier Stile kurz nach einem Schlag in der Welt fotografiert und angesehen. Vor dem Release zusätzlich 1.21.4 (vier Stile) und 1.12.2 (Start) bestanden.
- **Nicht geprüft:** Cooldowns, Bogen, Armbrust, Dreizack, Essen, Abbauen, Schlafen, beide Töne und die Position an der Hotbar im Spiel; ob Minecrafts eigene Anzeige im Spiel verschwindet (im Test-Datenordner ist das Fadenkreuz-Modul an); die übrigen Versionen im Spiel; ein echtes Selbst-Update von 0.3.8 auf 0.3.9.
- **Release:** `SHA256SUMS.txt.sig` mit dem Release-Schlüssel signiert und gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler.

**0.3.8 (2026-11-04):**
- **Build:** `gradlew clean build` erfolgreich (ein Teil der Aufgaben aus dem Gradle-Cache), 408 Unit-Tests ohne Fehler (1 übersprungen). Neu: 5 für die Screenshot-Bibliothek (Liste, Sortierung, Favoriten über Neustart und Umbenennen, ungültige Namen und Pfade außerhalb des Ordners, Löschen mit und ohne Papierkorb), 3 für den Screenshot-Manager (Vorschaubilder als Texturen und Freigabe beim Schließen, ohne Textur-Unterstützung, Suche und Esc), 3 für die Fadenkreuz-Vorlagen (eindeutig, mittig, Speichern, Umrandung je Pixel einmal, Farbton) und 1 für den Fadenkreuz-Editor (Reiter, Vorlage anklicken, Malen mit Spiegeln, Radieren, kleine Bildschirme).
- **Smoke-Test** mit dem fertigen 0.3.8-Build (stumm, Test-Datenordner, Beispiel-Addon): 26.3, 1.21.11, 1.16.5 und 1.8.9 bestanden. Screenshot-Manager mit Vorschaubildern und der Fadenkreuz-Editor in allen drei Reitern geöffnet und fotografiert. Vor dem Release zusätzlich 1.21.8 (Screenshot-Manager, Editor) und 1.21.4 (Screenshot-Manager) sowie der Kamera-Knopf im Haupt- und Pausenmenü (26.3) angesehen.
- **Nicht geprüft:** Umbenennen, Löschen, Papierkorb und Extern öffnen per Klick im Spiel, Malen im Pixel-Editor mit der Maus im Spiel, die übrigen Versionen im Spiel, ein echtes Selbst-Update von 0.3.7 auf 0.3.8.
- **Release:** `SHA256SUMS.txt.sig` mit dem Release-Schlüssel signiert und gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler.

**0.3.7 (2026-11-04):**
- **Build:** `gradlew clean build` erfolgreich, 396 Unit-Tests ohne Fehler (1 übersprungen), darunter 6 neue für das Ressourcenpaket „Lumora-Addons“ (Kopieren, gesperrte Namensräume, Pfade, Neu-Kopieren nur bei Änderung, kaputte JAR) und 3 für zusammengelegte HUD-Einstellungen (eine Karte je Addon, Abschnitt mit Schalter, Weitergabe an die Einstellungsseite). Die Einstellungsseite von Block-Overlay mit dem Abschnitt „HUD · Block info“ außerhalb des Spiels gerendert und angesehen; im Hub 28 statt 32 Karten.
- **LumoraVoice 0.8.1** (66 eigene Tests): Opus kodieren/dekodieren und RNNoise samt nativer DLL laufen aus der JAR allein (eigener Class-Loader ohne Spiel).
- **Smoke-Test** mit dem fertigen 0.3.7-Build (stumm, Test-Datenordner, LumoraVoice 0.8.1 und Beispiel-Addon in `addons/`): 26.3, 1.21.11, 1.16.5 und 1.8.9 bestanden, LumoraVoice jeweils aktiv, ohne Klassenfehler, beide Voice-Menüs geöffnet und fotografiert, Paket „Lumora-Addons“ geladen.
- **Nicht geprüft:** ein echtes Gespräch auf einem Server, Voice in den übrigen 12 Versionen, die zusammengelegte Einstellungsseite per Klick im Spiel, ein echtes Selbst-Update von 0.3.6 auf 0.3.7.
- **Release:** `SHA256SUMS.txt.sig` mit dem Release-Schlüssel signiert und gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler.

**0.3.6 (2026-11-03):**
- **Build:** `gradlew clean build` erfolgreich, 387 Unit-Tests ohne Fehler (1 übersprungen).
- **Oberfläche:** alle Reiter der neuen Einstellungsseite (Allgemein, Erscheinungsbild, Spiel, Speicher, Freunde & Chat, Integrationen) per `--ui-shot` geprüft.
- **Smoke-Test** mit dem fertigen 0.3.6-Build (stumm, Test-Datenordner, Beispiel-Addon): 26.3, 1.21.11 und 1.8.9 bestanden.
- **Nicht geprüft:** Suche, Tooltips und Klicks im laufenden Launcher, ein echtes Selbst-Update von 0.3.5 auf 0.3.6.
- **Release:** `SHA256SUMS.txt.sig` mit dem Release-Schlüssel signiert und gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler.

**0.3.5 (2026-11-03):**
- **Build:** `gradlew clean build` erfolgreich, 387 Unit-Tests ohne Fehler (1 übersprungen), darunter 6 neue für den Verbindungsverlauf (Logzeilen „Connecting to“, Adressen, Speichern, Abschalten, kaputte Datei).
- **Verbindungsverlauf:** Die Logzeile „Connecting to …“ in den Minecraft-JARs von 1.8.9 und 26.3 nachgesehen. Ein Smoke-Test schreibt keinen Verlauf.
- **Oberfläche:** Startseite, Konto (Allgemein, Sicherheit, Verlauf), Einstellungen und Server-Seite per `--ui-shot` mit erfundenen Testkonten geprüft, Seitenleiste mit Text und nur mit Icons. Die Microsoft-Adressen leiten zur Microsoft-Anmeldung weiter.
- **Smoke-Test** mit dem fertigen 0.3.5-Build (stumm, Test-Datenordner, Beispiel-Addon): 26.3, 1.21.11 und 1.8.9 bestanden.
- **Nicht geprüft:** ein echter Serverbeitritt im Spiel, „Beitreten“ aus dem Verlauf, Abmelden mit einem echten Konto, Umschalten der Seitenleiste per Klick, ein echtes Selbst-Update von 0.3.4 auf 0.3.5.
- **Release:** `SHA256SUMS.txt.sig` mit dem Release-Schlüssel signiert und gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler.

**0.3.4 (2026-11-03):**
- **Build:** `gradlew clean build` erfolgreich, 381 Unit-Tests ohne Fehler (1 übersprungen).
- **Smoke-Test** (stumm, Test-Datenordner, Beispiel-Addon): 26.3 mit Lunar, 1.20.4 mit Blackout und 1.8.9 mit Rounded bestanden, Hauptmenü und Hub im jeweiligen Stil auf den Screenshots. Mit dem fertigen 0.3.4-Build zusätzlich 26.3, 1.21.11 (Hyperium), 1.20.4, 1.16.5 (PvPClient) und 1.8.9 bestanden.
- **Nicht geprüft:** Liquid im Spiel (zeigt nur unter der Maus einen Hintergrund), Umschalten per Klick im echten Spiel, ein echtes Selbst-Update von 0.3.3 auf 0.3.4.
- **Release:** `SHA256SUMS.txt.sig` mit dem Release-Schlüssel signiert und gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler.

**0.3.3 (2026-11-03):**
- **Build:** `gradlew clean build` erfolgreich, 381 Unit-Tests ohne Fehler (1 übersprungen), darunter ein neuer für die Schriftauswahl im Hub.
- **Schriften:** jede Datei vor dem Einbau geprüft (TrueType, Name, alle Umlaute, Strichstärke Medium, Lizenz SIL OFL 1.1), Quelle und SHA-256 in `THIRD_PARTY_NOTICES.md`.
- **Smoke-Test** (stumm, Test-Datenordner, Beispiel-Addon): 26.3 und 1.20.4 mit Outfit, 1.16.5 mit Poppins bestanden, Schrift auf den Screenshots sichtbar, Zurückschalten auf Vanilla geprüft. Mit dem fertigen 0.3.3-Build 26.3, 1.21.11 (Manrope), 1.16.5 und 1.8.9 erneut bestanden.
- **Nicht geprüft:** Roboto, Montserrat, JetBrains Mono und Silkscreen im Spiel, Umschalten per Klick im echten Spiel, ein echtes Selbst-Update von 0.3.2 auf 0.3.3.
- **Release:** `SHA256SUMS.txt.sig` mit dem Release-Schlüssel signiert und gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler.

**0.3.2 (2026-11-03):**
- **Build:** `gradlew clean build` erfolgreich, 380 Unit-Tests ohne Fehler (1 übersprungen), darunter neue für den Hub (alle Seiten auf kleinen und großen Bildschirmen, Kategorien, Suche, Vanilla-/Lumora-Theme, Akzentfarben, neues Profil per Klick) und die neuen Einstellungen (alle Einstellungsarten, Schieberegler ziehen, Schalter, Hex-Eingabe, Esc).
- **Smoke-Test** (stumm, Test-Datenordner, Beispiel-Addon): 26.3, 1.20.4, 1.16.5, 1.12.2 und 1.8.9 mit dem neuen Hub bestanden, Hub-Screenshots (Module, Themes, Profile, im Spiel) geprüft. Mit dem fertigen 0.3.2-Build zusätzlich 26.3, 1.20.4, 1.12.2 und 1.8.9 erneut bestanden, Screenshots des Hubs und der neuen Einstellungen geprüft (1.8.9 und 1.12.2 jetzt mit übersetzten Namen). Die übrigen Versionen sind nur kompiliert und mit allen Tests gebaut.
- **Nicht geprüft:** Klicks im echten Spiel (An/Aus, Theme- und Farbwechsel, Suche, Profile, Schieberegler ziehen, Hex eingeben, Tasten belegen), Online-Server mit echtem Konto, ein echtes Selbst-Update von 0.3.1 auf 0.3.2.
- **Release:** `SHA256SUMS.txt.sig` mit dem Release-Schlüssel signiert und gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler.

**0.3.1 (2026-11-03):**
- **Build:** `gradlew clean build` erfolgreich, alle Unit-Tests ohne Fehler, darunter neue für die Zeitmessung und den Fortschritt des Welt-Ladebildschirms.
- **Smoke-Test** (stumm, Test-Datenordner, Beispiel-Addon): **alle 18 Profile** bestanden, in jedem erscheint der Lumora-Ladebildschirm beim Betreten der Demo-Welt (Logzeile „World load“ mit Phasen und Zeiten). Screenshots von 26.3, 1.20.4, 1.12.2 und 1.8.9 geprüft.
- **Nicht geprüft:** eine Welt über das echte Menü „Welt erstellen“ (der Smoke-Test legt seine Welt direkt an), Online-Server mit echtem Konto.
- **Release:** `SHA256SUMS.txt.sig` mit dem Release-Schlüssel signiert und gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler.

**0.3.0 (2026-11-03):**
- **Build:** `gradlew clean build` erfolgreich, 367 Unit-Tests ohne Fehler (1 übersprungen), darunter die neuen Tests des Lumora Loaders (Mixin-Ende-zu-Ende, Umbenennen mit ProGuard- und Tiny-Namenslisten).
- **Smoke-Test** (stumm, Test-Datenordner, Beispiel-Addon „Session Timer“ im Ordner `addons`): **alle 18 Profile** von 1.8.9 bis 26.3 bestanden, in jedem lädt das Addon. Mit dem fertigen 0.3.0-Build zusätzlich 26.1.2, 1.21.11 und 1.8.9 erneut bestanden.
- **Nicht geprüft:** Spielen auf einem Online-Server mit echtem Konto, ein echtes Selbst-Update von 0.2.2 auf 0.3.0 über GitHub.
- **Release:** `SHA256SUMS.txt.sig` mit dem Release-Schlüssel signiert und gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler.

**0.2.2 (2026-11-02):**
- **Build:** `gradlew build` erfolgreich, 371/371 Unit-Tests, darunter 6 neue für das Update-Ziel (Starter, gemerkter Pfad, eigene Kopie, Schalter aus, kein portabler Ordner).
- **Anheften:** an einem echten Fenster geprüft. `RelaunchCommand` wird gesetzt, richtig zurückgelesen und beim Beenden gelöscht.
- **Portabler Start** (Testordner über `LUMORA_PORTABLE_DIR`, Testdatenordner):
  - `LumoraClient.exe` entpackt den Launcher und schreibt `starter-path.txt`. Der Launcher setzt das Anheften auf die EXE (`STARTER`).
  - Direkter Start des entpackten `LumoraLauncher.exe` ohne den Starter: Der Launcher findet die gemerkte EXE (`RECORDED`) und setzt das Anheften darauf.
- **Nicht geprüft:** ein echtes Selbst-Update über GitHub beim direkten Start. Das geht erst, wenn es eine neuere Version als 0.2.2 gibt. Die Installation selbst ist dieselbe wie beim Start über den Starter (`LauncherUpdate.install`, Tests vorhanden).
- **Release:** `SHA256SUMS.txt.sig` mit dem Release-Schlüssel signiert und gegen `update-key.pub` geprüft.

**0.2.1 (2026-11-02):**
- **Build:** `gradlew build` erfolgreich, 365/365 Unit-Tests, darunter 10 neue für den Addon-Loader.
- **Smoke-Tests im Spiel** (stumm, Testdatenordner) bestanden für 26.3, 1.21.4 und 1.8.9. Das Beispiel-Addon „Session Timer“ lag dabei nur im Ordner `addons`, nicht in `mods`, und wurde gestartet und beim Beenden sauber ausgeschaltet.
- **Mod-Loader:** entfernt. Es gibt keine Starttests mehr für Forge, NeoForge und Quilt.
- **Release:** `SHA256SUMS.txt.sig` mit dem Release-Schlüssel signiert und gegen `update-key.pub` geprüft.

**0.2.0 (2026-11-02):**
- **Build:** `gradlew build` erfolgreich, 363/363 Unit-Tests.
- **Mod-Loader:** Starttests (`--launch-test --loader <id>`, Demo-Modus, Testdatenordner) bestanden für Forge 1.8.9, 1.12.2, 1.16.5, 1.20.1 und 26.3, NeoForge 1.20.1, 1.21.4 und 26.3 sowie Quilt 1.21.4 und 1.21.11.
- **Smoke-Tests im Spiel:** bestanden für 26.3, 1.21.4, 1.20.6, 1.20.1, 1.17.1, 1.16.5, 1.12.2 und 1.8.9.
- **Verbindungssystem:** In diesen Smoke-Tests geprüft, auf 26.3, 1.20.6 und 1.17.1 zusätzlich mit dem stillen Testserver `.t/SilentMc`. Geprüft wurden:
  - Verbindungsbildschirm mit Phasen
  - Abbruch
  - Trennungsbildschirm mit erkanntem Grund `REFUSED`
  - „Erneut verbinden“
- **Nicht geprüft:** Beitritt zu einem echten Online-Server mit Microsoft-Konto.
- **Release:** `SHA256SUMS.txt.sig` mit dem Release-Schlüssel signiert und gegen `update-key.pub` geprüft.

Ältere Stände:

Build-Datum 2026-10-27 (Lumora-API 1.1 mit LumoraVoice 0.8.0 in allen 18 Profilen; Lumora Social: Freunde, Gruppen, Chat in Launcher und allen 18 Profilen; davor: Lumora-Oberfläche in allen 18 Profilen 1.8.9–26.3, Lumora-API 1.0, HUD-Editor, LabyVoice als Addon), Stand dieses Arbeitsverzeichnisses. Ausgeführt auf: Windows 11 Pro
(10.0.26200), AMD Radeon RX Vega (Treiber 31.0.21923.11000, Vulkan 1.3.260), 16 GB RAM, JDK 25.0.3 (Corretto).

| Test | Ergebnis | Nachweis |
|---|---|---|
| `gradlew clean build` (alle Module, Kompilierung mit `-Werror` für den Launcher) | ✅ erfolgreich | Gradle-Ausgabe |
| Unit-Tests launcher-core (Konfiguration, Lockfile, Downloader/Prüfsummen/Retry, Abhängigkeitsprüfung, Startbefehl, Auth-Kette gegen Mock-Server, DPAPI-Session, Redaction, Java-Runtime, Updates, Mod-Installation, Versionsprofile, Extension-Schlüssel, Instanz-Migration vulkan/opengl → 26.1.2) | ✅ 114/114 (inkl. Lumora-Social-Serveradresse in der Konfiguration, vom Loader mitgelieferter Mods wie MixinExtras, Versionskatalog aller 18 Profile mit Java-21-Pflicht für alte Versionen, Ressourcenpaket-Aktivierung in options.txt, Mod-Browser gegen lokalen Testserver, Account-Manager, Benchmark-Verlauf, Mods-Inventar, NBT/Welten/Server, Server-Ping gegen lokalen Testserver, Spielzeit, Crash-Analyse, Diagnosepaket, Welt-Backup, Quick Play) | `launcher-core/build/test-results/test` |
| Unit-Tests launcher-app (Browser-Allow-List) | ✅ 1/1 | |
| Unit-Tests Begleit-Mod (Frametime-Statistik, Schlaganimation), alle 16 Mod-Module 26.3 … 1.16.5, 1.12.2, 1.8.9 | ✅ 7/7 je Version, 5/5 für 1.12.2 und 1.8.9 (Schlaganimation dort nur Parsing), zusammen 108 | `client/mc*/build/test-results` |
| **Smoke-Test aller 18 Profile** (2026-10-20, `LV=1 .t/smoke-all.sh`): 26.3, 26.2, 26.1.2, 26.1.1, 26.1, 1.21.11, 1.21.8, 1.21.4, 1.21, 1.20.6, 1.20.4, 1.20.1, 1.19.4, 1.18.2, 1.17.1, 1.16.5, 1.12.2, 1.8.9. Je Profil: Lumora-Hauptmenü, Hub (alle Tabs), Optionen, Welten, Multiplayer, Überblendung, HUD-Editor mit Verschieben/Speichern, Widget-Einstellungen, Demo-Welt mit 4 Schlaganimationen, Zoom, ESC-Menü, Hub und HUD in der Welt | ✅ 18/18 „SMOKE TEST PASSED“. Lumora-API überall aktiv: mit LabyVoice 3 Addons/11 Widgets (26.x, 1.21.11, 1.21.8), sonst 2 Addons/10 Widgets (Session-Timer, dieselbe JAR in allen Versionen) | `docs/test-evidence/smoke-summary-2026-10-20.txt`, `docs/test-evidence/smoke-<v>.json`, `instances/<v>/screenshots/smoke-*` |
| **LumoraVoice 0.8.0 mit Lumora-API 1.1.0 im Spiel, alle 18 Profile** (2026-10-27, `LV=lumora sh .t/smoke-all.sh`): 26.3, 26.2, 26.1.2, 26.1.1, 26.1, 1.21.11, 1.21.8, 1.21.4, 1.21, 1.20.6, 1.20.4, 1.20.1, 1.19.4, 1.18.2, 1.17.1, 1.16.5, 1.12.2, 1.8.9. Je Profil: Addon aktiv, Widget `lumoravoice:voice` registriert, verschoben und gespeichert, beide LumoraVoice-Menüs über `AddonScreens` (`AddonViewScreen`) geöffnet, Sprach-HUD in der Demo-Welt, Namensschild-Mixins angewendet (`defaultRequire 1`); auf 26.1.2 zusätzlich Vorschau-Spieler mit Namensschild-Symbolen und Kanalzeile | ✅ 18/18 „SMOKE TEST PASSED“. Der erste 26.3-Lauf scheiterte an zu wenig freiem Arbeitsspeicher (0,4 GB), die Wiederholung bestand. 26.1.2, 26.2 und 26.3 nach der Korrektur von `AvatarSubtitleMixin` erneut: bestanden | `docs/test-evidence/smoke-summary-lumoravoice-2026-10-27.txt` |
| Unit-Tests **lumora-social** (Textbereinigung inkl. Bidi/Formatcodes, Adressen mit TLS-Pflicht außerhalb des LAN, Konversations-IDs, JSON-Rundreise des Modells) | ✅ 5/5 | `lumora-social/build/test-results` |
| **Lumora-Social-Server mit echten Clients** über WebSocket (Freunde per Name, gegenseitige Anfrage, Status inkl. Server-Adresse, Direktnachrichten mit Ungelesen-Zähler und Lesebestätigung, nur unter Freunden, Gruppe mit eigenen Rollen und Rechten, 60 Nachrichten mit Nachladen, Stummschalten, Owner-Übergabe beim Verlassen, Neustart mit gespeicherten Daten, gleiches Konto in Launcher und Spiel, `/health`, Müll-Anfragen, `--dev-auth` nur auf Loopback) | ✅ 4/4 | `lumora-social-server/build/test-results` |
| **Lumora Social im Spiel, alle 18 Profile** (2026-10-26/27, `LV=1 .t/social-smoke.sh`): Testserver (`--dev-auth`, 127.0.0.1) mit Test-Bots; je Profil Freundschaftsanfrage, Aufnahme in „BedWars Squad“, Gruppenchat öffnen, Nachricht senden, Bestätigung und Antwort abwarten, Screenshots | ✅ 18/18 „SMOKE TEST PASSED“, `social` je Profil „PASSED: … message delivered, 1 reply(s)“. Kontrolllauf nach den letzten Änderungen (26.1.2, 1.21.4, 1.19.4, 1.8.9): ✅ 4/4 | `docs/test-evidence/social-smoke-2026-10-27.txt`, `docs/images/social-ingame-*.png` |
| Launcher-Seite „Freunde“ gegen denselben Testserver (offscreen gerendert): Freunde mit Live-Status, eingehende Anfrage, Gruppe mit Banner, Rollen, Mitgliedern, Chat mit Rollen-Tags und ✓✓, Direktnachricht, kompakte Ansicht | ✅ Nachrichten der Spieltests aller Versionen erscheinen live im Launcher-Chat | `docs/images/social-launcher-*.png` |
| Release-Paket mit Server (`LumoraSocialServer/`): Start mit der Java-Laufzeit des Launchers, `/health`, WebSocket-Pflicht (426), TLS mit PKCS12-Keystore | ✅ | Konsolenausgabe |
| Gefundene und behobene Fehler (Lumora Social) | Launcher-Chat sprang nicht zur neuesten Nachricht (Hilfs-Panel zwischen Viewport und Ansicht) → eigener Scroll-Container. Rollen-Reihenfolge (Mitglied vor eigenen Rollen) → Owner, eigene Rollen, Mitglied. Bei kleiner GUI im Spiel nahm die Info-Spalte dem Chat den Platz → ausgeblendet bzw. als Overlay. Ratenbegrenzung des Servers räumte nie auf → regelmäßiges Aufräumen | |
| Unit-Tests Neuigkeiten und Server-Liste (Mojang-News: nur Java-Einträge, Links nur zu minecraft.net, keine Pfad-Tricks in Bild- und Patch-Notes-Pfaden, Bilder nur vom Inhalts-Host; mitgelieferte Lumora-News; Server-Adressen, eigene Server, Bewertungen gespeichert; Verlauf, Kurve und Wochentrend) und Autostart-Befehle (nur HKCU) | ✅ 8/8 | `launcher-core`, `launcher-app` Test-Ergebnisse |
| Neue Launcher-Seiten offscreen gerendert (2026-10-27): Versions-Raster, Neuigkeiten (echte Mojang-Feeds) und Changelog, Server-Liste mit **echten Live-Pings** (z. B. DonutSMP 26.929, Hypixel 25.986 Spieler mit Server-Icons), Einstellungen normal und erweitert | ✅ | `docs/images/launcher-{home,news,changelog,servers,settings}.png` |
| Unit-Tests Launcher-Neugestaltung (2026-10-29): Startarten und Instanzordner, GC-Flags (nicht bei eigenem Collector), Heap-Dump, Debug nur Loopback, Umgebungsvariablen, Vollbild, Reparatur neuer Einstellungen; Speicherübersicht (Kategorien, nur verwaltete Daten leeren, ungenutzte Dateien, aktuelle Logs bleiben); Discord-IPC mit Test-Gegenstelle (Handshake, Aktivität, Abschalten, ohne ID/Discord nichts); Grafik-Profile (nur vorhandene Schlüssel, 1.8.9- und 1.21-Format, Sicherung/Wiederherstellen); Modrinth-Kategorien als Facetten (nur Slug-Zeichen) | ✅ 17/17, gesamt 312/312 | `launcher-core` Test-Ergebnisse |
| Spieltests mit der gebauten `LumoraLauncherCli.exe` im Test-Datenordner (2026-10-29): Lumora-Smoke 26.1.2 und 1.8.9 mit den neuen G1-Flags; `--launch-test --loader vanilla` für 1.21.11 und 1.8.9; `--loader fabric` für 26.1.2 (lädt nur Minecraft, Java, Fabric Loader, MixinExtras) | ✅ 5/5 | `.t/flavor-summary.txt`, Launcher-Log |
| Neugestaltete Launcher-Seiten offscreen gerendert (2026-10-29): Startbildschirm, Startseite, Versionsmenü am Startknopf, Performance, Entdecken mit Filtern, alle Einstellungs-Kategorien inkl. Speicherübersicht (echte Werte des Test-Datenordners), Freundesliste gegen `--dev-auth`-Server | ✅ | `docs/images/launcher-{splash,home,version-menu,performance,browser,storage,settings}.png` |
| Lila-Design im Spiel (2026-10-29): Farbwerte in allen 16 Mod-Modulen, lumora-core und Lumora-API, 428 Theme-Texturen, Polarlicht-Shader, neuer Menü-Hintergrund; Smoke-Test **aller 18 Versionen** bestanden, Screenshots von Hauptmenü, Hub, Optionen, Pausenmenü und HUD-Editor geprüft | ✅ 18/18 | `.t/smoke-summary-purple-3.txt`, `.t/smoke-summary.txt`, `docs/images/ingame-*.png` |
| Dabei gefunden und behoben: das Lumora-Theme-Paket war für 26.2 und 26.3 mit dem Paketformat 84 markiert und damit inkompatibel (Minecraft-Menüs blieben grau); jetzt 88 bzw. 97. Fabric API für 26.3 schaltet das Paket außerdem nicht von selbst ein, das macht jetzt der Launcher einmal (bleibt aus, wenn der Spieler es abschaltet; Unit-Test) | ✅ vorher/nachher per Screenshot | `smoke-26.3-pause.png`, `ResourcePackOptionsTest` |
| Server- und Weltliste, Block-Overlay, Button-Designs (2026-10-29): Smoke-Test **aller 18 Versionen** mit 11 Addons; je Version Screenshots der Lumora-Serverliste (gegen drei lokale Test-Statusserver auf 127.0.0.1 und einen nicht erreichbaren Eintrag), der Weltliste, „Klassisch“ (Vanilla-Liste zeigt dieselben Server), „Neue Welt“ und Verbinden (bis zur Anmeldung, die der Testserver nicht kann); Prüfung „Lumora-Liste ersetzt das Vanilla-Menü“ ohne Befund. Deutsch und Englisch geprüft | ✅ 18/18 | `docs/test-evidence/smoke-summary-browser.txt`, `docs/images/ingame-{multiplayer,worlds}.png` |
| Unit-Tests Server- und Weltliste: NBT lesen/schreiben byte-gleich, `servers.dat` mit erhaltenen Tags, versteckten Einträgen und `servers.dat_old`, beschädigte Datei bleibt unangetastet, Adressprüfung, Status-JSON samt MOTD-Farben, Ping gegen lokalen Testserver, Welten lesen/sortieren, Löschen nur unter `saves/`; Bedienung über einen Test-Renderer (Server hinzufügen per Dialog, ungültige Adresse, Enter verbindet, Suche, Welt starten/erstellen, Knopfleiste passt bei schmalem Fenster) | ✅ 13/13 | `lumora-core` Test-Ergebnisse |
| Dabei gefunden und behoben: in schmalen Fenstern lief „Klassisch“ unter „Zurück“ (deutsche Beschriftungen) → kurze Beschriftungen bzw. Ausblenden; 26.2/26.3 verwalten Bildschirme über `Minecraft.gui` statt `Minecraft.setScreen` (Mixin-Prüfung) → eigenes Ziel; 1.8.9/1.12.2 speichern beim Laden den übergebenen Weltnamen → der Anzeigename wird mitgegeben | ✅ vorher/nachher per Screenshot bzw. Prüfskript | `smoke-26.1.2-multiplayer.png`, `.t/check-mixins.pl` |
| Block-Overlay nach FDPClient/BThack (2026-10-30): eigene Box statt Vanilla-Umrandung in allen 16 Modulen (26.2/26.3 über `SubmitNodeCollector`, 26.1.2/1.21.11 eigene Linien mit Breite, 1.19.4–1.21.8 `debugQuads`, 1.16.5–1.18.2 direkt mit dem Tesselator, 1.8.9/1.12.2 OpenGL wie Vanilla); Smoke-Test **aller 18 Versionen** mit neuem Schritt „Blick auf den Boden“ und Screenshot `blockoverlay`: lila Box mit Umrandung und Blockinfo sichtbar; 1.12.2 zusätzlich mit „Nur Umrandung“ und deckender Füllung gegengeprüft | ✅ 18/18 | `.t/smoke-summary-overlay.txt`, `smoke-<v>-blockoverlay.png` |
| Start der gebauten `LumoraLauncher.exe --tray` (Autostart-Weg) mit Test-Datenordner | ✅ Prozess läuft ohne Fenster (nur Tray), Log „Lumora launcher started“ | Prozessliste, `launcher.log` |
| Mixin-Ziele aller Module gegen das echte Minecraft-JAR (`.t/check-mixins.pl`: Methoden, Aufrufe, Ordinals, Parametertypen von `@Inject` und `@ModifyArg`) | ✅ 16 Lumora-Module und 5 LabyVoice-Versionen ohne Befund | Skriptausgabe |
| Gefundene und behobene Fehler dieses Laufs | 1.12.2: Absturz beim Erstellen einer Welt (`FileSystemAlreadyExistsException`, Minecraft öffnet sein eigenes JAR für Fortschritte/Rezepte) → gemeinsames Dateisystem per Mixin. LabyVoice 1.21.8: `@ModifyArg`-Signatur des Namensschild-Mixins (erstmals im Spiel getestet) → korrigiert, Checker erkennt den Fall jetzt. Launcher: Lithium für 26.3 verlangt `mixinextras >=0.5.5`, das Fabric Loader 0.19.5 selbst mitbringt; die Mod-Prüfung kannte das nicht und blockierte den Start mit eingeschaltetem Lithium → die Prüfung liest jetzt die Jar-in-Jar-Mods der Loader-JAR (vorher/nachher per `--launch-test --profile 26.3` mit Lithium nachgestellt: „Inkompatible Mod-Kombination“ → „LAUNCH TEST PASSED“) | |
| Integrationstest mit echten JARs von Modrinth und der echten Fabric-Loader-JAR (alle 18 Versionsprofile mit ihrer Lumora-Mod auflösbar, alle Erweiterungen je Version kombinierbar, Lumora-Mod einer anderen Version abgelehnt, VFP-Versionsgrenze) | ✅ 6/6 (inkl. Ressourcenpakete samt EMF/ETF/Nuit je Version) | `launcher-core/build/test-results/integrationTest` |
| `jpackage` → `LumoraLauncher.exe`, `LumoraLauncherCli.exe` | ✅ erzeugt, PE32+ x86-64 (GUI bzw. Konsole), SHA-256 in `SHA256SUMS.txt` | `file`, `sha256sum -c` |
| GUI-EXE startet und öffnet das Fenster „Lumora Client“ | ✅ (Prozessbaum geprüft, Fenster offscreen gerendert: `docs/images/`) | |
| Smoke-Test **26.1.2** (Demo-Modus: Lumora-Hauptmenü, Hub, Optionen, Welten, Multiplayer, Überblendung, Demo-Welt mit 4 Schlaganimationen, ESC-Menü mit Lumora-Button, Hub aus dem ESC-Menü) | ✅ GLSL-Shader aktiv (OpenGL), Sodium 0.9.2 | `docs/test-evidence/smoke-26.1.2.json` |
| Smoke-Test **1.21.11** (gleicher Ablauf, Mojang-Java 21) | ✅ GLSL-Shader aktiv, Sodium 0.8.14, Theme-Paket geladen | `docs/test-evidence/smoke-1.21.11.json`, `docs/images/ingame-1.21.11-*.png` |
| Smoke-Test **1.21.8** (gleicher Ablauf, Mojang-Java 21) | ✅ GLSL-Shader aktiv, Sodium 0.7.3, Theme-Paket geladen | `docs/test-evidence/smoke-1.21.8.json`, `docs/images/ingame-1.21.8-*.png` |
| Migration alter Spielordner (`opengl`, `vulkan` → `26.1.2`) beim ersten Start | ✅ zuletzt gespielte Instanz übernommen, die andere unverändert, keine VulkanMod-JAR mehr in `mods` | `launcher.log` („Migrated instance“) |
| Absturz-Erkennung **mit echtem Minecraft-Crash** (`--smoke-test --crash-test`) für 26.1.2, 1.21.11 und 1.21.8 | ✅ Crash-Report gefunden, Beschreibung „Unexpected error“, Ausnahme erkannt, Ursache „Lumora Client“ (4 Stacktrace-Zeilen) richtig zugeordnet | CLI-Ausgabe „CRASH TEST PASSED“ |
| Ressourcenpakete im Spiel (alle 8 bzw. 6/7 je Version) mit EMF, ETF, Nuit, Sodium, ViaFabricPlus | ✅ alle drei Versionen geladen, keine Ladefehler; Fresh Food in 1.21.11 entfernt (Shader-Fehler des Pakets dort) | `latest.log` „Reloading ResourceManager … file/…“ |
| Neue Ingame-Funktionen: Mod-Hub (5 Tabs), Zoom, HUD + HUD-Editor, Lumora-Ladebildschirm, Lumora-Schrift (Inter TTF), Übergangs-Einstellungen | ✅ in 26.1.2, 1.21.11 und 1.21.8 per Smoke-Test-Screenshots geprüft (Ladebildschirm per Ressourcen-Neuladen ausgelöst) | `instances/<v>/screenshots/smoke-*` |
| Unit-Tests **lumora-core** (Event-Bus: Priorität, Abbruch, Fehlerisolation; Addon-Lebenszyklus inkl. Aufräumen, Berechtigungen, fehlende/unpassende/zyklische Abhängigkeiten, Kaskade; Widget-Registry: Duplikate, an/aus, Fehlerisolation, Einstellungen je Profil, gebundene Einstellungen; Layout-Speicher: Rundreise, kaputte Einträge/Dateien, Migration; Editor-Logik: Klick ohne Verschieben, Ziehen mit Einrasten/Raster, Eckgriffe, Mausrad, Alt-Klick, Ebenen, Undo/Redo, Verwerfen; Keybinds, Benachrichtigungen, Aufgaben) | ✅ 43/43 | `lumora-core/build/test-results` |
| Unit-Tests **LabyVoice** (bestehende 38 + Lumora-Metadaten, gebundene Einstellungen, Übernahme von Ecke/Größe) | ✅ 41/41 | `LabyVoice-…/build/test-results` |
| Lumora-API im echten Spiel, **26.1.2** mit LabyVoice 0.7.0 und Session-Timer | ✅ 3 Addons aktiv, 11 Widgets, keine Probleme; LabyVoice-Widget per Editor-Logik verschoben, gespeichert und **nach Neustart an derselben Stelle geladen** (x=0.511 y=0.772); Widget-Einstellungen, Addon-Tab, Widgets in der Demo-Welt | `docs/test-evidence/smoke-26.1.2.json` (`lumoraApi`), Log `LUMORA_SMOKE_LAYOUT`, `docs/images/api-*.png` |
| Lumora-API im echten Spiel, **26.1.2** mit LabyVoice 0.7.1 (Instanz des Nutzers, ohne Session-Timer) | ✅ Smoke-Test bestanden: Addon „LabyVoice 0.7.1 active“, Widget `labyvoice:voice` geladen, per Editor-Logik verschoben und gespeichert, in der Demo-Welt gezeichnet; `problems: []` | `docs/test-evidence/smoke-26.1.2-labyvoice-0.7.1.json` |
| Lumora-API **1.21.11** und **1.21.8** mit derselben Session-Timer-JAR | ✅ 2 Addons, 10 Widgets, Verschieben/Speichern, Widgets im Spiel | `docs/test-evidence/smoke-1.21.*.json` |
| Launcher: Mod-Browser (echte Modrinth-Suche), 3D-Skin (echte Mojang-Profile), Benchmark-Diagramm | ✅ offscreen gerendert | `docs/images/launcher-*.png` |
| Neue Launcher-Seiten (Mods, Bibliothek, Abstürze) | ✅ mit Testdaten offscreen gerendert (lokaler Testserver für den Server-Status, echte Welt, echter Crash-Report) | `docs/images/launcher-*.png` |
| Play-Ablauf ohne Client-ID | ✅ INSTALLING → LOGGING_IN → ERROR mit Erklärung | Harness-Ausgabe |
| Echter Microsoft-Endpunkt mit unregistrierter Client-ID | ✅ AADSTS70012 wird erkannt und erklärt | `MicrosoftAuthenticatorTest#unknownClientIdTemporarilyUnavailableIsExplained` |
| Geheimnis-Scan der Distribution (JWT, client_secret, private Schlüssel, GUIDs in eigenen JARs) | ✅ keine Treffer | |

### Nicht ausgeführt / nicht ausführbar

| Test | Grund |
|---|---|
| Server- und Weltliste: Verbinden mit einem echten öffentlichen Server, Welt löschen per Mausklick; Block-Overlay: Gleiten, „Nur die angesehene Seite“ und Regenbogen im Bild | nicht ausgeführt: Verbinden nur gegen lokale Testserver (keine fremden Server im Test); Löschen nur per Unit-Test im Temp-Ordner. Block-Overlay-Screenshots zeigen nur den Standard (Box mit Füllung und Umrandung) auf einem ruhenden Block; Gleiten, Seite und Kurven sind per Unit-Test geprüft. „Durch Wände“ (FDPClient Depth3D) ist nicht umgesetzt |
| Anmeldung mit echtem Microsoft-Konto und Spielstart online | Es gibt keine von Mojang freigeschaltete Azure-Client-ID und keine Kontozugangsdaten. Die Kette ist nur gegen Mock-Server getestet |
| Beitritt zu Servern anderer Protokollversionen über ViaFabricPlus | erfordert ein Konto (Online-Modus) bzw. Testserver |
| LumoraVoice: echtes Gespräch auf einem Server (Laby Voice oder Simple Voice Chat) | nicht geprüft; der Smoke-Test prüft Addon, Widget, Menüs, HUD und Namensschilder, aber keine Sprachverbindung |
| Lumora-Social-Anmeldung über den echten Mojang-Sitzungsdienst (`join`/`hasJoined`) | braucht ein echtes Minecraft-Konto mit gültigem Token; getestet ist der vollständige Ablauf mit `--dev-auth`. Der Server im Mojang-Modus startet und antwortet, die Mojang-Aufrufe selbst sind nicht mit echten Daten ausgeführt |
| „Mit Windows starten“ tatsächlich in der Registry setzen und Neustart des PCs | nicht auf dem Rechner des Nutzers verändert; geprüft sind die `reg`-Befehle (nur HKCU) und der Start mit `--tray` |
| Windows-Benachrichtigung aus dem Tray bei einer echten Chat-Nachricht | Tray-Symbol und Start geprüft, die Sprechblase selbst nicht auf dem Bildschirm beobachtet |
| Lumora Social über das Internet hinter einem Reverse-Proxy (Caddy/nginx) mit öffentlichem Zertifikat | kein öffentlicher Server verfügbar; TLS direkt im Server ist mit einem selbst signierten Zertifikat geprüft |
| Längeres Spielen in einer Welt (Frametimes in-world, Vergleich der Versionen) | Die Demo-Welt wird nur kurz betreten (Screenshots). **Es werden keine FPS-Verbesserungen behauptet.** Die Titelbildschirm-Werte sind durch VSync auf ~60 FPS begrenzt und nicht aussagekräftig |
| Fenstergröße, Vollbild, Ressourcen-Neuladen | manuell, siehe Checkliste |
| **Portable `LumoraClient.exe`** (einzelne Datei), echter Start wie per Doppelklick (2026-10-18) | ✅ 1. Start: Entpacken (188 Dateien) + Launcher-Fenster „Lumora Client“ nach 5,2 s. 2. Start: direkt, 2,2 s. Manipuliertes Byte im Paket → Fehlerdialog, nichts entpackt oder gestartet, keine Reste |
| Setup-EXE (IExpress), **vollständiger Doppelklick-Lauf** | Gebaut ✅ (PE, 32 MB, in `SHA256SUMS.txt`). Das Entpacken (`/C /T:`) ist geprüft ✅, `install.cmd` aus dem entpackten Setup installiert korrekt in einen Testordner, und der installierte Launcher startet ✅. Der automatische Startschritt des IExpress-Stubs (`/Q` ohne `/C`) scheiterte in der Sandbox der Build-Sitzung mit `0x80070002`, **auch bei einem trivialen Testpaket**. Ursache vermutlich die Sandbox/Container-Umgebung. Auf einem normalen Desktop nicht verifiziert |
| Discord Rich Presence mit der echten Discord-App | nicht ausgeführt: braucht eine eigene Discord-Anwendungs-ID; geprüft ist das Protokoll gegen eine Test-Gegenstelle |
| „Speicher freigeben“, Grafik-Profile und „Aktion nach dem Start: Schließen“ in der echten Instanz | nicht ausgeführt (nur Unit-Tests im Temp-Ordner und Render), um keine Nutzerdaten zu verändern |
| Startbildschirm und Akzentfarbe im echten Fensterbetrieb | nur offscreen gerendert |
| Installer mit Inno Setup | Inno Setup war auf dem Build-Rechner nicht installiert. Skript (`packaging/windows/LumoraClient.iss`) und CI-Job sind vorhanden, aber nicht ausgeführt |
| Saubere Windows-VM ohne Java | nicht verfügbar. Der Start erfolgte aus einem frisch entpackten ZIP mit eigener Runtime, aber auf dem Entwicklungsrechner |
| Linux-/macOS-Lauf des Launchers | nicht verfügbar (Code ist plattformneutral, CI-Job für Ubuntu definiert, aber nicht ausgeführt) |
| GitHub-Actions-Workflow | nicht ausgeführt (kein Repository/Runner) |
| Nvidia-/Intel-GPUs | nur AMD verfügbar |
