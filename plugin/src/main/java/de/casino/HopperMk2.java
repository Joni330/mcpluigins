package de.casino;

import java.util.*;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Speeds up vanilla transfers without moving items outside vanilla inventory events. */
final class HopperMk2 implements Listener {
    private final JavaPlugin plugin;
    private final NamespacedKey marker,tierKey; private final List<NamespacedKey> recipes=new ArrayList<>(); private boolean bonus;
    private final Set<Location> loaded=new HashSet<>();
    private BukkitTask task;
    HopperMk2(JavaPlugin plugin){this.plugin=plugin;marker=new NamespacedKey(plugin,"hopper_mk2");tierKey=new NamespacedKey(plugin,"machine_tier");}
    ItemStack item(){return item(Material.HOPPER,2);}
    ItemStack item(Material type,int tier){
        ItemStack item=ExchangeMenu.icon(type,(type==Material.HOPPER?"Hopper":"Ofen")+" MK"+tier);var meta=item.getItemMeta();
        meta.getPersistentDataContainer().set(marker,PersistentDataType.BYTE,(byte)1);meta.getPersistentDataContainer().set(tierKey,PersistentDataType.INTEGER,tier);
        meta.lore(List.of(Component.text("Geschwindigkeit: bis zu "+(1<<tier)+"×")));item.setItemMeta(meta);return item;
    }
    private int tier(org.bukkit.persistence.PersistentDataContainer data){return Math.max(2,Math.min(4,data.getOrDefault(tierKey,PersistentDataType.INTEGER,2)));}
    void enable(){
        Bukkit.getPluginManager().registerEvents(this,plugin);
        for(int tier=2;tier<=4;tier++)for(Material type:List.of(Material.HOPPER,Material.FURNACE)){
            NamespacedKey key=new NamespacedKey(plugin,(type==Material.HOPPER?"hopper":"furnace")+"_mk"+tier+"_recipe");recipes.add(key);
            ShapedRecipe craft=new ShapedRecipe(key,item(type,tier));craft.shape(type==Material.HOPPER?new String[]{"I I","IHI"," I "}:new String[]{"III","IHI","III"});
            craft.setIngredient('I',tier==2?Material.IRON_BLOCK:tier==3?Material.GOLD_BLOCK:Material.DIAMOND_BLOCK);craft.setIngredient('H',type);
            Bukkit.removeRecipe(key);Bukkit.addRecipe(craft);
        }
        for(World world:Bukkit.getWorlds())for(Chunk chunk:world.getLoadedChunks())scan(chunk);
        Bukkit.getOnlinePlayers().forEach(p->p.discoverRecipes(recipes));
        task=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,1,1);
    }
    void disable(){if(task!=null)task.cancel();loaded.clear();recipes.forEach(Bukkit::removeRecipe);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void transfer(InventoryMoveItemEvent event){
        if(bonus||event.getItem().getAmount()!=1)return;
        Location at=event.getInitiator().getLocation();
        if(at==null||!(at.getBlock().getState() instanceof Hopper hopper)||!marked(at.getBlock())||tier(hopper.getPersistentDataContainer())!=4)return;
        Inventory source=event.getSource(),destination=event.getDestination();ItemStack prototype=event.getItem().clone();
        Bukkit.getScheduler().runTask(plugin,()->{
            if(event.isCancelled()||!at.getWorld().isChunkLoaded(at.getBlockX()>>4,at.getBlockZ()>>4)||!marked(at.getBlock()))return;
            if(!(at.getBlock().getState() instanceof Hopper current)||tier(current.getPersistentDataContainer())!=4||!((org.bukkit.block.data.type.Hopper)at.getBlock().getBlockData()).isEnabled())return;
            if(!live(source)||!live(destination)||source.equals(destination))return;
            int[] from=slots(source,true,prototype,at),to=slots(destination,false,prototype,at);
            for(int slot:from){ItemStack stack=source.getItem(slot);if(BotInventory.empty(stack)||!stack.isSimilar(prototype))continue;
                for(int target:to){ItemStack existing=destination.getItem(target);int limit=Math.min(destination.getMaxStackSize(),prototype.getMaxStackSize());
                    if(!BotInventory.empty(existing)&&(!existing.isSimilar(prototype)||existing.getAmount()>=limit))continue;
                    ItemStack sourceBefore=stack.clone(),targetBefore=existing==null?null:existing.clone();
                    InventoryMoveItemEvent extra=new InventoryMoveItemEvent(source,prototype.clone(),destination,event.getInitiator().equals(source));
                    bonus=true;try{Bukkit.getPluginManager().callEvent(extra);}finally{bonus=false;}
                    if(extra.isCancelled()||!prototype.equals(extra.getItem())||!live(source)||!live(destination))return;
                    // Other listeners may have changed either slot during the event.
                    ItemStack fresh=source.getItem(slot),freshTarget=destination.getItem(target);
                    if(!Objects.equals(sourceBefore,fresh)||!Objects.equals(targetBefore,freshTarget))return;
                    ItemStack remainder=fresh.clone();remainder.setAmount(remainder.getAmount()-1);
                    ItemStack added=BotInventory.empty(freshTarget)?prototype.clone():freshTarget.clone();if(!BotInventory.empty(freshTarget))added.setAmount(added.getAmount()+1);
                    source.setItem(slot,remainder.getAmount()==0?null:remainder);destination.setItem(target,added);return;
                }
            }
        });
    }
    private boolean live(Inventory inventory){
        Location location=inventory.getLocation();if(location==null||!location.getWorld().isChunkLoaded(location.getBlockX()>>4,location.getBlockZ()>>4))return false;
        return location.getBlock().getState() instanceof Container container&&container.getInventory().equals(inventory);
    }
    private int[] slots(Inventory inventory,boolean source,ItemStack item,Location hopper){
        if(inventory.getType()==InventoryType.FURNACE){
            if(source)return item.getType()==Material.BUCKET?new int[]{1}:new int[]{2};
            Location at=inventory.getLocation();return new int[]{hopper.getBlockY()>at.getBlockY()?0:1};
        }
        if(!Set.of(InventoryType.CHEST,InventoryType.BARREL,InventoryType.HOPPER,InventoryType.DROPPER,InventoryType.DISPENSER).contains(inventory.getType()))return new int[0];
        return java.util.stream.IntStream.range(0,inventory.getSize()).toArray();
    }
    private boolean marked(Block block){return block.getState() instanceof Container hopper&&hopper.getPersistentDataContainer().has(marker,PersistentDataType.BYTE);}
    private void scan(Chunk chunk){for(BlockState state:chunk.getTileEntities())if(state instanceof Container hopper&&hopper.getPersistentDataContainer().has(marker,PersistentDataType.BYTE))loaded.add(state.getLocation());}
    private void tick(){
        for(Location location:List.copyOf(loaded)){
            World world=location.getWorld();if(!world.isChunkLoaded(location.getBlockX()>>4,location.getBlockZ()>>4)){loaded.remove(location);continue;}
            Block block=location.getBlock();if(!(block.getState() instanceof Container machine)||!machine.getPersistentDataContainer().has(marker,PersistentDataType.BYTE)){loaded.remove(location);continue;}
            int tier=tier(machine.getPersistentDataContainer());if(machine instanceof Furnace furnace){double speed=FurnaceSpeed.multiplier(tier);if(furnace.getCookSpeedMultiplier()!=speed){furnace.setCookSpeedMultiplier(speed);furnace.setCookTime((short)0);furnace.update(false,false);}continue;}if(!(machine instanceof Hopper hopper))continue;
            if(block.getBlockData() instanceof org.bukkit.block.data.type.Hopper data&&!data.isEnabled())continue;
            int cooldown=tier==2?2:1;if(hopper.getTransferCooldown()>cooldown){hopper.setTransferCooldown(cooldown);hopper.update(false,false);}
        }
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true) public void place(BlockPlaceEvent event){
        var meta=event.getItemInHand().getItemMeta();if(meta==null||!meta.getPersistentDataContainer().has(marker,PersistentDataType.BYTE))return;
        if(!(event.getBlock().getState() instanceof Container hopper)){event.setCancelled(true);return;}
        hopper.getPersistentDataContainer().set(new NamespacedKey(plugin,"machine_owner"),PersistentDataType.STRING,event.getPlayer().getUniqueId().toString());
        hopper.getPersistentDataContainer().set(marker,PersistentDataType.BYTE,(byte)1);int tier=tier(meta.getPersistentDataContainer());hopper.getPersistentDataContainer().set(tierKey,PersistentDataType.INTEGER,tier);hopper.customName(Component.text((hopper instanceof Hopper?"Hopper":"Ofen")+" MK"+tier));if(hopper instanceof Furnace furnace)furnace.setCookSpeedMultiplier(FurnaceSpeed.multiplier(tier));
        if(!hopper.update(false,false)){event.setCancelled(true);return;}loaded.add(hopper.getLocation());
    }
    // Replace only the block drop. Vanilla still drops the stored inventory and handles protection.
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true) public void drop(BlockDropItemEvent event){
        if(!(event.getBlockState() instanceof Container hopper)||!hopper.getPersistentDataContainer().has(marker,PersistentDataType.BYTE))return;
        for(var entity:event.getItems())if(entity.getItemStack().getType()==hopper.getType()&&entity.getItemStack().getAmount()==1){entity.setItemStack(item(hopper.getType(),tier(hopper.getPersistentDataContainer())));break;}
        loaded.remove(event.getBlock().getLocation());
    }
    @EventHandler public void load(ChunkLoadEvent event){scan(event.getChunk());}
    @EventHandler public void unload(ChunkUnloadEvent event){loaded.removeIf(l->l.getWorld()==event.getWorld()&&(l.getBlockX()>>4)==event.getChunk().getX()&&(l.getBlockZ()>>4)==event.getChunk().getZ());}
    @EventHandler public void join(PlayerJoinEvent event){event.getPlayer().discoverRecipes(recipes);}
    @EventHandler(ignoreCancelled=true) public void explode(EntityExplodeEvent event){event.blockList().removeIf(this::marked);}
    @EventHandler(ignoreCancelled=true) public void explode(BlockExplodeEvent event){event.blockList().removeIf(this::marked);}
    @EventHandler(ignoreCancelled=true) public void piston(BlockPistonExtendEvent event){if(event.getBlocks().stream().anyMatch(this::marked))event.setCancelled(true);}
    @EventHandler(ignoreCancelled=true) public void piston(BlockPistonRetractEvent event){if(event.getBlocks().stream().anyMatch(this::marked))event.setCancelled(true);}
}
