package de.casino;

import de.casino.XpTankData.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class XpTankDataTest {
    @TempDir Path root;
    private Tank tank() { return new Tank(UUID.randomUUID(), UUID.randomUUID(), new Position(UUID.randomUUID(), -17, -30, 49), 0, false, true); }
    @Test void ownershipLocationPointsAndSharingSurviveRestart() throws Exception {
        var data = new XpTankData(root); var empty = tank(); data.create(empty);
        var transfer = data.transfer(empty, 4321, 3210, true);
        assertEquals(1111, transfer.playerPoints());
        data.sharing(transfer.tank(), true);
        var restored = new XpTankData(root).get(empty.position());
        assertEquals(empty.id(), restored.id()); assertEquals(empty.owner(), restored.owner());
        assertEquals(3210, restored.points()); assertTrue(restored.shared());
    }
    @Test void roundTripConservesPointsAcrossDifferentPlayerLevels() throws Exception {
        var data = new XpTankData(root); Tank current = tank(); data.create(current);
        int points = 10_023;
        for (int level : new int[]{0, 1, 16, 17, 30, 31, 32, 60}) {
            int sender = XpAmounts.levelPoints(level) + 3;
            long amount = XpAmounts.deposit(sender, level, 5, current.points());
            var deposit = data.transfer(current, sender, amount, true); current = deposit.tank();
            assertEquals(sender, deposit.playerPoints() + amount);
            var withdraw = data.transfer(current, points, amount, false); current = withdraw.tank();
            assertEquals(points + amount, withdraw.playerPoints());
            assertEquals(0, current.points());
        }
    }
    @Test void staleSecondWithdrawalCannotReuseOldBalance() throws Exception {
        var data = new XpTankData(root); Tank empty = tank(); data.create(empty);
        Tank full = data.transfer(empty, 100, 100, true).tank();
        assertEquals(100, data.transfer(full, 0, 100, false).playerPoints());
        assertThrows(IllegalArgumentException.class, () -> data.transfer(full, 0, 100, false));
        assertEquals(0, new XpTankData(root).get(empty.position()).points());
    }
    @Test void failedWriteLeavesOriginalBalanceAndSharingUnchanged() throws Exception {
        var data = new XpTankData(root); Tank empty = tank(); data.create(empty);
        Tank full = data.transfer(empty, 50, 50, true).tank();
        var failing = new XpTankData(root, (source, target) -> { throw new AccessDeniedException(target.toString()); });
        assertThrows(IOException.class, () -> failing.transfer(full, 10, 10, true));
        assertThrows(IOException.class, () -> failing.transfer(full, 10, 10, false));
        assertThrows(IOException.class, () -> failing.sharing(full, true));
        assertEquals(full, failing.get(full.position()));
        assertEquals(full, new XpTankData(root).get(full.position()));
        try (var paths = Files.list(root.resolve("xp-tanks"))) { assertEquals(2, paths.count()); }
    }
    @Test void filledTankCannotBeRemovedAndEmptyRemovalIsDurable() throws Exception {
        var data = new XpTankData(root); Tank empty = tank(); data.create(empty);
        Tank full = data.transfer(empty, 50, 50, true).tank();
        assertThrows(IllegalArgumentException.class, () -> data.remove(full));
        Tank drained = data.transfer(full, 0, 50, false).tank(); data.remove(drained);
        assertNull(new XpTankData(root).get(empty.position()));
        var replacement = new Tank(UUID.randomUUID(), UUID.randomUUID(), empty.position(), 0, false, true);
        data.create(replacement); assertEquals(replacement, new XpTankData(root).get(empty.position()));
        assertThrows(IllegalArgumentException.class, () -> data.create(empty));
    }
    @Test void invalidTransfersCannotOverdrawEitherSideOrOverflow() throws Exception {
        var data = new XpTankData(root); Tank empty = tank(); data.create(empty);
        assertThrows(IllegalArgumentException.class, () -> data.transfer(empty, 100, 101, true));
        assertThrows(IllegalArgumentException.class, () -> data.transfer(empty, 0, 1, false));
        assertThrows(IllegalArgumentException.class, () -> data.transfer(empty, 0, 0, true));
        Tank full = data.transfer(empty, 100, 100, true).tank();
        assertThrows(IllegalArgumentException.class, () -> data.transfer(full, Integer.MAX_VALUE, 1, false));
        assertEquals(full, new XpTankData(root).get(full.position()));
    }
    @Test void brokenLatestGenerationDoesNotRestoreOlderXpOrOverwriteEvidence() throws Exception {
        var data = new XpTankData(root); Tank empty = tank(); data.create(empty); data.transfer(empty, 50, 50, true);
        Path latest;
        try (var paths = Files.list(root.resolve("xp-tanks"))) { latest = paths.sorted().toList().getLast(); }
        Files.writeString(latest, "version: 999\n");
        assertThrows(Exception.class, () -> new XpTankData(root));
        assertEquals("version: 999\n", Files.readString(latest));
    }
    @Test void generationFilesStayBoundedAcrossManyUpdates() throws Exception {
        var data = new XpTankData(root); Tank current = tank(); data.create(current);
        for (int i = 0; i < 12; i++) current = data.transfer(current, 100, 1, true).tank();
        assertEquals(12, new XpTankData(root).get(current.position()).points());
        try (var paths = Files.list(root.resolve("xp-tanks"))) { assertEquals(2, paths.count()); }
    }
    @Test void commitsAlwaysTargetNewFilesInsteadOfReplacingEarlierBalances() throws Exception {
        Set<Path> destinations = new HashSet<>();
        var data = new XpTankData(root, (source, target) -> {
            assertFalse(Files.exists(target)); assertTrue(destinations.add(target)); Files.move(source, target);
        });
        Tank current = tank(); data.create(current);
        for (int i = 0; i < 5; i++) current = data.transfer(current, 100, 5, true).tank();
        assertEquals(6, destinations.size()); assertEquals(25, new XpTankData(root).get(current.position()).points());
    }
}
