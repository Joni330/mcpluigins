package de.casino;

/** Plans insertions without mutating either inventory; compatible stacks precede empty slots. */
final class StorageTransfer {
    private StorageTransfer() {}
    static int[] plan(int offered, int[] room, boolean[] matchingStack) {
        if (offered < 0 || room.length != matchingStack.length) throw new IllegalArgumentException();
        int[] moves = new int[room.length];
        int remaining = Math.min(16, offered);
        for (int pass = 0; pass < 2; pass++) for (int slot = 0; slot < room.length && remaining > 0; slot++) {
            if (matchingStack[slot] != (pass == 0)) continue;
            moves[slot] = Math.min(remaining, Math.max(0, room[slot]));
            remaining -= moves[slot];
        }
        return moves;
    }
}
