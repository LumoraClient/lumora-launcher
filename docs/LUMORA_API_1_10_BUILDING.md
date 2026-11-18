# Lumora API 1.10 – Building (Bauplan für das Addon „BuildingUtilities“)

Stand: 2026-11-18. Stufe 1 (API 1.10, Client 0.3.71, Addon 1.0.0) und Stufe 2 (API 1.11, Addon 1.1.0) umgesetzt, beide noch nicht im Spiel getestet. Gilt nur für die Clients **26.1.2, 26.2 und 26.3**; auf älteren Versionen liefert
`context.building()` eine Schnittstelle, deren `available()` `false` ist.

## Ziel

Ein eigenständiges Addon `lumora-building-utilities` (eigenes Repo wie BauBlase/LumoraVoice, Layout der
Addon-Vorlage) mit Werkzeugen, die **ohne Befehle und ohne OP-Rechte** auskommen. Alles ist eine normale Spielaktion,
die ein Spieler im Kreativmodus auch mit Maus, Tastatur und Kreativ-Inventar könnte; Lumora nimmt nur die Handgriffe
ab.

| Werkzeug | Stufe |
|---|---|
| Interaction Control (Taste G): funktionale Blöcke gezielt nicht benutzen, daneben ohne Schleichen platzieren | 1 |
| Block Picker (Mittelklick, mit Blockvarianten und Block-States) | 1 |
| Fast Place / Fast Break (nur Kreativ, kürzere Wartezeit zwischen Klicks) | 1 |
| Block Replacer: DEFAULT (anvisierter Block wird zum Block in der Hand) und REPLACE (mehrere Regeln Ziel → Ersatz) | 1 |
| Tinker (leere Hand): Block-States wie Drehung ändern, umgesetzt als Abbauen + Neusetzen mit dem geänderten Zustand | 2 |
| Color Picker: Farbe des anvisierten Blocks, ähnliche Blöcke (Concrete, Wool, Terracotta …), Toleranz, Vorschau, RGB/HSV/Hex | 2 |
| Hotbar-Pool: zufällig nach Gewicht aus der Hotbar platzieren, Übergang, Farbverlauf 1D/2D/3D mit Start-/Endpunkt | 2 |
| Item-Suche: Kreativ-Items suchen, eigene Items filtern, in die Hotbar legen | 2 |

**Entfernt** (bräuchten Befehle bzw. OP-Rechte): Build Reach, Force Place, Phantom/NoClip, Display-Entity-Editor,
Gizmo.

Die Bilder des Benutzers (Hotbar-Pool, Tooltips, Farbwähler) sind Vorlage für Bedienung und Aussehen. Axiom („All
Rights Reserved“) wurde nur auf sein Prinzip angesehen (Paketnamen, Texte); kein Code und kein Protokoll werden
übernommen.

## Regeln

- Alle Werkzeuge, die die Welt verändern, arbeiten nur im **Kreativmodus**. Wechselt der Spielmodus, schalten sie sich
  sofort ab. Der Color Picker zeigt Farben auch außerhalb des Kreativmodus an, legt dort aber nichts in die Hand.
- Lumora schickt nur die normalen Pakete (Abbauen, Platzieren, Hotbar-Slot, Kreativ-Inventar, Schleichen), nie
  schneller oder weiter, als der Server erlaubt. Lehnt der Server etwas ab, macht das Addon nichts weiter.
- Keine Befehle, keine Server-Plugins, kein Umgehen von Server-Prüfungen oder Anti-Cheat.
- Tinker-Einschränkung: Inhalte des Blocks (z. B. einer Kiste) gehen beim Neusetzen verloren; Blöcke mit Inhalt sind
  deshalb gesperrt. Zustände, die Minecraft nach dem Setzen neu berechnet (z. B. Zaun-Verbindungen), springen zurück.

## Neue API (`dev.lumora.api.building`, Berechtigung `building`, `@since 1.10.0`)

`AddonContext.building()` → `Building`:

Stand Stufe 1 (umgesetzt):

```java
public interface Building {
    boolean available();                       // false vor 26.x
    boolean creative();

    // Ziel
    Optional<BlockHit> target();               // Block unter dem Fadenkreuz: Position, Seite, Trefferpunkt, Zustand
    BlockInfo block(int x, int y, int z);      // id, Eigenschaften (aktuell + erlaubte Werte), interactive, container

    // Spielaktionen (nur Kreativ)
    int selectedSlot();
    void selectHotbar(int slot);
    ItemSpec hotbar(int slot);
    ItemSpec mainHand();
    boolean setCreativeSlot(int slot, ItemSpec item);  // auch Items mit Block-State (Komponente block_state)
    ItemSpec pick(BlockHit hit, boolean withState);
    boolean breakBlock(BlockHit hit);
    boolean place(BlockHit hit);               // Rechtsklick mit dem gewählten Item, ohne die UseHandler zu fragen
    void setUseDelay(int ticks);               // Fast Place (Minecraft: 4), -1 = Minecraft
    void setBreakDelay(int ticks);             // Fast Break (Minecraft: 5), -1 = Minecraft

    // Einhängen in die Steuerung (wird beim Ausschalten des Addons entfernt)
    Registration onUse(UseHandler handler);    // vor einem Rechtsklick auf einen Block: NORMAL / AS_SNEAKING / CANCEL
    Registration onPick(PickHandler handler);  // Mittelklick auf einen Block
}
```

Den Hotbar-Pool (Stufe 2) deckt `onUse` mit ab: der Handler wählt den Slot und antwortet `NORMAL`; Minecraft schickt
den neuen Slot vor dem Klick. Spielmoduswechsel prüft das Addon pro Tick über `creative()`.

Stufe 2 (API 1.11, umgesetzt):
- `Building#blockIds()`: alle Blöcke mit Item, sortiert.
- `Building#averageColor(blockId)`: mittlere Farbe der Partikel-Textur (Accessor `SpriteContentsAccessor` auf das
  Originalbild), Alpha = Anteil deckender Pixel; nach einem Ressourcen-Neuladen neu berechnet. Gefärbte Texturen
  (Gras, Laub) zählen ungefärbt.
- `Building#creativeItems(search, blocksOnly)`: alle aktivierten Items, Suche in Name und ID.
- `Building#highlight(x, y, z, argb)`: Umrandung eines Blocks in der Welt (`BuildingOverlayRender`, eingehängt in den
  Haken des Light Block Overlay), weg mit der Registrierung oder beim Ausschalten des Addons.
- `HudGraphics#item(ItemSpec, x, y)`: Item-Bild in Addon-Fenstern (`GuiGraphicsExtractor#fakeItem`); auf Versionen
  ohne Bauwerkzeuge zeichnet es nichts.

Abweichungen von den Vorlagebildern: Im Hotbar-Pool bestimmen die Gewichte im Verlauf den Anteil jedes Blocks an der
Strecke (statt verschiebbarer Griffe auf einer Leiste); die Item-Suche filtert „Nur Blöcke“ (eigene Items gibt es
ohne Server-Plugin im Kreativ-Inventar nicht).

Umsetzung je Version in `client/mc26.x/.../api/McBuilding.java` plus drei Mixins:
- `BuildingUseMixin` (`MultiPlayerGameMode`): `useItemOn` HEAD/RETURN fragt die Addons; bei `AS_SNEAKING` bekommt der
  Server vor dem Klick die Schleich-Eingabe (`ServerboundPlayerInputPacket`) und danach die echte zurück;
  `startDestroyBlock`/`continueDestroyBlock` RETURN kürzen `destroyDelay` im Kreativmodus.
- `BuildingClientMixin` (`Minecraft`): `pickBlockOrEntity` HEAD (Mittelklick), `startUseItem` RETURN kürzt
  `rightClickDelay` im Kreativmodus.
- `BuildingSecondaryUseMixin` (`Player#isSecondaryUseActive`): während eines Schleich-Klicks auch lokal „schleichend“.

## Addon-Aufbau

- `addons/lumora-building-utilities` (Repo `LumoraClient/BuildingUtilities`), Paket `dev.lumora.building`,
  `api 1.10.0`, Berechtigungen `building`, `keybinds`, `hud`, `screens`.
- Jedes Werkzeug ist ein eigenes Modul mit Schalter, Taste und eigenem Abschnitt in der Einstellungskarte, Zustand im
  Addon-Speicher.
- Keine Hintergrund-Schleifen: Arbeit nur bei Eingaben oder pro Tick, solange ein Werkzeug aktiv ist.
- HUD-Widget „Bauwerkzeuge“ zeigt aktive Werkzeuge (z. B. „Interaction Control“, „Replacer: REPLACE“).
- Texte nur in den Sprachdateien (de_de, en_us).

## Stufen

1. **Stufe 1:** API 1.10 (Grundgerüst, Ziel, Spielaktionen, Benutzen-/Platzier-/Pick-Hooks, Verzögerungen) in
   26.1.2/26.2/26.3; Addon mit Interaction Control, Block Picker, Fast Place/Break, Block Replacer; Tests;
   Client-Release + Addon-Release.
2. **Stufe 2:** Tinker, Color Picker, Hotbar-Pool mit Verlauf, Item-Suche, Welt-Overlay.
