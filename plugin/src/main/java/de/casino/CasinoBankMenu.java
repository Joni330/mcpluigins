package de.casino;

import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import java.io.IOException;
import java.util.*;

final class CasinoBankMenu implements InventoryHolder {
    final UUID machine;
    private final Accounts accounts;
    private final Chips chips;
    private final Inventory inventory;
    private long amount = 0;
    CasinoBankMenu(UUID machine, Accounts accounts, Chips chips, Player player) throws IOException {
        this.machine = machine; this.accounts = accounts; this.chips = chips;
        inventory = Bukkit.createInventory(this, 54, Component.text("Hauptkonto ↔ Casino"));
        for(int i=0;i<54;i++) inventory.setItem(i, ExchangeMenu.icon(Material.GRAY_STAINED_GLASS_PANE," "));
        int[] slots={19,20,21,23,24,25}; String[] labels={"+1€","+10€","+100€","−1€","−10€","−100€"};
        for(int i=0;i<slots.length;i++) inventory.setItem(slots[i],ExchangeMenu.icon(i<3?Material.LIME_STAINED_GLASS_PANE:Material.RED_STAINED_GLASS_PANE,labels[i]));
        inventory.setItem(30,ExchangeMenu.icon(Material.LIME_WOOL,"Betrag ins Casino einzahlen"));
        inventory.setItem(32,ExchangeMenu.icon(Material.RED_WOOL,"Betrag aufs Hauptkonto auszahlen"));
        inventory.setItem(39,ExchangeMenu.icon(Material.CHEST,"Gesamtes Hauptguthaben ins Casino einzahlen"));
        inventory.setItem(41,ExchangeMenu.icon(Material.ENDER_CHEST,"Gesamtes Casino-Guthaben auszahlen"));
        inventory.setItem(53,ExchangeMenu.icon(Material.ARROW,"Zurück zum Wechselautomaten"));
        inventory.setItem(28,ExchangeMenu.icon(Material.LIME_STAINED_GLASS_PANE,"+0,01€"));
        inventory.setItem(34,ExchangeMenu.icon(Material.RED_STAINED_GLASS_PANE,"−0,01€"));
        inventory.setItem(37,ExchangeMenu.icon(Material.LIME_STAINED_GLASS_PANE,"+0,10€"));
        inventory.setItem(43,ExchangeMenu.icon(Material.RED_STAINED_GLASS_PANE,"−0,10€"));
        refresh(player);
    }
    private long chipValue(ItemStack[] items) {
        long value=0;
        for(ItemStack item:items) { var kind=chips.kind(item); if(kind!=null) value=Math.addExact(value,Math.multiplyExact((long)item.getAmount(),kind.euros*100L)); }
        return value;
    }
    private void refresh(Player player) throws IOException {
        inventory.setItem(11,ExchangeMenu.icon(Material.GOLD_INGOT,"Hauptkonto: "+Money.format(accounts.balance(player))));
        inventory.setItem(15,ExchangeMenu.icon(Material.GOLD_BLOCK,"Casino-Guthaben: "+Money.format(accounts.casinoBalance(player))));
        inventory.setItem(22,ExchangeMenu.icon(Material.PAPER,"Umbuchungsbetrag: "+Money.format(amount)));
        inventory.setItem(49,ExchangeMenu.icon(Material.PAPER,"Alle Chips im Inventar einlösen: "+Money.format(chipValue(player.getInventory().getStorageContents()))+" Casino-Guthaben"));
    }
    void click(int slot, Player player) throws IOException {
        long change=switch(slot) { case 19->100; case 20->1000; case 21->10000; case 23->-100; case 24->-1000; case 25->-10000; case 28->1; case 34->-1; case 37->10; case 43->-10; default->0; };
        if(change!=0) amount=Math.clamp(amount+change,0,100000000L);
        else if(slot==30 || slot==32 || slot==39 || slot==41) {
            long value=slot==39?accounts.balance(player):slot==41?accounts.casinoBalance(player):amount;
            accounts.move(player.getUniqueId(),value,slot==30 || slot==39);
            player.sendMessage(Money.format(value)+" erfolgreich "+((slot==30 || slot==39)?"ins Casino eingezahlt.":"aufs Hauptkonto ausgezahlt."));
        } else if(slot==49) {
            ItemStack[] before=player.getInventory().getStorageContents(); long value=chipValue(before);
            if(value==0) { player.sendMessage("Keine Casino-Chips im Inventar."); return; }
            ItemStack[] after=Arrays.stream(before).map(i->i==null?null:i.clone()).toArray(ItemStack[]::new);
            for(int i=0;i<after.length;i++) if(chips.kind(after[i])!=null) after[i]=null;
            accounts.changeCasino(player.getUniqueId(),value,"Chips eingelöst");
            player.getInventory().setStorageContents(after);
            player.sendMessage("Chips für "+Money.format(value)+" Casino-Guthaben eingelöst.");
        }
        refresh(player);
    }
    @Override public Inventory getInventory() { return inventory; }
}
