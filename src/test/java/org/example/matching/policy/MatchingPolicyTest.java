package org.example.matching.policy;

import org.example.book.OrderBook;
import org.example.book.OrderNode;
import org.example.book.PriceLevel;
import org.example.order.Order;
import org.example.order.OrderSide;
import org.example.support.BookImplementation;
import org.example.support.MatchingImplementation;
import org.example.trade.Trade;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class MatchingPolicyTest {
    private static Stream<Arguments> implementations() {
        return Stream.of(BookImplementation.values()).flatMap(book ->
                Stream.of(MatchingImplementation.values()).flatMap(policy ->
                        Stream.of(OrderSide.values()).map(side -> Arguments.of(book, policy, side))));
    }

    private Order order(long id, OrderSide side, long volume) {
        return new Order(id, side, 100, volume, volume, 100 - id);
    }

    private OrderSide opposite(OrderSide side) {
        return side == OrderSide.BUY ? OrderSide.SELL : OrderSide.BUY;
    }

    private Trade trade(Order incoming, long restingId, long volume) {
        return incoming.getSide() == OrderSide.BUY
                ? new Trade(incoming.getId(), restingId, 100, volume)
                : new Trade(restingId, incoming.getId(), 100, volume);
    }

    @ParameterizedTest
    @MethodSource("implementations")
    void testNullArgumentsAreRejectedWithoutMutatingOrders(BookImplementation implementation,
                                                          MatchingImplementation matching, OrderSide side) {
        OrderBook book = implementation.book();
        MatchingPolicy policy = matching.policy();
        Order incoming = order(2, side, 10);
        PriceLevel level = book.addOrder(order(1, opposite(side), 10)).getPriceLevel();
        List<Trade> trades = new ArrayList<>();
        assertThrows(NullPointerException.class, () -> policy.match(null, level, book, trades));
        assertThrows(NullPointerException.class, () -> policy.match(incoming, null, book, trades));
        assertThrows(NullPointerException.class, () -> policy.match(incoming, level, null, trades));
        assertThrows(NullPointerException.class, () -> policy.match(incoming, level, book, null));
        assertEquals(10, incoming.getRemainingVolume());
        assertEquals(10, level.getTotalVolume());
        assertTrue(trades.isEmpty());
    }

    @ParameterizedTest
    @MethodSource("implementations")
    void testEmptyLevelAndFilledIncomingDoNotCreateTrades(BookImplementation implementation,
                                                        MatchingImplementation matching, OrderSide side) {
        OrderBook book = implementation.book();
        MatchingPolicy policy = matching.policy();
        List<Trade> trades = new ArrayList<>();
        Order incoming = order(2, side, 10);
        policy.match(incoming, new PriceLevel(100), book, trades);
        assertEquals(10, incoming.getRemainingVolume());
        PriceLevel level = book.addOrder(order(1, opposite(side), 10)).getPriceLevel();
        incoming.fill(10);
        policy.match(incoming, level, book, trades);
        assertTrue(trades.isEmpty());
        assertEquals(10, level.getTotalVolume());
        assertEquals(1, level.getSize());
    }

    @ParameterizedTest
    @MethodSource("implementations")
    void testAllocationUsesRemainingVolumesAndPreservesInsertionPriority(BookImplementation implementation,
                                                                      MatchingImplementation matching, OrderSide side) {
        OrderBook book = implementation.book();
        Order first = new Order(1, opposite(side), 100, 100, 10, 99);
        Order second = new Order(2, opposite(side), 100, 100, 30, 1);
        PriceLevel level = book.addOrder(first).getPriceLevel();
        book.addOrder(second);
        Order incoming = order(3, side, 8);
        Trade previous = new Trade(90, 91, 99, 1);
        List<Trade> trades = new ArrayList<>(List.of(previous));
        matching.policy().match(incoming, level, book, trades);
        if (matching == MatchingImplementation.FIFO) {
            assertEquals(List.of(previous, trade(incoming, 1, 8)), trades);
            assertEquals(2, first.getRemainingVolume());
            assertEquals(30, second.getRemainingVolume());
        } else {
            assertEquals(List.of(previous, trade(incoming, 1, 2), trade(incoming, 2, 6)), trades);
            assertEquals(8, first.getRemainingVolume());
            assertEquals(24, second.getRemainingVolume());
        }
        assertTrue(incoming.isFilled());
        assertEquals(32, level.getTotalVolume());
        assertEquals(2, level.getSize());
        assertSame(first, level.peekOldest());
        assertSame(second, level.peekNewest());
        assertEquals(100, first.getOriginalVolume());
        assertEquals(100, second.getOriginalVolume());
    }

    @ParameterizedTest
    @MethodSource("implementations")
    void testRoundingLeftoversGoToOldestOrdersAndZeroAllocationsAreSkipped(BookImplementation implementation,
                                                                       MatchingImplementation matching, OrderSide side) {
        OrderBook book = implementation.book();
        OrderNode first = book.addOrder(order(1, opposite(side), 1));
        OrderNode second = book.addOrder(order(2, opposite(side), 1));
        OrderNode third = book.addOrder(order(3, opposite(side), 1));
        PriceLevel level = first.getPriceLevel();
        Order incoming = order(4, side, 2);
        List<Trade> trades = new ArrayList<>();
        matching.policy().match(incoming, level, book, trades);
        assertEquals(List.of(trade(incoming, 1, 1), trade(incoming, 2, 1)), trades);
        assertTrue(incoming.isFilled());
        assertFalse(book.containsOrder(1));
        assertFalse(book.containsOrder(2));
        assertTrue(first.isUnlinked());
        assertTrue(second.isUnlinked());
        assertNull(first.getPrev());
        assertNull(first.getNext());
        assertSame(third, level.peekOldestNode());
        assertSame(third.getOrder(), level.peekNewest());
        assertEquals(1, level.getSize());
        assertEquals(1, level.getTotalVolume());
    }

    @ParameterizedTest
    @MethodSource("implementations")
    void testPolicyConsumesOnlySuppliedLevelAndLeavesIncomingRemainder(BookImplementation implementation,
                                                                    MatchingImplementation matching, OrderSide side) {
        OrderBook book = implementation.book();
        PriceLevel level = book.addOrder(order(1, opposite(side), 10)).getPriceLevel();
        book.addOrder(order(2, opposite(side), 30));
        Order untouched = new Order(3, opposite(side), 101, 5, 5, 3);
        book.addOrder(untouched);
        Order incoming = order(4, side, 50);
        List<Trade> trades = new ArrayList<>();
        matching.policy().match(incoming, level, book, trades);
        assertEquals(List.of(trade(incoming, 1, 10), trade(incoming, 2, 30)), trades);
        assertEquals(10, incoming.getRemainingVolume());
        assertEquals(0, level.getTotalVolume());
        assertEquals(0, level.getSize());
        assertNull(level.peekOldestNode());
        assertFalse(book.containsOrder(1));
        assertFalse(book.containsOrder(2));
        assertFalse(book.containsOrder(4));
        assertSame(untouched, book.getOrder(3));
        assertEquals(5, untouched.getRemainingVolume());
    }

    @ParameterizedTest
    @MethodSource("implementations")
    void testAllocationCombinesWholeSharesWithFifoRounding(BookImplementation implementation,
                                                         MatchingImplementation matching, OrderSide side) {
        OrderBook book = implementation.book();
        PriceLevel level = book.addOrder(order(1, opposite(side), 2)).getPriceLevel();
        book.addOrder(order(2, opposite(side), 3));
        book.addOrder(order(3, opposite(side), 5));
        Order incoming = order(4, side, 6);
        List<Trade> trades = new ArrayList<>();
        matching.policy().match(incoming, level, book, trades);
        if (matching == MatchingImplementation.FIFO) {
            assertEquals(List.of(trade(incoming, 1, 2), trade(incoming, 2, 3), trade(incoming, 3, 1)), trades);
            assertFalse(book.containsOrder(1));
            assertFalse(book.containsOrder(2));
            assertEquals(4, Objects.requireNonNull(book.getOrder(3)).getRemainingVolume());
            assertEquals(1, level.getSize());
        } else {
            assertEquals(List.of(trade(incoming, 1, 2), trade(incoming, 2, 1), trade(incoming, 3, 3)), trades);
            assertFalse(book.containsOrder(1));
            assertEquals(2, Objects.requireNonNull(book.getOrder(2)).getRemainingVolume());
            assertEquals(2, Objects.requireNonNull(book.getOrder(3)).getRemainingVolume());
            assertEquals(2, level.getSize());
        }
        assertTrue(incoming.isFilled());
        assertEquals(4, level.getTotalVolume());
        assertEquals(6, trades.stream().mapToLong(Trade::volume).sum());
    }

    @ParameterizedTest
    @MethodSource("implementations")
    void testSingleUnitExecutionWithMaximumRestingVolumeDoesNotOverflow(BookImplementation implementation,
                                                                      MatchingImplementation matching, OrderSide side) {
        OrderBook book = implementation.book();
        Order resting = order(1, opposite(side), Long.MAX_VALUE);
        PriceLevel level = book.addOrder(resting).getPriceLevel();
        Order incoming = order(2, side, 1);
        List<Trade> trades = new ArrayList<>();
        matching.policy().match(incoming, level, book, trades);
        assertEquals(List.of(trade(incoming, 1, 1)), trades);
        assertTrue(incoming.isFilled());
        assertEquals(Long.MAX_VALUE - 1, resting.getRemainingVolume());
        assertEquals(Long.MAX_VALUE - 1, level.getTotalVolume());
    }
}
