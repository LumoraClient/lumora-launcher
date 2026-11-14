# Build-Anleitung

## Voraussetzungen

| Werkzeug | Version | Hinweis |
|---|---|---|
| JDK | **25** (getestet: Amazon Corretto 25.0.3) | Minecraft 26.1 und Fabric Loom verlangen Java 25. Gradle sucht passende Toolchains automatisch. |
| Gradle | nicht nötig | Der Wrapper (`gradlew`/`gradlew.bat`) lädt Gradle 9.7.1 und prüft die SHA-256-Summe. |
| Windows-Packaging | im JDK enthalten (`jpackage`) | erzeugt `LumoraLauncher.exe` als App-Image ohne Zusatzwerkzeuge |
| Installer | – | eigenes Repository lumora-installer (siehe unten) |
| Internet | – | Beim ersten Build lädt Loom Minecraft 26.1.2 und Fabric (~200 MB in den Gradle-Cache) |

## Befehle (Windows, PowerShell oder cmd)

```bat
gradlew.bat build
```
Kompiliert alle Module und führt die Unit-Tests aus (Launcher-Core, Launcher-App, Begleit-Mod).

```bat
gradlew.bat :launcher-core:integrationTest
```
Integrationstest mit den **echten** Sodium-, ViaFabricPlus- und Erweiterungs-JARs aller Versionen (Download von Modrinth, SHA-512-Prüfung, depends/breaks-Analyse jedes Profils zusammen mit der jeweiligen Lumora-Mod).

```bat
gradlew.bat :launcher-app:run
```
Startet den Launcher aus dem Quellcode (mit dem frisch gebauten Begleit-Mod).

```bat
gradlew.bat :launcher-app:releaseDist
```
Baut das Release nach `build\release\` (EXE, App-Ordner, ZIP, README, Lizenzen, `SHA256SUMS.txt`).

```bat
gradlew.bat :launcher-app:portableExe :launcher-app:releaseDist
```
Erzeugt **`LumoraClient.exe`**: eine einzelne, portable EXE (Starter in C#, kompiliert mit dem in Windows
enthaltenen .NET-Framework-4.8-Compiler `csc.exe`). Der komplette Launcher samt Java-Runtime ist eingebettet.
Beim ersten Doppelklick wird er (SHA-256-geprüft) nach `%LOCALAPPDATA%\LumoraClient\portable\<hash>` entpackt
und gestartet, danach startet er direkt. Ältere entpackte Versionen werden automatisch entfernt.
Zielordner für Tests: `LUMORA_PORTABLE_DIR`.

### Installer (eigenes Repository)

Der Windows-Installer `LumoraClient-Setup.exe` ist unabhängig vom Launcher und liegt in
[lumora-installer](https://github.com/LumoraClient/lumora-installer). Er enthält den Launcher nicht, sondern lädt
beim Installieren die neueste `LumoraClient.exe` aus den Releases dieses Repositorys, prüft die Ed25519-Signatur von
`SHA256SUMS.txt` (derselbe Schlüssel wie beim Selbst-Update) und den SHA-256 der EXE und entpackt den Launcher
daraus. Die Versionsliste lädt er aus `profiles.json` desselben Release-Tags. Ein Release dieses Repositorys braucht
dafür nur die gewohnten Dateien `LumoraClient.exe`, `SHA256SUMS.txt` und `SHA256SUMS.txt.sig`.

Im Assistenten wählt man Minecraft-Versionen, Programm- und Spieldatenordner und das Design im Spiel (Lumora Theme
oder klassisch). Die Auswahl übergibt das Setup in `%APPDATA%\LumoraClient\config\setup.json`; der Launcher (oder
seine CLI, mit der das Setup die Versionen lädt: `--install-only --machine-progress`) übernimmt sie beim nächsten
Start (`SetupHandoff`). Das Design wird pro Instanz einmal gesetzt (`GameThemeSeeder`), spätere Änderungen im Hub
bleiben erhalten. Bauen, Kommandozeile und Tests: siehe README von lumora-installer. Die CI dieses Repositorys
installiert die gebaute `LumoraClient.exe` einmal mit dem Installer (`--source`), damit beide zusammenpassen.


Linux/macOS: dieselben Befehle mit `./gradlew`. `jpackageImage` läuft nur unter Windows (sonst übersprungen).
Launcher-Core und Mod bauen und testen plattformunabhängig.

## Smoke-Test des fertigen Clients

```bat
build\release\LumoraLauncher\LumoraLauncherCli.exe --smoke-test --profile 26.1.2
```
Installiert alles (oder prüft es), startet Minecraft im **offiziellen Demo-Modus** (keine Anmeldung nötig),
prüft Hauptmenü und Menüs, betritt eine Demo-Welt (Schlaganimationen, ESC-Menü, Hub), schreibt
`%APPDATA%\LumoraClient\diagnostics\smoke-26.1.2.json` und beendet das Spiel. Exit-Code 0 bedeutet bestanden.
Mit `--profile 1.21.11` bzw. `--profile 1.21.8` werden die anderen Versionen getestet.
Mit `--crash-test` lässt die Lumora-Mod das Spiel nach Erreichen des Hauptmenüs absichtlich abstürzen. Der Test
besteht, wenn der Launcher den echten Crash-Report von Minecraft findet und die Ursache richtig erkennt (Ablauf der
Abstürze-Seite von Anfang bis Ende). Im normalen Spiel ist diese Funktion nie aktiv.

Weitere CLI-Befehle: `--system-check`, `--install-only [--repair]`, `--launch-test --profile <Version> [--loader lumora|fabric|vanilla]` (Start ohne Lumora-Bericht: wartet auf die Sound-Engine, läuft 15 s, beendet das Spiel), `--version`.

Mit `LUMORA_HOME=<Ordner>` lässt sich ein separates Datenverzeichnis nutzen, z. B. für CI.

## Microsoft-Anmeldung (Azure-App)

Seit 0.3.44 ist Lumoras eigene Anwendungs-ID fest eingebaut (`launcher-core/.../AppIds.java`, keine Einstellung mehr):
`54473e32-df8f-42e9-a649-9419b0dab9d3`. Sie ist eine öffentliche Client-ID ohne Secret (Browser-Anmeldung mit PKCE).
So wurde bzw. wird eine solche App eingerichtet, etwa für einen eigenen Build:

1. Im [Azure-Portal](https://portal.azure.com) unter **App-Registrierungen → Neue Registrierung** eine App anlegen.
   - Unterstützte Kontotypen: **Persönliche Microsoft-Konten** (Minecraft-Konten sind Consumer-Konten; der Launcher nutzt den `consumers`-Tenant).
   - Unter **Authentifizierung → Plattform hinzufügen → Mobile und Desktopanwendungen** die Umleitungs-URI
     **`http://localhost`** eintragen. Diese URI nutzt die Browser-Anmeldung des Launchers (Authorization Code + PKCE,
     zufälliger lokaler Port, den Microsoft bei `localhost` ignoriert).
   - Unter **Authentifizierung → Öffentliche Clientflows zulassen** auf **Ja** setzen (für die Alternative „Mit Code anmelden“).
2. Die **Anwendungs-ID (Client-ID)** notieren. Sie ist eine GUID und kein Geheimnis. Ein Client-Secret wird nicht verwendet.
3. **Freischaltung für die Minecraft-API:** Neu registrierte Apps erhalten von `api.minecraftservices.com`
   den Fehler *403 „Invalid app registration, see https://aka.ms/AppRegInfo“*, bis Mojang/Microsoft die App
   freigegeben hat. Den Antrag stellt man über den unter **https://aka.ms/AppRegInfo** beschriebenen Weg.
   Bearbeitungsdauer und Kriterien liegen bei Microsoft.
4. Für einen eigenen Build die Client-ID in `AppIds.MICROSOFT_CLIENT_ID` eintragen.

Ist eine Client-ID (noch) nicht freigegeben, funktionieren Installation, Smoke-Test und alle Diagnosefunktionen. Nur der
Start mit einem Konto ist dann nicht möglich, und der Launcher meldet das verständlich.

## Discord Rich Presence

Auch die Discord-Anwendungs-ID ist fest eingebaut (`AppIds.DISCORD_APP_ID` = `1568277782901948486`), Rich Presence ist immer
an. Ein Bild namens „lumora“ unter *Rich Presence → Art Assets* der Discord-Anwendung wird als Symbol angezeigt.

## Fehlermeldungen beim Build

| Meldung | Ursache / Lösung |
|---|---|
| `Unsupported class file major version 69` / Toolchain nicht gefunden | JDK 25 installieren oder `org.gradle.java.installations.paths` setzen |
| `Could not resolve com.mojang:minecraft:26.1.2` | keine Internetverbindung beim ersten Build |
| `Anwendungszielverzeichnis … ist bereits vorhanden` (jpackage) | ältere Builds: `launcher-app\build\jpackage` löschen (aktuelle Builds räumen selbst auf) |
| `jpackageImage SKIPPED` | nicht unter Windows ausgeführt |
| `companion mod jar was not built` | `:client-mc26.1.2:jar` fehlgeschlagen, Ausgabe davor prüfen |
