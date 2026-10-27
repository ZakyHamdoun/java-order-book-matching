package org.example.book;

import org.example.book.index.TreeMapPriceLevelIndex;
import org.example.order.Order;
import org.example.order.OrderSide;
import org.example.support.BookImplementation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

class OrderBookTest {
    @ParameterizedTest
    @EnumSource(BookImplementation.class)
    void testMarketOrderCannotRestInBook(BookImplementation implementation) {
        OrderBook book = implementation.book();
        for (OrderSide side : OrderSide.values()) {
            Order market = new Order(1, side, 0, 10, 10, 1);
            assertThrows(IllegalStateException.class, () -> book.addOrder(market));
            assertTrue(book.isEmpty());
            assertFalse(book.containsOrder(1));
            assertEquals(10, market.getRemainingVolume());
        }
    }

    private Order order(long id, OrderSide side, long price, long volume) {
        return new Order(id, side, price, volume, volume, id);
    }

    @Test
    void testNullBidOrAskIndexIsRejected() {
        TreeMapPriceLevelIndex bids = new TreeMapPriceLevelIndex(true);
        TreeMapPriceLevelIndex asks = new TreeMapPriceLevelIndex(false);
        assertThrows(NullPointerException.class, () -> new OrderBook(null, asks));
        assertThrows(NullPointerException.class, () -> new OrderBook(bids, null));
    }

    @ParameterizedTest
    @EnumSource(BookImplementation.class)
    void testNoBuyNoSell(BookImplementation implementation) {
        OrderBook book = implementation.book();
        assertTrue(book.isEmpty());
        assertNull(book.bestBid());
        assertNull(book.bestAsk());
        assertNull(book.getOrder(1));
        assertFalse(book.containsOrder(1));
        assertEquals("OrderBook[\n  BIDS:\n    (empty)\n  ASKS:\n    (empty)\n  totalOrders=0\n]", book.toString());
    }

    @ParameterizedTest
    @EnumSource(BookImplementation.class)
    void testNoBuyOnlySell(BookImplementation implementation) {
        OrderBook book = implementation.book();
        book.addOrder(order(1, OrderSide.SELL, 110, 10));
        Order best = order(2, OrderSide.SELL, 105, 20);
        book.addOrder(best);
        assertFalse(book.isEmpty());
        assertNull(book.bestBid());
        assertEquals(105, book.bestAsk().getPrice());
        assertSame(best, book.getOrder(2));
        assertTrue(book.containsOrder(2));
    }

    @ParameterizedTest
    @EnumSource(BookImplementation.class)
    void testOnlyBuyNoSell(BookImplementation implementation) {
        OrderBook book = implementation.book();
        book.addOrder(order(1, OrderSide.BUY, 100, 10));
        book.addOrder(order(2, OrderSide.BUY, 105, 20));
        assertFalse(book.isEmpty());
        assertNull(book.bestAsk());
        assertEquals(105, book.bestBid().getPrice());
    }

    @ParameterizedTest
    @EnumSource(BookImplementation.class)
    void testBuyAndSellAtSamePriceHaveIndependentLevels(BookImplementation implementation) {
        OrderBook book = implementation.book();
        OrderNode buy = book.addOrder(order(1, OrderSide.BUY, 100, 10));
        OrderNode sell = book.addOrder(order(2, OrderSide.SELL, 100, 20));
        assertNotSame(buy.getPriceLevel(), sell.getPriceLevel());
        book.cancelOrder(1);
        assertNull(book.bestBid());
        assertSame(sell.getPriceLevel(), book.bestAsk());
        assertEquals(20, book.bestAsk().getTotalVolume());
    }

    @ParameterizedTest
    @EnumSource(BookImplementation.class)
    void testSameSideSamePriceReusesLevelAndPreservesArrivalOrder(BookImplementation implementation) {
        OrderBook book = implementation.book();
        // Priority is metadata: FIFO here is the insertion order.
        OrderNode first = book.addOrder(new Order(1, OrderSide.BUY, 100, 10, 10, 99));
        OrderNode second = book.addOrder(new Order(2, OrderSide.BUY, 100, 20, 20, 1));
        assertSame(first.getPriceLevel(), second.getPriceLevel());
        assertSame(first.getOrder(), book.bestBid().peekOldest());
        assertSame(second.getOrder(), book.bestBid().peekNewest());
        assertEquals(2, book.bestBid().getSize());
        assertEquals(30, book.bestBid().getTotalVolume());
    }

    @ParameterizedTest
    @EnumSource(BookImplementation.class)
    void testNullFilledOrDuplicateOrderIsRejectedWithoutChanges(BookImplementation implementation) {
        OrderBook book = implementation.book();
        assertThrows(NullPointerException.class, () -> book.addOrder(null));
        Order filled = new Order(1, OrderSide.BUY, 100, 10, 0, 1);
        assertThrows(IllegalStateException.class, () -> book.addOrder(filled));
        Order original = order(1, OrderSide.BUY, 100, 10);
        book.addOrder(original);
        Order duplicate = order(1, OrderSide.SELL, 110, 20);
        assertThrows(IllegalArgumentException.class, () -> book.addOrder(duplicate));
        assertSame(original, book.getOrder(1));
        assertEquals(10, book.bestBid().getTotalVolume());
        assertNull(book.bestAsk());
    }

    @ParameterizedTest
    @EnumSource(BookImplementation.class)
    void testCancellingOneOrderKeepsLevelUntilLastOrderIsCancelled(BookImplementation implementation) {
        OrderBook book = implementation.book();
        OrderNode first = book.addOrder(order(1, OrderSide.BUY, 100, 10));
        book.addOrder(order(2, OrderSide.BUY, 100, 20));
        book.cancelOrder(1);
        assertFalse(book.containsOrder(1));
        assertNull(book.getOrder(1));
        assertTrue(first.isUnlinked());
        assertEquals(20, book.bestBid().getTotalVolume());
        assertEquals(1, book.bestBid().getSize());
        book.cancelOrder(2);
        assertTrue(book.isEmpty());
        assertNull(book.bestBid());
        assertThrows(IllegalArgumentException.class, () -> book.cancelOrder(2));
        assertThrows(IllegalArgumentException.class, () -> book.cancelOrder(99));
    }

    @ParameterizedTest
    @EnumSource(BookImplementation.class)
    void testPartialThenFullFillUpdatesVolumeLookupAndBestPrice(BookImplementation implementation) {
        OrderBook book = implementation.book();
        OrderNode best = book.addOrder(order(1, OrderSide.SELL, 100, 10));
        book.addOrder(order(2, OrderSide.SELL, 105, 20));
        book.fillOrder(best, 4);
        Order partiallyFilled = book.getOrder(1);
        PriceLevel bestAsk = book.bestAsk();
        assertNotNull(partiallyFilled);
        assertNotNull(bestAsk);
        assertSame(best.getOrder(), partiallyFilled);
        assertEquals(6, bestAsk.getTotalVolume());
        assertEquals(6, partiallyFilled.getRemainingVolume());
        book.fillOrder(best, 6);
        assertFalse(book.containsOrder(1));
        assertNull(book.getOrder(1));
        assertTrue(best.isUnlinked());
        assertEquals(105, book.bestAsk().getPrice());
        assertEquals(20, book.bestAsk().getTotalVolume());
    }

    @ParameterizedTest
    @EnumSource(BookImplementation.class)
    void testFullFillKeepsOtherOrdersAtSamePrice(BookImplementation implementation) {
        OrderBook book = implementation.book();
        OrderNode first = book.addOrder(order(1, OrderSide.BUY, 100, 10));
        OrderNode second = book.addOrder(order(2, OrderSide.BUY, 100, 20));
        book.fillOrder(first, 10);
        assertSame(second.getOrder(), book.bestBid().peekOldest());
        assertEquals(20, book.bestBid().getTotalVolume());
        book.fillOrder(second, 20);
        assertTrue(book.isEmpty());
    }

    @ParameterizedTest
    @EnumSource(BookImplementation.class)
    void testInvalidFillVolumeIsRejectedWithoutMutatingBook(BookImplementation implementation) {
        OrderBook book = implementation.book();
        OrderNode node = book.addOrder(order(1, OrderSide.BUY, 100, 10));
        for (long volume : new long[]{Long.MIN_VALUE, -1, 0, 11, Long.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> book.fillOrder(node, volume));
            Order unchanged = book.getOrder(1);
            assertNotNull(unchanged);
            assertEquals(10, unchanged.getRemainingVolume());
            assertEquals(10, book.bestBid().getTotalVolume());
            assertFalse(node.isUnlinked());
        }
        assertThrows(NullPointerException.class, () -> book.fillOrder(null, 1));
    }

    @ParameterizedTest
    @EnumSource(BookImplementation.class)
    void testForeignCancelledAndReplacedNodesCannotBeFilled(BookImplementation implementation) {
        OrderBook book = implementation.book();
        OrderNode original = book.addOrder(order(1, OrderSide.BUY, 100, 10));
        OrderBook other = implementation.book();
        OrderNode foreign = other.addOrder(order(1, OrderSide.BUY, 100, 10));
        assertThrows(IllegalArgumentException.class, () -> book.fillOrder(foreign, 1));
        assertEquals(10, other.bestBid().getTotalVolume());
        book.cancelOrder(1);
        assertThrows(IllegalArgumentException.class, () -> book.fillOrder(original, 1));
        OrderNode replacement = book.addOrder(order(1, OrderSide.BUY, 100, 20));
        assertThrows(IllegalArgumentException.class, () -> book.fillOrder(original, 1));
        assertSame(replacement.getOrder(), book.getOrder(1));
        book.fillOrder(replacement, 20);
        assertThrows(IllegalArgumentException.class, () -> book.fillOrder(replacement, 1));
    }

    @ParameterizedTest
    @EnumSource(BookImplementation.class)
    void testUnlinkedRegisteredNodeIsRejected(BookImplementation implementation) {
        OrderBook book = implementation.book();
        OrderNode node = book.addOrder(order(1, OrderSide.BUY, 100, 10));
        node.unlink();
        assertThrows(IllegalArgumentException.class, () -> book.fillOrder(node, 1));
        assertEquals(10, node.getOrder().getRemainingVolume());
    }

    @ParameterizedTest
    @EnumSource(BookImplementation.class)
    void testOverflowRejectionDoesNotRegisterNewOrderOrChangeExistingLevel(BookImplementation implementation) {
        OrderBook book = implementation.book();
        book.addOrder(order(1, OrderSide.BUY, 100, Long.MAX_VALUE));
        Order overflowing = order(2, OrderSide.BUY, 100, 1);
        assertThrows(ArithmeticException.class, () -> book.addOrder(overflowing));
        assertFalse(book.containsOrder(2));
        assertEquals(1, book.bestBid().getSize());
        assertEquals(Long.MAX_VALUE, book.bestBid().getTotalVolume());
    }

    @ParameterizedTest
    @EnumSource(BookImplementation.class)
    void testToStringShowsBidAndAskLevelsInBestPriceOrder(BookImplementation implementation) {
        OrderBook book = implementation.book();
        book.addOrder(order(1, OrderSide.BUY, 90, 10));
        book.addOrder(order(2, OrderSide.BUY, 95, 20));
        book.addOrder(order(3, OrderSide.SELL, 110, 30));
        book.addOrder(order(4, OrderSide.SELL, 105, 40));
        String text = book.toString();
        assertTrue(text.indexOf("price=95") < text.indexOf("price=90"));
        assertTrue(text.indexOf("price=105") < text.indexOf("price=110"));
        assertTrue(text.indexOf("BIDS:") < text.indexOf("ASKS:"));
        assertTrue(text.contains("totalOrders=4"));
        assertFalse(text.contains("OrderNode["));
        assertFalse(text.contains("(empty)"));
    }
}
