package org.example.functional;

import org.example.book.OrderBook;
import org.example.book.OrderNode;
import org.example.book.PriceLevel;
import org.example.order.Order;
import org.example.order.OrderSide;
import org.example.support.BookImplementation;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

// Functional tests use the public API, without access to package-private book mutations.
class OrderBookIT {
    private Order order(long id, OrderSide side, long price, long volume) {
        return new Order(id, side, price, volume, volume, id);
    }

    @ParameterizedTest
    @EnumSource(BookImplementation.class)
    void testMixedBuySellLifecyclePreservesFifoAndAdvancesBestPrices(BookImplementation implementation) {
        OrderBook book = implementation.book();
        OrderNode firstBuy = book.addOrder(order(1, OrderSide.BUY, 100, 50));
        OrderNode secondBuy = book.addOrder(order(2, OrderSide.BUY, 100, 30));
        book.addOrder(order(3, OrderSide.BUY, 105, 20));
        OrderNode firstSell = book.addOrder(order(4, OrderSide.SELL, 110, 40));
        book.addOrder(order(5, OrderSide.SELL, 115, 60));
        assertEquals(105, book.bestBid().getPrice());
        assertEquals(110, book.bestAsk().getPrice());
        assertSame(firstBuy.getOrder(), firstBuy.getPriceLevel().peekOldest());
        book.fillOrder(firstBuy, 20);
        assertEquals(60, firstBuy.getPriceLevel().getTotalVolume());
        book.fillOrder(firstBuy, 30);
        assertFalse(book.containsOrder(1));
        assertSame(secondBuy.getOrder(), secondBuy.getPriceLevel().peekOldest());
        assertEquals(30, secondBuy.getPriceLevel().getTotalVolume());
        book.cancelOrder(2);
        book.cancelOrder(3);
        assertNull(book.bestBid());
        assertFalse(book.isEmpty());
        book.fillOrder(firstSell, 40);
        assertEquals(115, book.bestAsk().getPrice());
        book.cancelOrder(5);
        assertTrue(book.isEmpty());
        for (long id = 1; id <= 5; id++) {
            assertNull(book.getOrder(id));
            assertFalse(book.containsOrder(id));
        }
    }

    @ParameterizedTest
    @EnumSource(BookImplementation.class)
    void testRemovedPriceAndOrderIdCanBeReusedWithoutRevivingStaleNodes(BookImplementation implementation) {
        OrderBook book = implementation.book();
        OrderNode stale = book.addOrder(order(1, OrderSide.SELL, 100, 10));
        PriceLevel oldLevel = stale.getPriceLevel();
        book.fillOrder(stale, 3);
        book.cancelOrder(1);
        assertEquals(0, oldLevel.getTotalVolume());
        OrderNode replacement = book.addOrder(order(1, OrderSide.SELL, 100, 20));
        assertNotSame(oldLevel, replacement.getPriceLevel());
        assertThrows(IllegalArgumentException.class, () -> book.fillOrder(stale, 1));
        assertSame(replacement.getOrder(), book.getOrder(1));
        book.fillOrder(replacement, 20);
        assertTrue(book.isEmpty());
    }

    @ParameterizedTest
    @EnumSource(BookImplementation.class)
    void testSeededOrderLifecyclesMatchIndependentReferenceAfterEveryOperation(BookImplementation implementation) {
        for (long seed : new long[]{42, 271828, 8675309}) {
            OrderBook book = implementation.book();
            Map<Long, ExpectedOrder> expected = new LinkedHashMap<>();
            Map<Long, OrderNode> nodes = new LinkedHashMap<>();
            Random random = new Random(seed);
            long nextId = 1;
            for (int step = 0; step < 500; step++) {
                String context = implementation + ", seed=" + seed + ", step=" + step;
                int action = random.nextInt(3);
                if (expected.isEmpty() || action == 0) {
                    long id = nextId++;
                    OrderSide side = random.nextBoolean() ? OrderSide.BUY : OrderSide.SELL;
                    long price = (side == OrderSide.BUY ? 95 : 105) + random.nextInt(5);
                    long original = 1 + random.nextInt(100);
                    long remaining = 1 + random.nextInt((int) original);
                    Order order = new Order(id, side, price, original, remaining, id);
                    nodes.put(id, book.addOrder(order));
                    expected.put(id, new ExpectedOrder(id, side, price, remaining));
                } else {
                    List<Long> ids = new ArrayList<>(expected.keySet());
                    long id = ids.get(random.nextInt(ids.size()));
                    ExpectedOrder previous = expected.get(id);
                    OrderNode node = nodes.get(id);
                    if (action == 1) {
                        book.cancelOrder(id);
                        expected.remove(id);
                        nodes.remove(id);
                        assertDetached(node, context);
                    } else {
                        long volume = 1 + random.nextInt((int) previous.remaining());
                        book.fillOrder(node, volume);
                        if (volume == previous.remaining()) {
                            expected.remove(id);
                            nodes.remove(id);
                            assertDetached(node, context);
                        } else {
                            expected.put(id, new ExpectedOrder(id, previous.side(), previous.price(), previous.remaining() - volume));
                        }
                    }
                }
                assertBookMatchesReference(book, expected, nodes, nextId, context);
            }
            for (long id : new ArrayList<>(expected.keySet())) book.cancelOrder(id);
            assertTrue(book.isEmpty(), "seed=" + seed);
        }
    }

    private void assertBookMatchesReference(OrderBook book, Map<Long, ExpectedOrder> expected,
                                           Map<Long, OrderNode> nodes, long nextId, String context) {
        assertEquals(expected.isEmpty(), book.isEmpty(), context);
        for (long id = 1; id < nextId; id++) {
            ExpectedOrder model = expected.get(id);
            assertEquals(model != null, book.containsOrder(id), context + ", id=" + id);
            if (model == null) {
                assertNull(book.getOrder(id), context);
            } else {
                Order actual = book.getOrder(id);
                assertNotNull(actual, context);
                assertEquals(model.side(), actual.getSide(), context);
                assertEquals(model.price(), actual.getPrice(), context);
                assertEquals(model.remaining(), actual.getRemainingVolume(), context);
            }
        }
        for (OrderSide side : OrderSide.values()) {
            List<ExpectedOrder> sideOrders = expected.values().stream().filter(order -> order.side() == side).toList();
            PriceLevel best = side == OrderSide.BUY ? book.bestBid() : book.bestAsk();
            if (sideOrders.isEmpty()) {
                assertNull(best, context);
                continue;
            }
            Comparator<Long> priceOrder = side == OrderSide.BUY ? Comparator.reverseOrder() : Comparator.naturalOrder();
            long bestPrice = sideOrders.stream().map(ExpectedOrder::price).min(priceOrder).orElseThrow();
            assertNotNull(best, context);
            assertEquals(bestPrice, best.getPrice(), context);
            for (long price : sideOrders.stream().map(ExpectedOrder::price).distinct().toList()) {
                List<ExpectedOrder> fifo = sideOrders.stream().filter(order -> order.price() == price).toList();
                OrderNode oldestNode = nodes.get(fifo.getFirst().id());
                assertNotNull(oldestNode, context);
                PriceLevel level = oldestNode.getPriceLevel();
                assertEquals(fifo.size(), level.getSize(), context);
                assertEquals(fifo.stream().mapToLong(ExpectedOrder::remaining).sum(), level.getTotalVolume(), context);
                Order oldest = level.peekOldest();
                Order newest = level.peekNewest();
                assertNotNull(oldest, context);
                assertNotNull(newest, context);
                assertEquals(fifo.getFirst().id(), oldest.getId(), context);
                assertEquals(fifo.getLast().id(), newest.getId(), context);
                OrderNode current = oldestNode;
                OrderNode older = null;
                for (ExpectedOrder order : fifo) {
                    assertNotNull(current, context);
                    assertEquals(order.id(), current.getOrder().getId(), context);
                    assertSame(level, current.getPriceLevel(), context);
                    assertSame(older, current.getNext(), context);
                    assertFalse(current.isUnlinked(), context);
                    older = current;
                    current = current.getPrev();
                }
                assertNull(current, context);
            }
        }
    }

    private void assertDetached(OrderNode node, String context) {
        assertTrue(node.isUnlinked(), context);
        assertNull(node.getPrev(), context);
        assertNull(node.getNext(), context);
    }

    private record ExpectedOrder(long id, OrderSide side, long price, long remaining) {}
}
