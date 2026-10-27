package org.example.matching.policy;

import org.example.book.OrderBook;
import org.example.book.OrderNode;
import org.example.book.PriceLevel;
import org.example.order.Order;
import org.example.order.OrderSide;
import org.example.trade.Trade;

import java.util.List;
import java.util.Objects;

public final class FifoMatchingPolicy implements MatchingPolicy {
    /*
    ** Matches an incoming order against resting orders at one price level
    ** using FIFO (oldest order first).
    **
    ** Preconditions:
    ** - opposingLevel is on the opposite side of incoming
    ** - opposingLevel's price crosses incoming's limit price
    ** - incoming has positive remaining volume
    */
    @Override
    public void match(Order incoming, PriceLevel opposingLevel, OrderBook orderBook, List<Trade> trades) {
        Objects.requireNonNull(incoming);
        Objects.requireNonNull(opposingLevel);
        Objects.requireNonNull(orderBook);
        Objects.requireNonNull(trades);

        while (!incoming.isFilled() && !opposingLevel.isEmpty()) {
            // 1. Retrieve the oldest (In --> [OUT]) order in specified opposing level.
            OrderNode oldestOrderNode = opposingLevel.peekOldestNode();

            if (oldestOrderNode == null)
                throw new IllegalStateException("Non-empty PriceLevel has a null tail");

            Order oldestOrder = oldestOrderNode.getOrder();

            // 2. Determine what is the maximum volume that can be executed (minimum of the two).
            long executionVolume = Math.min(incoming.getRemainingVolume(), oldestOrder.getRemainingVolume());

            // 3. Create a Trade record.
            long buyId;
            long sellId;

            if (incoming.getSide() == OrderSide.BUY) {
                buyId = incoming.getId();
                sellId = oldestOrder.getId();
            } else {
                buyId = oldestOrder.getId();
                sellId = incoming.getId();
            }

            Trade executedTrade = new Trade(buyId, sellId, oldestOrder.getPrice(), executionVolume);
            
            trades.add(executedTrade);
            incoming.fill(executionVolume);
            orderBook.fillOrder(oldestOrderNode, executionVolume);
        }
    }
}
