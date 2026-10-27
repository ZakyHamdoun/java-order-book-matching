package org.example.book;

import org.example.order.Order;
import org.example.order.OrderSide;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PriceLevelTest {
    private final PriceLevel level = new PriceLevel(100);

    private Order order(long id, long volume) {
        return new Order(id, OrderSide.BUY, 100, volume, volume, id);
    }

    @Test
    void testNewLevelIsEmpty() {
        assertEquals(100, level.getPrice());
        assertState(0, 0);
        assertNull(level.peekOldest());
        assertNull(level.peekNewest());
    }

    @ParameterizedTest
    @ValueSource(longs = {Long.MIN_VALUE, -1, 0})
    void testNonPositivePriceIsRejected(long price) {
        assertThrows(IllegalArgumentException.class, () -> new PriceLevel(price));
    }

    @Test
    void testOrdersAreLinkedNewestToOldestAndPoppedInFifoOrder() {
        OrderNode first = level.addOrder(order(1, 10));
        OrderNode second = level.addOrder(order(2, 20));
        OrderNode third = level.addOrder(order(3, 30));
        assertState(3, 60);
        assertSame(first.getOrder(), level.peekOldest());
        assertSame(third.getOrder(), level.peekNewest());
        assertNull(third.getPrev());
        assertSame(second, third.getNext());
        assertSame(third, second.getPrev());
        assertSame(first, second.getNext());
        assertSame(second, first.getPrev());
        assertNull(first.getNext());
        for (OrderNode expected : List.of(first, second, third)) {
            assertSame(expected, level.popOrder());
            assertDetached(expected);
        }
        assertState(0, 0);
        assertNull(level.peekNewest());
        assertNull(level.peekOldest());
        assertThrows(IndexOutOfBoundsException.class, level::popOrder);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void testRemovingOldestMiddleOrNewestPreservesRemainingFifoOrder(int removed) {
        List<OrderNode> nodes = new ArrayList<>(List.of(
                level.addOrder(order(1, 10)), level.addOrder(order(2, 20)), level.addOrder(order(3, 30))));
        OrderNode removedNode = nodes.remove(removed);
        level.removeOrder(removedNode);
        assertDetached(removedNode);
        assertState(2, 60 - removedNode.getOrder().getRemainingVolume());
        assertSame(nodes.getFirst().getOrder(), level.peekOldest());
        assertSame(nodes.getLast().getOrder(), level.peekNewest());
        for (OrderNode node : nodes) assertSame(node, level.popOrder());
        assertState(0, 0);
    }

    @Test
    void testRemovingOnlyOrderAllowsLevelToBeReused() {
        OrderNode node = level.addOrder(order(1, 10));
        level.removeOrder(node);
        assertState(0, 0);
        assertNull(level.peekOldest());
        assertNull(level.peekNewest());
        OrderNode replacement = level.addOrder(order(2, 5));
        assertSame(replacement.getOrder(), level.peekOldest());
        assertSame(replacement, level.popOrder());
        assertState(0, 0);
    }

    @Test
    void testPartiallyFilledOrderContributesOnlyRemainingVolume() {
        level.addOrder(new Order(1, OrderSide.BUY, 100, 20, 7, 1));
        assertState(1, 7);
        level.popOrder();
        assertState(0, 0);
    }

    @Test
    void testNullWrongPriceAndFilledOrdersAreRejectedWithoutChanges() {
        assertThrows(NullPointerException.class, () -> level.addOrder(null));
        Order wrongPrice = new Order(1, OrderSide.BUY, 101, 10, 10, 1);
        Order filled = new Order(1, OrderSide.BUY, 100, 10, 0, 1);
        assertThrows(IllegalArgumentException.class, () -> level.addOrder(wrongPrice));
        assertThrows(IllegalArgumentException.class, () -> level.addOrder(filled));
        assertState(0, 0);
    }

    @Test
    void testAggregateVolumeOverflowIsRejectedBeforeLinkingOrder() {
        OrderNode first = level.addOrder(order(1, Long.MAX_VALUE));
        Order overflowing = order(2, 1);
        assertThrows(ArithmeticException.class, () -> level.addOrder(overflowing));
        assertState(1, Long.MAX_VALUE);
        assertSame(first.getOrder(), level.peekOldest());
        assertSame(first.getOrder(), level.peekNewest());
        assertNull(first.getPrev());
        assertNull(first.getNext());
    }

    @Test
    void testRemovingNullForeignOrDetachedNodeIsRejectedWithoutChanges() {
        OrderNode node = level.addOrder(order(1, 10));
        PriceLevel other = new PriceLevel(100);
        OrderNode foreign = other.addOrder(order(2, 20));
        assertThrows(NullPointerException.class, () -> level.removeOrder(null));
        assertThrows(IllegalArgumentException.class, () -> level.removeOrder(foreign));
        assertState(1, 10);
        assertEquals(20, other.getTotalVolume());
        level.removeOrder(node);
        assertThrows(IllegalStateException.class, () -> level.removeOrder(node));
        assertState(0, 0);
    }

    @Test
    void testPartialFillKeepsFifoPositionAndFullFillDoesNotSubtractTwice() {
        OrderNode first = level.addOrder(order(1, 10));
        OrderNode second = level.addOrder(order(2, 20));
        level.fillOrder(first, 4);
        assertState(2, 26);
        assertEquals(6, first.getOrder().getRemainingVolume());
        assertSame(first.getOrder(), level.peekOldest());
        assertFalse(first.isUnlinked());
        level.fillOrder(first, 6);
        assertState(1, 20);
        assertDetached(first);
        assertSame(second.getOrder(), level.peekOldest());
        level.fillOrder(second, 20);
        assertState(0, 0);
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 0, 11, Long.MAX_VALUE})
    void testInvalidFillLeavesQuantityLinksAndVolumeUnchanged(long volume) {
        OrderNode node = level.addOrder(order(1, 10));
        assertThrows(IllegalArgumentException.class, () -> level.fillOrder(node, volume));
        assertState(1, 10);
        assertEquals(10, node.getOrder().getRemainingVolume());
        assertFalse(node.isUnlinked());
    }

    @Test
    void testFillingNullForeignOrDetachedNodeIsRejected() {
        OrderNode node = level.addOrder(order(1, 10));
        PriceLevel other = new PriceLevel(100);
        OrderNode foreign = other.addOrder(order(2, 20));
        assertThrows(NullPointerException.class, () -> level.fillOrder(null, 1));
        assertThrows(IllegalArgumentException.class, () -> level.fillOrder(foreign, 1));
        level.popOrder();
        assertThrows(IllegalArgumentException.class, () -> level.fillOrder(node, 1));
        assertState(0, 0);
        assertEquals(20, other.getTotalVolume());
    }

    @Test
    void testToStringShowsOrdersOldestFirstWithoutInternalNodeWrappers() {
        assertEquals("PriceLevel[price=100, size=0, totalVolume=0, orders=[]]", level.toString());
        Order first = order(1, 10);
        Order second = order(2, 20);
        level.addOrder(first);
        level.addOrder(second);
        assertEquals("PriceLevel[price=100, size=2, totalVolume=30, orders=["
                + first + ", " + second + "]]", level.toString());
    }

    private void assertState(int size, long volume) {
        assertAll(() -> assertEquals(size, level.getSize()),
                () -> assertEquals(volume, level.getTotalVolume()),
                () -> assertEquals(size == 0, level.isEmpty()));
    }

    private void assertDetached(OrderNode node) {
        assertTrue(node.isUnlinked());
        assertNull(node.getPrev());
        assertNull(node.getNext());
    }
}
