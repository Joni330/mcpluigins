package de.casino;

import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.*;
import org.bukkit.entity.minecart.PoweredMinecart;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.*;
import org.bukkit.event.world.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;
import java.util.Comparator;
import de.casino.MiningBotVeins.Pos;
import de.casino.AdvancedBotData.*;

/** Quarry and free-moving seeker; all inventory changes run synchronously and persist before rewards. */
final class AdvancedMiningBots implements Listener {
    private static final int FUEL=3, CARGO=30, SIZE=246;
    private static final List<Material> ORES=List.of(Material.COAL_ORE,Material.COPPER_ORE,Material.IRON_ORE,Material.GOLD_ORE,
            Material.REDSTONE_ORE,Material.LAPIS_ORE,Material.DIAMOND_ORE,Material.EMERALD_ORE,Material.NETHER_QUARTZ_ORE,Material.NETHER_GOLD_ORE,Material.ANCIENT_DEBRIS);
    private static final List<String> ORE_NAMES=List.of("Kohle","Kupfer","Eisen","Gold","Redstone","Lapis","Diamanten","Smaragde","Netherquarz","Nethergold","Antiker Schrott");
    private final JavaPlugin plugin;
    private final StorageTeams teams;
    private final MiningBots tunnels;
    private final BotAlerts alerts;
    private final AdvancedBotData store;
    private final NamespacedKey itemKey,partKey,deployKey,quarryRecipe,seekerRecipe,lumberRecipe;
    private final Map<UUID,Bot> bots=new LinkedHashMap<>();
    private boolean stopping;
    private static final class Bot {
        final State s; ItemStack[] items; PoweredMinecart entity; final List<Entity> terminal=new ArrayList<>();
        final Map<Pos,Long> unreachable=new HashMap<>(); boolean failed; int delay,preparedLayer=-1; String status="Station bereit";
        LumberScan treeScan;
        Bot(State state,ItemStack[] items) { s=state; this.items=items; }
    }
    private static final class Page implements InventoryHolder {
        final Bot bot; final UUID player; final String mode; final int page; final Inventory inventory;
        Page(Bot bot,Player player,String mode,int page) {
            this.bot=bot; this.player=player.getUniqueId(); this.mode=mode; this.page=page;
            inventory=Bukkit.createInventory(this,mode.equals("Fuel")?(bot.s.kind==Kind.LUMBER?54:36):mode.equals("Ores")?27:54,
                    Component.text(name(bot)+" · "+(mode.equals("Cargo")?"Lager "+(page+1)+"/4":mode.equals("Fuel")?(bot.s.kind==Kind.LUMBER?"Setzlinge & Knochenmehl":"Brennstoff"):mode.equals("Ores")?"Erzauswahl":mode.equals("Filter")?"Itemfilter":"Terminal")));
        }
        public Inventory getInventory(){return inventory;}
    }
    AdvancedMiningBots(JavaPlugin plugin,StorageTeams teams,MiningBots tunnels,BotAlerts alerts) throws Exception {
        this.plugin=plugin; this.teams=teams; this.tunnels=tunnels; this.alerts=alerts; store=new AdvancedBotData(plugin.getDataFolder().toPath());
        itemKey=new NamespacedKey(plugin,"advanced_bot_item"); partKey=new NamespacedKey(plugin,"advanced_bot_part"); deployKey=new NamespacedKey(plugin,"advanced_bot_deploy");
        quarryRecipe=new NamespacedKey(plugin,"quarry_bot_recipe"); seekerRecipe=new NamespacedKey(plugin,"seeker_bot_recipe");lumberRecipe=new NamespacedKey(plugin,"lumber_bot_recipe");
        for(State s:store.load()) {
            ItemStack[] items=ItemStack.deserializeItemsFromBytes(s.items); if(items.length==138)items=Arrays.copyOf(items,SIZE); if(items.length!=SIZE) throw new IllegalStateException("Ungültiges Bot-Inventar: "+s.id);
            bots.put(s.id,new Bot(s,items));
        }
    }
    void enable() {
        Bukkit.getPluginManager().registerEvents(this,plugin);
        ShapedRecipe quarry=new ShapedRecipe(quarryRecipe,item(Kind.QUARRY)); quarry.shape("GBG","DTD","EDE");
        quarry.setIngredient('G',Material.GOLD_BLOCK); quarry.setIngredient('B',new RecipeChoice.ExactChoice(tunnels.item()));
        quarry.setIngredient('D',Material.DIAMOND_BLOCK); quarry.setIngredient('T',Material.POINTED_DRIPSTONE); quarry.setIngredient('E',Material.EMERALD_BLOCK);
        ShapedRecipe seeker=new ShapedRecipe(seekerRecipe,item(Kind.SEEKER)); seeker.shape("ETE","DND","EBE");
        seeker.setIngredient('E',Material.ECHO_SHARD); seeker.setIngredient('T',Material.SOUL_TORCH); seeker.setIngredient('D',Material.DIAMOND_BLOCK);
        seeker.setIngredient('N',Material.NETHER_STAR); seeker.setIngredient('B',new RecipeChoice.ExactChoice(item(Kind.QUARRY)));
        ShapedRecipe lumber=new ShapedRecipe(lumberRecipe,item(Kind.LUMBER));lumber.shape("ACA","SFS"," M ");lumber.setIngredient('A',Material.IRON_AXE);lumber.setIngredient('C',Material.CHEST);lumber.setIngredient('S',Material.OAK_SAPLING);lumber.setIngredient('F',Material.FURNACE);lumber.setIngredient('M',Material.MINECART);Bukkit.removeRecipe(lumberRecipe);Bukkit.addRecipe(lumber);
        Bukkit.removeRecipe(quarryRecipe); Bukkit.removeRecipe(seekerRecipe); Bukkit.addRecipe(quarry); Bukkit.addRecipe(seeker);
        Bukkit.getOnlinePlayers().forEach(p->p.discoverRecipes(List.of(quarryRecipe,seekerRecipe,lumberRecipe)));
        tunnels.extraProtection(this::protectedBlock);
        Bukkit.getScheduler().runTaskTimer(plugin,this::tick,10,10);
    }
    static String name(Bot bot){return bot.s.kind==Kind.QUARRY?"Steinbruchbot":bot.s.kind==Kind.LUMBER?"Holzfällerbot":"Erzsucher";}
    ItemStack item(Kind kind) {
        ItemStack item=ExchangeMenu.icon(Material.FURNACE_MINECART,kind==Kind.QUARRY?"Steinbruchbot":kind==Kind.LUMBER?"Holzfällerbot":"Erzsucher");
        var meta=item.getItemMeta(); meta.getPersistentDataContainer().set(itemKey,PersistentDataType.STRING,kind.name());
        meta.lore(List.of(Component.text("Zuerst Station mit Rechtsklick auf Boden setzen"),Component.text("Im Terminal: Bot platzieren · dann Einsatzort wählen"),Component.text("216 Lagerplätze · automatische Heimkehr"))); item.setItemMeta(meta); return item;
    }
    void give(Player player,Kind kind){player.getInventory().addItem(item(kind)).values().forEach(i->player.getWorld().dropItemNaturally(player.getLocation(),i));}
    private World world(Bot bot){return Bukkit.getWorld(bot.s.world);}
    private Location location(Bot bot,Pos pos){return new Location(world(bot),pos.x()+.5,pos.y()+.1,pos.z()+.5);}
    private Block block(Bot bot,Pos pos){return world(bot).getBlockAt(pos.x(),pos.y(),pos.z());}
    private boolean loaded(Bot bot,Pos p){return world(bot)!=null && world(bot).isChunkLoaded(p.x()>>4,p.z()>>4);}
    private boolean allowed(Player p,Bot b){return p.hasPermission("casino.admin") || teams.shares(b.s.owner,p.getUniqueId());}
    private boolean usable(Player p,Bot b){return !stopping&&!b.failed&&allowed(p,b)&&p.getGameMode()!=GameMode.SPECTATOR&&p.getWorld()==world(b)
            &&(p.getLocation().distanceSquared(location(b,b.s.station))<=64||p.getLocation().distanceSquared(location(b,b.s.position))<=64);}
    private boolean save(Bot b,ItemStack[] items) {
        try { b.s.items=ItemStack.serializeItemsAsBytes(items); store.save(b.s); b.items=items; return true; }
        catch(Exception ex){b.failed=true; b.status="Fehler · Speichern fehlgeschlagen"; alerts.report(b.s.id,b.s.owner,name(b),b.status); plugin.getLogger().log(java.util.logging.Level.SEVERE,b.status,ex); return false;}
    }
    private Bot at(Block block){for(Bot b:bots.values()) if(b.s.world.equals(block.getWorld().getUID())&&b.s.station.equals(new Pos(block.getX(),block.getY(),block.getZ()))) return b; return null;}
    boolean protectedBlock(Block block){return at(block)!=null||at(block.getRelative(BlockFace.UP))!=null;}
    private Bot from(Entity entity){String id=entity.getPersistentDataContainer().get(partKey,PersistentDataType.STRING); if(id==null)return null; try{return bots.get(UUID.fromString(id));}catch(Exception e){return null;}}
    @EventHandler(priority=EventPriority.HIGHEST) public void place(PlayerInteractEvent event) {
        if(event.getAction()!=Action.RIGHT_CLICK_BLOCK || event.useInteractedBlock()==Event.Result.DENY || event.useItemInHand()==Event.Result.DENY)return;
        Player p=event.getPlayer(); ItemStack held=event.getItem(); Bot existing=at(event.getClickedBlock());
        if(existing!=null){event.setCancelled(true);if(event.getHand()==EquipmentSlot.HAND)open(p,existing,"Main",0);return;}
        if(held==null||!held.hasItemMeta())return;
        String deploy=held.getItemMeta().getPersistentDataContainer().get(deployKey,PersistentDataType.STRING);
        String kind=held.getItemMeta().getPersistentDataContainer().get(itemKey,PersistentDataType.STRING);
        if(deploy==null&&kind==null)return;
        event.setCancelled(true); if(event.getHand()!=EquipmentSlot.HAND||p.getGameMode()==GameMode.SPECTATOR)return;
        Block target=event.getClickedBlock().getRelative(BlockFace.UP);
        if(event.getBlockFace()!=BlockFace.UP||!target.getType().isAir()||!target.getRelative(BlockFace.UP).getType().isAir()||!event.getClickedBlock().getType().isOccluding()) {p.sendMessage("Vollen Bodenblock mit zwei freien Blöcken darüber wählen.");return;}
        if(deploy!=null){
            Bot b;try{b=bots.get(UUID.fromString(deploy));}catch(Exception ex){return;}
            if(b==null||!allowed(p,b)){p.sendMessage("Keine gültige eigene Station verknüpft.");return;}
            if(b.failed||b.s.phase==Phase.WORKING||b.s.phase==Phase.UNLOADING||b.s.pendingPosition!=null){p.sendMessage("Bot zuerst stoppen und vollständig entladen lassen.");return;}
            if(!b.s.world.equals(p.getWorld().getUID())){p.sendMessage("Bot und Station müssen in derselben Welt sein.");return;}
            int cx=target.getX()>>4,cz=target.getZ()>>4;
            if(b.s.kind!=Kind.SEEKER&&bots.values().stream().anyMatch(other->other!=b&&other.s.configured&&other.s.kind!=Kind.SEEKER&&other.s.world.equals(b.s.world)&&other.s.chunkX==cx&&other.s.chunkZ==cz)){
                p.sendMessage("Dieser Chunk ist bereits einem Steinbruch- oder Holzfällerbot zugeordnet.");return;
            }
            if(b.s.kind==Kind.QUARRY&&(cx==b.s.station.x()>>4)&&(cz==b.s.station.z()>>4)){p.sendMessage("Die Station muss außerhalb des Steinbruch-Chunks stehen.");return;}
            if(b.s.kind==Kind.QUARRY&&bots.values().stream().anyMatch(other->other!=b&&other.s.configured&&other.s.kind==Kind.QUARRY&&other.s.world.equals(b.s.world)&&other.s.chunkX==cx&&other.s.chunkZ==cz)){p.sendMessage("Dieser Chunk gehört bereits einem Steinbruchbot.");return;}
            if(!deploymentAllowed(p,target,held)){p.sendMessage("Platzieren ist hier gesperrt.");return;}
            b.s.position=new Pos(target.getX(),target.getY(),target.getZ()); b.s.resumePosition=b.s.position; b.s.configured=true; b.s.chunkX=cx;b.s.chunkZ=cz;b.s.cursor=0;
            b.s.heading=Math.floorMod(Math.round(p.getYaw()/90),4); b.s.route.clear();b.s.vein.clear();
            b.s.top=target.getY();
            if(b.s.kind==Kind.QUARRY)for(int x=0;x<16;x++)for(int z=0;z<16;z++)b.s.top=Math.max(b.s.top,world(b).getHighestBlockYAt((cx<<4)+x,(cz<<4)+z));
            b.s.phase=Phase.IDLE;b.s.planted.clear();b.s.treeBlocks.clear();b.s.treesScanned=false;b.treeScan=null;
            b.s.lumberStage=LumberCycle.Stage.CLEARING;b.s.lumberPlanned=false;b.s.grownTrunks.clear();
            if(save(b,b.items)){
                ItemStack rest=held.clone();rest.setAmount(held.getAmount()-1);p.getInventory().setItemInMainHand(rest.getAmount()==0?null:rest);
                spawn(b); move(b);p.sendMessage(name(b)+" verbunden und platziert. Im Terminal Start drücken.");
            }
            return;
        }
        if(tunnels.protectedBlock(target)){p.sendMessage("Hier ist bereits eine Maschine.");return;}
        BlockState previous=target.getState();target.setType(Material.POWERED_RAIL,false);
        BlockPlaceEvent placement=new BlockPlaceEvent(target,previous,event.getClickedBlock(),held.clone(),p,true,EquipmentSlot.HAND);Bukkit.getPluginManager().callEvent(placement);
        if(placement.isCancelled()||!placement.canBuild()||target.getType()!=Material.POWERED_RAIL){previous.update(true,false);return;}
        State s=new State();s.owner=p.getUniqueId();s.world=p.getWorld().getUID();s.kind=Kind.valueOf(kind);s.station=new Pos(target.getX(),target.getY(),target.getZ());s.position=s.station;
        Bot b=new Bot(s,new ItemStack[SIZE]);
        if(!save(b,b.items)){previous.update(true,false);return;}
        bots.put(s.id,b);
        if(p.getGameMode()!=GameMode.CREATIVE){ItemStack remainder=held.clone();remainder.setAmount(held.getAmount()-1);p.getInventory().setItemInMainHand(remainder.getAmount()==0?null:remainder);}
        try{spawn(b);}catch(RuntimeException ex){b.failed=true;b.status="Fehler · Anzeige konnte nicht erstellt werden";alerts.report(s.id,s.owner,name(b),b.status);plugin.getLogger().log(java.util.logging.Level.SEVERE,"Bot-Anzeige",ex);}
        p.sendMessage("Station erstellt. Rechtsklick öffnen → Bot platzieren. Ausgabekiste direkt neben die Schiene stellen.");
    }
    private boolean deploymentAllowed(Player p,Block target,ItemStack held){
        BlockState old=target.getState();target.setType(Material.POWERED_RAIL,false);
        BlockPlaceEvent event=new BlockPlaceEvent(target,old,target.getRelative(BlockFace.DOWN),held,p,true,EquipmentSlot.HAND);Bukkit.getPluginManager().callEvent(event);old.update(true,false);return !event.isCancelled()&&event.canBuild();
    }
    private void mark(Entity entity,Bot b){entity.getPersistentDataContainer().set(partKey,PersistentDataType.STRING,b.s.id.toString());entity.setPersistent(false);entity.setInvulnerable(true);entity.setGravity(false);}
    private void spawn(Bot b){
        if(world(b)==null)return;
        if(loaded(b,b.s.station)&&(b.terminal.isEmpty()||b.terminal.stream().anyMatch(e->!e.isValid()))){
            b.terminal.forEach(Entity::remove);b.terminal.clear();Location l=location(b,b.s.station).add(0,.9,0);
            b.terminal.add(world(b).spawn(l,Interaction.class,e->{mark(e,b);e.setInteractionWidth(.9f);e.setInteractionHeight(.8f);e.setResponsive(true);}));
            b.terminal.add(world(b).spawn(l,ItemDisplay.class,e->{mark(e,b);e.setItemStack(new ItemStack(Material.LECTERN));}));
            b.terminal.add(world(b).spawn(l.clone().add(0,.65,0),TextDisplay.class,e->{mark(e,b);e.text(Component.text(name(b)+" · Station"));e.setBillboard(Display.Billboard.CENTER);}));
        }
        if(loaded(b,b.s.position)&&(b.entity==null||!b.entity.isValid())) b.entity=world(b).spawn(location(b,b.s.position),PoweredMinecart.class,e->{mark(e,b);e.setMaxSpeed(0);e.customName(Component.text(name(b)));});
    }
    private void move(Bot b){spawn(b);if(b.entity!=null){if(!b.entity.teleport(location(b,b.s.position)))throw new IllegalStateException("Bot-Teleport verhindert");b.entity.setVelocity(new org.bukkit.util.Vector());}}
    @EventHandler public void interact(PlayerInteractEntityEvent event){Bot b=from(event.getRightClicked());if(b==null)return;event.setCancelled(true);if(event.getHand()==EquipmentSlot.HAND)open(event.getPlayer(),b,"Main",0);}
    private void icon(Page page,int slot,Material type,String text,String... lore){ItemStack item=ExchangeMenu.icon(type,text);var meta=item.getItemMeta();meta.lore(Arrays.stream(lore).map(Component::text).toList());item.setItemMeta(meta);page.inventory.setItem(slot,item);}
    private void render(Page p){
        p.inventory.clear();Bot b=p.bot;
        if(p.mode.equals("Cargo")){for(int i=0;i<54;i++)p.inventory.setItem(i,copy(b.items[CARGO+p.page*54+i]));return;}
        for(int i=0;i<p.inventory.getSize();i++)icon(p,i,Material.GRAY_STAINED_GLASS_PANE," ");
        if(p.mode.equals("Filter")){renderFilter(p);return;}
        if(p.mode.equals("Fuel")&&b.s.kind==Kind.LUMBER){for(int i=0;i<18;i++)p.inventory.setItem(i,copy(b.items[FUEL+i]));for(int i=0;i<9;i++)p.inventory.setItem(27+i,copy(b.items[21+i]));icon(p,22,Material.BONE_MEAL,"Oben: Setzlinge · unten: Knochenmehl (optional)","Gesammelte Setzlinge werden hier zum Nachpflanzen aufbewahrt.");icon(p,45,Material.ARROW,"Zurück");icon(p,53,Material.BARRIER,"Schließen");return;}
        if(p.mode.equals("Fuel")){for(int i=0;i<27;i++)p.inventory.setItem(i,copy(b.items[FUEL+i]));icon(p,27,Material.ARROW,"Zurück");icon(p,35,Material.BARRIER,"Schließen");return;}
        if(p.mode.equals("Ores")){for(int i=0;i<ORES.size();i++){Material ore=ORES.get(i);icon(p,i+9,ore,(b.s.ores.contains(ore.name())?"✓ ":"○ ")+ORE_NAMES.get(i),"Klicken: suchen / ignorieren");}icon(p,22,Material.ARROW,"Zurück");return;}
        icon(p,4,Material.PAPER,b.status,"Position: "+b.s.position.x()+", "+b.s.position.y()+", "+b.s.position.z(),"Energie: "+b.s.energy,
                b.s.kind==Kind.QUARRY?"Fortschritt: "+b.s.cursor+" / "+AdvancedBotRules.volume(b.s.top,world(b).getMinHeight()):"Scanner: 8 Blöcke · freie Wegsuche");
        icon(p,10,Material.LIME_WOOL,"Start","Arbeitet auch offline weiter");icon(p,12,Material.RED_WOOL,"Stop / Rückruf","Sofort zur Station, entladen und dort bleiben");
        icon(p,14,Material.COMPASS,"Bot platzieren / Einsatzort ändern","Gibt verknüpften Platzierer","Bei gestopptem Bot: Rechtsklick auf Zielboden");
        icon(p,19,Material.FURNACE,"Brennstoff");icon(p,21,Material.GOLDEN_PICKAXE,"Spitzhacke");icon(p,23,Material.GOLDEN_SHOVEL,"Schaufel");
        p.inventory.setItem(30,copy(b.items[0]));p.inventory.setItem(32,copy(b.items[1]));
        if(b.s.kind!=Kind.SEEKER){icon(p,25,Material.GOLDEN_AXE,"Axt");p.inventory.setItem(34,copy(b.items[2]));}
        icon(p,37,Material.CHEST,"Bot-Lager · Seite 1","54 Plätze · außerhalb rechtsklicken: Seite wechseln");icon(p,39,Material.CHEST,"Bot-Lager · Seite 2","Weitere 54 Plätze");
        icon(p,38,Material.CHEST,"Bot-Lager · Seite 3","54 Plätze");icon(p,40,Material.CHEST,"Bot-Lager · Seite 4","54 Plätze");icon(p,43,Material.HOPPER,"Itemfilter", "Ausschlussliste · "+b.s.excluded.size()+" Itemarten", "Gefilterte Beute wird beim Abbau verworfen");
        if(b.s.kind==Kind.LUMBER){icon(p,19,Material.OAK_SAPLING,"Setzlinge & Knochenmehl");for(int i:new int[]{21,23,30,32})icon(p,i,Material.GRAY_STAINED_GLASS_PANE," ");icon(p,4,Material.PAPER,b.status,"Phase: "+LumberCycle.label(b.s.lumberStage),"Arbeitschunk: "+b.s.chunkX+", "+b.s.chunkZ,"Bis zu 16 Pflanzplätze · breite/2×2-Bäume: 9","Ernte erst, wenn alle Bäume der Runde stehen");}
        if(b.s.kind==Kind.SEEKER)icon(p,41,Material.DIAMOND_ORE,"Erze auswählen",b.s.ores.size()+" Erzsorten ausgewählt");
        icon(p,49,Material.BOOK,"Bedienung","Inventar-Items anklicken: einlagern","Fach anklicken: entnehmen · rechts: 1 Item","Lager schließen: Escape · Terminal erneut öffnen");
        icon(p,51,Material.TNT,"Station abbauen","Nur gestoppt an der Basis und vollständig leer");icon(p,53,Material.BARRIER,"Schließen");
    }
    private void renderFilter(Page page){
        List<String> entries=new ArrayList<>(page.bot.s.excluded);
        for(int i=0;i<45;i++){int index=page.page*45+i;page.inventory.setItem(i,null);if(index<entries.size()){
            Material type=Material.matchMaterial(entries.get(index));if(type!=null)icon(page,i,type,"Verwerfen: "+type.name(),"Klicken: aus Filter entfernen");
        }}
        icon(page,45,Material.ARROW,"Zurück zum Terminal");
        icon(page,48,Material.ARROW,"Vorherige Seite");
        icon(page,49,Material.BOOK,"Ausschlussliste · Seite "+(page.page+1),"Item im eigenen Inventar anklicken: hinzufügen","Items werden nicht verbraucht","Nur zukünftige Abbaubeute wird verworfen","Filter gilt für die gesamte Itemart");
        icon(page,50,Material.ARROW,"Nächste Seite");icon(page,53,Material.BARRIER,"Schließen");
    }
    private void filterClick(Player player,Page page,int slot,InventoryClickEvent event){
        Bot b=page.bot;
        if(slot>=page.inventory.getSize()){
            if(event.getClickedInventory()!=player.getInventory())return;
            ItemStack item=event.getCurrentItem();if(BotInventory.empty(item))return;
            if(b.s.excluded.add(item.getType().name())&&save(b,b.items))refresh(b);return;
        }
        if(slot>=0&&slot<45){List<String> entries=new ArrayList<>(b.s.excluded);int index=page.page*45+slot;
            if(index<entries.size()){b.s.excluded.remove(entries.get(index));if(save(b,b.items))refresh(b);}return;
        }
        if(slot==45)later(player,page,()->open(player,b,"Main",0));
        else if(slot==48&&page.page>0)later(player,page,()->open(player,b,"Filter",page.page-1));
        else if(slot==50&&(page.page+1)*45<b.s.excluded.size())later(player,page,()->open(player,b,"Filter",page.page+1));
        else if(slot==53)later(player,page,player::closeInventory);
    }
    private ItemStack copy(ItemStack i){return BotInventory.empty(i)?null:i.clone();}
    private void open(Player player,Bot bot,String mode,int page){if(!usable(player,bot))return;Page p=new Page(bot,player,mode,page);render(p);player.openInventory(p.inventory);}
    private void refresh(Bot b){for(Player p:Bukkit.getOnlinePlayers())if(p.getOpenInventory().getTopInventory().getHolder() instanceof Page page&&page.bot==b)render(page);}
    private void later(Player p,Page page,Runnable task){Bukkit.getScheduler().runTask(plugin,()->{if(p.isOnline()&&p.getOpenInventory().getTopInventory()==page.inventory&&usable(p,page.bot))task.run();});}
    @EventHandler(priority=EventPriority.HIGHEST) public void click(InventoryClickEvent event){
        if(!(event.getInventory().getHolder() instanceof Page page))return;
        boolean cancelled=event.isCancelled();event.setCancelled(true);
        if(cancelled||!(event.getWhoClicked() instanceof Player p)||!page.player.equals(p.getUniqueId())||!usable(p,page.bot)||event instanceof InventoryCreativeEvent)return;
        if(!List.of(ClickType.LEFT,ClickType.RIGHT,ClickType.SHIFT_LEFT,ClickType.SHIFT_RIGHT).contains(event.getClick()))return;
        int slot=event.getRawSlot();boolean right=event.getClick().isRightClick();Bot b=page.bot;
        if(slot<0){if(page.mode.equals("Cargo")&&right)later(p,page,()->open(p,b,"Cargo",(page.page+1)%4));return;}
        if(page.mode.equals("Filter")){filterClick(p,page,slot,event);return;}
        if(slot>=page.inventory.getSize()){if(event.getClickedInventory()==p.getInventory())deposit(p,page,p.getInventory().getItem(event.getSlot()),event.getSlot(),-1);return;}
        if(page.mode.equals("Cargo")){transfer(p,page,CARGO+page.page*54+slot,right);return;}
        if(page.mode.equals("Fuel")&&b.s.kind==Kind.LUMBER){if(slot<18)transfer(p,page,FUEL+slot,right);else if(slot>=27&&slot<36)transfer(p,page,21+slot-27,right);else if(slot==45)later(p,page,()->open(p,b,"Main",0));else if(slot==53)later(p,page,p::closeInventory);return;}
        if(page.mode.equals("Fuel")){if(slot<27)transfer(p,page,FUEL+slot,right);else if(slot==27)later(p,page,()->open(p,b,"Main",0));else if(slot==35)later(p,page,p::closeInventory);return;}
        if(page.mode.equals("Ores")){if(slot>=9&&slot<9+ORES.size()){
            if(b.s.phase!=Phase.IDLE&&b.s.phase!=Phase.COMPLETE){p.sendMessage("Vor dem Ändern der Erzauswahl bitte stoppen.");return;}
            String ore=ORES.get(slot-9).name();if(!b.s.ores.remove(ore))b.s.ores.add(ore);b.s.route.clear();b.s.vein.clear();save(b,b.items);render(page);
        }else if(slot==22)later(p,page,()->open(p,b,"Main",0));return;}
        switch(slot){
            case 10 -> {if(!b.s.configured){p.sendMessage("Zuerst den Einsatzort mit dem Platzierer festlegen.");return;}if(b.s.kind==Kind.SEEKER&&b.s.ores.isEmpty()){p.sendMessage("Mindestens ein Erz auswählen.");return;}
                if(b.s.pendingPosition!=null){p.sendMessage("Ein gespeicherter Arbeitsschritt wird noch abgeschlossen.");return;}
                b.s.phase=Phase.WORKING;b.s.resume=true;if(b.s.resumePosition!=null)b.s.position=b.s.resumePosition;b.status="Gestartet";save(b,b.items);refresh(b);}
            case 12 -> {recall(b,false);refresh(b);}
            case 14 -> {ItemStack wand=ExchangeMenu.icon(Material.COMPASS,name(b)+" platzieren");var meta=wand.getItemMeta();meta.getPersistentDataContainer().set(deployKey,PersistentDataType.STRING,b.s.id.toString());wand.setItemMeta(meta);p.getInventory().addItem(wand).values().forEach(i->p.getWorld().dropItemNaturally(p.getLocation(),i));p.sendMessage("Platzierer ist mit dieser Station verbunden. Zielboden rechtsklicken.");}
            case 19 -> later(p,page,()->open(p,b,"Fuel",0));
            case 30 -> transfer(p,page,0,right);case 32 -> transfer(p,page,1,right);case 34 -> {if(b.s.kind!=Kind.SEEKER)transfer(p,page,2,right);}
            case 37 -> later(p,page,()->open(p,b,"Cargo",0));case 39 -> later(p,page,()->open(p,b,"Cargo",1));
            case 38 -> later(p,page,()->open(p,b,"Cargo",2));case 40 -> later(p,page,()->open(p,b,"Cargo",3)); case 43 -> later(p,page,()->open(p,b,"Filter",0));
            case 41 -> {if(b.s.kind==Kind.SEEKER)later(p,page,()->open(p,b,"Ores",0));}
            case 51 -> later(p,page,()->remove(p,b));case 53 -> later(p,page,p::closeInventory);
            default -> {}
        }
    }
    private void transfer(Player p,Page page,int slot,boolean one){
        if(!BotInventory.empty(p.getItemOnCursor())){deposit(p,page,p.getItemOnCursor(),-1,slot);return;}
        Bot b=page.bot;if(BotInventory.empty(b.items[slot]))return;ItemStack offered=b.items[slot].clone();if(one)offered.setAmount(1);
        ItemStack[] playerItems=BotInventory.copy(p.getInventory().getStorageContents());int moved=BotInventory.insert(playerItems,0,playerItems.length,offered);if(moved==0)return;
        ItemStack[] next=BotInventory.copy(b.items);next[slot].setAmount(next[slot].getAmount()-moved);if(BotInventory.empty(next[slot]))next[slot]=null;
        if(save(b,next)){p.getInventory().setStorageContents(playerItems);refresh(b);}
    }
    private void deposit(Player p,Page page,ItemStack item,int playerSlot,int target){
        if(BotInventory.empty(item))return;Bot b=page.bot;int from,to;
        if(page.mode.equals("Main")){
            String type=item.getType().name();int kind=type.endsWith("_PICKAXE")?0:type.endsWith("_SHOVEL")?1:type.endsWith("_AXE")&&b.s.kind!=Kind.SEEKER?2:-1;
            if(b.s.kind==Kind.LUMBER&&kind!=2)return;
            if(kind<0||target>=0&&target!=kind||!BotInventory.empty(b.items[kind]))return;from=kind;to=kind+1;
        }else if(page.mode.equals("Fuel")){if(b.s.kind==Kind.LUMBER){if(LumberRules.SAPLINGS.contains(item.getType().name())){from=FUEL;to=21;}else if(item.getType()==Material.BONE_MEAL){from=21;to=CARGO;}else return;}else{if(!item.getType().isFuel())return;from=FUEL;to=CARGO;}}
        else if(page.mode.equals("Cargo")){from=CARGO;to=SIZE;}else return;
        ItemStack offer=item.clone();if(from<FUEL)offer.setAmount(1);ItemStack[] next=BotInventory.copy(b.items);int moved=BotInventory.insert(next,from,to,offer);if(moved==0)return;
        if(save(b,next)){ItemStack rest=item.clone();rest.setAmount(item.getAmount()-moved);if(playerSlot<0)p.setItemOnCursor(BotInventory.empty(rest)?null:rest);else p.getInventory().setItem(playerSlot,BotInventory.empty(rest)?null:rest);refresh(b);}
    }
    @EventHandler public void drag(InventoryDragEvent event){if(event.getInventory().getHolder() instanceof Page)event.setCancelled(true);}
    private void remove(Player p,Bot b){
        if(b.s.phase==Phase.WORKING||b.s.phase==Phase.UNLOADING||b.s.pendingPosition!=null||!b.s.position.equals(b.s.station)||Arrays.stream(b.items).anyMatch(i->!BotInventory.empty(i))){p.sendMessage("Bot zurückrufen und alle Werkzeuge, Brennstoffe und Items entnehmen.");return;}
        try{store.remove(b.s.id);bots.remove(b.s.id);for(Player viewer:Bukkit.getOnlinePlayers())if(viewer.getOpenInventory().getTopInventory().getHolder() instanceof Page page&&page.bot==b)viewer.closeInventory();
            despawn(b);block(b,b.s.station).setType(Material.AIR);p.getInventory().addItem(item(b.s.kind)).values().forEach(i->p.getWorld().dropItemNaturally(p.getLocation(),i));alerts.progressed(b.s.id);
        }catch(Exception ex){b.failed=true;plugin.getLogger().log(java.util.logging.Level.SEVERE,"Station entfernen fehlgeschlagen",ex);}
    }
    private void tick(){
        if(stopping)return;
        Set<PluginChunks.Key> needed=new HashSet<>();
        for(Bot b:bots.values())if(!b.failed&&world(b)!=null&&(b.s.phase==Phase.WORKING||b.s.phase==Phase.UNLOADING||b.s.pendingPosition!=null)){
            needed.addAll(PluginChunks.area(b.s.world,b.s.station.x(),b.s.station.z(),1));
            needed.addAll(PluginChunks.area(b.s.world,b.s.position.x(),b.s.position.z(),b.s.kind==Kind.SEEKER?11:1));
            if(b.s.kind==Kind.LUMBER&&b.s.configured)needed.addAll(LumberRules.treeChunks(b.s.world,b.s.chunkX,b.s.chunkZ));
            if(b.s.kind==Kind.QUARRY&&b.s.configured){Pos next=AdvancedBotRules.quarry(b.s.chunkX,b.s.chunkZ,b.s.top,b.s.cursor);needed.addAll(PluginChunks.area(b.s.world,next.x(),next.z(),1));}
            if(b.s.pendingPosition!=null)needed.addAll(PluginChunks.area(b.s.world,b.s.pendingPosition.x(),b.s.pendingPosition.z(),0));
            for(Change c:b.s.pendingOthers)needed.addAll(PluginChunks.area(b.s.world,c.position().x(),c.position().z(),0));
        }
        PluginChunks.update(plugin,this,needed);
        for(Bot b:List.copyOf(bots.values())){
            if(b.failed||world(b)==null)continue;
            try{
                spawn(b);
                if(b.entity!=null&&b.entity.isValid()){b.entity.setVelocity(new org.bukkit.util.Vector());b.entity.setFireTicks(0);if(loaded(b,b.s.position)&&b.entity.getLocation().distanceSquared(location(b,b.s.position))>.1)move(b);}
                if(b.s.pendingPosition!=null){if(!recover(b))continue;}
                if(b.s.kind==Kind.LUMBER&&b.s.configured&&!b.s.treesScanned&&b.s.phase!=Phase.UNLOADING){
                    scanTrees(b);refresh(b);continue;
                }
                if(b.s.phase==Phase.IDLE||b.s.phase==Phase.COMPLETE)continue;
                if(block(b,b.s.station).getType()!=Material.POWERED_RAIL){pause(b,"Basisschiene fehlt");continue;}
                if(b.s.kind==Kind.LUMBER&&(b.s.phase==Phase.WORKING||b.s.resume)&&!collectLumberDrops(b)){refresh(b);continue;}
                if(b.s.phase==Phase.UNLOADING)unload(b);
                else if(--b.delay<=0){if(b.s.kind==Kind.QUARRY)quarry(b);else if(b.s.kind==Kind.LUMBER)lumber(b);else seek(b);}
                refresh(b);
            }catch(Exception ex){b.failed=true;b.status="Fehler · Bot sicher angehalten";alerts.report(b.s.id,b.s.owner,name(b),b.status);plugin.getLogger().log(java.util.logging.Level.SEVERE,"Bot "+b.s.id,ex);}
        }
        for(Player p:Bukkit.getOnlinePlayers())if(p.getOpenInventory().getTopInventory().getHolder() instanceof Page page&&!usable(p,page.bot))p.closeInventory();
    }
    private void pause(Bot b,String reason){b.status="Pause · "+reason;b.delay=10;alerts.report(b.s.id,b.s.owner,name(b),b.status);}
    private void progressed(Bot b){alerts.progressed(b.s.id);}
    private boolean inside(Bot b,Pos p){return p.y()>=world(b).getMinHeight()&&p.y()<world(b).getMaxHeight()&&loaded(b,p)&&world(b).getWorldBorder().isInside(location(b,p));}
    private boolean changesAllowed(Bot b,Block target,org.bukkit.block.data.BlockData after){
        if(tunnels.protectedBlock(target)||target.getState() instanceof TileState||target.getType().name().endsWith("_BED")){pause(b,"geschützter Block / Container bei "+target.getX()+", "+target.getY()+", "+target.getZ());return false;}
        if(b.entity==null||!b.entity.isValid()){pause(b,"Bot-Anzeige nicht verfügbar");return false;}
        EntityChangeBlockEvent machine=new EntityChangeBlockEvent(b.entity,target,after);Bukkit.getPluginManager().callEvent(machine);
        if(machine.isCancelled()){pause(b,"Schutzbereich verhindert Blockänderung");return false;}
        Player owner=Bukkit.getPlayer(b.s.owner);
        if(owner!=null){BlockBreakEvent event=new BlockBreakEvent(target,owner);event.setDropItems(false);event.setExpToDrop(0);Bukkit.getPluginManager().callEvent(event);if(event.isCancelled()){pause(b,"Schutzbereich verhindert Abbau");return false;}}
        return true;
    }
    private boolean replace(Bot b,Block target,Material material){
        if(target.getType()==material)return true;
        String before=target.getBlockData().getAsString();var data=Bukkit.createBlockData(material);
        if(!changesAllowed(b,target,data)||!before.equals(target.getBlockData().getAsString()))return false;
        if(material!=Material.AIR){
            Player owner=Bukkit.getPlayer(b.s.owner);if(owner!=null){BlockState old=target.getState();target.setType(material,false);BlockPlaceEvent event=new BlockPlaceEvent(target,old,target.getRelative(BlockFace.UP),new ItemStack(material),owner,true,EquipmentSlot.HAND);Bukkit.getPluginManager().callEvent(event);old.update(true,false);if(event.isCancelled()||!event.canBuild()){pause(b,"Schutzbereich verhindert Abdichten");return false;}}
        }
        b.s.pendingPosition=new Pos(target.getX(),target.getY(),target.getZ());b.s.pendingBefore=before;b.s.pendingAfter=data.getAsString();
        if(!save(b,b.items))return false;target.setBlockData(data,true);clearPending(b);return save(b,b.items);
    }
    private void clearPending(Bot b){b.s.pendingPosition=null;b.s.pendingBefore=null;b.s.pendingAfter=null;b.s.pendingOthers.clear();}
    private boolean recover(Bot b){
        if(!loaded(b,b.s.pendingPosition))return false;
        Block target=block(b,b.s.pendingPosition);
        if(target.getBlockData().getAsString().equals(b.s.pendingBefore))target.setBlockData(Bukkit.createBlockData(b.s.pendingAfter),true);
        for(Change change:b.s.pendingOthers){
            if(!loaded(b,change.position()))return false;
            Block other=block(b,change.position());if(other.getBlockData().getAsString().equals(change.before()))other.setBlockData(Bukkit.createBlockData(change.after()),true);
        }
        clearPending(b);return save(b,b.items);
    }
    private boolean wet(Block block){return block.isLiquid()||block.getType()==Material.BUBBLE_COLUMN||block.getBlockData() instanceof org.bukkit.block.data.Waterlogged data&&data.isWaterlogged();}
    private boolean clearFluids(Bot b,Pos at){
        for(Pos neighbour:at.neighbours()){
            if(!inside(b,neighbour))continue;Block other=block(b,neighbour);
            boolean outside=b.s.kind!=Kind.QUARRY||(neighbour.x()>>4)!=b.s.chunkX||(neighbour.z()>>4)!=b.s.chunkZ;
            if(outside&&wet(other)&&!replace(b,other,Material.COBBLESTONE))return false;
        }
        return true;
    }
    private int toolSlot(Bot b,Material material){
        if(b.s.kind==Kind.LUMBER&&LumberRules.treePart(material.name()))return 2;
        if(Tag.MINEABLE_SHOVEL.isTagged(material))return 1;
        if(Tag.MINEABLE_PICKAXE.isTagged(material)||material==Material.GLOW_LICHEN)return 0;
        if(Tag.MINEABLE_AXE.isTagged(material)||Tag.LEAVES.isTagged(material))return b.s.kind!=Kind.SEEKER?2:-2;
        return material.getHardness()==0?-1:-2;
    }
    private boolean mine(Bot b,Block target,Runnable advance){
        if(!clearFluids(b,new Pos(target.getX(),target.getY(),target.getZ())))return false;
        String before=target.getBlockData().getAsString();Material type=target.getType();
        if(!changesAllowed(b,target,Bukkit.createBlockData(Material.AIR)))return false;
        if(!before.equals(target.getBlockData().getAsString())){pause(b,"Block wurde verändert");return false;}
        boolean liquid=target.isLiquid()||type==Material.BUBBLE_COLUMN;
        int slot=liquid?-1:toolSlot(b,type);if(slot==-2){pause(b,"kein passendes Werkzeug für "+type.name());return false;}
        ItemStack tool=slot<0?new ItemStack(Material.AIR):b.items[slot];
        if(slot>=0&&BotInventory.empty(tool)){pause(b,(slot==0?"Spitzhacke":slot==1?"Schaufel":"Axt")+" fehlt");return false;}
        if(slot==0&&!tunnels.correctTier(type,tool.getType())){pause(b,"Spitzhacke zu schwach für "+type.name());return false;}
        ItemStack[] next=BotInventory.copy(b.items);
        if(!liquid)for(ItemStack drop:target.getDrops(tool))if(!b.s.excluded.contains(drop.getType().name())&&BotInventory.insert(next,CARGO,SIZE,drop)!=drop.getAmount()){recall(b,true);return false;}
        int energy=b.s.energy;
        if(!liquid&&energy==0){
            for(int i=FUEL;i<CARGO;i++)if(!BotInventory.empty(next[i])){
                int charge=MiningBots.fuelEnergy(next[i].getType());if(charge<=0)continue;boolean bucket=next[i].getType()==Material.LAVA_BUCKET;
                next[i].setAmount(next[i].getAmount()-1);if(BotInventory.empty(next[i]))next[i]=null;
                if(bucket&&BotInventory.insert(next,CARGO,SIZE,new ItemStack(Material.BUCKET))!=1){recall(b,true);return false;}energy=charge;break;
            }
            if(energy==0){pause(b,"Brennstoff fehlt");return false;}
        }
        if(!liquid&&slot>=0){var meta=next[slot].getItemMeta();if(meta instanceof org.bukkit.inventory.meta.Damageable damage&&!meta.isUnbreakable()){
            int unbreaking=next[slot].getEnchantmentLevel(org.bukkit.enchantments.Enchantment.UNBREAKING);
            if(java.util.concurrent.ThreadLocalRandom.current().nextInt(Math.max(1,unbreaking+1))==0){
                int max=damage.hasMaxDamage()?damage.getMaxDamage():next[slot].getType().getMaxDurability();
                if(damage.getDamage()+1>=max)next[slot]=null;else{damage.setDamage(damage.getDamage()+1);next[slot].setItemMeta(damage);}
            }
        }}
        Pos mined=new Pos(target.getX(),target.getY(),target.getZ());
        b.s.energy=liquid?energy:energy-1;advance.run();
        b.s.pendingPosition=mined;b.s.pendingBefore=before;b.s.pendingAfter="minecraft:air";
        if(!save(b,next))return false;var particles=target.getBlockData();target.setType(Material.AIR,true);clearPending(b);if(!save(b,b.items))return false;
        world(b).spawnParticle(Particle.BLOCK,target.getLocation().add(.5,.5,.5),10,.2,.2,.2,particles);
        int efficiency=slot<0?0:tool.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.EFFICIENCY);
        b.delay=liquid?1:Math.max(1,Math.min(20,(int)Math.ceil(type.getHardness()*2/(1+efficiency))));if(b.s.kind==Kind.SEEKER)b.delay=Math.max(1,(b.delay+1)/2);progressed(b);return true;
    }
    private void mineBatch(Bot b,List<Block> targets,java.util.function.IntConsumer advance,String message,int delay){
        ItemStack[] next=BotInventory.copy(b.items);int energy=b.s.energy; boolean quarry=b.s.kind==Kind.QUARRY;int credit=b.s.fuelCredit;int[] uses=b.s.toolUses.clone();
        int wearInterval=quarry?32:b.s.kind==Kind.LUMBER?8:1;
        List<Change> changes=new ArrayList<>();String stop=null;boolean full=false;
        for(Block target:targets){
            Pos pos=new Pos(target.getX(),target.getY(),target.getZ());
            if(b.s.kind==Kind.LUMBER&&!LumberRules.trackedTreePart(pos,target.getType().name(),b.s.chunkX,b.s.chunkZ,b.s.treeBlocks)){stop="Block gehört nicht zum erfassten Baum";break;}if(!inside(b,pos)){stop="Arbeitsbereich nicht verfügbar";break;}
            if(b.s.kind!=Kind.LUMBER&&!clearFluids(b,pos)){if(b.failed)return;stop=b.status.replaceFirst("^Pause · ","");break;}
            String before=target.getBlockData().getAsString();Material type=target.getType();
            if(!changesAllowed(b,target,Bukkit.createBlockData(Material.AIR))){stop=b.status.replaceFirst("^Pause · ","");break;}
            if(!before.equals(target.getBlockData().getAsString())){stop="Block wurde verändert";break;}
            boolean liquid=target.isLiquid()||type==Material.BUBBLE_COLUMN;
            int slot=liquid?-1:toolSlot(b,type);
            if(slot==-2){stop="kein passendes Werkzeug für "+type.name();break;}
            ItemStack tool=slot<0?new ItemStack(Material.AIR):next[slot];
            if(slot>=0&&BotInventory.empty(tool)){stop=(slot==0?"Spitzhacke":slot==1?"Schaufel":"Axt")+" fehlt oder ist zerbrochen";break;}
            if(slot==0&&!tunnels.correctTier(type,tool.getType())){stop="Spitzhacke zu schwach für "+type.name();break;}
            ItemStack[] candidate=BotInventory.copy(next);boolean fits=true;
            if(!liquid)for(ItemStack drop:target.getDrops(tool))if(!b.s.excluded.contains(drop.getType().name())&&insertHarvest(b,candidate,drop)!=drop.getAmount()){fits=false;break;}
            if(!fits){full=true;break;}
            int charge=energy;
            boolean spendEnergy=b.s.kind!=Kind.LUMBER&&!liquid&&(!quarry||credit==0); if(spendEnergy&&charge==0){
                for(int i=FUEL;i<CARGO;i++)if(!BotInventory.empty(candidate[i])){
                    int amount=MiningBots.fuelEnergy(candidate[i].getType());if(amount<=0)continue;
                    boolean bucket=candidate[i].getType()==Material.LAVA_BUCKET;candidate[i].setAmount(candidate[i].getAmount()-1);if(BotInventory.empty(candidate[i]))candidate[i]=null;
                    if(bucket&&BotInventory.insert(candidate,CARGO,SIZE,new ItemStack(Material.BUCKET))!=1){full=true;break;}charge=amount;break;
                }
                if(full)break;if(charge==0){stop="Brennstoff fehlt";break;}
            }
            boolean wear=!liquid&&slot>=0&&uses[slot]>=wearInterval-1; if(wear){var meta=candidate[slot].getItemMeta();if(meta instanceof org.bukkit.inventory.meta.Damageable damage&&!meta.isUnbreakable()){
                int level=candidate[slot].getEnchantmentLevel(org.bukkit.enchantments.Enchantment.UNBREAKING);
                if(java.util.concurrent.ThreadLocalRandom.current().nextInt(Math.max(1,level+1))==0){
                    int max=damage.hasMaxDamage()?damage.getMaxDamage():candidate[slot].getType().getMaxDurability();
                    if(damage.getDamage()+1>=max)candidate[slot]=null;else{damage.setDamage(damage.getDamage()+1);candidate[slot].setItemMeta(damage);}
                }
            }}
            changes.add(new Change(pos,before,"minecraft:air"));next=candidate;energy=spendEnergy?charge-1:charge;
            if(quarry&&!liquid)credit=credit==0?15:credit-1;
            if(!liquid&&slot>=0)uses[slot]=(uses[slot]+1)%wearInterval;
        }
        if(!changes.isEmpty()){
            for(Change c:changes)if(!block(b,c.position()).getBlockData().getAsString().equals(c.before())){pause(b,"Schicht verändert · erneut prüfen");return;}
            b.s.energy=energy;b.s.toolUses=uses;if(quarry)b.s.fuelCredit=credit;advance.accept(changes.size());
            Change first=changes.getFirst();b.s.pendingPosition=first.position();b.s.pendingBefore=first.before();b.s.pendingAfter=first.after();b.s.pendingOthers=new ArrayList<>(changes.subList(1,changes.size()));
            if(!save(b,next))return;
            for(Change c:changes)block(b,c.position()).setType(Material.AIR,true);
            clearPending(b);if(!save(b,b.items))return;
            b.status=message+" · "+changes.size()+" Blöcke";b.delay=delay;move(b);progressed(b);
            world(b).playSound(location(b,first.position()),Sound.BLOCK_STONE_BREAK,.7f,1f);
        }
        if(full)recall(b,true);else if(stop!=null)pause(b,stop);
    }
    private int insertHarvest(Bot b,ItemStack[] items,ItemStack drop){
        return b.s.kind==Kind.LUMBER?LumberInventory.insert(items,drop):BotInventory.insert(items,CARGO,SIZE,drop);
    }
    private boolean lumberDrop(Bot b,Item item){
        if(!item.isValid()||item.isDead()||item.getWorld()!=world(b)||item.getPickupDelay()>0||!item.canPlayerPickup())return false;
        Location at=item.getLocation();if((at.getBlockX()>>4)!=b.s.chunkX||(at.getBlockZ()>>4)!=b.s.chunkZ)return false;
        if(item.getOwner()!=null&&!teams.shares(item.getOwner(),b.s.owner))return false;
        ItemStack stack=item.getItemStack();return !BotInventory.empty(stack)&&LumberRules.treeDrop(stack.getType().name())&&!b.s.excluded.contains(stack.getType().name());
    }
    private boolean collectLumberDrops(Bot b){
        if(!b.s.configured||!world(b).isChunkLoaded(b.s.chunkX,b.s.chunkZ))return true;
        List<Item> drops=new ArrayList<>();List<ItemStack> offered=new ArrayList<>();Inventory pickupInventory=null;int checked=0;
        for(Entity entity:world(b).getChunkAt(b.s.chunkX,b.s.chunkZ).getEntities()){
            if(!(entity instanceof Item item)||!lumberDrop(b,item))continue;
            if(++checked>64)break;
            ItemStack before=item.getItemStack().clone();
            if(pickupInventory==null){pickupInventory=Bukkit.createInventory(null,54,Component.text("Holzfällerbot · Lager"));pickupInventory.setContents(Arrays.copyOfRange(b.items,CARGO,CARGO+54));}
            InventoryPickupItemEvent event=new InventoryPickupItemEvent(pickupInventory,item);Bukkit.getPluginManager().callEvent(event);
            if(event.isCancelled()||!lumberDrop(b,item)||!before.equals(item.getItemStack()))continue;
            drops.add(item);offered.add(before);if(drops.size()==16)break;
        }
        // Listeners may have stopped the bot, moved a drop or changed its stack during another event.
        if(b.failed||b.s.phase!=Phase.WORKING&&!(b.s.phase==Phase.UNLOADING&&b.s.resume))return false;
        for(int i=0;i<drops.size();i++)if(!lumberDrop(b,drops.get(i))||!offered.get(i).equals(drops.get(i).getItemStack()))return true;
        LumberInventory.Pickup pickup=LumberInventory.plan(b.items,offered);
        if(!pickup.commit(next->save(b,next),(index,count)->{
            Item item=drops.get(index);ItemStack rest=offered.get(index).clone();rest.setAmount(rest.getAmount()-count);
            if(BotInventory.empty(rest))item.remove();else item.setItemStack(rest);
        }))return false;
        if(pickup.full()&&b.s.phase==Phase.WORKING){recall(b,true);return false;}
        return true;
    }
    private void scanTrees(Bot b){
        World w=world(b);if(!w.isChunkLoaded(b.s.chunkX,b.s.chunkZ))return;
        if(b.treeScan==null)b.treeScan=new LumberScan(b.s.chunkX,b.s.chunkZ,w.getMinHeight(),w.getMaxHeight(),pos->{
            Block target=block(b,pos);Material type=target.getType();
            boolean persistent=type.name().endsWith("_LEAVES")&&target.getBlockData() instanceof org.bukkit.block.data.type.Leaves leaves&&leaves.isPersistent();
            return new LumberScan.Cell(type.name(),persistent);
        });
        if(!b.treeScan.step(4096)){b.status="Prüfe vorhandene Bäume · "+b.treeScan.percent()+" %";return;}
        b.s.treeBlocks.putAll(b.treeScan.harvest());b.s.treesScanned=true;
        if(!save(b,b.items))return;
        b.status="Baumscan fertig · "+b.treeScan.trees()+" Baumgruppen"+(b.s.phase==Phase.IDLE?" · Start drücken":"");b.treeScan=null;
    }
    private void lumber(Bot b){
        b.delay=4;
        switch(b.s.lumberStage){
            case CLEARING,HARVESTING -> harvestLumberRound(b);
            case PLANTING -> plantLumberRound(b);
            case GROWING -> growLumberRound(b);
        }
    }
    private LinkedHashMap<Pos,LumberCycle.Plot> lumberPlots(Bot b){
        LinkedHashMap<Pos,LumberCycle.Plot> result=new LinkedHashMap<>();
        for(var plot:b.s.planted.entrySet()){
            Pos trunk=b.s.grownTrunks.get(plot.getKey());String expected=LumberCycle.log(plot.getValue());
            boolean grown=trunk!=null&&LumberRules.trackedTreePart(trunk,expected,b.s.chunkX,b.s.chunkZ,b.s.treeBlocks)&&inside(b,trunk)
                    &&block(b,trunk).getType().name().equals(expected);
            result.put(plot.getKey(),LumberCycle.inspect(plot.getKey(),plot.getValue(),grown,p->block(b,p).getType().name()));
        }
        return result;
    }
    private boolean lumberStage(Bot b,LumberCycle.Stage stage){
        b.s.lumberStage=stage;b.status=LumberCycle.label(stage);return save(b,b.items);
    }
    private void harvestLumberRound(Bot b){
        boolean initial=b.s.lumberStage==LumberCycle.Stage.CLEARING;
        // Do not finish the round and forget an overhanging crown while its chunk is unavailable.
        for(var part:b.s.treeBlocks.entrySet())if(LumberRules.trackedTreePart(part.getKey(),part.getValue(),b.s.chunkX,b.s.chunkZ,b.s.treeBlocks)&&!inside(b,part.getKey())){
            pause(b,"Baumkrone nicht verfügbar oder außerhalb der Weltgrenze");return;
        }
        List<Pos> ready=b.s.treeBlocks.entrySet().stream()
                .filter(e->LumberRules.trackedTreePart(e.getKey(),e.getValue(),b.s.chunkX,b.s.chunkZ,b.s.treeBlocks)
                        &&block(b,e.getKey()).getType().name().equals(e.getValue()))
                .map(Map.Entry::getKey).sorted(Comparator.comparingInt(Pos::y).reversed())
                .limit(initial?256:Integer.MAX_VALUE).toList();
        if(!ready.isEmpty()){
            if(BotInventory.empty(b.items[2])){pause(b,"Axt fehlt oder ist zerbrochen");return;}
            // One shared transaction for the entire planted round. Capacity/tool/protection failures
            // may interrupt it; the journal keeps the remaining harvest for after unloading/restarting.
            mineBatch(b,ready.stream().map(p->block(b,p)).toList(),count->{for(Pos p:ready.subList(0,count))b.s.treeBlocks.remove(p);},
                    initial?"Räume vorhandene Bäume":"Ernte alle Bäume der Runde gemeinsam",2);
            return;
        }
        b.s.treeBlocks.clear();b.s.grownTrunks.clear();b.s.lumberPlanned=false;b.s.cursor=0;
        if(initial)b.s.planted.entrySet().removeIf(e->LumberRules.footprint(e.getKey(),e.getValue()).stream().noneMatch(p->block(b,p).getType().name().equals(e.getValue())));
        else b.s.planted.clear();
        if(!lumberStage(b,LumberCycle.next(b.s.lumberStage,false,List.of())))return;
        if(Arrays.stream(b.items,CARGO,SIZE).anyMatch(i->!BotInventory.empty(i)))
            recall(b,true,initial?"Wald geräumt · entlade an der Station":"Runde geerntet · entlade an der Station");
        else progressed(b);
    }
    private boolean planLumberRound(Bot b){
        Map<String,Integer> stock=new LinkedHashMap<>();
        for(int slot=FUEL;slot<21;slot++)if(!BotInventory.empty(b.items[slot])&&LumberRules.SAPLINGS.contains(b.items[slot].getType().name()))
            stock.merge(b.items[slot].getType().name(),b.items[slot].getAmount(),Integer::sum);
        Map<Pos,String> plan=new LinkedHashMap<>(b.s.planted);
        for(var supply:stock.entrySet()){
            String type=supply.getKey();int cost=LumberRules.width(type)*LumberRules.width(type),remaining=supply.getValue();
            for(Pos plot:LumberRules.plots(b.s.chunkX,b.s.chunkZ,b.s.top,type)){
                if(remaining<cost)break;if(!LumberRules.separated(plot,type,plan))continue;
                for(int dy=4;dy>=-4;dy--){Pos root=new Pos(plot.x(),plot.y()+dy,plot.z());
                    if(canPlant(b,root,type)){plan.put(root,type);remaining-=cost;break;}
                }
            }
        }
        if(plan.isEmpty()){pause(b,stock.isEmpty()?"Setzlinge fehlen":"Kein freier Pflanzplatz oder zu wenige Setzlinge für 2×2-Baum");return false;}
        b.s.planted.clear();b.s.planted.putAll(plan);b.s.lumberPlanned=true;
        b.status="Runde geplant · "+plan.size()+" Baumplätze";return save(b,b.items);
    }
    private void plantLumberRound(Bot b){
        if(!b.s.lumberPlanned&&!planLumberRound(b))return;
        var plots=lumberPlots(b);
        for(var plot:plots.entrySet()){
            if(plot.getValue()==LumberCycle.Plot.BLOCKED){pause(b,"Pflanzplatz blockiert oder Setzlinggruppe unvollständig bei "+plot.getKey().x()+", "+plot.getKey().y()+", "+plot.getKey().z());return;}
            if(plot.getValue()!=LumberCycle.Plot.EMPTY)continue;
            plantLumberTree(b,plot.getKey(),b.s.planted.get(plot.getKey()));return;
        }
        LumberCycle.Stage next=LumberCycle.next(b.s.lumberStage,false,plots.values());
        if(next!=b.s.lumberStage){if(lumberStage(b,next))progressed(b);}
        else pause(b,"Keine Pflanzplätze in der Runde");
    }
    private void plantLumberTree(Bot b,Pos root,String type){
        if(!canPlant(b,root,type)){pause(b,"Pflanzplatz braucht freien Boden und Platz nach oben");return;}
        int required=LumberRules.width(type)*LumberRules.width(type),available=0;
        for(int i=FUEL;i<21;i++)if(!BotInventory.empty(b.items[i])&&b.items[i].getType().name().equals(type))available+=b.items[i].getAmount();
        if(available<required){pause(b,"Setzlinge für die geplante Runde fehlen: "+type);return;}
        List<Change> changes=new ArrayList<>();
        for(Pos p:LumberRules.footprint(root,type)){
            Block target=block(b,p);String before=target.getBlockData().getAsString();BlockState previous=target.getState();Material material=Material.valueOf(type);
            if(!changesAllowed(b,target,Bukkit.createBlockData(material)))return;
            Player owner=Bukkit.getPlayer(b.s.owner);
            if(owner!=null){target.setType(material,false);BlockPlaceEvent event=new BlockPlaceEvent(target,previous,target.getRelative(BlockFace.DOWN),new ItemStack(material),owner,true,EquipmentSlot.HAND);Bukkit.getPluginManager().callEvent(event);previous.update(true,false);if(event.isCancelled()||!event.canBuild()){pause(b,"Pflanzen durch Schutzbereich gesperrt");return;}}
            changes.add(new Change(p,before,Bukkit.createBlockData(material).getAsString()));
        }
        if(!canPlant(b,root,type)||changes.stream().anyMatch(c->!block(b,c.position()).getBlockData().getAsString().equals(c.before()))){pause(b,"Pflanzplatz verändert · prüfe erneut");return;}
        ItemStack[] next=BotInventory.copy(b.items);int left=required;
        for(int i=FUEL;i<21&&left>0;i++)if(!BotInventory.empty(next[i])&&next[i].getType().name().equals(type)){int count=Math.min(left,next[i].getAmount());next[i].setAmount(next[i].getAmount()-count);if(BotInventory.empty(next[i]))next[i]=null;left-=count;}
        b.s.grownTrunks.remove(root);
        Change first=changes.getFirst();b.s.pendingPosition=first.position();b.s.pendingBefore=first.before();b.s.pendingAfter=first.after();b.s.pendingOthers=new ArrayList<>(changes.subList(1,changes.size()));
        if(!save(b,next))return;for(Change change:changes)block(b,change.position()).setBlockData(Bukkit.createBlockData(change.after()),true);clearPending(b);if(!save(b,b.items))return;
        lumberMove(b,root);long planted=lumberPlots(b).values().stream().filter(p->p==LumberCycle.Plot.SAPLING||p==LumberCycle.Plot.GROWN).count();
        b.status="Runde bepflanzen · "+planted+"/"+b.s.planted.size();progressed(b);
    }
    private void growLumberRound(Bot b){
        var plots=lumberPlots(b);LumberCycle.Stage nextStage=LumberCycle.next(b.s.lumberStage,false,plots.values());
        if(nextStage!=b.s.lumberStage){if(lumberStage(b,nextStage))progressed(b);return;}
        if(plots.isEmpty()){b.s.lumberPlanned=false;lumberStage(b,LumberCycle.Stage.PLANTING);return;}
        List<Pos> roots=new ArrayList<>(plots.keySet());List<LumberCycle.Plot> states=new ArrayList<>(plots.values());
        int index=LumberCycle.nextSapling(states,b.s.cursor);
        long grown=states.stream().filter(p->p==LumberCycle.Plot.GROWN).count();
        if(index<0){pause(b,"Runde unvollständig · Pflanzplätze oder Setzlinggruppen prüfen");return;}
        int meal=-1;for(int i=21;i<CARGO;i++)if(!BotInventory.empty(b.items[i])&&b.items[i].getType()==Material.BONE_MEAL){meal=i;break;}
        if(meal<0){b.status="Warte auf Wachstum · "+grown+"/"+roots.size()+" Bäume · Knochenmehl optional";progressed(b);return;}
        Pos root=roots.get(index);b.s.cursor=(index+1)%roots.size();
        ItemStack[] next=BotInventory.copy(b.items);next[meal].setAmount(next[meal].getAmount()-1);if(BotInventory.empty(next[meal]))next[meal]=null;
        if(!save(b,next))return;lumberMove(b,root);b.status="Versorge alle Setzlinge";block(b,root).applyBoneMeal(BlockFace.UP);
        if(!b.failed&&!b.status.startsWith("Pause")){
            long nowGrown=lumberPlots(b).values().stream().filter(p->p==LumberCycle.Plot.GROWN).count();
            b.status="Versorge alle Setzlinge · "+nowGrown+"/"+roots.size()+" Bäume";if(nowGrown>grown)progressed(b);
        }
    }
    private boolean canPlant(Bot b,Pos root,String type){
        for(Pos p:LumberRules.footprint(root,type)){
            if(!inside(b,p)||!LumberRules.inChunk(p,b.s.chunkX,b.s.chunkZ)||!LumberRules.soil(block(b,new Pos(p.x(),p.y()-1,p.z())).getType().name()))return false;
            for(int h=0;h<6;h++){Pos air=new Pos(p.x(),p.y()+h,p.z());if(!inside(b,air)||!block(b,air).getType().isAir()||tunnels.protectedBlock(block(b,air)))return false;}
        }return true;
    }
    private void lumberMove(Bot b,Pos root){
        for(Pos adjacent:root.neighbours())if(adjacent.y()==root.y()&&LumberRules.inChunk(adjacent,b.s.chunkX,b.s.chunkZ)
                &&block(b,adjacent).getType().isAir()&&block(b,new Pos(adjacent.x(),adjacent.y()+1,adjacent.z())).getType().isAir()){
            b.s.position=adjacent;b.s.resumePosition=adjacent;move(b);return;
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void treeGrows(StructureGrowEvent event){
        Pos at=new Pos(event.getLocation().getBlockX(),event.getLocation().getBlockY(),event.getLocation().getBlockZ());
        for(Bot b:bots.values())if(b.s.kind==Kind.LUMBER&&b.s.configured&&b.s.world.equals(event.getWorld().getUID())
                &&b.s.planted.entrySet().stream().anyMatch(e->LumberRules.footprint(e.getKey(),e.getValue()).contains(at))){
            if(b.failed||event.getBlocks().size()>1024){event.setCancelled(true);return;}
            // Complete planting before any crown can obstruct a reserved planting position.
            if(b.s.lumberStage==LumberCycle.Stage.PLANTING&&b.s.phase==Phase.WORKING){event.setCancelled(true);return;}
            Pos root=b.s.planted.entrySet().stream().filter(e->LumberRules.footprint(e.getKey(),e.getValue()).contains(at)).map(Map.Entry::getKey).findFirst().orElseThrow();
            Set<Pos> growingFootprint=b.s.planted.entrySet().stream().filter(e->LumberRules.footprint(e.getKey(),e.getValue()).contains(at))
                    .flatMap(e->LumberRules.footprint(e.getKey(),e.getValue()).stream()).collect(java.util.stream.Collectors.toSet());
            Map<Pos,String> tree=new LinkedHashMap<>();
            for(BlockState state:event.getBlocks()){
                Pos pos=new Pos(state.getX(),state.getY(),state.getZ());
                if(!LumberRules.inTreeArea(pos,b.s.chunkX,b.s.chunkZ)||!inside(b,pos)){event.setCancelled(true);pause(b,"Baumwachstum außerhalb des verfügbaren Kronenbereichs");return;}
                Block existing=state.getBlock();
                if(tunnels.protectedBlock(existing)||existing.getState() instanceof TileState||existing.getType().name().endsWith("_BED")){event.setCancelled(true);pause(b,"Baumwachstum an geschütztem Block");return;}
                if(LumberRules.treePart(state.getType().name())){
                    if(LumberRules.SAPLINGS.contains(existing.getType().name())&&!growingFootprint.contains(pos)){event.setCancelled(true);pause(b,"Baum braucht Abstand zu benachbarten Setzlingen");return;}
                    if(!existing.getType().isAir()&&!Objects.equals(b.s.treeBlocks.get(pos),existing.getType().name())&&!LumberRules.SAPLINGS.contains(existing.getType().name())
                            &&existing.getType()!=Material.SHORT_GRASS&&existing.getType()!=Material.TALL_GRASS&&existing.getType()!=Material.VINE){event.setCancelled(true);pause(b,"Baum braucht mehr freien Platz");return;}
                    tree.put(pos,state.getType().name());
                }
                if(b.entity!=null){EntityChangeBlockEvent change=new EntityChangeBlockEvent(b.entity,existing,state.getBlockData());Bukkit.getPluginManager().callEvent(change);if(change.isCancelled()){event.setCancelled(true);pause(b,"Baumwachstum durch Schutzbereich gesperrt");return;}}
            }
            String log=LumberCycle.log(b.s.planted.get(root));
            Pos trunk=tree.entrySet().stream().filter(e->e.getValue().equals(log)).map(Map.Entry::getKey)
                    .min(Comparator.comparingInt(p->Math.abs(p.x()-root.x())+Math.abs(p.y()-root.y())+Math.abs(p.z()-root.z()))).orElse(null);
            if(trunk==null){event.setCancelled(true);pause(b,"Baumwachstum ohne erkennbaren Stamm");return;}
            // Saving a proposed trunk is not enough to count a tree as grown: lumberPlots also
            // checks the actual world and remaining saplings, including after cancelled growth/restarts.
            b.s.treeBlocks.putAll(tree);b.s.grownTrunks.put(root,trunk);if(!save(b,b.items))event.setCancelled(true);return;
        }
    }
    private void quarry(Bot b){
        int total=AdvancedBotRules.volume(b.s.top,world(b).getMinHeight());
        if(b.s.cursor>=total){finishQuarry(b);return;}
        int layer=b.s.cursor/256;
        if(b.preparedLayer!=layer){b.preparedLayer=layer;b.delay=20;b.status="Bereite Schicht "+(b.s.top-layer)+" vor · 10 Sekunden";return;}
        int end=AdvancedBotRules.layerEnd(b.s.cursor,total);
        List<Block> layerBlocks=new ArrayList<>();
        for(int cursor=b.s.cursor;cursor<end;cursor++){
            Pos pos=AdvancedBotRules.quarry(b.s.chunkX,b.s.chunkZ,b.s.top,cursor);
            if(!inside(b,pos)){pause(b,"Weltgrenze oder Arbeitsbereich nicht verfügbar");return;}
            Block target=block(b,pos);
            if(target.getType().isAir()||target.getType()==Material.BEDROCK)continue;
            if(target.getType().getHardness()<0){pause(b,"unzerstörbares Hindernis bei "+pos.x()+", "+pos.y()+", "+pos.z());return;}
            layerBlocks.add(target);
        }
        if(layerBlocks.isEmpty()){b.s.cursor=end;save(b,b.items);return;}
        mineBatch(b,layerBlocks,count->{
            Block last=layerBlocks.get(count-1);Pos at=new Pos(last.getX(),last.getY(),last.getZ());
            b.s.cursor=count==layerBlocks.size()?end:AdvancedBotRules.quarryIndex(b.s.top,at)+1;
            b.s.position=at;b.s.resumePosition=at;
        },"Schicht "+(b.s.top-layer)+" gemeinsam abgebaut",1);
    }
    private void finishQuarry(Bot b){
        b.status="Steinbruch abgeschlossen";recall(b,false);Player owner=Bukkit.getPlayer(b.s.owner);if(owner!=null)owner.sendMessage("[Steinbruchbot] Chunk vollständig ausgehoben; unzerstörbare Blöcke bleiben stehen.");
    }
    private boolean walkable(Bot b,Pos pos){
        for(int h=0;h<2;h++){Pos at=new Pos(pos.x(),pos.y()+h,pos.z());if(!inside(b,at))return false;Block block=block(b,at);
            if(tunnels.protectedBlock(block)||block.getState() instanceof TileState||block.getType().getHardness()<0||block.getType().name().endsWith("_BED"))return false;
            if(block.getType().isAir()||wet(block)||MiningBots.isTorch(block.getType()))continue;
            if(toolSlot(b,block.getType())==-2)return false;
        }return true;
    }
    private boolean selected(Bot b,Pos pos){return inside(b,pos)&&b.s.ores.contains(MiningBotVeins.ore(block(b,pos).getType().name()));}
    private void seek(Bot b){
        if(b.s.ores.isEmpty()){pause(b,"kein Erz ausgewählt");return;}
        if(b.s.route.isEmpty()){
            long now=System.currentTimeMillis();b.unreachable.entrySet().removeIf(e->e.getValue()<=now);
            b.s.vein.removeIf(pos->!selected(b,pos)||b.unreachable.containsKey(pos));
            List<Pos> candidates=new ArrayList<>(b.s.vein);
            if(candidates.isEmpty()){
                Pos current=b.s.position;
                for(int x=-8;x<=8;x++)for(int y=-8;y<=8;y++)for(int z=-8;z<=8;z++){
                    Pos pos=new Pos(current.x()+x,current.y()+y,current.z()+z);if(selected(b,pos)&&!b.unreachable.containsKey(pos))candidates.add(pos);
                }
            }
            candidates.sort(java.util.Comparator.comparingInt(p->AdvancedBotRules.distance(b.s.position,p)));
            for(Pos goal:candidates.stream().limit(8).toList()){
                List<Pos> path=AdvancedBotRules.path(b.s.position,goal,p->walkable(b,p));
                if(!path.isEmpty()){
                    b.s.route=new ArrayList<>(path);
                    if(b.s.vein.isEmpty())b.s.vein=new ArrayList<>(MiningBotVeins.find(List.of(goal),goal,0,1,p->inside(b,p)?block(b,p).getType().name():""));
                    break;
                }
                b.unreachable.put(goal,now+30000);
            }
            if(b.s.route.isEmpty()){
                b.s.vein.clear(); // Unreachable ore must not prevent continued exploration.
                Pos current=b.s.position;int desired=preferredHeight(b);
                for(int turn=0;turn<4&&b.s.route.isEmpty();turn++){
                    int heading=(b.s.heading+turn)%4,dx=MiningBotWork.dx(heading*90),dz=MiningBotWork.dz(heading*90);
                    // Descend/ascend in steps to an ore-appropriate height, then explore without a distance cap.
                    int dy=Integer.compare(desired,current.y());
                    Pos forward=new Pos(current.x()+dx,current.y(),current.z()+dz);
                    Pos slope=new Pos(current.x()+dx,current.y()+dy,current.z()+dz);
                    if(dy!=0&&walkable(b,forward)&&walkable(b,slope)){b.s.route.add(forward);b.s.route.add(slope);b.s.heading=heading;}
                    else if(walkable(b,forward)){b.s.route.add(forward);b.s.heading=heading;}
                }
                if(b.s.route.isEmpty()){pause(b,"Hindernis oder Weltgrenze · kein freier Weg");return;}
            }
            if(!save(b,b.items))return;
        }
        Pos next=b.s.route.getFirst();
        if(!walkable(b,next)){b.s.route.clear();save(b,b.items);pause(b,"Hindernis auf der Route · suche neuen Weg");return;}
        for(int height=1;height>=0;height--){Pos at=new Pos(next.x(),next.y()+height,next.z());Block target=block(b,at);
            if(target.getType().isAir()||MiningBots.isTorch(target.getType()))continue;
            if(selected(b,at)){
                List<Pos> vein=MiningBotVeins.find(List.of(at),at,0,1,p->inside(b,p)?block(b,p).getType().name():"");
                List<Block> ores=vein.stream().map(p->block(b,p)).toList();
                mineBatch(b,ores,count->{b.s.vein.removeAll(vein.subList(0,count));b.s.route.clear();},"Erzader gemeinsam abgebaut",3);
                return;
            }
            if(mine(b,target,()->{}))b.status="Grabe Suchgang · Ziel "+next.x()+", "+next.y()+", "+next.z();return;
        }
        if(!clearFluids(b,next))return;
        b.s.position=next;b.s.resumePosition=next;b.s.route.removeFirst();b.status="Erkundung · "+next.x()+", "+next.y()+", "+next.z();
        if(save(b,b.items)){move(b);progressed(b);}
    }
    private int preferredHeight(Bot b){
        int preferred=b.s.ores.contains("ANCIENT_DEBRIS")?15:b.s.ores.contains("DIAMOND_ORE")||b.s.ores.contains("REDSTONE_ORE")?-54:
                b.s.ores.contains("LAPIS_ORE")?0:b.s.ores.contains("GOLD_ORE")?-16:b.s.ores.contains("IRON_ORE")?16:48;
        return Math.max(world(b).getMinHeight()+3,Math.min(world(b).getMaxHeight()-3,preferred));
    }
    private void recall(Bot b,boolean resume){
        recall(b,resume,resume?"Lager voll · zur Station zum Entladen":"Zurückgerufen · entlade");
    }
    private void recall(Bot b,boolean resume,String status){
        if(b.s.pendingPosition!=null){pause(b,"gespeicherten Arbeitsschritt zuerst abschließen");return;}
        if(b.s.phase!=Phase.UNLOADING&&!b.s.position.equals(b.s.station))b.s.resumePosition=b.s.position;
        b.s.position=b.s.station;b.s.phase=Phase.UNLOADING;b.s.resume=resume;
        b.status=status;
        if(save(b,b.items)){move(b);if(resume)alerts.report(b.s.id,b.s.owner,name(b),b.status);}
    }
    private void unload(Bot b){
        if(b.s.kind==Kind.LUMBER){ItemStack[] next=BotInventory.copy(b.items);if(LumberInventory.refill(next)>0&&!save(b,next))return;}
        boolean cargo=false;for(int i=CARGO;i<SIZE;i++)cargo|=!BotInventory.empty(b.items[i]);
        if(!cargo){
            boolean complete=b.s.kind==Kind.QUARRY&&b.s.cursor>=AdvancedBotRules.volume(b.s.top,world(b).getMinHeight());
            b.s.phase=complete?Phase.COMPLETE:b.s.resume?Phase.WORKING:Phase.IDLE;
            if(b.s.phase==Phase.WORKING&&b.s.resumePosition!=null)b.s.position=b.s.resumePosition;
            b.status=complete?"Steinbruch abgeschlossen":b.s.resume?"Setze Arbeit fort":"Station bereit";
            save(b,b.items);progressed(b);return;
        }
        Chest chest=null;
        for(BlockFace face:List.of(BlockFace.NORTH,BlockFace.EAST,BlockFace.SOUTH,BlockFace.WEST))if(block(b,b.s.station).getRelative(face).getState() instanceof Chest found&&!found.isLocked()){chest=found;break;}
        if(chest==null){pause(b,"Ausgabekiste fehlt neben der Station");return;}
        Inventory destination=chest.getInventory();
        for(int slot=CARGO;slot<SIZE;slot++)if(!BotInventory.empty(b.items[slot])){
            ItemStack offered=b.items[slot];Inventory source=Bukkit.createInventory(null,54);source.setItem(0,offered.clone());
            InventoryMoveItemEvent event=new InventoryMoveItemEvent(source,offered.clone(),destination,true);Bukkit.getPluginManager().callEvent(event);
            if(event.isCancelled()||!offered.isSimilar(event.getItem())||event.getItem().getAmount()!=offered.getAmount()){pause(b,"Kistentransfer gesperrt");return;}
            ItemStack[] contents=BotInventory.copy(destination.getStorageContents());int count=BotInventory.insert(contents,0,contents.length,offered);if(count==0)continue;
            ItemStack[] next=BotInventory.copy(b.items);next[slot].setAmount(offered.getAmount()-count);if(BotInventory.empty(next[slot]))next[slot]=null;
            if(save(b,next)){destination.setStorageContents(contents);b.status="Entlade in Ausgabekiste";}return;
        }
        pause(b,"Ausgabekiste voll");
    }
    private void despawn(Bot b){if(b.entity!=null)b.entity.remove();b.entity=null;b.terminal.forEach(Entity::remove);b.terminal.clear();}
    @EventHandler(ignoreCancelled=true) public void breakStation(BlockBreakEvent e){if(protectedBlock(e.getBlock())){e.setCancelled(true);e.getPlayer().sendMessage("Station über das Bot-Terminal abbauen.");}}
    @EventHandler(ignoreCancelled=true) public void physics(BlockPhysicsEvent e){if(at(e.getBlock())!=null)e.setCancelled(true);}
    @EventHandler(ignoreCancelled=true) public void piston(BlockPistonExtendEvent e){if(e.getBlocks().stream().anyMatch(this::protectedBlock))e.setCancelled(true);}
    @EventHandler(ignoreCancelled=true) public void retract(BlockPistonRetractEvent e){if(e.getBlocks().stream().anyMatch(this::protectedBlock))e.setCancelled(true);}
    @EventHandler(ignoreCancelled=true) public void explosion(EntityExplodeEvent e){e.blockList().removeIf(this::protectedBlock);}
    @EventHandler(ignoreCancelled=true) public void explosion(BlockExplodeEvent e){e.blockList().removeIf(this::protectedBlock);}
    @EventHandler public void damage(EntityDamageEvent e){if(from(e.getEntity())!=null)e.setCancelled(true);}
    @EventHandler public void burn(EntityCombustEvent e){if(from(e.getEntity())!=null)e.setCancelled(true);}
    @EventHandler public void damage(VehicleDamageEvent e){if(from(e.getVehicle())!=null)e.setCancelled(true);}
    @EventHandler public void destroy(VehicleDestroyEvent e){if(from(e.getVehicle())!=null)e.setCancelled(true);}
    @EventHandler public void collision(VehicleEntityCollisionEvent e){if(from(e.getVehicle())!=null)e.setCancelled(true);}
    @EventHandler(ignoreCancelled=true) public void flow(BlockFromToEvent e){
        Block to=e.getToBlock();if(protectedBlock(to)){e.setCancelled(true);return;}
        for(Bot b:bots.values())if(b.s.configured&&b.s.world.equals(to.getWorld().getUID())){
            if(b.s.kind==Kind.QUARRY&&(to.getX()>>4)==b.s.chunkX&&(to.getZ()>>4)==b.s.chunkZ&&to.getY()<=b.s.top){e.setCancelled(true);return;}
            if(b.s.kind==Kind.SEEKER&&Math.abs(to.getX()-b.s.position.x())<=1&&Math.abs(to.getZ()-b.s.position.z())<=1&&Math.abs(to.getY()-b.s.position.y())<=2){e.setCancelled(true);return;}
        }
    }
    @EventHandler(ignoreCancelled=true) public void falling(EntityChangeBlockEvent e){if(e.getEntity() instanceof FallingBlock falling&&protectedBlock(e.getBlock())){e.setCancelled(true);if(falling.getDropItem())e.getBlock().getWorld().dropItemNaturally(falling.getLocation(),new ItemStack(falling.getBlockData().getMaterial()));falling.remove();}}
    @EventHandler public void join(PlayerJoinEvent e){e.getPlayer().discoverRecipes(List.of(quarryRecipe,seekerRecipe,lumberRecipe));}
    @EventHandler public void load(ChunkLoadEvent e){if(!stopping)Bukkit.getScheduler().runTask(plugin,()->{if(!stopping)for(Bot b:bots.values())if(b.s.world.equals(e.getWorld().getUID()))spawn(b);});}
    @EventHandler(ignoreCancelled=true,priority=EventPriority.MONITOR) public void unloadChunk(ChunkUnloadEvent e){for(Bot b:bots.values())if(b.s.world.equals(e.getWorld().getUID())){
        if((b.s.position.x()>>4)==e.getChunk().getX()&&(b.s.position.z()>>4)==e.getChunk().getZ()){if(b.entity!=null)b.entity.remove();b.entity=null;}
        if((b.s.station.x()>>4)==e.getChunk().getX()&&(b.s.station.z()>>4)==e.getChunk().getZ()){b.terminal.forEach(Entity::remove);b.terminal.clear();}
    }}
    List<MachineOverview.Entry> machineEntries(){return bots.values().stream().map(b->new MachineOverview.Entry(b.s.id.toString(),b.s.owner,b.s.world,b.s.station.x(),b.s.station.y(),b.s.station.z(),name(b),b.s.kind==Kind.LUMBER?Material.IRON_AXE:Material.FURNACE_MINECART,b.status,"Bot: "+b.s.position.x()+", "+b.s.position.y()+", "+b.s.position.z(),false)).toList();}
    void disable(){stopping=true;for(Player p:Bukkit.getOnlinePlayers())if(p.getOpenInventory().getTopInventory().getHolder() instanceof Page)p.closeInventory();bots.values().forEach(this::despawn);PluginChunks.update(plugin,this,Set.of());Bukkit.removeRecipe(quarryRecipe);Bukkit.removeRecipe(seekerRecipe);Bukkit.removeRecipe(lumberRecipe);}
}
