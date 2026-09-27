# Hauptkonto und Casino-Guthaben

Beim ersten Start dieser Version bleibt das bestehende Guthaben unverändert auf dem
Hauptkonto. Casino-Guthaben beginnt bei 0. Vorher wird accounts.yml einmalig als
accounts-before-wallets-<UUID>.yml gesichert. wallet-version verhindert eine erneute
Umstellung. Ältere Kontoverlauf-Einträge heißen „Vor Kontentrennung“.

Hauptkonto: Erze verkaufen, Itemshop, /pay und Admin-Gutschriften mit /payload.
Casino-Guthaben: alle Spiele, Chipkäufe und eingelöste Chips.
/menu → Konto und /casino konto zeigen beide Guthaben.

Am Wechselautomaten den Goldblock „Casino einzahlen / auszahlen · Chips einlösen“
anklicken. Betrag mit den Glasscheiben einstellen (auch Cent-Schritte), dann
Einzahlen oder Auszahlen wählen. Alternativ das gesamte Guthaben umbuchen. Der
Umtausch ist 1:1 ohne Gebühr. Jeder Vorgang erzeugt zwei gekennzeichnete Buchungen
im gemeinsamen Verlauf. Beide Guthaben und Verlauf werden zusammen gespeichert.

„Alle Chips im Inventar einlösen“ nimmt alle erkannten Chips aus den normalen
Inventar-/Hotbarplätzen und schreibt ihren Wert dem Casino-Guthaben gut. Offhand,
Cursor, Kisten und platzierte Stapel werden nicht angetastet. Alte Casino-Chips
behalten ihren Wert. Die Einlösung ist kein Verkauf gewöhnlicher Papier-Items.

Im Spiel prüfen: Neustart-Migration, Einzahlung, Auszahlung, Cent-Reste, Chipkauf
und Einlösen, jedes Spiel sowie /pay und Itemshop. Das Casino-Menü funktioniert
nur in Reichweite des Wechselautomaten. Kein neues Resourcepack nötig.
