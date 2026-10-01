# Lagerterminal – erste Version

Bestandteil derselben Casino.jar. Kein weiteres Plugin und kein neues Ressourcenpaket nötig.

## Benutzung

## Kabelloser Lager-Sender

Als Admin `/lager sender` oder dieses Rezept (Mitte: Werfer, nicht Spender):

| Diamant | Enderperle | Diamant |
|---|---|---|
| Redstone | Werfer | Redstone |
| Diamant | Enderperle | Diamant |

1. Lager-Handy wie bisher am Zielterminal verbinden.
2. Lager-Sender bei der Farm platzieren.
3. Mit diesem Handy schleichen und den Sender rechtsklicken.
4. Hopper in den Sender führen; Rechtsklick mit leerer Hand öffnet seinen Puffer.

Neun normale Inventarplätze puffern Items. Alle fünf Sekunden werden insgesamt
höchstens 16 Items übertragen, über alle Lagerplätze einschließlich gekaufter Seiten. Passende Stapel werden
zuerst gefüllt, dann leere Plätze. NBT-Daten und normale Item-Stapelgrenzen bleiben
erhalten. Volles Lager: nur der passende Anteil wird übertragen; der Rest bleibt.
Keine Gebühren. Filter und optionaler Überlauf sind unten beschrieben.

Quelle und Ziel müssen in derselben Welt sein. Der Sender hält die Farm nicht
geladen. Er arbeitet nur, solange ihr Chunk geladen ist. Der Zielchunk wird bei
Bedarf für den Zugriff geladen, aber erhält dadurch kein dauerhaftes Chunk-Ticket.
Rechte des Sender-Besitzers werden vor jeder Lieferung geprüft, auch wenn er offline
ist. Nach Team-Austritt oder Ersetzen des Zielterminals stoppt die Lieferung.
Besitzer/Team dürfen den Puffer öffnen; Hopper dürfen ein- und auslagern. Nur
Besitzer/Admin dürfen den leeren, geschlossenen Sender abbauen; das Sender-Item droppt.
Redstone aktiviert keinen normalen Werfer-Auswurf. Explosionen/Kolben sind gesperrt.

Senderdaten und Puffer werden mit der Welt gespeichert. Sauber mit `stop` beenden.
Wie bei normalen Inventaren besteht keine atomare Speicherung mehrerer Chunks
bei einem harten Serverabsturz. Bei einem Speicherfehler wird die Lieferung gesperrt
und der Fehler im Serverlog gemeldet. Kein neues Resourcepack nötig.

Ingame prüfen: Hopper-Befüllung, höchstens 16 Items je fünf Sekunden, volle/teilvolle
Lager, benannte Items, Team-Austritt, Neustart, Chunk-Entladen und erneutes Laden,
ersetztes Zielterminal sowie Abbau eines leeren Senders.

## Terminal herstellen

Als Admin `/lager give` oder am Werktisch craften:

| Eisenbarren | Redstone | Eisenbarren |
|---|---|---|
| Diamant | Fass | Diamant |
| Eisenbarren | Redstone | Eisenbarren |

Terminal wie ein Fass platzieren, mit Rechtsklick öffnen. Anfangs 450 Slots auf zehn Seiten mit je 45 Lagerplätzen und
virtuellen Mengen bis 1.024 je Platz. Die Bedienung steht im Abschnitt unten.
Unter `/menu` → **Lager erweitern** kostet jede zusätzliche Seite mit 45 Plätzen **200 € vom Hauptkonto**.
Besitzer und Mitglieder seines Lagerteams können dasselbe Terminal gleichzeitig benutzen.
Jedes Terminal hat einen eigenen Speicher; es gibt noch kein verbundenes Netzwerk.
Leeren und alle Fenster schließen, dann abbauen: Ein leeres Terminal-Item mit der bisherigen Seitenzahl droppt.
Hopperzugriff, Explosionen und Verschieben durch Kolben sind gesperrt.

## Speicherung und Grenzen

Die vollständigen Item-NBT-Daten einschließlich Namen, Verzauberungen und Shulkerinhalten
werden in den Daten des Fassblocks gespeichert. Diese gehören zur Welt, nicht zur
Casino-Kontodatei. Bezahlte Erweiterungen werden zusätzlich gemeinsam mit der Abbuchung in `accounts.yml` gespeichert. Beim Umzug Welt und Plugin-Daten bei gestopptem Server sichern/kopieren.
Lageränderungen werden vor der Änderung am Spielerinventar, beim Schließen, Chunk-Entladen und beim
Deaktivieren des Plugins in den Block geschrieben. Minecraft speichert den Chunk
bei seinen normalen Speichervorgängen. Sauber mit `stop` beenden; kein `/reload`.
Harte Abstürze können wie bei normalen Weltinventaren seit dem letzten Speichern
Änderungen verlieren. Es besteht keine crash-atomare Transaktion mit Spielerdateien.
Keine Terminals per WorldEdit klonen/entfernen: Andere Plugins/Admin-Eingriffe können
Blockschutz umgehen und würden gespeicherte Inhalte kopieren bzw. löschen.
Ohne Casino-Plugin bleibt das Fass sichtbar, aber die virtuellen Slots sind nicht erreichbar.
Kabel, Autocrafting und eigenes Blockmodell sind nicht enthalten.

## Ingame-Prüfung vor dem produktiven Einsatz

1. Crafting und `/lager give`; platzieren und öffnen.
2. Normale Items, benannte/verzauberte Items und gefüllte Shulkerkiste einlegen.
3. Shift-Klick, Hotbar-Tausch und Ziehen testen; volle Seiten dürfen nicht überlaufen; die unterste Reihe ist für Navigation reserviert.
4. Zwei Spieler öffnen gleichzeitig und entnehmen denselben Stapel: nur einmal vorhanden.
5. Fenster schließen, Chunk verlassen, zurückkehren; Inhalte prüfen.
6. Server mit `stop` neu starten; Itemdetails und Mengen prüfen.
7. Gefülltes Terminal gegen Abbau, Hopper und Explosionen testen.
8. Leeres geschlossenes Terminal abbauen und erneut platzieren.

Diese Szenarien benötigen einen laufenden Paper-Server und sind nicht durch die
bisherigen automatisierten Casino-Tests abgedeckt.

## Lager-Handy und Seiten

Mit `/lager handy` als Admin erhältlich; sonst am Werktisch:

| Diamant | Redstone | Diamant |
|---|---|---|
| Eisenbarren | Enderauge | Eisenbarren |
| Diamant | Eisenbarren | Diamant |

Das Handy sieht vorerst wie ein benannter Kompass aus. Es wird mit Schleichen +
Rechtsklick auf ein Terminal verbunden und an den verknüpfenden Spieler gebunden.
Rechtsklick öffnet das verbundene Lager in derselben Welt ohne Entfernungsgrenze.
Der Spieler muss das passende Handy im Inventar behalten. Ein anderer Spieler kann
es weder benutzen noch neu binden. Terminalersatz am selben Ort macht die alte
Verbindung ungültig; der Besitzer kann sein Handy mit dem neuen Terminal verbinden.

Pfeile unten links/rechts wechseln die Seite; Shift-Klick lagert über alle verfügbaren
Seiten ein. Die Navigationssymbole sind keine entnehmbaren Items.
Doppelklick zum Einsammeln und Creative-Klonen sind im Lager gesperrt.
Bisherige 54 Plätze werden verlustfrei in Slotreihenfolge übernommen: 45 auf Seite 1,
9 auf Seite 2. Die Teamrechte gelten sowohl vor Ort als auch beim Handy.

Zusätzlich im Spiel testen: alte volle Lager migrieren, über Seitengrenzen arbeiten,
mit zwei Spielern auf verschiedenen Seiten arbeiten, Handy im entfernten/ungeladenen
Chunk benutzen, Fremdbesitzer und Weltwechsel prüfen, Terminal ersetzen und neu binden.
Chunks mit geöffneten Fenstern bleiben geladen; Tickets werden nach dem Schließen
wieder freigegeben. Harte Absturzsicherheit bleibt wie oben beschrieben begrenzt.

## Suche und Sortierung

Unten auf den Kompass klicken und innerhalb von 60 Sekunden den Suchbegriff in den
Chat schreiben. Diese Nachricht wird nicht öffentlich gesendet. `abbrechen` beendet
die Eingabe. Gesucht wird über alle verfügbaren Seiten nach englischen Materialnamen,
eigenen Itemnamen und häufigen deutschen Begriffen (z.B. eisen, kupfer, diamant,
holz). Es ist keine vollständige deutsche Übersetzung aller Minecraft-Items.
Die passende Lagerseite öffnet sich; Reihe und Spalte stehen im Chat. Das Fernrohr
springt zum nächsten Treffer und beginnt am Ende wieder vorne. Die Seite bleibt
ein normales Lagerinventar, es wird keine separate gefilterte Itemkopie erzeugt.

Trichter linksklicken: das gesamte Lager nach internem Itemtyp sortieren.
Rechtsklicken: größte Stapel zuerst. Leere Plätze landen hinten. Itemdaten und
Stapelgrößen bleiben erhalten; Stapel werden nicht zusammengeführt. Da das Lager
geteilt wird, gilt die neue Reihenfolge für alle Mitglieder.
Im Spiel zusätzlich mehrere Treffer, entfernte Treffer, gleichzeitige Teamzugriffe
und die Suche per Handy testen.

## Teambefehle

Neue Terminals gehören dem Spieler, der sie platziert. Bestehende Terminals bleiben
erhalten: Ein Admin schaut auf das Terminal und verwendet `/lager zuordnen <Spieler>`
(Zielspieler online). Das funktioniert nur bei noch besitzerlosen Terminals.
Admins mit `casino.admin` können auf alle Lager zugreifen.

- `/lagerteam erstellen Freunde`: eigenes Team erstellen.
- `/lagerteam einladen Milo`: als Leiter einen Spieler online einladen.
- `/lagerteam annehmen Freunde`: Einladung innerhalb von zehn Minuten annehmen.
- `/lagerteam info`: Mitglieder anzeigen.
- `/lagerteam verlassen`: als Mitglied austreten.
- `/lagerteam entfernen Milo`: als Leiter ein Mitglied entfernen.
- `/lagerteam aufloesen`: als Leiter das Team auflösen.

Jeder Spieler kann einem Team angehören. Mitglieder dürfen alle Terminals der
anderen Mitglieder benutzen und ihr eigenes Handy damit verbinden. Nur Besitzer
oder Admin dürfen ein leeres Terminal abbauen. Austritt/Entfernung beendet den
gemeinsamen Zugriff; eigene Terminals bleiben benutzbar. Inhalte werden nicht
verschoben oder aufgeteilt. Offene Fenster werden spätestens bei der regelmäßigen
Prüfung geschlossen; jeder weitere Inventarzugriff prüft die Berechtigung erneut.

Teamdaten stehen in `plugins/Casino/storage-teams.yml`. Beim Umzug diese Datei
zusammen mit der Welt sichern. Automatisierte Tests prüfen Einladungen, Rechte,
Neustart-Persistenz und fehlgeschlagene Speichervorgänge. Zusätzlich im Spiel mit
zwei Spielern ohne Adminrechte Teamzugriff, Handy und Rechteentzug prüfen.

## Senderfilter und Überlaufkiste

Schleichen + Rechtsklick mit leerer Haupthand auf den Sender öffnet das Filtermenü.
Die Statusmeldung erscheint zusätzlich im Chat. Linksklick auf ein Item im eigenen
Inventar trägt dessen Typ als Muster ein, ohne ein Item zu verbrauchen. Oben auf ein
Muster klicken entfernt es. Maximal neun unterschiedliche Typen; Namen,
Verzauberungen und sonstige NBT-Daten werden beim Filtern ignoriert, beim Transport
aber vollständig erhalten. Die Muster selbst sind nicht entnehmbar.

Der Trichterknopf wechselt zwischen Alles senden (Standard), Nur diese senden und
Diese ausschließen. Eine leere Positivliste lässt nichts ins Lager; eine leere
Negativliste erlaubt alles. Einstellungen werden am Sender in der Welt gespeichert.
Teammitglieder dürfen die Einstellungen ändern.

Eine normale oder Redstone-Kiste direkt vor die Ausgabeseite des Werfers stellen
(bei einer Doppelkiste wird die gesamte Kiste benutzt). Im Filtermenü den Kistenknopf
auf Überlauf AN stellen. Standard ist AUS; bestehende Sender bleiben unverändert.
Zuerst werden erlaubte Items ins verknüpfte Lager geschickt. Filterausschlüsse und
nicht mehr ins Lager passende Mengen gehen anschließend in die Überlaufkiste.
Fehlt diese Kiste, ist ihr Chunk ungeladen oder ist sie voll, bleibt der Rest im
Sender. Beide Ausgänge teilen sich insgesamt 16 Items je fünf Sekunden. Niemals
werden Items gelöscht. Bei ungültigem Lagerziel/fehlenden Rechten bleibt der ganze
Sender stehen; ein Speicherfehler wird ebenfalls nicht als volles Lager behandelt.

Zusätzlich ingame testen: Filtermuster hinzufügen/entfernen ohne Inventaränderung,
alle drei Modi (auch leere Listen), Rechteentzug, mehrere Menübenutzer, Neustart,
volle/fehlende/teilvolle Überlaufkiste, benannte Items und gemeinsame Mengenbegrenzung.
Mengenlimits pro Item und automatische Vernichtung sind noch nicht enthalten.


## Virtuelle Stapel bis 1.024

Alle Lagerplätze, auch auf gekauften Seiten, speichern je bis zu 1.024 identische Items, unabhängig von ihrer
normalen Minecraft-Stapelgröße. Die Anzeige ist ein einzelnes Muster; die genaue
Menge steht beim Darüberfahren in der Beschreibung. Anzeigebeschreibungen werden
niemals auf die echten Items geschrieben.

- Linksklick mit leerem Cursor: ein Item direkt ins Spielerinventar.
- Shift-Linksklick: bis zu einen normalen Stack direkt ins Spielerinventar.
- Rechtsklick mit leerem Cursor: ein Item direkt ins Spielerinventar.
- Shift-Klick im Spielerinventar: diesen Stack über alle Lagerseiten einlagern.
- Mit einem Item am Cursor auf einen Lagerplatz klicken: Cursorinhalt einlagern.
- Ziehen, Nummerntasten und direktes Entnehmen der Anzeigemuster sind gesperrt.

Fehlt Platz im Spielerinventar, wird nur die passende Menge entnommen. Außerhalb
des Lagers gelten die normalen Itemgrenzen, höchstens 64 pro Stack. Identische
Werkzeuge können virtuell zusammenliegen; sie werden einzeln entnommen. Abweichende
Namen, Verzauberungen, Schaden, Shulkerinhalte und sonstige Metadaten bleiben getrennt.
Sender nutzen dieselben virtuellen Plätze, weiterhin höchstens 16 je fünf Sekunden.

Bestehende Lager mit 54 oder 450 Plätzen werden beim ersten Öffnen/Empfangen
übernommen und identische Items zu größeren Stapeln zusammengefasst. Die vorherigen
Rohdaten bleiben einmalig im Block unter storage_legacy_backup erhalten. Neue Daten
bestehen aus Itemmustern und getrennten Ganzzahl-Mengen. Alte Plugin-Versionen
können diese Lager nicht öffnen; für einen Rückwechsel ist eine gezielte Migration
oder eine Welt-Sicherung nötig. Nicht einfach die Sicherungsdaten zurückspielen,
nachdem bereits Items entnommen wurden.

Zusätzlich ingame prüfen: mehr als 64 Items, 1.024-Grenze und zweiter Platz,
Teilentnahme bei fast vollem Inventar, Rechtsklick mehrfach, NBT-Items, zwei Spieler
am selben Stack, Sender/Überlauf und Mengen nach sauberem Serverneustart.

## Werkbank im Lager und Handy

In jeder Lagerseite öffnet die Werkbank unten (Slot 48) ein persönliches 3×3-Rezeptfeld. Das funktioniert auch beim Fernzugriff über ein gebundenes Handy und in einem freigegebenen Teamlager.

- Linksklick auf ein Rezeptfeld öffnet die Zutatenauswahl aus allen zehn Lagerseiten. Die gewählte Zutat ist nur eine Vorlage und wird nicht aus dem Lager genommen.
- Rechtsklick auf ein Rezeptfeld entfernt diese Zutat. Der Barrier-Knopf unten rechts leert die gesamte Vorlage.
- Ein Klick auf das Ergebnis oder den grünen Knopf stellt das Rezept einmal her. Die Vorlage bleibt für weitere Durchläufe bestehen.
- Die Zutaten werden mit ihren vollständigen Itemdaten aus dem aktuellen Lagerbestand entnommen. Ergebnis und Restitems (zum Beispiel Eimer) landen im Spielerinventar.
- Fehlende Zutaten, fehlender Platz oder ein Speicherfehler verhindern den Vorgang ohne Verbrauch. Ein Teammitglied kann eine bereits verbrauchte Zutat nicht anhand einer alten Vorschau erneut verwenden.
- Die normalen Zugriffsregeln des Terminals beziehungsweise Handys gelten weiter. Schließen verwirft nur die kostenlose Vorlage.

Die Werkbank verwendet die auf dem Server registrierten Crafting-Rezepte. Sie erweitert nicht das normale Minecraft-Rezeptbuch oder eine gewöhnliche Werkbank.

## Rezeptbuch und Mehrfach-Crafting

Das Buch unten in der Lager-Werkbank öffnet eine blätterbare Übersicht der registrierten geformten und ungeformten Crafting-Rezepte. Das gilt auch über das Handy.

- Grüne Rezeptnamen mit Glanz: Zutaten sind im Lager vorhanden. Rote Namen: fehlende Zutaten stehen im Tooltip, je fehlendem Rezeptfeld eine Zeile. Bei Alternativen wird ein Beispielmaterial angezeigt.
- Ein Klick übernimmt das ganze Rezept. Passende Zutaten einschließlich alternativer Holzarten werden automatisch aus dem aktuellen Lagerbestand gewählt.
- Der Kompass startet eine Suche per Chat (60 Sekunden). Häufige deutsche Namen wie Diamanthelm, Eisenspitzhacke und Werkbank sowie englische Itemnamen werden erkannt. Rechtsklick auf den Kompass löscht den Suchfilter.
- Linksklick auf Ergebnis oder Herstellen führt einen Rezeptdurchlauf aus. Shift-Linksklick führt bis zu 64 Durchläufe aus; fehlende Zutaten oder fehlender Platz beenden die Serie. Die Ausgabemenge pro Durchlauf entspricht dem Rezept (zum Beispiel vier Bretter).
- Die komplette Serie wird zunächst mit Kopien berechnet und anschließend einmal gespeichert. Erst dann werden die Ergebnisitems ins Spielerinventar gelegt. Restitems wie Eimer werden ebenfalls berücksichtigt.
- Rezepte mit dynamischen Zutaten ohne feste Rezeptform (zum Beispiel spezielle Feuerwerksrezepte) bleiben über die manuelle 3×3-Vorlage nutzbar und erscheinen nicht im Rezeptbuch.
