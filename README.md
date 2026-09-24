# Casino – Paper-Plugin

Casino-Plugin für Minecraft Java 26.3 mit Guthabenkonten, platzierbaren und abbaubaren Wechselautomaten sowie einem Kistenmenü. Das Papier zeigt den Gesamtwert der eingelegten Ressourcen; die grüne Scheibe bestätigt den Umtausch. Preise pro Item: Kupfer 0,50€, Eisen 2,00€, Gold 3,00€, Diamant/Smaragd 5,00€, Netheritbarren 100,00€. Guthaben wird in ganzen Cent gespeichert.

## Projektstruktur

- `plugin/src/`: Java-Quellcode, Plugin-Konfiguration und Tests.
- `plugin/pom.xml`: Abhängigkeiten und Build-Konfiguration.
- `plugin/build.ps1`: Plugin bauen und Tests ausführen.
- `plugin/start-server.ps1`: lokalen Testserver starten.
- `plugin/target/Casino-0.2.0.jar`: gebautes Plugin.
- `server/`: vorbereiteter Paper-Testserver mit Plugin; lokale Serverdaten bleiben hier erhalten.
- `.tools/`: Maven und Abhängigkeitscache für weitere Builds.

## Start und Bedienung

Die vollständige Anleitung zu EULA, Serverstart, Befehlen und Datenübernahme steht in [plugin/README.md](plugin/README.md).

Paper 26.3 Build 37 ist ein Alpha-Build. Das Plugin wurde erfolgreich kompiliert; der automatisierte Ressourcentest war erfolgreich. Der Ingame-Test des Plugins steht noch aus.

Die alten Datapack- und Resource-Pack-Dateien wurden beim Umzug entfernt. Eine frühere Verknüpfung aus dem Minecraft-Weltordner auf `datapack/` hat damit kein Ziel mehr. Die bestehende Minecraft-Welt wurde nicht verändert.
