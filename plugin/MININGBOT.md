# MiningBot: Terminal und 3×3-Abbau

Der Bot gräbt einen geraden, drei Blöcke breiten und drei Blöcke hohen Tunnel in der Blickrichtung beim Platzieren. Es gibt keine feste Längenbegrenzung. Die unterste Abbauebene liegt auf Höhe der Basisschiene; der Boden darunter bleibt stehen.

Pro Abbauschritt bearbeitet er den mittleren Block und entfernt gleichzeitig die acht Nachbarn der 3×3-Schicht. Sound, Partikel, Geschwindigkeit und einmaliger Werkzeugverschleiß richten sich nach dem mittleren Block. Ist die Mitte bereits frei, übernimmt ein verbleibender fester Block diese Rolle. Alle festen Blöcke werden vorab auf Schutz, geeignetes Werkzeug und vollständigen Lagerplatz für ihre Drops geprüft; bei einem Hindernis bleibt die ganze Schicht stehen. Für die Ausbeute jedes Nachbarn wird dessen passendes Werkzeug samt Verzauberungen verwendet. Nachfallende Blöcke werden in weiteren gemeinsamen Schritten geräumt.

## Rezept

| Eisenschaufel | Kiste | Eisenspitzhacke |
| --- | --- | --- |
| leer | Ofen | leer |
| leer | Lore | leer |

Die Werkzeuge im Rezept sind Baumaterial. Die beiden nutzbaren Werkzeugplätze beginnen leer.

## Platzieren und öffnen

- Das MiningBot-Item mit Rechtsklick auf die Oberseite eines vollen Bodenblocks setzen. Zwei freie Blöcke darüber sind erforderlich.
- Eine Antriebsschiene wird automatisch zur festen Basis. Es müssen keine Schienen vorbereitet werden.
- Ein kleines Terminal erscheint an der Basis, auch bei bestehenden Bots. Rechtsklick auf Terminal oder Basisschiene öffnet das Bot-Menü. Grün startet; Rot teleportiert den Bot sofort an die Basis, entlädt und lässt ihn dort stehen. Der manuelle Rückruf funktioniert auch aus einem inzwischen ungeladenen Bot-Chunk, wenn die Basis geladen ist.
- Der Rechtsklick am Bot öffnet das 27-Slot-Menü. Ofen links: Brennstoff; goldene Spitzhacke und Schaufel: Werkzeugkategorien mit je einem echten Platz darunter; Kiste rechts: Bot-Lager.
- Items im eigenen Inventar anklicken, um sie in das gewählte Fach einzulagern. Werkzeuge können auch über den Cursor auf den zugehörigen Platz gelegt werden. Ein Klick auf eingelagerte Items nimmt einen Stapel heraus, Rechtsklick ein Item. Ziehen ist gesperrt.
- Der Ofen öffnet 27 Brennstoffplätze und akzeptiert Ofenbrennstoffe. Das Bot-Lager hat 27 normale Inventarplätze.
- Besitzer, aktuelle Mitglieder seines Lagerteams und Admins dürfen die Menüs bedienen.

## Entladen

Eine normale Kiste direkt nördlich, östlich, südlich oder westlich neben die Basisschiene auf gleicher Höhe stellen. Bei mehreren Kisten gilt diese Reihenfolge; bei einer Doppelkiste wird zunächst nur die direkt angrenzende Hälfte verwendet. Verschlossene Kisten werden nicht verwendet.

Im Bot-Lager startet der Trichter unten das Entladen beziehungsweise den Rückruf. Passt die nächste Blockausbeute nicht mehr vollständig ins Lager, fährt der Bot automatisch zurück, entlädt und setzt seine Arbeit fort. Er fährt dabei den bestehenden Tunnel erneut ab. Pro halber Sekunde wird bis zu ein Stapel übertragen. Fehlende oder volle Kisten lassen alle übrigen Items im Bot; er wartet an der Basis. Rückfahrt und Entladen benötigen keinen Brennstoff.

## Werkzeuge, Energie und Hindernisse

Glow Lichen (Leuchtflechten) wird mit der Spitzhacke mit entfernt und blockiert den Abbau nicht. Die Drops entsprechen dem verwendeten Werkzeug; es wird kein zusätzlicher Flechten-Drop erfunden. Automatische Rückfahrten bei vollem Lager bleiben schrittweise; manuelle Rückrufe über Stop oder den Lager-Trichter erfolgen sofort.

Der Bot wählt anhand der Minecraft-Blocktags Spitzhacke oder Schaufel und prüft die erforderliche Werkzeugstufe. Verzauberungen beeinflussen Drops, Haltbarkeit und Abbaugeschwindigkeit. Werkzeuge verschleißen und können zerbrechen. Ohne passendes Werkzeug oder Brennstoff pausiert der Bot; nach Nachfüllen arbeitet er weiter. Holz und andere Blöcke, die weder Spitzhacke noch Schaufel zugeordnet sind, halten ihn an. Container, Block-Entities, Betten, unzerstörbare Blöcke und registrierte Bot-Basen werden nicht abgebaut. Abbau löst ein abbrechbares BlockBreakEvent im Namen des startenden Spielers aus; Erfahrung wird nicht erzeugt.

Ein gemeinsamer Abbauschritt mit bis zu neun Blöcken kostet eine Energieeinheit. Kohle/Holzkohle liefern 64, Kohleblock 640, Lavaeimer 800, Lohenrute 96, getrockneter Seetangblock 160, Stock/Bambus 4 und andere Ofenbrennstoffe 12 Einheiten. Leere Lavaeimer landen im Bot-Lager. Es gelten diese Plugin-Werte, nicht die Ofen-Brennzeiten.

Der Bot ist gegen Schaden, Feuer und Fahrzeugkollisionen geschützt. Flüssigkeiten werden durchquert, nicht entfernt: Der Tunnel kann also geflutet bleiben. Sand und Kies werden nach dem Fallen erneut geprüft und mit der Schaufel abgebaut. Die Basisschiene ist gegen Flüssigkeitsfluss und fallende Blöcke geschützt. Die Rückfahrt erfolgt schrittweise entlang der gespeicherten Route, auch durch nachträglich verschüttete Stellen.

Es werden keine Chunks erzwungen geladen. Basis und benötigte Arbeitsbereiche müssen geladen sein; ungeladene Bereiche pausieren die Arbeit. Für den Abbau muss der Spieler, der Start gedrückt hat, online, in derselben Welt und weiterhin zugriffsberechtigt sein. Rückruf und Entladen funktionieren unabhängig davon.

## Abbauen, Speicherung und Testen

Den gestoppten, vollständig geleerten Bot (einschließlich Werkzeugen und Brennstoff) an der Basis als Besitzer schleichend rechtsklicken. Er wird als Item zurückgegeben und seine Schiene samt Terminal entfernt. Die Schiene und ihr Bodenblock sind gegen manuelles Abbauen, Kolben und Explosionen geschützt, solange der Bot dort registriert ist.

Die Datei `plugins/Casino/miningbots/<Bot-UUID>.yml` speichert Basis, Besitzer, vollständige Itemdaten, Position entlang der Route, Arbeitszustand und Restenergie. Ein Abbaujournal verhindert eine zweite Belohnung beim Wiederholen eines noch nicht abgeschlossenen Abbauschritts. Welt- und Inventarspeicherung sind keine gemeinsame Transaktion; ein harter Serverabsturz kann trotzdem Abweichungen zur zuletzt gespeicherten Welt verursachen. Alte Basis-Dateien werden weiter eingelesen. Anzeigen werden beim Chunkladen rekonstruiert. Den kompletten Casino-Datenordner beim Serverumzug mitnehmen.

Admin-Test: `/miningbot give` gibt das neue funktionale Basis-Item. Alte `/miningbot design`-Entwürfe haben weiterhin keine Menüs und können mit `/miningbot entfernen` entfernt werden. Neu craften oder das Give-Item verwenden.

Live-Prüfung: Platzieren in vier Richtungen, Terminal, Start/Rückruf, gemischter Stein-Erde-Tunnel, Werkzeugverschleiß, Brennstoffmangel, Lava/Wasser, nachfallender Sand/Kies, Schutzgebiete, volle/fehlende Basiskiste, automatische Rückfahrt und Fortsetzen, Chunkwechsel und Neustart während des Abbaus. Kein Resourcepack-Update erforderlich. Automatisierte Tests ersetzen diesen Ingame-Test nicht.
