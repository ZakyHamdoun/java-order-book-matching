package org.example.support;

import org.example.book.OrderBook;
import org.example.book.index.BinaryHeapPriceLevelIndex;
import org.example.book.index.PriceLevelIndex;
import org.example.book.index.TreeMapPriceLevelIndex;

// Runs the same behavioral contract against both production implementations.
public enum BookImplementation {
    TREE_MAP, BINARY_HEAP;

    public PriceLevelIndex index(boolean bid) {
        return this == TREE_MAP ? new TreeMapPriceLevelIndex(bid) : new BinaryHeapPriceLevelIndex(bid);
    }

    public OrderBook book() {
        return new OrderBook(index(true), index(false));
    }
}
