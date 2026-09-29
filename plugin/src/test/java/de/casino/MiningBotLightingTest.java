package de.casino;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.bukkit.Material;
import java.nio.file.Path;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class MiningBotLightingTest {
    @Test void spacingResumesAfterRetracingTunnelWithoutDuplicateTorches() {
        var light = MiningBotLighting.off();
        assertFalse(light.due(80));
        light = light.toggle();
        assertFalse(light.due(7)); assertTrue(light.due(8));
        light = light.placed(8);
        assertFalse(light.due(0)); assertFalse(light.due(8)); assertFalse(light.due(15)); assertTrue(light.due(16));
        light = light.toggle(); assertFalse(light.due(16));
        light = light.toggle(); assertTrue(light.due(16));
    }
    @Test void everyStandingAndWallTorchVariantIsIgnored() {
        for (Material material : new Material[]{Material.TORCH, Material.WALL_TORCH, Material.SOUL_TORCH,
                Material.SOUL_WALL_TORCH, Material.REDSTONE_TORCH, Material.REDSTONE_WALL_TORCH}) assertTrue(MiningBots.isTorch(material));
        assertFalse(MiningBots.isTorch(Material.STONE)); assertFalse(MiningBots.isTorch(Material.GLOW_LICHEN));
    }
    @Test void switchAndProgressSurviveRestart(@TempDir Path dir) throws Exception {
        var store = new MiningBotStore(dir);
        var lighting = new MiningBotLighting(true, 24);
        store.save(new MiningBotStore.Saved(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 0, 64, 0, 0,
                false, new byte[]{1}, MiningBotWork.idle(), lighting));
        assertEquals(lighting, new MiningBotStore(dir).load().getFirst().lighting());
    }
}
