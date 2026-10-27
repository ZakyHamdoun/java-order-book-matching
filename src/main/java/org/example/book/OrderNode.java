package org.example.book;

import org.example.order.Order;

import java.util.Objects;

public final class OrderNode {
    private final Order order;
    private final PriceLevel priceLevel;
    private OrderNode prev;
    private OrderNode next;
    private boolean linked;

    OrderNode(Order order, PriceLevel priceLevel, OrderNode prev, OrderNode next, boolean linked) {
        this.order = Objects.requireNonNull(order);
        this.priceLevel = Objects.requireNonNull(priceLevel);
        this.prev = prev;
        this.next = next;
        this.linked = linked;
    }

    public boolean isUnlinked() { return !linked; }
    void unlink() { linked = false; }

    // Getters.
    public Order getOrder() { return this.order; }
    public PriceLevel getPriceLevel() { return this.priceLevel; }
    public OrderNode getPrev() { return this.prev; }
    public OrderNode getNext() { return this.next; }

    // Setters.
    void setPrev(OrderNode prev) { this.prev = prev; }
    void setNext(OrderNode next) { this.next = next; }

    @Override
    public String toString() {
        return "OrderNode[" +
                "order=" + order +
                ", priceLevel=" + priceLevel.getPrice() +
                ", linked=" + linked +
                ", prevOrderId=" + (prev == null ? null : prev.getOrder().getId()) +
                ", nextOrderId=" + (next == null ? null : next.getOrder().getId()) +
                "]";
    }
}
