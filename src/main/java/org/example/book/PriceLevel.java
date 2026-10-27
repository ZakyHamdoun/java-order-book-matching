package org.example.book;

import org.example.order.Order;

import java.util.Objects;

public final class PriceLevel {
    private final long price;
    private int size;
    private long totalVolume;
    private OrderNode head;
    private OrderNode tail;

    public PriceLevel(long price) {
        if (price <= 0)
            throw new IllegalArgumentException("price must be > 0!");

        this.price = price;
        this.size = 0;
        this.totalVolume = 0;
        this.head = null;
        this.tail = null;
    }

    OrderNode addOrder(Order order) {
        Objects.requireNonNull(order);

        if (order.getPrice() != price)
            throw new IllegalArgumentException("order price must match PriceLevel price");
        if (order.getRemainingVolume() <= 0)
            throw new IllegalArgumentException("order must have positive remaining volume");

        long newTotalVolume = Math.addExact(totalVolume, order.getRemainingVolume());

        OrderNode toAdd = new OrderNode(order, this, null, head, true);

        if (head != null) head.setPrev(toAdd); else tail = toAdd;

        head = toAdd;
        totalVolume = newTotalVolume;
        size++;

        return toAdd;
    }

    OrderNode popOrder() {
        if (size == 0)
            throw new IndexOutOfBoundsException("size must be > 0!");

        OrderNode toRemove = tail;
        totalVolume -= toRemove.getOrder().getRemainingVolume();
        tail = toRemove.getPrev();

        if (tail == null) head = null; else tail.setNext(null);

        size--;

        toRemove.setNext(null);
        toRemove.setPrev(null);
        toRemove.unlink();

        return toRemove;
    }

    void removeOrder(OrderNode toRemove) {
        Objects.requireNonNull(toRemove);

        if (toRemove.getPriceLevel() != this)
            throw new IllegalArgumentException("toRemove does not belong to this PriceLevel");
        if (toRemove.isUnlinked())
            throw new IllegalStateException("toRemove has already been removed!");

        // O(1): the node already provides both neighbors.
        OrderNode prev = toRemove.getPrev();
        OrderNode next = toRemove.getNext();

        // Update previous node or head.
        if (prev != null) prev.setNext(next); else head = next;
        // Update next node or tail.
        if (next != null) next.setPrev(prev); else tail = prev;

        totalVolume -= toRemove.getOrder().getRemainingVolume();
        size--;

        // Detach the removed node.
        toRemove.setPrev(null);
        toRemove.setNext(null);
        toRemove.unlink();
    }

    void fillOrder(OrderNode toFillNode, long volume) {
        Objects.requireNonNull(toFillNode);

        if (toFillNode.getPriceLevel() != this || toFillNode.isUnlinked())
            throw new IllegalArgumentException("Node is not active in this level");

        Order toFill = toFillNode.getOrder();

        if (volume <= 0 || volume > toFill.getRemainingVolume())
            throw new IllegalArgumentException("Invalid fill volume");

        toFill.fill(volume);
        totalVolume -= volume;

        if (toFill.isFilled())
            removeOrder(toFillNode);
    }

    public Order peekNewest() { return (head != null) ? head.getOrder() : null; }
    public Order peekOldest() { return (tail != null) ? tail.getOrder() : null; }
    public OrderNode peekOldestNode() { return tail != null ? tail : null; }
    public boolean isEmpty() { return size == 0; }

    // Getters.
    public long getPrice() { return this.price; }
    public int getSize() { return this.size; }
    public long getTotalVolume() { return this.totalVolume; }

    // toString() method (display price level).
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append("PriceLevel[")
                .append("price=").append(price)
                .append(", size=").append(size)
                .append(", totalVolume=").append(totalVolume)
                .append(", orders=[");

        OrderNode current = tail;

        while (current != null) {
            sb.append(current.getOrder());

            current = current.getPrev();

            if (current != null)
                sb.append(", ");
        }

        sb.append("]]");

        return sb.toString();
    }
}
