# Lager-Erweiterungen

Alles bleibt in Casino.jar. Server nach Austausch neu starten. Bestehende Lageritems bleiben erhalten.

## Mülleimer

Crafting (E = Eisenbarren, F = normales Fass, L = Lavaeimer):

```
 E 
EFE
 L 
```

Nur das so hergestellte Mülleimer-Fass löscht Items. Trichter auf das Fass ausrichten. Die reguläre Trichterübertragung zieht die Items ab; im nächsten Servertick wird der Fassinhalt dauerhaft gelöscht. Heraussaugen ist gesperrt. Rechtsklick zeigt einen Hinweis; keine manuelle Inventareinlagerung. Besitzer/Admin können den Block wieder abbauen. Normale Fässer bleiben unverändert.

Admin: `/lager mülleimer`

## Empfänger

Rezept wie Lager-Sender, aber mit Werfer (Dispenser) in der Mitte:

```
DED
RWR
DED
```

D = Diamant, E = Enderperle, R = Redstone, W = Werfer.

1. Lager-Handy mit dem gewünschten persönlichen Lager oder Teamlager verbinden.
2. Empfänger mit dem Handy schleichend rechtsklicken.
3. Kiste oder Doppelkiste auf gleicher Höhe direkt nördlich, östlich, südlich oder westlich daneben stellen. Bei mehreren Kisten gilt diese Reihenfolge.
4. Rechtsklick auf den Empfänger öffnet die Itemauswahl. Items im Spielerinventar anklicken: als Materialart auswählen, ohne sie zu verbrauchen. Oben anklicken: Auswahl entfernen.
5. Komparator: Zielmenge pro Materialart zwischen 16, 64, 256 und 1024 umschalten. Standard: 64.

Bis zu 16 Items alle 5 Sekunden werden nachgefüllt. Bereits vorhandene Items derselben Materialart in der Ausgabekiste zählen zur Zielmenge. Volle Kisten, fehlende Items und fehlender Teamzugriff stoppen die Ausgabe. Gespeicherte Itemdaten bleiben bei der Ausgabe erhalten; der Filter selbst unterscheidet nur Materialarten. Keine Auswahl bedeutet keine Ausgabe. Der Empfänger hat keinen nutzbaren Puffer und arbeitet nur in geladenen Chunks. Das Ziel-Lager muss in derselben Welt liegen. Hard-Crash-Sicherheit über Welt- und Plugin-Daten hinweg ist wie bei bestehenden Sendern keine gemeinsame Transaktion.

Admin: `/lager empfaenger`

## Kategorien

Die Kiste unten im Lager wechselt zwischen Alle, Rüstungen, Erze und Baublöcke sowie eigenen Kategorien. Die Kategorien sind gefilterte Ansichten desselben Lagers, keine getrennten Speicher. Sie gelten auch am Handy und werden am Lagerterminal gespeichert. Ein Item kann in mehreren Ansichten erscheinen, wird aber nur einmal gespeichert.

Eigene Kategorie anlegen/ändern, während man auf das Terminal schaut oder das verbundene Handy hält:

- `/lager kategorie Holz holz`
- `/lager kategorie Werkzeuge spitzhacke,schaufel,axt`
- `/lager kategorie Wertvolles diamant,smaragd,gold`
- `/lager kategorie Holz löschen`

Namen ohne Leerzeichen, maximal 24 Zeichen. Filter bis 100 Zeichen; Kommas bedeuten alternative Suchbegriffe. Deutsche Suchbegriffe und eigene Itemnamen funktionieren wie die vorhandene Suche. Maximal 24 Kategorien. Alle bleibt immer erreichbar. Teammitglieder dürfen gemeinsame Kategorien bearbeiten. Rechtsklick auf die Kategorie-Kiste zeigt die Befehle.

## Entnehmen

- Linksklick oder Rechtsklick: 1 Item.
- Shift-Linksklick: bis zu einem normalen Stack (64, bei Perlen 16, bei Werkzeugen 1).
- Shift-Klick im Spielerinventar lagert weiterhin ein.

Bedienhinweise außerdem unter `/menu` → Lager & Farmen.

Automatisiert geprüft: Kategoriefilter, deutsche Filterbegriffe, Entnahmemengen und vorhandene Tests. Noch ingame zu prüfen: Rezepte, Trichter-Mülleimer, Empfänger-Verbindung, volle/Doppelkisten, Teamwechsel, Kategorie-Wechsel mit mehreren Spielern, Chunk-Neuladen und Serverneustart.

## Hopper MK2
Rezept: Eisenblock / leer / Eisenblock; Eisenblock / Trichter / Eisenblock; leer / Eisenblock / leer. Verkürzt die normale Transferpause auf 2 statt 8 Ticks (vierfache Rate bei Vanilla-Einstellungen). Redstone deaktiviert ihn weiterhin. Normale Trichter bleiben unverändert. Nur geladene Chunks werden verarbeitet. Normale Inventartransfers bleiben für andere Plugins abbrechbar. Beim normalen Abbau bleibt das MK2-Item erhalten. Ingame noch zu prüfen: Trichterketten, Redstone, Inventarinhalte beim Abbau, Mülleimer und Sender.

## Gold- und Diamantstufen / Öfen
Hopper MK3: 5 Goldblöcke in Trichterform mit normalem Trichter in der Mitte. MK4: gleiches Rezept mit Diamantblöcken. MK2 bleibt Eisen. MK3 hat 1 Tick Transferpause, MK4 ebenfalls und einen zusätzlichen Einzelitemtransfer nach regulären Transfers zwischen Kisten/Fässern/Trichtern/Spendern/Werfern und normalen Öfen. Seitenregeln der normalen Öfen bleiben erhalten. Zusätzliche Transfers lösen abbrechbare InventoryMoveItemEvents aus. Spezialinventare und Bodenitems erhalten keinen zusätzlichen Transfer. Daher bis zu 4× / 8× / 16× bei Vanilla-Einstellungen und ausreichendem Nachschub; Serverlag und andere Plugins können begrenzen.
Ofen MK2/MK3/MK4: normaler Ofen, umgeben von 8 Eisen-/Gold-/Diamantblöcken. Papers Schmelzgeschwindigkeit wird auf 4×/8×/16× gesetzt. Brenndauer läuft weiterhin in Echtzeit, dadurch werden pro Brennstoff mehr Items verarbeitet. Normale Öfen, Räucheröfen und Schmelzöfen bleiben unverändert. Alle Rezepte direkt mit normalen Basisblöcken craftbar; keine vorherige Stufe notwendig. Alte MK2-Marker bleiben lesbar. Stufe bleibt beim normalen Abbau erhalten. Hilfetexte unter /menu → Lager & Farmen.
Noch ingame prüfen: Stufenrezepte, Abbau und Inventardrops, Redstone, Kisten/Ofen-Seiten/Trichterketten, volle Ausgaben, Brennstoff/XP, Chunk-Neuladen und Neustart.
