# Änderungen

## 0.3.40 (2026-11-13)

### Für Addon-Entwickler
- **Lumora-API 1.8.0:** Addons können zur Laufzeit Texturen aus Pixeln machen und zeichnen, etwa ein Album-Cover oder ein heruntergeladenes Bild: `AddonContext.textures()` → `DynamicTextures.upload(name, width, height, argb)` liefert eine `TextureRef` für `HudGraphics.texture`, `release(name)` gibt sie frei. Bis 512 × 512 Pixel, höchstens 32 Texturen je Addon; beim Abschalten des Addons werden alle freigegeben. In allen Versionen von 1.8.9 bis 26.3, ohne Berechtigung. Addons für 1.0 bis 1.7 laden unverändert.

### Addons
- **Spotify 1.1.0** (eigenes Addon) nutzt das für ein scharfes Album-Cover und bringt acht Layouts (Modern, Dynamic Island, Glas, Visualizer, Minimal, Vinyl, Micro, Klassisch), Album-Farben, Zufallswiedergabe und Wiederholen, einen Visualizer und Animationen – alles ohne Login.

## 0.3.39 (2026-11-13)

### Launcher
- **Discord Rich Presence funktioniert:** Der Launcher hat die Verbindung zu Discord gleich nach dem Setzen der Anzeige wieder getrennt (Discord antwortet mit `"evt": null`, das wurde als Fehler gelesen), deshalb erschien nichts. Die Antwort wird jetzt richtig gelesen, die Anzeige bleibt.
- **Discord zeigt, was du spielst:** Läuft Lumora, steht in Discord z. B. „Lumora 26.3“ und „auf eineip.de“ mit der Zeit seit dem Beitritt, sonst „Einzelspieler“ oder „Im Hauptmenü“. Das Spiel meldet seinen Zustand über eine lokale Datei in der Instanz (`.lumora/status.json`), in allen Versionen von 1.8.9 bis 26.3; Vanilla zeigt weiter die Version.
- Neuer Schalter **„Server in Discord zeigen“** (Einstellungen → Integrationen, anfangs an); ausgeschaltet steht nur „Mehrspieler“ da.

## 0.3.38 (2026-11-13)

### Für Addon-Entwickler
- **Lumora-API 1.7.0:** Addons können Textzeilen über und unter das Namensschild eines Spielers setzen (`NameTags.Decorator.lines` → `NameTags.Lines(above, below)`), gezeichnet wie der Name selbst, und eigene Zeilen an den Tooltip von Items hängen (`ItemTooltipEvent` mit `item()`, `count()`, `add(line)`). Beides in allen Versionen von 1.8.9 bis 26.3. Hat ein Spieler eine Zeile unter dem Namen, rückt die kleine Zeile eines Symbols (`Icon.subtitle`) eine Zeile tiefer. Addons für 1.0 bis 1.6 laden unverändert.

## 0.3.37 (2026-11-12)

### Im Spiel
- **Alben im Screenshot-Manager:** Neue Reiter „Fotos“ und „Alben“. Unter Alben stehen deine eigenen Alben (mit „Neues Album“) und automatische Alben nach Server, Spielern in der Nähe und Account – jeweils mit dem neuesten Bild als Titelbild und der Anzahl. Ein Klick öffnet das Album mit Suche, Filter und allen Aktionen; eigene Alben lassen sich umbenennen und löschen (die Bilder bleiben).
- Bei jedem neuen Screenshot (F2) merkt sich Lumora den Server (oder Einzelspieler), deinen Account und bis zu 8 Spieler in der Nähe. Ältere Screenshots haben diese Angaben nicht und erscheinen deshalb nicht in den automatischen Alben.
- Unter Fotos fügt „Zu Album“ die Auswahl in ein Album ein oder legt direkt ein neues an; in einem eigenen Album gibt es „Aus Album“. Die Suche findet jetzt auch Server-, Account- und Spielernamen, die Vorschau zeigt, wo und mit wem das Bild entstand. Beim Umbenennen eines Bildes ziehen Alben und Angaben mit.
- Auf schmalen Fenstern passt sich die Suche den Reitern an, und „Extern öffnen“ macht Platz, wenn er nicht reicht.

## 0.3.36 (2026-11-12)

### Im Spiel
- **Widget-Design unter Themes:** Das Widget-Design ist keine Modulkarte mehr, sondern steht unter Themes › Widgets: mit einer Live-Vorschau von zwei Beispiel-Widgets, dem Schalter „Ein Design für alle Widgets“ und „Einstellungen › Öffnen“ für alle Optionen.
- **RGB-Effekt:** Neuer Abschnitt „RGB“ in den Einstellungen des Widget-Designs. Akzentstreifen & Tasten, Rand, Beschriftung und Wert können einzeln durch den Regenbogen laufen; einstellbar sind Geschwindigkeit (1–10, ein Durchlauf in 12 bis 1,2 Sekunden), Sättigung und Helligkeit. Mit „Welle über die Widgets“ ist jedes Widget je nach Position etwas weiter im Regenbogen, so laufen die Farben über den Bildschirm. Dazu die Vorlage „RGB (Regenbogen-Rand)“. Gilt für alle eingebauten Widgets, die Tastenanzeige, die Leisten und Addon-Widgets, die das Widget-Design der API nutzen. Die Farbwähler des Widget-Designs haben dafür keinen eigenen Regenbogen mehr.

## 0.3.35 (2026-11-12)

### Im Spiel
- **Widget-Design:** Neues Modul im Hub (Kategorie Anzeige), mit dem du das Aussehen aller Widgets an einer Stelle festlegst: Hintergrund mit Farbe und Deckkraft, abgerundete Ecken, Rand, Akzentstreifen (Theme-Farbe oder eigene), Format von Beschriftung und Wert („FPS 60“, „FPS: 60“, „[FPS] 60“, „60 FPS“ oder nur der Wert), Beschriftung in Großbuchstaben, Textschatten, Farben von Beschriftung und Wert und die Größe aller Widgets. Dazu Vorlagen: Lumora, Minimal (nur Text) und Abgerundet mit Rand. Solange das Modul an ist, blenden die Widgets ihre eigenen Stil-Einstellungen aus; ab Werk ist es aus, damit sich nichts von selbst ändert.
- Es gilt für alle eingebauten Widgets in allen Versionen (FPS, CPS, Koordinaten, Uhr, Ping, Tempo, Server, Rüstung, Info-Widgets, Blockinfo, Toggle Sprint), die Tastenanzeige (Hintergrund, Ecken, Rand, Akzent beim Drücken) und Hotbar & Leisten (Hintergrund, Rand, Schatten). Farben mit Bedeutung bleiben, z. B. ein grüner oder roter Ping und die Farben von Herzen und Hunger.
- Die Größe aller Widgets gilt auch für Addon-Widgets; der HUD-Editor rechnet sie beim Ziehen und mit dem Mausrad mit ein.

### Für Addon-Entwickler
- **Lumora-API 1.6.0:** `Theme.widgetStyle()` liefert das Widget-Design (`active()`, Farben, Ecken, Rand, Format, `panel`, `line`, `lineWidth`), damit Addon-Widgets mitziehen können. Der Session-Timer zeigt es als Beispiel. Addons für 1.0 bis 1.5 laden unverändert.

## 0.3.34 (2026-11-12)

### Im Spiel
- **Spielermenü als Fenster:** Mausrad auf einem Spieler öffnet jetzt das Fenster *Spieler-Interaktionen* statt des Rings: links die Spielerkarte mit großem Kopf, Name, Freund- bzw. Lumora-Abzeichen, Online, Ping aus der Tab-Liste (auf Servern) und dem Lumora-Profil (Freund seit, gemeinsame Gruppen), rechts alle Aktionen als Liste mit Symbol. Kategorien von Addons öffnen eine eigene Liste mit einer Zeile zurück. Wie bisher: halten und über einer Aktion loslassen, oder kurz klicken und dann mit Klick, Mausrad oder neu mit Pfeiltasten und Enter wählen. Rechtsklick geht zurück, Esc oder ✕ schließt. „Client-Profil“ ist kein eigener Eintrag mehr, das Profil steht immer in der Karte.
- **In Party einladen** schickt jetzt `/party invite <Name>` an den Server (z. B. für Partys auf Netzwerken) statt einer Lumora-Social-Party. Nur auf Servern und nur bei echten Spielernamen.
- HUD-Widgets (z. B. Blockinfo, Combo) werden nicht mehr über dem Spielermenü und anderen Lumora-Fenstern gezeichnet (1.21.8 bis 26.3).

### Für Addon-Entwickler
- Kategorien im Spielermenü sind jetzt Zeilen, die eine eigene Liste öffnen (vorher äußerer Ring); die API bleibt gleich.

## 0.3.33 (2026-11-12)

### Im Spiel
- **Namensschild-Icons neben dem Namensschild:** Symbole von Addons (z. B. die Voice-Symbole von LumoraVoice) stehen jetzt rechts neben der Namensschild-Box, als eigenes Zeichen ohne Hintergrund, statt mit in der Box. Die Box ist wieder so breit wie der Name. Gilt in allen Versionen von 1.8.9 bis 26.3, auch durch Wände (durchsichtiger Teil des Namensschilds). In der Tab-Liste steht das Symbol weiter hinter dem Namen.

## 0.3.32 (2026-11-12)

### Launcher
- **News als Artikel:** Ein Klick auf eine News öffnet sie groß über dem ganzen Launcher, wie ein Artikel auf einer News-Seite: Titel, Kurzfassung, Datum, Autor und Version, ein großes Bild, dann der Text mit Zwischenüberschriften, Listen und Screenshots der neuen Funktionen und am Ende der komplette Changelog des Updates. Nur zum Lesen, ohne Kommentare oder Reaktionen. Esc, ✕ oder ein Klick daneben schließt den Artikel.
- **Ganzer Changelog:** Unter News → Changelog stehen jetzt alle Lumora-Versionen (bisher nur bis 0.3.7), jede mit ihren wichtigsten Punkten. „Ganzen Changelog anzeigen“ öffnet den Artikel zur Version.
- **Neue News** zu Lumora-API 1.5, Kinobalken, Spielermenü, HUD-Editor mit Ankerzonen, Hotbar & Leisten und Chat, Elytra-Streifen, Tastenanzeige und Addon-Bibliothek, mit Screenshots aus dem Spiel.
- Minecraft-News und Patch Notes öffnen sich ebenfalls als Artikel; „Im Browser öffnen“ führt zum ganzen Artikel auf minecraft.net.
- Lange Listen springen beim Öffnen einer Seite nicht mehr ans Ende.

### Im Spiel
- **Addon-Update aus der Bibliothek:** Nach dem Aktualisieren eines Addons gelten seine neuen Grafiken und Sprachdateien sofort beim nächsten Start (vorher konnte die alte Version gewinnen, z. B. rohe Schlüssel in den Einstellungen von LumoraVoice).

## 0.3.31 (2026-11-11)

### Lumora-API 1.5.0 – Addons erweitern den Client
- **Design:** Addons lesen deine Akzentfarbe und dein Theme (ein Wechsel wirkt sofort), zeichnen ihre Menüs mit Lumoras Steuerelementen (`UiKit`: Panel, Karte, Knopf, Schalter, Regler, Textfeld, Liste) und können eigene Akzentfarben im Hub anbieten (Berechtigung `design`). Die Farbreihe unter Themes bricht dafür um. Wählst du die Farbe eines Addons und ist das Addon aus, gilt Lila; mit dem Addon kommt deine Wahl zurück.
- **HUD:** Widgets eines Addons können an einer der neun Ankerzonen hängen, wie im HUD-Editor (`WidgetPlacement.anchored`).
- **Chat:** eigene Einträge im Menü von Chat-Nachrichten (Rechtsklick oder ⋯) und ein Event bei jeder neuen Nachricht mit Text, Absender und Links (Berechtigung `chat`).
- **Social:** Freunde mit Online-Status und die Party lesen, Event bei Änderungen (Berechtigung `social`).
- Das Spielermenü für Addons gibt es seit 1.4.0. Addons für ältere API-Versionen laden unverändert weiter.
- Das API-Artefakt heißt jetzt `lumora-api-1.5.0` (in 0.3.29 und 0.3.30 hieß es fälschlich noch 1.3.0).

### Doku
- Addons werden nicht mehr als Fabric-Mod beschrieben: Lumora lädt sie mit seinem eigenen Addon-Loader aus dem Ordner `addons`. Die Anleitung „Ein Addon erstellen“ hat neue Beispiele zu Design, HUD-Anker, Spielermenü, Chat und Social; das Beispiel-Addon bringt keine `fabric.mod.json` mehr mit.
- Die Addon-Vorlage, LumoraVoice und LumoraSpotify bauen jetzt gegen die API 1.5.0.

## 0.3.30 (2026-11-11)

### Spielermenü
- **Party-Einladung immer im Menü:** „In Party einladen“ und „Freund hinzufügen“ erscheinen jetzt immer, sobald Lumora Social eingerichtet ist. Geht es gerade nicht, sind sie ausgegraut und nennen den Grund – „nicht verbunden“ (kein Lumora-Social-Server erreichbar), „nutzt Lumora nicht“ oder „nur der Anführer“ (du bist in einer fremden Party). Vorher fehlten sie in diesen Fällen ganz.

### Zoom
- **Kinobalken:** neue Option in den Zoom-Einstellungen (Hub → Zoom → Verhalten), standardmäßig aus. Beim Zoomen gleiten oben und unten schwarze Balken herein und beim Loslassen wieder heraus. Alle Minecraft-Versionen.

## 0.3.29 (2026-11-11)

### Spielermenü
- **Mausrad auf einen Spieler:** Spieler anvisieren und das Mausrad gedrückt halten – um den Spieler erscheint ein Ring. Loslassen über einem Eintrag führt ihn aus; ein kurzer Klick lässt das Menü offen, dann wählst du mit Klick oder durch Drehen am Mausrad (Rechtsklick zurück, Esc schließt).
- **Ein Menü:** Über dem Ring die Spielerkarte (Kopf, Name, Lumora-Nutzer, Freund, Party), unter dem Ring der gewählte Eintrag. Kategorien öffnen ihre Einträge als äußeren Ring daneben, das Client-Profil erscheint neben dem Ring. Auf kleinen Fenstern wird das Menü kleiner.
- **Einträge:** Freund hinzufügen (bzw. Anfrage annehmen), In Party einladen (bzw. Party beitreten, Aus Party entfernen, Einladung zurückziehen), Client-Profil öffnen, Lumora-Nachricht (Freunde), Privat schreiben (`/msg`, auf Servern), Name kopieren.
- **Client-Profil:** ob der Spieler Lumora nutzt, Freund seit, Online-Status (nur Freunde), Party und gemeinsame Gruppen.

### Party
- **Neu im Lumora-Social-Server:** Einladen, Annehmen, Ablehnen, Verlassen und Entfernen; nur der Anführer lädt ein, Einladungen laufen nach 5 Minuten ab. Die Party lebt nur, solange jemand drin ist (nicht gespeichert).
- **Social-Menü (U):** Party-Bereich oben mit Einladungen (✔/✖), Mitgliedern, Anführer und „Verlassen“; neue Einladungen kommen als Benachrichtigung.
- Ein älterer Social-Server kennt Party und Profilkarte nicht und antwortet mit „Unbekannte Anfrage“; Freunde und Chat laufen weiter.

### Lumora-API 1.4.0
- `AddonContext.playerMenu()` (Berechtigung `playermenu`): eigene Einträge mit Symbol oder Textur, Kategorie, Reihenfolge, nötiger OP-Stufe, Bedingungen (`visibleWhen`, `enabledWhen`, `requiresLumoraUser`) und Aktion. Addons für 1.0 bis 1.3 laden unverändert weiter.

## 0.3.28 (2026-11-11)

### Hotbar & Leisten
- **Hotbar-Farben (26.x):** Die Vanilla-Hotbar von Lumora konnte mit rosa Rahmen, türkiser Auswahl und durchsichtigen Feldern erscheinen – Rot und Deckkraft der Grafik waren vertauscht. Hotbar, Auswahlrahmen, Zweithand-Slot, Herzen und die anderen Minecraft-Grafiken kommen jetzt aus Minecrafts eigenem GUI-Atlas, genau wie Minecraft sie zeichnet (26.1.2, 26.2, 26.3).

### HUD-Editor
- **Suche behält den Fokus:** Wenn sich das gewählte Widget bewegt oder seine Größe ändert und der Editor sich neu aufbaut, kannst du im Suchfeld weitertippen.

## 0.3.27 (2026-11-10)

### HUD-Editor
- **Ankerzonen:** Beim Ziehen teilt sich der Bildschirm in 3×3 Zonen, die Zone unter der Maus leuchtet mit ihrem Ankerpunkt. Das Widget hängt danach an der Ecke, Kante oder Mitte dieser Zone und behält bei anderer Fenster- oder GUI-Größe seinen Abstand dazu – ein Widget unten rechts bleibt unten rechts. Bestehende Layouts bleiben unverändert, bis du ein Widget verschiebst.
- **Magnet sichtbar:** Das Widget, an dem du einrastest, wird umrahmt, die Hilfslinie leuchtet; eingerastet wird an Kanten und Mitten.
- **Positionsanzeige:** Beim Ziehen steht „x × y“ am Widget, beim Skalieren Größe und Prozent; dazu blendet sich ein feines Raster ein.
- **Suche** über der Widget-Liste (Tastenkürzel wie G oder H sind beim Tippen aus).
- **Aktionen am Widget:** Die Karte mit Optionen, Ausblenden, Ein/Aus, Ebene und Standard erscheint direkt unter dem gewählten Widget (in der unteren Bildhälfte darüber) und wandert mit.
- **Klick holt nach vorne:** Ein angeklicktes Widget kommt in die oberste Ebene (mit Strg+Z rückgängig).
- **Freie Sicht beim Ziehen:** Widget-Liste und Karte verschwinden, solange du ziehst; vorher konnte ein Widget hinter der Liste verschwinden.
- Angelehnt an den HUD-Editor von LiquidBounce, neu umgesetzt.

## 0.3.26 (2026-11-10)

### Im Spiel
- **Neues Modul „Hotbar & Leisten“** (ab Werk aus, Hub → Spieler): Hotbar, Herzen, Rüstung, Hunger, Luft und Erfahrung werden zu sechs eigenen Widgets, die du im HUD-Editor verschieben, skalieren und einzeln ausblenden kannst.
  - **Leisten:** Stil Vanilla (die echten Minecraft-Symbole), Kompakt (Symbol und Zahl), Balken oder Nur Zahl; Zahl als Wert, Wert/Maximum oder Prozent; Text- und Balkenfarbe, Hintergrund, Textschatten. Herzen mit Absorption, Gift-, Wither- und Frost-Herzen und Wackeln bei wenig Leben; Rüstung und Luft blenden sich wie in Minecraft aus, wenn sie nicht gebraucht werden.
  - **Hotbar:** Vanilla- oder Lumora-Stil, Slot-Nummern, senkrecht, Zweithand links, rechts oder aus, Schild und Totem in der Zweithand ausblendbar.
- **Chat: Nachrichten-Menü:** Im offenen Chat erscheint neben der Nachricht unter der Maus ein „⋯“, alternativ Rechtsklick: Nachricht kopieren (ohne Lumora-Zeitstempel), Name kopieren, privat antworten („/msg Name “ im Eingabefeld), Link kopieren. Der Name wird auch bei Server-Formaten wie „[VIP] Steve: …“ erkannt.
- **Chat: Größe durch Ziehen:** Im offenen Chat den rechten Rand (Breite) oder den oberen Rand (Höhe) mit der Maus ziehen; Breite, Höhe offen und Höhe geschlossen gibt es auch als Regler, dazu „Größe zurücksetzen“.
- **Chat: Aussehen und mehr:** Hintergrund an oder aus mit Deckkraft, ohne Textschatten, wie lange Nachrichten im geschlossenen Chat sichtbar bleiben (2–120 s oder immer), Verlauf bis 1000 Nachrichten (ab Werk 500 statt 100), Ton, wenn jemand deinen Namen schreibt.

## 0.3.25 (2026-11-10)

### Im Spiel
- **Tastenanzeige: Buchstaben mittig:** W, A, S, D, die Leertaste und die Maustasten zeigen ihre Beschriftung jetzt genau in der Mitte der Taste. Vorher saßen die Buchstaben einen halben Pixel zu weit oben links (gerechnet wurde mit der vollen Zeilenhöhe statt der Höhe der Großbuchstaben) und LMB/RMB mit der CPS-Zeile gut einen Pixel zu hoch. Gilt für jede Textgröße, mit und ohne Schatten.

## 0.3.24 (2026-11-10)

### Im Spiel
- **Fadenkreuz genau in der Mitte:** Das Lumora-Fadenkreuz richtet sich jetzt nach der echten Fenstergröße statt nach dem gröberen GUI-Raster; vorher saß es je nach Auflösung und GUI-Größe 1–3 Pixel rechts unterhalb der Mitte. Es wird auf ganze Bildschirmpixel gesetzt und bleibt scharf (alle Modi, 1.8.9 bis 26.3).

### Hub
- **Eigenes Fadenkreuz frei zeichnen:**
  - „Spiegeln“ ist ab Werk aus – du malst frei, statt dass jeder Strich in alle vier Ecken kopiert wird (der Schalter bleibt).
  - Schnelle Striche werden lückenlos gezogen.
  - Das Mal-Raster wird größer, wenn Platz ist (bis 12 statt 7 Pixel pro Kästchen), mit Hinweis „gedrückt halten und ziehen“.

## 0.3.23 (2026-11-10)

### Im Spiel
- **Elytra-Streifen verbessert:**
  - **Dicker in Kurven:** In Schräglage und beim schnellen Drehen werden die Streifen bis zu 2,5-mal so breit und kräftiger; geradeaus bleiben sie dünn (abschaltbar).
  - **Breite nach Tempo:** Je schneller du gleitest, desto breiter die Streifen (abschaltbar).
  - **Leuchten:** Neuer Schalter, die Streifen bekommen einen hellen Saum und überblenden additiv – wirkt vor allem nachts. Ab Werk aus.
  - **Genauere Flügelspitzen:** Die Streifen starten dort, wo Minecrafts Gleitmodell die Flügel zeichnet: Es neigt sich zur Seite, in die du schaust, und klappt die Flügel im Sturzflug ein.

## 0.3.22 (2026-11-10)

### Im Spiel
- **Neues Modul „Elytra-Streifen“** (Kategorie Spieler, Minecraft 1.12.2 bis 26.3):
  - Hinter den Flügelspitzen gleitender Spieler entstehen zwei Streifen, die nach hinten breiter und blasser werden. Stil **Dampfstreifen** (gerade Linien) oder **Wirbel** (gegenläufige Spiralen, die sich aufweiten).
  - Einstellungen: eigene Streifen und die anderer Spieler getrennt, Farbe mit Deckkraft (auch Regenbogen), Länge (0,5–6 s), Breite und ab welchem Tempo (Standard 12 Blöcke/s).
  - Die eigenen Streifen sind in der Third-Person-Ansicht zu sehen (in der Ego-Sicht zeichnet Minecraft den eigenen Körper nicht).
  - In **1.8.9** ist die Karte gesperrt: Elytren gibt es erst ab Minecraft 1.9 (Hover-Fenster mit Grund).

### Hub
- Bei „zu alter“ Minecraft-Version steht im Hover-Fenster kein „(noch)“ mehr, und Versionen wie „1.9.x“ werden als „1.9“ angezeigt.

## 0.3.21 (2026-11-09)

### Hub
- **Hover-Fenster mit Grund, wenn etwas für die Minecraft-Version fehlt:**
  - Addons, die eine andere Minecraft-Version brauchen, sind gesperrt: Schloss, grauer Balken „INKOMPATIBEL“, kein Zahnrad; ein Klick zeigt den Grund kurz an. Das Hover-Fenster sagt, ob die Version **zu alt** (mit der ältesten unterstützten), **zu neu** (mit der neuesten) oder nicht dabei ist (mit der Liste) – oder ob das Addon eine neuere Lumora-Version braucht.
  - Bei Addons anderer Autoren steht dazu: „Dieses Addon wurde von … erstellt, nicht von Lumora. Darauf haben wir keinen Einfluss.“
  - **Addon-Bibliothek:** dasselbe Hover-Fenster bei „Nicht für …“ (neu: „Lumora zu alt“), bei fehlerhaften JARs der Fehler.
  - **Lumora-Schrift in 1.8.9 und 1.12.2** steht jetzt gesperrt auf der Themes-Seite statt ausgeblendet: Schriften (TrueType) gibt es erst ab Minecraft 1.13.

## 0.3.20 (2026-11-09)

### Im Spiel
- **Tastenanzeige mit Animationen von Badlion und LiquidBounce:**
  - Neuer Abschnitt **Animation** mit der Auswahl **Überblenden (Badlion)** oder **Füllen (LiquidBounce)**: Beim Füllen wächst die Farbe beim Drücken vom Rand zur Mitte (wie bei LiquidBounce mit der CSS-Kurve „ease“). „Übergang“ heißt jetzt „Dauer“ (0–400 ms). Beim Füllen wechselt der Text erst die Farbe, wenn die Taste fast voll ist.
  - Aus Badlion: **Höhe der Leertaste** (4–28 px) und **Textgröße** (50–150 %). Ab 14 px Höhe steht „Space“ auf der Leertaste.
  - **Vorlagen:** „LiquidBounce-Stil“ übernimmt dessen Aussehen (nur WASD und Leertaste, abgerundet, Schwarz mit 68 % Deckkraft, weißer Text, blaue Füll-Animation in 200 ms); „Lumora-Stil“ setzt alles zurück.

## 0.3.19 (2026-11-09)

### Im Spiel
- **Tastenanzeige mit den Einstellungen der Badlion-Keystrokes**, in drei Abschnitten:
  - **Tasten:** Maustasten, **CPS auf den Maustasten** (klein unter LMB und RMB, z. B. „12 CPS“), Leertaste und neu die **Schleichen-Taste** (standardmäßig aus).
  - **Aussehen:** **Tastengröße** (14–28 px), **Abstand** (0–6 px), **Übergang** (0–400 ms, Tasten leuchten weich auf und ab), **abgerundete Tasten**, **Rahmen** mit Farbe und **Breite** (1–3 px), **Textschatten**.
  - **Farben:** **Tastenfarbe** (mit Transparenz), Farbe gedrückt und **Textfarbe gedrückt**; beide „gedrückt“-Farben auch als Regenbogen.
  - Bisherige Einstellungen (Maustasten, Leertaste, Farbe gedrückt) bleiben erhalten.

## 0.3.18 (2026-11-09)

### Im Spiel
- **Addon-Bibliothek:** Im Lumora-Ordner gibt es jetzt den Ordner `addons` (unter Windows `%APPDATA%\LumoraClient\addons`, der Launcher legt ihn an). Lumora-Addons (.jar), die dort liegen, erscheinen im Hub unter **Allgemein › Addons** als Karten mit Name, Beschreibung, Version und Autor:
  - „Installieren“ kopiert das Addon in die Instanz; nach einem Neustart des Spiels ist es unter Module zu finden.
  - Ist eine ältere Version installiert, steht dort „Aktualisieren“ (mit beiden Versionsnummern); die alte Datei wird ersetzt. Ist sie gerade geladen, wird sie beim nächsten Start gelöscht.
  - Außerdem: „Installiert“, „Nach Neustart aktiv“, „Nicht für <Version>“ und „Fehlerhaft“ (mit Grund). „Ordner öffnen“ öffnet die Bibliothek.
  - Aus den Dateien der Bibliothek wird nur die Beschreibung gelesen, kein Code ausgeführt, bevor ein Addon installiert ist.
- **Favoriten:** Jede Modulkarte hat oben links ein Herz. Ein Klick macht die Karte zum Favoriten (rotes Herz): Favoriten stehen ganz oben und haben in der Seitenleiste den Eintrag „Favoriten“. Sie bleiben nach einem Neustart erhalten.
- Durch den neuen Eintrag „Addons“ ist die Kategorieliste links etwas kürzer; die unteren Kategorien erreicht man durch Scrollen.

## 0.3.17 (2026-11-09)

### Im Spiel
- **Theme und Schriften fest im Client:** „Lumora Theme“ und „Lumora Schrift: …“ stehen nicht mehr unter Optionen › Ressourcenpakete. Beides ist jetzt eingebaut und wird nur noch im Hub unter Themes geschaltet:
  - Die Lumora-Knöpfe und -Menüs sind an, wenn im Hub „Lumora“ gewählt ist, und aus bei „Vanilla“. Vorher blieb das Theme-Paket bei „Vanilla“ eingeschaltet.
  - Die Schrift folgt der Auswahl unter Themes › Schrift.
  - Theme und Schrift liegen über deinen eigenen Ressourcenpaketen, aber unter Ressourcenpaketen eines Servers. Eigene Server-Schriften und -GUIs bleiben damit erhalten.
  - Eigene Pakete können die Lumora-Knöpfe nicht mehr durch Umsortieren überschreiben; für Minecraft-Knöpfe im Hub „Vanilla“ wählen.
  - Wer im Hub eine Lumora-Schrift eingeschaltet hatte, deren Paket aber nicht aktiv war, sieht die Schrift jetzt.
- 1.8.9 und 1.12.2 sind nicht betroffen (dort gab es diese Pakete nie).

### Launcher
- Der Launcher schaltet das Theme nicht mehr in der `options.txt` ein und entfernt alte Einträge `lumora:lumora_theme` und `lumora:lumora_font_…` daraus.

## 0.3.16 (2026-11-09)

### Im Spiel
- **Hub aufgeräumt:** „Button-Design“ und „Server- & Weltliste“ stehen nicht mehr in der Modulliste, weil sie zu Themes gehören. Dort sind jetzt auch ihre übrigen Einstellungen: „Button-Schatten“ (bei einem anderen Knopf-Stil als Lumora) und unter „Lumora-Design“ die Schalter „Lumora-Serverliste“ und „Lumora-Weltliste“. „Verbindung“ bleibt ein Modul, weil es Auto-Reconnect und Zeitüberschreitung enthält.
- Die Beschreibung der Angriffsanzeige nennt die aktuellen Stile (Balken, Ring, Punkt, Levelleiste, Bossleiste).

## 0.3.15 (2026-11-05)

### Im Spiel
- **Angriffsanzeige aufgeräumt:** Der Stil „Minecraft“ ist entfernt; zur Wahl stehen Balken, Ring, Punkt, Levelleiste und Bossleiste. Wer „Minecraft“ gewählt hatte, bekommt den Standard „Ring“.
- Die Position „Wie in Minecraft“ ist entfernt: Mit der Minecraft-Standardoption war sie dasselbe wie „Am Fadenkreuz“. Es bleiben „Am Fadenkreuz“ und „An der Hotbar“; wer „Wie in Minecraft“ hatte, bekommt „Am Fadenkreuz“.

## 0.3.14 (2026-11-05)

### Im Spiel
- **Angriffsanzeige: Levelleiste und Bossleiste.** Zwei neue Stile neben Minecraft, Balken, Ring und Punkt:
  - **Levelleiste:** Beim Aufladen füllt sich die Erfahrungsleiste über der Hotbar in der Ladefarbe; mit „Prozent und Namen anzeigen“ steht der Prozentwert an der Stelle der Levelzahl. Ist der Schlag bereit (und „Ausblenden, wenn bereit“ an), ist wieder die normale Erfahrungsleiste zu sehen. Item-Cooldowns erscheinen als kurze Balken rechts neben der Hotbar.
  - **Bossleiste:** eine Leiste oben in der Mitte wie eine Boss-Leiste, jeder Cooldown als weitere Leiste darunter, mit Prozent bzw. Item-Namen darüber.
  - Beide sind auch in der dritten Person zu sehen; „Position“ und „Größe“ werden bei diesen Stilen ausgeblendet. Die Bossleiste kann sich mit echten Boss-Leisten und HUD-Widgets oben in der Mitte überschneiden.
- **Einstellungsseiten:** Eine Knopfreihe mit vielen Optionen nutzt bei kurzem Namen den Platz links davon, statt auf Pfeile zurückzufallen.

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

