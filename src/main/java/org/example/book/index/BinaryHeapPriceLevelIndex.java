package org.example.book.index;

import org.example.book.PriceLevel;

import java.util.*;

public final class BinaryHeapPriceLevelIndex implements PriceLevelIndex {
    private final PriorityQueue<PriceLevel> heap;
    private final Map<Long, PriceLevel> levelsByPrice;

    public BinaryHeapPriceLevelIndex(boolean isBid) {
        Comparator<PriceLevel> comparator = Comparator.comparingLong(PriceLevel::getPrice);

        if (isBid)
            comparator = comparator.reversed();

        heap = new PriorityQueue<>(comparator);
        levelsByPrice = new HashMap<>();
    }

    @Override
    public PriceLevel get(long price) {
        return levelsByPrice.get(price);
    }

    @Override
    public void put(PriceLevel level) {
        Objects.requireNonNull(level);

        if (levelsByPrice.containsKey(level.getPrice()))
            throw new IllegalArgumentException("Duplicate price level");

        heap.add(level);
        levelsByPrice.put(level.getPrice(), level);
    }

    @Override
    public void remove(long price) {
        PriceLevel level = levelsByPrice.remove(price);

        if (level != null)
            heap.remove(level); // O(P) generally; O(log P) for the root in OpenJDK 26.
    }

    @Override
    public PriceLevel best() {
        return heap.peek(); // O(1).
    }

    @Override
    public boolean isEmpty() {
        return levelsByPrice.isEmpty();
    }

    @Override
    public Collection<PriceLevel> levels() {
        // O(P log P) time and O(P) space; heap iteration is not sorted.
        return heap.stream()
                .sorted(heap.comparator())
                .toList();
    }
}
