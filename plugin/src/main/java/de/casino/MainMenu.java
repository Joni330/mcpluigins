package de.casino;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.IOException;
import java.util.*;

final class MainMenu implements Listener {
    private final JavaPlugin plugin;
    private final Accounts accounts;
    private final StorageTeams teams;
    private final Backpacks backpacks; private MachineOverview machines;
    private final StorageTerminals storage;
    void machines(MachineOverview value){machines=value;value.back(p->open(p,"Hauptmenü",0));}
    private static final class Page implements InventoryHolder {
        final UUID owner;
        final String section;
        final int offset;
        final Inventory inventory;
        int upgradeRows;
        long upgradePrice;
        StorageTerminals.UpgradeTarget storageTarget;
        Page(Player player, String section, int offset) {
            owner = player.getUniqueId(); this.section = section; this.offset = offset;
            inventory = Bukkit.createInventory(this, 54, Component.text("Menü · " + section));
        }
        @Override public Inventory getInventory() { return inventory; }
    }
    MainMenu(JavaPlugin plugin, Accounts accounts, StorageTeams teams, Backpacks backpacks, StorageTerminals storage) {
        this.plugin = plugin; this.accounts = accounts; this.teams = teams;
        this.backpacks = backpacks;
        this.storage = storage;
    }
    void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Objects.requireNonNull(plugin.getCommand("menu")).setExecutor((sender, command, label, args) -> {
            if (sender instanceof Player player) open(player, "Hauptmenü", 0);
            else sender.sendMessage("Bitte im Spiel ausführen.");
            return true;
        });
        plugin.getCommand("menu").setTabCompleter((sender, command, alias, args) -> List.of());
    }
    private void icon(Page page, int slot, Material material, String title, String... lines) {
        ItemStack item = ExchangeMenu.icon(material, title);
        var meta = item.getItemMeta(); meta.lore(Arrays.stream(lines).map(Component::text).toList()); item.setItemMeta(meta);
        if (meta instanceof org.bukkit.inventory.meta.SkullMeta skull) {
            skull.setOwningPlayer(Bukkit.getOfflinePlayer(page.owner)); item.setItemMeta(skull);
        }
        page.inventory.setItem(slot, item);
    }
    private void open(Player player, String section, int offset) {
        Page page = new Page(player, section, offset);
        for (int i = 0; i < 54; i++) if (i < 9 || i >= 45 || i % 9 == 0 || i % 9 == 8)
            icon(page, i, Material.BLACK_STAINED_GLASS_PANE, " ");
        icon(page, 53, Material.BARRIER, "Schließen");
        if (!section.equals("Hauptmenü")) icon(page, 45, Material.ARROW, "Zurück zum Hauptmenü");
        switch (section) {
            case "Hauptmenü" -> {
                icon(page,40,Material.COMPARATOR,"Maschinenübersicht","Miningbots und Holzfällerbots von dir und deinem Team");
                icon(page, 20, Material.GOLD_INGOT, "Konto", "Guthaben und Überweisungen");
                icon(page, 22, Material.PLAYER_HEAD, "Team", "Mitglieder und Teamverwaltung");
                icon(page, 24, Material.BARREL, "Lager & Farmen", "Lager-Handy, Sender und Filter");
                icon(page, 32, Material.BOOK, "Hilfe", "Die wichtigsten Befehle");
                icon(page, 30, Material.CHEST, "Rucksack Upgrades", "Mehr Platz für deinen persönlichen Rucksack", "Öffnen mit /bp");
                icon(page, 31, Material.BARREL, "Lager erweitern", "+45 Lagerplätze pro Seite · 200 €", "Auf dein Terminal schauen oder Lager-Handy halten.");
            }
            case "Lager erweitern" -> {
                try{
                    icon(page,20,Material.GOLD_INGOT,"Hauptkonto: "+Money.format(accounts.balance(player)));
                    page.storageTarget=storage.upgradeTarget(player);
                    if(page.storageTarget==null){
                        icon(page,22,Material.COMPASS,"Lager auswählen","Auf das gewünschte Lagerterminal schauen","oder das verbundene Lager-Handy halten.","Dann unten die Anzeige aktualisieren.");
                    }else{
                        var target=page.storageTarget;
                        icon(page,24,Material.BARREL,"Ausgewähltes Lager öffnen",target.coordinates(),target.pages()+" Seiten · "+StorageLayout.capacity(target.pages())+" Plätze","Die Erweiterung gilt für dieses gemeinsame Lager.");
                        icon(page,22,Material.LIME_STAINED_GLASS_PANE,"Eine Seite kaufen · "+Money.format(StorageLayout.PAGE_PRICE),
                                target.pages()+" → "+(target.pages()+1)+" Seiten","+45 Plätze · bis zu 1.024 Items pro Platz","Jede weitere Seite kostet ebenfalls 200 €.","Linksklick: vom Hauptkonto bezahlen");
                    }
                    icon(page,49,Material.SUNFLOWER,"Anzeige aktualisieren / Lager auswählen");
                }catch(IOException|IllegalArgumentException|IllegalStateException error){player.sendMessage("Lager-Erweiterungen konnten nicht geladen werden.");return;}
            }
            case "Rucksack Upgrades" -> {
                try {
                    long balance = accounts.balance(player);
                    int rows = accounts.backpackRows(player.getUniqueId());
                    icon(page, 20, Material.GOLD_INGOT, "Hauptkonto: " + Money.format(balance));
                    icon(page, 24, Material.CHEST, "Rucksack öffnen", rows + " Reihen · " + (rows * 9) + " Plätze", "Auch mit /bp erreichbar");
                    if (rows < 6) {
                        page.upgradeRows = rows; page.upgradePrice = backpacks.price(rows);
                        icon(page, 22, Material.LIME_STAINED_GLASS_PANE, "Nächste Reihe kaufen · " + Money.format(page.upgradePrice),
                                rows + " → " + (rows + 1) + " Reihen", "+9 Plätze · dauerhaft", "Linksklick: vom Hauptkonto bezahlen");
                    } else icon(page, 22, Material.GOLD_BLOCK, "Rucksack vollständig ausgebaut", "6 Reihen · 54 Plätze");
                } catch (IOException | IllegalArgumentException | IllegalStateException error) {
                    player.sendMessage("Rucksack-Upgrades konnten nicht geladen werden."); return;
                }
            }
            case "Konto" -> {
                try {
                    icon(page, 20, Material.GOLD_INGOT, "Hauptkonto: " + Money.format(accounts.balance(player)), "Erze, Itemshop und Überweisungen");
                    icon(page, 24, Material.GOLD_BLOCK, "Casino-Guthaben: " + Money.format(accounts.casinoBalance(player)),
                            "Spiele und Casino-Chips", "Letzte Spielauszahlung: " + Money.format(accounts.lastPayout(player)), "Einzahlen / auszahlen am Wechselautomaten");
                } catch (IOException error) { player.sendMessage("Kontostand konnte nicht geladen werden."); return; }
                icon(page, 31, Material.PAPER, "Geld überweisen", "Klicken: /pay im Chat vorbereiten", "Beispiel: /pay Milo 3");
                icon(page, 22, Material.WRITABLE_BOOK, "Kontoverlauf", "Die letzten 200 Buchungen ansehen");
                icon(page, 49, Material.SUNFLOWER, "Kontostand aktualisieren");
            }
            case "Kontoverlauf" -> {
                var history = accounts.history(player.getUniqueId());
                var format = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(java.time.ZoneId.of("Europe/Berlin"));
                if (history.isEmpty()) icon(page, 22, Material.PAPER, "Noch keine Buchungen", "Der Verlauf erfasst Buchungen ab diesem Update.");
                for (int i = offset; i < Math.min(offset + 28, history.size()); i++) {
                    var h = history.get(i); int n = i - offset;
                    icon(page, 10 + n / 7 * 9 + n % 7, h.delta() < 0 ? Material.RED_STAINED_GLASS_PANE : Material.LIME_STAINED_GLASS_PANE,
                            h.reason(), "Konto: " + h.wallet(), format.format(java.time.Instant.ofEpochMilli(h.time())),
                            "Änderung: " + (h.delta() > 0 ? "+" : h.delta() < 0 ? "−" : "") + Money.format(Math.abs(h.delta())), "Kontostand danach: " + Money.format(h.balance()),
                            h.bet() > 0 ? "Einsatz: " + Money.format(h.bet()) + " · Auszahlung: " + Money.format(h.payout()) : "");
                }
                icon(page, 45, Material.ARROW, "Zurück zum Konto");
                if (offset > 0) icon(page, 46, Material.ARROW, "Neuere Buchungen");
                if (offset + 28 < history.size()) icon(page, 52, Material.ARROW, "Ältere Buchungen");
                icon(page, 49, Material.SUNFLOWER, "Verlauf aktualisieren");
            }
            case "Team" -> {
                String team = teams.team(player.getUniqueId());
                if (team == null) {
                    icon(page, 20, Material.LIME_WOOL, "Team erstellen", "Klicken und Teamnamen im Chat ergänzen");
                    icon(page, 24, Material.PAPER, "Einladung annehmen", "Klicken und Teamnamen im Chat ergänzen");
                } else {
                    List<UUID> members = teams.members(player.getUniqueId());
                    icon(page, 4, Material.PLAYER_HEAD, "Team: " + teams.name(team), "Mitglieder: " + members.size(), teams.leader(player.getUniqueId()) ? "Du bist Teamleiter" : "Du bist Mitglied");
                    for (int i = offset; i < Math.min(offset + 21, members.size()); i++) {
                        OfflinePlayer member = Bukkit.getOfflinePlayer(members.get(i));
                        int n = i - offset;
                        icon(page, 10 + n / 7 * 9 + n % 7, Material.PLAYER_HEAD,
                                member.getName() == null ? members.get(i).toString() : member.getName(), member.isOnline() ? "Online" : "Offline");
                        ItemStack head = page.inventory.getItem(10 + n / 7 * 9 + n % 7);
                        var skull = (org.bukkit.inventory.meta.SkullMeta) head.getItemMeta();
                        skull.setOwningPlayer(member); head.setItemMeta(skull);
                    }
                    if (offset > 0) icon(page, 46, Material.ARROW, "Vorherige Mitglieder");
                    if (offset + 21 < members.size()) icon(page, 52, Material.ARROW, "Weitere Mitglieder");
                    if (teams.leader(player.getUniqueId())) {
                        icon(page, 39, Material.LIME_WOOL, "Freund einladen", "Klicken und Spielernamen im Chat ergänzen");
                        icon(page, 40, Material.RED_WOOL, "Mitglied entfernen", "Klicken und Spielernamen im Chat ergänzen");
                        icon(page, 41, Material.TNT, "Team auflösen", "Klicken: Befehl vorbereiten, dann selbst absenden");
                    } else icon(page, 40, Material.RED_WOOL, "Team verlassen", "Klicken: Befehl vorbereiten, dann selbst absenden");
                }
            }
            case "Lager & Farmen" -> {
                icon(page,4,Material.CAULDRON,"XP-Tank","Klicken: Rezept und Bedienung","XP sicher speichern · ohne Resourcepack-Update");
                icon(page,11,Material.LODESTONE,"Chunkloader","Klicken: Rezept und Bedienung","Hält einen Chunk auch offline geladen.");
                icon(page,15,Material.GOLD_BLOCK,"Lager erweitern","Klicken: eine zusätzliche Seite kaufen","+45 Plätze · immer 200 € pro Seite");
                icon(page,13,Material.IRON_AXE,"Holzfällerbot","Klicken: Rezept und Bedienung");
                icon(page, 40, Material.DAYLIGHT_DETECTOR, "Aufzug", "Klicken: Rezept und Bedienung ansehen");
                icon(page,38,Material.HOPPER,"Hopper MK2 / MK3 / MK4","5 Eisen- / Gold- / Diamantblöcke in Trichterform.","Normaler Trichter in die Mitte.","Bis zu 4× / 8× / 16× schneller; Redstone stoppt.");
                icon(page,42,Material.FURNACE,"Ofen MK2 / MK3 / MK4","Normaler Ofen, umgeben von 8 Blöcken.","Eisen: 4× · Gold: 8× · Diamant: 16×.");
                icon(page,29,Material.BARREL,"Mülleimer","Craften: Eisen oben, links und rechts vom Fass; Lavaeimer darunter.","Trichter anschließen: Items werden dauerhaft gelöscht.");
                icon(page,31,Material.DISPENSER,"Lager-Empfänger","Rezept wie Sender, aber Werfer in der Mitte.","Mit Lager-Handy schleichend rechtsklicken: verbinden.","Rechtsklick: Itemauswahl und Zielmenge.","Kiste daneben: bis zu 16 Items alle 5 Sekunden.");
                icon(page,33,Material.CHEST,"Lagerkategorien","Rüstungen, Erze, Holz und Baublöcke","Werkzeuge, Waffen, Nahrung und Pflanzen","Redstone und Mob-Drops","Kiste unten: Kategorie wechseln · Rechtsklick: eigene Filter","Klick: 1 Item · Shift-Klick: bis zu 64.");
                icon(page, 20, Material.BARREL, "Lagerterminal", "Rechtsklick am Terminal öffnet dein Lager.", "Start: 10 Seiten · 450 Plätze", "Erweiterbar · bis zu 1.024 Items pro Platz", "Umziehen: Lager einpacken im Lagermenü", "Oder auf das Terminal schauen: /lager umzug", "Items, Seiten, Kategorien und Verbindungen ziehen mit.");
                icon(page, 22, Material.COMPASS, "Lager-Handy", "Schleichen + Rechtsklick am Terminal: verbinden", "Rechtsklick mit deinem Handy: Fernzugriff");
                icon(page, 24, Material.DROPPER, "Lager-Sender", "Mit verbundenem Handy schleichend rechtsklicken.", "Leere Hand + Schleichen: Filtermenü", "Überlaufkiste vor die Ausgabeseite stellen.");
            }
            case "Hilfe" -> {
                icon(page, 20, Material.PAPER, "Konto & Geld", "/casino konto", "/pay <Spieler> <Betrag>");
                icon(page, 22, Material.PLAYER_HEAD, "Team-Befehle", "/lagerteam erstellen <Name>", "/lagerteam einladen <Spieler>", "/lagerteam annehmen <Team>", "/lagerteam info");
                icon(page, 24, Material.BOOK, "Navigation", "/menu öffnet das Hauptmenü.", "Pfeil: Zurück · Barriere: Schließen");
                icon(page, 29, Material.RED_BED, "Homes", "Bis zu 3 persönliche Homes speichern.",
                        "/sethome <Name> · Home setzen", "/home <Name> · Zum Home teleportieren",
                        "/home list · Alle Homes anzeigen", "/delhome <Name> · Home löschen");
                icon(page, 31, Material.BLUE_BED, "Teamhome", "Ein gemeinsames Home pro Team.",
                        "/setteamhome · Teamhome setzen", "/teamhome · Zum Teamhome teleportieren");
                icon(page, 33, Material.RECOVERY_COMPASS, "Zurück zum Todespunkt", "/back",
                        "Teleportiert dich zu deinem letzten Todespunkt.", "Ein neuer Tod ersetzt den bisherigen Todespunkt.");
            }
            case "Holzfällerbot" -> {
                icon(page,10,Material.IRON_AXE,"Eisenaxt");icon(page,11,Material.CHEST,"Kiste");icon(page,12,Material.IRON_AXE,"Eisenaxt");
                icon(page,19,Material.OAK_SAPLING,"Eichensetzling");icon(page,20,Material.FURNACE,"Ofen");icon(page,21,Material.OAK_SAPLING,"Eichensetzling");icon(page,29,Material.MINECART,"Lore");
                icon(page,24,Material.FURNACE_MINECART,"Holzfällerbot","Station setzen · im Terminal Kompass holen","Bot mit dem Kompass im Arbeitschunk platzieren","Axt und Setzlinge einlegen · Start","Knochenmehl optional · 216 Lagerplätze");
                icon(page,33,Material.BOOK,"Baumfarm vorbereiten","Chunk prüfen → vorhandene Bäume räumen → Runde pflanzen","Alle Setzlinge versorgen → alle Bäume gemeinsam ernten","Knochenmehl optional · ohne auf natürliches Wachstum warten","Bis zu 16 Pflanzplätze · breite/2×2-Bäume: 9","Pflanzplätze brauchen freien Gras-/Erdboden auf ähnlicher Höhe","Kiste oder Doppelkiste neben die Stationsschiene","Dunkleiche/Bleicheiche: 4 Setzlinge pro Pflanzung");
                icon(page,45,Material.ARROW,"Zurück zu Lager & Farmen");
            }
            case "XP-Tank" -> {
                for(int slot : new int[]{10,12,28,30}) icon(page,slot,Material.LAPIS_BLOCK,"Lapisblock");
                for(int slot : new int[]{11,19,21,29}) icon(page,slot,Material.GLASS,"Glas");
                icon(page,20,Material.CAULDRON,"Kessel");
                icon(page,24,Material.CAULDRON,"XP-Tank","4 Lapisblöcke + 4 Glasblöcke + 1 Kessel","Platzieren und rechtsklicken","1 Level, 5 Level oder alle XP einzahlen/entnehmen","Speichert echte XP-Punkte · beim Tod geschützt");
                icon(page,33,Material.BOOK,"Zugriff und Abbau","Anfangs privat · Teamfreigabe im Tankmenü","Teamfreigabe: aktuelles Lagerteam darf XP nutzen","Zum Abbauen erst leeren · danach mit Spitzhacke abbauen","Vanilla-Kessel · kein neues Resourcepack nötig","XP-Kugeln werden vorerst nicht automatisch gesammelt.");
                icon(page,45,Material.ARROW,"Zurück zu Lager & Farmen");
            }
            case "Chunkloader" -> {
                for(int slot : new int[]{10,12,28,30}) icon(page,slot,Material.DIAMOND,"Diamant");
                for(int slot : new int[]{11,19,21,29}) icon(page,slot,Material.ENDER_PEARL,"Enderperle");
                icon(page,20,Material.LODESTONE,"Magnetstein");
                icon(page,24,Material.LODESTONE,"Chunkloader","4 Diamanten + 4 Enderperlen + 1 Magnetstein","Platzieren: aktiv · Rechtsklick: An/Aus","Ein Chunk: 16 × 16 über die gesamte Welthöhe","Bleibt offline aktiv und startet nach Serverneustart wieder.");
                icon(page,33,Material.BOOK,"Hinweise","F3 + G zeigt die Chunkgrenzen.","Kein Brennstoff nötig · Mit Spitzhacke abbauen.","Bedienung: Besitzer und Lagerteam · Abbau: Besitzer","Öfen, Trichter und Plugin-Maschinen laufen weiter.","Wachstum und natürliche Mob-Spawns behalten Vanilla-Bedingungen.");
                icon(page,45,Material.ARROW,"Zurück zu Lager & Farmen");
            }
            case "Aufzug" -> {
                for (int slot : new int[]{10, 11, 12, 19, 21, 28, 29, 30})
                    icon(page, slot, Material.IRON_INGOT, "Eisenbarren");
                icon(page, 20, Material.ENDER_PEARL, "Enderperle");
                icon(page, 23, Material.DAYLIGHT_DETECTOR, "Aufzug", "Rezept: 8 Eisenbarren + 1 Enderperle", "Normale Tageslichtsensoren sind keine Aufzüge.");
                icon(page, 25, Material.BOOK, "So funktioniert es", "Aufzüge direkt übereinander platzieren.",
                        "Auf dem Sensor springen: nächste Etage hoch.", "Schleichen: nächste Etage runter.",
                        "Über dem Ziel zwei Blöcke freilassen.", "Decken zwischen den Etagen stören nicht.");
                icon(page, 45, Material.ARROW, "Zurück zu Lager & Farmen");
            }
        }
        player.openInventory(page.inventory);
    }
    private void suggest(Player player, String command) {
        player.closeInventory();
        player.sendMessage(Component.text("Hier klicken und Befehl ergänzen/absenden: " + command).clickEvent(ClickEvent.suggestCommand(command)));
    }
    @EventHandler public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Page page)) return;
        boolean cancelled = event.isCancelled(); event.setCancelled(true);
        if (cancelled || event.getClick() != ClickType.LEFT || !(event.getWhoClicked() instanceof Player p) || !page.owner.equals(p.getUniqueId())) return;
        int slot = event.getRawSlot();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline() || p.getOpenInventory().getTopInventory().getHolder() != page) return;
            if (slot == 53) { p.closeInventory(); return; }
            if (slot == 45 && !page.section.equals("Hauptmenü")) { open(p, (page.section.equals("Aufzug")||page.section.equals("Holzfällerbot")||page.section.equals("Chunkloader")||page.section.equals("XP-Tank")) ? "Lager & Farmen" : page.section.equals("Kontoverlauf") ? "Konto" : "Hauptmenü", 0); return; }
            switch (page.section) {
                case "Lager & Farmen" -> { if(slot==4)open(p,"XP-Tank",0);else if(slot==11)open(p,"Chunkloader",0);else if (slot == 40) open(p, "Aufzug", 0);else if(slot==13)open(p,"Holzfällerbot",0);else if(slot==15)open(p,"Lager erweitern",0); }
                case "Hauptmenü" -> {
                    if(slot==40&&machines!=null)machines.open(p);
                    else if (slot == 20) open(p, "Konto", 0);
                    else if (slot == 22) open(p, "Team", 0);
                    else if (slot == 24) open(p, "Lager & Farmen", 0);
                    else if (slot == 32) open(p, "Hilfe", 0);
                    else if (slot == 30) open(p, "Rucksack Upgrades", 0);
                    else if (slot == 31) open(p, "Lager erweitern", 0);
                }
                case "Lager erweitern" -> {
                    if(slot==49){open(p,"Lager erweitern",0);return;}
                    if(page.storageTarget==null)return;
                    try{
                        if(slot==24)storage.openUpgradeTarget(p,page.storageTarget);
                        else if(slot==22){
                            int pages=storage.upgrade(p,page.storageTarget);
                            p.sendMessage("Lager erweitert: "+pages+" Seiten · "+StorageLayout.capacity(pages)+" Plätze. 200 € vom Hauptkonto bezahlt.");
                            p.playSound(p.getLocation(),Sound.ENTITY_PLAYER_LEVELUP,.6f,1.2f);open(p,"Lager erweitern",0);
                        }
                    }catch(IllegalArgumentException|IllegalStateException error){p.sendMessage(error.getMessage());open(p,"Lager erweitern",0);}
                    catch(IOException error){p.sendMessage("Speichern fehlgeschlagen. Kein Upgrade gekauft und kein Geld abgebucht.");}
                }
                case "Rucksack Upgrades" -> {
                    if (slot == 24) backpacks.open(p);
                    else if (slot == 22 && page.upgradeRows > 0) {
                        try {
                            if (backpacks.price(page.upgradeRows) != page.upgradePrice) {
                                open(p, "Rucksack Upgrades", 0); return;
                            }
                            accounts.upgradeBackpack(p.getUniqueId(), page.upgradeRows, page.upgradePrice);
                            p.sendMessage("Rucksack erweitert: " + ((page.upgradeRows + 1) * 9) + " Plätze!");
                            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, .6f, 1.2f);
                            open(p, "Rucksack Upgrades", 0);
                        } catch (IllegalArgumentException error) { p.sendMessage(error.getMessage()); }
                        catch (IOException error) { p.sendMessage("Speichern fehlgeschlagen. Kein Upgrade gekauft und kein Geld abgebucht."); }
                    }
                }
                case "Konto" -> { if (slot == 49) open(p, "Konto", 0); else if (slot == 31) suggest(p, "/pay "); else if (slot == 22) open(p, "Kontoverlauf", 0); }
                case "Kontoverlauf" -> {
                    if (slot == 49) open(p, "Kontoverlauf", 0);
                    else if (slot == 46 && page.offset > 0) open(p, "Kontoverlauf", page.offset - 28);
                    else if (slot == 52 && page.offset + 28 < accounts.history(p.getUniqueId()).size()) open(p, "Kontoverlauf", page.offset + 28);
                }
                case "Team" -> {
                    if (teams.team(p.getUniqueId()) == null) {
                        if (slot == 20) suggest(p, "/lagerteam erstellen ");
                        else if (slot == 24) suggest(p, "/lagerteam annehmen ");
                    } else if (slot == 46 && page.offset > 0) open(p, "Team", page.offset - 21);
                    else if (slot == 52 && page.offset + 21 < teams.members(p.getUniqueId()).size()) open(p, "Team", page.offset + 21);
                    else if (teams.leader(p.getUniqueId())) {
                        if (slot == 39) suggest(p, "/lagerteam einladen ");
                        else if (slot == 40) suggest(p, "/lagerteam entfernen ");
                        else if (slot == 41) suggest(p, "/lagerteam aufloesen");
                    } else if (slot == 40) suggest(p, "/lagerteam verlassen");
                }
            }
        });
    }
    @EventHandler public void drag(InventoryDragEvent event) { if (event.getView().getTopInventory().getHolder() instanceof Page) event.setCancelled(true); }
    void disable() { for (Player p : Bukkit.getOnlinePlayers()) if (p.getOpenInventory().getTopInventory().getHolder() instanceof Page) p.closeInventory(); }
}
