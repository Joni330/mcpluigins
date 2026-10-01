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

## Chunkloader

Ein spezieller **Magnetstein** hält den Chunk seiner Platzierung (16 × 16 Blöcke, gesamte Welthöhe) über Plugin-Chunk-Tickets geladen. Rezept im Craftingfeld:

```text
Diamant     Enderperle   Diamant
Enderperle  Magnetstein  Enderperle
Diamant     Enderperle   Diamant
```

Ein gewöhnlicher Magnetstein lädt keine Chunks. Rezept und Anleitung sind unter `/menu` → **Lager & Farmen** → **Chunkloader** erreichbar; das Rezept wird auch im Rezeptbuch freigeschaltet. Admins erhalten das Item mit `/chunkloader`.

- Platzieren aktiviert den Loader sofort. Rechtsklick öffnet sein An/Aus-Menü und zeigt die Chunkkoordinaten. F3 + G zeigt die Grenzen des Chunks.
- Kein Brennstoff und keine laufenden Kosten. Der Loader bleibt ohne Spieler und bei ausgeloggtem Besitzer aktiv. Nach einem normalen Serverneustart wird der gespeicherte Zustand wiederhergestellt; während der Server ausgeschaltet ist, läuft nichts weiter.
- Besitzer und aktuelle Lagerteammitglieder dürfen das Menü bedienen; nur Besitzer oder Admin dürfen den Block abbauen. Beim normalen Abbau mit einer Spitzhacke im Überlebensmodus fällt das Chunkloader-Item wieder heraus. Explosionen und Kolben können den registrierten Block nicht entfernen/verschieben.
- Ausschalten/Abbauen entfernt nur den Bedarf dieses Loaders. Andere Loader im selben Chunk, Bots und offene Lagerfenster behalten ihre eigenen Tickets. Minecraft kann zusätzlich benachbarte Chunks für seine internen Abläufe laden.
- Öfen, Trichter und Plugin-Maschinen können weiterarbeiten. Dies simuliert keinen Spieler: Natürliches Pflanzenwachstum und Mob-Spawns unterliegen weiterhin ihren Vanilla-Bedingungen. Eigene Einstellungen des Servers oder anderer Plugins können Verarbeitung zusätzlich begrenzen.

Besitzer, Position und Aktivzustand stehen in `plugins/Casino/chunk-loaders.yml`; eine eindeutige Markierung im Weltchunk verhindert, dass ein ersetzter normaler Magnetstein als alter Loader gilt. Welt und Plugin-Daten gemeinsam sichern. Nachträglich abgebrochene Platzierungen werden zurückgenommen, entfernte Blöcke beim Prüfen aus dem Index gelöscht. Ungeladene Welten bleiben gespeichert und werden beim Laden berücksichtigt. Bei einem Speicherfehler werden die Chunkloader-Tickets freigegeben und die Loader bis zum Neustart angehalten; die Konsole nennt den Fehler.

Automatisiert geprüft: Speichern/Neustart, An/Aus, Abbau aus dem Index, fehlgeschlagene Speicherung ohne Änderung am bisherigen Stand, negativer Chunkrand, Überschneidungen, getrennte Welten und ausstehende/abgebrochene Platzierungen im Ticketbedarf. Ingame noch prüfen: Rezept, Menü/Teamrechte, Platzier-/Abbauschutz anderer Plugins, Drop, Weiterlaufen von Ofen/Trichter ohne Spieler, mehrere Loader, Neustart sowie Weltentladen/-neuladen.

## Kisten unter beliebigen Blöcken

Normale Kisten und Redstone-Kisten lassen sich per Rechtsklick unabhängig vom Block darüber öffnen: auch unter Stein, Erde, Fässern, Lagerterminals oder Mülleimern. Das gilt für Einzelkisten und für beide Seiten einer Doppelkiste, auch wenn beide Hälften überbaut sind. Es wird immer das echte Inventar mit 27 bzw. 54 Plätzen geöffnet; die Blöcke darüber bleiben unverändert. Die alte Fass-Sonderprüfung entfällt: Papers `Chest.isBlocked()` prüft bei Doppelkisten auch die andere Hälfte und konnte dadurch die freie Hälfte fälschlich für diese Ausnahme sperren.

Schleichen mit einem Item in einer Hand bleibt zum Anbauen von Trichtern/Blöcken nutzbar. Abgebrochene Interaktionen und Inventaröffnungen durch Schutzplugins werden respektiert; die normalen Kistenschlösser bleiben über den ursprünglichen Inventaranbieter wirksam. Beim Weggehen oder Entfernen einer Kistenhälfte wird das Fenster geschlossen; Klicks auf ein inzwischen ungültiges Kisteninventar werden sofort gesperrt.

Automatisiert geprüft: Einzel-/Doppelkisten und Redstone-Kisten unter verschiedenen Blöcken, Zugriff von beiden Seiten bei einem Fass nur über einer Hälfte (mit Papers kombinierter Blockade), unveränderte Abdeckung, nachträglich gesperrte Interaktion/Inventaröffnung, fremdes geöffnetes Menü, Offhand, Schleichen, Spectator, ungeladene Hälfte, Weggehen und Abbau der anderen Hälfte bei offenem Fenster. Ingame noch prüfen: Kistenanimation, Hopper-Anbau, Vanilla-Schloss, aktive Schutzplugins und gleichzeitiger Zugriff mehrerer Spieler.

## Kategorien

Die Kiste unten im Lager wechselt zwischen Alle, Rüstungen, Erze, Holz, Baublöcke, Werkzeuge, Waffen, Nahrung, Pflanzen, Redstone und Mob-Drops sowie eigenen Kategorien. Die Kategorien sind gefilterte Ansichten desselben Lagers, keine getrennten Speicher. Sie gelten auch am Handy und werden am Lagerterminal gespeichert. Ein Item kann in mehreren Ansichten erscheinen, wird aber nur einmal gespeichert. Neue Standardkategorien werden bei bestehenden Lagern einmalig ergänzt; bereits angelegte eigene Filter gleichen Namens bleiben erhalten. Danach gelöschte Kategorien bleiben auch nach einem Neustart gelöscht.

## Lagerseiten kaufen

`/menu` → **Lager erweitern**, außerdem unter **Lager & Farmen** erreichbar. Vor dem Öffnen auf das gewünschte Terminal schauen (bis zu acht Blöcke entfernt) oder das damit verbundene Lager-Handy in der Haupthand halten. Das Menü zeigt die Koordinaten und die aktuelle Größe des ausgewählten Lagers.

Ein Klick auf **Eine Seite kaufen** ergänzt **45 Lagerplätze für 200 € vom Hauptkonto**. Jede weitere Seite kostet ebenfalls 200 €. Die bisherigen zehn Seiten bleiben erhalten; es gibt keine neue kleinere Anfangsstufe. Jede Seite unterstützt weiterhin bis zu 1.024 identische Items je Platz. Die Erweiterung gehört zum Terminal und steht damit auch dessen Teammitgliedern zur Verfügung. Teammitglieder mit Lagerzugriff können ebenfalls aus ihrem eigenen Hauptkonto eine Seite für das gemeinsame Lager bezahlen.

Abbuchung, Kontoverlauf und bezahlte Seitenzahl werden gemeinsam in `accounts.yml` gespeichert. Beim Laden gleicht das Terminal seine Größe mit diesem Kaufstand ab, falls der Weltchunk vor einem Neustart noch die alte Größe gespeichert hatte. Fehlendes Guthaben, ein veraltetes Kaufmenü, verlorener Team-/Handyzugriff oder ein ersetztes Terminal verhindern den Kauf. Bei fehlgeschlagener Kontospeicherung bleiben Guthaben und Seitenzahl unverändert. Leere Terminals behalten beim normalen Abbau und erneuten Platzieren ihre Seitenzahl am gedroppten Item; Handy und Sender müssen wie bisher mit dem neu platzierten Terminal verbunden werden.

Alle Lagerfunktionen verwenden die gekaufte Größe: Seitenwechsel, Kategorien, Suche, Sortierung, Lager-Crafting, Sender und Empfänger. Neue leere Seiten werden erst beim Öffnen als Inventarfenster erzeugt. Normale Welt-/Plugin-Sicherungen sollten wie bisher zusammen erstellt werden.

Automatisiert geprüft: mehrfacher Kauf zu jeweils 200 €, Hauptkonto/Casino-Trennung, gemeinsame Erweiterungen, veraltete Kaufansichten, Speicherfehler ohne Abbuchung, Wiederherstellung aus dem Kaufstand, erweiterte Itemdaten ohne Verlust von Mengen/Metadaten, Crafting mit Zutaten auf gekauften Seiten und neue Kategorien. Ingame noch prüfen: Menübedienung mit Terminal und Handy, verlorener Zugriff, zwei gleichzeitige Betrachter, Suche/Crafting/Sender/Empfänger auf Seite 11+, Abbau/Neuplatzierung und Serverneustart.

## Eigene Kategorien

Eigene Kategorie anlegen/ändern, während man auf das Terminal schaut oder das verbundene Handy hält:

- `/lager kategorie Birkenholz birke`
- `/lager kategorie Garten schaufel,hacke`
- `/lager kategorie Wertvolles diamant,smaragd,gold`
- `/lager kategorie Birkenholz löschen`

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
