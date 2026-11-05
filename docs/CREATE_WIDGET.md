# Ein HUD-Widget erstellen

Ein Widget erweitert `dev.lumora.api.hud.HudWidget` und wird in `onEnable` des Addons registriert:

```java
context.widgets().register(new SessionTimerWidget(context.game()));
```

Ab dann steht es automatisch im HUD-Editor (Liste nach Kategorie, Auswahl, Verschieben, Größe, Ebene,
Ein-/Ausblenden, Einstellungen, Profile) und im Lumora-Hub unter „Module“. Der Editor muss dafür nicht angepasst
werden. Die globale ID ist `<addon-id>:<widget-id>`.

## Minimalbeispiel

Kompiliert gegen `lumora-api` (aus `examples/session-timer-addon`, gekürzt):

```java
public final class SessionTimerWidget extends HudWidget {
    private final GameContext game;
    private TextSetting label;
    private ChoiceSetting format;
    private BooleanSetting background;
    private ColorSetting color;
    private long startedAt = -1;
    private String text = "00:12:34";

    public SessionTimerWidget(GameContext game) {
        super("timer");
        this.game = game;
    }

    @Override
    public String category() {
        return WidgetCategory.TIME;
    }

    @Override
    public WidgetPlacement defaultPlacement() {
        return WidgetPlacement.at(0.5f, 0.01f).enabledByDefault();   // oben mittig, gleich sichtbar
    }

    @Override
    public void configure(SettingsGroup settings) {
        label = settings.text("label", "Session", 24);
        format = settings.choice("format", "hms", "hms", "hm", "minutes");
        background = settings.toggle("background", true);
        color = settings.color("color", Colors.ACCENT_HOVER, false);
    }

    @Override
    public void tick() {
        // 20x pro Sekunde: Text bauen. render() zeichnet ihn nur.
        long seconds = startedAt < 0 ? 754 : (System.currentTimeMillis() - startedAt) / 1000;
        text = String.format("%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
    }

    @Override
    public boolean isVisible(RenderContext context) {
        return game.inWorld() || context.preview();
    }

    @Override
    public int width(RenderContext context) {
        return 12 + text.length() * 6;
    }

    @Override
    public int height(RenderContext context) {
        return 17;
    }

    @Override
    public void render(HudGraphics g, RenderContext context) {
        if (background.get()) {
            g.card(0, 0, width(context), 17, 4, Colors.withAlpha(Colors.BACKGROUND, 0xB0), Colors.BORDER);
        }
        g.text(text, 6, 5, color.argb(), false);
    }
}
```

## Koordinaten, Größe, Position

- `render` zeichnet bei (0, 0) im eigenen Koordinatensystem von `width × height` Pixeln. Lumora verschiebt und skaliert.
- Position wird als Anteil der freien Bildschirmfläche gespeichert (`x = 0` links, `1` rechts, `0.5` mittig). Dadurch
  bleibt ein Widget bei jeder Fenstergröße und GUI-Skalierung an seinem Platz und immer vollständig sichtbar.
- `minScale()`/`maxScale()` begrenzen die Größe im Editor (Griffe, Mausrad).
- `width`/`height` werden einmal pro Frame abgefragt – billig halten (zwischengespeicherte Werte).

## Daten, Leistung, Threads

- Daten in `tick()` oder Event-Listenern sammeln, in `render` nur zeichnen. Keine Datei-/Netzwerkzugriffe, keine
  Suchen, möglichst keine Allokationen pro Frame.
- `GameContext` (Berechtigung `game`) ist ein Schnappschuss des letzten Ticks, Lesen kostet nichts.
- Langsame Arbeit über `context.tasks().supplyAsync(...)` und das Ergebnis mit `onClientThread` übernehmen.
- Wirft ein Widget in `render`, `tick`, `width`/`height` oder `isVisible`, schaltet Lumora nur dieses Widget aus
  (Hinweis im Spiel, Fehler im Editor) – der Rest des HUD läuft weiter. Wieder Einschalten versucht es erneut.

## Einstellungen

In `configure` beschreiben – den Bildschirm dafür baut Lumora (Schalter, Zahl mit −/+, Schieberegler, Textfeld,
Auswahl, Farbe mit Palette, Taste; alle Einstellungen sind immer sichtbar):

```java
BooleanSetting shadow = settings.toggle("shadow", true);
IntegerSetting rows = settings.slider("rows", 5, 1, 12);
DecimalSetting opacity = settings.slider("opacity", 0.85, 0.1, 1.0, 0.05);
TextSetting title = settings.text("title", "Hallo", 24);
ChoiceSetting mode = settings.choice("mode", "compact", "compact", "full");
ColorSetting tint = settings.color("tint", 0xFF8B5CF6, false);
KeybindSetting key = settings.keybind("key", Keys.UNBOUND);
```

- Werte werden validiert (Bereiche geklemmt, ungültige Werte abgelehnt) und **pro HUD-Profil** in
  `config/lumora/hud-layout.json` gespeichert.
- Hat das Addon schon ein eigenes Konfigurationsobjekt, die Einstellung binden statt kopieren – dann speichert Lumora
  keinen zweiten Wert (so macht es LabyVoice):

```java
settings.toggle("waves", true).bind(() -> config.showAudioWaves, v -> { config.showAudioWaves = v; dirty = true; });
```

- Beschriftungen: `<addon>.widget.<widget>.<key>`, Hilfetext `….description`, Auswahl-Optionen `….<option>`.

## Zeichnen

`HudGraphics` bietet Text, Rechtecke, Rahmen, abgerundete Flächen (`roundedRect`, `card`), Texturen
(`TextureRef`), Transformationen, Scissor und `Colors` mit den Lumora-Farben. Das funktioniert auf allen
Minecraft-Versionen gleich.

Was die API nicht abdeckt (z. B. Items, eigene Schriftarten), geht über den nativen Renderer der Version – dann ist
das Widget an diese Minecraft-Version gebunden:

```java
GuiGraphicsExtractor native2d = g.unwrap(GuiGraphicsExtractor.class).orElse(null);   // 26.1.2
if (native2d != null) {
    native2d.item(stack, 2, 2);
}
```

## Lebenszyklus

`configure` → gespeicherte Werte anwenden → `initialize` → (`onEnable` ↔ `onDisable` beim Ein-/Ausschalten) →
`dispose` nach dem Entfernen (auch wenn das Addon ausgeschaltet wird). `tick` läuft nur, solange das Widget
eingeschaltet ist; ein **verborgenes** Widget tickt weiter, wird aber nicht gezeichnet.

## Klicks

`mouseClicked(x, y, button)` wird aufgerufen, wenn der Chat offen ist (freier Mauszeiger) und der Klick auf dem
Widget liegt. `true` zurückgeben, wenn er verarbeitet wurde.
