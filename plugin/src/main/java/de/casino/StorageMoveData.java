package de.casino;

import org.bukkit.configuration.file.YamlConfiguration;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Durable move journal. Only its current token may restore a packed storage. */
final class StorageMoveData {
    enum Phase { PACKED, PLACING, ACTIVE }
    record Point(UUID world,int x,int y,int z) {
        String link(UUID id) { return world+";"+x+";"+y+";"+z+";"+id; }
        static Point fromLink(String link) {
            String[] p=link.split(";"); if(p.length!=5)throw new IllegalArgumentException("Ungültige Lagerverbindung");
            UUID.fromString(p[4]);return new Point(UUID.fromString(p[0]),Integer.parseInt(p[1]),Integer.parseInt(p[2]),Integer.parseInt(p[3]));
        }
    }
    record Snapshot(UUID owner,byte[] items,int[] counts,Map<String,String> categories) {
        Snapshot { Objects.requireNonNull(owner);items=items.clone();counts=counts.clone();categories=Collections.unmodifiableMap(new LinkedHashMap<>(categories));
            StorageLayout.pages(counts.length);for(int count:counts)if(count<0||count>VirtualStorage.LIMIT)throw new IllegalArgumentException("Ungültige Lagermenge");
            if(items.length==0||categories.isEmpty()||categories.entrySet().stream().anyMatch(e->e.getKey()==null||e.getValue()==null))throw new IllegalArgumentException("Ungültige Umzugsdaten"); }
        @Override public byte[] items(){return items.clone();}
        @Override public int[] counts(){return counts.clone();}
    }
    record Move(UUID id,UUID token,Phase phase,Point origin,Point target,Snapshot snapshot) {}
    interface Commit { void move(Path source,Path target)throws IOException; }
    private final Path folder;
    private final Commit commit;
    private final Map<UUID,Move> records=new LinkedHashMap<>();
    StorageMoveData(Path root)throws Exception{this(root,StorageMoveData::moveNew);}
    StorageMoveData(Path root,Commit commit)throws Exception{
        folder=root.resolve("storage-moves");Files.createDirectories(folder);this.commit=commit;
        Map<UUID,Path> latest=new HashMap<>();
        try(var paths=Files.list(folder)){for(Path path:paths.filter(p->p.toString().endsWith(".yml")).toList())latest.merge(id(path),path,(a,b)->revision(a)>revision(b)?a:b);}
        for(var entry:latest.entrySet()){
            var y=new YamlConfiguration();y.load(entry.getValue().toFile());if(y.getInt("version")!=1)throw new IOException("Unbekanntes Lagerumzug-Format");
            Map<String,String> categories=new LinkedHashMap<>();for(var row:y.getMapList("categories"))categories.put((String)row.get("name"),(String)row.get("filter"));
            Snapshot snapshot=new Snapshot(UUID.fromString(y.getString("owner")),Base64.getDecoder().decode(y.getString("items")),y.getIntegerList("counts").stream().mapToInt(Integer::intValue).toArray(),categories);
            Move move=new Move(entry.getKey(),UUID.fromString(y.getString("token")),Phase.valueOf(y.getString("phase")),readPoint(y.getString("origin"),entry.getKey()),readPoint(y.getString("target"),entry.getKey()),snapshot);
            if(move.origin()==null||move.phase()!=Phase.PACKED&&move.target()==null)throw new IOException("Ungültiger Lagerumzug");records.put(move.id(),move);
        }
    }
    Move get(UUID id){return records.get(id);}
    Collection<Move> all(){return List.copyOf(records.values());}
    boolean accessible(UUID id,Point point){Move move=get(id);return move==null||move.phase()==Phase.ACTIVE&&point.equals(move.target());}
    String resolve(String link){
        if(link==null)return null;Point.fromLink(link);UUID id=UUID.fromString(link.split(";")[4]);Move move=get(id);
        if(move==null)return link;
        if(move.phase()!=Phase.ACTIVE)throw new IllegalStateException("Das Lager ist für den Umzug eingepackt. Übertragung pausiert.");
        return move.target().link(id);
    }
    Move pack(UUID id,Point origin,Snapshot snapshot)throws IOException{
        if(!accessible(id,origin))throw new IllegalStateException("Dieses Lager ist bereits im Umzug oder wurde ersetzt.");
        Move move=new Move(id,UUID.randomUUID(),Phase.PACKED,origin,null,snapshot);save(move);return move;
    }
    Move beginPlace(UUID id,UUID token,UUID player,boolean admin,Point target)throws IOException{
        Move old=require(id,token,Phase.PACKED);
        if(!admin&&!old.snapshot().owner().equals(player))throw new IllegalArgumentException("Dieses eingepackte Lager gehört einem anderen Spieler.");
        Move next=new Move(id,token,Phase.PLACING,old.origin(),target,old.snapshot());save(next);return next;
    }
    Move finish(UUID id,UUID token)throws IOException{
        Move old=require(id,token,Phase.PLACING);Move next=new Move(id,token,Phase.ACTIVE,old.origin(),old.target(),old.snapshot());save(next);return next;
    }
    Move rollback(UUID id,UUID token)throws IOException{
        Move old=require(id,token,Phase.PLACING);Move next=new Move(id,token,Phase.PACKED,old.origin(),null,old.snapshot());save(next);return next;
    }
    Move reissue(UUID id)throws IOException{
        Move old=get(id);if(old==null||old.phase()!=Phase.PACKED)throw new IllegalArgumentException("Nur ein eingepacktes Lager kann wiederhergestellt werden.");
        Move next=new Move(id,UUID.randomUUID(),Phase.PACKED,old.origin(),null,old.snapshot());save(next);return next;
    }
    private Move require(UUID id,UUID token,Phase phase){
        Move old=get(id);if(old==null||old.phase()!=phase||!old.token().equals(token))throw new IllegalArgumentException("Dieses Umzugsitem ist ungültig oder das Lager wurde bereits platziert.");return old;
    }
    private void save(Move move)throws IOException{
        var y=new YamlConfiguration();y.set("version",1);y.set("owner",move.snapshot().owner().toString());y.set("token",move.token().toString());y.set("phase",move.phase().name());
        y.set("origin",move.origin().link(move.id()));y.set("target",move.target()==null?null:move.target().link(move.id()));
        y.set("items",Base64.getEncoder().encodeToString(move.snapshot().items()));y.set("counts",Arrays.stream(move.snapshot().counts()).boxed().toList());
        y.set("categories",move.snapshot().categories().entrySet().stream().map(e->Map.of("name",e.getKey(),"filter",e.getValue())).toList());
        List<Path> previous;
        try(var paths=Files.list(folder)){previous=paths.filter(p->p.getFileName().toString().startsWith(move.id()+"--")&&p.toString().endsWith(".yml")).sorted(Comparator.comparingLong(StorageMoveData::revision).reversed()).toList();}
        long next=Math.max(System.currentTimeMillis(),previous.isEmpty()?1:revision(previous.getFirst())+1);
        Path target=folder.resolve(move.id()+"--"+String.format(Locale.ROOT,"%019d",next)+".yml"),temp=Files.createTempFile(folder,"move-",".tmp");
        try{Files.writeString(temp,y.saveToString());commit.move(temp,target);}finally{try{Files.deleteIfExists(temp);}catch(IOException ignored){}}
        records.put(move.id(),move);
        for(int i=1;i<previous.size();i++)try{Files.deleteIfExists(previous.get(i));}catch(IOException ignored){}
    }
    private static Point readPoint(String value,UUID id){if(value==null)return null;if(!value.endsWith(";"+id))throw new IllegalArgumentException("Lager-ID stimmt nicht überein");return Point.fromLink(value);}
    private static UUID id(Path path){return UUID.fromString(path.getFileName().toString().substring(0,36));}
    private static long revision(Path path){String name=path.getFileName().toString();return Long.parseLong(name.substring(38,name.length()-4));}
    private static void moveNew(Path source,Path target)throws IOException{try{Files.move(source,target,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException error){Files.move(source,target);}}
}
