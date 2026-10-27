package org.example.book;

import org.example.order.Order;
import org.example.order.OrderSide;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OrderNodeTest {
    private final PriceLevel level = new PriceLevel(100);
    private final Order order = new Order(1, OrderSide.BUY, 100, 10, 10, 1);

    @Test
    void testConstructorPreservesOrderLevelLinksAndLinkedState() {
        OrderNode neighbor = new OrderNode(order, level, null, null, false);
        OrderNode node = new OrderNode(order, level, neighbor, neighbor, true);
        assertSame(order, node.getOrder());
        assertSame(level, node.getPriceLevel());
        assertSame(neighbor, node.getPrev());
        assertSame(neighbor, node.getNext());
        assertFalse(node.isUnlinked());
        assertTrue(neighbor.isUnlinked());
    }

    @Test
    void testNullOrderOrLevelIsRejected() {
        assertThrows(NullPointerException.class, () -> new OrderNode(null, level, null, null, true));
        assertThrows(NullPointerException.class, () -> new OrderNode(order, null, null, null, true));
    }

    @Test
    void testLinksCanBeUpdatedAndClearedWithinBookPackage() {
        OrderNode node = new OrderNode(order, level, null, null, true);
        OrderNode neighbor = new OrderNode(order, level, null, null, true);
        node.setPrev(neighbor);
        node.setNext(neighbor);
        assertSame(neighbor, node.getPrev());
        assertSame(neighbor, node.getNext());
        node.setPrev(null);
        node.setNext(null);
        node.unlink();
        assertNull(node.getPrev());
        assertNull(node.getNext());
        assertTrue(node.isUnlinked());
        node.unlink();
        assertTrue(node.isUnlinked());
    }

    @Test
    void testToStringShowsNeighborIdsWithoutRecursingThroughLinks() {
        OrderNode oldest = level.addOrder(order);
        OrderNode middle = level.addOrder(new Order(2, OrderSide.BUY, 100, 20, 20, 2));
        OrderNode newest = level.addOrder(new Order(3, OrderSide.BUY, 100, 30, 30, 3));
        assertEquals("OrderNode[order=" + middle.getOrder()
                + ", priceLevel=100, linked=true, prevOrderId=3, nextOrderId=1]", middle.toString());
        assertTrue(oldest.toString().contains("nextOrderId=null"));
        assertTrue(newest.toString().contains("prevOrderId=null"));
        level.removeOrder(middle);
        assertEquals("OrderNode[order=" + middle.getOrder()
                + ", priceLevel=100, linked=false, prevOrderId=null, nextOrderId=null]", middle.toString());
    }
}
