package de.casino;

import java.util.Arrays;

final class StorageLayout {
    static final int PAGE_SIZE = 45, PAGES = 10, CAPACITY = PAGE_SIZE * PAGES;
    static final long PAGE_PRICE = 20_000; // cents
    static int capacity(int pages) {
        if (pages < PAGES) throw new IllegalArgumentException("Ungültige Seitenzahl");
        return Math.multiplyExact(pages, PAGE_SIZE);
    }
    static int pages(int capacity) {
        if (capacity < CAPACITY || capacity % PAGE_SIZE != 0) throw new IllegalArgumentException("Ungültige Lagergröße: " + capacity);
        return capacity / PAGE_SIZE;
    }
    static int withdrawal(int available,int maxStack,boolean shift){return Math.min(available,shift?Math.min(64,maxStack):1);}
    static <T> T[] migrate(T[] old) {
        if (old.length == 54) return Arrays.copyOf(old, CAPACITY);
        pages(old.length);
        return Arrays.copyOf(old, old.length);
    }
    static int page(int index) {
        if (index < 0) throw new IndexOutOfBoundsException(index); return index / PAGE_SIZE;
    }
    static int slot(int index) {
        if (index < 0) throw new IndexOutOfBoundsException(index); return index % PAGE_SIZE;
    }
    private StorageLayout() {}
}
