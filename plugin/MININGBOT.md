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

Eine normale Kiste direkt nördlich, östlich, südlich oder westlich neben die Basisschiene auf gleicher Höhe stellen. Bei mehreren Kisten gilt diese Reihenfolge; bei einer Doppelkiste werden alle 54 Plätze beider Hälften verwendet. Verschlossene Kisten werden nicht verwendet.

Im Bot-Lager startet der Trichter unten das Entladen beziehungsweise den Rückruf. Passt die nächste Blockausbeute nicht mehr vollständig ins Lager, fährt der Bot automatisch zurück, entlädt und setzt seine Arbeit fort. Er fährt dabei den bestehenden Tunnel erneut ab. Pro halber Sekunde wird bis zu ein Stapel übertragen. Fehlende oder volle Kisten lassen alle übrigen Items im Bot; er wartet an der Basis. Rückfahrt und Entladen benötigen keinen Brennstoff.

## Werkzeuge, Energie und Hindernisse

### Flüssigkeitsschutz

Das Eimer-Symbol im Terminal schaltet den Flüssigkeitsschutz ein oder aus (anfangs aus). Vor dem Abbau werden die seitlichen Wände, der Boden und die Decke der aktuellen und nächsten Schicht geschlossen: offene/passierbare Stellen und Flüssigkeiten werden kostenlos durch Bruchstein ersetzt. Feste, trockene Blöcke bleiben stehen. Das erzeugt auch über Höhlenöffnungen eine geschlossene Tunnelhülle. Flüssigkeiten unmittelbar vor der nächsten Schicht erhalten eine vorläufige Bruchstein-Abdeckung, die beim späteren Vortrieb normal abgebaut wird.

Erst nach dem Abdichten werden Wasser und Lava im 3×3-Innenraum entfernt; wassergefüllte Blöcke werden entwässert, ohne ihre anderen Blockzustände zu verwerfen. Anschließend laufen Blockerneuerer und Fackelprüfung. Zusätzlich wird Flüssigkeitsfluss in die aktuelle und nächste Tunnelstrecke währenddessen unterbunden. Schutzereignisse gelten auch für Abdichten und Trockenlegen; ein geschütztes Hindernis pausiert den Bot. Container werden nicht ersetzt. Alle Einstellungen bleiben bei Neustarts erhalten.

Die Funktion behandelt den aktuellen Arbeitsbereich, nicht rückwirkend den gesamten bereits überfluteten Tunnel. Zum Ingame-Test gehören Wasser- und Lavaquellen vor, über und neben dem Bot, fließende Flüssigkeiten, Wasser in Blöcken, Höhlendurchquerung und die Kombination mit Erzadern und Fackeln.

### Blockerneuerer

Das Bruchstein-Symbol unten rechts im Terminal schaltet den Blockerneuerer ein oder aus (anfangs aus). Während der Arbeit prüft er unter seiner aktuellen Position alle drei Bodenplätze quer zur Fahrtrichtung. Luft, Höhlenluft und Leerenluft werden kostenlos mit Bruchstein gefüllt; Lagerbestand und Brennstoff werden dafür nicht benötigt. Bestehende Blöcke sowie Wasser und Lava werden nicht ersetzt. Zuerst wird der Boden ergänzt, danach läuft die normale Fackelprüfung. Die Fackelautomatik muss weiterhin separat aktiviert sein und ihren Abstand erreicht haben.

Die Einstellung bleibt bei Neustarts erhalten. Platzierungen beachten Weltgrenzen und BlockPlaceEvent-Schutz; bei gesperrter Platzierung pausiert der Bot, bis der Boden freigegeben oder der Blockerneuerer ausgeschaltet wird. Aktive Bots halten die benötigten Chunks geladen. Der separate Ersatzboden beim Erzader-Abbau bleibt bestehen.

### Erzadern abbauen

Das Diamanterz-Symbol im Terminal schaltet die Erz-Nachlese ein oder aus (anfangs aus). Nach einer freigelegten und erreichten Tunnelschicht werden jeweils drei Blöcke unten, oben, links und rechts geprüft (zwölf Prüfstellen insgesamt). Ein Fund startet eine Suche über gemeinsame Blockflächen, nicht über diagonale Ecken. Normale und Tiefenschiefer-Erze derselben Sorte zählen zusammen. Unterstützt werden Kohle, Kupfer, Eisen, Gold, Redstone, Lapis, Diamant, Smaragd, Netherquarz, Nethergolderz und antiker Schrott.

Pro Fund werden höchstens 64 Erze vorgemerkt. Die Suche bleibt in einem Bereich, der maximal acht Blöcke in jeder Richtung über die untersuchte 3×3-Tunnelfläche hinausreicht. Der aktive Bot lädt den benötigten Suchbereich vorab. Die vorgemerkten Erze werden einzeln mit der Spitzhacke abgebaut, jeweils mit einer Energieeinheit und normalem Werkzeugverschleiß. Werkzeugstufe, Verzauberungen, vollständiger Platz für Drops und Schutzplugin-Prüfungen gelten wie beim normalen Abbau.

Bei vollem Bot-Lager bleibt die Restliste gespeichert. Nach Entladen fährt der Bot bis zur Fundstelle vor und setzt dort fort. Auch ein Serverneustart oder manueller Rückruf verwirft die Liste nicht; nach einem manuellen Rückruf ist erneut Start nötig. Ausschalten pausiert die Liste und erlaubt normalen Tunnelabbau. Erneutes Einschalten setzt sie fort.

Abgebaute Erze auf Höhe des Tunnelbodens innerhalb der drei Tunnelspuren werden kostenlos durch Bruchstein ersetzt. Das gilt auch für Bodenblöcke weiter vorne entlang der geraden Tunnelroute. Für den Ersatzboden wird zusätzlich ein abbrechbares BlockPlaceEvent ausgelöst; bei gesperrter Platzierung bleibt das Erz stehen. Löcher außerhalb der Tunnelspuren werden nicht aufgefüllt. Der Ersatzboden ist im Abbaujournal enthalten.

### Fackeln

Im Terminal schaltet das Fackelsymbol unten links die automatische Beleuchtung pro Bot ein oder aus (anfangs aus). Der Bot erstellt kostenlos etwa alle acht Blöcke Vorwärtsfortschritt eine Bodenfackel, ohne Vorrat oder Itemverbrauch. Vorher sucht er im Tunnel bis drei Blöcke vor und hinter sich, über die gesamte Tunnelhöhe und seitlich bis zwei Blöcke nach vorhandenen Fackeln einschließlich Wandvarianten. Findet er eine, überspringt er die Platzierung und fährt weiter. Freier, trockener Platz und ein voller Bodenblock sind nötig; fehlender Platz stoppt den Abbau nicht. Platzierung beachtet BlockPlaceEvent-Schutz. Einstellung und letzte geprüfte Fackelposition werden gespeichert, damit der Bot beim erneuten Durchfahren keine doppelten Fackeln setzt. Fackeln im Bot-Lager werden wie andere Items entladen.

Alle normalen, Seelen- und Redstone-Fackeln, einschließlich Wandvarianten, gelten beim Abbau als freier Durchgang und werden nicht gezielt abgebaut. Minecraft kann bestehende Wandfackeln weiterhin ablösen, wenn deren tragender Block abgebaut wird.

Glow Lichen (Leuchtflechten) wird mit der Spitzhacke mit entfernt und blockiert den Abbau nicht. Die Drops entsprechen dem verwendeten Werkzeug; es wird kein zusätzlicher Flechten-Drop erfunden. Automatische Rückfahrten bei vollem Lager bleiben schrittweise; manuelle Rückrufe über Stop oder den Lager-Trichter erfolgen sofort.

Der Bot wählt anhand der Minecraft-Blocktags Spitzhacke oder Schaufel und prüft die erforderliche Werkzeugstufe. Verzauberungen beeinflussen Drops, Haltbarkeit und Abbaugeschwindigkeit. Werkzeuge verschleißen und können zerbrechen. Ohne passendes Werkzeug oder Brennstoff pausiert der Bot; nach Nachfüllen arbeitet er weiter. Holz und andere Blöcke, die weder Spitzhacke noch Schaufel zugeordnet sind, halten ihn an. Container, Block-Entities, Betten, unzerstörbare Blöcke und registrierte Bot-Basen werden nicht abgebaut. Abbau löst ein abbrechbares BlockBreakEvent im Namen des startenden Spielers aus; Erfahrung wird nicht erzeugt.

Ein gemeinsamer Abbauschritt mit bis zu neun Blöcken kostet eine Energieeinheit. Kohle/Holzkohle liefern 64, Kohleblock 640, Lavaeimer 800, Lohenrute 96, getrockneter Seetangblock 160, Stock/Bambus 4 und andere Ofenbrennstoffe 12 Einheiten. Leere Lavaeimer landen im Bot-Lager. Es gelten diese Plugin-Werte, nicht die Ofen-Brennzeiten.

Der Bot ist gegen Schaden, Feuer und Fahrzeugkollisionen geschützt. Flüssigkeiten werden durchquert, nicht entfernt: Der Tunnel kann also geflutet bleiben. Sand und Kies werden nach dem Fallen erneut geprüft und mit der Schaufel abgebaut. Die Basisschiene ist gegen Flüssigkeitsfluss und fallende Blöcke geschützt. Die Rückfahrt erfolgt schrittweise entlang der gespeicherten Route, auch durch nachträglich verschüttete Stellen.

Aktive Bots halten Basis, angrenzende Ausgabekisten und einen begrenzten Bereich um die aktuelle Arbeitsposition mit Plugin-Chunk-Tickets geladen. Mit Erzadern umfasst der Arbeitsbereich bis zu neun Chunks, zusätzlich maximal vier an der Basis. Alte Arbeitsbereiche werden freigegeben; abgeschlossene oder fehlerhafte Bots geben ihre Tickets frei. Offene Lager-Menüs teilen sich die Verwaltung, damit ihre Tickets nicht gegenseitig entfernt werden. Der Bot arbeitet auch nach dem Ausloggen und nach Serverneustarts weiter. Der gespeicherte Starter muss offline Besitzer oder aktuelles Teammitglied sein. Der Server muss laufen und darf sich bei leerem Server nicht pausieren; server.properties: pause-when-empty-seconds=-1. Lokal ist dies bereits eingestellt.

## Abbauen, Speicherung und Testen

Den gestoppten, vollständig geleerten Bot (einschließlich Werkzeugen und Brennstoff) an der Basis als Besitzer schleichend rechtsklicken. Er wird als Item zurückgegeben und seine Schiene samt Terminal entfernt. Die Schiene und ihr Bodenblock sind gegen manuelles Abbauen, Kolben und Explosionen geschützt, solange der Bot dort registriert ist.

Die Datei `plugins/Casino/miningbots/<Bot-UUID>.yml` speichert Basis, Besitzer, vollständige Itemdaten, Position entlang der Route, Arbeitszustand und Restenergie. Ein Abbaujournal verhindert eine zweite Belohnung beim Wiederholen eines noch nicht abgeschlossenen Abbauschritts. Welt- und Inventarspeicherung sind keine gemeinsame Transaktion; ein harter Serverabsturz kann trotzdem Abweichungen zur zuletzt gespeicherten Welt verursachen. Alte Basis-Dateien werden weiter eingelesen. Anzeigen werden beim Chunkladen rekonstruiert. Den kompletten Casino-Datenordner beim Serverumzug mitnehmen.

Admin-Test: `/miningbot give` gibt das neue funktionale Basis-Item. Alte `/miningbot design`-Entwürfe haben weiterhin keine Menüs und können mit `/miningbot entfernen` entfernt werden. Neu craften oder das Give-Item verwenden.

Live-Prüfung: Platzieren in vier Richtungen, Terminal, Start/Rückruf, gemischter Stein-Erde-Tunnel, Werkzeugverschleiß, Brennstoffmangel, Lava/Wasser, nachfallender Sand/Kies, Schutzgebiete, volle/fehlende Basiskiste, automatische Rückfahrt und Fortsetzen, Chunkwechsel und Neustart während des Abbaus. Kein Resourcepack-Update erforderlich. Automatisierte Tests ersetzen diesen Ingame-Test nicht.


Offline-Schutz: Automatische Blockänderungen lösen abbrechbare EntityChangeBlockEvents mit dem Bot als Entity aus. Bei online verfügbarem Starter werden zusätzlich BlockBreakEvent beziehungsweise BlockPlaceEvent ausgelöst. Schutzplugins, die ausschließlich Spielerereignisse auswerten, sind offline nicht automatisch kompatibel und müssen separat integriert bzw. geprüft werden. Container und registrierte Bot-Basen bleiben unabhängig davon geschützt.

