package de.casino;

import org.bukkit.configuration.file.YamlConfiguration;
import java.nio.file.*;
import java.util.*;
import java.io.IOException;
import de.casino.MiningBotVeins.Pos;

/** Separate versioned files leave existing tunnel bots untouched. */
final class AdvancedBotData {
    enum Kind { QUARRY, SEEKER }
    enum Phase { IDLE, WORKING, UNLOADING, COMPLETE }
    record Change(Pos position,String before,String after) {}
    static final class State {
        UUID id=UUID.randomUUID(), owner, world;
        Kind kind; Phase phase=Phase.IDLE;
        Pos station, position, resumePosition, pendingPosition;
        String pendingBefore, pendingAfter;
        List<Change> pendingOthers=new ArrayList<>();
        boolean configured, resume;
        int chunkX,chunkZ,top,cursor,energy,heading; int fuelCredit; int[] toolUses=new int[3];
        Set<String> excluded=new LinkedHashSet<>();
        Set<String> ores=new LinkedHashSet<>(List.of("DIAMOND_ORE"));
        List<Pos> route=new ArrayList<>(), vein=new ArrayList<>();
        byte[] items=new byte[0];
    }
    private final Path folder;
    interface Commit { void move(Path source,Path target) throws IOException; }
    private final Commit commit;
    AdvancedBotData(Path root) throws Exception { this(root,AdvancedBotData::moveNew); }
    AdvancedBotData(Path root,Commit commit) throws Exception { folder=root.resolve("advanced-bots"); Files.createDirectories(folder); this.commit=commit; }
    void save(State s) throws Exception {
        var y=new YamlConfiguration(); y.set("version",2); y.set("owner",s.owner.toString()); y.set("world",s.world.toString());
        y.set("kind",s.kind.name()); y.set("phase",s.phase.name()); y.set("station",pos(s.station)); y.set("position",pos(s.position));
        y.set("resume-position",pos(s.resumePosition)); y.set("configured",s.configured); y.set("resume",s.resume);
        y.set("chunk-x",s.chunkX); y.set("chunk-z",s.chunkZ); y.set("top",s.top); y.set("cursor",s.cursor); y.set("energy",s.energy); y.set("heading",s.heading);
        y.set("fuel-credit",s.fuelCredit); y.set("tool-uses",Arrays.stream(s.toolUses).boxed().toList()); y.set("ores",new ArrayList<>(s.ores)); y.set("route",s.route.stream().map(AdvancedBotData::pos).toList()); y.set("vein",s.vein.stream().map(AdvancedBotData::pos).toList());
        y.set("pending.position",pos(s.pendingPosition)); y.set("pending.before",s.pendingBefore); y.set("pending.after",s.pendingAfter);
        y.set("pending.others",s.pendingOthers.stream().map(c->Map.of("position",pos(c.position()),"before",c.before(),"after",c.after())).toList());
        y.set("excluded",new ArrayList<>(s.excluded)); y.set("items",Base64.getEncoder().encodeToString(s.items));
        writeRevision(s.id,y.saveToString());
    }
    List<State> load() throws Exception {
        List<State> result=new ArrayList<>();
        Map<UUID,Path> latest=new HashMap<>();
        try(var files=Files.list(folder)){for(Path p:files.filter(f->f.toString().endsWith(".yml")).toList())latest.merge(id(p),p,(a,b)->revision(a)>revision(b)?a:b);}
        for(Path p:latest.values()) {
            var y=new YamlConfiguration(); y.load(p.toFile()); if(y.getInt("version")<1||y.getInt("version")>2) throw new IllegalStateException("Unbekanntes Bot-Format: "+p);
            if(y.getBoolean("deleted"))continue;
            State s=new State(); s.id=id(p); s.owner=UUID.fromString(y.getString("owner")); s.world=UUID.fromString(y.getString("world"));
            s.kind=Kind.valueOf(y.getString("kind")); s.phase=Phase.valueOf(y.getString("phase")); s.station=read(y.getIntegerList("station")); s.position=read(y.getIntegerList("position")); s.resumePosition=read(y.getIntegerList("resume-position"));
            s.configured=y.getBoolean("configured"); s.resume=y.getBoolean("resume"); s.chunkX=y.getInt("chunk-x"); s.chunkZ=y.getInt("chunk-z"); s.top=y.getInt("top"); s.cursor=y.getInt("cursor"); s.energy=y.getInt("energy"); s.heading=y.getInt("heading");
            s.fuelCredit=y.getInt("fuel-credit"); var uses=y.getIntegerList("tool-uses"); for(int i=0;i<Math.min(3,uses.size());i++)s.toolUses[i]=uses.get(i); s.excluded=new LinkedHashSet<>(y.getStringList("excluded")); s.ores=new LinkedHashSet<>(y.getStringList("ores")); s.route=positions(y.getList("route",List.of())); s.vein=positions(y.getList("vein",List.of()));
            s.pendingPosition=read(y.getIntegerList("pending.position")); s.pendingBefore=y.getString("pending.before"); s.pendingAfter=y.getString("pending.after"); s.items=Base64.getDecoder().decode(y.getString("items"));
            for(var row:y.getMapList("pending.others"))s.pendingOthers.add(new Change(readNumbers((List<?>)row.get("position")),(String)row.get("before"),(String)row.get("after")));
            if(s.station==null || s.position==null || s.cursor<0 || s.energy<0) throw new IllegalStateException("Beschädigte Bot-Daten: "+p);
            result.add(s);
        } return result;
    }
    void remove(UUID id) throws Exception { writeRevision(id,"version: 2\ndeleted: true\n"); }
    private static UUID id(Path path){return UUID.fromString(path.getFileName().toString().substring(0,36));}
    private static long revision(Path path){String name=path.getFileName().toString();return name.length()==40?0:Long.parseLong(name.substring(38,name.length()-4));}
    private void writeRevision(UUID id,String text) throws Exception {
        List<Path> existing;
        try(var paths=Files.list(folder)){existing=paths.filter(p->p.getFileName().toString().startsWith(id.toString())&&p.toString().endsWith(".yml")).sorted(java.util.Comparator.comparingLong(AdvancedBotData::revision).reversed()).toList();}
        long next=Math.max(System.currentTimeMillis(),existing.isEmpty()?1:revision(existing.getFirst())+1);
        Path target=folder.resolve(id+"--"+String.format(Locale.ROOT,"%019d",next)+".yml");
        Path tmp=Files.createTempFile(folder,id+"-",".tmp");
        try{Files.writeString(tmp,text);commit.move(tmp,target);}finally{try{Files.deleteIfExists(tmp);}catch(IOException ignored){}}
        // Never overwrite a file held open by Windows/backup tools. Keep the last good generation.
        for(int i=1;i<existing.size();i++)try{Files.deleteIfExists(existing.get(i));}catch(IOException ignored){}
    }
    private static void moveNew(Path source,Path target) throws IOException {
        for(int attempt=0;;attempt++)try{
            try{Files.move(source,target,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException ex){Files.move(source,target);}
            return;
        }catch(AccessDeniedException ex){
            if(attempt==3)throw ex;
            try{Thread.sleep(15L*(attempt+1));}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw ex;}
        }
    }
    private static List<Integer> pos(Pos p) { return p==null?null:List.of(p.x(),p.y(),p.z()); }
    private static Pos read(List<Integer> list) { if(list.isEmpty()) return null; if(list.size()!=3) throw new IllegalArgumentException("Position"); return new Pos(list.get(0),list.get(1),list.get(2)); }
    private static Pos readNumbers(List<?> row){return new Pos(((Number)row.get(0)).intValue(),((Number)row.get(1)).intValue(),((Number)row.get(2)).intValue());}
    private static List<Pos> positions(List<?> list) {
        List<Pos> result=new ArrayList<>(); for(Object row:list) { List<?> values=(List<?>)row; result.add(new Pos(((Number)values.get(0)).intValue(),((Number)values.get(1)).intValue(),((Number)values.get(2)).intValue())); } return result;
    }
}
