package de.casino;

import java.util.Arrays;

final class StorageLayout {
    static final int PAGE_SIZE = 45, PAGES = 10, CAPACITY = PAGE_SIZE * PAGES;
    static <T> T[] migrate(T[] old) {
        if (old.length != 54 && old.length != CAPACITY)
            throw new IllegalArgumentException("Unbekannte Speichergröße: " + old.length);
        return Arrays.copyOf(old, CAPACITY);
    }
    static int page(int index) {
        check(index); return index / PAGE_SIZE;
    }
    static int slot(int index) {
        check(index); return index % PAGE_SIZE;
    }
    private static void check(int index) {
        if (index < 0 || index >= CAPACITY) throw new IndexOutOfBoundsException(index);
    }
    private StorageLayout() {}
}
