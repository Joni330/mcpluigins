package de.casino;

import java.util.*;
import java.util.function.Function;
import de.casino.MiningBotVeins.Pos;

/** Round barriers: no harvesting while even one planned sapling is still growing. */
final class LumberCycle {
    enum Stage { CLEARING, PLANTING, GROWING, HARVESTING }
    enum Plot { EMPTY, SAPLING, GROWN, BLOCKED }

    static Plot inspect(Pos root,String type,boolean grownTrunkPresent,Function<Pos,String> block){
        List<String> cells=LumberRules.footprint(root,type).stream().map(block).toList();
        if(cells.stream().allMatch(type::equals))return Plot.SAPLING;
        if(cells.stream().anyMatch(LumberRules.SAPLINGS::contains))return Plot.BLOCKED;
        if(grownTrunkPresent)return Plot.GROWN;
        return cells.stream().allMatch(t->Set.of("AIR","CAVE_AIR","VOID_AIR").contains(t))?Plot.EMPTY:Plot.BLOCKED;
    }
    static Stage next(Stage stage,boolean remainingWood,Collection<Plot> plots){
        return switch(stage){
            case CLEARING,HARVESTING -> remainingWood?stage:Stage.PLANTING;
            case PLANTING -> !plots.isEmpty()&&plots.stream().allMatch(p->p==Plot.SAPLING||p==Plot.GROWN)?Stage.GROWING:stage;
            case GROWING -> !plots.isEmpty()&&plots.stream().allMatch(p->p==Plot.GROWN)?Stage.HARVESTING:
                    plots.contains(Plot.EMPTY)?Stage.PLANTING:stage;
        };
    }
    static int nextSapling(List<Plot> plots,int cursor){
        for(int offset=0;offset<plots.size();offset++){
            int index=Math.floorMod(cursor+offset,plots.size());if(plots.get(index)==Plot.SAPLING)return index;
        }
        return -1;
    }
    static String log(String sapling){return sapling.equals("MANGROVE_PROPAGULE")?"MANGROVE_LOG":sapling.replace("_SAPLING","_LOG");}
    static String label(Stage stage){return switch(stage){case CLEARING->"Vorhandene Bäume räumen";case PLANTING->"Runde bepflanzen";case GROWING->"Alle Bäume wachsen lassen";case HARVESTING->"Gemeinsame Ernte";};}
}
