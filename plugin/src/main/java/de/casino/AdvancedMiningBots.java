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
    private final NamespacedKey itemKey,partKey,deployKey,quarryRecipe,seekerRecipe;
    private final Map<UUID,Bot> bots=new LinkedHashMap<>();
    private boolean stopping;
    private static final class Bot {
        final State s; ItemStack[] items; PoweredMinecart entity; final List<Entity> terminal=new ArrayList<>();
        final Map<Pos,Long> unreachable=new HashMap<>(); boolean failed; int delay,preparedLayer=-1; String status="Station bereit";
        Bot(State state,ItemStack[] items) { s=state; this.items=items; }
    }
    private static final class Page implements InventoryHolder {
        final Bot bot; final UUID player; final String mode; final int page; final Inventory inventory;
        Page(Bot bot,Player player,String mode,int page) {
            this.bot=bot; this.player=player.getUniqueId(); this.mode=mode; this.page=page;
            inventory=Bukkit.createInventory(this,mode.equals("Fuel")?36:mode.equals("Ores")?27:54,
                    Component.text(name(bot)+" · "+(mode.equals("Cargo")?"Lager "+(page+1)+"/4":mode.equals("Fuel")?"Brennstoff":mode.equals("Ores")?"Erzauswahl":mode.equals("Filter")?"Itemfilter":"Terminal")));
        }
        public Inventory getInventory(){return inventory;}
    }
    AdvancedMiningBots(JavaPlugin plugin,StorageTeams teams,MiningBots tunnels,BotAlerts alerts) throws Exception {
        this.plugin=plugin; this.teams=teams; this.tunnels=tunnels; this.alerts=alerts; store=new AdvancedBotData(plugin.getDataFolder().toPath());
        itemKey=new NamespacedKey(plugin,"advanced_bot_item"); partKey=new NamespacedKey(plugin,"advanced_bot_part"); deployKey=new NamespacedKey(plugin,"advanced_bot_deploy");
        quarryRecipe=new NamespacedKey(plugin,"quarry_bot_recipe"); seekerRecipe=new NamespacedKey(plugin,"seeker_bot_recipe");
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
        Bukkit.removeRecipe(quarryRecipe); Bukkit.removeRecipe(seekerRecipe); Bukkit.addRecipe(quarry); Bukkit.addRecipe(seeker);
        Bukkit.getOnlinePlayers().forEach(p->p.discoverRecipes(List.of(quarryRecipe,seekerRecipe)));
        tunnels.extraProtection(this::protectedBlock);
        Bukkit.getScheduler().runTaskTimer(plugin,this::tick,10,10);
    }
    static String name(Bot bot){return bot.s.kind==Kind.QUARRY?"Steinbruchbot":"Erzsucher";}
    ItemStack item(Kind kind) {
        ItemStack item=ExchangeMenu.icon(Material.FURNACE_MINECART,kind==Kind.QUARRY?"Steinbruchbot":"Erzsucher");
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
            if(b.s.kind==Kind.QUARRY&&(cx==b.s.station.x()>>4)&&(cz==b.s.station.z()>>4)){p.sendMessage("Die Station muss außerhalb des Steinbruch-Chunks stehen.");return;}
            if(b.s.kind==Kind.QUARRY&&bots.values().stream().anyMatch(other->other!=b&&other.s.configured&&other.s.kind==Kind.QUARRY&&other.s.world.equals(b.s.world)&&other.s.chunkX==cx&&other.s.chunkZ==cz)){p.sendMessage("Dieser Chunk gehört bereits einem Steinbruchbot.");return;}
            if(!deploymentAllowed(p,target,held)){p.sendMessage("Platzieren ist hier gesperrt.");return;}
            b.s.position=new Pos(target.getX(),target.getY(),target.getZ()); b.s.resumePosition=b.s.position; b.s.configured=true; b.s.chunkX=cx;b.s.chunkZ=cz;b.s.cursor=0;
            b.s.heading=Math.floorMod(Math.round(p.getYaw()/90),4); b.s.route.clear();b.s.vein.clear();
            b.s.top=target.getY();
            if(b.s.kind==Kind.QUARRY)for(int x=0;x<16;x++)for(int z=0;z<16;z++)b.s.top=Math.max(b.s.top,world(b).getHighestBlockYAt((cx<<4)+x,(cz<<4)+z));
            b.s.phase=Phase.IDLE;
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
        if(p.mode.equals("Fuel")){for(int i=0;i<27;i++)p.inventory.setItem(i,copy(b.items[FUEL+i]));icon(p,27,Material.ARROW,"Zurück");icon(p,35,Material.BARRIER,"Schließen");return;}
        if(p.mode.equals("Ores")){for(int i=0;i<ORES.size();i++){Material ore=ORES.get(i);icon(p,i+9,ore,(b.s.ores.contains(ore.name())?"✓ ":"○ ")+ORE_NAMES.get(i),"Klicken: suchen / ignorieren");}icon(p,22,Material.ARROW,"Zurück");return;}
        icon(p,4,Material.PAPER,b.status,"Position: "+b.s.position.x()+", "+b.s.position.y()+", "+b.s.position.z(),"Energie: "+b.s.energy,
                b.s.kind==Kind.QUARRY?"Fortschritt: "+b.s.cursor+" / "+AdvancedBotRules.volume(b.s.top,world(b).getMinHeight()):"Scanner: 8 Blöcke · freie Wegsuche");
        icon(p,10,Material.LIME_WOOL,"Start","Arbeitet auch offline weiter");icon(p,12,Material.RED_WOOL,"Stop / Rückruf","Sofort zur Station, entladen und dort bleiben");
        icon(p,14,Material.COMPASS,"Bot platzieren / Einsatzort ändern","Gibt verknüpften Platzierer","Bei gestopptem Bot: Rechtsklick auf Zielboden");
        icon(p,19,Material.FURNACE,"Brennstoff");icon(p,21,Material.GOLDEN_PICKAXE,"Spitzhacke");icon(p,23,Material.GOLDEN_SHOVEL,"Schaufel");
        p.inventory.setItem(30,copy(b.items[0]));p.inventory.setItem(32,copy(b.items[1]));
        if(b.s.kind==Kind.QUARRY){icon(p,25,Material.GOLDEN_AXE,"Axt");p.inventory.setItem(34,copy(b.items[2]));}
        icon(p,37,Material.CHEST,"Bot-Lager · Seite 1","54 Plätze · außerhalb rechtsklicken: Seite wechseln");icon(p,39,Material.CHEST,"Bot-Lager · Seite 2","Weitere 54 Plätze");
        icon(p,38,Material.CHEST,"Bot-Lager · Seite 3","54 Plätze");icon(p,40,Material.CHEST,"Bot-Lager · Seite 4","54 Plätze");icon(p,43,Material.HOPPER,"Itemfilter", "Ausschlussliste · "+b.s.excluded.size()+" Itemarten", "Gefilterte Beute wird beim Abbau verworfen");
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
            case 30 -> transfer(p,page,0,right);case 32 -> transfer(p,page,1,right);case 34 -> {if(b.s.kind==Kind.QUARRY)transfer(p,page,2,right);}
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
            String type=item.getType().name();int kind=type.endsWith("_PICKAXE")?0:type.endsWith("_SHOVEL")?1:type.endsWith("_AXE")&&b.s.kind==Kind.QUARRY?2:-1;
            if(kind<0||target>=0&&target!=kind||!BotInventory.empty(b.items[kind]))return;from=kind;to=kind+1;
        }else if(page.mode.equals("Fuel")){if(!item.getType().isFuel())return;from=FUEL;to=CARGO;}
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
                if(b.s.phase==Phase.IDLE||b.s.phase==Phase.COMPLETE)continue;
                if(block(b,b.s.station).getType()!=Material.POWERED_RAIL){pause(b,"Basisschiene fehlt");continue;}
                if(b.s.phase==Phase.UNLOADING)unload(b);
                else if(--b.delay<=0){if(b.s.kind==Kind.QUARRY)quarry(b);else seek(b);}
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
        if(Tag.MINEABLE_SHOVEL.isTagged(material))return 1;
        if(Tag.MINEABLE_PICKAXE.isTagged(material)||material==Material.GLOW_LICHEN)return 0;
        if(Tag.MINEABLE_AXE.isTagged(material)||Tag.LEAVES.isTagged(material))return b.s.kind==Kind.QUARRY?2:-2;
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
        List<Change> changes=new ArrayList<>();String stop=null;boolean full=false;
        for(Block target:targets){
            Pos pos=new Pos(target.getX(),target.getY(),target.getZ());
            if(!inside(b,pos)){stop="Arbeitsbereich nicht verfügbar";break;}
            if(!clearFluids(b,pos)){if(b.failed)return;stop=b.status.replaceFirst("^Pause · ","");break;}
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
            if(!liquid)for(ItemStack drop:target.getDrops(tool))if(!b.s.excluded.contains(drop.getType().name())&&BotInventory.insert(candidate,CARGO,SIZE,drop)!=drop.getAmount()){fits=false;break;}
            if(!fits){full=true;break;}
            int charge=energy;
            boolean spendEnergy=!liquid&&(!quarry||credit==0); if(spendEnergy&&charge==0){
                for(int i=FUEL;i<CARGO;i++)if(!BotInventory.empty(candidate[i])){
                    int amount=MiningBots.fuelEnergy(candidate[i].getType());if(amount<=0)continue;
                    boolean bucket=candidate[i].getType()==Material.LAVA_BUCKET;candidate[i].setAmount(candidate[i].getAmount()-1);if(BotInventory.empty(candidate[i]))candidate[i]=null;
                    if(bucket&&BotInventory.insert(candidate,CARGO,SIZE,new ItemStack(Material.BUCKET))!=1){full=true;break;}charge=amount;break;
                }
                if(full)break;if(charge==0){stop="Brennstoff fehlt";break;}
            }
            boolean wear=!liquid&&slot>=0&&(!quarry||uses[slot]==31); if(wear){var meta=candidate[slot].getItemMeta();if(meta instanceof org.bukkit.inventory.meta.Damageable damage&&!meta.isUnbreakable()){
                int level=candidate[slot].getEnchantmentLevel(org.bukkit.enchantments.Enchantment.UNBREAKING);
                if(java.util.concurrent.ThreadLocalRandom.current().nextInt(Math.max(1,level+1))==0){
                    int max=damage.hasMaxDamage()?damage.getMaxDamage():candidate[slot].getType().getMaxDurability();
                    if(damage.getDamage()+1>=max)candidate[slot]=null;else{damage.setDamage(damage.getDamage()+1);candidate[slot].setItemMeta(damage);}
                }
            }}
            changes.add(new Change(pos,before,"minecraft:air"));next=candidate;energy=spendEnergy?charge-1:charge; if(quarry&&!liquid){credit=credit==0?15:credit-1;if(slot>=0)uses[slot]=(uses[slot]+1)%32;}
        }
        if(!changes.isEmpty()){
            for(Change c:changes)if(!block(b,c.position()).getBlockData().getAsString().equals(c.before())){pause(b,"Schicht verändert · erneut prüfen");return;}
            b.s.energy=energy;if(quarry){b.s.fuelCredit=credit;b.s.toolUses=uses;}advance.accept(changes.size());
            Change first=changes.getFirst();b.s.pendingPosition=first.position();b.s.pendingBefore=first.before();b.s.pendingAfter=first.after();b.s.pendingOthers=new ArrayList<>(changes.subList(1,changes.size()));
            if(!save(b,next))return;
            for(Change c:changes)block(b,c.position()).setType(Material.AIR,true);
            clearPending(b);if(!save(b,b.items))return;
            b.status=message+" · "+changes.size()+" Blöcke";b.delay=delay;move(b);progressed(b);
            world(b).playSound(location(b,first.position()),Sound.BLOCK_STONE_BREAK,.7f,1f);
        }
        if(full)recall(b,true);else if(stop!=null)pause(b,stop);
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
        if(b.s.pendingPosition!=null){pause(b,"gespeicherten Arbeitsschritt zuerst abschließen");return;}
        if(b.s.phase!=Phase.UNLOADING&&!b.s.position.equals(b.s.station))b.s.resumePosition=b.s.position;
        b.s.position=b.s.station;b.s.phase=Phase.UNLOADING;b.s.resume=resume;
        b.status=resume?"Lager voll · zur Station zum Entladen":"Zurückgerufen · entlade";
        if(save(b,b.items)){move(b);if(resume)alerts.report(b.s.id,b.s.owner,name(b),b.status);}
    }
    private void unload(Bot b){
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
    @EventHandler public void join(PlayerJoinEvent e){e.getPlayer().discoverRecipes(List.of(quarryRecipe,seekerRecipe));}
    @EventHandler public void load(ChunkLoadEvent e){if(!stopping)Bukkit.getScheduler().runTask(plugin,()->{if(!stopping)for(Bot b:bots.values())if(b.s.world.equals(e.getWorld().getUID()))spawn(b);});}
    @EventHandler(ignoreCancelled=true,priority=EventPriority.MONITOR) public void unloadChunk(ChunkUnloadEvent e){for(Bot b:bots.values())if(b.s.world.equals(e.getWorld().getUID())){
        if((b.s.position.x()>>4)==e.getChunk().getX()&&(b.s.position.z()>>4)==e.getChunk().getZ()){if(b.entity!=null)b.entity.remove();b.entity=null;}
        if((b.s.station.x()>>4)==e.getChunk().getX()&&(b.s.station.z()>>4)==e.getChunk().getZ()){b.terminal.forEach(Entity::remove);b.terminal.clear();}
    }}
    void disable(){stopping=true;for(Player p:Bukkit.getOnlinePlayers())if(p.getOpenInventory().getTopInventory().getHolder() instanceof Page)p.closeInventory();bots.values().forEach(this::despawn);PluginChunks.update(plugin,this,Set.of());Bukkit.removeRecipe(quarryRecipe);Bukkit.removeRecipe(seekerRecipe);}
}
