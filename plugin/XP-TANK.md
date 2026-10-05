# XP-Tank

Ein platzierbarer **Vanilla-Kessel** zum sicheren Speichern von Erfahrung. Das bestehende Resourcepack kann unverändert bleiben; der Tank verwendet keine eigenen Modelle oder Texturen.

## Rezept

```text
Lapisblock   Glas     Lapisblock
Glas         Kessel   Glas
Lapisblock   Glas     Lapisblock
```

Das Rezept erscheint im Rezeptbuch und unter `/menu → Lager & Farmen → XP-Tank`. Administratoren können mit `/xptank` ein leeres Tank-Item erhalten.

## Bedienung

Tank platzieren und rechtsklicken. Oben stehen der Tankbestand in **XP-Punkten** sowie die eigene Erfahrung. Links einzahlen, rechts entnehmen: jeweils **1 Level, 5 Level oder alle XP**.

Die Levelknöpfe buchen bis zur angezeigten Levelgrenze. Beispiel: Bei Level 30 mit etwas Fortschritt zahlt „1 Level“ bis zum Anfang von Level 29 ein; „1 Level entnehmen“ füllt bis zum Anfang von Level 31 auf. Die Vorschau nennt die konkrete Punktmenge. Bei zu wenig Tankinhalt wird der verfügbare Rest entnommen. „Alle XP entnehmen“ lässt einen eventuellen Überschuss im Tank, falls das von Paper unterstützte Spielerlimit erreicht wird.

Intern werden ausschließlich echte Punkte gespeichert. Die [Paper-XP-Schnittstelle](https://jd.papermc.io/paper/1.21.11/org/bukkit/entity/Player.html#calculateTotalExperiencePoints()) liest den aktuellen Level samt Fortschritt und setzt beide nach einer Umbuchung passend zum neuen Punktbestand. Die unterschiedlich hohen Levelkosten erzeugen keine zusätzliche Erfahrung. Entnehmen erzeugt keine XP-Kugeln und repariert keine Ausrüstung automatisch.

Der Tank gehört zunächst nur seinem Besitzer. Dieser kann im Tankmenü die Freigabe für sein aktuelles **Lagerteam** einschalten. Dann dürfen Teammitglieder einzahlen und entnehmen; ehemalige Mitglieder verlieren den Zugriff. Besitzer und Administratoren können die Freigabe ändern. Ein Teamwechsel des Besitzers ändert das zugriffsberechtigte Team.

Nur Besitzer oder Administrator dürfen den **vollständig geleerten** Tank abbauen. Mit einer Spitzhacke erhält man das Tank-Item zurück; im Kreativmodus gelten die normalen Dropregeln. Explosionen, Kolben, Bot-Abbau und Befüllen mit Wasser/Lava/Pulverschnee verändern den Tank nicht. Normale Kessel bleiben unverändert. Tank-XP bleiben erhalten, wenn ein Spieler stirbt oder sich abmeldet. Automatisches Einsammeln von XP-Kugeln ist noch nicht enthalten.

## Speichern und Update

Bestände, Besitzer, Standorte und Teamfreigaben liegen unter `plugins/Casino/xp-tanks/`. Die Erkennung des platzierten Tanks liegt zusätzlich in den Chunkdaten. Neue Speicherstände werden in eigene Generationsdateien geschrieben; die letzte und vorletzte Generation bleiben erhalten. Beschädigte neueste Daten werden nicht stillschweigend durch einen älteren XP-Bestand ersetzt.

Eine Umbuchung speichert zuerst den neuen Tankbestand und ändert danach die Spieler-XP. Scheitert das Schreiben des Tankbestands, werden die Spieler-XP nicht verändert. Nach erfolgreicher Umbuchung werden auch die Spielerdaten gespeichert. Bei einem Fehler werden alle XP-Tanks bis zum nächsten Pluginstart gesperrt.

Tankdateien, Welt-/Chunkdaten und Spielerdaten immer gemeinsam sichern und wiederherstellen. Ein harter Prozessabbruch während einer Umbuchung ist keine atomare Transaktion über diese getrennten Minecraft-Dateien; reguläres Herunterfahren ist vorgesehen. Manuelles Ersetzen eines Tanks durch Welteditoren löscht seinen gespeicherten Bestand nicht, kann aber die Zuordnung zum Block zerstören.

Die neue JAR ist nach einem regulären Serverneustart aktiv. Kein Resourcepack-Update erforderlich.
