# HUD-Editor

Öffnen: Lumora-Hub (Rechte Umschalttaste) → **Module** → **HUD bearbeiten**, oder aus Addons über
`LumoraAPI.get().hudEditor().open()` (LabyVoice: Einstellungen → Anzeige → „Position und Größe“).

Der Editor zeigt alle registrierten Widgets – eingebaute und aus Addons – genau so, wie sie im Spiel aussehen.
Ohne Welt (Hauptmenü) zeigen sie Beispieldaten. Ausgeschaltete, verborgene, fehlerhafte oder gerade nicht
sichtbare Widgets erscheinen als beschrifteter Platzhalter.

## Bedienung

| Aktion | Wirkung |
|---|---|
| Klick | Widget auswählen (Rahmen und vier Eckgriffe). Ein Klick allein verschiebt nichts (erst ab 2 px Bewegung). |
| Ziehen | verschieben; rastet an Bildschirmrändern, der Mitte und an Kanten/Mitten anderer Widgets ein (Hilfslinie leuchtet) |
| **Shift** + Ziehen | frei, ohne Einrasten und Raster |
| Eckgriff ziehen | Größe ändern; die gegenüberliegende Ecke bleibt stehen (Schritte von 5 %) |
| Mausrad | Größe in 10-%-Schritten um die Mitte |
| Rechtsklick | Widget an/aus |
| **Alt** + Klick | nächstes Widget darunter (bei überlappenden Widgets) |
| Doppelklick | Optionen des Widgets |
| Pfeiltasten | 1 px verschieben (**Shift**: 10 px) |
| Entf / H | an/aus / verbergen-zeigen des ausgewählten Widgets |
| G | Raster an/aus |
| Strg+Z / Strg+Y (Strg+Shift+Z) | rückgängig / wiederholen (50 Schritte; eine Mausrad- oder Pfeiltasten-Folge zählt als ein Schritt) |
| Esc / Fertig | übernehmen und schließen |

Seitenleiste (☰ blendet sie ein/aus): Profil wechseln (Klick) bzw. neues Profil (**+**), Raster, Einrasten,
Rückgängig/Wiederholen, Widget-Liste nach Kategorie (Klick wählt aus, zweiter Klick schaltet an/aus; ● an, ○ aus,
◌ verborgen, ⚠ Fehler), **Alle zurück** (Standardpositionen), **Verwerfen** (alle Änderungen seit dem Öffnen
zurücknehmen, auch neue Profile) und **Fertig**.

Karte des ausgewählten Widgets (unten links): Name, Größe in %, Ebene, **Optionen**, **Verbergen/Zeigen**, **An/Aus**,
**Nach vorn/Nach hinten** (Zeichenreihenfolge), **Standard** (Position, Größe, Ebene und Einstellungen zurücksetzen).

## Einstellungen eines Widgets

**Optionen** öffnet einen Bildschirm, den Lumora aus den Einstellungsbeschreibungen des Widgets erzeugt: Schalter,
Zahlen mit −/+, Schieberegler, Textfelder, Auswahl, Farbe (Hex-Eingabe, Vorschaufläche, Palette ◐) und Tasten
(klicken, Taste drücken; Entf/Rücktaste = keine, Esc = abbrechen). „Erweitert“ zeigt seltene Optionen, „Standard“
setzt alle zurück. Werte werden beim Eingeben geprüft. Addon-Einstellungen und Lumora-Keybinds eines Addons stehen im
Hub unter **Addons** → ⚙.

## Profile

Fest: **Standard**, **PvP**, **Survival**, **Minimal**; dazu eigene (`custom-1`, …). Ein neues Profil startet als Kopie
des aktuellen. Jedes Profil speichert für jedes Widget an/aus, gezeigt, Position, Größe, Ebene und die eigenen
Einstellungen. Wechsel im Editor oder im Hub („Profil: …“). Die Rückgängig-Liste gilt je Profil.

## Speicherung

`.minecraft/config/lumora/hud-layout.json` (Schema 2):

```json
{
  "schema": 2,
  "activeProfile": "default",
  "grid": false,
  "gridSize": 8,
  "snap": true,
  "profiles": {
    "default": {
      "widgets": {
        "lumora:fps": { "enabled": true, "shown": true, "x": 0.005, "y": 0.01, "scale": 1.0, "layer": 0,
                        "settings": { "background": "true" } },
        "labyvoice:voice": { "enabled": true, "shown": true, "x": 0.015, "y": 0.985, "scale": 1.0, "layer": 0 }
      }
    }
  }
}
```

- Geschrieben wird 2 s nach der letzten Änderung, beim Schließen des Editors und beim Beenden – nie pro Frame. Das
  Schreiben passiert atomar (temporäre Datei, dann Umbenennen) im Hintergrund-Thread.
- Robust: jeder Eintrag wird einzeln gelesen. Ein kaputter Eintrag wird übersprungen, ein ungültiger Wert fällt auf
  den Standard zurück, fehlende Felder bekommen Standardwerte. Ist die Datei gar kein JSON, wird sie als
  `hud-layout.json.corrupt-<zeit>` gesichert und es gelten die Standardpositionen.
- Einträge von Widgets, deren Addon gerade fehlt oder aus ist, bleiben erhalten und gelten wieder, sobald es zurück ist.
- Doppelte Einträge sind durch das Format ausgeschlossen (eine ID je Profil).
- **Migration**: Gibt es noch die alte `config/lumora-hud.json` (Schema 1, Lumora vor der API), werden ihre Module
  einmalig als `lumora:<modul>` übernommen, inklusive Hintergrund/Akzent. Die alte Datei bleibt unverändert liegen.
- Schreibt eine neuere Lumora-Version ein höheres Schema, werden unbekannte Felder ignoriert.

Gebundene Einstellungen (z. B. die LabyVoice-Optionen) stehen nicht in dieser Datei, sondern bei ihrem Besitzer.
