package de.casino;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.view.AnvilView;

/** Allow vanilla combinations beyond the server limit while keeping the vanilla client usable. */
final class AnvilImprovements implements Listener {
    static int visibleCost(int cost) { return Math.min(cost, 39); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void open(InventoryOpenEvent event) {
        if (event.getView() instanceof AnvilView anvil) anvil.setMaximumRepairCost(Integer.MAX_VALUE);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void prepare(PrepareAnvilEvent event) {
        AnvilView anvil = event.getView();
        anvil.setMaximumRepairCost(Integer.MAX_VALUE);
        // Do not invent results or alter material consumption/enchantment compatibility.
        if (event.getResult() != null && !event.getResult().getType().isAir())
            anvil.setRepairCost(visibleCost(anvil.getRepairCost()));
    }
}
