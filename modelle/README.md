# Wechselautomat in Minecraft

Die Originale `wechsler.bbmodel`, `wechsler.json` und `wechsler.png` bleiben hier erhalten.
Nach Änderungen in Blockbench JSON und PNG erneut hier exportieren, dann aus dem Projektordner ausführen:

```powershell
powershell -ExecutionPolicy Bypass -File .\modelle\build-resourcepack.ps1
```

Das erzeugt `resourcepack/` und `Casino-Resourcepack.zip` für Minecraft Java 26.3.
In Minecraft unter Optionen → Ressourcenpakete → Paketordner öffnen die ZIP ablegen und aktivieren.
Nach erneutem Export die ZIP dort ersetzen und Ressourcen mit F3+T neu laden.

Das Plugin-Update benötigt einen Serverneustart. Bestehende Wechselautomaten einmal abbauen
und neu platzieren; der Drop, neue Crafting-Ergebnisse und `/casino give` verwenden das Modell.
Alte Inventar-Items erhalten das Modell spätestens beim Abbauen des damit platzierten Automaten.
Ohne aktiviertes Ressourcenpaket wird das eigene Modell nicht korrekt angezeigt.

Die Ausrichtung und Skalierung bitte im Spiel prüfen: Inventar, Hand, Boden-Drop und Platzieren
aus allen vier Richtungen. Das Weltmodell ist 1,875 Blöcke hoch; die Klickfläche bleibt 2 Blöcke hoch.
