# Änderungen

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

