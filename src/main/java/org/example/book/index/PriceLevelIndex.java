package org.example.book.index;

import org.example.book.PriceLevel;

import java.util.Collection;

public interface PriceLevelIndex {
    PriceLevel get(long price);

    void put(PriceLevel priceLevel);

    void remove(long price);

    PriceLevel best();

    boolean isEmpty();

    Collection<PriceLevel> levels();
}
