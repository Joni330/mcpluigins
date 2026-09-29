# Steinbruchbot und Erzsucher

Die beiden Stufen sind im Casino-Plugin enthalten. Kein zusätzlicher Plugin- oder Resourcepack-Download nötig. Bestehende Tunnelbots bleiben erhalten. Die neue Casino.jar installieren und den Server regulär neu starten.

## Rezepte

**Steinbruchbot** (der mittlere Bot ist das echte MiningBot-Item, keine normale Ofenlore):

| Goldblock | MiningBot | Goldblock |
| --- | --- | --- |
| Diamantblock | Spitzer Tropfstein | Diamantblock |
| Smaragdblock | Diamantblock | Smaragdblock |

**Erzsucher**:

| Echoscherbe | Seelenfackel | Echoscherbe |
| --- | --- | --- |
| Diamantblock | Netherstern | Diamantblock |
| Echoscherbe | Steinbruchbot | Echoscherbe |

Ein aufgestellter Bot muss zuerst gestoppt, zurückgerufen, vollständig geleert und über sein Terminal abgebaut werden, bevor sein Item im nächsten Rezept benutzt werden kann. Die alten Tunnelbots werden wie bisher schleichend rechtsgeklickt. Werkzeuge und Brennstoff müssen nach dem Upgrade wieder eingesetzt werden.

Admin-Testbefehle: `/miningbot steinbruch` und `/miningbot erzsucher`. `/miningbot give` bleibt der bisherige Tunnelbot.

## Station und Einsatzort

1. Das gecraftete Upgrade-Item auf einen vollen Bodenblock rechtsklicken. Dadurch entsteht die **Station** mit Antriebsschiene und Terminal.
2. Eine normale Ausgabekiste direkt neben die Schiene stellen, auf derselben Höhe. Station, Boden darunter und Kiste müssen **außerhalb des Zielchunks** des Steinbruchbots bleiben.
3. Terminal öffnen und **Bot platzieren / Einsatzort ändern** anklicken. Der Kompass im Spielerinventar ist fest mit dieser Station verbunden.
4. Mit diesem Kompass einen Bodenblock am gewünschten Einsatzort rechtsklicken. Beim Steinbruch legt das den gesamten Zielchunk fest. F3+G zeigt die Chunkgrenzen an. Station und Bot müssen in derselben Welt sein. Zwei freie Blöcke über dem angeklickten Boden sind nötig.
5. Brennstoff und Werkzeuge einlegen, beim Erzsucher die gewünschten Erzsorten auswählen, dann **Start** drücken.

Der Kompass verschwindet nach erfolgreichem Platzieren (auch im Kreativmodus). Bei abgewiesener Platzierung bleibt er erhalten. Für einen späteren Einsatzortwechsel gibt das Terminal einen neuen Kompass aus; er adressiert immer dieselbe registrierte Station und erzeugt keinen weiteren Bot. Besitzer, aktuelle Lagerteam-Mitglieder und Admins dürfen bedienen. Zum Ändern des Einsatzorts erst stoppen und das Entladen abwarten. Das Neuplatzieren setzt den Arbeitsplan für das neue Ziel zurück.

## Menüs und Inventare

- Start / Stop mit sofortigem Rückruf an die Station.
- 27 Brennstoffplätze. Kohle/Holzkohle: 64 Abbauschritte; weitere Brennstoffe wie beim bisherigen Bot.
- Spitzhacke und Schaufel; beim Steinbruchbot zusätzlich eine Axt.
- **216 Lagerplätze**, auf vier vollständig nutzbaren Seiten mit jeweils 54 Plätzen. Die vier Kisten im Hauptmenü öffnen die vier Lagerseiten. Bestehende Inventare mit 108 Plätzen werden beim Laden verlustfrei erweitert. Rechtsklick außerhalb des Lagerfensters wechselt die Seite; Escape schließt es. Das Terminal öffnet wieder das Hauptmenü.
- Items im eigenen Inventar anklicken: einlagern. Auf eingelagerte Items klicken: Stapel entnehmen; Rechtsklick: ein Item. Werkzeuge können auch über den Cursor in ihren Platz eingesetzt werden.
- TNT im Hauptmenü baut die Station ab, wenn der Bot zurückgerufen, gestoppt und vollständig leer ist. Er kommt als Upgrade-Item zurück. Alte Platzierer werden dadurch ungültig.

Passt die Ausbeute des nächsten Blocks nicht mehr ins Lager, teleportiert der Bot zur Station. Er entlädt in die angrenzende Kiste und kehrt zu seiner gespeicherten Arbeitsposition zurück. Eine fehlende, gesperrte oder volle Kiste lässt ihn an der Station warten. Ein manueller Rückruf lässt ihn nach dem Entladen dort stehen; Start setzt fort. Doppelkisten werden mit allen 54 Plätzen verwendet; eine Hälfte muss direkt neben der Basisschiene stehen. Rückkehr kostet keinen Brennstoff.

## Steinbruchbot

Der Bot erfasst beim Platzieren die höchste Oberfläche im Zielchunk (einschließlich Baumkronen) und arbeitet von dort in vollständigen 16×16-Schichten bis zur unteren Weltgrenze. Dadurch werden auch Bäume oberhalb der Platzierposition abgebaut. Er benötigt dafür eine Axt. Sand, Erde und ähnliche Blöcke verwenden die Schaufel; Gestein und Erze die Spitzhacke. Grundgestein bleibt stehen. Andere unzerstörbare Blöcke sowie Container, Betten und registrierte Maschinen stoppen den Bot mit einer Meldung.

Vor jeder neuen Schicht wartet er ungefähr zehn Sekunden. Dann wird die Schicht in einem gemeinsamen Arbeitsschritt entfernt, ohne zwischen jedem Block zu fahren. Der Steinbruchbot verbraucht eine Energieeinheit für 16 feste Blöcke (Kohle/Holzkohle: 1024 Blöcke). Jedes Werkzeug verliert nach 32 Einsätzen einen Haltbarkeitspunkt; Haltbarkeit-Verzauberungen können diesen verhindern. Die Zähler bleiben beim Entladen und Neustarten erhalten. Reicht eine Ressource nicht aus oder wird das Lager voll, wird nur der vollständig geprüfte Teil gemeinsam abgebaut; die restliche Schicht bleibt erhalten und wird nach Nachfüllen beziehungsweise Entladen fortgesetzt. Inventar und alle geplanten Blockänderungen werden zusammen gespeichert.

Wasser und Lava im Abbaugebiet werden entfernt. Flüssigkeitszuflüsse in den Zielchunk werden unterbunden; Flüssigkeiten direkt außerhalb des Chunks an der Arbeitsstelle werden mit Bruchstein abgedichtet. Die Station bleibt neben dem Steinbruch; es gibt keine ausgesparte Plattform im Zielchunk. Die Darstellung folgt den bearbeiteten Blöcken und benötigt keine Schienen im Steinbruch.

## Erzsucher

Im Erzauswahl-Menü können mehrere Erzsorten ein- oder ausgeschaltet werden. Zum Ändern vorher stoppen. Normale und Tiefenschiefer-Varianten zählen zusammen. Die Standardauswahl ist Diamant.

Er scannt versteckte Erze bis acht Blöcke um seine jeweilige Position und plant räumliche Wege zu erreichbaren Funden. Sein Suchgang ist 1×2 Blöcke groß; die Bewegung ist nicht an eine gerade Linie oder einen Chunk gebunden. Er kann seitlich, nach oben und nach unten graben. Verbundene Adern derselben Sorte werden mit einer Liste von bis zu 64 Blöcken je Fund verfolgt. Die Ader wird tatsächlich über Wege erreicht, nicht aus beliebiger Entfernung abgebaut.

Sobald der Bot den ersten ausgewählten Erzblock erreicht, werden bis zu 64 direkt verbundene Erzblöcke derselben Sorte gemeinsam per Veinmining abgebaut. Er muss nicht mehr zu jedem einzelnen Block der Ader fahren. Normale und Tiefenschiefer-Varianten zählen zusammen. Die lokale Suchgrenze, Schutzprüfungen, Werkzeugstufe, Verbrauch und Lagerkapazität gelten weiterhin pro Erzblock; bei Ressourcenmangel bleibt der Rest erhalten. Danach gibt es eine kurze Pause von etwa drei Sekunden.

Ohne Fund setzt er seine Erkundung fort. Er orientiert sich an der Platzierungsrichtung und bewegt sich auf eine zur Erzauswahl passende Höhe; bei Hindernissen probiert er andere Richtungen. Es gibt keine feste Gesamtreisedistanz, aber die Weltgrenze bleibt verbindlich. Ein endlicher Scanner garantiert keinen Fund: Erzvorkommen hängen von Dimension, Höhe und Weltgeneration ab. In der falschen Dimension oder ohne erreichbare Vorkommen kann die Suche lange dauern. Die lokale Wegsuche ist begrenzt; findet sie keinen sicheren Weg zu einem Fund, meldet der Bot das Hindernis.

Der Erzsucher hat keine Axt und umgeht Holz/Hindernisse, soweit ein lokaler Weg möglich ist. Spitzhacke und Schaufel, Werkzeugstufe, Verzauberungen und Haltbarkeit werden berücksichtigt. Jeder zusätzlich abgebaute Block verbraucht eine Energieeinheit; Flüssigkeiten werden ohne Werkzeugverschleiß oder Brennstoff entfernt. Ungeeignete Werkzeuge pausieren die Arbeit.

## Meldungen, Offline-Betrieb und Speicherung

Alle drei Bot-Stufen melden blockierende Zustände, z. B. fehlendes Werkzeug/Brennstoff, volle Ausgabekiste oder gesperrten Abbau. Dieselbe unveränderte Ursache wird nicht ständig wiederholt. Meldungen gehen an den Besitzer. Ist er offline, wird der aktuelle Hinweis gespeichert und beim nächsten Beitritt angezeigt. Erfolgreich fortgesetzte Arbeit löscht einen inzwischen erledigten Hinweis.

Die Upgrades arbeiten auch ohne anwesende Spieler weiter und halten Station und einen begrenzten Arbeitsbereich geladen. Der Server muss laufen; `pause-when-empty-seconds=-1` verhindert die Leerserver-Pause. Stoppen, Fehler oder Abschluss geben nicht mehr benötigte Chunk-Tickets frei. Beim normalen Serverneustart werden Arbeitszustand, Energie, Route, Erzader und Rückkehrpunkt wiederhergestellt.

Neue Dateien: `plugins/Casino/advanced-bots/<UUID>--<Version>.yml` und `plugins/Casino/bot-meldungen.yml`. Die letzten zwei erfolgreichen Versionen bleiben normalerweise erhalten. Neue Speicherstände werden unter einem neuen Namen veröffentlicht, statt eine möglicherweise durch Windows oder Backup-Software gesperrte Datei zu ersetzen. Kurzzeitige Umbenennungssperren werden begrenzt erneut versucht. Bei dauerhaften Zugriffsproblemen bleibt der Bot sicher angehalten; die vorherige Speicherung bleibt erhalten. Alte `<UUID>.yml`-Dateien werden weiterhin eingelesen. Ein gespeicherter Löschvermerk verhindert, dass entfernte Bots durch alte Sicherungsstände wieder erscheinen. Deshalb immer den gesamten Casino-Datenordner sichern/umziehen und keine einzelnen Versionsdateien entfernen. Abbauschritte speichern Inventaränderung und das gemeinsame Weltänderungsjournal vor dem Ändern der Welt. Welt und Plugin-Daten sind dennoch keine gemeinsame Transaktion; harte Abstürze können Abweichungen verursachen.

Automatischer Abbau und Platzierungen lösen abbrechbare EntityChangeBlockEvents aus; bei online verfügbarem Besitzer zusätzlich Spieler-Abbau-/Platzierereignisse. Externe Schutzplugins müssen auf diese Maschinenereignisse reagieren, um Offline-Abbau zu sperren. Container und registrierte Stationen werden unabhängig davon geschützt.

## Ingame-Abnahme

Noch auf dem Server zu prüfen: beide Craftingrezepte, Station außerhalb des Steinbruchs, alle Werkzeug- und Brennstoffplätze, vier Lagerseiten und volle Spielerinventare, Baum-/Blätterabbau, Wasserkante/Lavasee, komplette Schichten bis Grundgestein, freie Suchgänge in allen Richtungen, Erzauswahl, volle/fehlende Kiste, manuelles Stoppen, Ausloggen/Neustart während Arbeit und Entladen, Schutzbereiche und einmalige Hindernis-Meldungen. Automatisierte Tests decken Suchpläne und Speicherung ab, ersetzen diesen Ingame-Test aber nicht.

## Itemfilter und Erzsucher-Geschwindigkeit
Im Terminal öffnet der Trichter die Ausschlussliste. Items im Spielerinventar anklicken fügt ihre Materialart hinzu, ohne Items zu verbrauchen. Eintrag anklicken entfernt ihn. Gefilterte Abbaubeute wird verworfen; vorhandene Lageritems und manuelle Einlagerungen bleiben erhalten. Filter gelten für Einzelabbau und Erzadern und bleiben nach Neustarts erhalten. Leere Liste: alles behalten.
Der Erzsucher wartet nach Erzadern 1,5 statt 3 Sekunden. Einzelblock-Abbauwartezeiten werden halbiert (auf 0,5 Sekunden aufgerundet); Werkzeug, Effizienz und Blockhärte wirken weiterhin. Suchradius und Steinbruchtempo bleiben gleich.

Die Erzsucher-Wegfindung verwendet A* mit maximal 6000 untersuchten Positionen und bis zu 12 Blöcken Umweg je Achse. Nicht erreichbare Erz-Ziele werden 30 Sekunden übersprungen, während der Bot weiter erkundet. Der Erz-Scanradius bleibt 8 Blöcke. Tatsächlich eingeschlossene Bots und geschützte Bereiche können weiterhin eine Pause verursachen.
