package org.example.book;

import org.example.book.index.PriceLevelIndex;
import org.example.order.Order;
import org.example.order.OrderSide;

import java.util.*;

public final class OrderBook {
    private final PriceLevelIndex bids;
    private final PriceLevelIndex asks;
    private final Map<Long, OrderNode> ordersById;

    public OrderBook(PriceLevelIndex bids, PriceLevelIndex asks) {
        this.bids = Objects.requireNonNull(bids);
        this.asks = Objects.requireNonNull(asks);
        this.ordersById = new HashMap<>();
    }

    // Order lifecycle.
    public OrderNode addOrder(Order toAdd) {
        Objects.requireNonNull(toAdd);

        if (ordersById.containsKey(toAdd.getId()))
            throw new IllegalArgumentException("order with this id already exists!");
        if (toAdd.getRemainingVolume() <= 0)
            throw new IllegalStateException("order has zero or negative remainingVolume!");
        if (toAdd.isMarketOrder())
            throw new IllegalStateException("market orders cannot be added to order book!");

        PriceLevelIndex levels = levelsFor(toAdd.getSide());
        PriceLevel toAddPriceLevel = levels.get(toAdd.getPrice());

        if (toAddPriceLevel == null) {
            toAddPriceLevel = new PriceLevel(toAdd.getPrice());

            OrderNode orderNode = toAddPriceLevel.addOrder(toAdd);

            levels.put(toAddPriceLevel);
            ordersById.put(toAdd.getId(), orderNode);

            return orderNode;
        }

        OrderNode orderNode = toAddPriceLevel.addOrder(toAdd);

        ordersById.put(toAdd.getId(), orderNode);

        return orderNode;
    }

    public void cancelOrder(long orderId) {
        OrderNode toCancelNode = ordersById.get(orderId);

        if (toCancelNode == null)
            throw new IllegalArgumentException("order with orderId " + orderId + " does not exist!");

        Order toCancel = toCancelNode.getOrder();
        PriceLevel toCancelPriceLevel = toCancelNode.getPriceLevel();

        toCancelPriceLevel.removeOrder(toCancelNode);
        removeLevelIfEmpty(toCancel.getSide(), toCancelPriceLevel);
        ordersById.remove(orderId);
    }

    public void fillOrder(OrderNode toFillNode, long volume) {
        Objects.requireNonNull(toFillNode);

        Order toFill = toFillNode.getOrder();

        if (volume <= 0 || volume > toFill.getRemainingVolume())
            throw new IllegalArgumentException("Invalid fill volume");

        // Verify that the node belongs to this book.
        if (ordersById.get(toFill.getId()) != toFillNode || toFillNode.isUnlinked())
            throw new IllegalArgumentException("Order is not part of this book");

        PriceLevel level = toFillNode.getPriceLevel();

        // Update order quantity, level volume, and linked-list membership.
        level.fillOrder(toFillNode, volume);

        if (toFill.isFilled()) {
            ordersById.remove(toFill.getId());
            removeLevelIfEmpty(toFill.getSide(), level);
        }
    }

    // Best price levels.
    public PriceLevel bestBid() { return bids.best(); }

    public PriceLevel bestAsk() { return asks.best(); }

    // Order lookup.
    public Order getOrder(long orderId) {
        OrderNode node = ordersById.get(orderId);

        return node == null ? null : node.getOrder();
    }

    public boolean containsOrder(long orderId) {
        return ordersById.containsKey(orderId);
    }

    // Book state.
    public boolean isEmpty() { return asks.isEmpty() && bids.isEmpty(); }

    // Private helpers.
    private PriceLevelIndex levelsFor(OrderSide side) {
        Objects.requireNonNull(side);

        return (side == OrderSide.BUY) ? bids : asks;
    }

    private void removeLevelIfEmpty(OrderSide side, PriceLevel level) {
        Objects.requireNonNull(level);

        if (level.isEmpty())
        {
            PriceLevelIndex levels = levelsFor(side);
            levels.remove(level.getPrice());
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append("OrderBook[\n");
        sb.append("  BIDS:\n");

        if (bids.isEmpty())
            sb.append("    (empty)\n");

        for (PriceLevel level : bids.levels())
            sb.append("    ").append(level).append("\n");

        sb.append("  ASKS:\n");

        if (asks.isEmpty())
            sb.append("    (empty)\n");

        for (PriceLevel level : asks.levels())
            sb.append("    ").append(level).append("\n");

        sb.append("  totalOrders=").append(ordersById.size()).append("\n");
        sb.append("]");

        return sb.toString();
    }
}
