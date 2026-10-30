# Kompatibilitätsmatrix

Stand: **2026-10-20**. Alle Versionen, Dateinamen, Größen und SHA-512-Werte stammen aus der offiziellen
Modrinth-API (`api.modrinth.com/v2`), den Metadaten von Mojang (`piston-meta.mojang.com`), Fabric
(`meta.fabricmc.net`, `maven.fabricmc.net`) und Legacy Fabric (`meta.legacyfabric.net`, `maven.legacyfabric.net`)
sowie aus den `fabric.mod.json`-Dateien der heruntergeladenen JARs. Nichts davon ist geschätzt. Die vollständige
Liste steht in `launcher-core/src/main/resources/lumora/profiles.json`.

## 1. Profile: eine Minecraft-Version pro Profil

Jedes Profil hat einen eigenen Spielordner (`instances/<version>`). Alle 18 Profile bringen die Lumora-Oberfläche
mit (Hauptmenü, Lumora-Hub, HUD-Editor, Widgets, Zoom, Schlaganimationen, Addon-API).

| Profil | Loader | Java | Fabric API | Sodium | Protokoll | Lumora-Mod |
|---|---|---|---|---|---|---|
| `26.3` | fabric | 25 (`java-runtime-epsilon`) | 0.164.0+26.3 | mc26.3-0.9.2-fabric | ViaFabricPlus 5.1.2 | `lumora-client-mc26.3.jar` |
| `26.2` | fabric | 25 (`java-runtime-epsilon`) | 0.161.0+26.2 | mc26.2-0.9.2-fabric | ViaFabricPlus 5.0.3 | `lumora-client-mc26.2.jar` |
| `26.1.2` (Standard) | fabric | 25 (`java-runtime-epsilon`) | 0.155.3+26.1.2 | 0.9.2+mc26.1.2 | ViaFabricPlus 4.5.6 | `lumora-client-mc26.1.2.jar` |
| `26.1.1` | fabric | 25 (`java-runtime-epsilon`) | 0.155.3+26.1.2 | mc26.1.1-0.8.9-fabric | ViaFabricPlus 4.5.6 | `lumora-client-mc26.1.2.jar` |
| `26.1` | fabric | 25 (`java-runtime-epsilon`) | 0.155.3+26.1.2 | mc26.1.1-0.8.9-fabric | ViaFabricPlus 4.5.6 | `lumora-client-mc26.1.2.jar` |
| `1.21.11` | fabric | 21 (`java-runtime-delta`) | 0.141.6+1.21.11 | mc1.21.11-0.8.14-fabric | ViaFabricPlus 4.4.15 | `lumora-client-mc1.21.11.jar` |
| `1.21.8` | fabric | 21 (`java-runtime-delta`) | 0.136.1+1.21.8 | mc1.21.8-0.7.3-fabric | ViaFabricPlus 4.2.5 | `lumora-client-mc1.21.8.jar` |
| `1.21.4` | fabric | 21 (`java-runtime-delta`) | 0.119.4+1.21.4 | mc1.21.4-0.6.13-fabric | ViaFabricPlus 4.0.5-BACKPORT | `lumora-client-mc1.21.4.jar` |
| `1.21` | fabric | 21 (`java-runtime-delta`) | 0.102.0+1.21 | mc1.21.1-0.6.13-fabric | ViaFabricPlus 3.4.9 | `lumora-client-mc1.21.jar` |
| `1.20.6` | fabric | 21 (`java-runtime-delta`) | 0.100.8+1.20.6 | mc1.20.6-0.5.11 | ViaFabricPlus 3.3.2 | `lumora-client-mc1.20.6.jar` |
| `1.20.4` | fabric | **21** (statt 17) | 0.97.3+1.20.4 | mc1.20.4-0.5.8 | ViaFabricPlus 3.1.1 | `lumora-client-mc1.20.4.jar` |
| `1.20.1` | fabric | **21** (statt 17) | 0.92.12+1.20.1 | mc1.20.1-0.5.13-fabric | ViaFabricPlus 2.8.7 | `lumora-client-mc1.20.1.jar` |
| `1.19.4` | fabric | **21** (statt 17) | 0.87.2+1.19.4 | mc1.19.4-0.4.10 | ViaFabricPlus 2.7.5 | `lumora-client-mc1.19.4.jar` |
| `1.18.2` | fabric | **21** (statt 17) | 0.77.0+1.18.2 | mc1.18.2-0.4.1 | ViaFabric 0.4.19+111-main | `lumora-client-mc1.18.2.jar` |
| `1.17.1` | fabric | **21** (statt 16) | 0.46.1+1.17 | mc1.17.1-0.3.4 | ViaFabric 0.4.19+111-main | `lumora-client-mc1.17.1.jar` |
| `1.16.5` | fabric | **21** (statt 8) | 0.42.0+1.16 | mc1.16.5-0.2.0 | ViaFabric 0.4.19+111-main | `lumora-client-mc1.16.5.jar` |
| `1.12.2` | legacy-fabric | **21** (statt 8) | Legacy Fabric API 1.13.5 | – | ViaFabric 0.4.18+108-legacy | `lumora-client-mc1.12.2.jar` |
| `1.8.9` | legacy-fabric | **21** (statt 8) | Legacy Fabric API 1.13.5 | – | ViaFabric 0.4.18+108-legacy | `lumora-client-mc1.8.9.jar` |

**Java 21 für ältere Versionen:** Die Lumora-API (und damit jede Lumora-Mod) ist für Java 21 gebaut. Versionen,
für die Mojang ein älteres Java vorsieht, startet das Profil deshalb mit der Mojang-Runtime `java-runtime-delta`
(Feld `javaComponent`). Für 1.8.9 wurde vorab mit einem Probe-Mod geprüft, dass Minecraft, Legacy Fabric und die
Lumora-API auf Java 21 laufen; der Katalog-Test erzwingt die Einstellung für alle betroffenen Profile.

Optionale Erweiterungen (Mod Menu, FerriteCore, Lithium, Simple Voice Chat) gibt es je nach Verfügbarkeit
auf Modrinth; der Launcher nimmt jeweils den Build der Version. Das Feld `key` gruppiert die Builds derselben
Mod (z. B. `sodium-1.21.8` → `sodium`), der Katalog lehnt Profile mit zwei Builds derselben Mod ab.

## 2. Die Lumora-Mod je Version

Der Code liegt pro Version unter `client/mc<version>`; jede ältere Version ist aus der nächstneueren portiert.
Was sich zwischen den Versionsfamilien unterscheidet:

| Versionen | Grundlage | Oberfläche | Menü-Effekte | Design der Vanilla-Menüs |
|---|---|---|---|---|
| 26.1–26.3, 1.21.8, 1.21.11 | Mojang-Namen | `GuiGraphics` (26.x: `GuiGraphicsExtractor`) | GLSL-Shader (Aurora, Glas, Glow); auf 26.3 vorerst aus | Ressourcenpaket „Lumora Theme“ |
| 1.21.4, 1.21, 1.20.x | Mojang-Namen | `GuiGraphics` | ohne Shader (flache Karten) | Ressourcenpaket; 1.20.1 per Skin-Mixin |
| 1.16.5–1.19.4 | Mojang-Namen | eigene `GuiGraphics`-Schicht über `PoseStack` | ohne Shader | Skin-Mixins auf Buttons/Slider |
| 1.8.9, 1.12.2 | Legacy Fabric, Yarn-Namen | eigene Schicht für Screen, Widgets, Text, Grafik (`compat`) | ohne Shader | Skin-Mixins; Schalter `"vanillaSkin"` |

Weitere Unterschiede, die bei der Portierung wichtig waren:

- **Shader:** Die Menü-Shader sind RenderPipelines (ab 1.21.5). Davor laufen alle Menüs mit flachen Karten statt
  Glas; Layout und Funktionen sind gleich. Auf 26.3 findet Minecraft die Mod-Pipelines nicht, dort ebenso.
- **Überblendung zwischen Menüs:** ab 1.21.5 über GPU-Texturen, bis 1.21.4 über eine Framebuffer-Kopie
  (`glBlitFramebuffer`); auf 1.8.9/1.12.2 ohne FBO-Unterstützung entfällt sie.
- **Schlaganimation:** bis 1.21.1 steht der Schwung direkt in `renderArmWithItem` (Ordinals aus dem Bytecode
  geprüft), ab 1.21.2 in `swingArm`; 1.8.9/1.12.2 über `HeldItemRenderer`.
- **Zoom:** `getFov` liefert bis 1.21.1 `double`, danach `float`; die Mausempfindlichkeit ist bis 1.18.2 ein Feld.
- **Gson:** Minecraft 1.8.9/1.12.2 bringt Gson 2.2.4 mit. `lumora-core` wird gegen diese Version kompiliert,
  damit keine neuere Gson-API in den Kern gerät; die Tests laufen mit dem aktuellen Gson.
- **Lumora-Schrift (Inter):** ab 1.16.5 (TTF-Schriften); auf 1.8.9/1.12.2 nicht verfügbar.
- **Ressourcenpakete:** Ältere Fabric-API-Versionen aktivieren eingebaute Pakete nicht in bestehenden
  Instanzen. 1.16.5–1.21.4 schalten das Theme deshalb beim ersten Start mit Lumora einmal ein
  (`themePackApplied`); späteres Ausschalten bleibt erhalten.

Vor jedem Spieltest prüft `.t/check-mixins.pl` alle Mixin-Ziele gegen das echte Minecraft-JAR der Version
(Methoden, Aufrufe, Ordinals, Parametertypen).

### Lumora-API 1.1 je Version

Stand 2026-10-27. Die Lumora-API 1.1 ([API_REFERENCE.md](API_REFERENCE.md)) ist in allen 16 Modulen umgesetzt; ein
Addon nutzt dieselben Schnittstellen auf allen 18 Profilen. Unterschiede gibt es nur darin, wie die Version sie
abbildet:

| Bereich | 26.1–26.3, 1.21.11 | 1.20.6 – 1.21.8 | 1.16.5 – 1.20.4 | 1.8.9, 1.12.2 |
|---|---|---|---|---|
| Plugin-Kanäle (`network`) | Payload-Typen (ein Rohbyte-`CustomPacketPayload` je Kanal, `PayloadTypeRegistry`) | Payload-Typen | alte Fabric-Kanal-API mit Rohpuffern | Legacy-Fabric-Networking-API mit Rohpuffern; **Kanalnamen höchstens 20 Zeichen** |
| Namensschild-Symbol (`nametags`) | Zeichen der Addon-Schrift | Zeichen der Addon-Schrift | Zeichen der Addon-Schrift | keine eigenen Schriften: Text `fallback` in der nächstliegenden Chatfarbe |
| Zeile unter dem Namen (`Icon.subtitle`) | ✅ | – | – | – |
| Menüs (`screens`) | generischer `AddonViewScreen` | `AddonViewScreen` | `AddonViewScreen` | `AddonViewScreen`; Tastencodes aus `Keys` auch mit LWJGL 2 |

- **Plugin-Kanäle:** Payload-Typen gibt es ab 1.20.5; unter den Profilen sind das 1.20.6 und neuer. Auf 1.8.9 und
  1.12.2 lässt sich ein Kanal mit längerem Namen nicht anlegen: `register` wirft dann nicht, `serverSupports()` bleibt
  `false`, das Log meldet „Plugin channel … is not available in this Minecraft version“. Beispiel: Simple Voice Chat
  braucht `voicechat:request_secret` (24 Zeichen), deshalb nutzt LumoraVoice auf diesen Versionen nur Laby Voice.
- **Zeile unter dem Namen:** Nur auf 26.x (Module `mc26.1.2`, `mc26.2`, `mc26.3`, also auch die Profile 26.1 und
  26.1.1) und 1.21.11 gezeichnet. Auf den übrigen Versionen erscheint nur das Symbol, ohne Fehler.
- **Menüs:** Minecraft meldet `removed()` auch, wenn ein Dialog über dem Menü geöffnet wird. Der `AddonViewScreen`
  gibt `removed()` erst weiter, wenn die `AddonView` wirklich verschwindet (geschlossen oder vom Spiel ersetzt), damit
  der Menü-Stapel im Kern stimmt.

## 3. ViaFabricPlus und ViaFabric

- Ermöglichen Verbindungen zu Servern **anderer Protokollversionen** mit dem nativen Client der gewählten
  Version. Sie stellen keine alten Clientversionen nativ bereit.
- ViaFabricPlus gibt es ab 1.19.4; 1.16.5–1.18.2 nutzen ViaFabric, 1.8.9/1.12.2 die Legacy-Builds von ViaFabric
  (0.4.18, weil 0.4.19 mit einer unpassenden ViaVersion-Kombination abstürzt).
- Lumora zeigt die gewählte ViaFabricPlus-Version an und öffnet die Auswahl aus dem Lumora-Hub (Reflection,
  keine Compile-Abhängigkeit). Mit ViaFabric zeigt der Hub nur „ViaFabric“ an (es hat keine öffentliche API).

## 4. Sodium

Sodium-Code wird weder übernommen noch gebündelt (PolyForm Shield 1.0.0), die JARs werden unverändert von
Modrinth geladen. Für 1.8.9/1.12.2 gibt es kein Sodium.

## 5. Teststatus

Siehe [RELEASE.md](RELEASE.md#teststatus-dieses-standes).

## 6. Bekannte Einschränkungen

- Menü-Shader (Aurora, Glas, Glow) nur ab 1.21.5 und nicht auf 26.3.
- Shader-Packs (Iris) sind nicht integriert.
- Lumora-API 1.1: Plugin-Kanäle auf 1.8.9/1.12.2 nur mit Namen bis 20 Zeichen; Namensschild-Symbole dort nur als Text;
  die Zeile unter dem Namensschild nur auf 26.x und 1.21.11.
- Welten werden pro Version getrennt gespeichert. Eine Welt aus einer neueren Version in einer älteren zu
  öffnen (Downgrade) unterstützt Minecraft nicht.
