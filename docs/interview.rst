Interview preparation
=====================

A concise explanation of the design
-----------------------------------

Practice this account in your own words:

   The book stores resting orders using separate bid and ask price indexes.
   Each price has a doubly linked FIFO list, and an ID hash map points directly
   to nodes for cancellation. The engine selects the best crossing price and
   delegates allocation within that price to a policy. FIFO fills oldest
   orders; Pro Rata snapshots remaining sizes and distributes proportional
   integer shares with FIFO leftovers. The book coordinates all structural
   updates so quantities, lists and indexes remain consistent.

Then give the operation-specific complexity rather than claiming that the
whole design is O(1) or O(log N).

Questions worth answering aloud
-------------------------------

**Why are levels separate from orders?**
   Price discovery operates over P levels, while allocation operates over
   orders at one price. This avoids repeating price-ordering work for every
   order and enables a cached aggregate. See :doc:`order-book`.

**Why store a node in the ID map?**
   An order reference alone does not locate its neighbors. The node enables
   constant-time unlinking and gives access to its owning level. Explain
   the final-level deletion qualification from :doc:`complexity`.

**Is a heap always better because best-price lookup is O(1)?**
   No. Arbitrary deletion and ordered depth traversal have different costs.
   Distinguish root removal during matching from arbitrary level cancellation
   using :doc:`price-indexes`.

**How is FIFO maintained after a partial fill?**
   The node stays in place. Only its remaining volume and the level total
   decrease. The next incoming order sees the same oldest node.

**Does Pro Rata always give every order a fill?**
   No. Integer floors can be zero, and only some older orders receive leftover
   units. Use the numerical example and proof in :doc:`pro-rata`.

**Why not modify quantities while computing proportional shares?**
   Every share must use the same initial denominator and remaining sizes.
   Snapshot first, apply allocations later; otherwise results depend on
   mutation order and node removal can disrupt traversal.

**Can a one-unit incoming order be expensive?**
   Under Pro Rata it scans all orders at the selected price. FIFO may fill
   it from one node. Numerical volume and number of visited orders are
   different inputs to the analysis.

**Why is integer arithmetic still unsafe in one place?**
   A product can overflow even when its operands and the final quotient
   fit in a ``long``. Discuss the actual assumption, not a guarantee the
   current code does not make.

**What prevents stale-node reuse?**
   The book compares the registered node by reference and checks its linked
   status. Reusing an ID does not make the old node valid again.

**What would you change for concurrent production use?**
   Start with ownership, sequencing and whole-operation consistency, then
   persistence and recovery requirements. Thread-safe individual collections
   do not make a matching transaction safe. See :doc:`limitations`.

Practice traces
---------------

Use the existing snippets in :doc:`matching-engine`, :doc:`fifo` and
:doc:`pro-rata` as executable reference algorithms while solving these.

1. Ask level 100 contains A:6, B:3, C:1, oldest first. A buy for 8 at limit
   100 arrives. Compute every trade and the final level for both policies.
2. Add an ask D:5 at 101. From the original book, submit a market buy of 12.
   Which levels disappear and what remains?
3. A bid level has two orders. Cancel the first, then the second. Explain
   which operation invokes price-index removal and compare tree/heap costs.
4. Change only each order's ``priority`` value. Does either result change?

Answers
~~~~~~~

1. FIFO fills A:6 and B:2, leaving B:1 and C:1. Pro Rata floors are
   (4, 2, 0), with two leftovers assigned to A and B. Fills are (5, 3, 0),
   leaving A:1 and C:1. Both execute 8 units at 100.
2. Both policies consume all 10 units at 100 and then 2 at 101. The 100
   level disappears, D remains with 3, and the incoming order is filled.
3. The first cancellation has expected O(1) cost. The second deletes the
   level: O(log P) for TreeMap, O(P) for an arbitrary heap level (or O(log P)
   for the root in OpenJDK 26).
4. No. Insertion order controls time priority; ``priority`` is metadata.

Reading a new implementation critically
---------------------------------------

Identify its invariants, follow one partial fill and one last-order removal,
then count actual collection operations. Check whether a claimed constant-time
operation hides a search, whether a cached aggregate updates on every path,
and whether the arithmetic assumptions are enforced. Finally distinguish
output size from working storage. Those habits transfer beyond matching
engines to schedulers, caches and event-processing systems.
