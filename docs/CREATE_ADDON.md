# Ein Lumora-Addon erstellen

Ein Lumora-Addon ist eine normale Fabric-Mod, die zusätzlich eine `lumora-addon.json` mitbringt. Der Lumora-Client
findet sie beim Start, prüft die Metadaten und Abhängigkeiten und ruft die Einstiegsklasse auf. Ohne Lumora wird die
Einstiegsklasse nie geladen; eine Mod kann also mit und ohne Lumora laufen (so macht es LabyVoice).

Das vollständige, kompilierte Beispiel liegt in [`examples/session-timer-addon`](../examples/session-timer-addon):
ein Widget mit der Zeit in der aktuellen Welt, ein Keybind zum Neustarten, eine Addon-Einstellung und eine
Benachrichtigung. Es verwendet nur die API und läuft deshalb auf 26.1.2, 1.21.11 und 1.21.8 mit derselben JAR.

## 1. Projekt

Im Lumora-Repository (wie das Beispiel):

```kotlin
// settings.gradle.kts
include(":examples:mein-addon")

// examples/mein-addon/build.gradle.kts
plugins { `java-library` }
version = "1.0.0"
dependencies { compileOnly(project(":lumora-api")) }   // zur Laufzeit liefert der Lumora-Client die API
tasks.withType<JavaCompile>().configureEach { options.release.set(21) }  // läuft auch auf Minecraft 1.21.x
tasks.processResources {
    val v = project.version.toString()
    inputs.property("version", v)
    filesMatching(listOf("fabric.mod.json", "lumora-addon.json")) { expand("version" to v) }
}
```

In einem eigenen Projekt: zuerst im Lumora-Projekt `gradlew :lumora-api:publish` ausführen (schreibt
`build/repo/dev/lumora/lumora-api/1.1.0/`), dann im Addon:

```groovy
repositories {
    maven { url = file("../lumora-client/build/repo").toURI(); content { includeGroup 'dev.lumora' } }
}
dependencies { compileOnly 'dev.lumora:lumora-api:1.1.0' }
```

Braucht das Addon Minecraft-Klassen (z. B. eigene Items zeichnen), ist es eine Loom-Mod für genau diese
Minecraft-Version (wie LabyVoice) und nutzt `HudGraphics.unwrap(...)`. Seit API 1.1 kommen viele Addons ohne
Minecraft-Klassen aus: Spieler, Plugin-Kanäle, Menüs und Namensschild-Symbole gibt es über die API (Abschnitt 8).
LumoraVoice ist so eine JAR für alle 18 Profile.

## 2. fabric.mod.json

```json
{
  "schemaVersion": 1,
  "id": "mein-addon",
  "version": "${version}",
  "name": "Mein Addon",
  "environment": "client",
  "depends": { "fabricloader": ">=0.19.5", "lumora": "*" }
}
```

Soll die Mod auch ohne Lumora laufen, `"suggests": { "lumora": "*" }` statt `depends` verwenden.
Einen Fabric-Einstiegspunkt braucht ein reines API-Addon nicht.

## 3. lumora-addon.json (im Ressourcen-Wurzelverzeichnis)

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

Nur die Berechtigungen angeben, die gebraucht werden: `hud` (Widgets), `keybinds`, `notifications`, `tasks`
(Timer, Hintergrundaufgaben), `game` (Spieler-/Welt-/Verbindungsdaten). Seit API 1.1 außerdem `screens` (eigene
Menüs), `players` (andere Spieler, Sichtlinie), `session` (Konto, Mojang-Join), `network` (Plugin-Kanäle) und
`nametags` (Symbole am Namen); ein Addon, das sie nutzt, gibt `"api": "1.1.0"` und `"lumora": ">=1.1.0"` an.
Event-Bus, Übersetzungen, Ressourcen, die Addon-Einstellungen sowie `configDirectory()` und `modLoaded(id)` gehen immer.
Felder: siehe [API_REFERENCE.md](API_REFERENCE.md#lumora-addonjson).

## 4. Einstiegsklasse

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

## 5. Übersetzungen und Texturen

`src/main/resources/assets/<namespace>/lang/de_de.json` und `en_us.json`. Fabric macht die Assets jeder Mod zum
Ressourcenpaket, eine Registrierung ist nicht nötig. Schlüssel, die Lumora erwartet:

| Schlüssel | wofür |
|---|---|
| `<addon>.widget.<widget>` | Name des Widgets im HUD-Editor |
| `<addon>.widget.<widget>.<setting>` | Einstellungen eines Widgets, Auswahl-Optionen mit `.<option>` |
| `<addon>.setting.<setting>` | Addon-Einstellungen |
| beliebig | Keybind-Namen (`nameKey` beim Registrieren) |

## 6. Bauen und installieren

```bash
gradlew :examples:session-timer-addon:jar
```

Die JAR (`examples/session-timer-addon/build/libs/lumora-session-timer-1.0.0.jar`) in den `mods`-Ordner der
Lumora-Instanz kopieren (`%APPDATA%\LumoraClient\instances\<version>\mods`). Der Launcher lässt eigene Mods dort
liegen und zeigt sie unter „Mods“. Nach dem Start erscheint das Addon im Lumora-Hub unter **Addons** (an/aus,
Einstellungen, Fehlermeldung), sein Widget im **HUD-Editor**.

## 7. Prüfen

- Log: `Addon <id> <version> enabled` bzw. der Grund für `FAILED`.
- Lumora-Smoke-Test (`LumoraLauncherCli --smoke-test --profile <version>`): der Bericht enthält unter `lumoraApi` alle
  Addons mit Zustand und alle Widgets; ein fehlgeschlagenes Addon lässt den Test scheitern.
- Unit-Tests ohne Spiel: `lumora-core` hat eine `FakePlatform` (siehe `lumora-core/src/test`), mit der sich Addons im
  echten `LumoraCore` starten lassen.

## 8. Menüs und Namensschild-Symbole (API 1.1)

Beides braucht `"api": "1.1.0"`, `"depends": { "lumora": ">=1.1.0" }` und die Berechtigungen `screens` bzw.
`nametags`. Vollständiges Beispiel: LumoraVoice (`../LumoraVoice-SRC-0.8.0`, `dev.lumora.voice.lumora.LumoraVoiceAddon`).

### Menüs: `AddonView` und `registerMenu`

Ein Menü ist eine `AddonView`: Sie zeichnet mit `HudGraphics` und bekommt Maus und Tastatur, ohne Minecraft-Klassen.
Lumora zeigt sie in einem Bildschirm der laufenden Version (GUI-Koordinaten, Maustaste 0 = links, Tasten aus `Keys`,
auch auf 1.8.9/1.12.2 mit LWJGL 2).

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
    {"type": "bitmap", "file": "mein-addon:icon/symbol.png", "ascent": 7, "height": 8, "chars": [""]}
  ]
}
```

Die Textur liegt dann in `assets/mein-addon/textures/icon/symbol.png`. Weiße Symbole lassen sich über `color` einfärben.
Registrieren in `onEnable`:

```java
context.nameTags().register((player, place) -> spricht(player)
        ? Optional.of(new NameTags.Icon("mein-addon:icons", "", 0x55FF55, "♪", ""))
        : Optional.empty());
```

- Der Decorator wird **jedes Frame** für jeden sichtbaren Namen gefragt (`Place.WORLD` = Namensschild,
  `Place.TAB_LIST` = Tab-Liste): nur vorbereitete Daten nachschlagen, nichts berechnen.
- **1.8.9 und 1.12.2** kennen keine eigenen Schriften. Dort hängt Lumora stattdessen den Text `fallback` (oben `"♪"`)
  in der nächstliegenden Chatfarbe an. Ein leerer `fallback` heißt: auf diesen Versionen kein Symbol.
- Das letzte Feld (`subtitle`) ist eine kleine Zeile unter dem Namensschild. Sie erscheint nur auf 26.x und 1.21.11
  (siehe [COMPATIBILITY.md](COMPATIBILITY.md#lumora-api-11-je-version)); auf anderen Versionen fehlt sie ohne Fehler.
