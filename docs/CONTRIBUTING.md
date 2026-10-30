# Mitarbeit

## Grundsätze

1. **Keine erfundenen APIs, Versionen oder Hashes.** Jede Fremd-API wird vorher am echten JAR (`javap`)
   oder an der offiziellen Dokumentation geprüft. Versionen und SHA-512 kommen aus der Modrinth-API.
2. **Fremden Code nicht verändern.** Sodium, ViaFabricPlus und die Erweiterungen werden bitgenau verwendet.
   Probleme werden upstream gemeldet.
3. **Kein Sodium-Code**, auch nicht teilweise (PolyForm Shield). Weitere Optimierungen kommen als
   eigenständige, kompatible Mods (Erweiterungen im Lockfile, pro Minecraft-Version).
3a. **Jede Änderung an der Begleit-Mod in allen Versionen** (`client/mc26.1.2`, `client/mc1.21.11`,
   `client/mc1.21.8`) nachziehen und dort bauen. Die API-Unterschiede stehen in COMPATIBILITY.md, Abschnitt 3.
4. **Keine Geheimnisse im Repository** (Client-IDs, Tokens, Zertifikate). Neue Log-Ausgaben, die Tokens
   enthalten könnten, laufen über `Log`/`Redactor`.
5. **Messbar optimieren.** Performance-Behauptungen nur mit Frametime-Messungen (F9-Benchmark, JSON in
   `diagnostics/`) unter gleichen Bedingungen: gleiche Welt, Position, Sichtweite, Auflösung, VSync aus.

## Code

- Java 25, 4 Leerzeichen, keine Wildcard-Imports. Der Launcher kompiliert mit `-Xlint:all -Werror`.
- `launcher-core` darf keine Minecraft- oder Swing-Klassen verwenden.
- Begleit-Mod: Bestehende Screens nur über Fabric-API-Events erweitern, nicht ersetzen. Mixins nur, wenn
  kein Event existiert, und dann nur lesend bzw. minimal (`defaultRequire: 1`).
- Zugriff auf ViaFabricPlus nur über `RuntimeInfo` (Reflection auf öffentliche Klassen, mit
  Fallback).

## Tests

```bat
gradlew.bat build                         & rem Unit-Tests aller Module
gradlew.bat :launcher-core:integrationTest & rem echte Mod-JARs, braucht Internet
```

Neue Logik in `launcher-core` braucht Unit-Tests. Netzwerkcode wird gegen `LocalServer` getestet, nicht
gegen echte Dienste. Für Änderungen am Startpfad zusätzlich den Smoke-Test beider Profile auf einer GPU
ausführen (siehe [RELEASE.md](RELEASE.md)).

## Neue Minecraft-Version hinzufügen

Siehe [ARCHITECTURE.md → Erweiterbarkeit](ARCHITECTURE.md#erweiterbarkeit-weitere-minecraft-versionen).
Kurz: Kompatibilität aller Mods prüfen (`fabric.mod.json`: `depends`/`breaks`), neues Client-Modul unter
`client/mc<version>`, Profile und Artefakte in `profiles.json`, Integrationstest erweitern.
