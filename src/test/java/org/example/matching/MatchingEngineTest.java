package org.example.matching;

import org.example.book.OrderBook;
import org.example.matching.policy.MatchingPolicy;
import org.example.order.Order;
import org.example.order.OrderSide;
import org.example.support.BookImplementation;
import org.example.support.MatchingImplementation;
import org.example.trade.Trade;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class MatchingEngineTest {
    private static Stream<Arguments> implementations() {
        return Stream.of(BookImplementation.values()).flatMap(book ->
                Stream.of(MatchingImplementation.values()).flatMap(policy ->
                        Stream.of(OrderSide.values()).map(side -> Arguments.of(book, policy, side))));
    }

    private Order order(long id, OrderSide side, long price, long volume) {
        return new Order(id, side, price, volume, volume, id);
    }

    private OrderSide opposite(OrderSide side) {
        return side == OrderSide.BUY ? OrderSide.SELL : OrderSide.BUY;
    }

    private Trade trade(Order incoming, long restingId, long price, long volume) {
        return incoming.getSide() == OrderSide.BUY
                ? new Trade(incoming.getId(), restingId, price, volume)
                : new Trade(restingId, incoming.getId(), price, volume);
    }

    @Test
    void testConstructorRejectsNullDependencies() {
        MatchingPolicy policy = MatchingImplementation.FIFO.policy();
        OrderBook book = BookImplementation.TREE_MAP.book();
        assertThrows(NullPointerException.class,
                () -> new MatchingEngine(null, policy));
        assertThrows(NullPointerException.class,
                () -> new MatchingEngine(book, null));
    }

    @ParameterizedTest
    @MethodSource("implementations")
    void testInvalidSubmissionLeavesBookUnchanged(BookImplementation implementation,
                                                  MatchingImplementation policy, OrderSide side) {
        OrderBook book = implementation.book();
        MatchingEngine engine = new MatchingEngine(book, policy.policy());
        Order resting = order(1, opposite(side), 100, 10);
        book.addOrder(resting);
        assertSame(book, engine.getOrderBook());
        assertThrows(NullPointerException.class, () -> engine.submitOrder(null));
        Order duplicate = order(1, side, 100, 10);
        Order filled = new Order(2, side, 100, 10, 0, 2);
        assertThrows(IllegalArgumentException.class,
                () -> engine.submitOrder(duplicate));
        assertThrows(IllegalArgumentException.class,
                () -> engine.submitOrder(filled));
        assertSame(resting, book.getOrder(1));
        assertEquals(10, resting.getRemainingVolume());
        assertFalse(book.containsOrder(2));
    }

    @ParameterizedTest
    @MethodSource("implementations")
    void testNonCrossingOrdersRestAndCanBeCancelled(BookImplementation implementation,
                                                   MatchingImplementation policy, OrderSide side) {
        OrderBook book = implementation.book();
        MatchingEngine engine = new MatchingEngine(book, policy.policy());
        Order resting = order(1, side, side == OrderSide.BUY ? 99 : 101, 10);
        assertEquals(List.of(), engine.submitOrder(resting));
        Order incoming = order(2, opposite(side), 100, 20);
        assertEquals(List.of(), engine.submitOrder(incoming));
        assertSame(resting, book.getOrder(1));
        assertSame(incoming, book.getOrder(2));
        engine.cancelOrder(1);
        assertFalse(book.containsOrder(1));
        engine.cancelOrder(2);
        assertTrue(book.isEmpty());
        assertThrows(IllegalArgumentException.class, () -> engine.cancelOrder(2));
    }

    @ParameterizedTest
    @MethodSource("implementations")
    void testEqualPricePartiallyFillsRestingOrderThenRemovesLevel(BookImplementation implementation,
                                                               MatchingImplementation policy, OrderSide side) {
        OrderBook book = implementation.book();
        MatchingEngine engine = new MatchingEngine(book, policy.policy());
        Order resting = order(1, opposite(side), 100, 10);
        book.addOrder(resting);
        Order incoming = order(2, side, 100, 4);
        assertEquals(List.of(trade(incoming, 1, 100, 4)), engine.submitOrder(incoming));
        assertTrue(incoming.isFilled());
        assertFalse(book.containsOrder(2));
        assertEquals(6, resting.getRemainingVolume());
        assertEquals(6, (side == OrderSide.BUY ? book.bestAsk() : book.bestBid()).getTotalVolume());
        Order finalOrder = order(3, side, 100, 6);
        assertEquals(List.of(trade(finalOrder, 1, 100, 6)), engine.submitOrder(finalOrder));
        assertTrue(resting.isFilled());
        assertTrue(book.isEmpty());
    }

    @ParameterizedTest
    @MethodSource("implementations")
    void testSweepsBestPricesAndRestsLimitRemainderBeforeNonCrossingLevel(BookImplementation implementation,
                                                                       MatchingImplementation policy, OrderSide side) {
        OrderBook book = implementation.book();
        MatchingEngine engine = new MatchingEngine(book, policy.policy());
        long best = side == OrderSide.BUY ? 99 : 101;
        long outside = side == OrderSide.BUY ? 101 : 99;
        book.addOrder(order(3, opposite(side), outside, 30));
        book.addOrder(order(2, opposite(side), 100, 20));
        book.addOrder(order(1, opposite(side), best, 10));
        Order incoming = new Order(4, side, 100, 50, 40, 4);
        assertEquals(List.of(trade(incoming, 1, best, 10), trade(incoming, 2, 100, 20)),
                engine.submitOrder(incoming));
        assertEquals(10, incoming.getRemainingVolume());
        assertSame(incoming, book.getOrder(4));
        assertEquals(50, incoming.getOriginalVolume());
        assertFalse(book.containsOrder(1));
        assertFalse(book.containsOrder(2));
        assertEquals(outside, (side == OrderSide.BUY ? book.bestAsk() : book.bestBid()).getPrice());
        assertEquals(10, (side == OrderSide.BUY ? book.bestBid() : book.bestAsk()).getTotalVolume());
    }

    @ParameterizedTest
    @MethodSource("implementations")
    void testLimitRemainderRestsWhenLiquidityRunsOut(BookImplementation implementation,
                                                   MatchingImplementation policy, OrderSide side) {
        OrderBook book = implementation.book();
        MatchingEngine engine = new MatchingEngine(book, policy.policy());
        book.addOrder(order(1, opposite(side), 100, 3));
        Order incoming = order(2, side, 100, 5);
        assertEquals(List.of(trade(incoming, 1, 100, 3)), engine.submitOrder(incoming));
        assertSame(incoming, book.getOrder(2));
        assertEquals(2, incoming.getRemainingVolume());
        assertNull(side == OrderSide.BUY ? book.bestAsk() : book.bestBid());
    }

    @ParameterizedTest
    @MethodSource("implementations")
    void testMarketOrderSweepsPricesAndNeverRestsWhenFilled(BookImplementation implementation,
                                                          MatchingImplementation policy, OrderSide side) {
        OrderBook book = implementation.book();
        MatchingEngine engine = new MatchingEngine(book, policy.policy());
        long best = side == OrderSide.BUY ? 100 : 200;
        long next = side == OrderSide.BUY ? 200 : 100;
        book.addOrder(order(2, opposite(side), next, 20));
        book.addOrder(order(1, opposite(side), best, 10));
        Order incoming = order(3, side, 0, 30);
        assertEquals(List.of(trade(incoming, 1, best, 10), trade(incoming, 2, next, 20)),
                engine.submitOrder(incoming));
        assertTrue(incoming.isFilled());
        assertTrue(book.isEmpty());
    }

    @ParameterizedTest
    @MethodSource("implementations")
    void testMarketOrderStopsWhenFilledAndLeavesSameSideOrdersUntouched(BookImplementation implementation,
                                                                      MatchingImplementation policy, OrderSide side) {
        OrderBook book = implementation.book();
        MatchingEngine engine = new MatchingEngine(book, policy.policy());
        Order sameSide = order(1, side, 100, 7);
        book.addOrder(sameSide);
        Order opposing = order(2, opposite(side), 100, 10);
        book.addOrder(opposing);
        long worsePrice = side == OrderSide.BUY ? 101 : 99;
        Order worse = order(3, opposite(side), worsePrice, 20);
        book.addOrder(worse);
        Order incoming = order(4, side, 0, 4);
        assertEquals(List.of(trade(incoming, 2, 100, 4)), engine.submitOrder(incoming));
        assertTrue(incoming.isFilled());
        assertFalse(book.containsOrder(4));
        assertEquals(6, opposing.getRemainingVolume());
        assertEquals(20, worse.getRemainingVolume());
        assertEquals(7, sameSide.getRemainingVolume());
        assertSame(opposing, (side == OrderSide.BUY ? book.bestAsk() : book.bestBid()).peekOldest());
    }

    @ParameterizedTest
    @MethodSource("implementations")
    void testMarketOrderWithNoLiquidityReturnsNoTradesAndDoesNotRest(BookImplementation implementation,
                                                                   MatchingImplementation policy, OrderSide side) {
        OrderBook book = implementation.book();
        MatchingEngine engine = new MatchingEngine(book, policy.policy());
        Order incoming = order(2, side, 0, 5);
        assertEquals(List.of(), engine.submitOrder(incoming));
        assertEquals(5, incoming.getRemainingVolume());
        assertFalse(incoming.isFilled());
        assertFalse(book.containsOrder(2));
        assertTrue(book.isEmpty());
    }

    @ParameterizedTest
    @MethodSource("implementations")
    void testMarketOrderWithInsufficientLiquidityReturnsTradesAndDoesNotRestRemainder(BookImplementation implementation,
                                                                                   MatchingImplementation policy, OrderSide side) {
        OrderBook book = implementation.book();
        MatchingEngine engine = new MatchingEngine(book, policy.policy());
        Order incoming = order(2, side, 0, 5);
        Order resting = order(1, opposite(side), 100, 3);
        book.addOrder(resting);
        assertEquals(List.of(trade(incoming, 1, 100, 3)), engine.submitOrder(incoming));
        assertEquals(2, incoming.getRemainingVolume());
        assertFalse(incoming.isFilled());
        assertTrue(resting.isFilled());
        assertFalse(book.containsOrder(1));
        assertFalse(book.containsOrder(2));
        assertTrue(book.isEmpty());
    }

    @ParameterizedTest
    @MethodSource("implementations")
    void testPolicyMustMakeProgress(BookImplementation implementation,
                                    MatchingImplementation policy, OrderSide side) {
        OrderBook book = implementation.book();
        book.addOrder(order(1, opposite(side), 100, 10));
        MatchingEngine engine = new MatchingEngine(book, (incoming, level, currentBook, trades) -> {});
        Order incoming = order(2, side, 100, 5);
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> engine.submitOrder(incoming));
        assertEquals("Matching policy made no progress", failure.getMessage());
        assertEquals(5, incoming.getRemainingVolume());
        assertFalse(book.containsOrder(2));
        assertEquals(10, Objects.requireNonNull(book.getOrder(1)).getRemainingVolume());
    }
}
