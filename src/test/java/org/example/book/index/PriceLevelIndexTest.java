package org.example.book.index;

import org.example.book.PriceLevel;
import org.example.support.BookImplementation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

// Shared contract for every implementation of PriceLevelIndex.
class PriceLevelIndexTest {
    @ParameterizedTest
    @EnumSource(BookImplementation.class)
    void testEmptyIndexHasNoBestPriceOrLevels(BookImplementation implementation) {
        PriceLevelIndex index = implementation.index(true);
        assertTrue(index.isEmpty());
        assertNull(index.best());
        assertNull(index.get(100));
        assertTrue(index.levels().isEmpty());
        index.remove(100);
        assertTrue(index.isEmpty());
    }

    @ParameterizedTest
    @EnumSource(BookImplementation.class)
    void testBidPricesAreDescendingAndAskPricesAreAscending(BookImplementation implementation) {
        for (boolean bid : new boolean[]{true, false}) {
            PriceLevelIndex index = implementation.index(bid);
            for (long price : new long[]{100, Long.MAX_VALUE, 1, 105}) index.put(new PriceLevel(price));
            List<Long> expected = bid ? List.of(Long.MAX_VALUE, 105L, 100L, 1L)
                    : List.of(1L, 100L, 105L, Long.MAX_VALUE);
            assertEquals(expected, index.levels().stream().map(PriceLevel::getPrice).toList());
            assertEquals(expected.getFirst().longValue(), index.best().getPrice());
            for (long price : expected) assertEquals(price, index.get(price).getPrice());
        }
    }

    @ParameterizedTest
    @EnumSource(BookImplementation.class)
    void testRemovingBestMiddleAndLastLevelUpdatesAllLookups(BookImplementation implementation) {
        PriceLevelIndex index = implementation.index(true);
        for (long price : new long[]{90, 100, 110}) index.put(new PriceLevel(price));
        index.remove(100);
        assertNull(index.get(100));
        assertEquals(110, index.best().getPrice());
        index.remove(110);
        assertEquals(90, index.best().getPrice());
        index.remove(90);
        assertNull(index.best());
        assertTrue(index.isEmpty());
        assertTrue(index.levels().isEmpty());
        PriceLevel replacement = new PriceLevel(100);
        index.put(replacement);
        assertSame(replacement, index.best());
        assertSame(replacement, index.get(100));
    }

    @ParameterizedTest
    @EnumSource(BookImplementation.class)
    void testNullLevelIsRejectedWithoutChangingIndex(BookImplementation implementation) {
        PriceLevelIndex index = implementation.index(true);
        assertThrows(NullPointerException.class, () -> index.put(null));
        assertTrue(index.isEmpty());
    }

    @Test
    void testHeapRejectsDuplicatePriceAndPreservesOriginalLevel() {
        PriceLevelIndex index = new BinaryHeapPriceLevelIndex(true);
        PriceLevel original = new PriceLevel(100);
        index.put(original);
        PriceLevel duplicate = new PriceLevel(100);
        assertThrows(IllegalArgumentException.class, () -> index.put(duplicate));
        assertSame(original, index.get(100));
        assertEquals(List.of(original), new ArrayList<>(index.levels()));
    }

    @Test
    void testTreeMapReplacesExistingLevelAtSamePrice() {
        PriceLevelIndex index = new TreeMapPriceLevelIndex(true);
        index.put(new PriceLevel(100));
        PriceLevel replacement = new PriceLevel(100);
        index.put(replacement);
        assertSame(replacement, index.get(100));
        assertEquals(List.of(replacement), new ArrayList<>(index.levels()));
    }

    @ParameterizedTest
    @EnumSource(BookImplementation.class)
    void testSeededInsertionsAndRemovalsMatchSortedReference(BookImplementation implementation) {
        for (boolean bid : new boolean[]{true, false}) {
            PriceLevelIndex index = implementation.index(bid);
            Comparator<Long> comparator = bid ? Comparator.reverseOrder() : Comparator.naturalOrder();
            TreeMap<Long, PriceLevel> expected = new TreeMap<>(comparator);
            Random random = new Random(271828);
            for (int step = 0; step < 2_000; step++) {
                long price = random.nextInt(100) + 1;
                if (random.nextBoolean()) {
                    if (!expected.containsKey(price)) {
                        PriceLevel level = new PriceLevel(price);
                        expected.put(price, level);
                        index.put(level);
                    }
                } else {
                    expected.remove(price);
                    index.remove(price);
                }
                String context = "bid=" + bid + ", step=" + step;
                assertEquals(expected.isEmpty(), index.isEmpty(), context);
                assertSame(expected.isEmpty() ? null : expected.firstEntry().getValue(), index.best(), context);
                assertSame(expected.get(price), index.get(price), context);
                assertEquals(new ArrayList<>(expected.values()), new ArrayList<>(index.levels()), context);
            }
        }
    }
}
