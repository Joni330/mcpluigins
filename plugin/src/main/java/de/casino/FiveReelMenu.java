package de.casino;

import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

final class FiveReelMenu implements InventoryHolder {
    final UUID machine;
    private final CasinoPlugin plugin;
    private final Accounts accounts;
    private final Player player;
    private final Inventory inventory;
    private static final Material[] SYMBOLS = {Material.COPPER_INGOT, Material.IRON_INGOT, Material.GOLD_INGOT,
            Material.DIAMOND, Material.EMERALD, Material.NETHERITE_INGOT, Material.GOLD_BLOCK};
    private long bet = 100;
    private BukkitTask animation;
    private boolean closed;
    private long rollingBalance;
    private String pendingResult;
    private String result = "Noch keine Runde gespielt";
    private ItemStack[] gameContents;
    FiveReelMenu(CasinoPlugin plugin, Accounts accounts, Player player, UUID machine) throws IOException {
        this.plugin = plugin; this.accounts = accounts; this.player = player; this.machine = machine;
        inventory = Bukkit.createInventory(this, 54, Component.text("Casino · Fünf Walzen"));
        for (int i = 0; i < 54; i++) inventory.setItem(i, ExchangeMenu.icon(Material.BLACK_STAINED_GLASS_PANE, " "));
        for (int column = 0; column < 5; column++) {
            inventory.setItem(11 + column, ExchangeMenu.icon(Material.IRON_INGOT, "Walze · Obere Reihe"));
            inventory.setItem(20 + column, ExchangeMenu.icon(Material.GOLD_INGOT, "Gewinnlinie · Mitte"));
            inventory.setItem(29 + column, ExchangeMenu.icon(Material.IRON_INGOT, "Walze · Untere Reihe"));
        }
        inventory.setItem(46, ExchangeMenu.icon(Material.LIME_STAINED_GLASS_PANE, "Einsatz +1,00€"));
        inventory.setItem(48, ExchangeMenu.icon(Material.RED_STAINED_GLASS_PANE, "Einsatz −1,00€"));
        inventory.setItem(53, ExchangeMenu.icon(Material.BARRIER, "Zurück zur Spielauswahl"));
        inventory.setItem(51, ExchangeMenu.icon(Material.BOOK, "Gewinnübersicht"));
        refresh();
    }
    boolean usable() {
        if (player.getGameMode() == GameMode.SPECTATOR) return false;
        if (machine == null) return false;
        var entity = Bukkit.getEntity(machine);
        return entity != null && entity.isValid() && entity.getWorld() == player.getWorld()
                && entity.getLocation().distanceSquared(player.getLocation()) <= 64;
    }
    private void refresh() throws IOException {
        inventory.setItem(19, ExchangeMenu.icon(Material.LIME_WOOL, animation == null ? "Spin" : "Walzen drehen …"));
        ItemStack paper = ExchangeMenu.icon(Material.PAPER, "Einsatz: " + Money.format(bet));
        var meta = paper.getItemMeta();
        meta.lore(java.util.List.of(Component.text("Nur ganze Euro · 1,00€ bis 10,00€")));
        paper.setItemMeta(meta); inventory.setItem(47, paper);
        ItemStack balance = ExchangeMenu.icon(Material.GOLD_BLOCK, "Casino-Guthaben: " + Money.format(animation == null ? accounts.casinoBalance(player) : rollingBalance));
        meta = balance.getItemMeta(); meta.lore(java.util.List.of(Component.text(result))); balance.setItemMeta(meta); inventory.setItem(50, balance);
    }
    void click(int slot) {
        if (closed || !usable()) return;
        if (gameContents != null) {
            if (slot == 53) {
                inventory.setContents(gameContents); gameContents = null;
                player.getOpenInventory().setTitle("Casino · Fünf Walzen");
                try { refresh(); } catch (IOException error) { player.sendMessage("Kontostand konnte nicht geladen werden."); }
            }
            return;
        }
        if (slot == 53) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.getOpenInventory().getTopInventory().getHolder() != this) return;
                if (usable()) player.openInventory(new GameSelection(machine).getInventory()); else player.closeInventory();
            }); return;
        }
        if (animation != null) return;
        if (slot == 51) { showPayouts(); return; }
        try {
            if (slot == 46) bet = adjustBet(bet, true);
            else if (slot == 48) bet = adjustBet(bet, false);
            else if (slot == 19) spin();
            else return;
            refresh();
        } catch (IllegalStateException | ArithmeticException error) { player.sendMessage("Nicht genug Guthaben oder Kontolimit erreicht."); }
        catch (IOException error) { player.sendMessage("Kontodaten konnten nicht gespeichert werden."); plugin.getLogger().log(java.util.logging.Level.SEVERE, "Fünf-Walzen-Kontofehler", error); }
    }
    private void showPayouts() {
        gameContents = inventory.getContents();
        inventory.clear();
        for (int i = 0; i < 54; i++) inventory.setItem(i, ExchangeMenu.icon(Material.BLACK_STAINED_GLASS_PANE, " "));
        String[] names = {"Kupfer", "Eisen", "Gold", "Diamant", "Smaragd", "Netherit", "Goldblock"};
        for (int symbol = 0; symbol < SYMBOLS.length; symbol++) {
            inventory.setItem(10 + symbol, ExchangeMenu.icon(SYMBOLS[symbol], names[symbol]));
            for (int matches = 3; matches <= 5; matches++) {
                int[] line = {symbol, symbol, symbol, symbol, symbol};
                if (matches < 5) line[matches] = (symbol + 1) % SYMBOLS.length;
                long atOneEuro = FiveReelRules.payout(100, line);
                String multiplier = java.math.BigDecimal.valueOf(atOneEuro, 2).stripTrailingZeros().toPlainString().replace('.', ',');
                ItemStack icon = ExchangeMenu.icon(SYMBOLS[symbol], matches + " gleiche · ×" + multiplier);
                var meta = icon.getItemMeta();
                meta.lore(java.util.List.of(Component.text("Bei " + Money.format(bet) + " Einsatz: " + Money.format(FiveReelRules.payout(bet, line))),
                        Component.text("Auszahlung einschließlich Einsatz")));
                icon.setItemMeta(meta);
                inventory.setItem(19 + (matches - 3) * 9 + symbol, icon);
            }
        }
        ItemStack rules = ExchangeMenu.icon(Material.BOOK, "So werden Gewinne gewertet");
        var meta = rules.getItemMeta();
        meta.lore(java.util.List.of(Component.text("Nur die mittlere Reihe zählt."),
                Component.text("Mindestens drei gleiche Symbole direkt von links."),
                Component.text("Es zählt nur die längste passende Folge."),
                Component.text("Reihen unten: 3 / 4 / 5 gleiche Symbole"),
                Component.text("Langfristige Auszahlungsquote: 89,9 %")));
        rules.setItemMeta(meta); inventory.setItem(49, rules);
        inventory.setItem(53, ExchangeMenu.icon(Material.ARROW, "Zurück zum Spiel"));
        player.getOpenInventory().setTitle("Fünf Walzen · Gewinnübersicht");
    }
    static long adjustBet(long current, boolean increase) { return Math.clamp(current + (increase ? 100 : -100), 100, 1000); }
    private Material randomSymbol() { return SYMBOLS[ThreadLocalRandom.current().nextInt(SYMBOLS.length)]; }
    private void spin() throws IOException {
        FiveReelRules.Play play = FiveReelRules.draw(bet, ThreadLocalRandom.current());
        rollingBalance = accounts.casinoBalance(player) - bet;
        accounts.settleSpin(player, bet, play.payout(), "Fünf Walzen");
        player.getOpenInventory().setTitle("Casino · Fünf Walzen");
        for (int slot : new int[]{18, 26, 40})
            inventory.setItem(slot, ExchangeMenu.icon(Material.BLACK_STAINED_GLASS_PANE, " "));
        pendingResult = "Letzte Auszahlung: " + Money.format(play.payout());
        Material[][] finalReels = new Material[3][5];
        for (int row = 0; row < 3; row++) for (int col = 0; col < 5; col++) finalReels[row][col] = randomSymbol();
        int[] middle = play.middle();
        for (int col = 0; col < 5; col++) finalReels[1][col] = SYMBOLS[middle[col]];
        animation = new BukkitRunnable() {
            int frame;
            @Override public void run() {
                if (closed || !player.isOnline() || !usable() || player.getOpenInventory().getTopInventory().getHolder() != FiveReelMenu.this) { close(); return; }
                for (int col = 0; col < 5; col++) {
                    int stop = 14 + col * 5;
                    if (frame <= stop) for (int row = 0; row < 3; row++)
                        inventory.setItem(11 + row * 9 + col, ExchangeMenu.icon(frame == stop ? finalReels[row][col] : randomSymbol(), row == 1 ? "Gewinnlinie · Mitte" : "Walze"));
                }
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, .2f, 1.3f);
                if (frame++ >= 35) {
                    cancel(); animation = null;
                    int matches = 1;
                    while (matches < 5 && finalReels[1][matches] == finalReels[1][0]) matches++;
                    result = pendingResult;
                    try { refresh(); } catch (IOException error) { player.sendMessage("Kontostand konnte nicht angezeigt werden."); }
                    showOutcome(play.payout(), matches);
                    player.sendMessage(result);
                }
            }
        }.runTaskTimer(plugin, 1L, 3L);
    }
    private void showOutcome(long payout, int matches) {
        if (payout <= 0) {
            inventory.setItem(40, ExchangeMenu.icon(Material.PAPER, "Keine Auszahlung · Nächste Runde mit Spin"));
            return;
        }
        String label = "Auszahlung: " + Money.format(payout);
        for (int col = 0; col < matches; col++) {
            ItemStack symbol = inventory.getItem(20 + col);
            if (symbol == null) continue;
            var meta = symbol.getItemMeta();
            meta.setEnchantmentGlintOverride(true);
            meta.displayName(Component.text("Treffer · " + label, net.kyori.adventure.text.format.NamedTextColor.GOLD));
            symbol.setItemMeta(meta);
            inventory.setItem(20 + col, symbol);
        }
        for (int slot : new int[]{18, 26})
            inventory.setItem(slot, ExchangeMenu.icon(Material.LIME_STAINED_GLASS_PANE, "Gewinnlinie · " + label));
        player.getOpenInventory().setTitle("Fünf Walzen · " + label);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, .8f, 1.2f);
    }
    void close() { closed = true; if (animation != null) { animation.cancel(); animation = null; player.sendMessage(pendingResult + " (Runde bereits abgerechnet)"); } }
    @Override public Inventory getInventory() { return inventory; }
}





