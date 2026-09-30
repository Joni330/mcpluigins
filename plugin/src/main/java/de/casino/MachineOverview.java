package de.casino;

import java.util.*;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;

/** Read-only dashboard backed by the persisted mining and lumber bots. */
final class MachineOverview implements Listener {
    record Entry(String id,UUID owner,UUID world,int x,int y,int z,String name,Material icon,String status,String detail,boolean needsLoadedChunk){}
    private final JavaPlugin plugin;private final StorageTeams teams;
    private final List<Supplier<List<Entry>>> sources=new ArrayList<>();
    private java.util.function.Consumer<Player> back=p->p.closeInventory();
    MachineOverview(JavaPlugin plugin,StorageTeams teams){
        this.plugin=plugin;this.teams=teams;
    }
    void enable(){Bukkit.getPluginManager().registerEvents(this,plugin);Bukkit.getScheduler().runTaskTimer(plugin,()->{for(Player p:Bukkit.getOnlinePlayers())if(p.getOpenInventory().getTopInventory().getHolder() instanceof Page page)render(p,page);},100,100);}
    void back(java.util.function.Consumer<Player> action){back=action;}
    void source(Supplier<List<Entry>> source){sources.add(source);}
    private List<Entry> entries(Player player){
        List<Entry> entries=new ArrayList<>();for(var source:sources)entries.addAll(source.get());
        return entries.stream().filter(e->player.hasPermission("casino.admin")||e.owner()!=null&&teams.shares(e.owner(),player.getUniqueId()))
                .sorted(Comparator.comparing(Entry::name).thenComparing(Entry::id)).toList();
    }
    private static final class Page implements InventoryHolder{
        final UUID viewer;int index;final Inventory inventory;
        Page(Player player){viewer=player.getUniqueId();inventory=Bukkit.createInventory(this,54,Component.text("Maschinenübersicht"));}
        public Inventory getInventory(){return inventory;}
    }
    void open(Player player){Page page=new Page(player);render(player,page);player.openInventory(page.inventory);}
    private void icon(Page page,int slot,Material material,String title,String... lore){ItemStack item=ExchangeMenu.icon(material,title);var meta=item.getItemMeta();meta.lore(Arrays.stream(lore).map(Component::text).toList());item.setItemMeta(meta);page.inventory.setItem(slot,item);}
    private void render(Player player,Page page){
        List<Entry> entries=entries(player);int pages=Math.max(1,(entries.size()+44)/45);page.index=Math.min(page.index,pages-1);page.inventory.clear();
        for(int i=0;i<45&&page.index*45+i<entries.size();i++){
            Entry e=entries.get(page.index*45+i);World world=Bukkit.getWorld(e.world());boolean loaded=world!=null&&world.isChunkLoaded(e.x()>>4,e.z()>>4);
            String status=world==null?"Welt nicht geladen":e.needsLoadedChunk()&&!loaded?"Chunk nicht geladen · pausiert":e.status();
            icon(page,i,e.icon(),e.name(),"Status: "+status,"Standort: "+(world==null?e.world():world.getName())+" · "+e.x()+", "+e.y()+", "+e.z(),e.detail(),"Besitzer: "+(e.owner()==null?"Altgerät ohne Zuordnung":Optional.ofNullable(Bukkit.getOfflinePlayer(e.owner()).getName()).orElse(e.owner().toString())));
        }
        for(int i=45;i<54;i++)icon(page,i,Material.GRAY_STAINED_GLASS_PANE," ");
        if(entries.isEmpty())icon(page,22,Material.PAPER,"Keine Bots vorhanden","Hier erscheinen eure Miningbots und Holzfällerbots.");
        icon(page,45,Material.ARROW,"Zurück zum Menü");icon(page,48,Material.ARROW,"Vorherige Seite");icon(page,49,Material.COMPASS,"Aktualisieren · "+(page.index+1)+"/"+pages,entries.size()+" Bots · auch Teambots", "Automatische Aktualisierung alle 5 Sekunden");icon(page,50,Material.ARROW,"Nächste Seite");icon(page,53,Material.BARRIER,"Schließen");
    }
    @EventHandler public void click(InventoryClickEvent event){
        if(!(event.getView().getTopInventory().getHolder() instanceof Page page))return;boolean canceled=event.isCancelled();event.setCancelled(true);
        if(canceled||!(event.getWhoClicked() instanceof Player p)||!p.getUniqueId().equals(page.viewer)||event.getClick()!=ClickType.LEFT)return;
        int slot=event.getRawSlot();Bukkit.getScheduler().runTask(plugin,()->{if(!p.isOnline()||p.getOpenInventory().getTopInventory()!=page.inventory)return;
            if(slot==45)back.accept(p);else if(slot==53)p.closeInventory();else{if(slot==48)page.index=Math.max(0,page.index-1);if(slot==50)page.index++;render(p,page);}});
    }
    @EventHandler public void drag(InventoryDragEvent event){if(event.getView().getTopInventory().getHolder() instanceof Page)event.setCancelled(true);}
    void disable(){for(Player p:Bukkit.getOnlinePlayers())if(p.getOpenInventory().getTopInventory().getHolder() instanceof Page)p.closeInventory();}
}
