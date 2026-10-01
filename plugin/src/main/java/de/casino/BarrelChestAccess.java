package de.casino;

import java.util.*;
import java.util.function.Consumer;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;

/** Opens the actual chest inventory when a barrel is its only lid obstruction. */
final class BarrelChestAccess implements Listener {
    private final Consumer<Runnable> nextTick;
    private record Part(Block block,Inventory inventory) {}
    private record Session(Player player,InventoryView view,Block clicked,List<Part> parts) {}
    private final Map<UUID,Session> sessions=new HashMap<>();
    BarrelChestAccess(Consumer<Runnable> nextTick) { this.nextTick=nextTick; }

    @EventHandler(priority=EventPriority.MONITOR)
    public void interact(PlayerInteractEvent event) {
        if(event.getAction()!=Action.RIGHT_CLICK_BLOCK||event.getHand()!=EquipmentSlot.HAND
                ||event.useInteractedBlock()==Event.Result.DENY)return;
        Player player=event.getPlayer();Block clicked=event.getClickedBlock();
        if(player.getGameMode()==GameMode.SPECTATOR||placingWhileSneaking(player)||inventory(clicked)==null)return;
        InventoryView previous=player.getOpenInventory();
        // Do not override another listener's interaction decision or its replacement menu.
        // Vanilla consumes the blocked-chest interaction itself; no cancellation is needed.
        nextTick.accept(()->{
            if(event.useInteractedBlock()==Event.Result.DENY||!player.isOnline()
                    ||player.getGameMode()==GameMode.SPECTATOR||placingWhileSneaking(player)
                    ||player.getOpenInventory()!=previous||player.getWorld()!=clicked.getWorld()
                    ||player.getLocation().distanceSquared(clicked.getLocation().add(.5,.5,.5))>64)return;
            Inventory target=inventory(clicked);
            // The native single/double chest provider keeps lock checks, loot, lid animation,
            // viewers, redstone and cancellable InventoryOpenEvents. Never copy its contents.
            if(target==null||!(clicked.getState() instanceof Chest chest))return;
            List<Chest> halves=halves(chest,target);if(halves.isEmpty())return;
            List<Part> parts=halves.stream().map(half->new Part(half.getBlock(),half.getBlockInventory())).toList();
            InventoryView view=player.openInventory(target);
            if(view!=null)sessions.put(player.getUniqueId(),new Session(player,view,clicked,parts));
        });
    }
    private boolean valid(Session session){
        Player player=session.player();Block clicked=session.clicked();
        if(!player.isOnline()||player.isDead()||player.getGameMode()==GameMode.SPECTATOR||player.getWorld()!=clicked.getWorld()
                ||player.getLocation().distanceSquared(clicked.getLocation().add(.5,.5,.5))>64)return false;
        for(Part part:session.parts())if(!part.block().getWorld().isChunkLoaded(part.block().getX()>>4,part.block().getZ()>>4)
                ||!(part.block().getState() instanceof Chest current)||!current.getBlockInventory().equals(part.inventory()))return false;
        return true;
    }
    // Bukkit's explicit openInventory disables the native distance check. Restore that check
    // and reject clicks immediately if either physical half was removed or replaced.
    void tick(){
        for(Session session:List.copyOf(sessions.values())){
            if(session.player().getOpenInventory()!=session.view())sessions.remove(session.player().getUniqueId());
            else if(!valid(session)){sessions.remove(session.player().getUniqueId());session.player().closeInventory();}
        }
    }
    private boolean rejectInvalid(HumanEntity viewer,InventoryView view){
        Session session=sessions.get(viewer.getUniqueId());
        if(session==null||session.view()!=view||valid(session))return false;
        nextTick.accept(()->{if(viewer.getOpenInventory()==view)viewer.closeInventory();});return true;
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void click(InventoryClickEvent event){
        if(rejectInvalid(event.getWhoClicked(),event.getView()))event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void drag(InventoryDragEvent event){
        if(rejectInvalid(event.getWhoClicked(),event.getView()))event.setCancelled(true);
    }
    @EventHandler public void close(InventoryCloseEvent event){
        Session session=sessions.get(event.getPlayer().getUniqueId());
        if(session!=null&&session.view()==event.getView())sessions.remove(event.getPlayer().getUniqueId());
    }
    void disable(){
        for(Session session:List.copyOf(sessions.values()))if(session.player().getOpenInventory()==session.view())session.player().closeInventory();
        sessions.clear();
    }
    private static boolean placingWhileSneaking(Player player) {
        return player.isSneaking()&&(!BotInventory.empty(player.getInventory().getItemInMainHand())
                ||!BotInventory.empty(player.getInventory().getItemInOffHand()));
    }
    static Inventory inventory(Block block) {
        if(block==null||!block.getWorld().isChunkLoaded(block.getX()>>4,block.getZ()>>4)
                ||block.getType()!=Material.CHEST&&block.getType()!=Material.TRAPPED_CHEST
                ||!(block.getState() instanceof Chest chest))return null;
        Inventory inventory=chest.getInventory();List<Chest> halves=halves(chest,inventory);
        if(halves.isEmpty())return null;
        boolean barrel=false;
        for(Chest half:halves){
            Block part=half.getBlock();
            if(!part.getWorld().isChunkLoaded(part.getX()>>4,part.getZ()>>4))return null;
            if(part.getRelative(BlockFace.UP).getType()==Material.BARREL)barrel=true;
            else if(half.isBlocked())return null;
        }
        return barrel?inventory:null;
    }
    private static List<Chest> halves(Chest chest,Inventory inventory){
        if(inventory instanceof DoubleChestInventory pair){
            if(!(pair.getLeftSide().getHolder() instanceof Chest left)||!(pair.getRightSide().getHolder() instanceof Chest right))return List.of();
            return List.of(left,right);
        }else{
            if(chest.getBlockData() instanceof org.bukkit.block.data.type.Chest data
                    &&data.getType()!=org.bukkit.block.data.type.Chest.Type.SINGLE)return List.of();
            return List.of(chest);
        }
    }
}
