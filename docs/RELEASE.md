# Release-Prozess

## Ablauf

1. Version in `gradle.properties` (`lumora.version`) erhöhen.
2. Falls Komponenten aktualisiert werden: `launcher-core/src/main/resources/lumora/profiles.json` anpassen.
   Version, URL, Größe und SHA-512 **aus der Modrinth-API** übernehmen, nie von Hand berechnen oder raten.
3. Prüfen:
   ```bat
   gradlew.bat clean build :launcher-core:integrationTest
   ```
4. Distribution bauen (Windows, mit Installations-Assistent):
   ```bat
   gradlew.bat :launcher-app:portableExe :launcher-app:releaseDist
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

**0.3.92 (2026-11-22):**
- **Inhalt:** Launcher: Absturz-Analyse. `CrashDiagnosis` (Crash-Report, Ende der Spielausgabe, hs_err-Log derselben Zeit; Regeln für Speicher, Grafiktreiber, Vulkan, Java, Dateien, Mods, Lumora-Mixins, Anmeldung, Welt, Ressourcen), Knopf „Analyze“ auf der Crash-Seite (Kopf und jede Karte) mit Fix-Knopf (Speicher, Grafik-Backend, Reparieren, Mod/Erweiterung abschalten, Anmelden, Bibliothek), `SettingsPage.reload` über `PageHost.settingsChanged`. Mit News-Eintrag (Bild: echter Screenshot der Crash-Seite aus dem Test-Ordner, `--ui-shot`) und Changelog-Eintrag.
- **Build:** `gradlew clean build :launcher-core:integrationTest` (mit `--max-workers=2`) erfolgreich, 703 Unit-Tests ohne Fehler (1 übersprungen; neu: `CrashDiagnosisTest` (6)). `integrationTest` enthält weiterhin keine Tests.
- **Smoke-Test:** auf Wunsch nicht ausgeführt. Das Analysefenster und die Fix-Knöpfe sind nicht im laufenden Launcher durchgeklickt, nur die Regeln getestet und die Knöpfe per Screenshot geprüft.
- **Release:** `SHA256SUMS.txt.sig` gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler, `CrashDiagnosis`, Texte, News-Bild, News und Changelog im `launcher-core-0.3.92.jar`.

**0.3.91 (2026-11-22):**
- **Inhalt:** Neue Screenshots: 48 News-Bilder durch Aufnahmen aus Minecraft 26.3 (Smoke-Lauf im Test-Ordner, ohne Spotify-Addon, damit kein Song dieses PCs zu sehen ist) und aus dem Launcher 0.3.90 (`--ui-shot`, nicht angemeldet) ersetzt, gleiche Dateinamen; gezeichnete Titelbilder und Bilder ohne neue Aufnahme (Tastenanzeige, Kinobalken, Social, Hidden Armor, Inventar-HUD, Performance, Widget-Design, Namensschild-Symbole, Versionsmenü, Spotify-Layouts) bleiben. `docs/images`: 67 alte Screenshots entfernt, 24 `ingame-26.3-*` und 14 `launcher-*` neu, Logo und `social-launcher-group.png` (SOCIAL.md) bleiben; ältere Einträge hier verweisen deshalb auf nicht mehr vorhandene Bilder. Bilder mit lokalem Pfad nicht verwendet bzw. zugeschnitten. Auf Wunsch ohne News und Changelog.
- **Build:** `gradlew clean build :launcher-core:integrationTest` (mit `--max-workers=2`) erfolgreich, 697 Unit-Tests ohne Fehler (1 übersprungen). `integrationTest` enthält weiterhin keine Tests.
- **Smoke-Test:** für das Release auf Wunsch nicht ausgeführt; die zwei Smoke-Läufe 26.3 für die Aufnahmen bestanden (vor diesem Build, gleicher Code ohne die Bilder).
- **Release:** `SHA256SUMS.txt.sig` gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler, News-Bilder im `launcher-core-0.3.91.jar` identisch mit den neuen Dateien (Stichprobe).

**0.3.90 (2026-11-22):**
- **Inhalt:** Hub: Zahnrad auf den Karten entfernt, Klick auf die Karte öffnet die Einstellungen, die Leiste unten schaltet an und aus (ohne Einstellungen schaltet die Karte wie bisher). Hover-Hinweis `lumora.hub.card.hint` in allen 16 Sprachdateien (Werkzeug `.t/add-card-hint-lang.pl`), Zoom-Kartentext aktualisiert, README angepasst. Mit News-Eintrag (Bild: der vorhandene ältere Screenshot `news-settings-sections.png` einer Einstellungsseite, kein neuer, weil ohne Spiellauf; das alte Hub-Bild zeigt noch das Zahnrad) und Changelog-Eintrag.
- **Build:** `gradlew clean build :launcher-core:integrationTest` (mit `--max-workers=2`) erfolgreich, 697 Unit-Tests ohne Fehler (1 übersprungen). `integrationTest` enthält weiterhin keine Tests.
- **Smoke-Test:** auf Wunsch nicht ausgeführt. Die neuen Karten sind nicht im Spiel angesehen.
- **Release:** `SHA256SUMS.txt.sig` gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler, neuer Hinweistext in den gebündelten Client-Jars und kein „Zahnrad: Einstellungen“ mehr (Stichprobe 1.8.9, 1.21.11, 26.3), News und Changelog im `launcher-core-0.3.90.jar`.

**0.3.89 (2026-11-20):**
- **Inhalt:** Launcher: „Multiple instances“ erlaubt jetzt auch dieselbe Version mehrmals (bis zu 8 Läufe). `GameProcess.Slot` (Sperre `.lumora-running-n.lock`, eigene Ausgabe `lumora-game-output-n.log`, eigene Statusdatei `status-n.json`), `NativeExtractor` lässt gleiche, vom laufenden Spiel gehaltene Native-Dateien liegen, `PlayService` verfolgt alle laufenden Spiele. Beschreibung der Einstellung angepasst. Mit News-Eintrag (Bild: der vorhandene ältere Launcher-Screenshot `settings.png`, kein neuer, weil der automatische Screenshot den Schalter nicht zeigt) und Changelog-Eintrag.
- **Build:** `gradlew clean build :launcher-core:integrationTest` (mit `--max-workers=2`) erfolgreich, 697 Unit-Tests ohne Fehler (1 übersprungen; neu: `GameProcessSlotTest` (2), `NativeExtractorTest` (2, davon einer mit unter Windows gehaltener Datei)). `integrationTest` enthält weiterhin keine Tests.
- **Smoke-Test:** auf Wunsch nicht ausgeführt. Zwei gleichzeitige Läufe derselben Version sind nicht im Spiel geprüft.
- **Release:** `SHA256SUMS.txt.sig` gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler, `GameProcess$Slot`, News-Eintrag, Changelog und neuer Text im `launcher-core-0.3.89.jar`.

**0.3.88 (2026-11-20):**
- **Inhalt:** Lumora-API 1.13: geteilte Statuszeilen über Lumora Social (`SocialInfo.shareStatus/sharedStatus`, Zwischenspeicher `SharedStatuses` im Core, Social-Anfragen `status.set/status.get`, nur solange verbunden, nicht gespeichert). Genutzt vom Spotify-Addon 1.2.0 (Song unter dem Namen). Auf Wunsch ohne News und Changelog.
- **Build:** `gradlew clean build :launcher-core:integrationTest` (mit `--max-workers=2`) erfolgreich, 693 Unit-Tests ohne Fehler (1 übersprungen; neu: `SocialServerTest.sharedStatusLinesFollowTheirScope`, `SharedStatusesTest` (2)). `integrationTest` enthält weiterhin keine Tests.
- **Smoke-Test:** auf Wunsch nicht ausgeführt. Das Teilen zwischen zwei Spielern ist nicht im Spiel geprüft, nur über echte Clients gegen den echten Server im Test.
- **Release:** `SHA256SUMS.txt.sig` gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler, `SharedStatuses` und API 1.13.0 in den gebündelten Client-Jars (Stichprobe 1.8.9, 1.21.11, 26.3), `status.get` im gebündelten Social-Server.

**0.3.87 (2026-11-20):**
- **Inhalt:** Discord Rich Presence: großes Bild ist das Launcher-Icon als Link (`PresenceImages.LOGO`, das Asset „lumora“ gab es in der Discord-Anwendung nicht), kleines Bild Account-Kopf (mc-heads.net nach UUID) oder Server-Icon (mcsrvstat.us, nur mit „Show server in Discord“). Neu `PresenceImages`, `Activity.withSmall`, `GameStatus.shownServer`. Auf Wunsch ohne News und Changelog.
- **Build:** `gradlew clean build :launcher-core:integrationTest` (mit `--max-workers=2`) erfolgreich, 690 Unit-Tests ohne Fehler (1 übersprungen; neu: `DiscordPresenceTest.logoAndSmallPictureAsLinks`, `shownServer` in `GameStatusTest`). `integrationTest` enthält weiterhin keine Tests.
- **Discord:** per Testprogramm gegen das lokale Discord geprüft, dass Bild-Links angenommen werden (Antwort `mp:external/…`); die drei Bildquellen liefern PNG. Die Anzeige im neuen Launcher selbst ist nicht in Discord angesehen.
- **Smoke-Test:** auf Wunsch nicht ausgeführt (nur Launcher geändert).
- **Release:** `SHA256SUMS.txt.sig` gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler, `PresenceImages` und der neue Text in `launcher-core-0.3.87.jar`.

**0.3.86 (2026-11-19):**
- **Inhalt:** Zoom: neue Einstellungsseite (Zoom-Taste auf der Seite, „Zoom halten“, Zoom-Stärke als Regler 1,5 bis 20, Hand in der Ich-Perspektive, Balkenhöhe, HUD ausblenden, Kinokamera nur während des Übergangs, Übergangskurve und -dauer je Richtung). Neu `ZoomMotion` (Core), `ZoomHandMixin` in allen 16 Modulen, neue Optionen in `LumoraConfig`, `HubHost.zoomKeyCode/setZoomKeyCode`. Mit Changelog-Eintrag, auf Wunsch ohne News.
- **Build:** `gradlew clean build :launcher-core:integrationTest` (mit `--max-workers=2`) erfolgreich, 689 Unit-Tests ohne Fehler (1 übersprungen; neu: `ZoomMotionTest` (2), Zoom-Seite in `SettingKindsViewTest` neu). `integrationTest` enthält weiterhin keine Tests.
- **Mixins:** neu `ZoomHandMixin` in allen 16 Modulen (1.16.5 bis 26.2 `ItemInHandRenderer.renderArmWithItem`, 26.3 `submitArmWithItem`, 1.8.9/1.12.2 `HeldItemRenderer.renderArmHoldingItem`); `AnimationsHandDrawMixin` zeichnet nicht, solange die Hand ausgeblendet ist. `check-mixins.pl` findet die Ziele (geprüft 1.8.9, 1.12.2, 26.3). 26.2/26.3 blenden das HUD über `Hud.toggle()` aus, 26.3 rechnet die Zoom-Taste über `SdlKeys` um.
- **Smoke-Test:** auf Wunsch nicht ausgeführt. Nichts davon ist im Spiel geprüft.
- **Release:** `SHA256SUMS.txt.sig` gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler, `ZoomHandMixin` (auch in `lumora.mixins.json`) und die neuen Texte in den gebündelten Client-Jars (Stichprobe 1.8.9, 1.21.11, 26.3).

**0.3.85 (2026-11-19):**
- **Inhalt:** Held Item Tooltip: „Immer anzeigen“ heißt „Dauerhaft anzeigen“, neuer Schalter „Ausblenden (Fade-out)“ (aus: nach der Anzeigezeit auf einmal weg). Mit News-Eintrag (Bild: das vorhandene gezeichnete Titelbild `news-held-item-tooltip.png` aus 0.3.79, kein neuer Screenshot, weil ohne Spiellauf) und Changelog-Eintrag.
- **Build:** `gradlew clean build :launcher-core:integrationTest` (mit `--max-workers=2` wegen knappem Arbeitsspeicher) erfolgreich, 687 Unit-Tests ohne Fehler (1 übersprungen; neu in `ExtrasTest`: Fade-out, ohne Fade-out, dauerhaft). `integrationTest` enthält weiterhin keine Tests.
- **Mixins:** keine Änderung.
- **Smoke-Test:** auf Wunsch nicht ausgeführt. Nicht im Spiel angesehen.
- **Release:** `SHA256SUMS.txt.sig` gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler, neuer Text in den gebündelten Client-Jars (Stichprobe 1.8.9, 1.21.11, 26.3), News-Eintrag im Launcher-Jar.

**0.3.84 (2026-11-19):**
- **Inhalt:** Inventory-HUD: der Hotkey steht in den Einstellungen des Widgets (Widget-Karte im Hub und „Einstellungen“ im HUD-Editor aller 16 Versionen; neu `KeybindManager.ofWidget`), Standard-Modus „Solange die Taste gehalten wird“. Auf Wunsch ohne News- und ohne Changelog-Eintrag.
- **Build:** `gradlew clean build :launcher-core:integrationTest` erfolgreich, 686 Unit-Tests ohne Fehler (1 übersprungen). Der erste Lauf brach ab, weil der Rechner keinen freien Arbeitsspeicher mehr hatte (0,7 von 15,9 GB frei; Test-JVMs mit „Native memory allocation failed“, `hs_err_pid*.log` danach gelöscht); der zweite Lauf mit `--max-workers=2` lief durch. `integrationTest` enthält weiterhin keine Tests.
- **Mixins:** keine Änderung.
- **Smoke-Test:** auf Wunsch nicht ausgeführt. Hotkey und Halten-Modus sind nicht im Spiel geprüft.
- **Release:** `SHA256SUMS.txt.sig` gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler, `ofWidget` im HUD-Editor und der neue Text in den gebündelten Client-Jars (Stichprobe 1.8.9, 1.21.11, 26.3).

**0.3.83 (2026-11-19):**
- **Inhalt:** neues HUD-Widget „Inventory“ (Inventar auf Tastendruck, Standard X; Modi An/Aus, kurz, Halten, Immer; Stil Minecraft-Inventar oder Lumora-Widget; Überschrift und Hotbar-Reihe abschaltbar). `GameExtras.drawSlot` kennt dafür die Plätze 10 bis 36 (Hauptinventar) in allen 16 Modulen. Mit News-Eintrag (`news-inventory-hud.png`: echter Screenshot aus dem Smoke-Lauf von 1.8.9 mit dem Widget auf „Immer“, ausgeschnitten und verlustfrei auf 854×480 vergrößert, nicht bearbeitet; das „Combo“-Widget lag dort darüber) und Changelog-Eintrag.
- **Build:** `gradlew clean build :launcher-core:integrationTest` erfolgreich, 686 Unit-Tests ohne Fehler (1 übersprungen; neu in `ExtrasTest`: Größe, Platz-Nummern, Modi An/Aus, kurz, immer, außerhalb einer Welt). `integrationTest` enthält weiterhin keine Tests.
- **Mixins:** keine Änderung; Taste über die Lumora-Keybinds (wo Minecraft X belegt, ab 1.12 „Hotbar laden“, startet sie ungebunden), Texte in den Sprachdateien aller 16 Module.
- **Smoke-Test:** für dieses Release auf Wunsch nicht ausgeführt. Vor dem Release lief ein Smoke-Test von 26.3 und 1.8.9 mit dem Widget auf „Immer“ (beide bestanden, Screenshots angesehen: Raster, Überschrift und das Schwert im richtigen Hotbar-Platz); der Tastendruck selbst ist nicht im Spiel geprüft.
- **Release:** `SHA256SUMS.txt.sig` gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler, `InventoryHud` und die Texte in den gebündelten Client-Jars (Stichprobe 1.8.9, 1.21.11, 26.3), News-Bild im Launcher-Jar.

**0.3.82 (2026-11-19):**
- **Inhalt:** Animations: Schalter „Alte Block-Animation“ (Schwert-Blockhaltung von 1.7 bei gehaltener rechter Maustaste und beim Blocken mit Schild, auch wenn „Schwert / Blocken“ auf Default steht; eigener Spieler in der dritten Person mit Blockarm; Teil der Presets). Mit News-Eintrag (`news-old-block.png`, gezeichnetes Titelbild, kein Screenshot) und Changelog-Eintrag.
- **Build:** `gradlew clean build :launcher-core:integrationTest` erfolgreich, 685 Unit-Tests ohne Fehler (1 übersprungen; neu in `AnimationsModuleTest`: Schalter an/aus, Presets, Custom, Auswirkung auf `ItemPose.select`, Versionen ohne den Teil). `integrationTest` enthält weiterhin keine Tests.
- **Mixins:** keine neuen; `LegacyHands.clickBlocking` und `AnimationsAvatarPoseMixin` in allen 14 Modulen von 1.16.5 bis 26.3 erweitert (`.t/anim-swordblock.pl`), Texte in den Sprachdateien aller 16 Module.
- **Smoke-Test:** komplett, alle 16 Versionen (1.8.9, 1.12.2, 1.16.5, 1.17.1, 1.18.2, 1.19.4, 1.20.1, 1.20.4, 1.20.6, 1.21, 1.21.4, 1.21.8, 1.21.11, 26.1.2, 26.2, 26.3) `SMOKE TEST PASSED`, stumm, im Test-Home, mit dem Modul „Animations“ an und allen Teilen auf Legacy samt „Alte Block-Animation“; in `latest.log` ab 1.16.5 jeweils „Addon lumora-animations … enabled“ und kein Mixin-Fehler. Der Smoke-Test hält nie die rechte Maustaste: die Blockhaltung selbst ist nicht im Spiel angesehen.
- **Release:** `SHA256SUMS.txt.sig` gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler, `clickBlocking` und die Texte in den gebündelten Client-Jars (Stichprobe 1.16.5, 1.21.11, 26.3), News-Bild im Launcher-Jar.

**0.3.81 (2026-11-19):**
- **Inhalt:** Modul „Animations“ vereinfacht (je Teil nur „Default“ und „Legacy“, Presets Legacy/Default/Custom, Rezeptbuch als normaler Teil) und nach 1.16.5 bis 1.21.11 portiert (Creative-Tabs ab 1.19.4, 1.12.2 vorerst nur Rückwärtslaufen und Kopfdrehung); Teile ohne Umsetzung je Version über `Platform.animations()` ausgeblendet. Mit News-Eintrag (`news-animations.png`, gezeichnetes Titelbild, kein Screenshot) und Changelog-Einträgen für 0.3.81 und nachträglich 0.3.80. Gespeicherte Werte aus 0.3.80 (1.7/1.8/Modern, alte Presets) sind unbekannt und fallen auf „Default“ zurück.
- **Build:** `gradlew clean build :launcher-core:integrationTest` erfolgreich, 684 Unit-Tests ohne Fehler (1 übersprungen; `AnimationsModuleTest` auf zwei Optionen und Versions-Unterstützung umgestellt). `integrationTest` enthält weiterhin keine Tests.
- **Mixins:** neu in 1.16.5 bis 1.21.11 (je nach Version): `AnimationsBodyMixin`, `AnimationsEquipMixin`, `AnimationsBuildMixin`, `AnimationsCameraMixin`, `AnimationsSneakModelMixin`, `AnimationsHeartMixin` (1.16.5 über die Feldzugriffe in `renderPlayerHealth`), `AnimationsHandDrawMixin`, `AnimationsThirdPersonMixin`, `AnimationsArmPoseMixin`, `AnimationsAvatarPoseMixin`, `AnimationsRecipeBookMixin`, `AnimationsRecipeBookHiddenMixin`, `AnimationsCreativeTabMixin` (ab 1.19.4), `AnimationsArmedStateMixin` (1.21.4 bis 1.21.11); in 1.12.2 `AnimationsBodyMixin`. `check-mixins.pl` findet alle Ziele (die üblichen „MISMATCH“-Meldungen nur für Handler ohne Parameter).
- **Smoke-Test:** auf Wunsch nicht ausgeführt. Keine der Animationen ist in einer älteren Version im Spiel angesehen.
- **Release:** `SHA256SUMS.txt.sig` gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler, Animations-Mixins in den gebündelten Client-Jars (Stichprobe 1.16.5, 1.21.11, 26.3), News-Bild im Launcher-Jar.

**0.3.80 (2026-11-19):**
- **Inhalt:** neues Modul „Animations“ (1.7 / 1.8 / modern je Teil, Presets, Schlag-Stil von der Themes-Seite ins Modul); die Teile sind in 26.1.2, 26.2 und 26.3 eingebaut, in 1.12.2 bis 1.21.11 bietet das Modul vorerst nur den Schlag-Stil (die übrigen Einstellungen sind dort ausgeblendet). Details und Stand in `docs/ANIMATIONS.md`. Auf Wunsch ohne News-Eintrag und ohne Changelog-Eintrag.
- **Build:** `gradlew clean build :launcher-core:integrationTest` erfolgreich, 684 Unit-Tests ohne Fehler (1 übersprungen; neu: `AnimationsModuleTest` (6), `ItemPoseTest` (3), `CreativeTabs18Test` (2)). `integrationTest` enthält weiterhin keine Tests.
- **Mixins:** neu in 26.1.2, 26.2 und 26.3: `AnimationsBodyMixin`, `AnimationsEquipMixin`, `AnimationsBuildMixin`, `AnimationsCameraMixin`, `AnimationsSneakModelMixin`, `AnimationsHeartMixin`, `AnimationsHandDrawMixin` (26.3 zusätzlich `AnimationsHandPrepareMixin`), `AnimationsArmedStateMixin`, `AnimationsThirdPersonMixin`, `AnimationsArmPoseMixin`, `AnimationsAvatarPoseMixin`, `AnimationsRecipeBookMixin`, `AnimationsRecipeBookHiddenMixin`, `AnimationsCreativeTabMixin`; `check-mixins.pl` findet alle Ziele (die üblichen „MISMATCH“-Meldungen nur für Handler ohne Parameter). Schlag-Mixins aller Versionen ab 1.9 lesen den Stil aus dem Modul.
- **Smoke-Test:** auf Wunsch nicht ausgeführt. Keine der Animationen ist im Spiel angesehen.
- **Release:** `SHA256SUMS.txt.sig` gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler.

**0.3.79 (2026-11-19):**
- **Build:** `gradlew clean build :launcher-core:integrationTest` erfolgreich, 673 Unit-Tests ohne Fehler (1 übersprungen; neu in `ExtrasTest`: Held Item Tooltip im Pocket- und Java-Stil, Verzauberungen abschaltbar, Minecrafts Name ausgeblendet, erneutes Anzeigen bei anderem Platz, Item, Namen oder Verzauberungen). `integrationTest` enthält weiterhin keine Tests.
- **Mixins:** neu `HeldItemNameMixin` in allen 16 Client-Modulen (bricht `renderHeldItemName` bzw. `renderSelectedItemName` bzw. `extractSelectedItemName` am Anfang ab, solange das Widget im HUD ist); `check-mixins.pl` findet die Zielmethode in jeder Version (die Meldung „MISMATCH“ kommt wie bei den übrigen Hotbar-Mixins nur vom Handler ohne Parameter). Dazu `McExtras.heldItem()` (Name, Seltenheit, umbenannt, Verzauberungen, Hotbar-Platz) in allen 16 Modulen, Widget `InfoWidgetsModule.HeldItemTooltip`, Texte in den Sprachdateien aller 16 Module.
- **Smoke-Test:** auf Wunsch nicht ausgeführt. Das Widget ist in keiner Version im Spiel angesehen, auch nicht, ob Minecrafts eigener Name überall verschwindet. Das News-Bild ist ein gezeichnetes Titelbild, kein Screenshot.
- **Release:** `SHA256SUMS.txt.sig` gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler, `HeldItemNameMixin` (auch in `lumora.mixins.json`), `InfoWidgetsModule$HeldItemTooltip` und die neun Texte in den gebündelten Client-Jars (Stichprobe 1.8.9, 1.21.11, 26.3), News-Bild im Launcher-Jar.

**0.3.78 (2026-11-19):**
- **Build:** `gradlew clean build :launcher-core:integrationTest` erfolgreich, 672 Unit-Tests ohne Fehler (1 übersprungen; neu in `ExtrasTest`: Bildpfade der Effekte (Datei, Sprite, `inventory.png`), ungültige Ids, Liste mit Name/Stufe und Restzeit, Höhe, Einstellung „Kompakt“). `integrationTest` enthält weiterhin keine Tests.
- **Mixins:** keine Änderung; Widget in `lumora-core` (`InfoWidgetsModule.Potions`, `GameExtras.EffectIcon`), Effekt-Bild und Beacon-Kennzeichen in `McExtras` aller 16 Client-Module (26.x über den GUI-Sprite-Atlas, 1.16.5 bis 1.21.11 über `textures/mob_effect`, 1.8.9 und 1.12.2 über `inventory.png`), Text für „Kompakt“ in den Sprachdateien aller 16 Module.
- **Smoke-Test:** auf Wunsch nicht ausgeführt. Die Effekt-Bilder sind in keiner Version im Spiel angesehen. Das News-Bild ist ein gezeichnetes Titelbild, kein Screenshot.
- **Release:** `SHA256SUMS.txt.sig` gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler, `GameExtras.EffectIcon`, `InfoWidgetsModule.Potions` und der neue Text in den gebündelten Client-Jars (Stichprobe 1.8.9, 1.21.11, 26.3), News-Bild im Launcher-Jar.

**0.3.77 (2026-11-19):**
- **Build:** `gradlew clean build :launcher-core:integrationTest` erfolgreich, 671 Unit-Tests ohne Fehler (1 übersprungen; `ServerMappingsTest` neu mit 9 Tests für die Liste aus LumoraClient/Multiplayer-Mapping, erlaubte Download-Adressen, Bildpfade nur im eigenen Ordner, Versionsbereiche, Cache, Entfernen der alten Caches und die Spielmodus-Felder; `ServerGamemodesTest` entfällt mit `ServerGamemodes`). `integrationTest` enthält weiterhin keine Tests.
- **Echte Daten:** die veröffentlichte `servers.json` aus LumoraClient/Multiplayer-Mapping ohne Spiel mit dem neuen `ServerMappings` gelesen: 375 Server, 116 mit Icon und Banner, 268 mit Spielmodi; mc.hypixel.net, gommehd.net und play.cubecraft.net werden erkannt. Die Git-Hashes aller 232 Bilder im Repo stimmen mit `servers.json` überein, Stichprobe Hypixel-Icon direkt von raw.githubusercontent.com.
- **Mixins:** keine Änderung; alles in `lumora-core` (`browser`), Texte in den Sprachdateien aller 16 Client-Module.
- **Smoke-Test:** auf Wunsch nicht ausgeführt. Die Serverliste mit den neuen Daten ist im Spiel nicht angesehen. Kein News-Eintrag.
- **Release:** `SHA256SUMS.txt.sig` gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler, das neue `ServerMappings` (ohne `ServerGamemodes`) und die neuen Texte in den gebündelten Client-Jars (Stichprobe 1.8.9, 1.21.11, 26.3).

**0.3.76 (2026-11-19):**
- **Build:** `gradlew clean build :launcher-core:integrationTest` erfolgreich, 673 Unit-Tests ohne Fehler (1 übersprungen; neu: `ServerGamemodesTest` mit 5 Tests für Tree und Manifest, Versionsbereiche, Adress- und Wildcard-Abgleich mit Cache und die Spielmodus-Felder mit „+n“). `integrationTest` enthält weiterhin keine Tests.
- **Echte Daten:** Tree von LabyMod/server-media (296 Server) und das Hypixel-Manifest ohne Spiel durch `ServerGamemodes` geprüft: Git-Blob-Hash stimmt, für 26.3 kommen SkyBlock, Bed Wars Solo, Skywars Solo Normal, Murder Mystery Classic, Housing, Bed Wars 4v4 heraus.
- **Mixins:** keine Änderung; alles in `lumora-core` (`browser`), Texte in den Sprachdateien aller 16 Client-Module.
- **Smoke-Test:** auf Wunsch nicht ausgeführt. Die Spielmodus-Felder sind im Spiel nicht angesehen. Das News-Bild ist ein gezeichnetes Titelbild, kein Screenshot.
- **Release:** `SHA256SUMS.txt.sig` gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler, `ServerGamemodes` und die neuen Texte in den gebündelten Client-Jars (Stichprobe 1.8.9, 1.21.11, 26.3), News-Bild im Launcher-Jar.

**0.3.75 (2026-11-19):**
- **Build:** `gradlew clean build :launcher-core:integrationTest` erfolgreich, 668 Unit-Tests ohne Fehler (1 übersprungen; neu: `ServerMappingsTest` mit 6 Tests für Adressabgleich, Tree- und Manifest-Prüfung, Git-Blob-Hash und PNG-Signatur, Cache und Anzeigenamen; in `BrowserViewTest` Server per Drag and Drop umsortieren). Ein vorheriger Einzellauf von `lumora-core:test` hatte einmal `HubViewTest` beim Löschen des Temp-Verzeichnisses scheitern lassen (Windows-Dateisperre), einzeln und im Release-Build grün. `integrationTest` enthält weiterhin keine Tests.
- **Echte Daten:** Tree von NoRiskClient/nrc-server-mappings (117 Server-Ordner), ein Manifest und ein Icon ohne Spiel durch `ServerMappings` geprüft: Git-Blob-Hashes stimmen, Manifest gelesen, Icon dekodiert.
- **Mixins:** keine Änderung; alles in `lumora-core` (`browser`), Texte in den Sprachdateien aller 16 Client-Module.
- **Smoke-Test:** auf Wunsch nicht ausgeführt. Drag and Drop, Banner und Icons als Laufzeit-Texturen sind im Spiel nicht angesehen. Das News-Bild ist ein gezeichnetes Titelbild, kein Screenshot.
- **Release:** `SHA256SUMS.txt.sig` gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler, `ServerMappings` und die neuen Texte in den gebündelten Client-Jars (Stichprobe 1.8.9, 1.21.11, 26.3), News-Bild im Launcher-Jar.

**0.3.74 (2026-11-18):**
- **Build:** `gradlew clean build :launcher-core:integrationTest` erfolgreich, 661 Unit-Tests ohne Fehler (1 übersprungen; neu in `SettingsViewTest`: beim Festlegen einer Taste landet ihr Zeichen nicht in der Suche, Tippen während „Taste drücken…“ ist kein Text, ESC als Taste und als Schließen-Anfrage setzt die Taste auf keine). `integrationTest` enthält weiterhin keine Tests.
- **Mixins:** keine Änderung; die Änderung steckt nur in `lumora-core` (`SettingsView`).
- **Smoke-Test:** auf Wunsch nicht ausgeführt. Tasten festlegen und ESC sind im Spiel nicht angesehen. Das News-Bild ist ein gezeichnetes Titelbild, kein Screenshot.
- **Release:** `SHA256SUMS.txt.sig` gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler, neue `SettingsView` in den gebündelten Client-Jars (Stichprobe 1.8.9 und 26.3), News-Bild im Launcher-Jar.

**0.3.73 (2026-11-18):**
- **Build:** `gradlew clean build :launcher-core:integrationTest` erfolgreich, 660 Unit-Tests ohne Fehler (1 übersprungen; neu in `Api110Test`: NoClip gehört dem Addon, geht beim Zurücksetzen durch den Server und beim Ausschalten des Addons aus). Der erste Lauf scheiterte an `ArticleTest`/`NewsAndServersTest`, weil der News-Eintrag noch kein Bild hatte; mit Titelbild grün. `integrationTest` enthält weiterhin keine Tests.
- **Mixins:** neue `NoClipMixin` (`Player#tick` nach dem Setzen von `noPhysics`), `NoClipViewMixin` (26.1.2 `LevelRenderer#update`, 26.2/26.3 `Camera#extractRenderState`, Aufruf `LocalPlayer#isSpectator`) und `NoClipServerMoveMixin` (`ClientPacketListener#handleMovePlayer` nach `ensureRunningOnSameThread`) mit `check-mixins.pl` in 26.1.2, 26.2 und 26.3 OK; die Server-Prüfung `noPhysics` in `ServerGamePacketListenerImpl` per `javap` nachgesehen.
- **Smoke-Test:** auf Wunsch nicht ausgeführt. NoClip, Blockmerkmale und Item-Farben sind im Spiel nicht angesehen. Das News-Bild ist ein gezeichnetes Titelbild, kein Screenshot.
- **Release:** `SHA256SUMS.txt.sig` gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler, die drei NoClip-Mixins und API 1.12.0 in den gebündelten 26.1.2-, 26.2- und 26.3-Jars, News-Bild im Launcher-Jar.

**0.3.72 (2026-11-18):**
- **Build:** `gradlew clean build :launcher-core:integrationTest` erfolgreich, 659 Unit-Tests ohne Fehler (1 übersprungen; neu in `Api110Test`: Umrandungen verschwinden mit ihrer Registrierung und beim Ausschalten des Addons). Ein vorheriger Lauf hatte einmal `HubViewTest` beim Löschen des Temp-Verzeichnisses scheitern lassen (Windows-Dateisperre), einzeln und im Release-Build grün. `integrationTest` enthält weiterhin keine Tests.
- **Mixins:** neuer Accessor `SpriteContentsAccessor` (`originalImage`, Feld in 26.1.2, 26.2 und 26.3 per `javap` geprüft); `LightOverlayMixin` ruft zusätzlich `BuildingOverlayRender` auf, Ziele unverändert.
- **Smoke-Test:** auf Wunsch nicht ausgeführt. Texturfarben, Kreativ-Items, Umrandungen und Item-Bilder sind im Spiel nicht angesehen.
- **Release:** `SHA256SUMS.txt.sig` gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler, `SpriteContentsAccessor`, `BuildingOverlayRender` und API 1.11.0 in den gebündelten 26.1.2-, 26.2- und 26.3-Jars.

**0.3.71 (2026-11-18):**
- **Build:** `gradlew clean build :launcher-core:integrationTest` erfolgreich, 658 Unit-Tests ohne Fehler (1 übersprungen; neu: `Api110Test` mit 5 Tests für Berechtigung, Lesen und Handeln über den Versions-Adapter, Reihenfolge der Handler mit übersprungenem fehlerhaftem Handler, Wartezeiten und Handler weg beim Ausschalten, Werte von Items und Block-States). `integrationTest` enthält weiterhin keine Tests.
- **Mixins:** neue `BuildingUseMixin` (`MultiPlayerGameMode#useItemOn`, `startDestroyBlock`, `continueDestroyBlock`), `BuildingClientMixin` (`Minecraft#pickBlockOrEntity`, `startUseItem`) und `BuildingSecondaryUseMixin` (`Player#isSecondaryUseActive`) mit `check-mixins.pl` in 26.1.2, 26.2 und 26.3 OK.
- **Smoke-Test:** auf Wunsch nicht ausgeführt. Die Bauwerkzeuge (Schleich-Klick, Mittelklick, Wartezeiten, Abbauen und Platzieren) sind im Spiel nicht ausprobiert.
- **Release:** `SHA256SUMS.txt.sig` gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler, `McBuilding` und die drei Mixins in den gebündelten 26.1.2-, 26.2- und 26.3-Jars, API-Version 1.10.0.

**0.3.70 (2026-11-18):**
- **Build:** `gradlew clean build :launcher-core:integrationTest` erfolgreich, 653 Unit-Tests ohne Fehler (1 übersprungen; neu: `ToggleSprintFlyTest` mit 2 Tests für Standardwerte, Modul aus = normales Tempo und die Grenzen der Regler). `integrationTest` enthält weiterhin keine Tests.
- **Mixins:** neue `FlySpeedMixin` (Player: `getFlyingSpeed` ab 1.19.4, `travel` 1.16.5–1.18.2, `PlayerEntity#travel`/`method_2657` in 1.8.9/1.12.2) und `FlySpeedVerticalMixin` (`LocalPlayer#aiStep`, `ClientPlayerEntity#tickMovement` in Legacy) mit `check-mixins.pl` in allen 16 Versionen OK.
- **Smoke-Test:** auf Wunsch nicht ausgeführt. Fluggeschwindigkeit und Flug-Schwung sind im Spiel nicht ausprobiert.
- **News:** Artikel „Toggle Sprint: schneller fliegen“. Das Titelbild ist aus dem echten Hub-Icon des Moduls erzeugt (kein Spiel-Screenshot). Der Artikel ist im Launcher nicht angesehen.
- **Release:** `SHA256SUMS.txt.sig` gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler, beide Mixins in allen 16 gebündelten Client-Jars, News-Bild im Launcher-Jar.

**0.3.69 (2026-11-18):**
- **Build:** `gradlew clean build :launcher-core:integrationTest` erfolgreich, 651 Unit-Tests ohne Fehler (1 übersprungen). `integrationTest` enthält weiterhin keine Tests.
- **News:** Artikel „Light Block Overlay: Lichtstufen“. Bilder aus einem 26.1.2-Smoke-Lauf im Test-Home (Steinboden mit zwei Fackeln, Lichtstufe als Zahl und als Fläche, mit HUD wie im alten Light-Overlay-Bild). Dabei lief der Lichtstufen-Modus erstmals im Spiel: Farbverlauf und Zahlen sichtbar. Ein 26.3-Lauf mit den neuen Smoke-Aufnahmen hat ebenfalls bestanden. Weitere Tests auf Wunsch nicht ausgeführt; der Artikel ist im Launcher nicht angesehen.
- **Smoke-Code:** `LightOverlaySmoke` schaltet zuerst auf „Spawn-Stellen“ (gleiche Prüfung wie vorher), 26.3 nimmt zusätzlich zwei Lichtstufen-Bilder auf, 26.1.2 hat einen neuen Schritt mit Fackel-Szene.
- **Release:** `SHA256SUMS.txt.sig` gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler, News-Eintrag und beide Bilder im Launcher-Jar enthalten.

**0.3.68 (2026-11-17):**
- **Build:** `gradlew clean build :launcher-core:integrationTest` erfolgreich, 651 Unit-Tests ohne Fehler (1 übersprungen; `LightOverlayModuleTest` jetzt 11 Tests, neu: Lichtstufe als Standard auf jedem begehbaren Block, Moduswechsel liest die Welt neu, Blocklicht/Himmelslicht/beides und Farben dunkel–mittel–hell). Alle 16 Client-Versionen kompilieren mit der erweiterten `LightOverlayProbe`. `integrationTest` enthält weiterhin keine Tests.
- **Mixins:** unverändert.
- **Smoke-Test:** auf Wunsch nicht ausgeführt. Der Lichtstufen-Modus ist im Spiel nicht angesehen.
- **Release:** `SHA256SUMS.txt.sig` gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler, neuer Modus in allen 16 gebündelten Client-Jars enthalten.

**0.3.67 (2026-11-17):**
- **Build:** `gradlew clean build :launcher-core:integrationTest` erfolgreich, 649 Unit-Tests ohne Fehler (1 übersprungen; neu: `HiddenArmorModuleTest` mit 7 Tests für Deckkraft pro Teil, „Gilt für“, Treffer-Anzeige, Ausblenden und „Alle Teile gleich“). `integrationTest` enthält weiterhin keine Tests.
- **Mixins:** alle Ziele mit `check-mixins.pl` gegen die Minecraft-Jars geprüft (1.16.5 bis 26.3). Ein erster 26.1.2-Lauf scheiterte, weil `EquipmentLayerRenderer.renderLayers` zwei Überladungen hat und ein reiner Name nur die erste trifft; jetzt mit voller Signatur.
- **Smoke-Test** (normal, stumm, mit LumoraVoice 0.9.8) für alle 16 Versionen bestanden, jeweils mit „hidden armor“ und Namensschild-Icons. Drei Vorschau-Spieler in Diamantrüstung bei Helm 0 %, Brust 40 %, Hose 100 %, Stiefel 0 %, einer gerade getroffen; in jedem Screenshot angesehen: Helm und Stiefel fehlen, Brust durchscheinend, Hose voll, der getroffene Spieler trägt die ganze Rüstung. Zwei Testszenen-Fehler behoben (Spieler im Laub in 26.1.2, Blick in den Himmel in 1.12.2: Platz freiräumen, Blick geradeaus).
- **News:** Artikel „Hidden Armor“ im fertigen Release-Launcher über `--ui-shot` angesehen.
- **Nicht geprüft:** ein echter Treffer durch einen Mob oder Spieler (der Test setzt den Treffer-Zustand direkt), Rüstungsbesätze (Trims) bei Teiltransparenz im Spiel, ein Selbst-Update von 0.3.66.
- **Release:** `SHA256SUMS.txt.sig` gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler.

**0.3.66 (2026-11-17):**
- **Build:** `gradlew clean build :launcher-core:integrationTest` erfolgreich, 642 Unit-Tests ohne Fehler (1 übersprungen). `integrationTest` enthält weiterhin keine Tests.
- **News:** vier neue Einträge für 0.3.55 bis 0.3.65 mit sechs Bildern (echte Screenshots: Launcher über `--ui-shot` im Test-Home, Performance-Seite und HUD-Messszene aus dem 26.1.2-Smoke-Test, Hub mit Icon-Pack). Mit dem Launcher-Code geprüft: Karten, Artikel mit angehängten Changelog-Abschnitten und Changelog-Titel/Bilder für 0.3.54 bis 0.3.65; Screenshots der News-, Changelog- und Artikelseiten mit dem fertigen Release-Launcher angesehen.
- **Smoke-Test** 26.1.2 (normal, stumm) mit dem neuen Bild der Performance-Seite bestanden.
- **Nicht geprüft:** ein Selbst-Update von 0.3.65.
- **Release:** `SHA256SUMS.txt.sig` gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler.

**0.3.65 (2026-11-17):**
- **Build:** `gradlew clean build :launcher-core:integrationTest` erfolgreich, 642 Unit-Tests ohne Fehler (1 übersprungen; neu: `SmartHudTest` mit gleichen Rechtecken in gleicher Reihenfolge, gleichen Beschriftungen, nur Rechtecken in einem Zeichenlauf und verworfenen Breiten bei Schriftwechsel). `integrationTest` enthält weiterhin keine Tests.
- **HUD-Messung** (stumm, Test-Home, alle Widgets, Widget-Design mit Ecken und Rahmen; CPU-Zeit des Lumora-HUD pro Frame, alles aus → Smart HUD + Frame): 1.20.1 2250 → 606 µs, 1.20.4 2406 → 673, 1.20.6 2269 → 564, 1.21 1640 → 377, 1.19.4 1762 → 547, 1.18.2 1316 → 514, 1.17.1 1275 → 478, 1.16.5 1355 → 549, 1.12.2 787 → 282, 1.21.11 1435 → 865, 26.1.2 1121 → 652, 1.8.9 1692 → 1144 bis 1312. Fast der ganze Gewinn kommt von der Frame-Optimierung; Smart HUD allein 0 bis 6 % (im Rauschen). Ein JFR-Profil (1.20.1) zeigte, dass die HUD-Zeit fast nur in OpenGL-Aufrufen liegt.
- **Pixelvergleich** HUD aus/an in allen gemessenen Versionen: gleich bis auf Live-Werte (FPS, RAM, Uhr, Session-Timer, Spotify-Titel) und wippende Items in der Welt.
- **Messszene:** ein 1.21.11-Lauf scheiterte am Demo-Begrüßungsbildschirm (Szene unvollständig); pausierende Bildschirme werden jetzt geschlossen, die Wiederholung bestand. In 1.12.2 schwankt die Zahl der aufgebauten Ziele (118, 236, 353); ein Lauf scheiterte an der Mindestzahl, die Wiederholung bestand.
- **Smoke-Test** (normal, stumm, mit LumoraVoice 0.9.8) für alle 16 Versionen bestanden, Namensschild-Icons jetzt in allen 16 Versionen geprüft (Vorschau-Spieler neu in 11 Smoke-Tests).
- **Nicht geprüft:** ein Selbst-Update von 0.3.64; das Umschalten von „Unicode-Schrift erzwingen“ im Spiel (nur Unit-Test).
- **Release:** `SHA256SUMS.txt.sig` gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler.

**0.3.64 (2026-11-16):**
- **Build:** `gradlew clean build :launcher-core:integrationTest` erfolgreich, 638 Unit-Tests ohne Fehler (1 übersprungen). `integrationTest` enthält weiterhin keine Tests.
- **Messszene** (stumm, Test-Home, FPS-Limit aus, Villager ohne KI statt Husks) für 1.12.2 und 1.8.9 bestanden: hinter der Wand Ø FPS alles aus → nur Culling etwa 75 bis 81 → 540 (1.12.2) und etwa 73 bis 80 → 208 (1.8.9), nahezu alle Ziele verdeckt. Je Version 240 Ein/Aus-Prüfungen ohne Fehler. Ein erster 1.8.9-Durchlauf scheiterte an der Szene (Wand fehlte); die Wiederholung bestand.
- **Wolken (1.12.2, 1.8.9):** Display-List etwa 97-mal in 8 s neu kompiliert statt in jedem Frame neu gezeichnet; Ø FPS ohne → mit Optimierung 588/566 → 687/755 (1.12.2) und 242/240 → 265/243 (1.8.9, im Rauschen). Screenshot-Vergleich: Wolken gleich bis auf einzelne Randpixel.
- **Smoke-Test** (normal, stumm) für 1.12.2 und 1.8.9 bestanden, Namensschild-Icons in 1.8.9 gezeichnet.
- **Nicht geprüft:** ein Selbst-Update von 0.3.63.
- **Release:** `SHA256SUMS.txt.sig` gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler.

**0.3.63 (2026-11-16):**
- **Build:** `gradlew clean build :launcher-core:integrationTest` erfolgreich, 638 Unit-Tests ohne Fehler (1 übersprungen). `integrationTest` enthält weiterhin keine Tests.
- **Messszene** (stumm, Test-Home, FPS-Limit aus) für 1.20.6, 1.20.4, 1.20.1, 1.19.4, 1.18.2, 1.17.1 und 1.16.5 bestanden: hinter der Wand Ø FPS alles aus → nur Culling 178 → 297, 142 → 228, 158 → 205, 98 → 210, 134 → 228, 170 → 340, 134 → 316 (342 bis 380 Ziele, nahezu alle verdeckt). Hub und offene Sicht im Rauschen; diese Messreihe schwankte stark (Läufe gleicher Einstellung bis etwa 25 % auseinander). Je Version 240 Ein/Aus-Prüfungen ohne Fehler. Ein erster Durchlauf scheiterte an der Szene (Zombies verbrannten ohne /tick freeze, in 1.20.4 fehlte die Wand); nach der Korrektur (Husks, Wand-Prüfung) bestanden.
- **Wolken (1.20.6 bis 1.17.1):** 11 bis 14 Neuaufbauten in 8 s ohne, 0 bis 1 mit Optimierung; ein FPS-Gewinn ist nicht belegt (in 1.20.4 lagen beide Läufe mit Optimierung niedriger, 207 und 212 gegen 231 und 249, bei allgemein hoher Schwankung).
- **Smoke-Test** (normal, stumm) für 1.20.1 und 1.16.5 bestanden.
- **Nicht geprüft:** Namensschild-Icons der Versionen 1.20.6 bis 1.16.5 im Spiel (die Testinstanzen haben kein Voice-Addon), Wolken-Screenshot-Vergleich für diese Versionen, ein Selbst-Update von 0.3.62.
- **Release:** `SHA256SUMS.txt.sig` gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler.

**0.3.62 (2026-11-16):**
- **Build:** `gradlew clean build :launcher-core:integrationTest` erfolgreich, 638 Unit-Tests ohne Fehler (1 übersprungen). `integrationTest` enthält weiterhin keine Tests.
- **Messszene** (stumm, Test-Home, FPS-Limit aus) für 1.21.11, 1.21.8, 1.21.4 und 1.21: hinter der Wand Ø FPS aus → nur Culling 225 → 604, 319 → 836, 302 → 597, 236 → 365 (Wiederholung 260 → 408); Hub ≈ +15 bis 25 %; offene Sicht im Rauschen. Je Version 240 Ein/Aus-Prüfungen ohne Fehler. Ein erster 1.21-Lauf mit unvollständig aufgebauter Szene (232 statt etwa 345 Ziele) hid nur 6; in zwei Wiederholungen 345 und 347 von 347.
- **Wolken (1.21):** bei 10-fach beschleunigtem Sonnenuntergang 61 und 63 Neuaufbauten in 8 s ohne, 0 mit Optimierung; FPS-Unterschied im Rauschen. Screenshots 2 Ticks auseinander: Wolkenpixel weichen um höchstens 1 von 255 Stufen ab, zwei Aufnahmen ohne Optimierung sind gleich.
- **Smoke-Test** (normal, stumm) für 1.21.11 (mit Namensschild-Icons) und 1.21 bestanden.
- **Nicht geprüft:** Namensschild-Icons in 1.21.8, 1.21.4 und 1.21 im Spiel; ein Selbst-Update von 0.3.61.
- **Release:** `SHA256SUMS.txt.sig` gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler.

**0.3.61 (2026-11-16):**
- **Build:** `gradlew clean build :launcher-core:integrationTest` erfolgreich, 638 Unit-Tests ohne Fehler (1 übersprungen). Neu: 11 Tests für den Occlusion-Culler (Wand, Fenster, Nähe, Kamera im Block, veraltete Ergebnisse, Block-Änderungen, Ein/Aus), ein Pixel-Vergleich der schnellen abgerundeten Flächen über 10.000 Größen, ein Test für den Hub-Karten-Cache, 2 Loader-Tests (Schalter aus den Einstellungen, gleiche Klasse und gleiche CodeSource mit schnellem Start). `integrationTest` enthält weiterhin keine Tests.
- **Messszene** (`-Dlumora.perfBench=true`, stumm, Test-Home, FPS-Limit aus) für 26.3, 26.2 und 26.1.2 bestanden: hinter einer Wand mit 225 Zombies und Truhen Ø FPS alles aus → nur Culling 229 → 551 (26.3), 269 → 801 (26.2), 252 → 1115 (26.1.2); Hub offen ≈ 119 → 133 / 126 → 159 / 119 → 135; offene Sicht ohne Verdecktes im Rauschen (Läufe gleicher Einstellung schwanken bis etwa 13 %, Culling-Hooks laut Profiler etwa 1,5 % der Render-Thread-Zeit). Screenshots Culling aus/an (offene Sicht, Fenster in der Wand) unterscheiden sich nur in wechselnden HUD-Werten. Je Version 240 Ein/Aus-Prüfungen von Modul und Schaltern ohne Fehler.
- **Start** (26.3, je 3 Starts, `-Dlumora.perfBench=startup`): Klassen lesen etwa 400 statt 670 ms, insgesamt etwa 0,3 bis 0,4 s weniger CPU-Zeit beim Laden; Zeit bis zum Hauptmenü (etwa 12 s) auf die Sekunde gemessen unverändert.
- **Smoke-Test** (normal, stumm, Test-Home) für 26.3 und 1.8.9 bestanden, Namensschild-Icons gezeichnet, Modul „Performance“ im Hub (36 Module).
- **Nicht geprüft:** die übrigen 12 Minecraft-Versionen im Spiel (dort nur Smart Menüs und schnellerer Start), ein Selbst-Update von 0.3.60.
- **Release:** `SHA256SUMS.txt.sig` gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler.

**0.3.60 (2026-11-16):**
- **Build:** `gradlew clean build :launcher-core:integrationTest` erfolgreich, 623 Unit-Tests ohne Fehler (1 übersprungen). Neu: Test, dass Icons mit eigenem Bild (Baublase) als 128×128-PNG vorhanden und nicht zusätzlich im Sprite-Sheet sind. `integrationTest` enthält weiterhin keine Tests.
- **Smoke-Test** (stumm, Test-Home) für 26.3 bestanden. Hub › Module auf dem Screenshot geprüft: Baublase zeigt das „B“-Icon.
- **Nicht geprüft:** die übrigen 15 Minecraft-Versionen im Spiel (das Bild liegt in Lumora Core und ist in allen Client-JARs enthalten), ein Selbst-Update von 0.3.59.
- **Release:** `SHA256SUMS.txt.sig` gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler.

**0.3.59 (2026-11-16):**
- **Build:** `gradlew clean build :launcher-core:integrationTest` erfolgreich, 622 Unit-Tests ohne Fehler (1 übersprungen). Neu: 4 Tests für das Icon-Pack (Namen passen zu `icons.txt`, jedes Feld hat Pixel, eingebaute Module und Widgets haben ein eigenes Icon, bekannte Addons ihr Icon, andere das Logo). Hinweis: `integrationTest` enthält seit `1abd043` keine Tests mehr und läuft leer durch.
- **Smoke-Test** (stumm, Test-Home) für 26.3 und 1.8.9 bestanden. Hub › Module und Hub › Addons auf den Screenshots geprüft: neue Icons in beiden Versionen, Baublase mit Münz-Icon, LumoraVoice mit Mikrofon, das Beispiel-Addon „Session Timer“ mit dem Lumora-Logo.
- **Nicht geprüft:** die übrigen 14 Minecraft-Versionen im Spiel, das Spotify-Icon im Spiel (Addon nicht im Test-Home).
- **Release:** `SHA256SUMS.txt.sig` gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler.

**0.3.58 (2026-11-15):**
- **Build:** `gradlew clean build :launcher-core:integrationTest` erfolgreich, 618 Unit-Tests ohne Fehler (1 übersprungen). Neu: Test für `NewsFeed.updatePicture` (Release der Hauptversion, sonst ältester Patch, Snapshots zählen nicht). Nur Launcher geändert, Client und Mixins unverändert.
- **Oberfläche:** Startseite und Einstellungen › Appearance per `--ui-shot` geprüft: Drache rechts im Kopfbereich, schmale Seitenleiste mit Namen unter den Symbolen, Einstellung „Sidebar“ entfernt. Versionsraster mit den echten Update-Bildern aus den Patchnotes gerendert (26.3 bis 1.16 mit Bild, 1.12 und 1.8 mit eigenem Pixel-Bild).
- **Kein Smoke-Test** (nur Launcher-Oberfläche geändert).
- **Nicht geprüft:** das Versionsmenü im laufenden Launcher geöffnet, ein Selbst-Update von 0.3.57 auf 0.3.58.
- **Release:** `SHA256SUMS.txt.sig` gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler.

**0.3.57 (2026-11-15):**
- **Build:** `gradlew clean build :launcher-core:integrationTest` erfolgreich, 617 Unit-Tests ohne Fehler (1 übersprungen). Nur Launcher geändert (`HeroPanel`: fester Berg-Hintergrund, Karussell entfernt), Client und Mixins unverändert.
- **Oberfläche:** Startseite per `--ui-shot` geprüft: Berg-Hintergrund, keine Punkte mehr.
- **Kein Smoke-Test** (nur der Hintergrund der Startseite geändert).
- **Nicht geprüft:** ein Selbst-Update von 0.3.56 auf 0.3.57.
- **Release:** `SHA256SUMS.txt.sig` gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler.

**0.3.56 (2026-11-15):**
- **Build:** `gradlew clean build :launcher-core:integrationTest` erfolgreich, 617 Unit-Tests ohne Fehler (1 übersprungen). Nur Launcher geändert (neue Startseite `HomeDashboard`, Szenen-Karussell in `HeroPanel`, Glocke in der Seitenleiste), Client und Mixins unverändert.
- **Oberfläche:** Startseite des fertigen Builds per `--ui-shot` geprüft, mit und ohne Spielzeit (Beispielwert im Test-Datenordner), mit Beschriftung und nur Symbolen: alles bei 1320 × 840 ohne Scrollen sichtbar, alle Texte aus `en_us.json`. Die fünf Hintergrund-Szenen einzeln gerendert. News- und Bibliotheksseite unverändert.
- **Kein Smoke-Test** (auf Wunsch weggelassen).
- **Nicht geprüft:** die Downloads-Karte während einer echten Installation (braucht ein angemeldetes Konto), „Continue playing“ direkt in eine Welt, Karussell und Klicks im laufenden Launcher, ein Selbst-Update von 0.3.55 auf 0.3.56.
- **Release:** `SHA256SUMS.txt.sig` gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler.

**0.3.55 (2026-11-15):**
- **Build:** `gradlew clean build :launcher-core:integrationTest` erfolgreich, 617 Unit-Tests ohne Fehler (1 übersprungen). Launcher-Texte aus `lumora/lang/en_us.json` (Klasse `Lang`), 7 Testdateien auf die englischen Texte angepasst. Nur Launcher geändert, Client und Mixins unverändert.
- **Oberfläche:** Alle Seiten des fertigen Builds per `--ui-shot` geprüft (19 Ansichten): nur englische Texte, keine fehlenden Schlüssel, Tabs/Knöpfe/Felder/Dropdowns gleich hoch. Ein Skript prüft, dass jeder im Code benutzte Schlüssel in `en_us.json` steht.
- **Smoke-Test mit dem fertigen 0.3.55-Build:** 26.3 bestanden (Installations- und Startmeldungen jetzt englisch).
- **Nicht geprüft:** Dropdowns und Tabs per Klick im laufenden Launcher, das moderne Design ohne Pixel-Schrift (nur Pixel-Design per Screenshot), die übrigen Versionen im Spiel, ein Selbst-Update von 0.3.54 auf 0.3.55.
- **Release:** `SHA256SUMS.txt.sig` gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler.

**0.3.54 (2026-11-15):**
- **Build:** `gradlew clean build :launcher-core:integrationTest` erfolgreich, 617 Unit-Tests ohne Fehler (1 übersprungen). Off Hand: Schalter „Haupthand anzeigen“ entfernt (neuer Test: die Haupthand lässt sich nicht ausschalten, nur Schild/Totem darin; die Tests zur Zweithand angepasst). Mixins unverändert.
- **Smoke-Test mit dem fertigen 0.3.54-Build:** 26.3, 1.21.11, 1.16.5 und 1.12.2 bestanden, Schritt „off-hand“: Schild ausgeblendet, dann die ganze Zweithand (Screenshots: Schwert in der Haupthand bleibt sichtbar), dann beide leeren Hände (9–42 Bilder je Schritt); in 26.3 auch Haupthand links.
- **Nicht geprüft:** die übrigen 11 Versionen im Spiel (nur kompiliert), Schalter im Hub, ein Selbst-Update von 0.3.53 auf 0.3.54.

**0.3.53 (2026-11-15):**
- **Build:** `gradlew clean build :launcher-core:integrationTest` erfolgreich, 616 Unit-Tests ohne Fehler (1 übersprungen). Neu: Off Hand „Beide Hände anzeigen“ (1 Test: Standard an, aus bei ausgeschalteter Einstellung, ausgeblendeter Zweithand oder Modul aus; Schalter nur sichtbar, solange die Zweithand angezeigt wird). `check-mixins.pl`: `OffHandMixin` mit dem neuen `renderPlayerArm`-Aufruf in 1.12.2, 1.16.5, 1.21.8, 1.21.11, 26.2 und 26.3 OK.
- **Smoke-Test mit dem fertigen 0.3.53-Build:** 26.3, 1.21.11, 1.16.5 und 1.12.2 bestanden, Schritt „off-hand“ um beide leeren Hände erweitert: die leere Zweithand wurde gezeichnet (9–42 Bilder), Screenshots zeigen beide Arme; in 26.3 zusätzlich mit Haupthand links (Arme auf beiden Seiten). Schild und Haupthand ausblenden weiter OK.
- **Nicht geprüft:** Beide Hände in den übrigen 11 Versionen im Spiel (nur kompiliert), Haupthand links außer in 26.3, Fernrohr und Unsichtbarkeit, ein Selbst-Update von 0.3.52 auf 0.3.53.

**0.3.52 (2026-11-15):**
- **Build:** `gradlew clean build :launcher-core:integrationTest` erfolgreich, 615 Unit-Tests ohne Fehler (1 übersprungen). Neu: Modul „Off Hand“ (5 Tests: Standard blendet nichts aus, ausgeblendete Hand samt leerem Arm und jedem Gegenstand, Schild und Totem je Hand getrennt, Schalter für Schild/Totem nur sichtbar solange die Hand angezeigt wird, Modul aus blendet nichts aus). `check-mixins.pl`: neue `OffHandMixin` in allen 15 Versionen ab 1.12.2 OK (`renderArmWithItem` 1.16.5–26.1.2, `submitArmWithItem` 26.2/26.3, `method_12329` 1.12.2).
- **Smoke-Test mit dem fertigen 0.3.52-Build:** 26.3, 1.21.11, 1.16.5 und 1.12.2 bestanden, neuer Schritt „off-hand“ (Schwert in der Haupthand, Schild in der Zweithand): mit den Standardeinstellungen nichts ausgeblendet, dann der Schild in der Zweithand (9–35 Bilder übersprungen), dann die ganze Haupthand (9–35 Bilder). Screenshots zeigen beide Hände, ohne Schild und ohne Haupthand.
- **Nicht geprüft:** Off Hand in 1.17.1, 1.18.2, 1.19.4, 1.20.1, 1.20.4, 1.20.6, 1.21, 1.21.4, 1.21.8, 26.1.2 und 26.2 im Spiel (nur kompiliert und Mixin-Ziele geprüft; gleiche Methode wie in den getesteten Versionen), das Totem im Spiel (nur Unit-Tests), Schild beim Blocken, die Schalter im Hub, ein Selbst-Update von 0.3.51 auf 0.3.52.

**0.3.51 (2026-11-14):**
- **Build:** `gradlew clean build :launcher-core:integrationTest` erfolgreich, 610 Unit-Tests ohne Fehler (1 übersprungen). Neu: Modul „Light Block Overlay“ (9 Tests: Rot/Gelb/sicher nach Block- und Himmelslicht, Lichtstufe 0 ab 1.18 und 7 davor, nächste Chunks zuerst, Zeitbudget pro Tick, erneutes Lesen nach Abstand, Geometrie je Darstellung, Zahlen aufrecht und nur bis 16 Blöcke, vertikale Reichweite, ausgeblendet/ohne Welt kein Lesen). `check-mixins.pl`: neue `LightOverlayMixin` in allen 16 Versionen OK.
- **Smoke-Test mit dem fertigen 0.3.51-Build:** 26.3, 1.21.11, 1.21.8, 1.20.1, 1.16.5 und 1.8.9 bestanden, neuer Schritt „light-overlay“ (Kreuze, dann Zahlen, Blick zum Boden): 25 Chunks, 4485–5823 Spawn-Positionen, 8856–10728 Linien; ein Chunk-Scan im Mittel 0,36–0,53 ms, längster Tick 1,9–3,4 ms (nur die ersten, noch nicht vom JIT übersetzten Ticks über 1,5 ms: 1–4 Ticks). Screenshots zeigen die gelben Kreuze auf dem Gras (1.21.8, 1.16.5, 26.3) und die Zahlen (1.8.9, 26.3). Der Elytra-Schritt setzt den Testspieler jetzt auch in 1.16.5, 1.20.1, 1.21.4 und 1.21.11 auf den Boden zurück.
- **Nicht geprüft:** Light Block Overlay in 1.12.2, 1.17.1, 1.18.2, 1.19.4, 1.20.4, 1.20.6, 1.21, 1.21.4, 26.1.2 und 26.2 im Spiel (nur kompiliert und Mixin-Ziele geprüft), rote Markierungen in dunklen Höhlen und bei Nacht (die Testwelt ist am Tag), die Darstellungen Fläche und Markierung im Spiel (nur Unit-Tests), die Taste zum Ein- und Ausblenden, ein Selbst-Update von 0.3.50 auf 0.3.51. Ab 1.21.4 ist das Overlay aus, wenn Minecraft keine Block-Umrisse zeichnet (F1, Abenteuermodus, Zuschauer).
- **Release:** `SHA256SUMS.txt.sig` mit dem Release-Schlüssel signiert und gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler.

**0.3.50 (2026-11-14):**
- **Build:** `gradlew clean build :launcher-core:integrationTest` erfolgreich, 601 Unit-Tests ohne Fehler (1 übersprungen). Neu: HUD-Editor wie bei LabyMod – Papierkorb in der Bildschirmmitte (Widget dort loslassen = aus dem HUD, alte Position bleibt, ein Rückgängig-Schritt), Hinzufügen aus der Seitenleiste per Klick (an die letzte Stelle) oder Ziehen (unter die Maus, über dem Papierkorb losgelassen = nicht hinzugefügt), Schließen während des Ziehens entfernt nichts; der Schalter „Im HUD anzeigen“ in den Hub-Einstellungen ist entfernt.
- **Smoke-Test mit dem fertigen 0.3.50-Build:** 26.3 und 1.8.9 bestanden. Neuer Schritt in 26.3 `hud-editor-trash`: `LUMORA_SMOKE_TRASH over=true removed=true backAtOldPlace=true`; der Screenshot zeigt den roten Papierkorb mit „Drop here to remove“.
- **Nicht geprüft:** echte Mausbedienung im Editor (der Test steuert das Modell direkt, deshalb ist im Screenshot die Seitenleiste noch zu sehen), der Editor in 1.12.2 und 1.16.5 bis 26.2 im Spiel (nur kompiliert), ein Selbst-Update von 0.3.49 auf 0.3.50.
- **Release:** `SHA256SUMS.txt.sig` mit dem Release-Schlüssel signiert und gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler.

**0.3.49 (2026-11-14):**
- **Build:** `gradlew clean build :launcher-core:integrationTest` erfolgreich, 598 Unit-Tests ohne Fehler (1 übersprungen). Neu: Chat-Eingabe-Knöpfe liegen in der Eingabezeile (rechts, 12 px hoch), das Textfeld endet 3 px vor dem ersten Knopf und wird wieder breiter, wenn ein Knopf verschwindet. `check-mixins.pl` ohne neue Meldungen (neuer Accessor `TextFieldWidgetAccessor` in 1.8.9/1.12.2).
- **Smoke-Test mit dem fertigen 0.3.49-Build:** 26.3, 1.16.5 und 1.8.9 bestanden. Neue Prüfung `LUMORA_CHAT_INPUT`: Textfeld 393 statt 423 px breit (zwei Knöpfe: Emoji und Session-Timer); Screenshots zeigen beide Knöpfe rechts in der Eingabezeile und die Emoji-Auswahl darüber. Der Elytra-Schritt setzt den Testspieler in 26.3 jetzt auf den Boden zurück; vorher stürzte er ab und starb vor den Chat-Schritten.
- **Nicht geprüft:** echte Mausklicks auf die Knöpfe (der Test öffnet die Auswahl per Code), 1.12.2 und 1.17.1 bis 26.2 im Spiel (nur kompiliert), ein Selbst-Update von 0.3.48 auf 0.3.49.
- **Release:** `SHA256SUMS.txt.sig` mit dem Release-Schlüssel signiert und gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler.

**0.3.48 (2026-11-14):**
- **Build:** `gradlew clean build` erfolgreich, 597 Unit-Tests ohne Fehler (1 übersprungen). Neu: Freelook (Mausbewegung dreht nur die freie Kamera, 0,15° pro Einheit wie `Entity#turn`, Pitch zwischen -90° und 90°; Halten und Umschalten; dritte Person beim Start, vorige Perspektive beim Ende; F5 und geänderte Ansicht während Freelook; außerhalb einer Welt nichts; Modul anfangs aus). Alle 16 Client-Module kompiliert, `check-mixins.pl` für alle Freelook-Mixins in allen 16 Versionen ohne fehlende Ziele.
- **Vor dem Release** (Entwicklungsstand, Testordner): Smoke-Test 26.3, 1.16.5, 1.12.2 und 1.8.9 bestanden mit dem neuen Schritt „freelook“ (freie Kamera 180° um den Spieler gedreht). 26.3 und 1.16.5: Spieler bleibt bei 90°/15°, Kamera steht auf 270° wie die freie Kamera; Screenshot 1.16.5 zeigt den Spieler von vorne. 1.8.9: Spieler bleibt bei 0°/70°. Ein erster Lauf fand einen Absturz in 1.8.9 (Umleitung in der statischen Methode `Camera#update` muss statisch sein), behoben und erneut geprüft.
- **Smoke-Test mit dem fertigen 0.3.48-Build:** 1.8.9 und 26.3 – bestanden, gleiche Werte wie oben.
- **Nicht geprüft:** echte Maus- und Tasteneingabe im Spiel (der Smoke-Test dreht die Kamera direkt), Freelook in 1.17.1 bis 26.2 im Spiel (nur kompiliert und Mixin-Ziele geprüft), sichtbare Chunks beim Drehen in 1.8.9/1.12.2 über längere Zeit, der 26.3-Screenshot (Testspieler im Demo-Level gestorben), ein Selbst-Update von 0.3.47 auf 0.3.48.
- **Release:** `SHA256SUMS.txt.sig` mit dem Release-Schlüssel signiert und gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler.

**0.3.47 (2026-11-14):**
- **Build:** `gradlew clean build` erfolgreich, 590 Unit-Tests ohne Fehler (1 übersprungen). Neu: Lumora API 1.9 (Knöpfe und Fenster an der Chat-Eingabe, Icons im Chat, doppelte Ids abgelehnt, Entfernen beim Abschalten), `:name:`-Erkennung im Chattext und Rückwandlung beim Kopieren, Emoji-Liste und Suche (1870 Twemoji), Emoji-Auswahl (Tabs, Zeilen-Scrollen, Größenänderung), Vorschläge beim Tippen, Scissor-Stapel für alte Versionen (verschoben und verschachtelt). Alle 16 Client-Module kompiliert, `check-mixins.pl` für ChatInputMixin und ChatMixin ohne fehlende Ziele.
- **Vor dem Release** (Entwicklungsstand, Testordner): Smoke-Test 26.3, 1.16.5 und 1.8.9 bestanden mit dem neuen Schritt „chat-emoji“. In 26.3 und 1.16.5 zeigt der Chat „Emoji-Test 😄👍❤️😂 um 12:30“, in 1.8.9 bleibt der Text. Auswahl mit Raster und Vorschläge zu `:jo` im Bild, ebenso der Knopf des Beispiel-Addons.
- **Smoke-Test mit dem fertigen 0.3.47-Build:** 1.16.5 und 1.8.9 – bestanden (mit der Scissor-Korrektur): `LUMORA_EMOJI glyphs=true panel=true` bzw. `glyphs=false panel=true`, Auswahl in 1.8.9 vollständig mit Tabs, Raster und Fußzeile.
- **Nicht geprüft:** die Auswahl in 26.3 im Bild (der Testspieler stirbt im Demo-Level in diesem Schritt, laut Log ist sie offen), Emojis in 1.12.2 und 1.17.1 bis 26.2 im Spiel (nur kompiliert), Emojis bei echter Bildschirmgröße, ein Selbst-Update von 0.3.46 auf 0.3.47.
- **Release:** `SHA256SUMS.txt.sig` mit dem Release-Schlüssel signiert und gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler.

**0.3.46 (2026-11-14):**
- **Build:** `gradlew clean build` erfolgreich, 566 Unit-Tests ohne Fehler (1 übersprungen). Neu: Armor HUD (fünf Widgets mit eigenen Plätzen, Haltbarkeit als Rest, Rest/Maximum und Prozent, Anzahl bei Items ohne Haltbarkeit, Diamant-Vorschau außerhalb der Welt, leere Plätze ausgeblendet außer im HUD-Editor, Modul anfangs aus), eine Karte „Armor HUD“ mit fünf Abschnitten im Hub, Modulnamen und -beschreibungen in allen 32 Sprachdateien der 16 Versionen (Namen in Deutsch und Englisch gleich). Alle 16 Client-Module kompiliert (Ausrüstung lesen in vier Varianten: 1.8.9, 1.12.2, 1.16.5–1.21.11, 26.x).
- **Vor dem Release** (Entwicklungsstand, Testordner, Armor HUD dort eingeschaltet): Smoke-Test 26.3 und 1.8.9 bestanden, Prüfung im Smoke-Test: 8 eingebaute Widgets und 5 Armor-HUD-Widgets. Im Spiel zeigt das Held-Item-Widget das Diamantschwert mit „1561“ in Grün, in 26.3 und in 1.8.9. Der Hub zeigt die Karte „Armor HUD“ und die englischen Namen.
- **Smoke-Test mit dem fertigen 0.3.46-Build:** 26.3 – bestanden (Client-Module dieses Builds; danach nur noch die News im Launcher ergänzt).
- **Nicht geprüft:** getragene Rüstung im Spiel (der Smoke-Test gibt nur ein Schwert), Rüstung in den Versionen 1.12.2 bis 1.21.11 im Spiel (nur kompiliert), die neue Standardposition links bei echter Bildschirmgröße, ein Selbst-Update von 0.3.45 auf 0.3.46.
- **Release:** `SHA256SUMS.txt.sig` mit dem Release-Schlüssel signiert und gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler.

**0.3.45 (2026-11-14):**
- **Build:** `gradlew clean build` erfolgreich, 559 Unit-Tests ohne Fehler (1 übersprungen). Neu: Grafik-Backend (nur ab 26.2, Wert ersetzen/anlegen, „Standard“ lässt die Datei in Ruhe), Download-Cache-Grenze und Log-Aufbewahrung, Thread-Dump bei Hänger (einmal pro Hänger, neu scharf nach dem nächsten Tick), Lumora-Einstellungen zurücksetzen (als Sicherung) und Instanzen wiederherstellen, Update-Kanal „Vorab“ (Vorabversionen ja, Entwürfe nie) gegen einen lokalen Testserver. Alle 16 Client-Module kompiliert; im Spiel nur lumora-core (HangWatchdog, aus ohne `-Dlumora.hangDump`).
- **Vor dem Release** (Entwicklungsstand, Testordner): Smoke-Test 26.3 mit Grafik-Backend „Vulkan“ bestanden, `options.txt` vom Launcher auf `preferredGraphicsBackend:"vulkan"` gesetzt, Minecraft meldet „Using graphics backend Vulkan“; ebenso mit „OpenGL“ („Using graphics backend OpenGL“). Startseite offscreen gerendert: Quick-Play-Leiste mit den Servern und Favicons aus der Serverliste; Einstellungen › Spiel und Speicher mit den neuen Zeilen.
- **Smoke-Test mit dem fertigen 0.3.45-Build:** 26.3 – bestanden.
- **Nicht geprüft:** ein Klick auf einen Quick-Play-Server; die Rückfrage-Dialoge (Zurücksetzen, Ingame-Erlebnis); der Thread-Dump bei einem echten Hänger im Spiel; der Update-Kanal „Vorab“ gegen GitHub (es gibt noch keine Vorabversion); ein echtes Selbst-Update von 0.3.44 auf 0.3.45.
- **Release:** `SHA256SUMS.txt.sig` mit dem Release-Schlüssel signiert und gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler.

**0.3.44 (2026-11-14):**
- **Build:** `gradlew clean build` erfolgreich, 550 Unit-Tests ohne Fehler (1 übersprungen). Neu: Format der eingebauten IDs (`AppIdsTest`), Discord bekommt Lumoras eigene Anwendungs-ID; der Test für eine gespeicherte Client-ID entfällt. Nur Launcher geändert, keine Client-Module.
- **Vor dem Release:** Einstellungen › Integrationen offscreen gerendert: nur noch „Server in Discord zeigen“ und die übrigen Punkte, keine Felder für Anwendungs-IDs.
- **Smoke-Test mit dem fertigen 0.3.44-Build:** 26.3 – bestanden (Demo-Modus, ohne Anmeldung).
- **Nicht geprüft:** eine echte Microsoft-Anmeldung mit der eingebauten Anwendungs-ID (braucht ein Konto und die Freischaltung der App durch Mojang); die Anzeige in einer laufenden Discord-App (nur per Test mit nachgebautem Discord-Protokoll); ein echtes Selbst-Update von 0.3.43 auf 0.3.44.
- **Release:** `SHA256SUMS.txt.sig` mit dem Release-Schlüssel signiert und gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler.

**0.3.43 (2026-11-14):**
- **Build:** `gradlew clean build` erfolgreich, 549 Unit-Tests ohne Fehler (1 übersprungen); ein Test erwartet nach dem Abschalten eines Addon-Akzents jetzt den neuen Standard Blau. Alle 16 Client-Module kompiliert; keine Mixin-Ziele geändert (in den Mixins nur Farbwerte).
- **Umfärben im Spiel:** Farbwerte im Code per Werkzeug ersetzt (Design-Farben exakt auf die Launcher-Farben, übrige Violett-Töne per Farbton-Drehung von 258° auf 217°); bewusst nicht: die Farbpalette der Einstellungen, die Akzentliste (Lila bleibt), Rollenfarben (Social), das Lumora-Logo. 926 Texturen (Theme-Pakete, Hub-Icons) gleich umgefärbt, Menü-Hintergründe mit `.t/BgGen.java` aus der Launcher-Szene neu erzeugt, Menü-Shader-Farben ersetzt.
- **Vor dem Release** (Entwicklungsstand): Smoke-Test 26.3 bestanden, Hauptmenü, Hub und Themes in Blau gesichtet (gespeichertes „purple“ wurde einmalig zu „blue“, Lila in der Auswahl). Launcher offscreen gerendert: Startseite, Einstellungen, Konto, Freunde im Pixel-Design; Startseite mit ausgeschaltetem Pixel-Design wie bisher.
- **Smoke-Test mit dem fertigen 0.3.43-Build:** nur 26.3 – bestanden. Andere Versionen wurden nicht im Spiel getestet.
- **Nicht geprüft:** andere Versionen im Spiel (vor allem die Theme-Texturen von 1.8.9 bis 1.20); nicht gerenderte Launcher-Seiten (Server, Performance, Bibliothek, News, Dialoge); der Splash beim ersten Start nach dem Update; ein echtes Selbst-Update von 0.3.42 auf 0.3.43.
- **Release:** `SHA256SUMS.txt.sig` mit dem Release-Schlüssel signiert und gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler.

**0.3.42 (2026-11-14):**
- **Build:** `gradlew clean build` erfolgreich, 549 Unit-Tests ohne Fehler (1 übersprungen). Neu: 7 für `InstanceLayout` (Standard-Ordner mit gemeinsamen Einstellungen, eigene Instanz, beide Einstellungsarten unabhängig vom Spielordner, einmalige Übernahme vorhandener Instanzen, Neuinstallation, Mitnehmen der Einstellungen ohne Überschreiben, ungültige Instanz-IDs). Alle 16 Client-Module kompiliert; keine Mixins geändert (nur `LoaderEnv`, `McPlatform`, `Packs`, bei 1.8.9/1.12.2 `JsonLang`).
- **Vor dem Release** (Entwicklungsstand, Testordner): Smoke-Test 26.3 als übernommene eigene Instanz bestanden (Spiel weiter in `instances/26.3`); Smoke-Test 26.3 im Standard-Ordner mit gemeinsamen Einstellungen bestanden (Session-Timer aus `data/shared/addons` geladen, Client-JARs, Natives und Statusdatei in `data/26.3`, im Spielordner nur Minecraft-Dateien und die Sperrdatei). Versionsmenü offscreen gerendert.
- **Smoke-Test mit dem fertigen 0.3.42-Build:** nur 26.3 (übernommene eigene Instanz) – bestanden. Andere Versionen wurden nicht im Spiel getestet.
- **Installer** (lumora-installer, eigener Commit): mit eingebauter Versionsliste gebaut, stille Neuinstallation dieses Builds im Sandbox-Modus: Übergabe `dataDir = <LUMORA_HOME>\minecraft\LumoraClient`, der installierte Launcher übernimmt den Ordner (`installDir`, keine alten Instanzen); stilles Deinstallieren ohne Fehler.
- **Nicht geprüft:** andere Versionen im Standard-Ordner (vor allem 1.8.9 mit einer `options.txt` von 26.3); das Umschalten per Klick im echten Launcher-Fenster; macOS und Linux; ein echtes Selbst-Update von 0.3.41 auf 0.3.42.
- **Release:** `SHA256SUMS.txt.sig` mit dem Release-Schlüssel signiert und gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler.

**0.3.41 (2026-11-13):**
- **Build:** `gradlew clean build` erfolgreich, 542 Unit-Tests ohne Fehler (1 übersprungen). Alle 16 Client-Module kompiliert; keine Mixins geändert. Geändert sind nur Reihenfolge und Abschnitte der Einstellungen (Module, Zoom, Widgets in `Panels` und allen 16 `BuiltInWidgets`) sowie 14 neue Abschnittsnamen in allen 32 Sprachdateien (alle als JSON geprüft).
- **Vor dem Release** (Entwicklungsstand): Reihenfolge aller eingebauten Module und Widgets per Test ausgegeben und durchgesehen; Smoke-Test 26.3 bestanden, Angriffsanzeige-Einstellungen in der Aufnahme mit „Anzeigen“, „Aussehen“, „Farben“.
- **Smoke-Test mit dem fertigen 0.3.41-Build:** nur 26.3 – bestanden. Die anderen Versionen wurden nicht im Spiel getestet.
- **Nicht geprüft:** die übrigen Einstellungsbildschirme im Spiel; andere Versionen im Spiel; ein echtes Selbst-Update von 0.3.40 auf 0.3.41.
- **Release:** `SHA256SUMS.txt.sig` mit dem Release-Schlüssel signiert und gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler.

**0.3.40 (2026-11-13):**
- **Build:** `gradlew clean build` erfolgreich, 542 Unit-Tests ohne Fehler (1 übersprungen). Neu: 3 für die Lumora-API 1.8 (Texturen unter dem eigenen Namen des Addons, ersetzen, freigeben, beim Abschalten alle frei; Namen, Größe, Pixelzahl und Obergrenze von 32 geprüft; `null` auf Versionen ohne Texturen). Alle 16 Client-Module kompiliert; keine Mixins geändert (die API nutzt das vorhandene `uploadTexture` aller Versionen). Enthält den bereits gepushten neuen Windows-Installer (Commit 38a1449, eigene Sitzung); `setupExe` wurde für dieses Release nicht gebaut, veröffentlicht werden wie bisher `LumoraClient.exe` und die Prüfsummen.
- **Vor dem Release** (Entwicklungsstand): Smoke-Test 26.3 mit Spotify 1.1.0 im Addons-Ordner bestanden; im Spiel zeigt das Widget das Cover als Textur (scharf), Akzentfarbe aus dem Cover. Spotify-Addon gegen eine Laufzeit mit API 1.5 geprüft: fällt ohne Fehler auf das Raster-Cover zurück.
- **Smoke-Test mit dem fertigen 0.3.40-Build:** nur 26.3 – bestanden, Lumora API 1.8.0, Spotify 1.1.0 aktiv, scharfes Cover in der Aufnahme. Die anderen Versionen wurden nicht im Spiel getestet.
- **Nicht geprüft:** Laufzeit-Texturen auf anderen Minecraft-Versionen als 26.3 im Spiel; der neue Installer (`setupExe`); ein echtes Selbst-Update von 0.3.39 auf 0.3.40.
- **Release:** `SHA256SUMS.txt.sig` mit dem Release-Schlüssel signiert und gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler.

**0.3.39 (2026-11-13):**
- **Build:** `gradlew clean build` erfolgreich, 530 Unit-Tests ohne Fehler (1 übersprungen). Neu bzw. geändert: Discord-Test antwortet wie die echte Discord-App (`"evt": null`), Verbindung bleibt und wird wiederverwendet, abgelehnte Anwendungs-ID trennt; Statusdatei des Spiels (Menü, Server ohne Standard-Port, Einzelspieler, Startzeit bleibt auf demselben Server); Lesen der Datei im Launcher (Server-Zeile, „Mehrspieler“ bei ausgeblendetem Server, kaputte Dateien und seltsame Server-Namen werden ignoriert). Alle 16 Client-Module kompiliert; keine Mixins geändert. Gebaut aus einem eigenen Worktree auf dem Feature-Commit (ohne die nicht committeten Installer-Änderungen einer anderen Sitzung).
- **Mit der echten Discord-App:** Verbindung mit der eingetragenen Anwendungs-ID, Aktivität gesetzt, Verbindung bleibt bestehen, danach gelöscht (Entwicklungsstand). Vorher brach die Verbindung mit „JsonNull“ ab.
- **Smoke-Test mit dem fertigen 0.3.39-Build:** nur 26.3 (Änderung im Kern) – bestanden; der Launcher übergibt `-Dlumora.statusFile`, das Spiel schrieb `singleplayer` mit Version 26.3. Die anderen Versionen wurden nicht im Spiel getestet.
- **Nicht geprüft:** die Discord-Anzeige während des Spiels auf einem echten Server („auf <Server>“) und ob Discord daneben seine eigene Spielerkennung „Minecraft“ zeigt; ein echtes Selbst-Update von 0.3.38 auf 0.3.39.
- **Release:** `SHA256SUMS.txt.sig` mit dem Release-Schlüssel signiert und gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler.

**0.3.38 (2026-11-13):**
- **Build:** `gradlew clean build` erfolgreich, alle Unit-Tests ohne Fehler. Neu: 2 für die Lumora-API 1.7 (Zeilen am Namensschild vom ersten Decorator mit Zeilen, leere Zeilen zählen nicht; Tooltip-Zeilen von Addons, ohne Listener keine Arbeit, Listener verschwindet mit dem Addon). Alle 16 Client-Module kompiliert; geändert: `NameTagBesideMixin` (bzw. `NameTagIconMixin` auf 1.8.9) in allen Versionen, neu `ItemTooltipMixin` in allen Versionen. `check-mixins.pl`: Ziele vorhanden (die gemeldeten Abweichungen betreffen Handler ohne Zielparameter, wie bei den bestehenden Mixins).
- **Vor dem Release** (Entwicklungsstand): Smoke-Test 26.3, 26.2, 26.1.2, 1.21.11, 1.21.8, 1.16.5, 1.12.2 und 1.8.9 bestanden, damit jede Mixin-Familie einmal im Spiel. Aufnahmen der Vorschau-Spieler angesehen auf 26.3, 26.1.2, 1.21.11, 1.21.8 und 1.8.9: Zeile über und unter dem Namen, die kleine Zeile eines Symbols rückt darunter. Auf 1.8.9 ohne Kasten und ohne die Zeichen 💵/💀.
- **Smoke-Test mit dem fertigen 0.3.38-Build:** nicht abgewartet. Der fertige Build unterscheidet sich vom getesteten Entwicklungsstand nur durch Versionsnummer, News und Changelog.
- **Nicht geprüft:** die Tooltip-Zeilen im Spiel (das Mixin lädt in allen getesteten Versionen, die Logik ist nur per Unit-Test geprüft); 26.2, 1.16.5 und 1.12.2 ohne Aufnahme der Zeilen (keine Vorschau-Spieler im Smoke-Test); 1.17.1 bis 1.21.4 nur kompiliert; ein Server mit Punktezeile unter dem Namen; ein echtes Selbst-Update von 0.3.37 auf 0.3.38.
- **Release:** `SHA256SUMS.txt.sig` mit dem Release-Schlüssel signiert und gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler.

**0.3.37 (2026-11-12):**
- **Build:** `gradlew clean build` erfolgreich, 521 Unit-Tests ohne Fehler (1 übersprungen). Neu: 3 für die Screenshot-Alben (eigene Alben auch nach Umbenennen, Löschen und Neustart; automatische Alben nach Server, Spieler und Account, Suche nach Spielern; neue Screenshots merken sich Server ohne Standard-Port, Account und Spieler in der Nähe, nicht sich selbst, ältere bleiben ohne Angaben; Alben-Reiter, Album öffnen, Esc zurück, neues Album mit der Auswahl). Alle 16 Client-Module kompiliert; keine Mixins geändert.
- **Vor dem Release** (Entwicklungsstand): Smoke-Test 26.3 bestanden, mit neuer Aufnahme des Alben-Reiters (mit Beispiel-Angaben in der Test-Instanz, danach entfernt); Aufnahmen angesehen: Reiter Fotos und Alben, „Neues Album“, eigenes Album mit Titelbild, Fotos mit „Zu Album“. Auf schmalem Fenster überdeckte die Suche zuerst den Reiter „Alben“ – behoben, erneut bestanden.
- **Smoke-Test mit dem fertigen 0.3.37-Build:** nur 26.3 (Änderungen im Kern, im Versionsmodul nur der Smoke-Test) – bestanden; die Screenshots des Tests in der Welt wurden mit „Einzelspieler“ und dem Account des Tests eingetragen. Die anderen Versionen wurden nicht im Spiel getestet.
- **Nicht geprüft:** automatische Alben mit Screenshots von einem echten Server und mit echten Spielern in der Nähe; Album anlegen, füllen, umbenennen und löschen von Hand; ein echtes Selbst-Update von 0.3.36 auf 0.3.37.
- **Release:** `SHA256SUMS.txt.sig` mit dem Release-Schlüssel signiert und gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler.

**0.3.36 (2026-11-12):**
- **Build:** `gradlew clean build` erfolgreich, 518 Unit-Tests ohne Fehler (1 übersprungen). Neu: RGB-Effekt (Farbe wechselt mit der Zeit, Werte nur wenn gewählt, Rand behält seine Transparenz, ohne Sättigung grau, Welle nach Position) und Widget-Design unter Themes (keine Modulkarte, Themes › Widgets öffnet die Einstellungen); Vorlagen RGB und Lumora im Modul-Test. Alle 16 Client-Module kompiliert; keine Mixins geändert.
- **Vor dem Release** (Entwicklungsstand): Smoke-Test 26.3 mit Widget-Design und RGB (Welle, Geschwindigkeit 4, Regenbogen-Rand, runde Ecken) bestanden; Aufnahme angesehen: die Widgets laufen nach Position durch den Regenbogen, auch die Tastenanzeige. Die Aufnahme dient als News-Bild. Der Hub zeigt 30 statt 31 Module.
- **Smoke-Test mit dem fertigen 0.3.36-Build:** nur 26.3 (Änderungen im Kern, in den Versionsmodulen nur Übersetzungen) – bestanden. Die anderen Versionen wurden nicht im Spiel getestet.
- **Nicht geprüft:** der Abschnitt Themes › Widgets im Spiel (liegt unterhalb der Smoke-Aufnahme der Themes-Seite; nur per Unit-Test); RGB in anderen Versionen; ein echtes Selbst-Update von 0.3.35 auf 0.3.36.
- **Release:** `SHA256SUMS.txt.sig` mit dem Release-Schlüssel signiert und gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler.

**0.3.35 (2026-11-12):**
- **Build:** `gradlew clean build` erfolgreich, 516 Unit-Tests ohne Fehler (1 übersprungen). Neu: 6 für das Widget-Design (eigenes Aussehen der Widgets, solange es aus ist; Formate „FPS 60“, „FPS: 60“, „[FPS] 60“, „60 FPS“, nur Wert, Großbuchstaben, Farben mit Bedeutung bleiben; Rand lässt das Innere frei, Hintergrund und Akzentstreifen; Größe aller Widgets mal eigene Größe; Modul ab Werk aus, eigene Stil-Einstellungen der Widgets ausgeblendet, Änderungen sofort sichtbar, Vorlagen; Addon-API `Theme.widgetStyle()`). Alle 16 Client-Module kompiliert; keine Mixins geändert.
- **Vor dem Release** (Entwicklungsstand): Smoke-Test 26.3 mit eingeschaltetem Widget-Design (runde Ecken, Rand, ohne Akzentstreifen, „[FPS] 60“, Großbuchstaben, Größe 1,25) bestanden; Aufnahme angesehen: eingebaute Widgets, Tastenanzeige und Session-Timer im gemeinsamen Design. Die Aufnahme dient als News-Bild.
- **Smoke-Test mit dem fertigen 0.3.35-Build:** 26.3 und 1.8.9 (Widgets in allen Versionsmodulen geändert; 1.8.9 als älteste Version) – beide bestanden, mit ausgeschaltetem Widget-Design; HUD-Aufnahme von 1.8.9 unverändert. Die anderen Versionen wurden nicht im Spiel getestet.
- **Nicht geprüft:** die Einstellungsseite des Moduls im Hub von Hand; Ziehen und Mausrad im HUD-Editor mit geänderter Größe aller Widgets; das Widget-Design in 1.8.9 bis 26.2 im Spiel; ein echtes Selbst-Update von 0.3.34 auf 0.3.35.
- **Release:** `SHA256SUMS.txt.sig` mit dem Release-Schlüssel signiert und gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler.

**0.3.34 (2026-11-12):**
- **Build:** `gradlew clean build` erfolgreich, 510 Unit-Tests ohne Fehler (1 übersprungen). Die Spielermenü-Tests prüfen jetzt das Fenster: Halten und Loslassen über einer Aktion, kurzer Klick lässt es offen, Mausrad und Enter, `/party invite <Name>` auf Servern (ohne Befehlsversand öffnet sich der Chat mit dem Befehl), nur echte Spielernamen, ausgegrauter Freund-Eintrag mit Grund ohne Lumora-Verbindung, Addon-Kategorie mit eigener Liste und Zurück. Alle 16 Client-Module kompiliert (neu `sendCommand` und `latency` je Version); keine Mixins geändert.
- **Vor dem Release** (Entwicklungsstand): Smoke-Test 26.3 bestanden; Aufnahmen angesehen: Fenster mit Spielerkarte und Aktionsliste, geöffnete Kategorie, HUD-Widgets liegen hinter dem Fenster. Die Aufnahme des Fensters dient als News-Bild.
- **Smoke-Test mit dem fertigen 0.3.34-Build:** nur 26.3 (dort wirken Fenster und HUD-Korrektur, deckt den Start über den Launcher ab) – bestanden, Aufnahmen angesehen. Die anderen Versionen wurden nicht im Spiel getestet.
- **Nicht geprüft:** `/party invite` und der Ping auf einem echten Server; die Spielerkarte mit verbundenem Lumora Social; die HUD-Korrektur in 1.21.8, 1.21.11, 26.1.2 und 26.2 im Spiel; ein echtes Selbst-Update von 0.3.33 auf 0.3.34.
- **Release:** `SHA256SUMS.txt.sig` mit dem Release-Schlüssel signiert und gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler.

**0.3.33 (2026-11-12):**
- **Build:** `gradlew clean build :launcher-core:integrationTest` erfolgreich, 510 Unit-Tests ohne Fehler (1 übersprungen). Alle 16 Client-Module kompiliert, Mixin-Ziele mit `check-mixins.pl` gegen die Minecraft-Jars geprüft.
- **Vor dem Release** (Entwicklungsstand): Smoke-Test in 26.3, 26.1.2, 1.21.11, 1.21.8 und 1.8.9 bestanden, jeweils mit drei Vorschau-Spielern und Namensschild-Icons (neu im Smoke-Test von 26.3, 1.21.11, 1.21.8 und 1.8.9); auf den Aufnahmen steht das Icon rechts neben der Box.
- **Smoke-Test mit dem fertigen 0.3.33-Build:** 26.3 bestanden. 1.8.9 im ersten Lauf am Chat-Test gescheitert (neueste Testnachricht nicht gefunden, Chat-Code unverändert), im zweiten Lauf bestanden.
- **Nicht geprüft:** 26.2, 1.12.2 und 1.16.5 bis 1.21.4 nur kompiliert und per `check-mixins.pl` geprüft, nicht im Spiel; Namensschilder echter Spieler auf einem Server; ein echtes Selbst-Update von 0.3.32 auf 0.3.33.
- **Release:** `SHA256SUMS.txt.sig` mit dem Release-Schlüssel signiert und gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler.

**0.3.32 (2026-11-12):**
- **Build:** `gradlew clean build` erfolgreich, 510 Unit-Tests ohne Fehler (1 übersprungen). Neu: 12 für die News-Artikel (Changelog-Parser mit Abschnitten, Unterpunkten und Absätzen; Kurzpunkte für die Liste; mitgelieferter Changelog vollständig und absteigend; Artikel mit Text, Screenshots und dem ganzen Changelog einer oder mehrerer Versionen; Changelog-Eintrag öffnet den News-Artikel oder die Version; Minecraft-News mit Link; jedes Bild der News ist mitgeliefert; sichere HTML-Umwandlung und verschachtelte Listen) und 1 für das Addon-Update aus der Bibliothek (neue Assets beim nächsten Start).
- **Vor dem Release** (Entwicklungsstand): Launcher mit `--ui-shot` im Testordner: News-Seite, Changelog-Liste, Artikel einer News und einer Version (Aufnahmen angesehen). Smoke-Test 26.3 bestanden; die Aufnahmen des Spielermenüs sind ohne Testchat und Toasts und dienen als News-Bilder.
- **Smoke-Test mit dem fertigen 0.3.32-Build:** nur 26.3 (die einzige geänderte Minecraft-Version; deckt den Start über den neuen Launcher ab) – bestanden. Die anderen Versionen wurden nicht erneut getestet, weil sich an ihnen nichts geändert hat.
- **Nicht geprüft:** Bedienung des Artikels von Hand (Scrollen, Esc, Klick daneben, „Im Browser öffnen“), Minecraft-Patch-Notes als Artikel mit echter Verbindung, ein echtes Selbst-Update von 0.3.31 auf 0.3.32.
- **Release:** `SHA256SUMS.txt.sig` mit dem Release-Schlüssel signiert und gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler.

**0.3.31 (2026-11-11):**
- **Build:** `gradlew clean build` erfolgreich, 497 Unit-Tests ohne Fehler (1 übersprungen). Neu: 6 für die Lumora-API 1.5 (Berechtigungen der neuen Dienste; Akzentfarbe eines Addons wird angeboten, gewählt, fällt beim Ausschalten auf Lila zurück und kommt mit dem Addon wieder; UiKit zeichnet in den Theme-Farben; Widget an einer Ankerzone und der alte Konstruktor; Chat-Menüeinträge mit Bedingung und Nachrichten-Event mit Absender und Link, entfernt mit dem Addon; Social ohne Verbindung leer).
- **Vor dem Release** (Entwicklungsstand): Alle 16 Client-Module kompiliert. Smoke-Test in 26.3 und 1.8.9 bestanden. Addon-Vorlage, LumoraVoice 0.8.3 und LumoraSpotify 1.0.1 bauen gegen die API 1.5.0 (Voice 73 Tests, Spotify 18 Tests + 1 übersprungener Live-Test).
- **Smoke-Test mit dem fertigen 0.3.31-Build:** 26.3, 26.1.2, 1.21.11, 1.20.1, 1.16.5 und 1.8.9 bestanden.
- **Nicht geprüft:** ein Addon, das die neuen Dienste im Spiel nutzt (nur Unit-Tests), Social-Daten mit laufendem Server, eine Addon-Akzentfarbe im echten Hub, ein echtes Selbst-Update von 0.3.30 auf 0.3.31.
- **Release:** `SHA256SUMS.txt.sig` mit dem Release-Schlüssel signiert und gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler.

**0.3.30 (2026-11-11):**
- **Build:** `gradlew clean build` erfolgreich, 491 Unit-Tests ohne Fehler (1 übersprungen). Neu: Spielermenü bei nicht erreichbarem Social-Server (Party und Freund erscheinen ausgegraut mit Grund, ein Klick darauf tut nichts); der Zoom-Einstellungstest prüft „Kinobalken“ (standardmäßig aus, umschaltbar).
- **Vor dem Release** (Entwicklungsstand): Alle 16 Client-Module kompiliert. Smoke-Test in 26.3 und 1.8.9 bestanden; in der 26.3-Zoom-Aufnahme sind die Kinobalken zu sehen (Aufnahme angesehen).
- **Smoke-Test mit dem fertigen 0.3.30-Build:** 26.3, 26.1.2, 1.21.11, 1.20.1, 1.16.5 und 1.8.9 bestanden.
- **Nicht geprüft:** das Hinein- und Herausgleiten der Balken (die Aufnahme zeigt den Endzustand), die Party-Einladung mit einem echten zweiten Lumora-Spieler, ein echtes Selbst-Update von 0.3.29 auf 0.3.30.
- **Release:** `SHA256SUMS.txt.sig` mit dem Release-Schlüssel signiert und gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler.

**0.3.29 (2026-11-11):**
- **Build:** `gradlew clean build` erfolgreich, 490 Unit-Tests ohne Fehler (1 übersprungen). Neu: 5 für das Spielermenü (Ring beginnt oben und Winkel wählen das Segment, Kategorie als äußerer Ring, Mausrad-Reihenfolge; Segmente mit Lücken und freier Mitte; Halten und Loslassen führt aus; kurzer Klick lässt offen, Mausrad und Klick; Addon-Einträge mit Kategorie, OP-Stufe und fehlerhaftem Eintrag) und 1 Servertest (Party einladen, annehmen, ablehnen, verlassen, Ende beim Offline-Gehen; Profilkarte für Fremde und Freunde).
- **Vor dem Release** (Entwicklungsstand): Alle 16 Client-Module kompiliert. Smoke-Test in 26.3, 1.21.11, 1.16.5 und 1.8.9 bestanden; in 26.3 öffnet der Test das Menü mit Test-Einträgen und wechselt per Mausrad in den äußeren Ring einer Kategorie (Aufnahme angesehen).
- **Smoke-Test mit dem fertigen 0.3.29-Build:** 26.1.2, 1.21.11, 1.20.1, 1.16.5 und 1.8.9 bestanden. 26.3 schlug im ersten Lauf fehl: Die Testfigur war im Elytra-Schritt durch Fallschaden gestorben, der Todesbildschirm verdrängte das Menü. Der Testschritt belebt jetzt zuerst wieder; danach neu gebaut und 26.3 bestanden. Im letzten Lauf wechselte das Mausrad nicht in die Kategorie, weil der Test das Rad an der Bildschirmmitte dreht, der Mauszeiger aber woanders stand (das Menü folgt der Maus).
- **Nicht geprüft:** Mausrad-Halten auf einen echten Spieler (im Einzelspieler gibt es keinen; der Test öffnet das Menü direkt), Party und Profilkarte im Spiel (Social-Verbindung im Test aus; nur im Servertest), das Client-Profil neben dem Ring, die OP-Stufe auf Servern, 1.8.9/1.12.2 liefern immer OP-Stufe 0, ein echtes Selbst-Update von 0.3.28 auf 0.3.29.
- **Release:** `SHA256SUMS.txt.sig` mit dem Release-Schlüssel signiert und gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler.

**0.3.28 (2026-11-11):**
- **Build:** `gradlew clean build` erfolgreich, 484 Unit-Tests ohne Fehler (1 übersprungen); keine neuen Unit-Tests (die Änderungen liegen in den Versionsmodulen).
- **Vor dem Release** (Entwicklungsstand): Alle 16 Client-Module kompiliert. In 26.3 neuer Smoke-Schritt für die Hotbar (Blöcke, Items, Stapelzahl, beschädigtes Werkzeug, Schild in der Zweithand; Vanilla-, Lumora- und Minecraft-Hotbar als Aufnahme), auch unter Vulkan und mit den Einstellungen des Spielers, der den Fehler gemeldet hat (GUI-Größe 2, seine Paket-Reihenfolge): Lumora-Hotbar mit denselben Pixelfarben wie Minecrafts eigene. Den Fehler selbst (Rot und Alpha vertauscht) konnte ich in der Testumgebung nicht nachstellen; er ist nur durch das F2-Bildschirmfoto des Spielers belegt.
- **Smoke-Test mit dem fertigen 0.3.28-Build:** 26.3, 26.1.2, 1.21.11, 1.20.1, 1.16.5 und 1.8.9 bestanden; Hotbar, Herzen und Hunger in 26.3 und 26.1.2 angesehen.
- **Nicht geprüft:** 26.2 nur kompiliert, die Suche im HUD-Editor (der Test tippt nicht), ob der Farbfehler im Spiel des Spielers wirklich weg ist, ein echtes Selbst-Update von 0.3.27 auf 0.3.28.
- **Release:** `SHA256SUMS.txt.sig` mit dem Release-Schlüssel signiert und gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler.

**0.3.27 (2026-11-10):**
- **Build:** `gradlew clean build` erfolgreich (ein Teil der Aufgaben aus dem Gradle-Cache), 484 Unit-Tests ohne Fehler (1 übersprungen). Neu: 4 für die Anker (Zonen als Drittel des Bildschirms, Offsets und Platzierung für alle neun Anker umkehrbar, gleicher Abstand auf größerem Bildschirm, Speichern und Laden, alte Layouts ohne Anker) und 3 für den Editor (Widget hängt an der Zone unter der Maus und bleibt bei anderer Größe dort, Magnet-Widget beim Einrasten, Klick holt nach vorne als ein Rückgängig-Schritt). Der Klick-Test erwartet jetzt „nach vorne“ statt „keine Änderung“.
- **Vor dem Release** (Entwicklungsstand): Alle 16 Client-Module kompiliert. Smoke-Test in 26.3, 1.16.5 und 1.8.9 bestanden; in 26.3 neue Aufnahme mitten im Ziehen (Zone, Raster, Positionsanzeige, hervorgehobenes Magnet-Widget, Hilfslinie) und nach dem Verschieben (Aktionskarte am Widget, Suchfeld). Das Ausblenden der Liste beim Ziehen kam danach dazu und ist nur in 26.3 erneut getestet (der Test zieht am Bildschirm vorbei, die Liste blieb dort sichtbar).
- **Smoke-Test mit dem fertigen 0.3.27-Build: auf Wunsch nicht ausgeführt.**
- **Nicht geprüft:** Ziehen und Tippen mit echter Maus und Tastatur, die Suche in keiner Version, die Aktionskarte am Widget in 1.16.5 und 1.8.9 nur kompiliert und durchgelaufen (nicht angesehen), ein echtes Selbst-Update von 0.3.26 auf 0.3.27.
- **Release:** `SHA256SUMS.txt.sig` mit dem Release-Schlüssel signiert und gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler.



**0.3.26 (2026-11-10):**
- **Build:** `gradlew clean build` erfolgreich (ein Teil der Aufgaben aus dem Gradle-Cache), 477 Unit-Tests ohne Fehler (1 übersprungen). Neu: 6 für „Hotbar & Leisten“ (Zahlenformate, Herzen mit Absorption in Sprites und icons.png, Kompakt/Balken/Zahl, Rüstung und Luft nur wenn nötig, Hotbar mit Zweithand-Regeln und senkrecht, Erfahrung) und 7 für den Chat (Absender in verschiedenen Server-Formaten, Links, Erwähnung, Werte für die Mixins, Ränder und Zeile unter der Maus, Ziehen der Ränder, Menü mit Kopieren und privat Antworten).
- **Vor dem Release** (Entwicklungsstand): Alle 16 Client-Module kompiliert, jeder neue Einhängepunkt mit `check-mixins.pl` gegen die Spieldateien geprüft. Smoke-Test mit eingeschaltetem „Hotbar & Leisten“ in 26.3, 1.16.5 und 1.8.9 bestanden. Smoke-Test mit Chat-Prüfung (Nachrichten, umgebrochene Nachricht als eine Nachricht, Menü per Rechtsklick, kleinerer Chat ohne Hintergrund und Textschatten) in 26.3, 1.21.11, 1.20.1, 1.16.5 und 1.8.9 bestanden; normaler Smoke-Test in 1.21.8, 1.19.4, 1.12.2, 26.1.2 und 26.2 bestanden. Dabei behoben: 1.21.8 und 1.21.11 stürzten beim Start ab (Lambda-Namen im laufenden Spiel anders als im Entwicklungs-Jar).
- **Smoke-Test mit dem fertigen 0.3.26-Build: auf Wunsch nicht ausgeführt.**
- **Nicht geprüft:** Ziehen der Chat-Ränder und „⋯“ beim Darüberfahren mit der echten Maus (nur Unit-Tests), Erwähnungston, Anzeigedauer und „Privat antworten“ im Spiel; senkrechte Hotbar, Zweithand-Items, Schild/Totem-Schalter, echte Rüstung/Luft/Absorption im Spiel; 1.17.1, 1.18.2, 1.20.4, 1.20.6, 1.21 und 1.21.4 nur kompiliert, nicht gestartet; ein echtes Selbst-Update von 0.3.25 auf 0.3.26.
- **Release:** `SHA256SUMS.txt.sig` mit dem Release-Schlüssel signiert und gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler.



**0.3.25 (2026-11-10):**
- **Build:** `gradlew clean build` erfolgreich (ein Teil der Aufgaben aus dem Gradle-Cache), 464 Unit-Tests ohne Fehler (1 übersprungen). Neu: 1 für die Tastenanzeige (Lage eines Buchstabens mit und ohne Schatten, LMB mit CPS-Zeile als Block in der Tastenmitte).
- **Vor dem Release** (Entwicklungsstand): Smoke-Test in 26.3 bestanden; vergrößerter Ausschnitt der Tastenanzeige vorher/nachher angesehen – W, A, S, D und LMB/RMB mit CPS sitzen mittig.
- **Smoke-Test mit dem fertigen 0.3.25-Build: auf Wunsch nicht ausgeführt.**
- **Nicht geprüft:** Textgrößen außer 100 % und die Lumora-Schrift (Inter) im Spiel, ein echtes Selbst-Update von 0.3.24 auf 0.3.25.
- **Release:** `SHA256SUMS.txt.sig` mit dem Release-Schlüssel signiert und gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler.



**0.3.24 (2026-11-10):**
- **Build:** `gradlew clean build` erfolgreich (ein Teil der Aufgaben aus dem Gradle-Cache), 463 Unit-Tests ohne Fehler (1 übersprungen). Neu: 3 für das Fadenkreuz (Mitte bei 1920×1080 mit GUI-Größe 3 und 4, bei 1366×768 und im 854×480-Fenster; lückenlose Linien; ein schneller Strich malt jedes Kästchen ohne Spiegeln, ein Strich auf einem gemalten Kästchen radiert). Der Einstellungs-Test erwartet jetzt „ein Klick, ein Pixel“ statt Spiegeln.
- **Vor dem Release** (Entwicklungsstand): Smoke-Test in 26.3 und 1.8.9 bestanden, der Editor zeigt das größere Raster und „Spiegeln: AUS“.
- **Smoke-Test mit dem fertigen 0.3.24-Build: auf Wunsch nicht ausgeführt.**
- **Nicht geprüft:** die Lage des Fadenkreuzes im Spiel bei großen Auflösungen (der Smoke-Test schaltet das Modul im Spiel nicht ein und läuft im 854×480-Fenster), ein echtes Selbst-Update von 0.3.23 auf 0.3.24.
- **Release:** `SHA256SUMS.txt.sig` mit dem Release-Schlüssel signiert und gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler.



**0.3.23 (2026-11-10):**
- **Build:** `gradlew clean build` erfolgreich (ein Teil der Aufgaben aus dem Gradle-Cache), 460 Unit-Tests ohne Fehler (1 übersprungen). Neu: 2 für die Elytra-Streifen (Schräglage zur Blickseite wie im Gleitmodell, eingeklappte Flügel im Sturzflug; breiter in Kurven und nach Tempo, Leucht-Saum).
- **Mixins** aller 15 Versionen mit Elytren mit `check-mixins.pl` geprüft: alle OK.
- **Vor dem Release** (Entwicklungsstand, Smoke-Test mit automatischem Gleitflug): 26.3 und 1.21.4 bestanden, Streifen auf den Bildern normal und mit Leuchten zu sehen, nach der Drehung in Schräglage breiter; 1.16.5 bestanden, dort brach der automatische Gleitstart ab.
- **Smoke-Test mit dem fertigen 0.3.23-Build: auf Wunsch nicht ausgeführt.**
- **Nicht geprüft:** fließende Kurven mit echter Steuerung, das Leuchten in 1.12.2 und 1.16.5 bis 1.18.2 (eigener OpenGL-Code, nur kompiliert), ein echtes Selbst-Update von 0.3.22 auf 0.3.23.
- **Release:** `SHA256SUMS.txt.sig` mit dem Release-Schlüssel signiert und gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler.



**0.3.22 (2026-11-10):**
- **Build:** `gradlew clean build` erfolgreich (ein Teil der Aufgaben aus dem Gradle-Cache), 458 Unit-Tests ohne Fehler (1 übersprungen). Neu: 6 für die Elytra-Streifen (Flügelspitzen, zwei Streifen hinter dem Spieler, Ausblenden nach der Länge und erst ab dem Tempo, eigene/fremde getrennt, Wirbel, gesperrt vor 1.9).
- **Mixins** aller 15 Versionen mit Elytren gegen die Minecraft-Jars geprüft (`check-mixins.pl`): alle OK.
- **Smoke-Test** mit automatischem Gleitflug (Elytra über den integrierten Server, 40 Blöcke hoch, Kamera vor dem Spieler, Drehung um 90° für die Seitenansicht): vor dem Release in 26.3, 1.21.11, 1.21.4, 1.20.1 und 1.16.5 Streifen gezeichnet und auf den Screenshots zu sehen (je eine der fünf Zeichen-Varianten), 1.12.2 lädt das Modul, 1.8.9 zeigt es gesperrt; mit dem fertigen 0.3.22-Build 26.3, 1.21.11, 1.16.5 und 1.8.9 bestanden. Dabei gefunden und behoben: die Smoke-Prüfung wertete das absichtlich gesperrte Modul in 1.8.9 als Fehler.
- **Nicht geprüft:** der Gleitflug in 1.12.2 (altes OpenGL, nur geladen), der Stil „Wirbel“ im Spiel (nur Unit-Test), Streifen anderer Spieler auf einem Server, ein echtes Selbst-Update von 0.3.21 auf 0.3.22.
- **Release:** `SHA256SUMS.txt.sig` mit dem Release-Schlüssel signiert und gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler.



**0.3.21 (2026-11-09):**
- **Build:** `gradlew clean build` erfolgreich (ein Teil der Aufgaben aus dem Gradle-Cache), 452 Unit-Tests ohne Fehler (1 übersprungen). Neu: 4 für die Versionsprüfung (Vergleich, zu alt/zu neu/nicht dabei, Lumora zu alt, Hinweis bei fremden Autoren) und 3 im Hub (gesperrte Addon-Karte mit Hover-Fenster, Bibliothek mit Grund, gesperrte Schrift).
- **Hover-Fenster** mit einem Zeichen-Hilfsprogramm außerhalb des Spiels erzeugt und angesehen (Addon-Karte, Bibliothek, Schrift).
- **Smoke-Test** mit dem fertigen 0.3.21-Build (stumm, Test-Datenordner, Beispiel-Addon): 26.3, 1.21.11, 1.16.5 und 1.8.9 bestanden; in 1.8.9 steht die Lumora-Schrift auf der Themes-Seite gesperrt mit Schloss.
- **Nicht geprüft:** das Hover-Fenster im laufenden Spiel mit der Maus (der Smoke-Test bewegt die Maus nicht), ein echtes inkompatibles Addon in einer Instanz, ein echtes Selbst-Update von 0.3.20 auf 0.3.21.
- **Release:** `SHA256SUMS.txt.sig` mit dem Release-Schlüssel signiert und gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler.



**0.3.20 (2026-11-09):**
- **Build:** `gradlew clean build` erfolgreich (ein Teil der Aufgaben aus dem Gradle-Cache), 445 Unit-Tests ohne Fehler (1 übersprungen). Neu: 1 für die Tastenanzeige (CSS-Kurve „ease“, LiquidBounce-Vorlage, Füllen halb offen nach 100 ms und voll nach 200 ms, zurück zum Lumora-Stil); der Größen-Test prüft die einstellbare Höhe der Leertaste.
- **Einzelbilder** der Animationen (Überblenden, Füllen, LiquidBounce-Vorlage; 0–300 ms) mit einem Zeichen-Hilfsprogramm erzeugt und angesehen; dabei gefunden und behoben: beim Füllen wurde der Text zu früh dunkel.
- **Smoke-Test** mit dem fertigen 0.3.20-Build (stumm, Test-Datenordner, Beispiel-Addon): 26.3, 1.21.11, 1.16.5 und 1.8.9 bestanden.
- **Nicht geprüft:** die Animationen im laufenden Spiel beim Drücken (der Smoke-Test zeigt nur ungedrückte Tasten), die Einstellungsseite und die Vorlagen-Knöpfe im Spiel, ein echtes Selbst-Update von 0.3.19 auf 0.3.20.
- **Release:** `SHA256SUMS.txt.sig` mit dem Release-Schlüssel signiert und gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler.


**0.3.19 (2026-11-09):**
- **Build:** `gradlew clean build` erfolgreich (ein Teil der Aufgaben aus dem Gradle-Cache), 444 Unit-Tests ohne Fehler (1 übersprungen). Neu: 4 für die Tastenanzeige (Größe folgt Tasten, Tastengröße und Abstand; bisherige Einstellungs-IDs bleiben; CPS auf den Maustasten an und aus; Übergang halb und ganz nach der gewählten Zeit; Rahmen mit 3 px Breite).
- **Smoke-Test** mit dem fertigen 0.3.19-Build (stumm, Test-Datenordner, Beispiel-Addon): 26.3, 1.21.11, 1.16.5 und 1.8.9 bestanden. HUD-Aufnahme in 26.3 und 1.8.9 angesehen: „0 CPS“ unter LMB und RMB.
- **Nicht geprüft:** die Einstellungsseite der Tastenanzeige im Spiel angesehen, abgerundete Tasten, Rahmen, Übergang und Schleichen-Taste im Spiel eingeschaltet, ein echtes Selbst-Update von 0.3.18 auf 0.3.19. Die Badlion-Seite ließ sich nicht abrufen; die Optionen folgen den bekannten Badlion-Keystrokes-Einstellungen.
- **Release:** `SHA256SUMS.txt.sig` mit dem Release-Schlüssel signiert und gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler.


**0.3.18 (2026-11-09):**
- **Build:** `gradlew clean build` erfolgreich (ein Teil der Aufgaben aus dem Gradle-Cache), 440 Unit-Tests ohne Fehler (1 übersprungen). Neu: 3 für die Addon-Bibliothek (Ordner neben den Instanzen, Zustände Verfügbar/Installiert/Aktualisieren/Nicht für diese Version/Fehlerhaft, Installieren kopiert die Datei und ersetzt die ältere Version, vorgemerkte Dateien werden beim Start gelöscht) und 2 für den Hub (Herz macht eine Karte zum Favoriten, der zuerst steht und eine eigene Kategorie hat, Klick auf das Herz nimmt ihn wieder weg; Klick auf „Installieren“ kopiert das Addon in die Instanz).
- **Smoke-Test** mit dem fertigen 0.3.18-Build (stumm, Test-Datenordner, Beispiel-Addon, in der Test-Bibliothek LumoraVoice 0.8.2 und das Beispiel-Addon): 26.3, 1.21.11, 1.16.5 und 1.8.9 bestanden. Neue Aufnahme der Addon-Seite in 26.3 und 1.8.9 angesehen (LumoraVoice „v0.8.1 → v0.8.2 · Aktualisieren“, Beispiel-Addon „Installiert“), Modulkarten mit Herz in 26.3.
- **Nicht geprüft:** ein Addon im Spiel wirklich installiert und danach neu gestartet, das Ersetzen einer geladenen (gesperrten) älteren Version beim nächsten Start außerhalb der Tests, ein echtes Selbst-Update von 0.3.17 auf 0.3.18.
- **Release:** `SHA256SUMS.txt.sig` mit dem Release-Schlüssel signiert und gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler.


**0.3.17 (2026-11-09):**
- **Build:** `gradlew clean build` erfolgreich (ein Teil der Aufgaben aus dem Gradle-Cache), 435 Unit-Tests ohne Fehler (1 übersprungen). Neu: 3 für die eingebauten Ebenen (Theme folgt Lumora/Vanilla, Schrift folgt der Auswahl, Neuladen nur bei Änderung, Schrift-Vorgabe des Smoke-Tests); Launcher-Test für das Entfernen alter `lumora:lumora_…`-Einträge.
- **Smoke-Test** mit dem fertigen 0.3.17-Build (stumm, Test-Datenordner, Beispiel-Addon): alle 16 Versionen bestanden (26.3, 26.2, 26.1.2, 1.21.11, 1.21.8, 1.21.4, 1.21, 1.20.6, 1.20.4, 1.20.1, 1.19.4, 1.18.2, 1.17.1, 1.16.5, 1.12.2, 1.8.9). 1.19.4 brach im ersten Durchlauf nach 84 s ohne Fehler und ohne Absturz ab (Spiel normal beendet, vermutlich Fenster geschlossen) und bestand in der Wiederholung. In den Spiel-Logs von 26.3, 1.21.11, 1.20.1 und 1.16.5 geprüft: Theme über den Spielerpaketen, Schrift kommt beim Einschalten dazu und geht beim Ausschalten; die options.txt enthält keine `lumora:lumora_…`-Einträge mehr.
- **Bekannt, nicht neu:** In 1.19.4 lädt die Lumora-Schrift nicht (ihre Schriftdatei nutzt `"type": "reference"`, das es erst ab 1.20 gibt); dort bleibt die Minecraft-Schrift.
- **Nicht geprüft:** Umschalten Lumora/Vanilla im Hub im Spiel angeklickt, Server-Ressourcenpakete zusammen mit Theme und Schrift, ein echtes Selbst-Update von 0.3.16 auf 0.3.17.
- **Release:** `SHA256SUMS.txt.sig` mit dem Release-Schlüssel signiert und gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler.


**0.3.16 (2026-11-09):**
- **Build:** `gradlew clean build` erfolgreich (ein Teil der Aufgaben aus dem Gradle-Cache), 432 Unit-Tests ohne Fehler (1 übersprungen). Neu: 1 für den Hub (keine Karten für Button-Design und Server- & Weltliste, Verbindung bleibt; ein Klick auf „Lumora-Serverliste“ unter Themes schaltet die Serverliste um).
- **Smoke-Test** mit dem fertigen 0.3.16-Build (stumm, Test-Datenordner, Beispiel-Addon): 26.3, 1.21.11, 1.16.5 und 1.8.9 bestanden. Modulliste in 26.3 und 1.8.9 angesehen: 27 statt 29 Module, neue Beschreibung der Angriffsanzeige im Tooltip.
- **Nicht geprüft:** die neuen Schalter unter Themes im Spiel angeklickt (sie liegen unterhalb der Smoke-Test-Aufnahme), ein echtes Selbst-Update von 0.3.15 auf 0.3.16.
- **Release:** `SHA256SUMS.txt.sig` mit dem Release-Schlüssel signiert und gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler.


**0.3.15 (2026-11-05):**
- **Build:** `gradlew clean build` erfolgreich (ein Teil der Aufgaben aus dem Gradle-Cache), 431 Unit-Tests ohne Fehler (1 übersprungen). Der Test der Angriffsanzeige prüft alle verbliebenen Stile und Positionen und dass ein gespeichertes „Wie in Minecraft“ zu „Am Fadenkreuz“ wird.
- **Smoke-Test** mit dem fertigen 0.3.15-Build (stumm, Test-Datenordner, Beispiel-Addon): 26.3, 1.21.11, 1.16.5 und 1.8.9 bestanden. Stil-Reihe der Angriffsanzeige mit den fünf verbliebenen Stilen in 26.3 angesehen.
- **Nicht geprüft:** ein echtes Selbst-Update von 0.3.14 auf 0.3.15.
- **Release:** `SHA256SUMS.txt.sig` mit dem Release-Schlüssel signiert und gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler.

**0.3.14 (2026-11-05):**
- **Build:** `gradlew clean build` erfolgreich (ein Teil der Aufgaben aus dem Gradle-Cache), 431 Unit-Tests ohne Fehler (1 übersprungen). Neu: 1 für die Angriffsanzeige (Levelleiste genau auf der Erfahrungsleiste 182 x 5 px, halb gefüllt bei 50 %, Prozent und Cooldown-Name; Bossleiste oben ab y 12, Cooldown als nächste Leiste; Position und Größe ausgeblendet) und 1 für die Einstellungsseite (Knopfreihe mit sechs Optionen bleibt eine Reihe, Klick auf die letzte Option).
- **Smoke-Test** mit dem fertigen 0.3.14-Build (stumm, Test-Datenordner, Beispiel-Addon): 26.3, 1.21.11, 1.16.5 und 1.8.9 bestanden. Levelleiste und Bossleiste in 26.3, 1.21.11 und 1.16.5 kurz nach einem Schlag fotografiert und angesehen, die Stil-Reihe mit sechs Knöpfen in 1.8.9.
- **Nicht geprüft:** die Leisten zusammen mit echten Boss-Leisten, Cooldowns, Bogen und Essen in den neuen Stilen im Spiel, ein echtes Selbst-Update von 0.3.13 auf 0.3.14.
- **Release:** `SHA256SUMS.txt.sig` mit dem Release-Schlüssel signiert und gegen `update-key.pub` geprüft, `sha256sum -c` ohne Fehler.

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
