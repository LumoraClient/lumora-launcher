Lumora Client - Kurzanleitung (Windows 10/11, 64 Bit)
=====================================================

1. Entpacken (ZIP) oder Setup ausführen.
2. LumoraLauncher.exe per Doppelklick starten.
   Windows SmartScreen: Die EXE ist NICHT code-signiert. Beim ersten Start
   ggf. "Weitere Informationen" -> "Trotzdem ausführen" wählen. Prüfe vorher
   die SHA-256-Prüfsumme mit SHA256SUMS.txt:
       certutil -hashfile LumoraLauncher\LumoraLauncher.exe SHA256
3. Version wählen (1.8.9 bis 26.3, Standard 26.1.2). Jede Version hat einen
   eigenen Spielordner und die Lumora-Oberfläche.
4. Auf "Spielen" klicken.
   - Beim ersten Mal lädt der Launcher Minecraft, Fabric Loader, die
     Java-Laufzeit von Mojang und die Mods (je nach Version einige hundert MB).
     Jede Datei wird per Prüfsumme kontrolliert.
   - Danach öffnet sich die Microsoft-Anmeldeseite im Browser. Mit dem
     Microsoft-Konto anmelden, dem Minecraft: Java Edition gehört - der
     Launcher übernimmt die Anmeldung danach automatisch. Das Passwort wird
     nur bei Microsoft eingegeben. (Alternative im Anmeldefenster:
     "Mit Code anmelden".)
5. Ab dann startet "Spielen" das Spiel direkt.

Voraussetzungen
- Eine gekaufte Minecraft: Java Edition.
- Internet beim ersten Start.
- Für die Microsoft-Anmeldung muss in "Einstellungen" eine Azure-Client-ID
  hinterlegt sein, die von Mojang für die Minecraft-API freigeschaltet ist
  (Verteiler tragen sie per LUMORA_MS_CLIENT_ID oder in den Einstellungen ein).
  Ohne diese ID ist keine Anmeldung möglich - siehe docs/BUILD.md.

Freunde, Gruppen und Chat (Lumora Social)
- Im Launcher unter "Freunde", im Spiel mit der Taste U oder "Freunde" im
  Haupt- und ESC-Menü.
- Braucht einen Lumora-Social-Server, den du oder jemand aus deiner Runde
  betreibt: Ordner "LumoraSocialServer", start-server.bat ausführen (nutzt die
  Java-Laufzeit des Launchers). Im lokalen Netz reicht die IP-Adresse dieses
  PCs, im Internet ist wss:// (TLS) nötig - siehe LumoraSocialServer\SOCIAL.md.
- Anmeldung über das Minecraft-Konto (offizieller Mojang-Sitzungsdienst), kein
  Passwort. Nachrichten liegen im Klartext auf dem Server.

Daten & Logs
- Einstellungen, Logs, Spieldaten: %APPDATA%\LumoraClient
- Launcher-Log:  %APPDATA%\LumoraClient\logs\launcher.log
- Spiel-Ausgabe: %APPDATA%\LumoraClient\instances\<profil>\logs\
- Zugangstoken werden nie in Logs geschrieben und unter Windows mit DPAPI
  verschlüsselt gespeichert.

Lizenzen: siehe Ordner "licenses". Dieses Projekt ist nicht von Mojang,
Microsoft, CaffeineMC, xCollateral, ViaVersion oder LabyMedia autorisiert.
