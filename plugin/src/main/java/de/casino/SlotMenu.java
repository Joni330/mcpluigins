package de.casino;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;

final class SlotMenu implements InventoryHolder {
    final java.util.UUID machine;
    static final int SPIN = 12, PAPER = 19, INCREASE = 27, DECREASE = 29, BALANCE = 40, BACK = 53;
    static final int INCREASE_EURO = 36, DECREASE_EURO = 38, LAST_WIN = 33;
    private static final int[] REELS = {14, 15, 16};
    private static final Material[] SYMBOLS = {Material.REDSTONE, Material.COPPER_INGOT, Material.IRON_INGOT, Material.GOLD_INGOT, Material.DIAMOND, Material.EMERALD, Material.NETHERITE_INGOT, Material.GOLD_BLOCK};
    private final Inventory inventory;
    private final CasinoPlugin plugin;
    private final Accounts accounts;
    private final Player player;
    private long bet = Money.MIN_SPIN_CENTS;
    private BukkitTask animation;
    private boolean closed;
    private long displayedPayout;

    SlotMenu(CasinoPlugin plugin, Accounts accounts, Player player) throws IOException {
        this(plugin, accounts, player, null);
    }

    SlotMenu(CasinoPlugin plugin, Accounts accounts, Player player, java.util.UUID machine) throws IOException {
        this.machine = machine;
        this.plugin = plugin;
        this.accounts = accounts;
        this.player = player;
        displayedPayout = accounts.lastPayout(player);
        inventory = Bukkit.createInventory(this, 54, Component.text("Spielautomat"));
        for (int i = 0; i < 54; i++) inventory.setItem(i, ExchangeMenu.icon(Material.BLACK_STAINED_GLASS_PANE, " "));
        for (int slot : REELS) inventory.setItem(slot, ExchangeMenu.icon(Material.IRON_INGOT, "Walze"));
        inventory.setItem(BACK, ExchangeMenu.icon(Material.RED_WOOL, "Zurück"));
        refresh();
    }

    static long adjustBet(long bet, boolean increase) {
        return adjustBet(bet, increase, 10);
    }

    static long adjustBet(long bet, boolean increase, long step) {
        return Math.clamp(bet + (increase ? step : -step), Money.MIN_SPIN_CENTS, Money.MAX_SPIN_CENTS);
    }

    private void refresh() throws IOException {
        inventory.setItem(PAPER, ExchangeMenu.icon(Material.PAPER, "Einsatz pro Spin: " + Money.format(bet)));
        inventory.setItem(INCREASE, ExchangeMenu.icon(Material.LIME_STAINED_GLASS_PANE, "Einsatz erhöhen (+0,10€)"));
        inventory.setItem(DECREASE, ExchangeMenu.icon(Material.RED_STAINED_GLASS_PANE, "Einsatz verringern (−0,10€)"));
        inventory.setItem(INCREASE_EURO, ExchangeMenu.icon(Material.LIME_STAINED_GLASS_PANE, "Einsatz erhöhen (+1,00€)"));
        inventory.setItem(DECREASE_EURO, ExchangeMenu.icon(Material.RED_STAINED_GLASS_PANE, "Einsatz verringern (−1,00€)"));
        inventory.setItem(LAST_WIN, ExchangeMenu.icon(Material.GOLD_INGOT, "Letzter Gewinn: " + Money.format(displayedPayout)));
        inventory.setItem(BALANCE, ExchangeMenu.icon(Material.GOLD_BLOCK, "Dein Konto: " + Money.format(accounts.balance(player))));
        inventory.setItem(SPIN, ExchangeMenu.icon(Material.LIME_WOOL, animation == null ? "Spin" : "Walzen drehen …"));
    }

    void click(int slot) {
        if (closed) return;
        if (machine != null) {
            var entity = Bukkit.getEntity(machine);
            if (entity == null || !entity.isValid() || entity.getWorld() != player.getWorld()
                    || entity.getLocation().distanceSquared(player.getLocation()) > 64) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.getOpenInventory().getTopInventory().getHolder() == this) player.closeInventory();
                });
                return;
            }
        }
        if (slot == BACK) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.getOpenInventory().getTopInventory().getHolder() == this) player.closeInventory();
            });
            return;
        }
        if (animation != null) return;
        try {
            if (slot == INCREASE || slot == DECREASE) bet = adjustBet(bet, slot == INCREASE);
            if (slot == INCREASE_EURO || slot == DECREASE_EURO) bet = adjustBet(bet, slot == INCREASE_EURO, 100);
            refresh();
            if (slot == SPIN) spin();
        } catch (IllegalStateException | ArithmeticException error) {
            player.sendMessage("Spin nicht möglich: Guthaben zu niedrig oder Kontolimit erreicht.");
        } catch (IOException error) {
            player.sendMessage("Dein Konto konnte nicht geladen werden.");
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Spielautomaten-Konto konnte nicht geladen werden", error);
        }
    }

    private void spin() throws IOException {
        if (accounts.balance(player) < bet) throw new IllegalStateException("Nicht genug Guthaben");
        SlotRules.Play play = SlotRules.draw(bet, ThreadLocalRandom.current());
        // Die gesamte Respin-Kette wird vor der Animation atomar gespeichert.
        // Schließen, Disconnect oder Neustart können keinen Gewinn verlieren oder doppelt buchen.
        accounts.settleSpin(player, bet, play.payout());
        animation = new BukkitRunnable() {
            int frame, round;
            @Override public void run() {
                if (closed || !player.isOnline() || player.getOpenInventory().getTopInventory().getHolder() != SlotMenu.this) {
                    close(); return;
                }
                SlotRules.Round result = play.rounds().get(round);
                for (int reel = 0; reel < REELS.length; reel++) {
                    int stop = 12 + reel * 5;
                    if (frame <= stop) {
                        Material symbol = frame == stop ? result.reels().get(reel)
                                : SYMBOLS[ThreadLocalRandom.current().nextInt(SYMBOLS.length)];
                        inventory.setItem(REELS[reel], ExchangeMenu.icon(symbol, "Walze " + (reel + 1)));
                    }
                }
                if (frame <= 22) player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, .25f, 1.4f);
                if (++frame >= 30) {
                    if (result.outcome() == SlotRules.Outcome.REDSTONE) {
                        player.sendMessage("Drei Redstone! Kostenloser Respin mit " + Money.format(bet) + " Einsatz.");
                        round++; frame = 0;
                    } else {
                        cancel(); animation = null;
                        displayedPayout = play.payout();
                        try { refresh(); } catch (IOException error) { player.sendMessage("Kontostand konnte nicht aktualisiert werden."); }
                        player.sendMessage(play.payout() > 0 ? "Auszahlung: " + Money.format(play.payout()) : "Keine Auszahlung bei diesem Spin.");
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 3L);
        refresh();
    }

    void close() {
        closed = true;
        if (animation != null) { animation.cancel(); animation = null; }
    }
    @Override public Inventory getInventory() { return inventory; }
}
