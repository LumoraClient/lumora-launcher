# Mods, Modloader und Instanzen

Dieses Dokument beschreibt das Mod- und Instanzsystem des Launchers: Referenzanalyse, Architektur, Datenablage,
Sicherheitsregeln und den Stand der Etappen.

## Stand der Etappen

| Etappe | Inhalt | Stand |
|---|---|---|
| 1 | Modloader (Fabric, Quilt, Forge, NeoForge, Vanilla, Lumora), Instanzen für alles, Modrinth-Discovery mit Detailseite, Installer mit Kompatibilitätsprüfung und Abhängigkeiten, Mod-Verwaltung je Instanz, `.mrpack`-Import, Deutsch als Standardsprache | gebaut und getestet, noch nicht veröffentlicht |
| 2 | CurseForge (eigener API-Schlüssel des Spielers), Update-Manager, Import aus anderen Launchern, Drag & Drop, Backups, Export, Datenpakete pro Welt | gebaut und getestet, noch nicht veröffentlicht |

## Etappe 2 im Überblick

- **CurseForge** (`platform/CurseForgeProvider`): offizielle API `api.curseforge.com`, nur mit dem **eigenen Schlüssel des
  Spielers** (Optionen › Integrationen). Lumora liefert keinen Schlüssel aus. Der Schlüssel liegt DPAPI-verschlüsselt in
  `auth/curseforge.bin` (`auth/SecretStore`), geht nur im Header `x-api-key` raus und wird beim Laden beim Log-Schwärzer
  angemeldet. Ohne Schlüssel ist CurseForge aus, alles andere funktioniert. Dateien, deren Autor keine Downloads über andere
  Apps erlaubt (keine `downloadUrl`), werden nie auf anderem Weg geladen: Installer und Modpack-Import nennen sie mit Seite.
  Prüfung mit SHA-1 (CurseForge veröffentlicht kein SHA-512). Erkennung fremder Dateien über den CurseForge-Fingerabdruck
  (MurmurHash2 ohne Leerraum-Bytes, `CurseForgeFingerprint`).
- **Updates** (`content/ContentUpdates`): Modrinth über die SHA-1 aller Dateien in einer Anfrage (nur Releases),
  CurseForge über die gespeicherte Herkunft. Festgehaltene Dateien bekommen kein Angebot. Einspielen über den Installer
  (Sicherung der ersetzten Datei, Rollback), auf Wunsch vorher eine Sicherung der ganzen Instanz.
- **Sicherungen** (`instance/BackupManager`): ZIP unter `<Daten>/backups/<Instanz>/`, ohne Logs, Absturzberichte, Sperren
  und Caches; höchstens 10 je Instanz. Wiederherstellen sichert zuerst den aktuellen Stand, entpackt sicher in einen
  Nebenordner und tauscht dann die Ordner. Der gemeinsame Standard-Ordner `.minecraft` wird nicht gesichert.
- **Export** (`pack/InstanceExporter`): `.mrpack` (Modrinth-Dateien als Download-Verweis, der Rest in `overrides/`, keine
  Welten) oder Lumora-Archiv (`lumora-instance.json` + ganzer Spielordner samt Welten). Ausgewählte Dateien lassen sich in
  einen Ordner exportieren oder in eine andere Instanz kopieren (`content/ContentTransfer`, nur Passendes, nichts
  überschrieben).
- **Import** (`pack/LauncherImport`, `pack/CurseForgePackImporter`): Prism Launcher, MultiMC, CurseForge-App, Modrinth App
  (`profile.json`), offizieller Launcher (Profile mit eigenem Spielordner), Lumora-Archive und CurseForge-Modpacks. Andere
  Launcher werden nur gelesen.
- **Drag & Drop**: Dateien aus dem Explorer auf das Fenster, eine Instanzkarte oder die Instanzseite; Instanzkarten auf eine
  andere Instanz (Mods & Pakete kopieren, Einstellungen übernehmen oder neue Kopie) oder auf die Zone „Instanz kopieren“.
  Während des Ziehens erscheinen die Ablagezonen; jede Datei wird am Inhalt erkannt, nicht am Namen.
- **Datenpakete pro Welt**: In „Welten“ zeigt jede Welt ihre Datenpakete mit Hinzufügen, Entfernen und Suchen.

## Instanz-Auswahl und Instanzliste (nach 0.3.98)

- **Startseite:** Der Pfeil neben „Spielen“ und der Knopf in der Spielleiste öffnen die Instanz-Auswahl
  (`pages/InstancePicker`): Suche, die gewählte Instanz oben, darunter deine Instanzen und die Lumora-Versionen auf der
  Liste, dann je Herkunft eine Gruppe („Importiert aus CurseForge“). Beim Überfahren erscheinen „Öffnen“ (Zahnrad) und
  „Spielen“, unten „Instanz erstellen“ und „Dateien prüfen und spielen“.
- **Nur gewünschte Lumora-Versionen:** Eingebaute Instanzen stehen nur auf der Liste, wenn sie hinzugefügt wurden
  (`LauncherConfig.addedInstances`), plus immer die gewählte. Beim ersten Start übernimmt Lumora die gewählte Version
  und Versionen mit eigenem Ordner, in dem echte Welten liegen (Testwelten der Starttests zählen nicht). Hinzufügen über
  „Instanz erstellen“ › Lumora oder Vanilla › „Gemeinsamen Minecraft-Ordner nutzen“; „Aus der Liste entfernen“ löscht
  nichts. Der Spielordner einer eingebauten Version wird auf ihrer Instanzseite unter „Einstellungen“ gewählt.
- **Versionswahl beim Erstellen:** Kacheln je Hauptversion wie im früheren Versionsmenü (`ui/VersionGrid`), Unterversionen
  als Chips, was nicht passt, hinter „›“; Snapshots teilen sich eine Kachel.
- **Instanzkarten** (`pages/InstanceTile`): Bild (eigenes Symbol, sonst Mojangs Bild des Updates, sonst Pixel-Art),
  Loader und Minecraft-Version als Plaketten, Name und Ersteller („Meine Erstellung“, „Von …“ beim Modpack-Autor,
  „Importiert aus …“, „Lumora“). Der Autor kommt aus Modrinth (Entdecken) oder dem CurseForge-Manifest (`Instance.author`).

## Referenzanalyse

Zwölf Referenzbilder, jedes einzeln betrachtet. Übernommen werden Ideen, keine Oberflächen.

### 1. Instanzliste mit Kopfleiste (eigener Launcher-Entwurf)
- **Funktionen:** Umschalter „Einfach / Experte“, Suche, Sortierung „Zuletzt gespielt (absteigend)“, „Importieren“,
  „Instanz erstellen“ als einzige Akzentfläche; Karten mit Icon, Name, Zeile „Version – Client – Loader“ und eigenem
  Start-Segment rechts.
- **Gut:** Starten ohne Umweg über eine Detailseite, die Karte bleibt trotzdem zum Öffnen klickbar. Eine Akzentfläche
  pro Leiste, klare Hierarchie.
- **Schwäche:** Der Loader steht nur als Text da; bei vielen Instanzen fehlt eine Gruppierung.
- **Lumora:** Instanzkarten mit Start-Segment, Loader-Chip mit Farbe, Sortierung und Suche, Experten-Schalter blendet
  Ordnerpfade, Java und Loader-Version ein.

### 2. Dialog „Neue Instanz“
- **Funktionen:** Drei große Wahlkarten: Eigene Instanz, Modpack (Modrinth/CurseForge), Importieren (anderer Launcher
  oder `.mrpack`).
- **Gut:** Der Einstieg trennt die drei Wege sauber, jede Karte erklärt sich in einer Zeile.
- **Lumora:** Gleicher Einstieg als erster Schritt des Assistenten, die Modpack-Karte öffnet „Entdecken“ mit dem Filter
  Modpacks.

### 3. Plattform-Leiste
- **Funktionen:** Modrinth, CurseForge, FTB als Liste mit Logo, die Auswahl ist umrandet.
- **Gut:** Die Plattform ist ein Filter wie jeder andere, nicht ein eigener Bereich.
- **Lumora:** Plattform-Filter oben in der Filterspalte (Alle / Modrinth; CurseForge erscheint, sobald angebunden).

### 4. Assistent „Instanzen importieren“
- **Funktionen:** Schrittleiste Quelle → Übersicht → Auswahl → Import, Quellenkarten mit Anzahl gefundener Instanzen,
  Liste der Instanzen der gewählten Quelle, Fußleiste „Ordner scannen“, „.mrpack-Datei importieren“, „Mehrere auf einmal“.
- **Gut:** Der Launcher sucht selbst und zeigt Zahlen, bevor man klickt. Die Schrittleiste macht den Ablauf vorhersehbar.
- **Schwäche:** Vier Schritte für einen einzelnen Import sind viel.
- **Lumora:** Schrittleiste im Instanz-Assistenten; der `.mrpack`-Import zeigt eine Vorschau (Name, Version, Loader,
  Mods, Overrides, Speicherbedarf) und importiert nach einer Bestätigung. Launcher-Import folgt in Etappe 2.

### 5. Versionswahl im Assistenten
- **Funktionen:** Schrittleiste Art/Version/Fertig, Suche, Raster-/Listen-Umschalter, Filter „Releases“, Kacheln je
  Hauptversion mit Bild und Unterversionen als Chips, Loader-Leiste unten, „Zurück“ und „Weiter“.
- **Gut:** Bilder machen Versionen wiedererkennbar; Unterversionen sind ein Klick, ohne lange Liste.
- **Lumora:** Lumoras eigenes Versionsraster (`VersionGrid`) wird im Assistenten wiederverwendet, darunter die
  Loader-Wahl (Lumora, Vanilla, Fabric, Quilt, Forge, NeoForge). Loader, die es für die Version nicht gibt, sind
  ausgegraut und erklären warum.

### 6. CurseForge „Create Profile“
- **Funktionen:** Bild mit Stift zum Ändern, Name, Version als Dropdown, Loader als Radiogruppe, Loader-Version als
  Dropdown, „Create“ erst aktiv, wenn alles gültig ist.
- **Gut:** Kompakt; der deaktivierte Knopf verhindert halbe Eingaben.
- **Schwäche:** Kein Hinweis, warum „Create“ grau ist.
- **Lumora:** Letzter Schritt „Fertig“ mit Name, Icon und Loader-Version (Empfohlen/Neueste/Andere); fehlt etwas, steht
  der Grund unter dem Knopf.

### 7. CurseForge Instanzkarten
- **Funktionen:** Großes Titelbild, Badges oben (Loader-Icon, Version), Titel, Autor („My creation“ / „By …“), Aktionen
  Create, Import, Create Group.
- **Gut:** Modpacks erkennt man am Bild; die Herkunft (eigen oder Modpack) steht an der Karte.
- **Lumora:** Karten zeigen das Modpack-Icon bzw. ein generiertes Icon, die Herkunft („Eigene Instanz“, „Modpack von …“,
  „Lumora“) und den Loader als Chip.

### 8. CurseForge Inhalts-Tabs
- **Funktionen:** Content / Logs / Screenshots, darunter Mods, Data Packs, Resource Packs, Shaders, Worlds mit Anzahl,
  „Update all“, Suche, Filter, Ansicht, Sortierung, Spalten, Aktualisieren, „Add Content“.
- **Gut:** Anzahl je Kategorie auf einen Blick; „Add Content“ führt direkt in die Suche für genau diese Instanz.
- **Schwäche:** Viele kleine Symbole ohne Beschriftung.
- **Lumora:** Instanzansicht mit Tabs Inhalte (Mods, Ressourcenpakete, Shader, Datenpakete, Welten mit Anzahl),
  Einstellungen; „Inhalte hinzufügen“ öffnet „Entdecken“ mit vorausgewählter Instanz; Symbole bekommen Tooltips.

### 9. Versionsraster mit schwebender Loader-Leiste
- **Funktionen:** Gewählter Unterversions-Chip hervorgehoben, ältere Versionen (1.12, 1.8) im selben Raster, Loader-Leiste
  schwebt über dem Raster.
- **Lumora:** Wie Bild 5; die Leiste bleibt beim Scrollen sichtbar.

### 10. Modrinth App: Instanz-Einstellungen
- **Funktionen:** Seitenleiste Instanz (Allgemein, Installation) und Spiel (Java und Speicher, Fenster, Start-Hooks,
  Sync), Schalter mit Beschreibung.
- **Gut:** Jede Einstellung hat eine Zeile Erklärung; Abweichungen von den globalen Werten sind sichtbar.
- **Lumora:** Einstellungs-Tab der Instanz: Allgemein (Name, Icon, Loader-Version, festhalten), Java und Speicher
  (eigene Java, RAM), Fenster; leer gelassene Werte nehmen die globalen Launcher-Einstellungen.

### 11. Modrinth App: Instanz erstellen
- **Funktionen:** Icon mit Hochladen/Zufällig/Anpassen, Name mit Vorschlag als Platzhalter, Loader als Pillen,
  Spielversion als Dropdown, Loader-Version Stabil/Neueste/Andere.
- **Gut:** Der Name wird aus Loader und Version vorgeschlagen; „Stabil“ ist vorgewählt.
- **Lumora:** Namensvorschlag „Fabric 26.3“, Loader-Version standardmäßig „Empfohlen“.

### 12. Modrinth App: Dateien-Tab
- **Funktionen:** Kopf mit Icon, Name, „Fabric 26.3 • Nie gespielt“, Statusknopf „Installing…“, Zahnrad, Mehr-Menü;
  Tabs Content/Files/Worlds/Logs/Share; Dateitabelle mit Größe, Datum, Aktionen.
- **Gut:** Der Kopf zeigt Zustand und letzte Nutzung; die Installation läuft sichtbar im Hintergrund weiter.
- **Lumora:** Instanzkopf mit Icon, Name, Loader, Version, „zuletzt gespielt“, Start-/Fortschrittsknopf und Mehr-Menü
  (Ordner öffnen, Duplizieren, Umbenennen, Reparieren, Löschen).

### Zusammengeführt
- Ein Akzentknopf pro Leiste, alles andere ruhig im bestehenden Lumora-Design (`Theme`, `Ui`, `Card`, `TabBar`).
- Instanzen sind das zentrale Objekt; Starten geht von der Startseite und von jeder Karte.
- Plattform, Loader, Version und Kategorie sind gleichrangige Filter, kombinierbar.
- Vor jeder Installation eine Zusammenfassung: was installiert wird, welche Abhängigkeiten, was nicht passt und warum.

## Architektur

```
launcher-core
 ├─ loader/      ModLoader (Fabric, Quilt, Forge, NeoForge), LoaderManager (= ModLoaderManager): Versionen, Installation
 ├─ instance/    Instance, InstanceKind, InstanceManager: eingebaute Lumora-Instanzen + eigene Instanzen
 ├─ platform/    ModPlatformProvider (Schnittstelle), gemeinsames Datenmodell, ModrinthProvider, ApiCache, ModRepository
 ├─ content/     InstalledContent (Scan + Metadaten), ContentManifest, CompatibilityChecker, DependencyResolver,
 │               ConflictDetector, ContentInstaller (Plan → Download → Prüfung → atomar einsetzen → Rollback)
 └─ pack/        SafeZip (Zip-Slip-Schutz), MrpackImporter (Analyse, Vorschau, Import)
launcher-app
 ├─ pages/InstancesPage, InstanceView, CreateInstanceDialog, DiscoverPage, ProjectView, InstallDialog, ImportDialog
 └─ Startseite und Spielen-Knopf arbeiten mit der gewählten Instanz
```

Zuordnung zu den Namen aus dem Auftrag: `ModRepository` vereint Such- und Metadatendienst (ModSearchService,
ModMetadataService); `ContentInstaller` übernimmt ModInstaller und InstallationValidator; der Name `ModInstaller` ist
schon für Lumoras eigene JARs vergeben.

## Datenablage

```
<Datenordner>/custom-instances/<id>/
 ├─ instance.json        Name, Typ, Minecraft-Version, Loader-Version, Icon, Java, RAM, Fenster, festgehaltene Versionen
 ├─ icon.png             eigenes Icon (optional)
 └─ minecraft/           Spielordner (--gameDir): mods, config, resourcepacks, shaderpacks, saves, screenshots, options.txt
      └─ .lumora/content.json   Herkunft installierter Dateien (Plattform, Projekt, Version, Hashes, festgehalten)
```

Die eingebauten Lumora-Instanzen (eine je Lumora-Version) behalten ihre bisherigen Ordner (`InstanceLayout`: Standard-Ordner
oder eigene Instanz). Es wird nichts verschoben.

## Sicherheit

- Downloads nur über HTTPS von der Allow-List (`Downloader`), jede Datei mit dem Hash, den die Quelle veröffentlicht
  (Modrinth: SHA-512; Maven: `.sha1` daneben). Erst nach erfolgreicher Prüfung wird die Datei an ihren Platz verschoben.
- Archive (`.mrpack`) werden mit `SafeZip` gelesen: keine absoluten Pfade, kein `..`, keine Laufwerksbuchstaben,
  Größen- und Anzahl-Grenzen; Ziele müssen im Instanzordner liegen.
- `.mrpack`-Downloads nur von den Domains, die das Format erlaubt (cdn.modrinth.com, github.com,
  raw.githubusercontent.com, gitlab.com) und die zusätzlich auf der Allow-List stehen.
- Nichts wird ohne Bestätigung überschrieben; Installationen werden bei einem Fehler zurückgerollt.

## Tests (Etappe 1)

- **Unit-Tests** (launcher-core): Instanzen anlegen, kopieren, umbenennen, löschen und prüfen; Modrinth-Provider gegen
  einen lokalen Server (Facetten, Suche, Projektseite, Cache, Kategorien); Installer mit Abhängigkeitsbaum, Update mit
  Sicherung, Rollback bei falscher Prüfsumme und verständlichen Ablehnungen; Mod-Metadaten von Fabric, Quilt, Forge,
  NeoForge und `mcmod.info` samt Jar-in-Jar; Konfliktprüfung; `SafeZip` gegen Zip-Slip; `.mrpack`-Analyse und -Import
  mit Aufräumen nach Fehlern; Dateierkennung; Loader-Versionslisten.
- **Live-Prüfung** gegen api.modrinth.com im Test-Home (`.t/e2e/ModsE2E.java`): Suche, Projektseite, Loader-Listen,
  Installation von Mod Menu mit Fabric API und Placeholder API (SHA-512 geprüft), Konfliktprüfung, Ablehnung in einer
  Lumora-Instanz, Analyse des Modpacks Fabulously Optimized.
- **Start einer eigenen Fabric-Instanz** (`--launch-test --instance custom:<id>`, Demo-Modus, stumm): Fabric Loader 0.20.1
  für 1.21.11 installiert, 50 Mods geladen, 15 s stabil.
- **Oberfläche** per `--ui-shot` (Seiten `instances`, `instances/<key>`, `browser/<typ>`, `project/<slug>`).

## Tests (Etappe 2)

- **Unit-Tests**: CurseForge-Provider gegen einen lokalen Server (Header mit Schlüssel, Kategorien nach Namen, Loader-Tags
  der Dateien, fehlende Download-Erlaubnis, abgelehnter Schlüssel ohne Schlüssel in der Meldung, Fingerabdrücke),
  CurseForge-Modpacks mit Liste der Handdownloads, Updates samt festgehaltener Datei, Sicherung und Wiederherstellung,
  `.mrpack`-Export mit Wiedereinlesen, Lumora-Archiv hin und zurück, Launcher-Import (Prism, CurseForge, offizieller
  Launcher, Ordner-Scan) und Übertragen zwischen Instanzen.
- **Live-Prüfung** im Test-Home (`.t/e2e/Stage2E2E.java`): alte Sodium-Version installiert, Update gefunden und eingespielt,
  Sicherung zurückgespielt, Export wieder eingelesen, andere Launcher auf dem PC nur gelesen.
- **Starts eigener Instanzen je Loader** (`.t/e2e/LoaderInstances.java` legt sie an, dann `--launch-test --instance …`,
  Demo-Modus, stumm): Quilt 1.21.1 mit Mod Menu und Fabric API, NeoForge 1.21.1 mit JEI, Forge 1.20.1 mit JEI.
  Dabei gefunden und behoben:
  - Die Quilt-Schnittstelle liefert die Loader-Versionen unsortiert; Lumora nahm die erste stabile (0.24.0) statt der
    neuesten, und aktuelles Fabric API startete damit nicht. Die Liste wird jetzt nach Version sortiert
    (Vorabversionen numerisch: `beta.10` nach `beta.9`).
  - Das Spielende hing am Ende der Ausgabe statt am Ende des Prozesses. Ein Kindprozess (Fehlerfenster von Fabric/Quilt,
    Hilfsprozess einer Mod) hielt die Ausgabe offen, und Lumora meldete das Spiel weiter als laufend. „Beenden“ schließt
    jetzt auch Kindprozesse; der Starttest erkennt einen Abbruch des Loaders sofort.
- **Nicht live geprüft**: CurseForge selbst (kein Schlüssel vorhanden); Drag & Drop wurde nicht mit echter Maus geprüft.
