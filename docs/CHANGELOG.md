# Änderungen

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

