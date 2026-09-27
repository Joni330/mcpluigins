package de.casino;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class StorageTeamsTest {
    @Test void invitationRequiredAndAccessRevokedAfterKick(@TempDir Path dir) throws Exception {
        UUID owner = UUID.randomUUID(), friend = UUID.randomUUID(), stranger = UUID.randomUUID();
        StorageTeams teams = new StorageTeams(dir);
        teams.create(owner, "Freunde");
        assertThrows(IllegalArgumentException.class, () -> teams.accept(friend, "Freunde"));
        assertThrows(IllegalArgumentException.class, () -> teams.invite(stranger, friend));
        teams.invite(owner, friend);
        teams.accept(friend, "freunde");
        assertTrue(teams.shares(owner, friend));
        assertFalse(teams.shares(owner, stranger));
        StorageTeams reloaded = new StorageTeams(dir);
        assertTrue(reloaded.shares(owner, friend));
        reloaded.kick(owner, friend);
        assertFalse(reloaded.shares(owner, friend));
        assertTrue(reloaded.shares(friend, friend));
        assertFalse(new StorageTeams(dir).shares(owner, friend));
    }
    @Test void leavingAndDissolvingPreservesPersonalOwnership(@TempDir Path dir) throws Exception {
        UUID owner = UUID.randomUUID(), friend = UUID.randomUUID();
        StorageTeams teams = new StorageTeams(dir);
        teams.create(owner, "Team1"); teams.invite(owner, friend); teams.accept(friend, "Team1");
        assertThrows(IllegalArgumentException.class, () -> teams.leave(owner));
        teams.leave(friend);
        assertFalse(teams.shares(owner, friend));
        teams.invite(owner, friend); teams.dissolve(owner); teams.create(owner, "Team1");
        assertThrows(IllegalArgumentException.class, () -> teams.accept(friend, "Team1"));
        assertTrue(teams.shares(owner, owner));
    }
    @Test void failedSaveDoesNotGrantAccess(@TempDir Path dir) throws Exception {
        UUID owner = UUID.randomUUID(), friend = UUID.randomUUID();
        StorageTeams teams = new StorageTeams(dir);
        teams.create(owner, "Team1"); teams.invite(owner, friend);
        Path blocker = Files.createDirectory(dir.resolve("storage-teams.yml.tmp"));
        assertThrows(java.io.IOException.class, () -> teams.accept(friend, "Team1"));
        assertFalse(teams.shares(owner, friend));
        Files.delete(blocker);
        teams.accept(friend, "Team1");
        assertTrue(teams.shares(owner, friend));
    }
}
