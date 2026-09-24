# Casino als Paper-Plugin

Ziel: Minecraft Java **26.3**, Paper **26.3 Build 37 (Alpha)**, Java **25**.
Der tatsächliche Paper-Build wurde über die offizielle Downloads-API geprüft. Alpha bedeutet, dass Fehler in der Serverbasis möglich sind; zuerst in der vorbereiteten separaten Testwelt testen.

## Lokal starten

Ein lokaler Server ist im benachbarten Ordner `server` vorbereitet. Er bindet nur an `127.0.0.1`, ist also für diesen PC vorgesehen. Die bestehende Modrinth-Welt wurde nicht geändert.

1. Minecraft-EULA unter https://aka.ms/MinecraftEULA lesen. Bei Zustimmung in `server/eula.txt` den Wert auf `eula=true` setzen.
2. PowerShell im Projekt öffnen: `powershell -ExecutionPolicy Bypass -File .\plugin\start-server.ps1`.
3. Beim ersten Start lädt Paper weitere offizielle Minecraft-Dateien herunter und erzeugt eine neue Welt.
4. Minecraft Java 26.3 öffnen → Mehrspieler → Direkt verbinden → `localhost`.
5. In der Serverkonsole `op DEIN_SPIELERNAME` eingeben.
6. Im Spiel `/casino give`, dann das Item auf einem freien Boden platzieren.
7. Zum sicheren Beenden `stop` in der Serverkonsole eingeben.

## Funktionen

- `/casino konto`: persönliches, dauerhaft gespeichertes Guthaben anzeigen.
- `/casino give`: ein Automaten-Item erhalten (Operator oder `casino.admin`).
- Aufstellen durch Rechtsklick auf einen Boden mit zwei freien Blöcken darüber.
- Abbauen durch Linksklick: in Survival ein Item-Drop, in Creative kein Drop.
- Rechtsklick auf den Automaten öffnet ein persönliches 3×9-Kistenmenü.
- Links (Slot 11) Kupferbarren, Eisenbarren, Goldbarren, Diamanten oder Smaragde hineinlegen.
- Rechts (Slot 15) erscheint Papier: 1 Eisenbarren = 5€, 2 = 10€, 64 = 320€. Der Betrag steht im Hover-Namen; Links- oder Rechtsklick tauscht den gesamten Eingabestapel. Das Papier bleibt nicht entnehmbar.
- Rahmen und Knöpfe können nicht herausgenommen werden. Grün ist noch ohne Funktion; Rot schließt das Menü.
- **Eisen-Umtausch aktiv:** Der Eingabestapel wird verbraucht und der Betrag dauerhaft gespeichert. Bei Speicherfehlern oder Kontoüberlauf bleibt das Eisen erhalten. Für andere Ressourcen erscheint „Noch kein Wechselkurs“.
- Beim Schließen, Verlassen, Abbauen des Automaten und regulären Serverstopp werden Eingaben zurückgegeben. Bei vollem Inventar fallen sie beim Spieler auf den Boden.
- Jeder Spieler hat sein eigenes Menü. Konten liegen nach UUID in `plugins/Casino/accounts.yml`; Automaten bleiben als markierte Entities in der Welt gespeichert.
- Ein hart beendeter Server kann wie andere ungespeicherte Inventaraktionen offene Menüeingaben verlieren. Der Prototyp ist noch nicht für Echtgeld oder produktive Wirtschaft gedacht.

## Umzug bestehender Daten

Die alten Datapack-Dateien wurden aus dem Projekt entfernt. Falls eine Welt noch eine separate Kopie des Casino-Datapacks enthält, muss sie deaktiviert werden, bevor das Plugin alte Automaten übernimmt. Eine frühere Ordnerverknüpfung auf den gelöschten Projektordner `datapack` hat kein Ziel mehr.

Für den ersten Test die neue Serverwelt verwenden. Eine Übernahme der bestehenden Modrinth-Welt ist ein separater Schritt: nur mit einer Kopie bei beendetem Spiel und Server arbeiten, andere Datapacks und serverabhängige Mods auf Paper-Kompatibilität prüfen. Diese Übernahme wurde noch nicht ausgeführt.

- Wenn das Scoreboard `casino_balance` in der übernommenen Welt vorhanden ist, wird das Guthaben beim ersten Beitritt/ersten Kontoaufruf anhand des Spielernamens einmalig in die UUID-Kontodatei importiert. Bereits vorhandene Plugin-Konten werden nicht überschrieben.
- `/casino migrate` übernimmt geladene Datapack-Automaten im Umkreis von 8 Blöcken. Andere Entities werden nicht angefasst; wiederholte Aufrufe erzeugen keine Duplikate.
- Alte Datapack-Items im Inventar sind keine Plugin-Items. Für den Test `/casino give` verwenden; alte Items nicht erneut platzieren.
- Das Plugin lässt das alte Scoreboard bestehen. Konten werden danach unabhängig geführt; Rückwechsel synchronisiert Guthaben nicht automatisch zurück.

## Entwickeln und aktualisieren

`powershell -ExecutionPolicy Bypass -File .\plugin\build.ps1` baut das Plugin und führt die Tests aus.
Ergebnis: `plugin/target/Casino-0.2.0.jar`.
Zum Aktualisieren Server mit `stop` beenden, die JAR nach `server/plugins` kopieren und neu starten. Für Plugin-Updates kein Minecraft-`/reload` verwenden.

## Manueller Test

1. Platzieren, rechtsklicken, jeden der fünf Rohstoffe einzeln eingeben; jeweils Papier sichtbar.
2. Holz, Netherite, Papier und Automaten-Item ablehnen lassen.
3. Shift-Klick, Rechtsklick-Teilstapel und Ziehen prüfen; keine Items duplizieren.
4. Papier und Rahmen mit Shift-Klick, Zahlentasten, Offhand-Tausch, Doppelklick und Creative-Mittelklick testen: nicht entnehmbar.
5. Menü schließen, ausloggen, weggehen und Automaten während geöffnetem Menü abbauen: Eingaben zurückerhalten.
6. Survival-Abbau: genau ein Item. Creative-Abbau: kein Drop. Wiederaufstellen funktioniert.
7. Server stoppen/starten: Automat und Konto erhalten; Rechtsklick funktioniert.
8. Zwei Spieler öffnen denselben Automaten: Eingaben bleiben getrennt.

Automatisiert geprüft werden Kompilierung und erlaubte Eingaberessourcen. Die GUI und die Entity-Ereignisse benötigen zusätzlich den Ingame-Test.

## Eisen-Umtausch testen

1. Kontostand mit /casino konto merken.
2. Einen Eisenbarren einlegen: Hover zeigt 5€. Zweiten ergänzen: 10€.
3. Papier anklicken: Eisen verschwindet, Konto steigt um 10€. Nochmals klicken: keine weitere Gutschrift.
4. Eisen einlegen und ohne Tausch schließen: Eisen wird zurückgegeben.
5. Server mit stop beenden und neu starten: Guthaben bleibt erhalten.

## Aktuelle Wechselkurse (ersetzen die frühere Eisen-Testtabelle)

Kupferbarren: 0,50€; Eisenbarren: 2,00€; Goldbarren: 3,00€; Diamant: 5,00€; Smaragd: 5,00€; Netheritbarren: 100,00€ pro Item. Umtausch über die grüne Scheibe Bestätigen, Rückgabe über Abbrechen. Papier zeigt den Betrag ohne Beschreibung.

Konten speichern ab jetzt ganze Cent als long mit currency-unit: cents. Bestehende Euro-Konten werden beim ersten Start einmalig mit 100 multipliziert und vorher als accounts-before-cents-UUID.yml gesichert. Keine alte Plugin-Version auf die Cent-Kontodatei anwenden. Zukünftige Spins: mindestens 10 Cent, höchstens 1000 Cent; noch kein Spielautomat implementiert.

## Spielautomat: Menü-Prototyp

Öffnen: /casino spielautomat. 6×9 Slots wie in der Referenz: grüne Wolle Slot 12, Walzen 14–16, Papier 19, grüne/rote Scheibe 27/29, Konto 40, Zurück 43 (Slots ab 0). Einsatz: 0,10–10,00€ in 0,10€-Schritten. Zurück schließt das direkt per Befehl geöffnete Menü. Noch kein platzierbares Spielautomaten-Item.

Spin ist ausdrücklich eine kostenlose Demo ohne Kontobuchung. Drei gleiche Symbole sollen später gewinnen; Multiplikatoren und Wahrscheinlichkeiten werden noch gemeinsam festgelegt. Die aktuellen Demo-Symbole sind Kupfer, Eisen, Gold, Diamant und Smaragd. Während der Animation sind weitere Spins und Einsatzänderungen gesperrt; Schließen, Ausloggen und Plugin-Stopp brechen sie ab.

Ingame prüfen: alle Positionen, beide Einsatzgrenzen, Kontostand, Walzenstopps nacheinander, wiederholtes Klicken, Schließen während Spin, zwei Spieler gleichzeitig. Dekorationsitems dürfen sich auch per Zahlentasten/Shift-Klick/Ziehen nicht entnehmen lassen. Konto muss vor und nach dem Demo-Spin unverändert sein.

## Aktuell: Spins mit Echtgeld-unabhängigem Casino-Guthaben

Der Demo-Stand oben ist abgelöst: Einsatz 0,10–10,00€, Abbuchung einmal pro bezahltem Spin. Ergebnisverteilung je Runde: Redstone 12,5%, Kupfer 15%, Eisen 10%, Gold 7%, Diamant 5%, Smaragd 4%, Netherit 2%, Goldblock 1%, Niete 43,5%. Multiplikatoren: 0,5 / 1,2 / 1,5 / 2 / 2,5 / 3 / 5. Drei Redstone starten kostenlos mit unverändertem Einsatz erneut, auch mehrfach. Nieten zeigen niemals drei gleiche Symbole. Auszahlung auf volle Cent kaufmännisch gerundet.

Die komplette Runde einschließlich Respins wird vor der Animation bestimmt und in einer Kontobuchung dauerhaft gespeichert: alter Stand minus Einsatz plus Auszahlung. Dies schützt vor Verlust oder Doppelbuchung durch Schließen, Disconnect oder Serverstopp. Der Kontostand zeigt deshalb schon während der Animation den abgerechneten Stand. Bei Speicherfehlern wird der Kontostand zurückgesetzt und kein Spin gestartet. Bei zu wenig Guthaben startet kein Spin.

Ingame prüfen: Kontodifferenz = Auszahlung minus Einsatz; Respins ohne weiteren Abzug; Schließen während Animation mit anschließend unverändertem abgerechnetem Konto; Gewinnsymbole und Nieten; schnelle Mehrfachklicks; Serverneustart nach Spin. Zehn automatisierte Tests bestanden, einschließlich exakter Wahrscheinlichkeitsverteilung, Cent-Rundung, Respin-Kette und Überlaufprüfung.

## Platzierbarer Spielautomat

Rezept: oben Stein / Eisenblock / leer, Mitte Stein / Goldblock / Stock, unten Stein / Diamantblock / leer. Ergebnis: ein Spielautomat (Diamantblock-Icon). Alternativ als Admin /casino give spielautomat. Auf Boden rechtsklicken zum Platzieren; Rechtsklick auf den Automaten öffnet das Spielmenü. Linksklick entfernt Anzeigen und Klickfläche und lässt genau ein wieder platzierbares Item fallen, auch in Creative. Bereits abgerechnete Spins bleiben beim Abbauen erhalten. Crafting, Survival-Verbrauch, Drop, Wiederaufstellen und Erhalt nach Serverneustart bitte im Spiel testen.
