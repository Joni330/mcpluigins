package de.casino;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class HomesTest {
    final HomeData.Point point = new HomeData.Point(UUID.randomUUID(), 1.5, 70, -20, 90, 10);
    @Test void limitReplaceDeleteAndRestart(@TempDir Path dir) throws Exception {
        UUID id = UUID.randomUUID(); HomeData homes = new HomeData(dir);
        homes.set(id, "Haus", point); homes.set(id, "farm", point); homes.set(id, "mine", point);
        homes.set(id, "HAUS", point);
        assertThrows(IllegalArgumentException.class, () -> homes.set(id, "vier", point));
        assertThrows(IllegalArgumentException.class, () -> homes.set(id, "list", point));
        assertThrows(IllegalArgumentException.class, () -> homes.set(id, "a.b", point));
        assertEquals(point, new HomeData(dir).get(id, "haus"));
        homes.delete(id, "farm"); homes.set(id, "vier", point);
        assertEquals(3, homes.list(id).size()); assertTrue(homes.list(UUID.randomUUID()).isEmpty());
    }
    @Test void failedSavePreservesPreviousHomes(@TempDir Path dir) throws Exception {
        HomeData homes = new HomeData(dir); UUID id = UUID.randomUUID(); homes.set(id, "haus", point);
        Files.createDirectory(dir.resolve("homes.yml.tmp"));
        assertThrows(java.io.IOException.class, () -> homes.delete(id, "haus"));
        assertEquals(point, homes.get(id, "haus"));
        assertEquals(point, new HomeData(dir).get(id, "haus"));
    }
    @Test void teamAccessAndDissolution(@TempDir Path dir) throws Exception {
        StorageTeams teams = new StorageTeams(dir); UUID leader = UUID.randomUUID(), member = UUID.randomUUID();
        teams.create(leader, "Team"); teams.invite(leader, member); teams.accept(member, "Team");
        teams.setHome(member, point); assertEquals(point, teams.home(leader)); assertEquals(point, new StorageTeams(dir).home(leader));
        teams.kick(leader, member); assertThrows(IllegalArgumentException.class, () -> teams.home(member));
        teams.dissolve(leader); teams.create(member, "Team");
        assertThrows(IllegalArgumentException.class, () -> teams.home(member));
    }
}
