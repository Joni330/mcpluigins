package de.casino;

import java.lang.reflect.Proxy;
import java.util.*;
import java.util.function.BiFunction;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BarrelChestAccessTest {
    @SuppressWarnings("unchecked")
    private static <T> T mock(Class<T> type,BiFunction<String,Object[],Object> call){
        return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(self,method,args)->{
            if(method.getName().equals("equals"))return self==args[0];
            if(method.getName().equals("hashCode"))return System.identityHashCode(self);
            if(method.getName().equals("toString"))return type.getSimpleName();
            Object value=call.apply(method.getName(),args);
            if(value!=null)return value;
            if(method.getReturnType()==boolean.class)return false;
            if(method.getReturnType()==int.class)return 0;
            return null;
        });
    }
    private static final class Fixture {
        Material cover=Material.BARREL;boolean blocked=true,loaded=true,sneaking,online=true,exists=true,closed;
        final UUID id=UUID.randomUUID();
        double distance=2;
        GameMode mode=GameMode.SURVIVAL;
        final World world=mock(World.class,(name,args)->name.equals("isChunkLoaded")?loaded:null);
        final InventoryView original=mock(InventoryView.class,(name,args)->null);
        InventoryView current=original;Inventory opened;
        final InventoryView openedView=mock(InventoryView.class,(name,args)->switch(name){case "getPlayer"->this.player;case "getTopInventory"->opened;default->null;});
        ItemStack main,off;
        final PlayerInventory hands=mock(PlayerInventory.class,(name,args)->switch(name){case "getItemInMainHand"->main;case "getItemInOffHand"->off;default->null;});
        final Player player=mock(Player.class,(name,args)->switch(name){
            case "getUniqueId"->id;case "getWorld"->world;case "getLocation"->new Location(world,0,64,distance);case "getGameMode"->mode;
            case "isSneaking"->sneaking;case "isOnline"->online;case "getInventory"->hands;case "getOpenInventory"->current;
            case "openInventory"->{opened=(Inventory)args[0];current=openedView;yield openedView;}
            case "closeInventory"->{closed=true;current=original;yield null;}default->null;
        });
        final Block above=mock(Block.class,(name,args)->name.equals("getType")?cover:null);
        final Block block=mock(Block.class,(name,args)->switch(name){
            case "getType"->Material.CHEST;case "getWorld"->world;case "getState"->exists?this.chest:null;case "getRelative"->above;
            case "getLocation"->new Location(world,0,64,0);default->null;
        });
        final Chest chest=mock(Chest.class,(name,args)->switch(name){
            case "getBlock"->block;case "getInventory"->this.inventory;case "getBlockInventory"->this.single;case "isBlocked"->blocked;default->null;
        });
        final Inventory single=mock(Inventory.class,(name,args)->name.equals("getHolder")?chest:null);
        Inventory inventory=single;
        PlayerInteractEvent click(EquipmentSlot hand){return new PlayerInteractEvent(player,Action.RIGHT_CLICK_BLOCK,null,block,BlockFace.NORTH,hand);}
    }
    @Test void opensSingleChestUsingItsRealInventoryAndLeavesInteractionUntouched(){
        Fixture f=new Fixture();List<Runnable> queue=new ArrayList<>();var event=f.click(EquipmentSlot.HAND);
        Event.Result original=event.useInteractedBlock();
        new BarrelChestAccess(queue::add).interact(event);
        assertEquals(original,event.useInteractedBlock());assertNull(f.opened);assertEquals(1,queue.size());
        queue.removeFirst().run();assertSame(f.single,f.opened);
    }
    @Test void doubleChestChecksBothCoversAndReturnsFullInventory(){
        Fixture left=new Fixture(),right=new Fixture();
        Inventory pair=mock(DoubleChestInventory.class,(name,args)->switch(name){case "getLeftSide"->left.single;case "getRightSide"->right.single;default->null;});
        left.inventory=pair;right.inventory=pair;
        assertSame(pair,BarrelChestAccess.inventory(left.block));
        left.cover=Material.AIR;left.blocked=false;assertSame(pair,BarrelChestAccess.inventory(left.block));
        right.cover=Material.AIR;right.blocked=false;assertNull(BarrelChestAccess.inventory(left.block));
        left.cover=Material.BARREL;left.blocked=true;right.cover=Material.STONE;right.blocked=true;
        assertNull(BarrelChestAccess.inventory(left.block));
        right.cover=Material.AIR; // isBlocked may also indicate a sitting cat.
        assertNull(BarrelChestAccess.inventory(left.block));
    }
    @Test void obstructionPermissionAndMenuAreRecheckedAfterOtherListeners(){
        Fixture f=new Fixture();List<Runnable> queue=new ArrayList<>();var listener=new BarrelChestAccess(queue::add);
        var denied=f.click(EquipmentSlot.HAND);denied.setUseInteractedBlock(Event.Result.DENY);listener.interact(denied);assertTrue(queue.isEmpty());
        var laterDenied=f.click(EquipmentSlot.HAND);listener.interact(laterDenied);laterDenied.setUseInteractedBlock(Event.Result.DENY);queue.removeFirst().run();assertNull(f.opened);
        listener.interact(f.click(EquipmentSlot.HAND));f.current=mock(InventoryView.class,(name,args)->null);queue.removeFirst().run();assertNull(f.opened);
        f.current=f.original;listener.interact(f.click(EquipmentSlot.HAND));f.cover=Material.STONE;queue.removeFirst().run();assertNull(f.opened);
    }
    @Test void offhandSpectatorAndSneakingWithAnItemKeepVanillaBehavior(){
        Fixture f=new Fixture();List<Runnable> queue=new ArrayList<>();var listener=new BarrelChestAccess(queue::add);
        listener.interact(f.click(EquipmentSlot.OFF_HAND));assertTrue(queue.isEmpty());
        f.mode=GameMode.SPECTATOR;listener.interact(f.click(EquipmentSlot.HAND));assertTrue(queue.isEmpty());
        f.mode=GameMode.SURVIVAL;f.sneaking=true;f.off=new ItemStack(){@Override public boolean isEmpty(){return false;} @Override public int getAmount(){return 1;}};
        listener.interact(f.click(EquipmentSlot.HAND));assertTrue(queue.isEmpty());
        f.off=null;listener.interact(f.click(EquipmentSlot.HAND));assertEquals(1,queue.size());
    }
    @Test void unloadedChestDoesNotGetOpened(){
        Fixture f=new Fixture();assertSame(f.single,BarrelChestAccess.inventory(f.block));
        f.loaded=false;assertNull(BarrelChestAccess.inventory(f.block));
    }
    @Test void closesWhenPlayerWalksAwayButLeavesAnUnrelatedMenuAlone(){
        Fixture f=new Fixture();List<Runnable> queue=new ArrayList<>();var listener=new BarrelChestAccess(queue::add);
        listener.interact(f.click(EquipmentSlot.HAND));queue.removeFirst().run();listener.tick();assertFalse(f.closed);
        f.distance=12;listener.tick();assertTrue(f.closed);
        f.closed=false;f.distance=2;listener.interact(f.click(EquipmentSlot.HAND));queue.removeFirst().run();
        f.current=f.original;f.distance=12;listener.tick();assertFalse(f.closed);
    }
    @Test void removedChestCannotYieldItemsThroughAnOldWindow(){
        Fixture f=new Fixture();List<Runnable> queue=new ArrayList<>();var listener=new BarrelChestAccess(queue::add);
        listener.interact(f.click(EquipmentSlot.HAND));queue.removeFirst().run();f.exists=false;
        var click=new InventoryClickEvent(f.current,InventoryType.SlotType.CONTAINER,0,ClickType.LEFT,InventoryAction.PICKUP_ALL);
        listener.click(click);assertTrue(click.isCancelled());
        var second=new InventoryClickEvent(f.current,InventoryType.SlotType.CONTAINER,1,ClickType.LEFT,InventoryAction.PICKUP_ALL);
        listener.click(second);assertTrue(second.isCancelled());queue.removeFirst().run();assertTrue(f.closed);
    }
}
