package de.casino;

import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

final class RouletteMenu implements InventoryHolder {
    final UUID machine;
    private final CasinoPlugin plugin;
    private final Accounts accounts;
    private final Player player;
    private final Inventory inventory;
    private static final int[] RING = {1,2,3,4,5,6,7,17,26,35,44,52,51,50,49,48,47,46,36,27,18,9};
    private long bet = Money.MIN_SPIN_CENTS;
    private RouletteRules.Color selected = RouletteRules.Color.RED;
    private BukkitTask animation;
    private boolean closed;
    private long rollingBalance;
    private String pendingResult;
    private String result = "Noch keine Runde gespielt";
    RouletteMenu(CasinoPlugin plugin, Accounts accounts, Player player, UUID machine) throws IOException {
        this.plugin=plugin; this.accounts=accounts; this.player=player; this.machine=machine;
        inventory=Bukkit.createInventory(this,54,Component.text("Casino · Roulette"));
        for(int i=0;i<54;i++) inventory.setItem(i,ExchangeMenu.icon(Material.BLACK_STAINED_GLASS_PANE," "));
        inventory.setItem(53,ExchangeMenu.icon(Material.BARRIER,"Zurück zur Spielauswahl"));
        ring(); refresh();
    }
    private static String name(RouletteRules.Color c) { return switch(c) { case RED -> "Rot"; case BLACK -> "Schwarz"; case GREEN -> "Grün"; }; }
    private static Material wool(RouletteRules.Color c) { return switch(c) { case RED -> Material.RED_WOOL; case BLACK -> Material.BLACK_WOOL; case GREEN -> Material.LIME_WOOL; }; }
    private RouletteRules.Color ringColor(int i) { return i==3 ? RouletteRules.Color.GREEN : (i<3 ? i%2==0 : i%2==1) ? RouletteRules.Color.RED : RouletteRules.Color.BLACK; }
    private void ring() { for(int i=0;i<RING.length;i++) inventory.setItem(RING[i],ExchangeMenu.icon(wool(ringColor(i)),name(ringColor(i)))); }
    private void refresh() throws IOException {
        inventory.setItem(20,ExchangeMenu.icon(Material.PAPER,"Einsatz: "+Money.format(bet)));
        for(var c:RouletteRules.Color.values()) {
            int slot=c==RouletteRules.Color.RED?21:c==RouletteRules.Color.GREEN?22:23;
            var icon=ExchangeMenu.icon(wool(c),name(c)+(selected==c?" · Ausgewählt":" · Auswählen"));
            var meta=icon.getItemMeta(); meta.setEnchantmentGlintOverride(selected==c); icon.setItemMeta(meta); inventory.setItem(slot,icon);
        }
        var info=ExchangeMenu.icon(Material.PAPER,"Auswahl: "+name(selected)+" · Auszahlung: "+Money.format(bet*(selected==RouletteRules.Color.GREEN?36:2)));
        var meta=info.getItemMeta(); meta.lore(List.of(Component.text("Auszahlung einschließlich Einsatz"),Component.text("Rot / Schwarz: ×2 · Grün: ×36"),Component.text(animation == null ? result : "Kugel rollt …")));
        info.setItemMeta(meta); inventory.setItem(24,info);
        inventory.setItem(29,ExchangeMenu.icon(Material.LIME_STAINED_GLASS_PANE,"Einsatz +0,10€"));
        inventory.setItem(30,ExchangeMenu.icon(Material.LIME_STAINED_GLASS_PANE,"Einsatz +1,00€"));
        inventory.setItem(32,ExchangeMenu.icon(Material.RED_STAINED_GLASS_PANE,"Einsatz −1,00€"));
        inventory.setItem(33,ExchangeMenu.icon(Material.RED_STAINED_GLASS_PANE,"Einsatz −0,10€"));
        var start=ExchangeMenu.icon(Material.GOLD_INGOT,animation==null?"Roulette starten":"Kugel rollt …");
        meta=start.getItemMeta(); meta.lore(List.of(Component.text("Casino-Guthaben: "+Money.format(animation == null ? accounts.casinoBalance(player) : rollingBalance)))); start.setItemMeta(meta); inventory.setItem(31,start);
    }
    boolean usable() {
        if(player.getGameMode()==GameMode.SPECTATOR) return false;
        if(machine==null) return false;
        var entity=Bukkit.getEntity(machine);
        return entity!=null && entity.isValid() && entity.getWorld()==player.getWorld() && entity.getLocation().distanceSquared(player.getLocation())<=64;
    }
    void click(int slot) {
        if(closed || !usable()) return;
        if(slot==53) { Bukkit.getScheduler().runTask(plugin,()-> {
            if(player.getOpenInventory().getTopInventory().getHolder()!=this) return;
            if(usable()) player.openInventory(new GameSelection(machine).getInventory()); else player.closeInventory();
        }); return; }
        if(animation!=null) return;
        try {
            switch(slot) {
                case 21 -> selected=RouletteRules.Color.RED;
                case 22 -> selected=RouletteRules.Color.GREEN;
                case 23 -> selected=RouletteRules.Color.BLACK;
                case 29 -> bet=SlotMenu.adjustBet(bet,true,10);
                case 30 -> bet=SlotMenu.adjustBet(bet,true,100);
                case 32 -> bet=SlotMenu.adjustBet(bet,false,100);
                case 33 -> bet=SlotMenu.adjustBet(bet,false,10);
                case 31 -> spin();
                default -> { return; }
            }
            refresh();
        } catch(IllegalStateException | ArithmeticException e) { player.sendMessage("Nicht genug Guthaben oder Kontolimit erreicht."); }
        catch(IOException e) { player.sendMessage("Kontodaten konnten nicht gespeichert werden."); plugin.getLogger().log(java.util.logging.Level.SEVERE,"Roulette-Kontofehler",e); }
    }
    private void spin() throws IOException {
        int number=ThreadLocalRandom.current().nextInt(37);
        long payout=RouletteRules.payout(bet,selected,number);
        rollingBalance = accounts.casinoBalance(player) - bet;
        accounts.settleSpin(player,bet,payout,"Roulette");
        var color=RouletteRules.color(number);
        List<Integer> stops=new ArrayList<>();
        for(int i=0;i<RING.length;i++) if(ringColor(i)==color) stops.add(i);
        int stop=stops.get(ThreadLocalRandom.current().nextInt(stops.size()));
        int steps=RING.length*2+stop;
        pendingResult="Ergebnis: "+name(color)+" · Auszahlung: "+Money.format(payout);
        animation=new BukkitRunnable() {
            int frame;
            @Override public void run() {
                if(closed || !player.isOnline() || player.getOpenInventory().getTopInventory().getHolder()!=RouletteMenu.this || !usable()) { close(); return; }
                ring();
                inventory.setItem(RING[frame%RING.length],ExchangeMenu.icon(Material.QUARTZ,"Kugel"));
                player.playSound(player.getLocation(),Sound.UI_BUTTON_CLICK,.2f,1.3f);
                if(frame++>=steps) {
                    cancel(); animation=null;
                    result=pendingResult;
                    // Keep the ball on the final field. Replacing it in this same tick
                    // made the previous (usually opposite-colour) field look like the stop.
                    var icon=ExchangeMenu.icon(Material.QUARTZ,"Kugel · " + name(color));
                    var meta=icon.getItemMeta();
                    meta.lore(List.of(Component.text("Gesetzt auf: " + name(selected)),
                            Component.text("Auszahlung: " + Money.format(payout))));
                    meta.setEnchantmentGlintOverride(false); icon.setItemMeta(meta);
                    inventory.setItem(RING[stop],icon);
                    try { refresh(); } catch(IOException e) { player.sendMessage("Kontostand konnte nicht angezeigt werden."); }
                    player.sendMessage(result);
                }
            }
        }.runTaskTimer(plugin,1L,2L);
    }
    void close() { closed=true; if(animation!=null) { animation.cancel(); animation=null;
                    result=pendingResult; player.sendMessage(pendingResult+" (Runde bereits abgerechnet)"); } }
    @Override public Inventory getInventory() { return inventory; }
}



