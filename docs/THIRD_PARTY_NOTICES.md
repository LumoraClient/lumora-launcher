# Third-Party Notices – Lumora Client

Lumora Client (Launcher und Begleit-Mod) © Lumora Client contributors, lizenziert unter der
**GNU General Public License v3.0 or later** (siehe `LICENSE-GPL-3.0.txt`).

**Hinweis:** Dies ist eine technische Zusammenstellung der Lizenzen, keine Rechtsberatung. Vor einer
öffentlichen Verteilung sollte eine rechtliche Prüfung erfolgen, insbesondere zur PolyForm-Shield-Lizenz
von Sodium und zu den Markenrichtlinien von Mojang/Microsoft.

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
| Lumora Social Server `LumoraSocialServer/lumora-social-server.jar` (eigener Code mit eingebettetem **Gson 2.14.0** und Error Prone Annotations 2.48.0, beide Apache-2.0) | 0.1.0 | GPL-3.0-or-later (eigener Code), Apache-2.0 (Gson) | Lumora Client contributors; Google LLC | `licenses/Apache-2.0.txt` |
| ↳ darin: Schrift **Inter Medium** (TTF) im eingebauten Paket „Lumora Schrift“ | 4.1 | **SIL Open Font License 1.1** | The Inter Project Authors (Rasmus Andersson) | `resourcepacks/lumora_font/OFL-Inter.txt` · Quelle: github.com/rsms/inter/releases/tag/v4.1, Inter-4.1.zip SHA-256 `9883fdd4…b11e`, Inter-Medium.ttf SHA-256 `97ad806f…a872` |
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
| Minecraft: Java Edition (Client, Assets, Bibliotheken) | 26.1.2, 1.21.11, 1.21.8 | proprietär, [Minecraft EULA](https://www.minecraft.net/eula) | Mojang AB / Microsoft | piston-data.mojang.com, libraries.minecraft.net, resources.download.minecraft.net |
| Java-Laufzeit für das Spiel (`java-runtime-epsilon` bzw. `java-runtime-delta`) | 25 bzw. 21 | OpenJDK-Build, Lizenztexte liegen der Runtime unter `legal/` bei | von Mojang bereitgestellt | launchermeta.mojang.com / piston-meta.mojang.com |
| Fabric Loader + Bibliotheken (ASM, Sponge Mixin) | 0.19.5 | Apache-2.0 (Loader, Mixin: MIT; ASM: BSD-3-Clause) | FabricMC u. a. | maven.fabricmc.net |
| Fabric API | 0.155.3+26.1.2, 0.141.6+1.21.11, 0.136.1+1.21.8 | Apache-2.0 | FabricMC | cdn.modrinth.com |
| Sodium | 0.9.2+mc26.1.2, 0.8.14+mc1.21.11, 0.7.3+mc1.21.8 | **PolyForm Shield 1.0.0** | JellySquid (jellysquid3), CaffeineMC und Mitwirkende | cdn.modrinth.com · Quellcode: github.com/CaffeineMC/sodium |
| ViaFabricPlus | 4.5.6, 4.4.15, 4.2.5 | **GPL-3.0** | Florian Reuth (EnZaXD), RK_01 und Mitwirkende | cdn.modrinth.com · Quellcode: github.com/ViaVersion/ViaFabricPlus |
| Mod Menu (optional) | 18.0.2, 17.0.1, 15.0.2 | MIT | TerraformersMC | cdn.modrinth.com |
| FerriteCore (optional) | 9.0.0, 8.2.0, 8.0.4 (fabric) | MIT | malte0811 | cdn.modrinth.com |
| Lithium (optional) | 0.24.7+mc26.1.2, 0.21.4+mc1.21.11, 0.18.1+mc1.21.8 | LGPL-3.0-only | CaffeineMC | cdn.modrinth.com |
| Simple Voice Chat (optional) | 2.6.22 (je Version eigener Build) | All Rights Reserved (laut Modrinth) – daher **nur** Download durch den Nutzer, keine Weitergabe | henkelmax (Max Henkel) | cdn.modrinth.com · github.com/henkelmax/simple-voice-chat |
| Entity Model Features / Entity Texture Features (für Fresh Animations) | 3.3.12 / 7.2.6 | LGPL-3.0-only | Traben | cdn.modrinth.com |
| Nuit (Himmel für Dramatic Skys) | 1.0.0-beta | laut Modrinth | Nuit-Autoren | cdn.modrinth.com |
| Ressourcenpakete: Better Leaves (MIT, Motschen), Dramatic Skys (ARR, thebaum64), Enhanced Audio (ARR, Frawzy), Even Better Enchants (Apache-2.0, Mythitorium), Fresh Animations (Nutzungsbedingungen, FreshLX), Fresh Food (ARR, Cyber u. a.), Icons (ARR, WeNAN Studios), Enhanced Boss Bars (ARR, Naku) | je Version passend | wie angegeben | jeweilige Autoren | cdn.modrinth.com – **nur Download beim Nutzer**, keine Weitergabe, unverändert und SHA-512-geprüft |
| Vom Nutzer im Mod-Browser gewählte Mods/Pakete | – | Lizenz des jeweiligen Projekts | jeweilige Autoren | cdn.modrinth.com (SHA-512 aus der Modrinth-API) |

Alle in den Fremd-JARs enthaltenen Lizenz- und Copyright-Hinweise bleiben unverändert. Die JARs werden
bitgenau so verwendet, wie sie veröffentlicht wurden (SHA-512-Prüfung).

## C. Begründung der Distributionsform

- **Sodium (PolyForm Shield 1.0.0)** erlaubt Nutzung und Weitergabe nur unter Bedingungen, darunter eine
  Wettbewerbsklausel. Ob ein eigener Client, der Sodium bündelt, als „konkurrierendes Produkt“ gilt, ist
  ohne rechtliche Prüfung nicht sicher zu beantworten. **Deshalb wird Sodium nicht gebündelt** und
  sein Code nicht übernommen oder portiert, sondern unverändert von Modrinth geladen.
- **ViaFabricPlus (GPL-3.0)** dürfte unter Beachtung der GPL-Pflichten
  (Lizenztext, Quellcode-Angebot) weiterverteilt werden. Aus Konsistenzgründen und damit Updates aus der
  Originalquelle kommen, wird es zur Laufzeit geladen. Der Lumora-Mod linkt nicht dagegen (Zugriff
  per Reflection auf öffentliche APIs) und steht ohnehin unter der GPL-3.0-or-later.
- **Minecraft** wird nie mitgeliefert. Der Launcher lädt es ausschließlich von Mojang. Das Spielen setzt
  eine eigene, gekaufte Lizenz voraus, die über die Microsoft-Anmeldung und die Entitlement-Prüfung
  kontrolliert wird.
- Lumora ist ein inoffizielles Projekt und steht in keiner Verbindung zu Mojang, Microsoft, CaffeineMC,
  xCollateral, ViaVersion oder LabyMedia.
