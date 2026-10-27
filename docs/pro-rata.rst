Pro Rata matching
=================

Allocate against a fixed snapshot
---------------------------------

At one price, let v_i be order i's remaining quantity, V their total,
Q the incoming remainder, and E = min(Q, V) the quantity to execute here.
Each order first receives the integer share::

   a_i = floor(E * v_i / V)
   R   = E - sum(a_i)

The policy gives each of the R leftover units to the next oldest order that
still has capacity. This is **FIFO remainder allocation**, not a
largest-fractional-remainder rule. It uses remaining quantities, not original
quantities, and it does not allocate across different prices at once.

The implementation has three passes. First, fix the denominator and capture
nodes and base shares before any order is filled:

.. literalinclude:: ../src/main/java/org/example/matching/policy/ProRataMatchingPolicy.java
   :language: java
   :start-at:     @Override
   :end-before:         // 2. Distribute
   :dedent: 4

Computing shares while changing the denominator would make later orders use
a different book state. The node snapshot also avoids following links after
full fills have detached nodes and cleared their neighbors.

Second, allocate the rounding leftovers in oldest-to-newest order:

.. literalinclude:: ../src/main/java/org/example/matching/policy/ProRataMatchingPolicy.java
   :language: java
   :start-at:         // 2. Distribute
   :end-before:         // 3. Execute
   :dedent: 8

Third, execute each positive allocation. The snapshot retains references to
nodes, so all structural updates can go through the book:

.. literalinclude:: ../src/main/java/org/example/matching/policy/ProRataMatchingPolicy.java
   :language: java
   :start-at:         // 3. Execute
   :end-at:             orderBook.fillOrder(node, allocation);
   :dedent: 8

The three excerpts are consecutive portions of ``match``. Closing loop,
method and class braces are omitted from the final excerpt.

A worked allocation with rounding
---------------------------------

Use the same level as the FIFO example: A, B, C have quantities 6, 3, 1,
in arrival order. Incoming executable quantity is 5; V = 10.

.. list-table:: Pro Rata result
   :header-rows: 1

   * - Order
     - Exact share
     - Floor
     - FIFO extra
     - Final fill
     - After
   * - A
     - 3.0
     - 3
     - 1
     - 4
     - 2
   * - B
     - 1.5
     - 1
     - 0
     - 1
     - 2
   * - C
     - 0.5
     - 0
     - 0
     - 0
     - 1

The floors sum to 4, leaving one unit for A. A receives the extra even though
its fractional share was zero: arrival order, rather than fractional size,
is the tie-breaking policy. The result is two trades of 4 and 1, while FIFO
would produce a single trade of 5 against A.

If three equal orders compete for just one unit, every base share is zero
and the oldest receives the unit. Small executions can therefore behave much
like FIFO even under this Pro Rata policy.

Why one remainder pass is sufficient
------------------------------------

Assume positive remaining quantities and no arithmetic overflow. The exact
shares sum to E. Each floor discards less than one unit, so for m orders::

   0 <= R = E - sum(floor(E * v_i / V)) < m

R is an integer, hence at most m - 1. If E = V, every base allocation equals
its order quantity and R = 0. If E < V, then E * v_i / V < v_i for every
positive v_i. Thus every floor is at most v_i - 1, and every order has room
for one extra unit. Visiting the oldest orders once suffices to place all R
units without overfilling any order.

Consequently the final allocations sum exactly to E. The incoming order is
filled or the level is exhausted, which justifies the engine's one-call-per-
visited-level analysis for this policy.

Arithmetic boundary
-------------------

.. important::

   The multiplication ``executionVolume * order.getRemainingVolume()`` must
   fit in a signed Java ``long``. Java multiplication silently wraps on
   overflow, and this method does not check it. The proof above and the
   complexity discussion assume the multiplication is representable.

For example, E = 4,000,000,000 and v_i = 4,000,000,000 have a product larger
than ``Long.MAX_VALUE``, even though each quantity and their level total may
be legal. Dividing the wrapped product does not repair it. The aggregate
``Math.addExact`` in the book protects sums, not this multiplication.
Tests near the maximum with E = 1 demonstrate that safe case only.

A production version could use checked arithmetic with explicit input limits
or wider intermediate arithmetic such as ``BigInteger``. Neither change is
implemented here. See :doc:`limitations` for the related absence of rollback.

Cost, fairness and comparison
-----------------------------

For m orders at the chosen price, all three passes take O(m) time, plus a
possible level deletion. The two temporary ``ArrayList`` instances consume
O(m) additional storage; ``List<Long>`` also boxes allocations. Output costs
O(k) for the k positive allocations, where k can be much smaller than m.

.. list-table:: Allocation tradeoffs at one price
   :header-rows: 1
   :widths: 25 35 40

   * - Aspect
     - FIFO
     - Pro Rata here
   * - Advantage rewarded
     - Earlier arrival
     - Larger remaining quantity, then age for leftovers
   * - Work for a small fill
     - Stops after orders receiving fills
     - Scans every order at the level
   * - Temporary storage
     - Constant, excluding output
     - Linear in orders at the level
   * - Distribution
     - Can all go to one old order
     - Often shared, with integer rounding effects
   * - Operational complexity
     - Minimum and subtraction
     - Snapshot, multiplication, division, remainder handling

Pro Rata reduces pure arrival advantage for larger executions and rewards
displayed quantity. That can encourage larger displayed orders; FIFO
encourages earlier queue placement. These are consequences of the rules,
not measurements of this implementation. This simplified book has no
participant identity, anti-gaming rules or self-trade prevention. Splitting
quantity across multiple IDs can also affect who receives rounding units.
