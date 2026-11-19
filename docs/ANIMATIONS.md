# Modul „Animationen“ (lumora-animations)

Klassische Animationen aus Minecraft 1.7 und 1.8 oder die modernen, jeder Teil einzeln einstellbar. Ab Minecraft
1.9 (vorher gibt es keinen Unterschied zu 1.8). Rein optisch: Es ändert sich nur, wie dieser Client zeichnet, nie
was an den Server geht, Hitboxen oder Spielmechanik.

## Quellen

Die Legacy-Optionen bauen die Original-Formeln nach, keine Annäherungen:

| Version | Quelle |
| --- | --- |
| 1.7.10 | offizielle Client-Jar von Mojang (piston-meta, SHA-1 `e80d9b3bf5085002218d4be59e668bac718abbc6` geprüft), nur lokal mit Vineflower dekompiliert und gelesen; nichts davon wird mitgeliefert |
| 1.8.9 | dekompilierte Quellen der Build-Umgebung (MCP-Namen) |
| modern | dekompilierte Quellen von 26.1.2, für jede Zielversion deren eigene Klassen |

## Einstellungen

Jede Funktion hat eine eigene Auswahl. **Default** lässt die Minecraft-Version unverändert. Angeboten werden nur
Optionen, die es im Originalcode wirklich gibt:

- Verhalten sich 1.7 und 1.8 gleich, gibt es eine Option **Legacy (1.7/1.8)**.
- Verhält sich 1.8 schon wie heute, gibt es keine eigene 1.8-Option.

**Presets:** Classic 1.7, Classic 1.8, Modern und Custom. Ein Preset nimmt pro Funktion 1.7 bzw. 1.8, sonst die
gemeinsame Legacy-Option, sonst Modern. Eine Änderung von Hand stellt das Preset auf Custom. Alles wird in der
Modul-Konfiguration gespeichert und wirkt sofort.

**Schlag-Stil** (Vanilla, Smooth, Spin, Slap): früher auf der Themes-Seite, jetzt im Modul; der alte Wert wird beim
ersten Start übernommen. Er gilt nur, solange die Item-Haltung nicht auf Legacy steht, weil die Legacy-Haltung den
Original-Schlag von 1.7/1.8 mitbringt.

## Original-Verhalten je Funktion

| Funktion | 1.7.10 | 1.8.9 | modern (26.x) | Optionen |
| --- | --- | --- | --- | --- |
| Schleichen | Kamera sofort 0,08 tiefer (`ySize` 0,2 × 0,4), beim Loslassen weich hoch; Modell: Körper 0,5 rad, Beine y 9 / z 4, Kopf y 1 | Augenhöhe 1,62 − 0,08, ohne Übergang; Modell wie 1.7, beim Zeichnen 0,2 nach unten | Augenhöhe gleitet auf 1,27 (Kamera mit 0,5-Glättung), Haltung `HumanoidModel` crouching | 1.7, 1.8, Modern |
| Herzen | blinken, solange die Unverwundbarkeit ≥ 10 Ticks ist (`hurtResistantTime / 3 % 2`) | eigener Zähler: 20 Ticks bei Schaden, 10 bei Heilung | wie 1.8 | 1.7, Modern (= 1.8) |
| Block bauen | kein Absenken beim Platzieren (gleiches Item und gleicher Schaden werden sofort getauscht) | Absenken, sobald sich der Stapel ändert (Anzahl, also nicht im Kreativmodus) | Absenken bei jeder Platzierung, im Kreativmodus auch | 1.7, 1.8, Modern |
| Rückwärtslaufen | Körper dreht zur Laufrichtung (Ziel = Bewegungsrichtung) | wie 1.7 | Körper bleibt vorn (95°–265° → Richtung − 180°) | Legacy (1.7/1.8), Modern |
| Kopfdrehung | Kopf bis 75° zum Körper, ab 50° zieht der Körper mit 0,2 nach | wie 1.7 | höchstens 50° | Legacy (1.7/1.8), Modern |
| Schwert / Blocken | Blockhaltung, der Schlag läuft beim Blocken weiter (Blockhitting) | Blockhaltung ohne Schlag | Schwerter blocken nur mit Komponente / Schild | 1.7, 1.8, Modern |
| Item-Haltung (1. Person) | 0,7·0,8 / −0,65·0,8 / −0,9·0,8, 45°, Schlag, ×0,4, flaches Item 1,5×, 50°, 335° | `transformFirstPersonItem` + Modell-Anzeige „firstperson“ | `firstperson_righthand` | 1.7, 1.8, Modern |
| Bogen | −18° / −12° / −8°, (−0,9 / 0,2), Spannen z 0,1, Skalierung z 1 + 0,2·Kraft | wie 1.7 mit 1.8-Haltung | eigene Bogenhaltung | 1.7, 1.8, Modern |
| Essen / Trinken | 1 − t²⁷, Wackeln bis 80 % | gleiche Formel, 1.8-Haltung | gleiche Formel, moderne Haltung | 1.7, 1.8, Modern |
| Angel | um 180° gedreht (`shouldRotateAroundWhenRendering`) | Modell-Anzeige der Angel | `handheld_rod` | 1.7, 1.8, Modern |
| Item-Wechsel | Absenken nur bei anderem Slot oder anderem Item/Schaden | Absenken bei jedem anderen Stapel | Absenken auch beim Angriffs-Cooldown | 1.7, 1.8, Modern |
| Item-Haltung (3. Person) | Blockhaltung ohne Drehung | Blockhaltung mit −30° | moderne Haltung | 1.7, 1.8, Modern |
| Rezeptbuch entfernen | – | – | – | an / aus |
| Creative-Layout | – | Tabs von 1.8 | moderne Tabs | 1.8, Modern |

## Umsetzung

- `lumora-core`: `AnimationsModule` (Einstellungen, Presets, Abfragen). Die Mixins lesen den Stil mit einem
  Array-Zugriff, gerechnet wird nur an den Stellen, die ohnehin gezeichnet werden.
- je Version: Mixins an den Stellen, an denen Minecraft die jeweilige Animation rechnet; die Formeln mit der
  PoseStack-API der Version.

## Stand

Wird mit jedem Schritt ergänzt.

| Teil | 26.1.2 / 26.2 / 26.3 | 1.12.2 – 1.21.11 |
| --- | --- | --- |
| Kernmodul (Einstellungen, Presets, Custom, Speichern) | fertig | fertig (versionsunabhängig) |
| Schlag-Stil aus Themes ins Modul | fertig | fertig |
| Rückwärtslaufen, Kopfdrehung (`AnimationsBodyMixin`, `BodyTurn`) | fertig | offen |
| Item wechseln, Block bauen (`AnimationsEquipMixin`, `AnimationsBuildMixin`, `HandEquip`) | fertig | offen |
| Schleichen (`AnimationsCameraMixin`, `AnimationsSneakModelMixin`, `SneakCamera`) | fertig | offen |
| Herzen (`AnimationsHeartMixin`, `HeartBlink`; nur Minecrafts Herzen, Lumoras eigene blinken nicht) | fertig | offen |
| Schwert, Item-Haltung, Bogen, Essen, Angel (1. Person; `AnimationsHandPrepareMixin`/`AnimationsHandDrawMixin`, `LegacyHands`, `ItemPose`) | fertig | offen |
| Item-Haltung (3. Person; `AnimationsArmedStateMixin`, `AnimationsThirdPersonMixin`, `AnimationsArmPoseMixin`, `AnimationsAvatarPoseMixin`) | fertig | offen |
| Rezeptbuch entfernen (`AnimationsRecipeBookMixin`, `AnimationsRecipeBookHiddenMixin`) | fertig | offen |
| Creative-Layout, Tabs von 1.8 (`AnimationsCreativeTabMixin`, `LegacyCreative`, `CreativeTabs18`, Tabelle `creative_1_8.tsv`) | fertig | offen |

Umsetzungsdetails:

- **Item-Haltung:** nur die Haupthand (1.7 und 1.8 haben keine Nebenhand). Items mit eigener moderner Animation
  (Armbrust, Schild, Dreizack, Fernrohr, Karten, Speere …) bleiben bei Minecraft. Blockt ein Spieler mit Schild in der
  Nebenhand und hält ein Schwert, zeigt Legacy das Schwert in Blockhaltung, wie 1.7/1.8 (der Schild lässt sich mit
  „Off Hand“ ausblenden).
- **1.8-Anzeige-Werte** aller 1.8.9-Itemmodelle ausgewertet: flache Items, Werkzeuge, Bogen, Angel, Lohenrute, Blöcke,
  Teppiche, Falltüren, Zauntore, Zäune, Knöpfe, Ambosse, Banner, Köpfe und die Items mit leicht anderer
  Dritte-Person-Lage (Helme, Stiefel, Samen …).
- **Blockhaltung 3. Person:** 26.x lässt den Arm dem Kopf folgen; 1.8 setzt −30°, 1.7 dreht nicht.
- **Creative-Tabs 1.8:** Belegung aus 1.8.9 selbst ausgelesen (`CreativeTabs.displayAllReleventItems`, 624 Einträge) und mit
  Minecrafts eigenem DataFixer (Datenversion 99 → 26.3) in heutige IDs übersetzt, samt Tränken, Spawn-Eiern und
  Verzauberungsbüchern. Minecrafts Tab-Plätze zeigen die 1.8-Tabs an denselben Stellen wie 1.8; Hotbar- und
  Operator-Tab gab es in 1.8 nicht und werden ausgeblendet. Neuere Items kommen nach den 1.8-Items in den passenden
  1.8-Tab, bei neuen Verzauberungen nur die höchste Stufe, wie 1.8 seine Bücher zeigte.
- **Schleichen:** unter niedrigen Decken (Aufstehen unmöglich) bleibt die moderne Kamera, sonst steckte sie in den
  Blöcken darüber.

Erkenntnisse für die offenen Teile:

- **Item-Haltung 1. Person:** 26.3 bereitet die Hand-Items in `FirstPersonHandsAndItems.extractRenderState` mit
  `FIRST_PERSON_RIGHT_HAND` vor; für Legacy wird dort `ItemDisplayContext.NONE` genommen und die 1.7- bzw.
  1.8-Kette samt Anzeige-Werten (1.8: Drehung in der Reihenfolge Y, X, Z) vor dem Zeichnen angewendet. 1.7 zeichnet
  flache Items gespiegelt (`renderItemIn2D` mit vertauschten U-Koordinaten), Blöcke ohne weitere Anzeige-Werte.
- **Schleichen, Modell:** Die moderne Haltung ist die von 1.8 plus dem Versatz 0,2, den 1.8 beim Zeichnen anwendet.
  Unterschied nur beim eigenen Spieler in der dritten Person: modern −0,125 (`getRenderOffset`), 1.8 kein Versatz,
  1.7 der `ySize`-Versatz; andere Spieler sind in 1.7, 1.8 und modern um 0,125 abgesenkt.
