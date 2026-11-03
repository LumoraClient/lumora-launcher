# Third-Party Notices – Lumora Client

Lumora Client (Launcher und Begleit-Mod) © Lumora Client contributors, lizenziert unter der
**GNU General Public License v3.0 or later** (siehe `LICENSE-GPL-3.0.txt`).

**Hinweis:** Dies ist eine technische Zusammenstellung der Lizenzen, keine Rechtsberatung. Vor einer
öffentlichen Verteilung sollte eine rechtliche Prüfung erfolgen, insbesondere zu den Markenrichtlinien von Mojang/Microsoft.

## A. In der Distribution enthalten (weiterverteilt)

| Komponente | Version | Lizenz | Urheber | Lizenztext |
|---|---|---|---|---|
| Gson | 2.14.0 | Apache-2.0 | Google LLC | `licenses/Apache-2.0.txt` |
| Error Prone Annotations (transitiv über Gson) | 2.48.0 | Apache-2.0 | Google LLC | `licenses/Apache-2.0.txt` |
| FlatLaf | 3.7.2 | Apache-2.0 | FormDev Software GmbH | im JAR (`META-INF/LICENSE`), `licenses/Apache-2.0.txt` |
| FlatLaf Fonts Inter (Schriftart Inter) | 4.1 | SIL Open Font License 1.1 (Schrift), Apache-2.0 (Paket) | Rasmus Andersson (Inter), FormDev Software GmbH | im JAR |
| JNA, JNA Platform | 5.19.1 | Apache-2.0 **oder** LGPL-2.1+ (hier gewählt: Apache-2.0) | Timothy Wall u. a. | im JAR (`META-INF/LICENSE`) |
| OpenJDK-Laufzeit (jlink-Auszug von Amazon Corretto 25.0.3) | 25.0.3 | GPL-2.0 mit Classpath Exception | Oracle und Mitwirkende, Amazon | `runtime/legal/**` (vollständig mitgeliefert) |
| Lumora-Begleit-Mod `lumora-client-mc<version>.jar` (16 Builds, 1.8.9 bis 26.3; mit lumora-api, lumora-core und lumora-social) | 0.1.0 | GPL-3.0-or-later | Lumora Client contributors | im JAR (`LICENSE_lumora`) |
| Lumora Loader `bundled/lumora-loader.jar` (startet Minecraft ohne Mod-Loader; eigener Code) | 1.0.0 | GPL-3.0-or-later | Lumora Client contributors | im JAR (`LICENSE_lumora`) |
| ↳ darin: SpongePowered **Mixin** in der von FabricMC gepflegten Fassung (`net.fabricmc:sponge-mixin`, nur die Bibliothek, kein Fabric Loader, keine Fabric API) | 0.17.4+mixin.0.8.7 | MIT | SpongePowered, FabricMC und Mitwirkende | im JAR (`LICENSE_mixin.txt`) |
| ↳ darin: **ASM** (asm, asm-tree, asm-commons, asm-util, asm-analysis) | 9.10.1 | BSD-3-Clause | INRIA, France Telecom (OW2) | im JAR (`LICENSE_asm.txt`), `licenses/BSD-3-Clause-ASM.txt` |
| Namensliste `lumora/mappings.tiny` in `lumora-client-mc1.8.9.jar` und `-mc1.12.2.jar` (zusammengeführt aus Legacy Fabric **Intermediary** und **Yarn** 1.8.9+build.604 / 1.12.2+build.604; keine Mojang-Namen) | – | CC0-1.0 | Legacy Fabric und Mitwirkende | im JAR |
| Lumora Social Server `LumoraSocialServer/lumora-social-server.jar` (eigener Code mit eingebettetem **Gson 2.14.0** und Error Prone Annotations 2.48.0, beide Apache-2.0) | 0.1.0 | GPL-3.0-or-later (eigener Code), Apache-2.0 (Gson) | Lumora Client contributors; Google LLC | `licenses/Apache-2.0.txt` |
| ↳ darin: Schrift **Inter Medium** (TTF) im eingebauten Paket „Lumora Schrift“ | 4.1 | **SIL Open Font License 1.1** | The Inter Project Authors (Rasmus Andersson) | `resourcepacks/lumora_font/OFL-Inter.txt` · Quelle: github.com/rsms/inter/releases/tag/v4.1, Inter-4.1.zip SHA-256 `9883fdd4…b11e`, Inter-Medium.ttf SHA-256 `97ad806f…a872` |
| ↳ darin: Schrift **Outfit Medium** (TTF, Latin) im eingebauten Paket „Lumora Schrift: Outfit“ | Google Fonts | **SIL Open Font License 1.1** | The Outfit Project Authors | `resourcepacks/lumora_font_outfit/OFL-*.txt` · Quelle: cdn.jsdelivr.net/fontsource/fonts/outfit@latest/latin-500-normal.ttf (Fontsource, aus Google Fonts), Lizenz aus github.com/google/fonts/ofl/outfit, SHA-256 `b3f3cf7e…a4cb` |
| ↳ darin: Schrift **Manrope Medium** (TTF, Latin) im eingebauten Paket „Lumora Schrift: Manrope“ | Google Fonts | **SIL Open Font License 1.1** | The Manrope Project Authors | `resourcepacks/lumora_font_manrope/OFL-*.txt` · Quelle: cdn.jsdelivr.net/fontsource/fonts/manrope@latest/latin-500-normal.ttf (Fontsource, aus Google Fonts), Lizenz aus github.com/google/fonts/ofl/manrope, SHA-256 `a8e77668…2726` |
| ↳ darin: Schrift **Roboto Medium** (TTF, Latin) im eingebauten Paket „Lumora Schrift: Roboto“ | Google Fonts | **SIL Open Font License 1.1** | The Roboto Project Authors | `resourcepacks/lumora_font_roboto/OFL-*.txt` · Quelle: cdn.jsdelivr.net/fontsource/fonts/roboto@latest/latin-500-normal.ttf (Fontsource, aus Google Fonts), Lizenz aus github.com/google/fonts/ofl/roboto, SHA-256 `c609c081…8b8d` |
| ↳ darin: Schrift **Poppins Medium** (TTF, Latin) im eingebauten Paket „Lumora Schrift: Poppins“ | Google Fonts | **SIL Open Font License 1.1** | The Poppins Project Authors (Indian Type Foundry) | `resourcepacks/lumora_font_poppins/OFL-*.txt` · Quelle: cdn.jsdelivr.net/fontsource/fonts/poppins@latest/latin-500-normal.ttf (Fontsource, aus Google Fonts), Lizenz aus github.com/google/fonts/ofl/poppins, SHA-256 `6370b534…7bbf` |
| ↳ darin: Schrift **Montserrat Medium** (TTF, Latin) im eingebauten Paket „Lumora Schrift: Montserrat“ | Google Fonts | **SIL Open Font License 1.1** | The Montserrat Project Authors | `resourcepacks/lumora_font_montserrat/OFL-*.txt` · Quelle: cdn.jsdelivr.net/fontsource/fonts/montserrat@latest/latin-500-normal.ttf (Fontsource, aus Google Fonts), Lizenz aus github.com/google/fonts/ofl/montserrat, SHA-256 `ceefe865…9cc7` |
| ↳ darin: Schrift **JetBrains Mono Medium** (TTF, Latin) im eingebauten Paket „Lumora Schrift: JetBrains Mono“ | Google Fonts | **SIL Open Font License 1.1** | The JetBrains Mono Project Authors | `resourcepacks/lumora_font_jetbrainsmono/OFL-*.txt` · Quelle: cdn.jsdelivr.net/fontsource/fonts/jetbrains-mono@latest/latin-500-normal.ttf (Fontsource, aus Google Fonts), Lizenz aus github.com/google/fonts/ofl/jetbrainsmono, SHA-256 `02ec8096…a929` |
| ↳ darin: Schrift **Silkscreen Regular** (TTF, Latin) im eingebauten Paket „Lumora Schrift: Silkscreen“ | Google Fonts | **SIL Open Font License 1.1** | The Silkscreen Project Authors (Jason Kottke) | `resourcepacks/lumora_font_silkscreen/OFL-*.txt` · Quelle: cdn.jsdelivr.net/fontsource/fonts/silkscreen@latest/latin-400-normal.ttf (Fontsource, aus Google Fonts), Lizenz aus github.com/google/fonts/ofl/silkscreen, SHA-256 `b08f197a…9227` |
| ↳ darin: Lumora-Logo und Ladebildschirm | – | GPL-3.0-or-later | Lumora Client contributors | generiert von `packaging/IconGen.java` bzw. Code, keine fremden Grafiken |
| ↳ darin: Schlaganimations-Formeln (`dev.lumora.client.anim.SwingAnimation`), portiert aus LiquidBounce `SwingAnimations.kt` (Branch nextgen, Stand 2026-10-18) | – | GPL-3.0-or-later | Copyright (c) 2015–2026 CCBlueX | Quellenhinweis im Dateikopf; Lizenz wie Lumora |
| ↳ darin: Button-Stile Blackout, Hyperium, Liquid, Lunar, PvPClient, Rounded (`dev.lumora.core.extras.ButtonStyleModule`), nachgebaut nach FDPClient `features/module/modules/client/button` (Farben, Alphawerte, Animationsschritte; auf Lumoras HudGraphics neu geschrieben) | – | GPL-3.0-or-later | SkidderMC (FDPClient), auf Basis von LiquidBounce (CCBlueX) | Quellenhinweis im Dateikopf; Lizenz wie Lumora |
| ↳ darin: Block-Overlay (`dev.lumora.core.extras.BlockOverlayModule`): eigene Umsetzung, Funktionsumfang angelehnt an FDPClient `BlockOverlay.kt` und BThack-Legacy `BlockHighlight.java`; kein Code übernommen | – | GPL-3.0-or-later (Lumora) · Vorbilder: GPL-3.0 (FDPClient), MIT (BThack-Legacy, Ferra13671) | Lumora Client contributors | Quellenhinweis im Dateikopf |
| ↳ darin: Server- und Weltliste (`dev.lumora.core.browser`): eigene Umsetzung, Aufbau angelehnt an das Mehrspieler-Menü von LiquidBounce (nur Gestaltung, kein Code); Server List Ping nach der öffentlichen Protokollbeschreibung | – | GPL-3.0-or-later | Lumora Client contributors | – |
| ↳ darin: Verbindungssystem (`dev.lumora.core.connection`, Verbindungs- und Trennungsbildschirm, automatisches Neuverbinden): eigene Umsetzung, Konzept angelehnt an LiquidBounce (`MixinConnectScreen`, `MixinDisconnectedScreen`, `ServerObserver.reconnect`, nextgen, Stand 2026-11-02: Verbindungsweg im Verbindungsbildschirm, letzter Server für Neuverbinden); kein Code übernommen | – | GPL-3.0-or-later | Lumora Client contributors | – |

## B. Nicht enthalten: zur Laufzeit aus der offiziellen Quelle geladen

Diese Komponenten lädt der Launcher erst auf dem Rechner des Nutzers, direkt von der jeweiligen
offiziellen Quelle, und prüft sie per Prüfsumme. Lumora verteilt sie **nicht** weiter. Die Lizenzbedingungen
gelten unmittelbar zwischen Nutzer und Rechteinhaber.

| Komponente | Version | Lizenz | Urheber | Quelle |
|---|---|---|---|---|
| Minecraft: Java Edition (Client, Assets, Bibliotheken) | 1.8.9 bis 26.3 (18 Profile) | proprietär, [Minecraft EULA](https://www.minecraft.net/eula) | Mojang AB / Microsoft | piston-data.mojang.com, libraries.minecraft.net, resources.download.minecraft.net |
| Java-Laufzeit für das Spiel (`java-runtime-epsilon` bzw. `java-runtime-delta`) | 25 bzw. 21 | OpenJDK-Build, Lizenztexte liegen der Runtime unter `legal/` bei | von Mojang bereitgestellt | launchermeta.mojang.com / piston-meta.mojang.com |
| Ressourcenpakete: Better Leaves (MIT, Motschen), Enhanced Audio (ARR, Frawzy), Even Better Enchants (Apache-2.0, Mythitorium), Fresh Food (ARR, Cyber u. a.), Icons (ARR, WeNAN Studios), Enhanced Boss Bars (ARR, Naku) | je Version passend | wie angegeben | jeweilige Autoren | cdn.modrinth.com – **nur Download beim Nutzer**, keine Weitergabe, unverändert und SHA-512-geprüft |
| Mojangs offizielle Namensliste (`client_mappings`) für die verschleierten Versionen 1.16.5 bis 1.21.11 | je Version | Nutzungsbedingungen von Mojang (Weitergabe nicht erlaubt): wird **nur beim Nutzer** geladen und benutzt, um die Minecraft-JAR dort umzubenennen; Lumora verteilt sie nicht | Mojang AB / Microsoft | piston-data.mojang.com (SHA-1 aus dem Versions-JSON) |
| Vom Nutzer gewählte Ressourcenpakete (Entdecken) | – | Lizenz des jeweiligen Projekts | jeweilige Autoren | cdn.modrinth.com (SHA-512 aus der Modrinth-API) |

Alle in den Fremddateien enthaltenen Lizenz- und Copyright-Hinweise bleiben unverändert. Ressourcenpakete werden
bitgenau so verwendet, wie sie veröffentlicht wurden (SHA-512-Prüfung).

## C. Begründung der Distributionsform

- **Keine Fremd-Mods mehr:** Seit dem Lumora Loader lädt der Launcher weder Fabric noch Fabric-Mods (Sodium,
  ViaFabricPlus u. a.). Mitgeliefert werden nur eigener Code, Mixin (MIT) und ASM (BSD-3-Clause) mit ihren
  Lizenztexten.
- **Mojangs Namensliste** (`client_mappings`) wird nicht weitergegeben. Der Launcher lädt sie beim Nutzer von Mojang,
  der Loader benennt damit nur dort die Minecraft-JAR um; die umbenannte JAR bleibt auf dem Rechner des Nutzers.
- **Minecraft** wird nie mitgeliefert. Der Launcher lädt es ausschließlich von Mojang. Das Spielen setzt
  eine eigene, gekaufte Lizenz voraus, die über die Microsoft-Anmeldung und die Entitlement-Prüfung
  kontrolliert wird.
- Lumora ist ein inoffizielles Projekt und steht in keiner Verbindung zu Mojang, Microsoft, CaffeineMC,
  FabricMC, ViaVersion oder LabyMedia.
