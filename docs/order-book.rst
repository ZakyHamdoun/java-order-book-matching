Order book: structures and invariants
=====================================

Why three structures cooperate
------------------------------

Different queries need different access paths. A single sorted list would
make cancellation by ID expensive; a single hash map would not provide the
best price. ``OrderBook`` combines:

* Two price indexes, one for bids and one for asks, to locate levels.
* One doubly linked list per level, to preserve arrival order and remove a
  known node without searching.
* One ``HashMap<Long, OrderNode>`` for the whole book, to find an order by ID.

The map stores the **node**, not just the order. That node points to its owning
level and neighbors, providing everything needed to cancel it locally.

.. code-block:: text

   ordersById[42] -------------------------+
                                          v
   asks[101] -> PriceLevel -> head       [node 42]          tail
                             newest <----prev---- ... ---- oldest
                                    ----next---->

   In a three-order level (arrival A, B, C):

   head -> C <-> B <-> A <- tail
           next --->       (toward older orders)
           <--- prev       (toward newer orders)

The naming is easy to misread: **head is newest, tail is oldest**.
FIFO starts at ``tail``; traversal from oldest to newest follows ``prev``.
The ID map and the level list refer to the same node and the same mutable
order; they do not hold separate copies of its quantity.

State invariants
----------------

After each successful public book operation:

1. Every resting ID maps to exactly one active node in exactly one level.
2. Every resting order has positive remaining volume and a positive price.
3. A level contains orders of its price, on the side selected by the book.
4. ``size`` equals the number of linked nodes, and ``totalVolume`` equals the
   sum of their remaining quantities.
5. ``head.prev`` and ``tail.next`` are null; an empty level has both ends null.
6. Empty levels are absent from the price indexes.
7. A removed node is marked unlinked and its neighbor references are cleared.

These assume callers respect the ownership contract in :doc:`architecture`.
The book itself does **not** enforce an uncrossed market: direct
``addOrder`` calls can insert crossing buy and sell orders. Use the engine
for trading semantics.

Adding a resting order
----------------------

The book rejects a duplicate live ID, nonpositive remaining volume and market
orders. It selects the correct side, reuses or creates a level, and registers
the returned list node by ID:

.. literalinclude:: ../src/main/java/org/example/book/OrderBook.java
   :language: java
   :start-at:     public OrderNode addOrder(
   :end-before:     public void cancelOrder(
   :dedent: 4

Within the level, insertion adds at the head. Updating a handful of pointers
is O(1); no scan depends on the number of orders at this price:

.. literalinclude:: ../src/main/java/org/example/book/PriceLevel.java
   :language: java
   :start-at:     OrderNode addOrder(
   :end-before:     OrderNode popOrder(
   :dedent: 4

``Math.addExact`` checks aggregate-volume overflow **before** any links or
counters change. Cache the total rather than re-summing the list: Pro Rata
can read the denominator in O(1). The overall insertion cost includes the
price-index operations; it is not just the O(1) list insertion.

Cancellation by ID
------------------

The ID lookup finds the node in expected O(1), avoiding a search of every
order. The book unlinks it, deletes an empty level if necessary, and removes
the ID entry. An unknown ID throws ``IllegalArgumentException``:

.. literalinclude:: ../src/main/java/org/example/book/OrderBook.java
   :language: java
   :start-at:     public void cancelOrder(
   :end-before:     public void fillOrder(
   :dedent: 4

The doubly linked list makes arbitrary known-node removal O(1). Each neighbor
is reconnected, with separate handling for an endpoint or the sole node:

.. literalinclude:: ../src/main/java/org/example/book/PriceLevel.java
   :language: java
   :start-at:     void removeOrder(
   :end-before:     void fillOrder(
   :dedent: 4

Two links are worthwhile here: a singly linked list would need to find the
predecessor to remove an arbitrary node. Java's ordinary ``LinkedList`` does
not expose its internal nodes for this ID-to-node lookup design.

Deletion of the final order adds the index's deletion cost:

.. literalinclude:: ../src/main/java/org/example/book/OrderBook.java
   :language: java
   :start-at:     private void removeLevelIfEmpty(
   :end-before:     @Override
   :dedent: 4

Thus "cancel is O(1)" is incomplete. A cancellation that empties an arbitrary
level costs O(log P) with TreeMap and O(P) in the heap's general case, where
P is the number of levels on that side. See :doc:`price-indexes` for why.

Partial and complete fills
--------------------------

``OrderBook.fillOrder`` first checks volume bounds and **node identity**.
Comparing references prevents a stale node from being used after its ID has
been canceled and reused by another order:

.. literalinclude:: ../src/main/java/org/example/book/OrderBook.java
   :language: java
   :start-at:     public void fillOrder(
   :end-before:     // Best price levels.
   :dedent: 4

The level then reduces both the order remainder and the cached aggregate:

.. literalinclude:: ../src/main/java/org/example/book/PriceLevel.java
   :language: java
   :start-at:     void fillOrder(
   :end-before:     public Order peekNewest()
   :dedent: 4

A partial fill leaves the node in place, preserving time priority. On a full
fill, ``removeOrder`` subtracts the order's remaining volume, which is now
**zero**. The aggregate is therefore not decremented twice. The book removes
the ID and, only if needed, the empty price level.

The list work and a partial fill are O(1). A full fill has the extra level
deletion cost only when it empties that level. During engine matching this
is always the current best opposing level, a distinction used in
:doc:`complexity`.

The lower-level FIFO pop helper
-------------------------------

``PriceLevel.popOrder`` removes the tail in O(1) and subtracts its entire
remaining quantity:

.. literalinclude:: ../src/main/java/org/example/book/PriceLevel.java
   :language: java
   :start-at:     OrderNode popOrder(
   :end-before:     void removeOrder(
   :dedent: 4

This package-private helper is exercised by level tests. Production matching
uses ``OrderBook.fillOrder`` instead, because popping alone would not update
the book's ID map or price index.

Lookup, inspection and memory
-----------------------------

``getOrder(id)`` returns the live order or null; ``containsOrder(id)`` checks
the same hash map. Both have expected O(1) lookup cost. Best-price queries
delegate to the selected index. Level size, total volume and endpoint access
are cached O(1) reads.

With N resting orders and P total levels across both sides, retained book
storage is O(N + P): one order/node and ID entry per order, plus level/index
entries. Object headers, boxed ``Long`` keys and pointers add real JVM memory
costs; asymptotic space does not describe those constants. ``toString`` visits
every order, and the heap index additionally sorts levels for display; it is
diagnostic output, not a constant-time snapshot.
