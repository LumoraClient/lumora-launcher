# Ein Lumora-Addon erstellen

Ein Lumora-Addon ist eine JAR mit einer `lumora-addon.json` und Klassen, die gegen die Lumora-API gebaut sind. Der
Lumora-Client lädt Addons mit seinem **eigenen Addon-Loader** aus dem Ordner `addons/` der Instanz, z. B.
`%APPDATA%\LumoraClient\instances\<version>\addons`. Beim Start findet er sie, prüft Metadaten und Abhängigkeiten und
ruft die Einstiegsklasse auf. Ein Addon braucht keinen Mod-Loader und keine Minecraft-Klassen: Widgets, Menüs,
Spielermenü, Chat, Design, Spieler, Plugin-Kanäle und Namensschild-Symbole gibt es über die API. Dieselbe JAR läuft
deshalb in allen Minecraft-Versionen, die Lumora unterstützt (1.8.9 bis 26.3).

Jede JAR bekommt einen eigenen Class-Loader:
- **Spielklassen zuerst:** Klassen der API und des Spiels kommen immer aus dem Spiel. Ein Addon kann sie also nicht ersetzen.
- **Gesperrte Pakete:** Eigene Klassen in den Paketen von Java, Minecraft und Lumora (`dev.lumora.api`, `dev.lumora.core` …) sind gesperrt.
- **Andere Addons:** Klassen eines anderen Addons sieht ein Addon nur, wenn es dieses unter `depends` oder `optional` angibt.
- **Fehler:** Eine fehlerhafte JAR (keine `lumora-addon.json`, kaputtes Archiv) steht mit Grund in der Addon-Liste. Die anderen Addons laufen weiter.

Eine ID darf nur einmal vorkommen.

Das vollständige, kompilierte Beispiel liegt in [`examples/session-timer-addon`](../examples/session-timer-addon):
ein Widget mit der Zeit in der aktuellen Welt, ein Keybind zum Neustarten, eine Addon-Einstellung und eine
Benachrichtigung. Es verwendet nur die API und läuft deshalb in allen Minecraft-Versionen mit derselben JAR.

## 1. Projekt

Im Lumora-Repository (wie das Beispiel):

```kotlin
// settings.gradle.kts
include(":examples:mein-addon")

// examples/mein-addon/build.gradle.kts
plugins { `java-library` }
version = "1.0.0"
dependencies { compileOnly(project(":lumora-api")) }   // zur Laufzeit liefert der Lumora-Client die API
tasks.withType<JavaCompile>().configureEach { options.release.set(21) }  // Java 21: läuft in allen Profilen
tasks.processResources {
    val v = project.version.toString()
    inputs.property("version", v)
    filesMatching("lumora-addon.json") { expand("version" to v) }
}
```

In einem eigenen Projekt: zuerst im Lumora-Projekt `gradlew :lumora-api:publish` ausführen (schreibt
`build/repo/dev/lumora/lumora-api/1.5.0/`), dann im Addon:

```groovy
repositories {
    maven { url = file("../lumora-client/build/repo").toURI(); content { includeGroup 'dev.lumora' } }
}
dependencies { compileOnly 'dev.lumora:lumora-api:1.5.0' }
```

Einfacher ist die [Addon-Vorlage](https://github.com/LumoraClient/lumora-addon-template): Sie bringt die API als
Quellmodul mit und baut ohne weitere Abhängigkeiten.

## 2. lumora-addon.json (im Ressourcen-Wurzelverzeichnis)

```json
{
  "schema": 1,
  "id": "mein-addon",
  "name": "Mein Addon",
  "version": "${version}",
  "description": "Kurze Beschreibung für den Lumora-Hub",
  "authors": ["Ich"],
  "api": "1.0.0",
  "minecraft": ["*"],
  "entrypoint": "com.example.meinaddon.MeinAddon",
  "depends": { "lumora": ">=1.0.0" },
  "permissions": ["hud", "keybinds", "notifications", "game"]
}
```

`"api"` ist die **niedrigste** API-Version, die das Addon braucht: Ein Addon mit `"api": "1.1.0"` läuft auch auf 1.5,
ein älterer Client lehnt ein zu neues Addon mit klarem Hinweis ab. Gib also die Version an, deren Dienste du nutzt.

Nur die Berechtigungen angeben, die gebraucht werden:

| Berechtigung | seit | wofür |
|---|---|---|
| `hud` | 1.0 | Widgets |
| `keybinds` | 1.0 | eigene Tasten |
| `notifications` | 1.0 | Benachrichtigungen |
| `tasks` | 1.0 | Timer, Hintergrundaufgaben |
| `game` | 1.0 | Spieler-, Welt- und Verbindungsdaten |
| `screens` | 1.1 | eigene Menüs |
| `players` | 1.1 | andere Spieler, Sichtlinie, Tab-Liste |
| `session` | 1.1 | Konto, Mojang-Join |
| `network` | 1.1 | Plugin-Kanäle |
| `nametags` | 1.1 | Symbole am Namen |
| `playermenu` | 1.4 | eigene Einträge im Spielermenü |
| `design` | 1.5 | eigene Akzentfarben im Hub (das Theme lesen geht ohne) |
| `chat` | 1.5 | Einträge im Menü von Chat-Nachrichten, Chat öffnen |
| `social` | 1.5 | Freunde und Party von Lumora Social lesen |

Event-Bus, Übersetzungen, Ressourcen, die Addon-Einstellungen, das Theme sowie `configDirectory()` und `modLoaded(id)`
gehen immer. Felder: siehe [API_REFERENCE.md](API_REFERENCE.md#lumora-addonjson).

## 3. Einstiegsklasse

Aus dem Beispiel (`SessionTimerAddon`):

```java
public final class SessionTimerAddon implements LumoraAddon {
    private BooleanSetting notifyOnJoin;

    @Override
    public void onInitialize(AddonContext context) {
        // Addon-Einstellungen: im Lumora-Hub unter "Addons" → ⚙, gespeichert in config/lumora/addons/session-timer.json
        notifyOnJoin = context.configuration().settings().toggle("notify_on_join", true);
    }

    @Override
    public void onEnable(AddonContext context) {
        SessionTimerWidget widget = new SessionTimerWidget(context.game());
        context.widgets().register(widget);
        context.events().subscribe(WorldJoinEvent.class, e -> {
            widget.start();
            if (notifyOnJoin.get()) {
                context.notifications().info(context.translations().translate("session-timer.title"),
                        context.translations().translate("session-timer.started"));
            }
        });
        context.events().subscribe(WorldLeaveEvent.class, e -> widget.stop());
        context.keybinds().register("reset", "session-timer.key.reset", Keys.J, () -> {
            widget.start();
            context.notifications().success(context.translations().translate("session-timer.title"),
                    context.translations().translate("session-timer.reset"));
        });
    }
}
```

Lebenszyklus und Regeln:

1. `onInitialize` – einmal nach dem Erzeugen. Hier nur Einstellungen definieren; registrieren ist noch nicht erlaubt.
2. `onEnable` – Listener, Widgets, Keybinds, Timer registrieren. Eine Ausnahme hier macht das Addon zu `FAILED` und
   entfernt alles, was es bis dahin registriert hatte; andere Addons laufen weiter.
3. `onDisable` – eigene Ressourcen freigeben. Alles über den Kontext Registrierte entfernt Lumora danach selbst, auch
   wenn `onDisable` eine Ausnahme wirft.
4. Nach „Entladen“ (`AddonManager.unload`) erzeugt das nächste Einschalten eine neue Instanz. Java-Klassen bleiben bis
   zum Neustart geladen.

Ein ausgeschaltetes Addon darf nicht weiterarbeiten: keine eigenen Threads, keine statischen Referenzen auf
Spielobjekte, die `onDisable` überleben. Für Hintergrundarbeit `context.tasks().supplyAsync(...)` nutzen.

## 4. Übersetzungen, Texturen, Schriften und Klänge

`src/main/resources/assets/<namespace>/lang/de_de.json` und `en_us.json` (bis 1.12 auch `.lang`).
- `context.translations()` liest die Sprachdateien direkt aus der JAR. Erst kommt das Spiel (Ressourcenpakete können Texte überschreiben), dann die JAR, dann `en_us`.
- Eigene Dateien liest das Addon mit `getClass().getResourceAsStream("/…")`. Die JAR wird dabei zuerst durchsucht.
- **Texturen, Schriften und Klänge:** Lumora kopiert beim Start die `assets/` aller Addon-JARs nach `.lumora/addon-assets` und meldet den Ordner als immer aktives Ressourcenpaket „Lumora-Addons“ an. `TextureRef`, eigene Schriften (`font/*.json`) und Klänge funktionieren deshalb wie aus einem Ressourcenpaket. Kopiert wird nur bei geänderten JARs. Die Namensräume `minecraft`, `realms` und `lumora` kann ein Addon nicht überschreiben, dafür gibt es normale Ressourcenpakete. Liefern zwei Addons dieselbe Datei, gewinnt die JAR, deren Dateiname alphabetisch zuerst kommt.
- **Bibliotheken** gehören entpackt in die Addon-JAR (verschachtelte JARs unter `META-INF/jars` werden nicht ausgepackt). Jede JAR hat genau einen Klassenpfad.

Schlüssel, die Lumora erwartet:

| Schlüssel | wofür |
|---|---|
| `<addon>.widget.<widget>` | Name des Widgets im HUD-Editor |
| `<addon>.widget.<widget>.<setting>` | Einstellungen eines Widgets, Auswahl-Optionen mit `.<option>` |
| `<addon>.setting.<setting>` | Addon-Einstellungen |
| beliebig | Keybind-Namen (`nameKey` beim Registrieren) |

## 5. Bauen und installieren

```bash
gradlew :examples:session-timer-addon:jar
```

Die JAR (`examples/session-timer-addon/build/libs/lumora-session-timer-1.0.0.jar`) in den Ordner `addons` der
Lumora-Instanz kopieren (`%APPDATA%\LumoraClient\instances\<version>\addons`). Im Spiel öffnet der Lumora-Hub unter
**Addons** → **Addon-Ordner** genau diesen Ordner. Er wird beim ersten Start angelegt.

Nach dem Neustart erscheint das Addon im Lumora-Hub unter **Addons** (an/aus, Einstellungen, Fehlermeldung), sein
Widget im **HUD-Editor**. Die Quelle steht unter dem Namen (`addons/<datei>.jar`). Lumora gibt die JARs beim Beenden
des Spiels frei, danach lassen sie sich ersetzen oder löschen.

Addons sind Programmcode mit denselben Rechten wie das Spiel. Die Berechtigungen in `lumora-addon.json` regeln
nur, welche Lumora-Dienste ein Addon nutzen darf, sie sind keine Sandbox. Installiere nur Addons aus Quellen, denen du
vertraust.

## 6. Prüfen

- Log: `Addon <id> <version> enabled` bzw. der Grund für `FAILED`.
- Lumora-Smoke-Test (`LumoraLauncherCli --smoke-test --profile <version>`): der Bericht enthält unter `lumoraApi` alle
  Addons mit Zustand und alle Widgets; ein fehlgeschlagenes Addon lässt den Test scheitern.
- Unit-Tests ohne Spiel: `lumora-core` hat eine `FakePlatform` (siehe `lumora-core/src/test`), mit der sich Addons im
  echten `LumoraCore` starten lassen.

## 7. Menüs und Namensschild-Symbole (API 1.1)

Beides braucht `"api": "1.1.0"`, `"depends": { "lumora": ">=1.1.0" }` und die Berechtigungen `screens` bzw.
`nametags`. Vollständiges Beispiel: LumoraVoice (`dev.lumora.voice.lumora.LumoraVoiceAddon`).

### Menüs: `AddonView` und `registerMenu`

Ein Menü ist eine `AddonView`: Sie zeichnet mit `HudGraphics` und bekommt Maus und Tastatur, ohne Minecraft-Klassen.
Lumora zeigt sie in einem Bildschirm der laufenden Version (GUI-Koordinaten, Maustaste 0 = links, Tasten aus `Keys`,
auch auf 1.8.9/1.12.2 mit LWJGL 2). Seit 1.5 zeichnet `UiKit` die Lumora-Steuerelemente in den Farben des Spielers
(Abschnitt 9).

```java
final class MeinMenue implements AddonView {
    private final AddonScreens screens;

    MeinMenue(AddonScreens screens) {
        this.screens = screens;
    }

    @Override
    public void render(HudGraphics g, int width, int height, int mouseX, int mouseY) {
        g.fill(0, 0, width, height, Colors.SURFACE);
        g.centeredText("Mein Addon", width / 2, 20, Colors.TEXT, true);
    }

    @Override
    public boolean keyPressed(int key, boolean ctrl, boolean shift) {
        if (key == Keys.BACKSPACE) {
            screens.back();   // zurück zum vorherigen Bildschirm, wie Escape
            return true;
        }
        return false;
    }
}

// in onEnable:
AddonScreens screens = context.screens();
screens.registerMenu(() -> new MeinMenue(screens));                         // ▶ im Lumora-Hub
context.keybinds().register("menu", "mein-addon.key.menu", Keys.G, () -> screens.open(new MeinMenue(screens)));
```

- `registerMenu` macht die Ansicht zum Hauptmenü des Addons: Im Lumora-Hub unter **Addons** erscheint neben ⚙ ein
  **▶-Knopf**. Das ist wichtig, weil Lumora einen Keybind ohne Taste startet, wenn die Standardtaste schon belegt ist –
  das Menü bleibt so trotzdem erreichbar. Der `Supplier` erzeugt bei jedem Öffnen eine neue Ansicht.
- Menüs stapeln sich: `open` legt eine Ansicht oben drauf, `back()` oder Escape kehrt zurück. `closeRequested()`
  kann Escape abfangen; `removed()` meldet, dass die Ansicht nicht mehr gezeigt wird.
- `openAddonSettings()` springt in die Hub-Einstellungen des Addons, `clipboard()` liest die Zwischenablage.

### Namensschild-Symbole: eigene Schrift

Ein Symbol ist ein Zeichen aus einer **Schrift, die das Addon mitbringt**. So übernimmt die normale Textdarstellung
Position, Durchsichtigkeit und Tab-Liste. Typisch ist ein Bitmap-Provider, z. B.
`src/main/resources/assets/mein-addon/font/icons.json`:

```json
{
  "providers": [
    {"type": "bitmap", "file": "mein-addon:icon/symbol.png", "ascent": 7, "height": 8, "chars": [""]}
  ]
}
```

Die Textur liegt dann in `assets/mein-addon/textures/icon/symbol.png`. Weiße Symbole lassen sich über `color` einfärben.
Registrieren in `onEnable`:

```java
context.nameTags().register((player, place) -> spricht(player)
        ? Optional.of(new NameTags.Icon("mein-addon:icons", "", 0x55FF55, "♪", ""))
        : Optional.empty());
```

- Der Decorator wird **jedes Frame** für jeden sichtbaren Namen gefragt (`Place.WORLD` = Namensschild,
  `Place.TAB_LIST` = Tab-Liste): nur vorbereitete Daten nachschlagen, nichts berechnen.
- **1.8.9 und 1.12.2** kennen keine eigenen Schriften. Dort hängt Lumora stattdessen den Text `fallback` (oben `"♪"`)
  in der nächstliegenden Chatfarbe an. Ein leerer `fallback` heißt: auf diesen Versionen kein Symbol.
- Das letzte Feld (`subtitle`) ist eine kleine Zeile unter dem Namensschild. Sie erscheint nur auf 26.x und 1.21.11
  (siehe [COMPATIBILITY.md](COMPATIBILITY.md#lumora-api-11-je-version)); auf anderen Versionen fehlt sie ohne Fehler.

## 8. Einstellungen (API 1.3)

Einstellungen beschreibt ein Addon in `onInitialize` über `context.configuration().settings()` (Addon) oder in
`configure(SettingsGroup)` (Widget). Lumora baut daraus die Einstellungsseite im Hub, speichert die Werte und setzt
sie zurück. Jede Art von Steuerelement ist eine eigene Einstellung, ähnlich den Annotationen eines LabyMod-Addons:

```java
SettingsGroup s = context.configuration().settings();
// Abschnitt "Anzeige": Schalter, Knopfreihe, Aufklapp-Liste mit einem Enum
BooleanSetting show = s.toggle("show", true).section("my-addon.setting.section.display");
ChoiceSetting style = s.selection("style", "ring", "minecraft", "bar", "ring", "dot").requires(show);
EnumSetting<Position> position = s.dropdown("position", Position.CROSSHAIR).requires(show);
IntegerSetting size = s.slider("size", 100, 50, 200).step(5).unit("%").requires(show);
// Abschnitt "Farben": Farbwähler mit Deckkraft und Regenbogen
ColorSetting color = s.chroma("color", 0xFFFFFFFF, true).section("my-addon.setting.section.colors");
// Häkchen-Kästen für eine Liste von Dingen
BooleanSetting cooldowns = s.checkbox("cooldowns", true).section("my-addon.setting.section.show");
BooleanSetting bows = s.checkbox("bows", true);
// nur sichtbar, solange die Bedingung gilt
IntegerSetting volume = s.slider("volume", 60, 0, 100).unit("%").visibleWhen(() -> show.get() && cooldowns.get());
// Knopf: führt eine Aktion aus, wird nicht gespeichert
s.button("reset_position", () -> { size.reset(); position.reset(); });
```

- **Übersetzungen:** Namen kommen aus `<addon>.setting.<schlüssel>`, Optionen aus `<addon>.setting.<schlüssel>.<option>`
  (Enum-Konstanten klein geschrieben). Abschnitte nutzen den Schlüssel, den du `section(...)` gibst, die Knopf-Beschriftung
  kommt aus `<addon>.setting.<schlüssel>.button` oder `label(...)`.
- **Farben** immer mit `color.argb()` zeichnen, das enthält den Regenbogen. `get()` ist die gewählte Grundfarbe.
- **Hilfetexte** erscheinen beim Drüberfahren: `<name>.description`.
- **Alle Einstellungen sind immer sichtbar.** Einen Schalter „Erweiterte Einstellungen“ gibt es nicht mehr; `.advanced()` bleibt nur, damit ältere Addons weiter kompilieren, und blendet nichts aus.
- Ein Addon, das diese Arten nutzt, gibt in `lumora-addon.json` `"api": "1.3.0"` an. Ältere Clients lehnen es dann
  mit einem klaren Hinweis ab, statt beim Start zu scheitern.

## 9. Design, HUD-Anker, Spielermenü, Chat und Social (API 1.4 und 1.5)

Damit kann ein Addon alles erweitern, was der Client selbst zeigt. `"api"` entsprechend auf `"1.4.0"` (nur
Spielermenü) oder `"1.5.0"` setzen und die Berechtigungen angeben.

### Design: Theme und Lumora-Bausteine

`context.theme()` liefert die Farben, die der Spieler im Hub gewählt hat – bei jedem Aufruf neu, ein Wechsel wirkt
sofort. `UiKit` zeichnet damit Lumoras Steuerelemente; das Addon behält seinen Zustand selbst.

```java
Theme t = context.theme();
UiKit.panel(g, t, 0, 0, width, height);
UiKit.title(g, t, 20, 16, "Mein Addon");
boolean hover = UiKit.inside(mouseX, mouseY, 20, 40, 120, 20);
UiKit.button(g, t, 20, 40, 120, 20, "Start", hover, true);
UiKit.toggle(g, t, 150, 44, aktiv, UiKit.inside(mouseX, mouseY, 150, 44, UiKit.TOGGLE_W, UiKit.TOGGLE_H));
UiKit.slider(g, t, 20, 70, 120, lautstaerke, false);       // Klick → UiKit.sliderValue(mouseX, 20, 120)
UiKit.textField(g, t, 20, 90, 160, 16, name, "Name …", fokus);

// eigene Akzentfarbe im Hub anbieten (Berechtigung "design")
t.addAccent("gold", "Gold", 0xD4A017);   // Id im Hub: "mein-addon:gold"
```

Weitere Bausteine: `card`, `secondaryButton`, `label`, `hint`, `listRow`, `badge`. Auch HUD-Widgets können mit
`t.accent()` statt einer festen Farbe zeichnen.

### HUD: Widgets an Ankerzonen

```java
@Override
public WidgetPlacement defaultPlacement() {
    // 4 px vom rechten, 30 px vom unteren Rand – bleibt dort bei jeder Fenster- und GUI-Größe
    return WidgetPlacement.anchored("bottom_right", 4, 30).enabledByDefault();
}
```

Die neun Zonen stehen in `WidgetPlacement.ANCHORS`, wie im HUD-Editor. Zieht der Spieler das Widget woanders hin,
gilt seine Position.

### Spielermenü (API 1.4, Berechtigung `playermenu`)

Spieler anvisieren, Mausrad halten: Um den Spieler erscheint ein Ring. Eigene Einträge, auch in Kategorien:

```java
context.playerMenu().add(PlayerMenuEntry.builder("kick", "Kicken")
        .icon("⚒").category("Moderation", "⚒")
        .requiresPermissionLevel(2)             // nur mit OP-Stufe 2
        .visibleWhen(PlayerTarget::multiplayer)
        .onSelect(t -> context.chat().openChat("/kick " + t.name() + " "))
        .build());
```

`PlayerTarget` sagt, ob der Spieler Lumora nutzt, dein Freund oder in deiner Party ist. `enabledWhen(...)` zeigt einen
Eintrag ausgegraut, `keepMenuOpen()` lässt das Menü nach dem Klick offen.

### Chat (Berechtigung `chat`)

```java
// Eintrag im Menü einer Chat-Nachricht (Rechtsklick oder ⋯), nach Lumoras eigenen
context.chat().addMenuEntry(ChatMenuEntry.builder("translate", "Übersetzen")
        .visibleWhen(line -> !line.text().isBlank())
        .onSelect(line -> uebersetze(line.text()))
        .build());

// jede neue Nachricht, auch wenn das Chat-Modul aus ist
context.events().subscribe(ChatMessageEvent.class, e -> {
    ChatLine line = e.line();       // Text, Absender (falls erkennbar), Links
});
```

Das Menü der Chat-Nachrichten gibt es, solange das Chat-Modul an ist.

### Social (Berechtigung `social`)

```java
SocialInfo s = context.social();
s.party().ifPresent(p -> sprachgruppe(p.members()));      // z. B. Voice-Gruppe = Party
context.events().subscribe(SocialChangedEvent.class, e -> {
    if (e.kind() == SocialChangedEvent.Kind.PARTY) { aktualisieren(s.party()); }
});
```

Nur lesen: Freunde mit Online-Status (`friends()`, `isFriend`), die Party und das eigene Konto. Ohne Verbindung zum
Lumora-Social-Server ist alles leer.
