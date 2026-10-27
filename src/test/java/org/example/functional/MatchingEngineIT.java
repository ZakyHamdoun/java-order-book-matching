package org.example.functional;

import org.example.book.OrderBook;
import org.example.matching.MatchingEngine;
import org.example.order.Order;
import org.example.order.OrderSide;
import org.example.support.BookImplementation;
import org.example.support.MatchingImplementation;
import org.example.trade.Trade;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class MatchingEngineIT {
    private static Stream<Arguments> implementations() {
        return Stream.of(BookImplementation.values()).flatMap(book ->
                Stream.of(MatchingImplementation.values()).map(policy -> Arguments.of(book, policy)));
    }

    private Order order(long id, OrderSide side, long price, long volume) {
        return new Order(id, side, price, volume, volume, id);
    }

    @ParameterizedTest
    @MethodSource("implementations")
    void testMixedLifecycleMatchesTradesAndConservesVolumeAfterCancellations(BookImplementation implementation,
                                                                         MatchingImplementation matching) {
        OrderBook book = implementation.book();
        MatchingEngine engine = new MatchingEngine(book, matching.policy());
        Order first = order(1, OrderSide.SELL, 100, 10);
        Order second = order(2, OrderSide.SELL, 100, 30);
        assertTrue(engine.submitOrder(first).isEmpty());
        assertTrue(engine.submitOrder(second).isEmpty());
        assertTrue(engine.submitOrder(order(3, OrderSide.SELL, 105, 20)).isEmpty());
        assertTrue(engine.submitOrder(order(4, OrderSide.BUY, 95, 10)).isEmpty());

        Order buy = order(5, OrderSide.BUY, 100, 8);
        List<Trade> initialTrades = engine.submitOrder(buy);
        assertEquals(matching == MatchingImplementation.FIFO
                        ? List.of(new Trade(5, 1, 100, 8))
                        : List.of(new Trade(5, 1, 100, 2), new Trade(5, 2, 100, 6)), initialTrades);
        assertTrue(buy.isFilled());
        assertEquals(32, book.bestAsk().getTotalVolume());

        long cancelledVolume = second.getRemainingVolume();
        engine.cancelOrder(2);
        assertFalse(book.containsOrder(2));
        assertEquals(first.getRemainingVolume(), book.bestAsk().getTotalVolume());
        long firstRemaining = first.getRemainingVolume();
        Order sweep = order(6, OrderSide.BUY, 105, firstRemaining + 5);
        assertEquals(List.of(new Trade(6, 1, 100, firstRemaining), new Trade(6, 3, 105, 5)),
                engine.submitOrder(sweep));
        assertTrue(sweep.isFilled());
        assertFalse(book.containsOrder(1));
        assertEquals(105, book.bestAsk().getPrice());
        assertEquals(15, book.bestAsk().getTotalVolume());

        Order marketSell = order(7, OrderSide.SELL, 0, 4);
        assertEquals(List.of(new Trade(4, 7, 95, 4)), engine.submitOrder(marketSell));
        assertEquals(6, book.bestBid().getTotalVolume());
        Order marketBuy = order(8, OrderSide.BUY, 0, 15);
        assertEquals(List.of(new Trade(8, 3, 105, 15)), engine.submitOrder(marketBuy));
        assertNull(book.bestAsk());

        // Reuse a cancelled id and rest only the unexecuted limit volume.
        Order replacement = order(2, OrderSide.SELL, 95, 10);
        assertEquals(List.of(new Trade(4, 2, 95, 6)), engine.submitOrder(replacement));
        assertNull(book.bestBid());
        assertSame(replacement, book.getOrder(2));
        assertEquals(4, replacement.getRemainingVolume());
        assertEquals(40, 8 + cancelledVolume + firstRemaining);
        engine.cancelOrder(2);
        assertTrue(book.isEmpty());
        for (long id = 1; id <= 8; id++) {
            assertFalse(book.containsOrder(id));
            assertNull(book.getOrder(id));
        }
    }
}
