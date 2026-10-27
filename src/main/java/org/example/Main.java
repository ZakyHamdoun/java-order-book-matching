package org.example;

import org.example.book.OrderBook;
import org.example.book.index.BinaryHeapPriceLevelIndex;
import org.example.book.index.TreeMapPriceLevelIndex;
import org.example.matching.MatchingEngine;
import org.example.matching.policy.FifoMatchingPolicy;
import org.example.matching.policy.ProRataMatchingPolicy;
import org.example.order.Order;
import org.example.order.OrderSide;
import org.example.trade.Trade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    static void main() {
        runMatchingEngine("TreeMap / FIFO", new MatchingEngine(new OrderBook(
                new TreeMapPriceLevelIndex(true), new TreeMapPriceLevelIndex(false)),
                new FifoMatchingPolicy()));
        runMatchingEngine("TreeMap / Pro Rata", new MatchingEngine(new OrderBook(
                new TreeMapPriceLevelIndex(true), new TreeMapPriceLevelIndex(false)),
                new ProRataMatchingPolicy()));
        runMatchingEngine("Binary Heap / FIFO", new MatchingEngine(new OrderBook(
                new BinaryHeapPriceLevelIndex(true), new BinaryHeapPriceLevelIndex(false)),
                new FifoMatchingPolicy()));
        runMatchingEngine("Binary Heap / Pro Rata", new MatchingEngine(new OrderBook(
                new BinaryHeapPriceLevelIndex(true), new BinaryHeapPriceLevelIndex(false)),
                new ProRataMatchingPolicy()));
    }

    private static void runMatchingEngine(String name, MatchingEngine engine) {
        log.info("========== {} ==========", name);

        // Rest multiple orders at the same price and at a second ask level.
        submit(engine, 1, OrderSide.SELL, 100, 10);
        submit(engine, 2, OrderSide.SELL, 100, 30);
        submit(engine, 3, OrderSide.SELL, 105, 20);
        submit(engine, 4, OrderSide.BUY, 95, 10);

        // Show FIFO versus proportional allocation, then sweep two price levels.
        submit(engine, 5, OrderSide.BUY, 100, 8);
        submit(engine, 6, OrderSide.BUY, 105, 40);

        // Execute market orders on both sides using available liquidity.
        submit(engine, 7, OrderSide.BUY, 0, 2);
        submit(engine, 8, OrderSide.SELL, 0, 4);

        // Rest a partially executed limit order, then cancel both remaining orders.
        submit(engine, 9, OrderSide.SELL, 95, 10);
        log.info("Best bid: {}", engine.getOrderBook().bestBid());
        log.info("Best ask: {}", engine.getOrderBook().bestAsk());
        log.info("Order 9: {}", engine.getOrderBook().getOrder(9));
        engine.cancelOrder(3);
        engine.cancelOrder(9);
        log.info("Book empty: {}", engine.getOrderBook().isEmpty());
        log.info("{}", engine.getOrderBook());
    }

    private static void submit(MatchingEngine engine, long id, OrderSide side, long price, long volume) {
        Order incoming = new Order(id, side, price, volume, volume, id);
        log.info("Submit: {}", incoming);
        for (Trade trade : engine.submitOrder(incoming))
            log.info("Trade: {}", trade);
        log.info("{}", engine.getOrderBook());
    }
}
