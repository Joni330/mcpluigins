# Rucksack

- Jeder Spieler erhält kostenlos neun Plätze. Öffnen mit `/bp` oder über `/menu` → Rucksack Upgrades → Rucksack öffnen.
- Normale Inventarbedienung inklusive Shift-Klick. Der Rucksack ist persönlich, nicht mit dem Team geteilt. Er bleibt auch nach dem Tod erhalten.
- Weitere Reihen werden nacheinander mit Geld vom Hauptkonto gekauft. Standardpreise: Reihe 2 = 500 €, Reihe 3 = 1.250 €, Reihe 4 = 2.500 €, Reihe 5 = 5.000 €, Reihe 6 = 12.500 €. Die bisherigen unveränderten Standardpreise werden beim ersten Start dieses Updates automatisch angepasst; individuelle Preislisten bleiben erhalten.
- Preise stehen nach dem ersten Start unter `backpack.upgrade-euros` in `plugins/Casino/config.yml` (positive ganze Euro). Nach Änderungen Server neu starten.
- Ausbau und Abbuchung werden gemeinsam in `accounts.yml` gespeichert, inklusive Kontoverlauf.
- Inhalte stehen mit vollständigen Itemdaten unter `plugins/Casino/backpacks/<UUID>.dat`. Beim Serverumzug den gesamten Casino-Datenordner mitnehmen.
- Inhalte werden nach Inventaraktionen und beim Schließen sowie beim normalen Herunterfahren gespeichert. Ladefehler überschreiben keine vorhandene Datei; Speicherfehler werden protokolliert und sperren weitere Interaktionen bis zum erfolgreichen Speichern.
- Beim Beitritt erhält jeder Spieler eine persönliche Begrüßung mit anklickbarem `/menu`-Hinweis.

Live zu prüfen: Items mit Namen/Verzauberungen einlagern, Shift-Klick/Drag, Ausloggen/Neustart, Upgrade bei vollem Rucksack, Kauf ohne Guthaben.
