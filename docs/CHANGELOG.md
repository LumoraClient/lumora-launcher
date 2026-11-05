# Änderungen

## 0.3.13 (2026-11-05)

### Im Spiel
- **Alle Einstellungen sofort sichtbar:** Den Schalter „Erweiterte Einstellungen anzeigen“ unten auf den Einstellungsseiten gibt es nicht mehr. Alle Module und Addons zeigen immer alle Einstellungen, z. B. die Hintergrundfarbe der Angriffsanzeige, die Zeitüberschreitung bei Verbindung, die Animationszeiten im Block-Overlay und die Text- und Wertfarben der HUD-Widgets.

### Addons
- `advanced()` blendet eine Einstellung nicht mehr aus; die Methode bleibt, damit bestehende Addons unverändert kompilieren und laufen.

## 0.3.12 (2026-11-05)

### Launcher
- **Nur noch ein Launcher:** Läuft der Launcher nach dem Schließen weiter im Infobereich (Tray) und du startest ihn noch einmal, öffnet sich kein zweiter Launcher mehr. Stattdessen kommt das Fenster des laufenden zurück, auch wenn es minimiert war oder hinter anderen Fenstern lag. Ein Autostart mit Windows lässt einen schon laufenden Launcher in Ruhe. Antwortet der laufende Launcher nicht, fragt Lumora, ob trotzdem ein weiterer starten soll.

## 0.3.11 (2026-11-05)

### Im Spiel
- **Neue Einstellungsseiten:** Abschnitte mit Überschriften, Häkchen-Kästen, Knopfreihen und Aufklapp-Listen. Ein Klick auf ein Farbfeld öffnet einen Farbwähler mit Sättigung/Helligkeit, Farbton, Deckkraft, Palette und (wo angeboten) Regenbogen mit Tempo. Einstellungen, die gerade nichts bewirken, sind ausgeblendet (z. B. die Lautstärke, solange kein Ton an ist). Zahlen zeigen ihre Einheit (%, px, ms, s).
- **Module:** Angriffsanzeige, Fadenkreuz, Block-Overlay, Verbindung, Chat, Tab-Liste und Button-Design nutzen die neuen Arten; die Farben von Angriffsanzeige und Fadenkreuz können als Regenbogen laufen. Die Tab-Liste hat einen Knopf „Position und Größe zurücksetzen“.
- **Zoom:** Die eigene Zoom-Seite im Hub ist jetzt eine normale Einstellungsseite (Vergrößerung und Modus als Knopfreihe, Extras als Häkchen); die Werte bleiben in `lumora-client.json`.
- **Behoben:** „Größe (%)“ der Angriffsanzeige und „Scoreboard-Größe (%)“ wurden in Minecraft als „Format error“ angezeigt.

### Addons
- **Lumora API 1.3.0** mit Einstellungs-Arten nach dem Vorbild von LabyMod: `checkbox`, `dropdown` und `selection` (auch mit Enums, `EnumSetting`), `button`, `chroma` (Farbwähler mit Regenbogen), `step`/`unit` an Zahlen, `section` für Überschriften, `requires`/`visibleWhen` für Einstellungen, die nur bei eingeschaltetem Schalter erscheinen. Alle Setter geben den konkreten Typ zurück, Casts entfallen. Addons für API 1.0 bis 1.2 laufen unverändert.
- Doku: API-Referenz mit LabyMod-Vergleich, Kapitel „Einstellungen“ in CREATE_ADDON.md; das Beispiel-Addon zeigt die neuen Arten.

## 0.3.10 (2026-11-05)

### Im Spiel
- **Server- und Weltliste ohne „Klassisch“:** Die untere Leiste der Lumora-Mehrspieler- und Einzelspieler-Liste zeigt nur noch Hinzufügen, Direkt verbinden und Aktualisieren bzw. Neue Welt und Aktualisieren. Minecrafts eigene Listen (z. B. zum Bearbeiten von Welt-Optionen) erreichst du, indem du im Hub das Modul „Server- & Weltliste“ ausschaltest; mit ViaFabricPlus öffnet dessen Knopf weiter die Versionsauswahl.

## 0.3.9 (2026-11-05)

### Im Spiel
- **Angriffsanzeige:** Neues eingebautes Modul (Hub → Kampf, anfangs aus). Seit 1.9 macht ein Schlag erst vollen Schaden, wenn die Waffe aufgeladen ist; die Anzeige zeigt das gut sichtbar.
  - **Stile:** Minecraft, Balken, Ring und Punkt. **Position:** wie in Minecraft (folgt der Option „Angriffsanzeige“), am Fadenkreuz oder an der Hotbar. **Größe** 50 bis 200 %.
  - **Item-Cooldowns** aller Items in Hotbar und Nebenhand (Enderperle, Chorusfrucht, Windkugel, Ziegenhorn, Schild nach einem Axthieb) als zusätzliche Ringe, Balken oder Punkte.
  - **Aufladen** von Bogen, Armbrust und Dreizack, dazu Essen, Trinken, Blöcke abbauen und Einschlafen.
  - **Töne:** wenn der Angriff bereit ist (ab 1.9, anfangs aus) und wenn ein Cooldown abgelaufen ist, mit Lautstärke.
  - Aufploppen in der Fertig-Farbe, eigene Farben für Aufladen, bereit und Cooldowns, Farbverlauf Rot bis Grün, Prozent und Item-Namen, wie in Minecraft ausblenden, wenn bereit, aber voll zeigen, solange du auf ein Wesen zielst.
  - Solange das Modul an ist, zeichnet Minecraft seine eigene Anzeige nicht; die Minecraft-Option bleibt unverändert.
  - 1.8.9 hat noch keine Angriffs-Abklingzeit und keine Item-Cooldowns (dort: Bogen, Essen, Trinken, Abbauen, Schlafen); 1.12.2 kennt noch keine Armbrust und keinen Dreizack.

## 0.3.8 (2026-11-04)

### Im Spiel
- **Screenshot-Manager:** Ein kleiner Kamera-Knopf im Lumora-Hauptmenü, im Minecraft-Titelmenü und im Pausenmenü (1.8.9 und 1.12.2: Knopf „Screenshots“) öffnet alle Screenshots der Instanz als Raster mit Vorschaubildern, Name, Datum und Größe.
  - Suche (auch einfach lostippen), Filter Alle/Favoriten, Sortierung nach Neueste, Älteste, Name oder Größe, Anzahl und Speicherplatz in der Kopfzeile.
  - Große Ansicht per Doppelklick oder Enter, Blättern mit ← → und dem Mausrad, Auflösung, Datum und Größe dazu.
  - Extern öffnen, im Ordner zeigen, Favorit (gespeichert in `config/lumora/screenshots.json`, bleibt beim Umbenennen erhalten), Umbenennen und Löschen nach Rückfrage (unter Windows in den Papierkorb). Mehrfachauswahl mit Strg und Umschalt, Strg + A wählt alle.
  - Vorschaubilder werden im Hintergrund verkleinert und beim Schließen wieder freigegeben. Esc schließt Schritt für Schritt Suche, Rückfrage, große Ansicht und dann den Manager.
- **Fadenkreuz-Editor:** Die Einstellungen des Moduls „Fadenkreuz“ haben oben einen Editor mit großer Vorschau auf Karomuster und drei Reitern.
  - **Formen:** die bisherigen sechs Formen als Kacheln, Größe, Abstand und Dicke darunter.
  - **Vorlagen:** 27 fertige Pixel-Fadenkreuze zum Anklicken; „Als eigenes Fadenkreuz bearbeiten“ übernimmt eine Vorlage in den Pixel-Editor.
  - **Eigene:** 15 × 15-Raster wie beim Vanilla-Fadenkreuz. Linksklick malt, Rechtsklick radiert, Ziehen malt weiter; „Spiegeln“ malt in allen vier Vierteln, dazu Leeren, Umkehren und Vorlage übernehmen.
  - Farbbalken für den Farbton und neue Einstellung „Skalierung“ (0,5 bis 3) für Vorlagen und eigene Fadenkreuze. Es erscheinen nur die Zeilen, die zur gewählten Art passen. Bisherige Einstellungen bleiben erhalten.
  - Neue Einträge in den Moduleinstellungen: `mode`, `preset`, `pixels`, `scale`.
- Einstellungsseiten können jetzt oben einen eigenen Editor eines Moduls zeigen (`SettingsView.Panel`).

## 0.3.7 (2026-11-04)

### Im Spiel
- **Ressourcenpaket „Lumora-Addons“:** Addon-JARs aus dem Ordner `addons/` bringen jetzt auch Texturen, Schriften und Klänge mit (bisher nur Sprachdateien). Lumora kopiert beim Start die `assets/` aller Addon-JARs nach `.lumora/addon-assets` und meldet den Ordner als immer aktives Ressourcenpaket an, in allen Versionen von 1.8.9 bis 26.3. Kopiert wird nur bei geänderten JARs. Die Namensräume `minecraft`, `realms` und `lumora` kann ein Addon nicht überschreiben.
- **LumoraVoice läuft wieder:** Seit dem Wechsel zum Lumora Loader (0.3.2) wurden Fabric-Mods aus `mods/` nicht mehr geladen, LumoraVoice 0.8.0 startete deshalb nicht. LumoraVoice 0.8.1 ist ein reines Lumora-Addon für `addons/` (Opus, RNNoise und Kotlin in der JAR) und nutzt das neue Ressourcenpaket für Menü-Grafiken und Namensschild-Symbole.
- **HUD-Einstellungen beim Addon:** Addons und Module mit eigenem HUD-Widget (z. B. Block-Overlay mit „Block info“, LumoraVoice, Session-Timer) haben im Hub nur noch eine Karte. Ihre Einstellungen zeigen unter einem Trenner „HUD · <Widget>“ den Schalter „Im HUD anzeigen“ und die Widget-Einstellungen. Suche, „Erweitert“ und „Alles zurücksetzen“ gelten für die ganze Seite, die Hub-Suche findet die Karte auch über den Widget-Namen. Lumoras eigene Widgets (Armor, FPS, Uhr …) bleiben einzelne Karten.
- Der Smoke-Test findet LumoraVoice als Addon-JAR und prüft wieder die Voice-Menüs.

## 0.3.6 (2026-11-03)

### Launcher
- **Neue Einstellungsseite:** Die Kategorien stehen als Reiter oben (Allgemein, Erscheinungsbild, Spiel, Speicher, Freunde & Chat, Integrationen, Fehlersuche), dazu „Ordner öffnen“, Suche und „Erweitert“ in der Kopfzeile. Einstellungen mit eigenem Bedienelement erscheinen als Abschnitt mit Icon, Titel, Beschreibung und dem Bedienelement darunter. An/Aus-Schalter stehen als kompakte Kästen in zwei Spalten, ihre Beschreibung erscheint beim Drüberfahren. Die Suche zeigt Treffer aus allen Kategorien. Die Einstellungen selbst sind unverändert.

## 0.3.5 (2026-11-03)

### Launcher
- **Konto mit Reitern** Allgemein, Sicherheit und Verlauf.
  - **Sicherheit:** Zwei-Faktor-Authentifizierung über das Microsoft-Konto (Lumora hat kein eigenes Konto; der Knopf öffnet die Microsoft-Seite für den zweiten Anmeldeschritt). Dazu eine Sitzungstabelle aller auf diesem PC angemeldeten Konten mit „Zuletzt aktiv“, „Erstmals genutzt“, Verwenden und Abmelden, sowie ein Link zur Anmeldeaktivität bei Microsoft.
  - **Verlauf:** Anmeldungen, erneuerte Sitzungen, Abmeldungen und Spielstarts je Konto sowie die Server, mit denen sich Minecraft verbunden hat (Konto, Version, zuletzt, erstmals, Besuche). Server lassen sich direkt wieder betreten. Der Verlauf bleibt lokal in `config/connections.json`, enthält keine Tokens und lässt sich abschalten und leeren (neuer Eintrag `connectionHistory` in `launcher.json`).
- **Seitenleiste mit Text oder nur Icons:** Umschalten mit dem Knopf unten in der Leiste oder unter Einstellungen → Erscheinungsbild → Seitenleiste, ohne Neustart (neuer Eintrag `navIconsOnly`). Größere Einträge, Logo wie bisher.
- **Schlichteres Design:** flachere Karten und Hintergrund, Einstellungen als ein Kasten je Kategorie mit Trennlinien, 14 Akzentfarben als Kacheln plus eigene Farbe, Startseite ohne Funktionsleiste.

## 0.3.4 (2026-11-03)

### Im Spiel
- **Button-Design für alle Menüs:** Der Stil aus dem Modul „Button-Design“ (Blackout, Hyperium, Liquid, Lunar, PvPClient, Rounded oder Lumora) gilt jetzt überall: Minecraft-Menüs, Lumora-Hauptmenü und alle Lumora-Knöpfe, Hub, Server- und Weltliste, Verbindungsbildschirme und Social-Menü. Auswahl im Hub unter Themes → Buttons mit einer Live-Vorschau jedes Stils. Alle Versionen.

## 0.3.3 (2026-11-03)

### Im Spiel
- **Schriften zur Auswahl** im Hub unter Themes → Schrift (1.16.5 bis 26.3): Vanilla (Minecraft), Inter (Lumora, auch LiquidBounce Nextgen), Outfit (LiquidBounce Legacy), Manrope (im Stil von Lunar), Roboto, Poppins, Montserrat, JetBrains Mono (Monospace) und Silkscreen (Pixel). Alle stehen unter der SIL Open Font License 1.1 und liegen als eigene Ressourcenpakete „Lumora Schrift: …“ im Client. Der Wechsel lädt die Ressourcen einmal neu. 1.8.9 und 1.12.2 können keine TrueType-Schriften.
  - Neuer Eintrag `font` in `lumora-client.json`.

## 0.3.2 (2026-11-03)

### Im Spiel
- **Neuer Lumora-Hub** (Rechte Shift-Taste, „✦ Lumora“ im Haupt- und Pausenmenü), gleich in allen Versionen:
  - **Seitenleiste:** Modul-Kategorien (Alle, Anzeige, Spieler, Kampf, Welt, Server, Zeit, Voice, Addons) mit Anzahl. Darunter „Allgemein“ mit Themes, HUD-Editor, Profile und System. Der Knopf „+ Neues Profil“ speichert das aktuelle HUD als neues Profil.
  - **Modul-Karten** mit eigenem Pixel-Icon, Name und An/Aus-Leiste („AKTIV“ blau, „INAKTIV“ rot). Das Zahnrad öffnet die Einstellungen, ▶ das Menü eines Addons. Fährt man über eine Karte, erscheint die Beschreibung.
  - Als Karten erscheinen alle HUD-Widgets, die eingebauten Module (Fadenkreuz, Toggle Sprint, Fullbright, Chat …), Addons und der Zoom.
  - **Suche** (Strg + F oder einfach lostippen), Mausrad zum Blättern.
  - **Profile:** laden, eigene löschen, neu anlegen. **System:** Renderer, Grafikkarte, Speicher, Benchmark, Diagnose, Ordner. **Zoom:** alle Zoom-Einstellungen auf einer eigenen Seite (Zahnrad der Zoom-Karte).
- **Neue Einstellungen für Module, Widgets und Addons** im Stil des Hubs (überall, wo Einstellungen geöffnet werden: Hub, HUD-Editor, Addons): Kopfzeile mit Zurück, Titel, Suche, „Im HUD-Editor verschieben“ und „Alles zurücksetzen“, darunter die Beschreibung. Schieberegler über die ganze Breite mit Wert, Schalter AN/AUS (grün/rot) in zwei Spalten, Farben mit Vorschau, editierbarem Hex-Wert und Zurücksetzen, Auswahl mit ‹ ›, Zahlen mit − +, Textfelder und Tasten. Hilfetexte beim Drüberfahren, erweiterte Einstellungen per Schalter.
- **Themes:** „Lumora“ oder „Vanilla“.
  - Vanilla schaltet Lumoras Hauptmenü, Menü-Hintergrund, Effekte, Schrift, Ladebildschirm, Server- und Weltliste, Verbindungsbildschirme und Button-Design ab. Der Hub bleibt im Lumora-Stil.
  - Bei Lumora wählt man eine **Akzentfarbe** (Lila, Blau, Türkis, Grün, Orange, Rot, Pink). Sie gilt für Hub, Lumora-Knöpfe, Schieberegler, Hauptmenü, Ladebildschirm, Server- und Weltliste, Verbindungs- und Social-Menüs.
  - Neue Einträge in `lumora-client.json`: `theme` und `accentColor`.
- **1.8.9 und 1.12.2:** Lumoras Texte (HUD-Editor, Widgets, Einstellungen, Hub) und die Texte von Addons erscheinen wieder übersetzt statt als Schlüssel wie „lumora.hud.armor“. Seit dem Wechsel zum Lumora Loader wurden die JSON-Sprachdateien dort nicht mehr geladen.

## 0.3.1 (2026-11-03)

### Im Spiel
- **Lumora-Ladebildschirm beim Betreten einer Welt** (alle 18 Versionen), im Stil des Verbindungsbildschirms: Weltname, Gesamtfortschritt und jede Phase mit Dauer: Weltdaten lesen bzw. Welt anlegen, Datenpakete, Server starten, Spawnbereich vorbereiten (mit Prozent), Gelände laden. Minecraft lädt die Welt wie gewohnt selbst.
  - Abschaltbar im Modul „Verbindung“: „Lumora-Ladebildschirm beim Betreten einer Welt“.
  - Portale, Dimensionswechsel, Respawn und Server-Beitritte zeigen weiter Minecrafts Bildschirme.

## 0.3.0 (2026-11-03)

### Lumora Loader: Minecraft ohne Mod-Loader (alle 18 Versionen)
- **Eigener Lumora Loader** statt Fabric. Er startet Minecraft, lädt den Lumora Client und wendet dessen Eingriffe ins Spiel (Mixins) selbst an. Lumora-Addons kommen wie bisher aus dem Ordner `addons`.
- **Alle Versionen von 1.8.9 bis 26.3 laufen ohne Fabric:** kein Fabric Loader, keine Fabric API, keine Fabric-Mods. Alles, was vorher die Fabric API lieferte, bringt der Client je Version selbst mit:
  - Menü- und HUD-Ereignisse
  - Tastenbelegungen
  - Lumoras Ressourcenpakete (Theme und Schrift wie gewohnt)
  - Plugin-Kanäle für Addons
- **Lesbare Namen beim Nutzer:** Für 1.16.5 bis 1.21.11 lädt der Launcher Mojangs offizielle Namensliste (`client_mappings`, SHA-1-geprüft) und der Loader benennt die Minecraft-JAR damit einmalig auf dem eigenen Rechner um. 1.8.9 und 1.12.2 bringen ihre Namensliste (Yarn, CC0) im Lumora-Client mit. 26.x ist nicht verschleiert.
- **Entfallen:** Sodium, Lithium, FerriteCore, ViaFabricPlus, ViaFabric, Simple Voice Chat, Mod Menu und Ressourcenpakete, die eine Mod brauchten (z. B. über EMF/ETF), weil das Fabric-Mods sind. Ein vorhandener `mods`-Ordner bleibt unangetastet, wird aber nicht mehr geladen.

### Launcher
- **Seite „Mods“ entfernt**, ebenso der Bereich „Performance-Mods“ auf der Performance-Seite.
- **„Entdecken“** zeigt nur noch Ressourcenpakete von Modrinth.
- Keine Downloads mehr von Fabric-Servern, keine Mod-Abhängigkeitsprüfung mehr.

## 0.2.2 (2026-11-02)

### Launcher
- **Update auch beim direkten Start:** Bisher hat sich der Launcher nicht selbst aktualisiert, wenn er über ein angeheftetes Taskleisten-Symbol oder eine eigene Verknüpfung gestartet wurde (Hinweis „aktualisiert sich nicht selbst“). Jetzt aktualisiert er sich auch dann.
  - `LumoraClient.exe` merkt sich ihren Ort.
  - Fehlt diese Angabe (ältere Versionen), legt der Launcher die neue Version als `%LOCALAPPDATA%\LumoraClient\LumoraClient.exe` ab.
- **Anheften:** Ein angeheftetes Lumora-Symbol startet jetzt `LumoraClient.exe` statt des entpackten Programms. Es bleibt deshalb auch nach Updates gültig. Ein schon vorhandenes altes Symbol einmal lösen und neu anheften.
- **Neuer Schalter** „Update auch beim direkten Start“ unter Einstellungen → Allgemein (Standard: an). Aus: Nur ein Start über `LumoraClient.exe` aktualisiert.

## 0.2.1 (2026-11-02)

### Launcher
- **Update-Dialog:** feste Größe mit Scrollbereich. Die Knöpfe „Jetzt aktualisieren“ und „Später“ bleiben bei langen Versionshinweisen sichtbar.
- **Mod-Loader vorerst entfernt:** Die Startarten Fabric, Quilt, Forge und NeoForge und die Seite „Mod-Loader“ gibt es nicht mehr. Gestartet wird Lumora Client oder Vanilla.
  - Wer einen der Loader ausgewählt hatte, startet beim nächsten Mal Lumora.
  - Alte Spielordner (`instances\<version>-forge` …) bleiben unangetastet liegen.
  - Die Server von Quilt, Forge und NeoForge stehen nicht mehr auf der Download-Liste.
- Lumora läuft weiter auf Fabric. Den Loader wählt aber nur noch der Launcher selbst, nicht der Spieler.

### Im Spiel: eigener Addon-Loader
- **Addon-JARs direkt in den Ordner `addons`** der Instanz legen. Eine `lumora-addon.json` genügt: keine `fabric.mod.json`, kein Mod-Loader.
- **Getrennte Class-Loader:**
  - Jedes Addon bekommt einen eigenen. Klassen des Spiels, von Fabric und von Lumora kann ein Addon nicht ersetzen.
  - Klassen anderer Addons sieht es nur, wenn es sie unter `depends` oder `optional` angibt.
- **Sprachdateien** kommen direkt aus der Addon-JAR.
- **Fehlerhafte Dateien** stehen mit Grund in der Addon-Liste (keine `lumora-addon.json`, kaputtes Archiv, Fabric-Mod im falschen Ordner).
- **Neuer Knopf „Addon-Ordner“** im Lumora-Hub unter Addons, in allen 16 Versionen.
- **Freigabe beim Beenden:** Lumora schließt die JARs, sie lassen sich danach ersetzen oder löschen.
- **Fabric-Mods** mit `lumora-addon.json` im Ordner `mods` funktionieren weiter.
- Texturen eines Addons kommen vorerst nur über den Weg als Fabric-Mod oder ein Ressourcenpaket.

## 0.2.0 (2026-11-02)

### Neu im Launcher
- **Fabric, Quilt, Forge und NeoForge** als eigene Startarten, jede mit eigenem Spielordner. Auswahl über den Pfeil neben „Spiel starten“.
- **Seite „Mod-Loader“:** für jede Minecraft-Version die Loader-Version wählen. „Empfohlen“ aktualisiert sich selbst, alternativ eine feste Version. Dazu eigene JVM-Argumente je Loader, Installieren, Reparieren, Aktualisieren, Entfernen und der Spielordner.
- **Downloads:** nur von den offiziellen Servern der Projekte, jede Datei per Prüfsumme kontrolliert. Forge und NeoForge werden aus ihrem offiziellen Installer eingerichtet, ohne den Installer selbst zu starten.
- **Blaues Design:**
  - Navy und Blau als Grundfarben
  - Polarlicht und funkelnde Sterne im Startbanner
  - fließender Start-Knopf mit Lichtstreifen
  - gleitende Auswahl in der Seitenleiste
  - leuchtende Karten unter der Maus
  - weiche Seitenwechsel

  „Weniger Bewegung“ schaltet alle Animationen ab. Wer bisher das Standard-Lila hatte, bekommt Blau.

### Neu im Spiel (alle 18 Versionen, Modul „Verbindung“)
- **Verbindungsbildschirm:** jede Phase mit ihrer Dauer (Verbinden, Login, Kontoprüfung bei Mojang, Verschlüsselung, Konfiguration, Welt laden). Dazu Live-Ping mit Spielern, Version und MOTD, der Weg „Du ⟷ IP ⟷ Server“ und das angemeldete Konto.
- **Trennungsbildschirm:** erkannter Grund mit Tipp (nicht erreichbar, Zeitüberschreitung, gesperrt, Whitelist, voll, falsche Version, Kontoproblem …) und „Erneut verbinden“.
- **Automatisch neu verbinden** (einschaltbar): wachsende Pausen und begrenzte Versuche. Nie bei Sperre, Whitelist, falscher Version oder Kontofehlern.
- **Hängende Verbindungen** werden abgebrochen. Ein Stream-Modus verbirgt Serveradressen.
- **Handshake, Login, Verschlüsselung und Sitzung** macht weiterhin Minecraft selbst. Lumora liest nur mit und fasst keine Zugangsdaten an.
- **Lumora-API 1.2.0** mit Verbindungs-Events für Addons. Addons für 1.0 und 1.1 laufen unverändert weiter.

### Prüfung
- 363 automatisierte Tests bestanden.
- Starttests bestanden:
  - Forge 1.8.9, 1.12.2, 1.16.5, 1.20.1 und 26.3
  - NeoForge 1.20.1, 1.21.4 und 26.3
  - Quilt 1.21.4 und 1.21.11
- Smoke-Tests im Spiel bestanden für 26.3, 1.21.4, 1.20.6, 1.20.1, 1.17.1, 1.16.5, 1.12.2 und 1.8.9, mit Verbindungs- und Trennungsbildschirm gegen lokale Testserver.
- Nicht automatisch getestet: Beitritt zu einem echten Online-Server mit Microsoft-Konto.

