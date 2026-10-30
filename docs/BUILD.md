# Build-Anleitung

## Voraussetzungen

| Werkzeug | Version | Hinweis |
|---|---|---|
| JDK | **25** (getestet: Amazon Corretto 25.0.3) | Minecraft 26.1 und Fabric Loom verlangen Java 25. Gradle sucht passende Toolchains automatisch. |
| Gradle | nicht nötig | Der Wrapper (`gradlew`/`gradlew.bat`) lädt Gradle 9.7.1 und prüft die SHA-256-Summe. |
| Windows-Packaging | im JDK enthalten (`jpackage`) | erzeugt `LumoraLauncher.exe` als App-Image ohne Zusatzwerkzeuge |
| Installer (optional) | Inno Setup 6 (`ISCC.exe`) | nur für `LumoraClient-Setup-<v>.exe` |
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

```bat
gradlew.bat :launcher-app:setupExe :launcher-app:releaseDist
```
Wie oben, zusätzlich mit **`LumoraClient-Setup-<v>.exe`**: eine einzelne Datei, gebaut mit dem in Windows
enthaltenen **IExpress** (kein Zusatzwerkzeug). Das Setup installiert ohne Admin-Rechte nach
`%LOCALAPPDATA%\Programs\LumoraClient`, legt Startmenü- und Desktop-Verknüpfung sowie `Deinstallieren.cmd` an
und startet den Launcher. Für stille Installationen und Tests: `LUMORA_NO_SHORTCUTS=1`, `LUMORA_NO_START=1`,
Aufruf `LumoraClient-Setup-<v>.exe /Q`.

```bat
gradlew.bat :launcher-app:installerExe :launcher-app:releaseDist
```
Alternative mit Inno Setup 6 (Assistent mit Sprachauswahl und Deinstallation über die Windows-Einstellungen),
falls installiert (Pfad über `-PisccPath=...` oder Umgebungsvariable `ISCC`). Beide Tasks erzeugen denselben
Dateinamen. Es sollte nur einer verwendet werden.

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

## Microsoft-Anmeldung einrichten (einmalig, für Verteiler)

Der Launcher enthält bewusst **keine** Client-ID. So geht's:

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
4. Client-ID hinterlegen, auf eine der folgenden Arten:
   - im Launcher: Beim ersten Klick auf *Anmelden* oder *Spielen* öffnet sich der Einrichtungsdialog (oder später unter
     *Einstellungen → Microsoft-Anmeldung: Azure-Client-ID*), oder
   - systemweit über die Umgebungsvariable `LUMORA_MS_CLIENT_ID`.

Ohne freigegebene Client-ID funktionieren Installation, Smoke-Test und alle Diagnosefunktionen. Nur der
Start mit einem Konto ist dann nicht möglich, und der Launcher meldet das verständlich.

## Fehlermeldungen beim Build

| Meldung | Ursache / Lösung |
|---|---|
| `Unsupported class file major version 69` / Toolchain nicht gefunden | JDK 25 installieren oder `org.gradle.java.installations.paths` setzen |
| `Could not resolve com.mojang:minecraft:26.1.2` | keine Internetverbindung beim ersten Build |
| `Anwendungszielverzeichnis … ist bereits vorhanden` (jpackage) | ältere Builds: `launcher-app\build\jpackage` löschen (aktuelle Builds räumen selbst auf) |
| `jpackageImage SKIPPED` | nicht unter Windows ausgeführt |
| `installerExe SKIPPED` | Inno Setup nicht gefunden, `-PisccPath=C:\…\ISCC.exe` angeben |
| `companion mod jar was not built` | `:client-mc26.1.2:jar` fehlgeschlagen, Ausgabe davor prüfen |
