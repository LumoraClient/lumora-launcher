# Änderungen

## 0.3.102 (2026-11-26)

### Client
- **Lumora Badge vor dem Namen:** Der Lumora-Stern steht jetzt vor dem Namen statt dahinter, über dem Kopf und in der Tab-Liste, in allen Versionen von 1.8.9 bis 26.3. Symbole von Addons (z. B. der Lautsprecher von LumoraVoice) bleiben hinter dem Namen, und beide sind jetzt gleichzeitig zu sehen. Auf 1.8.9 und 1.12.2 ist das ✦ hellblau.

## 0.3.101 (2026-11-26)

### Lumora-Backend
- **Offizieller Lumora-Server:** Freunde, Chat, Gruppen, Status, Cosmetics und Emotes laufen jetzt über den offiziellen Server `wss://lumora.jardateien.de` (TLS, Anmeldung über dein Minecraft-Konto bei Mojang, kein Passwort, kein Token an Lumora). Eigene Server lassen sich nicht mehr eintragen; die Server-Felder im Launcher sind entfernt.
- **Datenbank:** Der Server speichert in PostgreSQL und schreibt nur, was sich geändert hat. Tägliche Sicherung, Schema mit Versionen.
- **Privatsphäre:** Status „Online“, „Beschäftigt“, „Abwesend“ oder „Unsichtbar“ (Freunde sehen dich dann offline). Wer dir Freundschaftsanfragen schicken darf: alle, Freunde von Freunden oder niemand. Spieler blockieren und wieder freigeben (Launcher › Freunde › Einstellungen unten links und Menü am Freund). Blockierte Spieler sehen keinen Hinweis, keine Cosmetics, keine Emotes und kein Badge.
- **Nachrichten:** eigene Nachrichten bearbeiten und löschen, Suche im Verlauf (Protokoll und Server; Bedienung folgt).

### Client
- **Lumora Badge:** Neues Modul. Ein kleiner Lumora-Stern neben dem Namen von Spielern, die gerade mit Lumora spielen, über dem Kopf und in der Tab-Liste. Einstellungen: über dem Kopf, Tab-Liste, eigenes Badge, „Andere sehen mein Badge“. Der Server verrät nur Ja oder Nein und lässt ausgeblendete und blockierte Spieler weg. Auf 1.8.9 und 1.12.2 als ✦.

## 0.3.100 (2026-11-26)

### Launcher
- **Lumora Client neben Mod-Loadern:** Eigene Instanzen mit Fabric, Quilt, Forge oder NeoForge können den Lumora Client mitstarten („Lumora Client mitladen“ beim Erstellen, vorausgewählt, und in den Einstellungen der Instanz). Lumora-Menü, HUD, Addons und Cosmetics laufen dann neben deinen Mods. Fabric und Quilt ab 1.16.5, Forge und NeoForge ab 1.20.6, jeweils bis 26.3. Bei Fabric und Quilt bleibt der `mods`-Ordner unberührt; bei Forge und NeoForge legt der Launcher eine eigene Kopie in `mods/` und entfernt sie wieder, wenn die Option aus ist. Für 1.16.5 und 1.17.1 nimmt der Launcher automatisch Java 21. Greift eine Lumora-Funktion neben einem anderen Loader oder einer Mod nicht, schaltet sie nur sich selbst ab, statt das Spiel zu beenden. Instanzen mit Lumora tragen auf ihrer Karte eine Lumora-Plakette und „+ Lumora“ beim Loader.
- **Behoben:** Forge ab 1.20.6 startete nicht (das von Forge gepatchte Spiel fehlte beim Start).
- **Behoben:** Eigene Instanzen mit Loader „Lumora“ stürzten beim Start ab.

## 0.3.99 (2026-11-26)

### Launcher
- **Instanz-Auswahl auf der Startseite:** Der Pfeil neben „Spielen“ und der Knopf in der Spielleiste öffnen eine Liste deiner Instanzen statt der Versionskacheln. Mit Suche, der gewählten Instanz oben und importierten Instanzen in eigenen Gruppen („Importiert aus CurseForge“). Ein Klick wählt die Instanz; beim Überfahren öffnet das Zahnrad die Instanz, der Start-Knopf startet sie direkt. Unten „Instanz erstellen“ und „Dateien prüfen und spielen“.
- **Nur gewünschte Lumora-Versionen:** In der Instanzliste stehen nicht mehr alle Lumora-Versionen, sondern nur die, die du hinzufügst, und immer die gewählte. Hinzufügen über „Instanz erstellen“ › Lumora oder Vanilla › „Gemeinsamen Minecraft-Ordner nutzen“. „Aus der Liste entfernen“ löscht nichts. Beim ersten Start bleiben die gewählte Version und Versionen mit eigenem Ordner, in dem Welten liegen.
- **Versionswahl als Kacheln:** Beim Erstellen einer Instanz wählst du die Version wie früher im Versionsmenü: eine Kachel je Hauptversion mit Bild, Unterversionen als Chips, weitere hinter „›“. Snapshots teilen sich eine Kachel.
- **Neue Instanzkarten:** Großes Bild (eigenes Symbol oder das Bild des Updates), Loader und Version als Plaketten, darunter Name und Ersteller („Meine Erstellung“, „Von …“ beim Modpack-Autor, „Importiert aus …“). Instanzen sind nach Herkunft gruppiert.
- **Spielordner einer Lumora-Version:** Die Wahl zwischen Standard-Ordner und eigenem Ordner steht jetzt auf der Instanzseite unter „Einstellungen“.

## 0.3.98 (2026-11-25)

### Launcher
- **Instanzen für alles:** Neuer Bereich „Instanzen“. Die Lumora- und Vanilla-Versionen sind jetzt Instanzen, dazu beliebig viele eigene mit Fabric, Quilt, Forge, NeoForge oder Vanilla für jede passende Minecraft-Version. Erstellen, kopieren, umbenennen, löschen, eigenes Symbol; Starten direkt von der Karte. „Spielen“ und die Schnellauswahl folgen der gewählten Instanz.
- **Entdecken:** Mods, Modpacks, Ressourcenpakete, Shader und Datenpakete von Modrinth, auf Wunsch auch von CurseForge. Suche, Sortierung, Kategorien, Umgebung, Favoriten, „Nur passende“ für die gewählte Instanz, Ansichten Liste, Raster und Kompakt. Detailseite mit Beschreibung, Galerie, Versionen, Änderungsprotokoll und Abhängigkeiten.
- **Installer mit Prüfung:** Vor jeder Installation werden Loader, Minecraft-Version, Abhängigkeiten und Konflikte geprüft; der Abhängigkeitsbaum wird angezeigt. Jede Datei wird mit ihrer Prüfsumme kontrolliert, bei einem Fehler wird alles zurückgerollt.
- **Mod-Verwaltung je Instanz:** Mods, Ressourcenpakete, Shader, Lumora-Addons, Welten mit ihren Datenpaketen und eigene Einstellungen (Java, Arbeitsspeicher). Ein- und ausschalten, entfernen, Version festhalten, Mehrfachauswahl, Konfliktprüfung.
- **Updates:** Bereich „Updates“ je Instanz mit „Alle aktualisieren“, auf Wunsch mit vorheriger Sicherung. Angeboten werden nur stabile Versionen.
- **Sicherungen und Export:** Sicherungen erstellen und wiederherstellen (höchstens 10 je Instanz, vor dem Wiederherstellen wird der aktuelle Stand gesichert). Export als `.mrpack` oder als Lumora-Archiv samt Welten; ausgewählte Mods in eine andere Instanz kopieren.
- **Import:** Modpacks als `.mrpack` und CurseForge-Zip mit Vorschau vor dem Import. Instanzen aus Prism Launcher, MultiMC, CurseForge-App, Modrinth App (`profile.json`) und dem offiziellen Launcher übernehmen; die anderen Launcher werden nur gelesen.
- **Drag & Drop:** Dateien aus dem Explorer auf das Fenster, eine Instanzkarte oder die Instanzseite ziehen; Lumora erkennt den Inhalt. Instanzkarten auf eine andere Instanz ziehen kopiert Mods und Pakete oder übernimmt die Einstellungen (mit Rückfrage und Sicherung). Archive werden gegen Zip-Slip geprüft.
- **CurseForge mit eigenem Schlüssel:** Unter Optionen › Integrationen trägst du deinen eigenen CurseForge-API-Schlüssel ein. Er wird verschlüsselt gespeichert (Windows DPAPI) und nie protokolliert. Lumora liefert keinen Schlüssel mit; ohne Schlüssel bleibt CurseForge aus. Dateien, deren Autor keine Downloads über andere Apps erlaubt, werden nie auf anderem Weg geladen, sondern mit Link zur Seite genannt.
- **Deutsch als Standardsprache:** Der Launcher ist vollständig auf Deutsch, Englisch lässt sich unter Optionen wählen.
- **Behoben:** Ein beendetes Spiel galt weiter als laufend, wenn ein Kindprozess (z. B. das Fehlerfenster eines Mod-Loaders) die Ausgabe offen hielt. „Beenden“ schließt jetzt auch Kindprozesse.

## 0.3.97 (2026-11-25)

### Client
- **Bessere Cosmetics-Modelle:** Alle 16 Cosmetics sind neu gebaut, mit mehr Teilen, Kantenschattierung und eigenen Mustern (Strick, Leder, Metall, Flammen). Engelsflügel haben einzelne Federn, außen länger; Drachenflügel Klauen und eine gewellte Membran zwischen den Fingern; der Umhang eine Goldborte, den Lumora-Stern, Fransen und Schließen auf der Brust; die Krone ein Samtkissen mit Kreuz; der Zylinder eine gebogene Krempe und eine Goldschnalle; die Katzenohren zucken ab und zu.
- **8 neue Cosmetics:** Fischerhut (Schwarz, Beige, Oliv, Weiß, Blau), Mütze mit hüpfendem Bommel, Basecap, Teufelshörner, Hasenohren, Kopfhörer (lassen sich zusammen mit einem Hut tragen), Schal, der beim Laufen weht, und Schmetterlingsflügel. Alle in mehreren Farben. Insgesamt 24 Cosmetics.
- **22 neue Emotes:** Dab, Hampelmann, Liegestütze, Knien, Meditieren, Zombie, Muskeln zeigen, Daumen hoch, Herz, Headbangen, Wiegen, Hip-Hop, Karate, Schattenboxen, Gähnen, T-Pose, Erschrecken, Wütend, Kusshand, Rückwärtssalto, Schweben und Hühnertanz. Insgesamt 42 Emotes.
- **Variante als Ausklappliste:** Die Variante des gewählten Cosmetics wählst du jetzt aus einer Liste („Variante [Schwarz ▾]“), das Mausrad über dem Feld blättert weiter.

## 0.3.96 (2026-11-25)

### Client
- **Cosmetics & Emotes für andere Lumora-Spieler:** Andere Lumora-Spieler sehen deine Cosmetics (mit Variante und Versatz) und deine Emotes, und du siehst ihre, wenn ihr mit demselben Lumora-Social-Server verbunden seid (Adresse im Launcher unter „Freunde“). Neue Einstellungen im Bereich „Mehrspieler“: „Sichtbar für“ (Alle, Freunde, Niemand; Standard Alle) und „Cosmetics anderer Spieler zeigen“ (an). Gesendet werden nur die eingebauten Cosmetics und der Start oder das Ende eines Emotes an Spieler im Umkreis von 48 Blöcken. Eigene Dateien und die Vorschau im Menü bleiben lokal. Spieler ohne Lumora werden unverändert gezeichnet.
- **Emote-Rad per Halten:** Taste (ab Werk R) halten, auf ein Emote zeigen, loslassen spielt es. Kurz tippen lässt das Rad offen wie bisher.
- **Emote-Rad mit Seiten und Tauschen:** 1 bis 8 Seiten mit je 4 bis 10 Plätzen (Einstellungen, Standard 3 Seiten mit 6 Plätzen). Blättern mit Mausrad, Pfeiltasten oder den Knöpfen unter dem Rad. „Bearbeiten“ im Emote-Menü öffnet das Rad zum Bearbeiten: Ein Platz öffnet ein Fenster mit kleinem Rad und einer durchsuchbaren Liste aller Emotes zum Tauschen, mit „Platz leeren“ und „Abbrechen“. Rechtsklick leert einen Platz.
- **Varianten und Versatz:** 14 der 16 Cosmetics gibt es in Farbvarianten (z. B. Zylinder Schwarz, Braun, Weiß; Engelsflügel Weiß, Gold, Schwarz; Umhang Blau, Rot, Grün, Lila). Unter der Vorschau stellst du Variante und Versatz X/Y/Z (±8 Pixel in halben Pixeln, Rechtsklick setzt auf 0) für das gewählte Cosmetic ein. Beides wirkt sofort in der Vorschau, am angelegten Cosmetic und bei anderen Spielern und wird gespeichert.

### Lumora Social Server
- **Neue Nachrichten** `cosmetics.set`, `cosmetics.watch`, `emote.play`, `emote.stop` (Server → Client: `cosmetics`, `emote`): Der Server hält die Ausrüstung nur, solange die Verbindung steht, schickt Änderungen nur an Spieler, die den Spieler in der Nähe haben, leitet Emotes weiter, ohne sie zu speichern, prüft und kürzt alle Werte und begrenzt die Rate. Ältere Clients ignorieren die neuen Nachrichten.

## 0.3.95 (2026-11-24)

### Client
- **Cosmetics & Emotes:** Neues eingebautes Modul (an). Cosmetics am Spielermodell, unabhängig von Items und Inventar-Plätzen, und Emotes für den ganzen Körper ohne Item in der Hand. Nur lokal: nur du siehst sie, es wird nichts gesendet.
  - **Start-Set:** 16 Cosmetics in den Kategorien Flügel, Umhänge, Hüte, Accessoires, Effekte und Begleiter (viele animiert, je nach Zustand: stehen, laufen, rennen, schleichen, springen, fallen, gleiten, schwimmen) und 20 Emotes in den Kategorien Tanzen, Sozial, Lustig, Aktionen, Reaktionen, Sitzen, Feiern und Spezial.
  - **Emotes und Bewegung:** Ein Emote bewegt nur die Körperteile, die es animiert, und blendet weich ein und aus. Die übrigen behalten Minecrafts Bewegung. Ganzkörper-Emotes enden beim Laufen (Einstellung). Auf Wunsch wechselt die Kamera währenddessen in die dritte Person.
  - **Cosmetics-Menü (Taste J):** Kategorien, Suche, Filter nach Seltenheit, Favoriten, „Angelegt“, Live-Vorschau des eigenen Spielers (ziehen: 360° drehen, Mausrad: zoomen). Klicks probieren Cosmetics und Kombinationen an, ohne sie anzulegen, „Übernehmen“ legt sie an. Ein Platz pro Art: ein zweiter Hut ersetzt den ersten.
  - **Emote-Menü, Rad und Tasten:** Emote-Menü mit Kategorien, Suche, Favoriten, Vorschau in Schleife, Abspielen und Beenden. Emote-Rad (Taste R, 4 bis 10 Plätze, Belegung im Emote-Menü). Frei belegbare Tasten für Emote-Menü, Emote beenden und die Plätze 1 bis 4.
  - **GeckoLib-Format:** Modelle und Animationen im GeckoLib-/Blockbench-Format (`.geo.json`, `.animation.json`). Die Teile ohne Minecraft-Bezug sind aus GeckoLib 5 portiert (MIT): Easing-Kurven, Keyframes mit Pre/Post und Catmull-Rom, Modell-Baking mit Box- und Face-UVs. Dazu Molang und Animations-Controller mit Übergängen, gleichzeitigen Animationen, Overlays und Sound-Keyframes (nicht auf 1.8.9 und 1.12.2).
  - **Eigene Inhalte:** eigene Cosmetics in `config/lumora/cosmetics/<name>/` (Modell, Textur, optional Animationen und `cosmetic.json`), eigene Emotes in `config/lumora/emotes/*.animation.json`. Beschreibung in `docs/COSMETICS.md`.
  - **Verfügbarkeit und Speicherort:** für alle Minecraft-Versionen 1.8.9–26.3. Gespeichert in `config/lumora/cosmetics.json`.

## 0.3.94 (2026-11-24)

### Client
- **Chat: Ziehen zum Scrollen:** Im offenen Chat die linke Maustaste auf den Nachrichten halten und ziehen scrollt die Nachrichten (nach unten ziehen zeigt ältere), das Fenster bleibt an seiner Stelle. Ein Klick ohne Ziehen führt das Klick-Ereignis der Nachricht beim Loslassen aus wie bisher. Die Ränder eines Fensters am Bildschirmrand sind leichter zu greifen.
- **Chat: Beim Verlassen behalten:** Neue Einstellung (an): Der Chat bleibt beim Verlassen einer Welt oder eines Servers erhalten, Minecraft leert ihn dann auch nicht (auch ohne „Tabs und Fenster“). F3+D leert weiterhin.
- **Chat: Längerer Verlauf:** Die Einstellung „Verlauf“ geht jetzt bis 5000 Nachrichten (vorher 1000).
- **Chat anschauen:** Neue Taste (ab Werk Z): solange gedrückt, zeigt der Chat im Spiel alle Nachrichten in voller Höhe ohne Ausblenden, ohne ihn zu öffnen.

## 0.3.93 (2026-11-24)

### Client
- **Chat: Tabs, eigene Fenster, Filter und Spielerköpfe:** Lumora zeichnet den Chat jetzt selbst (Chat-Modul, Einstellung „Tabs und Fenster“, an). Im offenen Chat sitzt eine Tab-Leiste über jedem Chat-Fenster: „+“ für einen neuen Tab, „≡“ für die Liste aller Tabs und Fenster (wechseln, bearbeiten, neuer Tab, Fenster zurück an ihren Platz), Stift am aktiven Tab zum Bearbeiten, Rechtsklick auf einen Tab: bearbeiten, in ein eigenes Fenster bzw. in den Haupt-Chat, löschen. Inaktive Tabs zählen neue Nachrichten. Ein Assistent in drei Schritten (Start, Anpassen, Name und Ort) legt Tabs an: Vorschläge aus den letzten 50 Nachrichten (Spieler, Präfix, Rang, mit Anzahl) oder eigene Regeln (Erwähnungen von mir mit weiteren Wörtern, Spieler, Präfix, Rang, Wörter, Nachrichten ohne Spieler, alle), auch im Haupt-Chat behalten oder herausnehmen, Ton, farbige Hervorhebung, Vorschau. Eigene Fenster lassen sich an der Leiste verschieben und an jedem Rand und jeder Ecke in der Größe ziehen, der Haupt-Chat ebenso. Das Nachrichtenmenü (Rechtsklick oder „⋯“) hat „Filter …“ mit Vorschlägen aus dieser Nachricht. Vor Nachrichten von Spielern aus der Tabliste steht ihr Kopf (Einstellung „Spielerköpfe“). Die Zeilen bleiben Minecrafts eigener Text: Schriften, Farben, Klick- und Hover-Ereignisse der Server funktionieren wie vorher. Tabs und Fenster werden in `config/lumora/chat-tabs.json` gespeichert. Für alle Minecraft-Versionen 1.8.9–26.3.

## 0.3.92 (2026-11-22)

### Launcher
- **Absturz-Analyse:** Neuer Knopf „Analyze“ auf der Seite Crashes, oben neben Version und Refresh (neuester Absturz) und auf jeder Absturz-Karte. Lumora liest den Crash-Report, das Ende der Spielausgabe und ein Java-Absturzprotokoll (`hs_err_pid*.log`) aus derselben Zeit und zeigt, was passiert ist, warum, was hilft (Schritte) und die Zeile im Report, an der die Ursache erkannt wurde. Erkannt werden Speichermangel in Minecraft oder auf dem PC, Abstürze im Grafiktreiber (NVIDIA, AMD, Intel), Vulkan-Fehler, falsches Java, beschädigte oder unpassende Dateien, Mods, die nicht passen oder sich nicht einhaken können, Fehler in Lumoras eigenem Code, abgelehnte Anmeldung, Fehler in der Welt und in Ressourcenpaketen. Ein Knopf führt den Vorschlag aus: Speicher erhöhen oder senken, auf Vulkan oder OpenGL umstellen (26.2+), Reparieren und Starten, die Mod oder Erweiterung abschalten, neu anmelden oder die Bibliothek öffnen; danach bietet der Launcher den Neustart an. Die Analyse läuft komplett lokal.

## 0.3.90 (2026-11-22)

### Client
- **Hub: Karte anklicken öffnet die Einstellungen:** Das Zahnrad auf den Karten ist weg. Ein Klick auf die Karte (Icon, Name, freie Fläche) öffnet die Einstellungen, die Leiste unten (AKTIV / INAKTIV) über die ganze Breite schaltet an und aus. Karten ohne Einstellungen schalten mit einem Klick wie bisher an und aus. Der Hinweis beim Darüberfahren sagt das jetzt, der Text der Zoom-Karte nennt die aktuellen Einstellungen. Für alle Karten (Module, Addons, Zoom, Widgets) in allen Minecraft-Versionen.

## 0.3.89 (2026-11-20)

### Launcher
- **Mehrere Instanzen funktionieren mit derselben Version:** Mit „Multiple instances“ (Settings → Game) lässt sich dieselbe Version jetzt mehrmals gleichzeitig starten, bis zu 8 Läufe; bisher kam beim zweiten Start „The profile … is already running“. Jeder weitere Lauf hat eine eigene Sperre im Instanzordner, eine eigene Spielausgabe (`logs/lumora-game-output-2.log` usw.) und eine eigene Statusdatei für Discord. In 1.8.9 bis 1.18.2 bleiben Native-Dateien, die das laufende Spiel geladen hat, beim zweiten Start liegen, statt den Start abzubrechen. Der Launcher verfolgt alle laufenden Spiele; endet eins, bleibt er bei „spielt“, solange ein anderes läuft. Ohne die Einstellung läuft jede Version wie bisher nur einmal.

## 0.3.86 (2026-11-19)

### Client
- **Zoom: neue Einstellungsseite:** Die Zoom-Taste lässt sich direkt auf der Seite setzen (es bleibt Minecrafts Zoom-Taste). „Zoom halten“ ersetzt den Modus (an: Zoom, solange die Taste gedrückt ist; aus: die Taste schaltet an und aus). Die Zoom-Stärke ist ein Regler von 1,5 bis 20 statt fester Stufen, mit dem Mausrad geht es weiter bis 50. Neu: „Hand in der Ich-Perspektive zeigen“ (aus: Hand und Item beim Zoomen ausgeblendet), „Balkenhöhe (%)“ für die Kinobalken (5 bis 25), „HUD ausblenden“ (HUD und Chat wie mit F1 beim Zoomen) und bei der Kinokamera „Nach dem Übergang ausschalten“. Der Zoom-Übergang hat jetzt je Richtung eine eigene Kurve (Linear, Ease In, Ease Out, Ease In Out) und Dauer (0 bis 1000 ms, Standard 100) und läuft nach Zeit, also bei jeder Bildrate gleich weich. In allen Minecraft-Versionen.

## 0.3.85 (2026-11-19)

### Client
- **Held Item Tooltip: dauerhaft oder mit/ohne Fade-out:** „Immer anzeigen“ heißt jetzt „Dauerhaft anzeigen“: der Name des Items bleibt stehen. Neu ist der Schalter „Ausblenden (Fade-out)“ (Standard an): an blendet der Name nach 2 Sekunden weich aus wie bei Minecraft, aus verschwindet er nach 2 Sekunden auf einmal. Der Schalter erscheint nur, solange „Dauerhaft anzeigen“ aus ist. In allen Minecraft-Versionen.

## 0.3.83 (2026-11-19)

### Client
- **Neues HUD-Widget „Inventory“:** Zeigt dein Inventar auf Tastendruck als HUD: die drei Reihen mit Items, Anzahl und Haltbarkeitsbalken, auf Wunsch die Hotbar als vierte Reihe. Taste Standard X, änderbar; Modi „Taste schaltet an und aus“ (Standard), „Kurz nach dem Drücken“ (1 bis 10 Sekunden), „Solange die Taste gehalten wird“ und „Immer“. Stil „Minecraft-Inventar“ oder „Lumora-Widget“, Überschrift abschaltbar. Ab 1.12 belegt Minecraft X selbst („Hotbar laden“); dort startet die Taste ungebunden, bis du X in Minecrafts Steuerung freigibst und sie zuweist. Das Widget ist im HUD, aber unsichtbar, bis du die Taste drückst. In allen Minecraft-Versionen.

## 0.3.82 (2026-11-19)

### Client
- **Animations: Schalter „Alte Block-Animation“:** Server seit 1.9 kennen kein Blocken mit dem Schwert. Ist der neue Schalter unter „Schwert / Blocken“ an, zeigt ein Schwert die Blockhaltung von 1.7, solange du die rechte Maustaste hältst; schlägst du dabei zu, läuft der Schlag weiter (Blockhitting). Auch beim Blocken mit einem Schild in der Nebenhand zeigt das Schwert die alte Haltung, und in der dritten Person hebt dein Spieler den Arm. Das gilt auch, wenn „Schwert / Blocken“ auf Default steht. Nur die Anzeige: Du nimmst nicht weniger Schaden, andere Spieler sehen es nicht. „Legacy (alles)“ schaltet ihn ein, „Default (alles normal)“ aus. In 1.16.5 bis 26.3.

## 0.3.81 (2026-11-19)

### Client
- **Modul „Animations“ in allen Versionen ab 1.16.5 und einfacher:** Jeder Teil hat nur noch zwei Optionen: „Default“ lässt Minecraft unverändert, „Legacy“ zeigt das Original von 1.7. Bei Rückwärtslaufen und Kopfdrehung heißt die Option „Legacy (1.7/1.8)“, weil beide gleich sind, bei den Creative-Tabs „Legacy (Tabs von 1.8)“, weil 1.7 dieselben Tabs hatte. Presets: „Legacy (alles)“, „Default (alles normal)“ und „Custom“. Das Rezeptbuch ist jetzt ein normaler Teil („Legacy (1.7, ohne Rezeptbuch)“). Neu in 1.16.5 bis 1.21.11: Rückwärtslaufen, Kopfdrehung, Item-Wechsel, Block bauen, Schleichen, Herzen, Item-Haltung in der ersten Person (Schwert, Bogen, Essen, Angel), Item-Haltung in der dritten Person und Rezeptbuch; die Creative-Tabs von 1.8 ab 1.19.4 (davor hat Minecraft noch die alten Tabs). In 1.12.2 vorerst nur Rückwärtslaufen und Kopfdrehung. Teile, die eine Version noch nicht hat, sind dort ausgeblendet. Einstellungen aus 0.3.80 (1.7 / 1.8 / Modern) stehen nach dem Update wieder auf „Default“.

## 0.3.80 (2026-11-19)

### Client
- **Neues Modul „Animations“:** klassische Animationen oder die modernen, jeder Teil einzeln: Schleichen, Herzen, Block bauen, Rückwärtslaufen, Kopfdrehung, Schwert/Blocken, Item-Haltung in erster und dritter Person, Bogen, Essen, Angel, Item-Wechsel, Rezeptbuch entfernen und die Creative-Tabs von 1.8. Nachgebaut nach dem Original-Code von 1.7.10 und 1.8.9. Der Schlag-Stil (Vanilla, Smooth, Spin, Slap) ist von der Themes-Seite ins Modul gezogen; der alte Wert wird übernommen. Zuerst nur in 26.1.2, 26.2 und 26.3.

## 0.3.79 (2026-11-19)

### Client
- **Neues HUD-Widget „Held Item Tooltip“:** Nach dem Wechsel des Items zeigt es kurz den Namen des Items in der Hand, wie Minecraft über der Hotbar, und blendet dann aus. Stil „Pocket“ (Standard): dunkle Box mit Rahmen, darin der Name in der Farbe seiner Seltenheit und darunter die Verzauberungen (Flüche rot), wie in der Pocket-/Bedrock-Edition. Stil „Java“: nur der Name mit Schatten wie bei Minecraft. Einstellungen „Verzauberungen anzeigen“ und „Immer anzeigen“. Solange das Widget im HUD ist, blendet Lumora Minecrafts eigenen Namen aus. Standardmäßig aus, einschalten im HUD-Editor. In allen Minecraft-Versionen.

## 0.3.78 (2026-11-19)

### Client
- **Trank-Effekte als Liste:** Das HUD-Widget „Potion Effects“ zeigt jeden Effekt in einer eigenen Box mit dem Effekt-Bild aus Minecraft, Name und Stufe, darunter die Restzeit. Effekte von Beacon oder Conduit bekommen einen türkisen Rand, schädliche Effekte stehen rot da. Hintergrund und Rand der Boxen folgen dem Widget-Design. Die bisherige Anzeige (eine Zeile pro Effekt, ohne Bild) gibt es weiter über die neue Einstellung „Kompakt“. In allen Minecraft-Versionen (1.8.9 und 1.12.2 mit den Bildern aus `inventory.png`).

## 0.3.77 (2026-11-19)

### Client
- **Eigenes Server-Mapping:** Banner, Icons, Namen, Kategorien und Spielmodi der Serverliste kommen jetzt nur noch aus dem Lumora-Repository LumoraClient/Multiplayer-Mapping (GPL-3.0). Es wird dort automatisch aus NoRiskClient/nrc-server-mappings und LabyMod/server-media zusammengestellt: 375 Server in einer Datei, die Bilder auf die angezeigte Größe verkleinert. Der Client lädt damit beim ersten Öffnen eine Datei statt rund 400 einzelner, Bilder weiter nur für Server in der eigenen Liste, gegen den Git-Hash geprüft und zwischengespeichert. Die alten Zwischenspeicher `config/lumora/server-mappings` und `server-gamemodes` werden entfernt. Die Schalter „Server-Mappings“ und „Spielmodi anzeigen“ bleiben.

## 0.3.76 (2026-11-19)

### Client
- **Serverliste: Spielmodi:** Bekannte Server zeigen in der Lumora-Serverliste unter dem MOTD ihre Spielmodi als farbige Felder (so viele, wie Platz ist, der Rest als „+n“), nur die für die eigene Minecraft-Version. Ohne NoRisk-Mapping steht statt der bloßen Adresse der Servername. Die Texte kommen aus dem öffentlichen Repository LabyMod/server-media; es hat keine Lizenz, darum liefert Lumora nichts davon mit, der Client liest nur Name, Adressen und Spielmodi beim Spieler direkt von GitHub (höchstens alle 12 Stunden, gegen den Git-Hash geprüft, zwischengespeichert), keine Bilder. Abschaltbar im Modul „Server- & Weltliste“ (Schalter „Spielmodi anzeigen“). Die Zeilen der Serverliste sind dafür etwas höher.

## 0.3.75 (2026-11-19)

### Client
- **Serverliste: Drag and Drop:** Server lassen sich in der Lumora-Serverliste mit der Maus an eine neue Stelle ziehen; eine Linie zeigt, wo der Server landet, am Rand scrollt die Liste mit. Auch mit Suche oder Filtern landet er an der richtigen Stelle der ganzen Liste. Die ▲/▼-Knöpfe bleiben; die Reihenfolge steht in Minecrafts `servers.dat`.
- **Server-Mappings:** Bekannte Server bekommen in der Lumora-Serverliste ihr Banner als Hintergrund, ihr Icon und ihre Kategorien, und statt der bloßen Adresse ihren Namen. Die Daten kommen aus dem öffentlichen Projekt NoRiskClient/nrc-server-mappings (GPL-3.0): die Liste höchstens alle 12 Stunden von GitHub, Bilder nur für Server in der eigenen Liste, alles gegen den Git-Hash geprüft und zwischengespeichert. Abschaltbar im Modul „Server- & Weltliste“ (Schalter „Server-Mappings“).

## 0.3.74 (2026-11-18)

### Client
- **Tasten in den Einstellungen:** Wer bei einem Modul, Widget oder Addon eine Taste festlegt, tippt den Buchstaben nicht mehr zusätzlich in die Suchleiste. Solange „Taste drücken…“ angezeigt wird, geht kein Text in die Suche.
- **ESC entfernt die Taste:** ESC während „Taste drücken…“ setzt die Taste auf keine (Anzeige „Keine“), statt nur abzubrechen; das Einstellungsfenster bleibt offen. Backspace und Entf löschen sie wie bisher.

## 0.3.73 (2026-11-18)

### Client
- **Lumora API 1.12: NoClip, Blockmerkmale und Item-Farben für Addons** (nur Minecraft 26.1.2, 26.2 und 26.3): Addons können NoClip beim Fliegen im Kreativmodus einschalten. In der eigenen Welt (Einzelspieler, LAN-Host) lässt Lumora den Spieler durch Blöcke, die Welt bleibt im Block sichtbar; auf anderen Servern nur, wenn der Server es zulässt, sonst schaltet sich NoClip beim Zurücksetzen ab. Dazu Blockmerkmale (voller Block, fest, undurchsichtig, gleiche Textur auf allen Seiten) und die Farbe von Items. Grundlage für BuildingUtilities 1.2.0.

## 0.3.72 (2026-11-18)

### Client
- **Lumora API 1.11: mehr Bauwerkzeuge für Addons** (nur Minecraft 26.1.2, 26.2 und 26.3): Liste aller Blöcke, mittlere Texturfarbe eines Blocks, Suche im Kreativ-Inventar, farbige Umrandungen um Blöcke in der Welt und Item-Bilder in Addon-Fenstern. Grundlage für BuildingUtilities 1.1.0 (Tinker, Color Picker, Hotbar-Pool, Item-Suche).

## 0.3.71 (2026-11-18)

### Client
- **Lumora API 1.10: Bauwerkzeuge für Addons** (nur Minecraft 26.1.2, 26.2 und 26.3). Neue Berechtigung `building` und `AddonContext.building()`: anvisierter Block mit Block-States, Hotbar lesen und wählen, Kreativ-Slots setzen (auch mit Block-State), Block aufnehmen, abbauen und platzieren, sich vor Rechtsklick und Mittelklick einhängen (normal / wie Schleichen / abbrechen), Wartezeiten beim Setzen und Abbauen verkürzen. Alles nur im Kreativmodus und nur mit den Paketen, die ein Spieler auch selbst schicken würde; keine Befehle. Grundlage für das Addon BuildingUtilities. Bauplan: `docs/LUMORA_API_1_10_BUILDING.md`.

## 0.3.70 (2026-11-18)

### Client
- **Toggle Sprint: Fliegen.** Neuer Abschnitt mit zwei Reglern. „Fluggeschwindigkeit“ (1x bis 5x, Standard 1x) macht das Fliegen im Kreativ- und Zuschauermodus schneller, waagerecht wie hoch und runter. „Flug-Schwung“ (0 bis 100 %, Standard 100 %) legt fest, wie weit du nach dem Loslassen der Bewegungstasten weitergleitest; bei 0 % hältst du sofort an. Nur die eigene Bewegung ändert sich, der vom Server gesendete Flugwert bleibt unverändert. In allen Versionen von 1.8.9 bis 26.3.

### Launcher
- **News:** Artikel „Toggle Sprint: schneller fliegen“.

## 0.3.69 (2026-11-18)

### Launcher
- **News:** neuer Artikel „Light Block Overlay: Lichtstufen“ zum Lichtstufen-Modus aus 0.3.68, mit zwei Bildern (Zahlen und Flächen um zwei Fackeln).

## 0.3.68 (2026-11-17)

### Client
- **Light Block Overlay mit zwei Modi:** Neu ist der Modus „Lichtstufe“, jetzt Standard. Er zeigt auf jedem Block, auf dem man stehen kann, die Lichtstufe von 15 (voll ausgeleuchtet) bis 0 (kein Licht): als Zahl, Fläche oder Markierung, in einer Farbe von dunkel über mittel bis hell (drei Farben einstellbar). Wählbar sind Blocklicht, Himmelslicht oder das hellere von beiden. Der bisherige Modus „Spawn-Stellen“ bleibt vollständig erhalten (rot sofort, gelb nur nachts, Kreuz/Zahl/Fläche/Markierung, Farbverlauf, Zahlen auf sicheren Blöcken, Lichtstufe für den Spawn) und ist in den Einstellungen mit einem Klick zurück. In allen Versionen von 1.8.9 bis 26.3.

## 0.3.67 (2026-11-17)

### Client
- **Neues Modul „Hidden Armor“:** Helm, Brustpanzer, Hose und Stiefel einzeln unsichtbar oder durchsichtig machen (0 bis 100 %), für dich allein oder für alle Spieler. Wirst du getroffen, ist die Rüstung für eine einstellbare Zeit wieder voll zu sehen und blendet danach auf Wunsch langsam aus. Nur deine Ansicht ändert sich; der Verzauberungsglanz erscheint nur bei 100 %. In allen Versionen von 1.8.9 bis 26.3, zu Beginn aus.
- **Hub:** neues Icon für Hidden Armor.

## 0.3.66 (2026-11-17)

### Launcher
- **News nachgetragen:** Zu den Versionen 0.3.55 bis 0.3.65 gibt es jetzt Artikel mit Bildern in den News: „Launcher auf Englisch“ (0.3.55), „Neue Startseite“ (0.3.56 bis 0.3.58), „Neues Icon-Pack im Hub“ (0.3.59, 0.3.60) und „Performance-Engine“ (0.3.61 bis 0.3.65). Im Changelog tragen diese Versionen jetzt Titel und Bild.

## 0.3.65 (2026-11-17)

### Client
- **Schnelleres HUD:** Hintergrund, Rahmen und Akzentstreifen eines Widgets werden mit der Frame-Optimierung in einem Zeichenaufruf statt Rechteck für Rechteck gezeichnet, Bild unverändert. In der Messszene mit allen Widgets braucht das Lumora-HUD in 1.16.5 bis 1.21 etwa 60 bis 75 % weniger Zeit pro Frame, in 1.8.9 etwa 25 %.
- **Smart HUD & Menüs** gilt jetzt auch für die HUD-Widgets: Textbreiten, Beschriftungen und Panel-Maße werden nur neu berechnet, wenn sich der Inhalt ändert.
- **Namensschilder nach Unicode-Umschaltung:** Nach dem Umschalten auf „Unicode-Schrift erzwingen“ saßen Namensschild-Texte mit Icons bis zum nächsten Neuladen der Ressourcen leicht versetzt. Behoben.

## 0.3.64 (2026-11-16)

### Client
- **Performance für 1.12.2 und 1.8.9:** Entity Culling, optimiertes Rendering der Namensschilder und die Frame-Optimierung gibt es jetzt auch in den Legacy-Versionen. Damit hat die Performance-Engine alle 16 Minecraft-Versionen erreicht.
- **Wolken-Optimierung (1.12.2, 1.8.9):** Die schöne Wolkendarstellung wird nicht mehr in jedem Frame Stück für Stück neu gezeichnet, sondern aus einer zwischengespeicherten Display-List; sie wird nur bei Bedarf neu gebaut.

## 0.3.63 (2026-11-16)

### Client
- **Performance für 1.20.6 bis 1.16.5:** Entity Culling, optimiertes Rendering der Namensschilder und die Frame-Optimierung gibt es jetzt auch in 1.20.6, 1.20.4, 1.20.1, 1.19.4, 1.18.2, 1.17.1 und 1.16.5.
- **Wolken-Optimierung (1.20.6 bis 1.17.1):** Die Wolken werden bei Farbwechseln nicht mehr komplett neu gebaut (wie in 1.21). In 1.16.5 nicht unterstützt: Dort werden die Wolken mit der alten Fixed-Function-Pipeline gezeichnet.

## 0.3.62 (2026-11-16)

### Client
- **Performance für 1.21.x:** Entity Culling, optimiertes Rendering der Namensschilder und die Frame-Optimierung gibt es jetzt auch in 1.21.11, 1.21.8, 1.21.4 und 1.21.
- **Wolken-Optimierung (1.21):** Minecraft 1.21 baut die Wolken bei jeder Farbänderung neu (Sonnenauf- und -untergang, Regen). Mit der Optimierung wird nur noch die Farbe angepasst; die Wolken sehen gleich aus (Rundung höchstens eine von 255 Farbstufen). Ab 1.21.4 baut Minecraft die Wolken schon selbst nur bei Bedarf neu.

## 0.3.61 (2026-11-16)

### Client
- **Neues Modul „Performance“:** eine zentrale Performance-Engine mit einem Schalter pro Optimierung, alle standardmäßig an. „Status“ zeigt, was in der laufenden Minecraft-Version an, aus oder nicht verfügbar ist (mit Grund).
- **Entity Culling (26.3, 26.2, 26.1.2):** Entities und Block-Entities, die vollständig hinter undurchsichtigen Blöcken liegen, werden nicht gezeichnet. Spieler, sichtbare Namensschilder, leuchtende und geleinte Entities, Angelhaken, Leuchtfeuer-Strahlen und alles in der Nähe werden immer gezeichnet.
- **Optimiertes Rendering (26.x):** Namensschild-Icons und Lumora-Texte über Spielern werden nicht mehr in jedem Frame neu gebaut.
- **Smart HUD & Menüs (alle Versionen):** Das Hub baut seine Karten nur noch neu, wenn sich etwas daran geändert hat.
- **Frame-Optimierung (26.x):** Abgerundete Flächen und Buttons zeichnen dieselben Pixel mit weniger Aufwand.
- **Schnellerer Start (alle Versionen):** Der Lumora Loader liest die Spielklassen direkt aus den Jars (wirkt ab dem nächsten Start).
- **Wolken:** In 26.x nicht nötig, weil Minecraft die Wolken dort schon nur bei Bedarf neu baut. Ältere Versionen folgen.

## 0.3.60 (2026-11-16)

### Client
- **Baublase mit eigenem Icon:** Das Baublase-Addon zeigt im Hub jetzt das pinke „B“ mit Bausteinen und Blasen statt des Münzstapels.

## 0.3.59 (2026-11-16)

### Client
- **Neues Icon-Pack im Hub:** Alle Module und HUD-Widgets haben neue, selbst gezeichnete Pixel-Icons mit dunkler Kontur und mehreren Farbstufen. LumoraVoice zeigt ein Mikrofon, Spotify eine Musiknote und Baublase einen Münzstapel. Addons und Widgets anderer Autoren zeigen das Lumora-Logo.
- **Ein Sprite-Sheet statt Einzelbilder:** Die Icons liegen gemeinsam in `icons.png` in Lumora Core und gelten für alle Minecraft-Versionen. Bisher lagen 32 Einzelbilder in jedem der 16 Versionsordner.

## 0.3.58 (2026-11-15)

### Launcher
- **Drache auf der Startseite:** Rechts im Kopfbereich fliegt jetzt ein Drache vor den Bergen, der Mond steht dafür links.
- **Schmale Seitenleiste:** Die Seiten stehen links als Symbole mit dem Namen darunter. Die breite Leiste mit Text, der Umschalter unten und die Einstellung „Sidebar“ sind entfernt. Die Glocke heißt „News“.
- **Versionsmenü mit Update-Bildern:** Jede Version zeigt das offizielle Bild ihres Updates aus den Minecraft-Patchnotes, etwa die Axolotl bei 1.17. 1.12 und 1.8 haben dort keine Patchnotes und behalten ihr eigenes Pixel-Bild.
- **Neue Symbole:** gefüllte Symbole in der Seitenleiste, ein Kompass für „Discover“ und eine Flamme bei „Recently played“.

## 0.3.57 (2026-11-15)

### Launcher
- **Startseite:** Der Kopfbereich zeigt jetzt immer die verschneiten Berge im Mondlicht. Der wechselnde Hintergrund und die Punkte zum Umschalten sind entfernt.

## 0.3.56 (2026-11-15)

### Launcher
- **Neue Startseite:** Der Kopfbereich ist kompakter, darunter stehen die Karten „Recently played“, „Downloads“ und „News“ statt der System-Kacheln.
- **Wechselnde Hintergründe:** Fünf eigene Pixel-Szenen (Burg, Drache über der Burg, Berge im Mondlicht, schwebende Inseln, Leuchtturm) wechseln alle paar Sekunden; mit den Punkten unten rechts wählst du eine aus. Mit „Bewegung reduzieren“ bleibt das Bild stehen.
- **Recently played:** zeigt die zuletzt gespielte Version mit deinem neuesten Screenshot, wann und wie lange du gespielt hast. „Continue playing“ startet sie wieder, direkt in die Welt der letzten Sitzung, falls es eine gab. Über „…“ erreichst du Spielordner, Screenshots, die Dateiprüfung und die Einstellungen.
- **Downloads:** zeigt während einer Installation, was geladen wird und wie weit es ist, sonst ob die gewählte Version installiert ist und ob es Updates für Komponenten gibt.
- **News:** die zwei neuesten Meldungen von Lumora und Minecraft, ein Klick öffnet den Artikel.
- **Seitenleiste:** Die Glocke unten öffnet die News und zeigt mit einem roten Punkt, ob es ungelesene gibt. Die Bibliothek hat ein Puzzle-Symbol.

## 0.3.55 (2026-11-15)

### Launcher
- **Oberfläche auf Englisch:** Alle Texte des Launchers kommen jetzt aus einer Sprachdatei (`lumora/lang/en_us.json`) statt aus dem Code, auch Meldungen bei Anmeldung, Installation, Spielstart und Abstürzen. Weitere Sprachen lassen sich als eigene Datei ergänzen und fallen bei fehlenden Texten auf Englisch zurück.
- **Ein Tab-Stil:** Einstellungen, Konto und News nutzen dieselben Tabs.
- **Versionsauswahl als Dropdown:** In Bibliothek und Abstürze wählst du die Version jetzt aus einer Liste, statt Tabs, die rechts abgeschnitten waren.
- **„Open folder“** ist überall derselbe Knopf mit Ordner-Symbol.
- **Gleiche Höhen:** Tabs, Knöpfe, Eingabefelder und Auswahllisten sind gleich hoch, auch die Versionsknöpfe unter Einstellungen › Game und die Leiste auf der Speicher-Seite.
- **Pixel-Schrift:** In Titeln steht „and“ statt „&“, weil die Pixel-Schrift das Zeichen wie ein „$“ zeichnet.

## 0.3.54 (2026-11-15)

### Im Spiel
- **Off Hand:** Die Haupthand wird immer gezeichnet, der Schalter „Haupthand anzeigen“ ist entfernt. Schild und Totem in der Haupthand lassen sich weiter einzeln ausblenden.

## 0.3.53 (2026-11-15)

### Im Spiel
- **Off Hand: „Beide Hände anzeigen“** (anfangs an): zeigt in der Ego-Ansicht auch die leere Zweithand, nicht nur die Haupthand. Sie sitzt immer auf der anderen Seite als die Haupthand, die in Minecraft unter Optionen › Skin-Anpassung › Haupthand eingestellt ist (rechts oder links). Unsichtbar oder mit Fernrohr bleibt sie weg wie die Haupthand.

## 0.3.52 (2026-11-15)

### Im Spiel
- **Neues Modul „Off Hand“** (Spieler, anfangs aus, ab Minecraft 1.9): blendet in der Ego-Ansicht die Zweithand oder die Haupthand aus, oder nur ein Schild oder ein Totem darin, für jede Hand einzeln. Die Schalter für Schild und Totem erscheinen nur, solange die Hand selbst angezeigt wird. Andere Spieler sehen deine Hände weiter wie immer. In allen Versionen von 1.12.2 bis 26.3; in 1.8.9 gibt es keine Zweithand, dort ist das Modul gesperrt.

## 0.3.51 (2026-11-14)

### Im Spiel
- **Neues Modul „Light Block Overlay“** (Welt, anfangs aus): markiert Blöcke, auf denen feindliche Mobs spawnen können, rot wo sie sofort spawnen, gelb wo nur nachts. Darstellung als Kreuz, Zahl (Lichtstufe, auf Wunsch auch auf sicheren Blöcken), Fläche oder Markierung, Farbverlauf nach Himmelslicht, eigene Farben, horizontale und vertikale Reichweite, Lichtstufe (seit 1.18 Blocklicht 0, davor 7). Eine Taste blendet es ein und aus. Auf Leistung gebaut: Die Welt wird in ganzen Chunks gelesen, die nächsten zuerst, höchstens 1,5 ms pro Tick, und nur alle paar Ticks erneut (in der Nähe zweimal pro Sekunde); die Linien eines Chunks werden nur nach einem Scan oder einer Einstellungsänderung neu gebaut, ein Bild kopiert nur fertige Punkte. Ausgeblendet kostet es nichts. In allen Versionen von 1.8.9 bis 26.3.

## 0.3.50 (2026-11-14)

### Im Spiel
- **HUD-Editor wie bei LabyMod:** Beim Ziehen eines Widgets erscheint in der Bildschirmmitte ein Papierkorb; dort loslassen nimmt das Widget aus dem HUD. Auf dem Editor-Bildschirm liegen nur noch Widgets, die im HUD sind; alle anderen stehen in der Seitenleiste (mit +): ein Klick holt ein Widget an seine letzte Stelle zurück, aus der Liste ziehen legt es unter die Maus. Die Knöpfe „Verbergen“ und „An/Aus“ in der Widget-Karte sind durch „Entfernen“ ersetzt, Rechtsklick schaltet nichts mehr um, Entf nimmt das ausgewählte Widget heraus.
- In den Modul-Einstellungen im Hub gibt es den Schalter „Im HUD anzeigen“ nicht mehr: Ob ein Widget im HUD ist, entscheidet nur der HUD-Editor.

## 0.3.49 (2026-11-14)

### Im Spiel
- **Chat-Knöpfe in der Eingabezeile:** Der Emoji-Knopf (und die Knöpfe anderer Addons) sitzt jetzt rechts in der Chat-Eingabezeile statt darüber. Das Textfeld wird um die Knöpfe kürzer, damit langer Text nicht unter einem Knopf verschwindet. Die Emoji-Auswahl öffnet direkt über der Eingabezeile.

## 0.3.48 (2026-11-14)

### Im Spiel
- **Neues Modul „Freelook“** (Spieler, anfangs aus): Solange die Taste gehalten wird (Standard: linke Alt-Taste), schaltet die Kamera in die dritte Person und die Maus dreht nur die Kamera, ganz um den Spieler herum. Laufrichtung und Blick des Spielers bleiben, wie sie waren; beim Loslassen kommt die vorige Ansicht zurück. Einstellbar: Halten oder Umschalten, Start von hinten oder von vorne. F5 wechselt währenddessen nicht weg. In allen Versionen von 1.8.9 bis 26.3; in 1.8.9 und 1.12.2 folgen auch die sichtbaren Chunks und die Partikel der Kamera.

## 0.3.47 (2026-11-14)

### Im Spiel
- **Neues Modul „Emoji Chat“** (Server, anfangs an): `:smile:`, `:+1:`, `:heart:` … in Chatnachrichten werden zu Emojis (1870 Twemoji-Emojis mit den Namen von gemoji, ab Minecraft 1.16.5; auf 1.8.9 und 1.12.2 bleibt der Text). Ein Emoji-Knopf rechts über der Chat-Eingabe öffnet die Auswahl mit Kategorien, zuletzt benutzten Emojis und Mausrad; ein Klick fügt den Code ein. Beim Tippen von `:jo` schlägt der Chat passende Emojis vor, Tab übernimmt, die Pfeiltasten wählen. Andere Spieler sehen den gesendeten Text. Kopieren einer Nachricht gibt die Codes zurück.

### Lumora API 1.9.0
- **Knöpfe und Fenster an der Chat-Eingabe:** `Chat.addInputButton(ChatInputButton)` mit Icon oder Text, Tooltip und `ChatPanel` (eigenes Fenster über der Eingabe) oder Aktion – Lumoras Gegenstück zu LabyMods Chat-Eingabe-Knöpfen.
- **Icons in Chatnachrichten:** `Chat.addIcons(ChatIcons)` ersetzt `:name:` durch ein Zeichen einer Addon-Schrift (wie LabyMods `IconComponent`).
- `Chat.insertText(text)`, `Chat.input()`, `Chat.closePanel()`.
- Addon-Vorlage und Beispiel (Session Timer) zeigen einen Knopf an der Chat-Eingabe, der die Sitzungszeit in den Chat schreibt.

### Behoben
- Zugeschnittene Bereiche (Scissor) in Minecraft 1.8.9 bis 1.18.2 beachten jetzt Verschiebungen und lassen sich verschachteln, wie es die API zusagt; vorher konnten Addon-Fenster dort leer bleiben.

## 0.3.46 (2026-11-14)

### Im Spiel
- **Englische Modulnamen, auch auf Deutsch:** Crosshair, Attack Indicator, Block Overlay, Connection Screen, Hotbar & Status Bars, Elytra Trails, Tab List & Scoreboard, Widget Design, Button Design, Server & World List; Widgets wie Coordinates, Keystrokes, Clock, Speed, Server Address, Potion Effects, Item Counter, Memory Usage, Day Counter, Pack Display, Combo Counter, Block Info und Health/Armor/Hunger/Air/Experience Bar. Beschreibungen und Einstellungen bleiben in der gewählten Sprache.
- Namen und Beschreibungen der eingebauten Module stehen jetzt in den Sprachdateien (`lumora.module.<id>`), nicht mehr im Code.
- **Neues Modul „Armor HUD“** (Kampf, anfangs aus): Helm, Brustpanzer, Beinschutz, Stiefel und Item in der Hand sind je ein eigenes Widget und lassen sich im HUD-Editor einzeln verschieben. Haltbarkeit als Rest, Rest/Maximum oder Prozent, Farbe nach Haltbarkeit, leere Plätze ausblenden. Ersetzt das bisherige Widget „Rüstung“ mit allen Teilen in einem Kasten; wer es benutzt hat, schaltet das neue Modul einmal ein.

## 0.3.45 (2026-11-14)

### Launcher
- **Quick Play:** Unter „Spiel starten“ stehen die ersten fünf Server aus der Serverliste der gewählten Version (mit ihren Icons); ein Klick startet das Spiel direkt auf dem Server. Abschaltbar unter Erscheinungsbild.
- **Grafik-Backend** für Minecraft 26.2 und neuer: Standard (Minecraft entscheidet), Vulkan oder OpenGL; wird vor jedem Start gesetzt.
- **Update-Kanal** Stabil oder Vorab (Vorabversionen des Launchers, ebenfalls nur mit gültiger Signatur).
- **Ingame-Erlebnis anpassen:** Lumora-Design oder Minecraft-Stil für die Menüs im Spiel, gilt beim nächsten Start jeder Version.
- **Ausgewählten Patch merken:** pro Hauptversion die zuletzt gewählte Unterversion, auch nach einem Neustart.
- **Speicher:** Grenze des Download-Caches (Standard 8 GB) und „Spiel-Logs aufbewahren für“ (eigene Instanzen; `.minecraft` bleibt unverändert).
- **Fehlersuche:** Thread-Dump bei Hänger (Bericht unter Abstürze), Lumora-Client-Einstellungen zurücksetzen (mit Sicherung) und Instanzen von Festplatte wiederherstellen.

## 0.3.44 (2026-11-14)

### Launcher
- **Microsoft-Anmeldung ohne Einrichtung:** Lumoras eigene Anwendungs-ID ist fest eingebaut. Das Feld in den Einstellungen, der Einrichtungsdialog beim ersten Anmelden und die Umgebungsvariable `LUMORA_MS_CLIENT_ID` entfallen.
- **Discord Rich Presence immer an:** mit Lumoras eigener Discord-Anwendung; Schalter und ID-Feld entfallen. „Server in Discord zeigen“ bleibt abschaltbar (dann nur „Mehrspieler“).
- Alte Werte dieser Einstellungen in `launcher.json` werden ignoriert.

## 0.3.43 (2026-11-14)

### Launcher
- **Lumora-Pixel-Design:** Der Launcher sieht aus wie die Lumora-Menüs im Spiel: Pixelschrift (Silkscreen) für Titel, Navigation, Überschriften und den Start-Knopf, Karten und Knöpfe mit abgestuften Pixelecken, der gewählte Menüpunkt mit Akzentbalken wie im Hub, der Start-Knopf mit dunkler Unterkante wie ein Minecraft-Knopf. Fließtexte bleiben gut lesbar in Inter.
- Das Pixel-Design ist für alle eingeschaltet; Einstellungen › Erscheinungsbild › „Lumora-Pixel-Design“ schaltet zurück zum modernen Design mit runden Ecken.

### Im Spiel
- **Blau wie der Launcher:** Hub, Hauptmenü, Knöpfe, Schieberegler, Tabs, Modul-Icons, Menü-Effekte und der Burg-Hintergrund sind blau statt lila, in allen 16 Versionen.
- Standard-Akzentfarbe ist Blau. Lila bleibt unter Hub › Themes wählbar; wer bisher die alte Vorgabe Lila hatte, bekommt einmalig Blau.

### Für Addon-Entwickler
- Die Lumora-Farben `Colors.ACCENT`, `SURFACE`, `TEXT` … sind jetzt blau. Java setzt diese Konstanten beim Kompilieren ein: Addons zeigen die neuen Farben nach einem Neubau; für die Akzentfarbe des Spielers `context.theme().accent()` verwenden.

## 0.3.42 (2026-11-14)

### Launcher
- **Standard-Spielordner wie bei anderen Clients:** Alle Versionen spielen standardmäßig in `.minecraft` und teilen sich Welten, Ressourcenpakete, Screenshots und Optionen mit Vanilla-Minecraft.
- **Eigene Instanzen auf Wunsch:** Im Versionsmenü wählst du pro Version „Standard-Ordner“ oder „Eigene Instanz“ (`instances/<Version>`), dazu, ob Lumora-Einstellungen, HUD und Addons für alle Versionen gelten oder nur für diese. Beim Umschalten kommen die Einstellungen mit (nur kopiert, nie überschrieben).
- **Bestehende Installationen ändern sich nicht:** Vorhandene Versionsordner bleiben eigene Instanzen mit eigenen Einstellungen, der Datenordner bleibt, wo er war.
- Neue Installationen legen die Lumora-Daten in `.minecraft\LumoraClient` ab. Einstellungen › Allgemein: „Datenordner“ und neu „Standard-Spielordner“.
- Im Standard-Ordner installiert der Launcher keine Ressourcenpakete und ändert `options.txt` nicht von sich aus.

## 0.3.41 (2026-11-13)

### Im Spiel
- **Modul-Einstellungen aufgeräumt:** Alle eingebauten Module und Widgets haben jetzt dieselbe Einteilung: Vorlagen, Allgemein bzw. Anzeigen, Aussehen, Farben, Animation, Verhalten, Töne. Eine Farbe steht weiter direkt unter ihrem Schalter.
- Neue Abschnitte, wo bisher keine waren: Fadenkreuz, Zoom, Toggle Sprint und Fullbright („Allgemein“), Button-Design („Aussehen“), Server- & Weltliste („Menüs“).
- Umsortiert: Angriffsanzeige („Anzeigen“ zuerst), Block-Overlay (Farben und Verhalten getrennt), Widget-Design (Größe unter Aussehen, eigene Farben), Elytra-Streifen („Ab Tempo“ unter Verhalten), Chat (Erwähnungston unter „Töne“), Tastenanzeige (Vorlagen zuerst).
- Widgets wie FPS, Uhr oder Koordinaten zeigen ihre eigenen Optionen zuerst („Anzeigen“), danach Hintergrund, Akzent und Farben („Aussehen“). Deine Einstellungen bleiben erhalten.

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

