package org.example.matching.policy;

import org.example.book.OrderBook;
import org.example.book.PriceLevel;
import org.example.order.Order;
import org.example.trade.Trade;

import java.util.List;

public interface MatchingPolicy {
    void match(Order incoming,
               PriceLevel opposingLevel,
               OrderBook orderBook,
               List<Trade> trades);
}
