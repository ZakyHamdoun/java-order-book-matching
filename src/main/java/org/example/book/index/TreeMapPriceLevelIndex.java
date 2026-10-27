package org.example.book.index;

import org.example.book.PriceLevel;

import java.util.*;

public final class TreeMapPriceLevelIndex implements PriceLevelIndex {
    private final NavigableMap<Long, PriceLevel> levels;

    public TreeMapPriceLevelIndex(boolean isBid) {
        Comparator<Long> comparator = Comparator.naturalOrder();

        if (isBid)
            comparator = comparator.reversed();

        levels = new TreeMap<>(comparator);
    }

    @Override
    public PriceLevel get(long price) {
        return levels.get(price);
    }

    @Override
    public void put(PriceLevel level) {
        Objects.requireNonNull(level);

        levels.put(level.getPrice(), level);
    }

    @Override
    public void remove(long price) {
        levels.remove(price);
    }

    @Override
    public PriceLevel best() {
        // O(log P): firstEntry walks a balanced tree; P is the number of price levels.
        return levels.isEmpty()
                ? null
                : levels.firstEntry().getValue();
    }

    @Override
    public boolean isEmpty() {
        return levels.isEmpty();
    }

    @Override
    public Collection<PriceLevel> levels() {
        return levels.values();
    }
}
