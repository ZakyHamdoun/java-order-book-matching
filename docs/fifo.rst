FIFO matching
=============

Algorithm at one price
----------------------

FIFO serves the oldest resting order first. For each match, the execution
quantity is the smaller of the incoming remainder and the oldest order's
remainder. This either fills the incoming order, removes the oldest resting
order, or both. Repeat while both sides still have volume at this price.

.. literalinclude:: ../src/main/java/org/example/matching/policy/FifoMatchingPolicy.java
   :language: java
   :start-at: public final class

.. note::

   The listing above contains the complete method. Its loop reads the tail
   again after each fill because the book may have unlinked the old tail.

A worked allocation
-------------------

At price 100, orders A, B and C arrived in that order with remaining
quantities 6, 3 and 1. An incoming opposing order can execute 5 units:

.. list-table:: FIFO result
   :header-rows: 1

   * - Resting order
     - Before
     - Fill
     - After
   * - A (oldest)
     - 6
     - 5
     - 1
   * - B
     - 3
     - 0
     - 3
   * - C (newest)
     - 1
     - 0
     - 1

Only one trade is created. A keeps its position at the tail because it
remains active. If the incoming quantity had instead been 8, A would fill
6 and B would fill 2; C would remain untouched.

Correctness and termination
---------------------------

The minimum prevents either order from being overfilled. Every iteration
creates a positive trade and reduces incoming remaining volume. If the
incoming order survives, the resting tail was fully filled and removed,
so the next iteration considers a different resting order. The finite
list therefore cannot cause an infinite loop under the book invariants.

For each trade of quantity q, both incoming and resting remaining volume
decrease by q. That is the conservation rule: executed buy volume equals
executed sell volume. Do not compare the sum of decreases on both sides to q;
that sum is 2q because there are two counterparties.

Cost and tradeoffs
------------------

If k resting orders receive fills at this level, the loop costs O(k), plus
the index deletion cost if the level becomes empty. List removal and partial
fills are constant time. It uses O(1) working storage beyond the O(k) output
trades. It never scans younger orders once the incoming order is filled.

FIFO is simple to explain, deterministic and efficient for small incoming
orders. Earlier arrivals receive a clear advantage; a newer order can wait
behind a large older order even when both quote the same price. This favors
arrival priority over distributing executions across participants. The
project has no timestamps or network sequencing: insertion order is its
definition of arrival.

Use the same initial quantities in :doc:`pro-rata` to compare allocations.
