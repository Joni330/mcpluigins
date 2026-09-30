package de.casino;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LumberInventoryTest {
    // Exercise the real packing logic without a running Minecraft item/registry implementation.
    private static final class Stack extends ItemStack {
        private final Material type; private final String tag; private int amount;
        Stack(Material type,int amount){this(type,amount,"");}
        Stack(Material type,int amount,String tag){super();this.type=type;this.amount=amount;this.tag=tag;}
        @Override public Material getType(){return type;}
        @Override public int getAmount(){return amount;}
        @Override public void setAmount(int amount){this.amount=amount;}
        @Override public int getMaxStackSize(){return 64;}
        @Override public boolean isEmpty(){return amount<=0||type==Material.AIR;}
        @Override public boolean isSimilar(ItemStack other){return other instanceof Stack s&&s.type==type&&s.tag.equals(tag);}
        @Override public ItemStack clone(){return new Stack(type,amount,tag);}
    }
    private ItemStack[] empty(){return new ItemStack[LumberInventory.END];}
    private void fill(ItemStack[] items,int from,int to){for(int i=from;i<to;i++)items[i]=new Stack(Material.OAK_LOG,64);}
    private int count(ItemStack[] items,Material material){return Arrays.stream(items).filter(Objects::nonNull).filter(i->i.getType()==material).mapToInt(ItemStack::getAmount).sum();}

    @Test void smallHarvestFitsAndSaplingsAreKeptForPlanting(){
        ItemStack[] current=empty();var result=LumberInventory.plan(current,List.of(new Stack(Material.OAK_LOG,4),new Stack(Material.OAK_SAPLING,2),new Stack(Material.APPLE,1)));
        assertFalse(result.full());assertTrue(result.changed());assertEquals(3,result.moves().size());
        assertEquals(2,result.items()[LumberInventory.SAPLINGS].getAmount());
        assertEquals(Material.OAK_LOG,result.items()[LumberInventory.CARGO].getType());
        assertEquals(7,Arrays.stream(result.items()).filter(Objects::nonNull).mapToInt(ItemStack::getAmount).sum());
        assertTrue(Arrays.stream(current).allMatch(Objects::isNull));
    }
    @Test void fullCargoStillAcceptsSaplingsIntoTheirSupplySlots(){
        ItemStack[] current=empty();fill(current,LumberInventory.CARGO,LumberInventory.END);
        var result=LumberInventory.plan(current,List.of(new Stack(Material.BIRCH_SAPLING,6)));
        assertFalse(result.full());assertEquals(6,count(result.items(),Material.BIRCH_SAPLING));
    }
    @Test void excessSaplingsCanReturnFromCargoToPlantingSupply(){
        ItemStack[] current=empty();
        for(int i=LumberInventory.SAPLINGS;i<LumberInventory.SAPLINGS_END;i++)current[i]=new Stack(Material.OAK_SAPLING,64);
        var first=LumberInventory.plan(current,List.of(new Stack(Material.OAK_SAPLING,12)));
        assertEquals(12,first.items()[LumberInventory.CARGO].getAmount());
        first.items()[LumberInventory.SAPLINGS].setAmount(50);
        var refilled=LumberInventory.plan(first.items(),List.of());
        assertTrue(refilled.changed());assertTrue(refilled.moves().isEmpty());
        assertEquals(62,refilled.items()[LumberInventory.SAPLINGS].getAmount());assertNull(refilled.items()[LumberInventory.CARGO]);
        assertEquals(12,first.items()[LumberInventory.CARGO].getAmount()); // Planning did not mutate its source.
    }
    @Test void partialPickupConsumesOnlyTheAmountThatActuallyFits(){
        ItemStack[] current=empty();fill(current,LumberInventory.CARGO,LumberInventory.END);
        current[LumberInventory.CARGO]=new Stack(Material.APPLE,62);
        Stack drop=new Stack(Material.APPLE,7);var pickup=LumberInventory.plan(current,List.of(drop));
        assertTrue(pickup.full());assertEquals(List.of(new LumberInventory.Move(0,2)),pickup.moves());
        AtomicInteger remaining=new AtomicInteger(7),saves=new AtomicInteger();
        assertTrue(pickup.commit(next->{saves.incrementAndGet();assertEquals(64,next[LumberInventory.CARGO].getAmount());return true;},(index,amount)->{assertEquals(1,saves.get());remaining.addAndGet(-amount);}));
        assertEquals(5,remaining.get());assertEquals(7,drop.getAmount());assertEquals(62,current[LumberInventory.CARGO].getAmount());
    }
    @Test void failedInventorySaveNeverConsumesWorldDrops(){
        ItemStack[] current=empty();var pickup=LumberInventory.plan(current,List.of(new Stack(Material.STICK,8)));
        assertFalse(pickup.commit(next->false,(index,amount)->fail("World item consumed after failed save")));
        assertTrue(Arrays.stream(current).allMatch(Objects::isNull));
    }
    @Test void pickupBatchSavesOnceBeforeConsumingAnyDropAndPreservesMetadata(){
        ItemStack[] current=empty();current[LumberInventory.CARGO]=new Stack(Material.OAK_LOG,20,"custom");
        var pickup=LumberInventory.plan(current,List.of(new Stack(Material.OAK_LOG,3),new Stack(Material.OAK_SAPLING,4)));
        assertEquals(20,pickup.items()[LumberInventory.CARGO].getAmount());assertEquals(3,pickup.items()[LumberInventory.CARGO+1].getAmount());
        List<String> calls=new ArrayList<>();assertTrue(pickup.commit(next->{calls.add("save");return true;},(index,amount)->calls.add(index+":"+amount)));
        assertEquals(List.of("save","0:3","1:4"),calls);
    }
    @Test void fullInventoryLeavesTheDropAloneAndDoesNotSaveAnUnchangedInventory(){
        ItemStack[] current=empty();fill(current,LumberInventory.CARGO,LumberInventory.END);
        var pickup=LumberInventory.plan(current,List.of(new Stack(Material.APPLE,3)));
        assertTrue(pickup.full());assertFalse(pickup.changed());
        assertTrue(pickup.commit(next->{fail("Nothing changed");return false;},(index,amount)->fail("Nothing fitted")));
    }
}
