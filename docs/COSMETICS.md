# Cosmetics & Emotes

Eingebautes Modul `lumora-cosmetics`: Cosmetics am Spielermodell (Flügel, Umhänge, Hüte, Accessoires, Effekte,
Begleiter) und Emotes (Animationen des ganzen Körpers, ohne Item in der Hand), mit Menü, Emote-Menü, Emote-Rad,
Tasten und einer Live-Vorschau. Alle 16 Versionen von 1.8.9 bis 26.3.

**Andere Spieler (seit 0.3.96):** Andere Lumora-Spieler sehen deine Cosmetics und Emotes, wenn ihr mit demselben
Lumora-Social-Server verbunden seid (Adresse im Launcher unter „Freunde“, siehe [SOCIAL.md](SOCIAL.md)). Einstellung
„Sichtbar für“: Alle (Standard), Freunde, Niemand. „Cosmetics anderer Spieler zeigen“ schaltet die Anzeige der anderen
ab. Spieler ohne Lumora oder auf einem anderen Social-Server haben keine Daten und werden genau so gezeichnet wie von
Minecraft. Die Vorschau im Menü ist immer nur lokal.

## Bedienung

| Was | Wo |
|---|---|
| Cosmetics-Menü | Taste **J**, Hub → Addons → Cosmetics & Emotes → „Cosmetics öffnen“ |
| Emote-Rad | Taste **R** (nur in einer Welt) **halten**, auf ein Emote zeigen und loslassen. Kurz tippen lässt das Rad offen: dann Segment anklicken oder Ziffern 1–0. Mausrad, Pfeiltasten oder die Knöpfe blättern die Seiten. Die Mitte beendet ein laufendes Emote, Rechtsklick oder Esc schließt |
| Emote-Menü | Reiter „Emotes“ im Menü, eigene Taste (frei belegbar) |
| Emote beenden | eigene Taste (frei belegbar), Mitte des Rads, „Emote beenden“ im Emote-Menü, Laufen bei Ganzkörper-Emotes |
| Emote auf Platz 1–4 | vier frei belegbare Tasten |

Im Menü: Kategorien, Suche, Filter nach Seltenheit, Favoriten (Stern oder Rechtsklick), „Angelegt“. Ein Klick auf
eine Karte probiert das Cosmetic in der Vorschau an, ohne es anzulegen. Mehrere Klicks ergeben eine Kombination,
die „Übernehmen“ anlegt und „Zurücksetzen“ verwirft. Die Vorschau dreht sich beim Ziehen um 360° und zoomt mit dem
Mausrad. Sie zeigt den eigenen Spieler und gibt es daher nur in einer Welt. Das Emote-Menü spielt das gewählte Emote
in der Vorschau in einer Schleife. Darunter liegen die Plätze des Emote-Rads (4–10 pro Seite, 1–8 Seiten, Einstellungen): erst ein Emote wählen,
dann einen Platz anklicken. Ein Rechtsklick leert einen Platz. „Bearbeiten“ öffnet das Rad mit allen Seiten: ein Platz
öffnet ein Fenster mit kleinem Rad und einer durchsuchbaren Liste aller Emotes zum Tauschen.

**Variante und Versatz:** Beim gewählten Cosmetic stehen unter der Vorschau die Variante als Ausklappliste
(Farbe, z. B. Schwarz, Beige, Oliv; das Mausrad über dem Feld blättert weiter) und der Versatz X/Y/Z in Pixeln (±8, halbe Pixel; Rechtsklick setzt auf 0). Beides gilt sofort für
Vorschau, angelegtes Cosmetic und für andere Spieler.

Einstellungen: eigene Cosmetics zeigen, Cosmetics anderer Spieler zeigen, Sichtbar für, animierte Cosmetics, Emote beim Laufen beenden, Kamera beim Emote nach
hinten (dritte Person, danach zurück), Plätze im Emote-Rad. Gespeichert in `config/lumora/cosmetics.json`.

## Technik

- **GeckoLib:** GeckoLib ist eine Fabric-, NeoForge- und Forge-Mod mit Minecraft-Klassen im Kern und eigenen Branches
  pro Version, aber ohne 1.8.9. Der Lumora Loader lädt keine Fabric-Mods. Lumora nutzt deshalb GeckoLibs
  **Dateiformat** (`.geo.json`, `.animation.json` aus Blockbench „GeckoLib Animated Model“ bzw. „Bedrock“) und
  übernimmt die Minecraft-freien Teile unter MIT mit Quellenangabe: Easing-Kurven, Keyframe-Interpolation (Pre/Post,
  Catmull-Rom) und das Backen von Modellen (Bone-Reihenfolge, Box- und Face-UVs). Siehe `THIRD_PARTY_NOTICES.md`.
- **Kern** (`lumora-core`, `dev.lumora.core.cosmetics`), versionsunabhängig und mit Unit-Tests:
  - `anim/`: Molang, `Track`, `Animation`, `AnimationParser`, `AnimationController` (Übergänge, mehrere Controller
    gleichzeitig, additive Overlays, Events), `PoseBuffer`;
  - `model/`: `GeoModel`, `MeshBuilder`, `CosmeticMesh`;
  - `CosmeticRegistry` (eingebaute und eigene Inhalte), `CosmeticsState` (Speicherstand), `Cosmetics` (Laufzeit pro
    gezeichnetem Spieler), `EmotePose`, `PlayerFrame`, `CosmeticsModule`;
  - `ui/`: Menü, Emote-Menü, Rad, Vorschau.
- **Pro Version** nur ein kleiner Adapter (`render/CosmeticsRender`, `render/CosmeticsLayer`, Mixins `Cosmetics*`,
  `McExtras.drawPlayerPreview`/`playSoundEvent`):
  - 26.x und 1.21.11 über den Render-State und `submitCustomGeometry`;
  - 1.21.4 und 1.21.8 über den Render-State und `MultiBufferSource`;
  - 1.16.5 bis 1.21 direkt in `PlayerRenderer.render`;
  - 1.8.9 und 1.12.2 als GL-Feature mit Tessellator.

  Generatoren: `.t/cosmetics-port.pl` (26.2, 26.1.2, 1.21.11) und `.t/cosmetics-port-b.pl` (1.16.5–1.21).
- **Animationen und Minecraft:** Ein Emote überschreibt nur die Teile, die es animiert, und blendet weich ein und aus.
  Alle anderen Teile behalten die Bewegung von Minecraft: Beim Winken laufen die Beine weiter. `root` bewegt den
  ganzen Spieler (sitzen, liegen, drehen), nur im Bild. Hitbox, Bewegung und Server bleiben unberührt. Cosmetics
  hängen an den Modellteilen und folgen damit auch Emotes. In der ersten Person werden sie nicht gezeichnet (wie das
  Spielermodell).
- **Leistung:** Gerechnet wird nur für Spieler, die gerade gezeichnet werden, und nur im Frame, in dem sie gezeichnet
  werden. Puffer werden wiederverwendet. Zustände von Spielern, die 10 s nicht gezeichnet wurden, fallen weg, ebenso
  alle beim Verlassen der Welt. Ohne Daten kostet ein Spieler einen Map-Zugriff.
- **Werkzeuge:** `.t/cosmetics/CosmeticArt.java` erzeugt das Start-Set (Modelle mit Box-UV, Pixel-Texturen,
  Animationen, `index.json`). Jede Fläche bekommt ein Muster (Strick, Leder, Metall, Flammen …) oder einen
  eigenen Maler, leichtes Licht und einen dunkleren Rand. Platten mit Silhouette (Federn, Membranen, Ohren) haben
  Löcher, die der Cutout-Render weglässt. Varianten färben die Hauptfarbe um. `.t/cosmetics/MeshRender.java` und `EmoteRender.java` zeichnen Cosmetics und
  Emote-Posen mit dem echten Kern-Code als PNG, zum Prüfen ohne Spiel.

## Eigene Cosmetics

Ordner `config/lumora/cosmetics/<name>/` (Minecraft-Ordner der Instanz):

| Datei | Inhalt |
|---|---|
| `*.geo.json` | Modell aus Blockbench (Format „Bedrock Entity“ oder „GeckoLib Animated Model“, 1.12.0 oder alt 1.8.0) |
| `*.png` | Textur, höchstens 512 × 512 |
| `*.animation.json` | optional: Animationen |
| `cosmetic.json` | optional: Angaben (alle Felder optional) |

```json
{
  "name": "Bommelmütze",
  "description": "Warm und weich",
  "category": "hats",
  "slot": "head_top",
  "rarity": "rare",
  "attach": "head",
  "render": "cutout",
  "previewYaw": 20,
  "animations": {"idle": "animation.muetze.wackeln", "walk": "animation.muetze.laufen"},
  "overlay": "animation.muetze.glitzern"
}
```

- **Modell:** im normalen Spieler-Rig bauen (Kopf-Pivot `[0, 24, 0]`, Arme `[±5, 22, 0]`, Beine `[±1.9, 12, 0]`).
  Oberste Bones mit dem Namen eines Körperteils (`head`, `body`, `right_arm`, `left_arm`, `right_leg`, `left_leg`,
  auch `bipedHead`, `armorBody` …) hängen an diesem Teil. Alle anderen hängen an `attach` (Standard `root`: dreht
  nur mit dem Körper).
- **category:** `wings`, `capes`, `hats`, `accessories`, `effects`, `companions`. **slot:** ein Cosmetic pro Platz
  (z. B. zwei Hüte mit `head_top` schließen sich aus). **rarity:** `common` … `legendary`. **render:** `cutout`,
  `translucent`, `emissive` (leuchtet).
- **Animationen je Zustand:** `idle`, `walk`, `run`, `sneak`, `jump`, `fall`, `air`, `fly` (Elytra), `swim`, `emote`
  (während der eigene Spieler ein Emote spielt). Fehlt einer, gilt die Kette `run → walk → idle`,
  `jump/fall/fly → air → idle`. `overlay` läuft immer zusätzlich (additiv). Ohne `cosmetic.json` und mit nur einer
  Animationsdatei spielt deren erste Animation immer.

## Eigene Emotes

Jede Animation in `config/lumora/emotes/*.animation.json` wird ein Emote (Kategorie „Spezial“). Bones:

| Bone | bewegt |
|---|---|
| `head`, `right_arm`, `left_arm`, `right_leg`, `left_leg` | den Körperteil (auch `rightArm`, `bipedHead` …) |
| `body` (auch `torso`, `waist`) | den Oberkörper: knickt in der Hüfte ab und nimmt Kopf und Arme mit, wie die Taille im Bedrock-Rig |
| `root` | den ganzen Spieler, Drehpunkt an den Füßen |

Drehungen in Grad wie in Blockbench, Positionen in Pixeln. `loop`: `true`, `false` (spielt einmal) oder
`"hold_on_last_frame"` (bleibt stehen, z. B. Sitzen). Emotes, die Beine oder `root` bewegen, enden beim Laufen.

## Molang

Zahlen, `+ - * / %`, Vergleiche, `&& || !`, `?:`, `??`, Klammern, `math.*` (`sin`/`cos` in Grad, `abs`, `clamp`,
`lerp`, `lerprotate`, `min`, `max`, `pow`, `sqrt`, `floor`, `ceil`, `round`, `mod`, `random` …), Variablen
`v.name = …;` und diese Abfragen: `query.anim_time`, `life_time`, `delta_time`, `ground_speed` (Blöcke/s),
`vertical_speed`, `is_on_ground`, `is_sneaking`, `is_sprinting`, `is_swimming`, `is_gliding`, `is_moving`,
`head_x_rotation`, `head_y_rotation`, `body_y_rotation`, `limb_swing`, `limb_swing_amount`, `is_first_person`.
Unbekannte Abfragen und Funktionen sind 0 (wie bei GeckoLib).

Sound-Keyframes (`sound_effects`) spielen beim eigenen Emote einen Minecraft-Sound (`minecraft:…` oder `clap`,
`pop`, `whoosh`), nur für dich und nicht auf 1.8.9 und 1.12.2. Partikel-Keyframes werden gelesen, aber noch nicht
gezeichnet.

## Synchronisierung

Über die WebSocket-Verbindung von Lumora Social (Anmeldung über Mojang, siehe [SOCIAL.md](SOCIAL.md)):

| Nachricht | Inhalt |
|---|---|
| `cosmetics.set` | eigene Ausrüstung: bis zu 16 Einträge `{id, variant?, offset?: [x, y, z]}` und `scope` (`all`, `friends`, `none`). Nur eingebaute Cosmetics, eigene Dateien bleiben lokal. Nach jeder Anmeldung erneut gesendet. Der Server hält sie nur, solange die Verbindung steht. |
| `cosmetics.watch` | die Spieler in der Nähe (bis zu 128 UUIDs, nur wenn sich die Liste ändert, höchstens alle 2 s). Antwort: deren sichtbare Ausrüstung. Spätere Änderungen schiebt der Server als `cosmetics` `{uuid, loadout}`. |
| `emote.play` / `emote.stop` | Start oder Ende des eigenen Emotes an die Spieler im Umkreis von 48 Blöcken (bis zu 64). Der Server leitet als `emote` `{uuid, id}` an deren Spielsitzungen weiter, die es sehen dürfen, und speichert nichts. |

Der Server prüft und kürzt alles (`Rules.cosmetics`: gültige IDs, Varianten, Versatz ±8 in halben Pixeln) und
begrenzt die Rate (30 Änderungen, 60 Abfragen und 40 Emotes pro Minute). Unbekannte IDs zeichnet der Empfänger
einfach nicht. Ältere Clients ignorieren die neuen Nachrichten.
