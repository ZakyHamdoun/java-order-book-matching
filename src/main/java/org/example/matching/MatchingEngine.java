package org.example.matching;

import org.example.book.OrderBook;
import org.example.book.PriceLevel;
import org.example.matching.policy.MatchingPolicy;
import org.example.order.Order;
import org.example.order.OrderSide;
import org.example.trade.Trade;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class MatchingEngine {
    private final OrderBook orderBook;
    private final MatchingPolicy matchingPolicy;

    public MatchingEngine(OrderBook orderBook, MatchingPolicy matchingPolicy) {
        this.orderBook = Objects.requireNonNull(orderBook);
        this.matchingPolicy = Objects.requireNonNull(matchingPolicy);
    }

    private PriceLevel bestOpposingPriceLevel(OrderSide side) {
        Objects.requireNonNull(side);

        return (side == OrderSide.BUY)
                ? orderBook.bestAsk()
                : orderBook.bestBid();
    }

    // "Crosses" means the conditions are met for the Order to consume the level.
    private boolean crosses(Order incoming, PriceLevel opposingLevel) {
        Objects.requireNonNull(incoming);
        Objects.requireNonNull(opposingLevel);

        if (incoming.isMarketOrder())
            return true;
        if (incoming.getSide() == OrderSide.BUY)
            return incoming.getPrice() >= opposingLevel.getPrice();

        return incoming.getPrice() <= opposingLevel.getPrice();
    }

    public List<Trade> submitOrder(Order incoming) {
        Objects.requireNonNull(incoming);

        if (orderBook.containsOrder(incoming.getId()))
            throw new IllegalArgumentException("incoming order's id already exists within book!");
        if (incoming.isFilled())
            throw new IllegalArgumentException("incoming order was already executed and filled!");

        List<Trade> executedTrades = new ArrayList<>();
        OrderSide orderSide = incoming.getSide();

        while (!incoming.isFilled()) {
            PriceLevel currentBestOpposingLevel = bestOpposingPriceLevel(orderSide);

            if (currentBestOpposingLevel == null
            || !crosses(incoming, currentBestOpposingLevel))
                break;

            long remainingBefore = incoming.getRemainingVolume();
            matchingPolicy.match(incoming, currentBestOpposingLevel, orderBook, executedTrades);

            // Optional, robustness check which you theoretically never happen.
            if (incoming.getRemainingVolume() >= remainingBefore)
                throw new IllegalStateException("Matching policy made no progress");
        }

        if (!incoming.isFilled() && !incoming.isMarketOrder())
            orderBook.addOrder(incoming);

        return executedTrades;
    }

    public void cancelOrder(long orderId) {
        orderBook.cancelOrder(orderId);
    }

    // Getters.
    public OrderBook getOrderBook() { return this.orderBook; }
}
