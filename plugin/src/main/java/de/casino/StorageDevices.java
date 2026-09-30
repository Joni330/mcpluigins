package de.casino;

import java.util.*;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Block PDC holds configuration; only loaded receiver chunks are processed. */
final class StorageDevices implements Listener {
    private final JavaPlugin plugin;private final StorageTerminals terminals;private final StorageTeams teams;
    private final NamespacedKey kindKey,ownerKey,idKey,linkKey,selectionKey,amountKey,trashRecipe,receiverRecipe;
    private final Set<Location> loaded=new HashSet<>(),failed=new HashSet<>();private BukkitTask task;
    private static final int[] TARGETS={16,64,256,1024};
    StorageDevices(JavaPlugin plugin,StorageTerminals terminals,StorageTeams teams){
        this.plugin=plugin;this.terminals=terminals;this.teams=teams;
        kindKey=new NamespacedKey(plugin,"storage_device");ownerKey=new NamespacedKey(plugin,"device_owner");idKey=new NamespacedKey(plugin,"device_id");
        linkKey=new NamespacedKey(plugin,"device_link");selectionKey=new NamespacedKey(plugin,"device_items");amountKey=new NamespacedKey(plugin,"device_target");
        trashRecipe=new NamespacedKey(plugin,"trash_recipe");receiverRecipe=new NamespacedKey(plugin,"receiver_recipe");
    }
    ItemStack item(boolean trash){
        ItemStack item=ExchangeMenu.icon(trash?Material.BARREL:Material.DISPENSER,trash?"Mülleimer":"Lager-Empfänger");var meta=item.getItemMeta();
        meta.getPersistentDataContainer().set(kindKey,PersistentDataType.STRING,trash?"trash":"receiver");
        meta.lore(List.of(Component.text(trash?"Trichter-Items werden dauerhaft gelöscht":"Mit verbundenem Lager-Handy schleichend rechtsklicken")));
        item.setItemMeta(meta);return item;
    }
    void enable(){
        Bukkit.getPluginManager().registerEvents(this,plugin);
        ShapedRecipe trash=new ShapedRecipe(trashRecipe,item(true));trash.shape(" I ","IBI"," L ");trash.setIngredient('I',Material.IRON_INGOT);trash.setIngredient('B',Material.BARREL);trash.setIngredient('L',Material.LAVA_BUCKET);
        ShapedRecipe receiver=new ShapedRecipe(receiverRecipe,item(false));receiver.shape("DED","RHR","DED");receiver.setIngredient('D',Material.DIAMOND);receiver.setIngredient('E',Material.ENDER_PEARL);receiver.setIngredient('R',Material.REDSTONE);receiver.setIngredient('H',Material.DISPENSER);
        Bukkit.removeRecipe(trashRecipe);Bukkit.removeRecipe(receiverRecipe);Bukkit.addRecipe(trash);Bukkit.addRecipe(receiver);
        for(World world:Bukkit.getWorlds())for(Chunk chunk:world.getLoadedChunks())scan(chunk);
        Bukkit.getOnlinePlayers().forEach(p->p.discoverRecipes(List.of(trashRecipe,receiverRecipe)));
        task=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,100,100);
    }
    void disable(){if(task!=null)task.cancel();for(Player p:Bukkit.getOnlinePlayers())if(p.getOpenInventory().getTopInventory().getHolder() instanceof Menu)p.closeInventory();Bukkit.removeRecipe(trashRecipe);Bukkit.removeRecipe(receiverRecipe);loaded.clear();}
    boolean isDevice(Block block){return kind(block)!=null;}
    private String kind(Block block){return block!=null&&block.getState() instanceof Container c?c.getPersistentDataContainer().get(kindKey,PersistentDataType.STRING):null;}
    private boolean trash(Inventory inventory){Location location=inventory.getLocation();return location!=null&&"trash".equals(kind(location.getBlock()));}
    private boolean allowed(Player p,Container c){try{return p.hasPermission("casino.admin")||teams.shares(UUID.fromString(c.getPersistentDataContainer().get(ownerKey,PersistentDataType.STRING)),p.getUniqueId());}catch(Exception ex){return false;}}
    private void scan(Chunk chunk){for(BlockState state:chunk.getTileEntities())if(state instanceof Container c&&c.getPersistentDataContainer().has(kindKey,PersistentDataType.STRING)){loaded.add(c.getLocation());if("trash".equals(kind(c.getBlock())))c.getInventory().clear();}}
    @EventHandler public void load(ChunkLoadEvent e){scan(e.getChunk());}
    @EventHandler public void unload(ChunkUnloadEvent e){loaded.removeIf(l->l.getWorld()==e.getWorld()&&(l.getBlockX()>>4)==e.getChunk().getX()&&(l.getBlockZ()>>4)==e.getChunk().getZ());}
    @EventHandler public void join(PlayerJoinEvent e){e.getPlayer().discoverRecipes(List.of(trashRecipe,receiverRecipe));}
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true) public void place(BlockPlaceEvent e){
        var meta=e.getItemInHand().getItemMeta();if(meta==null)return;String kind=meta.getPersistentDataContainer().get(kindKey,PersistentDataType.STRING);if(kind==null)return;
        if(!(e.getBlock().getState() instanceof Container c)){e.setCancelled(true);return;}
        c.getPersistentDataContainer().set(kindKey,PersistentDataType.STRING,kind);c.getPersistentDataContainer().set(ownerKey,PersistentDataType.STRING,e.getPlayer().getUniqueId().toString());c.getPersistentDataContainer().set(idKey,PersistentDataType.STRING,UUID.randomUUID().toString());
        c.customName(Component.text(kind.equals("trash")?"Mülleimer · löscht automatisch":"Lager-Empfänger"));if(!c.update(false,false)){e.setCancelled(true);return;}
        loaded.add(c.getLocation());failed.remove(c.getLocation());
    }
    @EventHandler(priority=EventPriority.HIGH) public void interact(PlayerInteractEvent e){
        if(e.getAction()!=Action.RIGHT_CLICK_BLOCK||!isDevice(e.getClickedBlock())||e.useInteractedBlock()==Event.Result.DENY)return;
        Container c=(Container)e.getClickedBlock().getState();Player p=e.getPlayer();
        if(p.getGameMode()==GameMode.SPECTATOR){e.setCancelled(true);return;}
        if(!allowed(p,c)){e.setCancelled(true);if(e.getHand()==EquipmentSlot.HAND)p.sendMessage("Dieser Block gehört nicht deinem Team.");return;}
        if(p.isSneaking()&&e.getItem()!=null&&e.getItem().getType().isBlock()){
            // Suppress the container action, but leave placement and item consumption to vanilla.
            // In particular, never override another plugin's useItemInHand DENY.
            e.setUseInteractedBlock(Event.Result.DENY);return;
        }
        e.setCancelled(true);if(e.getHand()!=EquipmentSlot.HAND)return;
        if("trash".equals(kind(c.getBlock()))){p.sendMessage("Mülleimer: Trichter anschließen. Eingehende Items werden sofort dauerhaft gelöscht.");return;}
        String link=e.getItem()==null?null:terminals.senderLink(p,e.getItem());
        if(p.isSneaking()&&link!=null){String problem=terminals.senderTargetProblem(UUID.fromString(c.getPersistentDataContainer().get(ownerKey,PersistentDataType.STRING)),c.getWorld(),link);
            if(problem!=null){p.sendMessage(problem);return;}c.getPersistentDataContainer().set(linkKey,PersistentDataType.STRING,link);p.sendMessage(c.update(false,false)?"Empfänger verbunden. Eine Ausgabekiste direkt daneben stellen.":"Verbindung konnte nicht gespeichert werden.");return;}
        p.openInventory(new Menu(c).inventory);
    }
    private final class Menu implements InventoryHolder {
        final Location location;final String id;final Inventory inventory;
        Menu(Container c){location=c.getLocation();id=c.getPersistentDataContainer().get(idKey,PersistentDataType.STRING);inventory=Bukkit.createInventory(this,54,Component.text("Empfänger · Itemauswahl"));render(c);}
        public Inventory getInventory(){return inventory;}
        Container current(Player p){if(!location.getWorld().isChunkLoaded(location.getBlockX()>>4,location.getBlockZ()>>4)||p.getWorld()!=location.getWorld()||p.getLocation().distanceSquared(location.clone().add(.5,.5,.5))>64||!"receiver".equals(kind(location.getBlock())))return null;Container c=(Container)location.getBlock().getState();return Objects.equals(id,c.getPersistentDataContainer().get(idKey,PersistentDataType.STRING))&&allowed(p,c)?c:null;}
        void render(Container c){inventory.clear();List<Material> selected=selection(c);for(int i=0;i<selected.size();i++)inventory.setItem(i,ExchangeMenu.icon(selected.get(i),"Entfernen: "+selected.get(i).name()));
            for(int slot=45;slot<54;slot++)inventory.setItem(slot,ExchangeMenu.icon(Material.GRAY_STAINED_GLASS_PANE," "));
            inventory.setItem(45,ExchangeMenu.icon(Material.PAPER,"Inventar-Item anklicken: auswählen (wird nicht verbraucht)"));inventory.setItem(49,ExchangeMenu.icon(Material.COMPARATOR,"Zielmenge pro Item: "+target(c)+" · Klicken: ändern"));inventory.setItem(53,ExchangeMenu.icon(Material.BARRIER,"Schließen"));}
    }
    private List<Material> selection(Container c){String value=c.getPersistentDataContainer().getOrDefault(selectionKey,PersistentDataType.STRING,"");return Arrays.stream(value.split(",")).map(Material::matchMaterial).filter(Objects::nonNull).distinct().limit(45).toList();}
    private int target(Container c){return c.getPersistentDataContainer().getOrDefault(amountKey,PersistentDataType.INTEGER,64);}
    @EventHandler(priority=EventPriority.HIGHEST) public void click(InventoryClickEvent e){
        if(!(e.getView().getTopInventory().getHolder() instanceof Menu menu))return;boolean cancelled=e.isCancelled();e.setCancelled(true);
        if(cancelled||!(e.getWhoClicked() instanceof Player p)||e instanceof InventoryCreativeEvent||!List.of(ClickType.LEFT,ClickType.RIGHT,ClickType.SHIFT_LEFT).contains(e.getClick()))return;
        Container c=menu.current(p);if(c==null){p.closeInventory();return;}List<Material> selected=new ArrayList<>(selection(c));int slot=e.getRawSlot();
        if(slot>=54&&e.getClickedInventory()==p.getInventory()&&!BotInventory.empty(e.getCurrentItem())){Material type=e.getCurrentItem().getType();if(selected.size()<45&&!selected.contains(type))selected.add(type);}
        else if(slot>=0&&slot<selected.size())selected.remove(slot);
        else if(slot==49){int index=0;for(int i=0;i<TARGETS.length;i++)if(TARGETS[i]==target(c))index=i;c.getPersistentDataContainer().set(amountKey,PersistentDataType.INTEGER,TARGETS[(index+1)%TARGETS.length]);}
        else if(slot==53){Bukkit.getScheduler().runTask(plugin,()->p.closeInventory());return;}else return;
        c.getPersistentDataContainer().set(selectionKey,PersistentDataType.STRING,String.join(",",selected.stream().map(Enum::name).toList()));
        if(!c.update(false,false)){p.sendMessage("Einstellungen konnten nicht gespeichert werden.");return;}
        for(Player viewer:Bukkit.getOnlinePlayers())if(viewer.getOpenInventory().getTopInventory().getHolder() instanceof Menu other&&other.location.equals(c.getLocation()))other.render(c);
    }
    @EventHandler public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof Menu)e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true) public void move(InventoryMoveItemEvent e){
        Location sourceLocation=e.getSource().getLocation(),destinationLocation=e.getDestination().getLocation(); if((sourceLocation!=null&&"receiver".equals(kind(sourceLocation.getBlock())))||(destinationLocation!=null&&"receiver".equals(kind(destinationLocation.getBlock())))){e.setCancelled(true);return;}
        if(trash(e.getSource())){e.setCancelled(true);return;}
        // Let vanilla perform the decrement, then erase the received stack before the next hopper tick.
        if(trash(e.getDestination()))Bukkit.getScheduler().runTask(plugin,()->{if(trash(e.getDestination()))e.getDestination().clear();});
    }
    @EventHandler public void open(InventoryOpenEvent e){Location l=e.getInventory().getLocation();if(l!=null&&isDevice(l.getBlock()))e.setCancelled(true);}
    private void tick(){
        for(Player p:Bukkit.getOnlinePlayers())if(p.getOpenInventory().getTopInventory().getHolder() instanceof Menu menu&&menu.current(p)==null)p.closeInventory();
        for(Location location:List.copyOf(loaded)){
            if(!location.getWorld().isChunkLoaded(location.getBlockX()>>4,location.getBlockZ()>>4)||!isDevice(location.getBlock())){loaded.remove(location);continue;}
            Container c=(Container)location.getBlock().getState();if("trash".equals(kind(c.getBlock()))){c.getInventory().clear();continue;}if(failed.contains(location))continue;
            try{
                String link=c.getPersistentDataContainer().get(linkKey,PersistentDataType.STRING);if(link==null)continue;UUID owner=UUID.fromString(c.getPersistentDataContainer().get(ownerKey,PersistentDataType.STRING));
                if(terminals.senderTargetProblem(owner,c.getWorld(),link)!=null)continue;
                Inventory destination=null;for(BlockFace face:List.of(BlockFace.NORTH,BlockFace.EAST,BlockFace.SOUTH,BlockFace.WEST))if(c.getBlock().getRelative(face).getState() instanceof Chest chest&&!chest.isLocked()){destination=chest.getInventory();break;}
                if(destination==null)continue;
                for(Material type:selection(c)){int count=0;for(ItemStack stack:destination.getStorageContents())if(!BotInventory.empty(stack)&&stack.getType()==type)count+=stack.getAmount();
                    int wanted=Math.min(16,Math.max(0,target(c)-count));if(wanted>0&&terminals.export(owner,c.getWorld(),link,type,wanted,destination)>0)break;}
            }catch(Exception ex){failed.add(location);plugin.getLogger().log(java.util.logging.Level.SEVERE,"Empfänger sicher angehalten: "+location,ex);}
        }
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true) public void breakBlock(BlockBreakEvent e){
        if(!isDevice(e.getBlock()))return;e.setCancelled(true);Container c=(Container)e.getBlock().getState();
        if(!e.getPlayer().hasPermission("casino.admin")&&!e.getPlayer().getUniqueId().toString().equals(c.getPersistentDataContainer().get(ownerKey,PersistentDataType.STRING))){e.getPlayer().sendMessage("Nur der Besitzer darf diesen Block abbauen.");return;}
        boolean trash="trash".equals(kind(e.getBlock()));ItemStack[] remaining=c.getInventory().getContents();loaded.remove(c.getLocation());failed.remove(c.getLocation());c.getInventory().clear();e.getBlock().setType(Material.AIR);
        if(!trash)for(ItemStack stack:remaining)if(!BotInventory.empty(stack))c.getWorld().dropItemNaturally(c.getLocation(),stack);
        c.getWorld().dropItemNaturally(c.getLocation(),item(trash));
    }
    @EventHandler(ignoreCancelled=true) public void burn(BlockBurnEvent e){if(isDevice(e.getBlock()))e.setCancelled(true);}
    @EventHandler(ignoreCancelled=true) public void dispense(BlockDispenseEvent e){if(isDevice(e.getBlock()))e.setCancelled(true);}
    @EventHandler(ignoreCancelled=true) public void explode(EntityExplodeEvent e){e.blockList().removeIf(this::isDevice);}
    @EventHandler(ignoreCancelled=true) public void explode(BlockExplodeEvent e){e.blockList().removeIf(this::isDevice);}
    @EventHandler(ignoreCancelled=true) public void piston(BlockPistonExtendEvent e){if(e.getBlocks().stream().anyMatch(this::isDevice))e.setCancelled(true);}
    @EventHandler(ignoreCancelled=true) public void piston(BlockPistonRetractEvent e){if(e.getBlocks().stream().anyMatch(this::isDevice))e.setCancelled(true);}
}
