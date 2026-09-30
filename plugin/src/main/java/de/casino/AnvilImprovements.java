package de.casino;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import java.util.Objects;

/** Allow vanilla combinations beyond the server limit while keeping the vanilla client usable. */
final class AnvilImprovements implements Listener {
    static int visibleCost(int cost) { return Math.min(cost, 39); }
    static int materialCost(int original,int materials,boolean renamed) {
        return Math.min(visibleCost(original),Math.max(1,materials)+(renamed?1:0));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void open(InventoryOpenEvent event) {
        if (event.getView() instanceof AnvilView anvil) anvil.setMaximumRepairCost(Integer.MAX_VALUE);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void prepare(PrepareAnvilEvent event) {
        AnvilView anvil = event.getView();
        anvil.setMaximumRepairCost(Integer.MAX_VALUE);
        // Do not invent results or alter material consumption/enchantment compatibility.
        ItemStack result=event.getResult();
        if (result == null || result.getType().isAir()) return;
        int cost=visibleCost(anvil.getRepairCost());
        ItemStack input=event.getInventory().getItem(0),material=event.getInventory().getItem(1);
        if(input!=null&&material!=null&&!material.getType().isAir()
                &&input.getType()==result.getType()&&input.isRepairableBy(material)
                &&input.getItemMeta() instanceof Damageable before
                &&result.getItemMeta() instanceof Damageable after
                &&after.getDamage()<before.getDamage()&&anvil.getRepairItemCountCost()>0){
            boolean renamed=!Objects.equals(input.getItemMeta().displayName(),result.getItemMeta().displayName());
            cost=materialCost(cost,anvil.getRepairItemCountCost(),renamed);
        }
        anvil.setRepairCost(cost);
    }
}
