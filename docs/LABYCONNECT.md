# LabyMod-4-Addon-API und LabyConnect: Analyse

Stand: 2026-10-15. Untersucht wurden ausschließlich öffentliche, offizielle Quellen. Es wurde kein
LabyMod-Client dekompiliert, und es wurden keine internen Endpunkte ermittelt oder verwendet.

## 1. Untersuchte Quellen

| Quelle | Inhalt |
|---|---|
| github.com/LabyMod/labymod4-api-wiki (Commit-Stand 2026-10-14) | Offizielles Wiki der Addon-API (`docs/pages/addon/**`) |
| github.com/LabyMod/addon-template (Stand 2026-09-16) | Offizielle Addon-Vorlage |
| `https://maven.laby.net/api/v1/maven/release/net/labymod/labymod4/api/` | API-Artefakt, neueste Version `47.3-internal-1bf7a24b` (Javadoc-Signaturen per `javap` gelesen) |
| github.com/LabyMod (Organisation) | Liste öffentlicher Repositories. Ein LabyConnect-Repository oder eine Bibliothek gibt es nicht |

## 2. Wie Addons gebaut werden und laufen

- Die Vorlage nutzt das Gradle-Plugin `net.labymod.labygradle` (Settings-Plugin `0.9.0`) und die
  Abhängigkeit `labyApi("api")`. Gebaut wird gegen viele Minecraft-Versionen gleichzeitig
  (`net.labymod.minecraft-versions=1.8.9;…;26.1.2;26.3`), Zielniveau Java 21.
- Ein Addon ist **kein eigenständiges Programm und kein Fabric-Mod**. Es wird von LabyMod 4 geladen
  (`@AddonMain`, `AddonEnableEvent`) und erhält Zugriff über `Laby.labyAPI()`.
- Die Richtlinien des Wikis (`publishing/guidelines.md`) verbieten unter anderem Reflection und den
  Zugriff auf LabyMod-Kernpakete: *„Do not access LabyMod core packages“*.

## 3. LabyConnect in der API

Die API bietet LabyConnect als **Schnittstelle innerhalb von LabyMod** an:

| Element | Signatur (aus `api-47.3-internal-1bf7a24b.jar`) |
|---|---|
| Einstieg | `net.labymod.api.LabyAPI#labyConnect()` → `LabyConnect` |
| `net.labymod.api.labyconnect.LabyConnect` | `connect()`, `connect(String host, int port)`, `disconnect(Initiator, String)`, `forceDisconnect(Initiator, String)`, `state()`, `isAuthenticated()`, `isConnectionEstablished()`, `isConnected()`, `getSession()`, `getLastDisconnectReason()`, `configProvider()` |
| `LabyConnectSession` | Freunde (`getFriends`, `getOnlineFriends`, `removeFriend`), Anfragen (`sendFriendRequest`, `acceptFriendRequest`, `declineFriendRequest`), Chat (`getChats`, `sendChatMessage`, `sendChatFile`), `self()`, `isPremium()`, `tokenStorage()`, `sendCurrentServer(…)`, `playEmote`, `spray`, `sendCustomPayload` |
| `TokenStorage` | `getToken(Purpose, UUID)`, `hasValidToken(Purpose, UUID)` |
| `protocol.LabyConnectState` | `HELLO`, `LOGIN`, `PLAY`, `ALL`, `OFFLINE` |
| Events (`net.labymod.api.event.labymod.labyconnect…`) | `LabyConnectStateUpdateEvent`, `LabyConnectConnectEvent`, `LabyConnectDisconnectEvent`, `LabyConnectRejectAuthenticationEvent`, `LabyConnectTokenEvent`, `LabyConnectUpdateSettingEvent`, Chat-, Freundes- und Anfrage-Events (vollständige Liste: Wiki `features/events.md`, Abschnitt „LabyConnect Events“) |

### Ermittelter Verbindungsablauf (soweit öffentlich belegbar)

Aus den Typen ergibt sich das Zustandsmodell `OFFLINE → HELLO → LOGIN → PLAY` (`LabyConnectState`).
Die Anmeldung kann scheitern (`LabyConnectRejectAuthenticationEvent`), Tokens werden pro Zweck und
Spieler-UUID verwaltet (`TokenStorage`, `LabyConnectTokenEvent`). Trennungen tragen einen Auslöser und
einen Grund (`disconnect(Initiator, String)`, `getLastDisconnectReason()`).

**Nicht öffentlich dokumentiert sind:** Server-Adresse und Port, Wire-Protokoll und Paketformat,
Authentifizierungsverfahren gegenüber dem LabyConnect-Server (welcher Nachweis, ob z. B. ein
Mojang-Session-Join verwendet wird), Token-Format, Wiederverbindungsstrategie. `connect(String, int)`
zeigt nur, dass Host und Port existieren, nicht welche.

## 4. Ergebnis

1. Die **Addon-API stellt LabyConnect bereit, aber nur für Addons, die innerhalb des laufenden
   LabyMod-4-Clients ausgeführt werden.** Das API-JAR enthält ausschließlich Interfaces. Die
   Implementierung liegt im nicht öffentlichen LabyMod-Kern. Das veröffentlichte Artefakt trägt das
   Versionssuffix `-internal` und enthält keine Lizenzdatei.
2. Es gibt **keine offizielle, dokumentierte Schnittstelle**, über die ein eigenständiger Client wie
   Lumora (Fabric, ohne LabyMod) eine LabyConnect-Verbindung aufbauen könnte. Weder eine Client-Bibliothek
   noch eine Protokollspezifikation ist veröffentlicht.
3. Eine Integration würde Reverse Engineering des geschlossenen LabyMod-Kerns oder des Netzwerkprotokolls
   erfordern. Genau das schließen die Vorgaben dieses Projekts aus (keine erfundenen oder privaten
   Endpunkte, keine fremden Schlüssel oder Tokens). Die Addon-Richtlinien untersagen den Zugriff auf
   Kernpakete ebenfalls.

**Deshalb enthält Lumora keine LabyConnect-Implementierung, auch keine vorgetäuschte oder
experimentelle.** Ein Verbindungstest konnte mangels zulässiger Schnittstelle nicht durchgeführt werden.

## 5. Zulässige Wege, falls LabyConnect gebraucht wird

- **LabyMod selbst verwenden.** LabyConnect ist Teil des LabyMod-Clients. Ob und wie sich LabyMod 4 mit
  den hier verwendeten Fabric-Mods kombinieren lässt, wurde nicht geprüft und ist nicht Teil dieser Distribution.
- **Offizielle Anfrage bei LabyMedia** (Entwickler-Discord laut Wiki: `labymod.net/dc/dev`) nach einer
  Partner- oder Client-Schnittstelle. Sollte es eine dokumentierte, freigegebene API geben, kann sie als
  eigenes, abschaltbares Modul ergänzt werden. Der Launcher trennt Authentifizierungsbereiche bereits
  sauber (Microsoft/Minecraft-Sitzung in `auth/`). Eine LabyConnect-Sitzung bekäme ein eigenes Modul
  ohne Kopplung an Renderer oder ViaFabricPlus.
- Ein **LabyMod-Addon** (gebaut mit der Vorlage) kann LabyConnect-Funktionen nutzen, läuft aber nur in
  LabyMod und nicht in Lumora.
