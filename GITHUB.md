# GitHub und Serverupdates

Vorbereitet: `.github/workflows/build.yml` baut nach jedem Push und für Pull Requests
das Plugin mit Java 25, führt Tests aus und erstellt das Ressourcenpaket.
Beide Dateien stehen anschließend als GitHub-Actions-Artefakt bereit.
Der Workflow ist noch nicht auf GitHub ausgeführt worden.

Noch nicht eingerichtet: GitHub-Repository, Upload und Deployment zum Minecraft-Server.
Die Serverübertragung hängt vom Hoster und dessen SSH/SFTP- oder Panel-Zugang ab.
Zugangsschlüssel gehören in GitHub Actions Secrets, nicht in Dateien im Repository.
Ein Plugin-Update wird erst nach einem Serverneustart aktiv; kein /reload verwenden.

Arbeitsablauf: lokal bearbeiten und testen, committen, nach GitHub pushen,
automatisch bauen/testen, anschließend nach Einrichtung auf dem Zielserver bereitstellen.
Allein das Speichern einer Datei löst keinen GitHub-Push aus.

Der lokale Server bleibt separat nutzbar. `server/`, Kontostände, Welten,
Umzugspakete (`dist/`) und lokale Werkzeuge (`.tools/`) sind durch `.gitignore` ausgeschlossen.
Der Build arbeitet ausschließlich mit Quellen, Modellen und Texturen.

Offizielle Dokumentation: https://docs.github.com/actions
