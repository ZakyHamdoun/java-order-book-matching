package org.example.matching.policy;

import org.example.book.OrderBook;
import org.example.book.OrderNode;
import org.example.book.PriceLevel;
import org.example.order.Order;
import org.example.order.OrderSide;
import org.example.trade.Trade;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ProRataMatchingPolicy implements MatchingPolicy {
    // We assume executionVolume * order.getRemainingVolume() fits in a long.
    @Override
    public void match(Order incoming, PriceLevel opposingLevel, OrderBook orderBook, List<Trade> trades) {
        Objects.requireNonNull(incoming);
        Objects.requireNonNull(opposingLevel);
        Objects.requireNonNull(orderBook);
        Objects.requireNonNull(trades);

        long executionVolume = Math.min(incoming.getRemainingVolume(), opposingLevel.getTotalVolume());
        long totalRemainingLevelVolume = opposingLevel.getTotalVolume();

        if (executionVolume == 0)
            return;

        // 1. Snapshot nodes and compute proportional allocations.
        List<OrderNode> nodes = new ArrayList<>();
        List<Long> allocations = new ArrayList<>();

        long allocatedVolume = 0;
        OrderNode current = opposingLevel.peekOldestNode();

        while (current != null) {
            Order order = current.getOrder();

            long allocation = executionVolume
                    * order.getRemainingVolume()
                    / totalRemainingLevelVolume;

            nodes.add(current);
            allocations.add(allocation);
            allocatedVolume += allocation;

            // Traverse from oldest to newest.
            current = current.getPrev();
        }

        // 2. Distribute rounding leftovers in FIFO order.
        long leftoverVolume = executionVolume - allocatedVolume;

        for (int i = 0; i < nodes.size() && leftoverVolume > 0; i++) {
            long available = nodes.get(i).getOrder().getRemainingVolume();
            long allocation = allocations.get(i);

            if (allocation < available) {
                allocations.set(i, allocation + 1);
                leftoverVolume--;
            }
        }

        // 3. Execute trades and update the book.
        for (int i = 0; i < nodes.size(); i++) {
            long allocation = allocations.get(i);

            if (allocation == 0)
                continue;

            OrderNode node = nodes.get(i);
            Order restingOrder = node.getOrder();

            long buyId;
            long sellId;

            if (incoming.getSide() == OrderSide.BUY) {
                buyId = incoming.getId();
                sellId = restingOrder.getId();
            } else {
                buyId = restingOrder.getId();
                sellId = incoming.getId();
            }

            trades.add(new Trade(
                    buyId,
                    sellId,
                    restingOrder.getPrice(),
                    allocation
            ));

            incoming.fill(allocation);
            orderBook.fillOrder(node, allocation);
        }
    }
}
