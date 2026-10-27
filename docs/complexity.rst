Deriving time and space complexity
==================================

Define the input sizes first
----------------------------

.. list-table:: Symbols
   :header-rows: 1
   :widths: 15 85

   * - Symbol
     - Meaning
   * - N
     - Resting orders in the entire book.
   * - P
     - Maximum number of active levels on the relevant side during the operation.
   * - m
     - Orders at one selected level when matching starts there.
   * - L
     - Distinct opposing levels visited by one submission.
   * - D
     - Visited levels fully removed, with D <= L.
   * - K
     - Trades produced, one per positive resting-order allocation.
   * - M
     - Sum of m over visited levels, including zero-allocation orders.
   * - A
     - Cost of resting the incoming remainder, or zero if no remainder rests.

Use log(P + 1) wherever the tables abbreviate log P. Hash lookup bounds are
**expected**, and insertion/array-growth bounds are **amortized**. In a
single operation, an array or hash-table resize can cost linear time.
These are algorithmic bounds, not latency guarantees under JVM allocation,
garbage collection or scheduling.

Book operations
---------------

.. list-table:: Including level creation/deletion
   :header-rows: 1
   :widths: 44 28 28

   * - Operation
     - TreeMap book
     - Heap book
   * - Lookup/contains ID
     - Expected O(1)
     - Expected O(1)
   * - Add at an existing price
     - O(log P)
     - Expected amortized O(1)
   * - Add at a new price
     - O(log P)
     - Expected amortized O(log P)
   * - Cancel/fill without deleting level
     - Expected O(1)
     - Expected O(1)
   * - Cancel/fill deleting arbitrary level
     - O(log P)
     - O(P)
   * - Fill deleting current best, OpenJDK 26
     - O(log P)
     - O(log P)
   * - Best bid/ask
     - O(log P)
     - O(1)
   * - Format the whole book
     - O(N + P)
     - O(N + P log P)

The bounds involving hash maps retain the hashing assumptions above.
Cancellation does not need a tree lookup to find the order's level: the
node already holds it. Only removal of an empty level touches the price
index. For whole-book formatting, P represents total levels across both
sides; both sides' traversal costs are included. See :doc:`order-book` and
:doc:`price-indexes` for the code underlying each row.

Compose the cost of a submission
--------------------------------

The :doc:`matching-engine` loop performs at most L + 1 best-level lookups
for the supplied policies. Let B be one such lookup and R_best the cost of
removing a best level. The work is bounded by::

   FIFO:     O((L + 1) * B + K + D * R_best + A)
   Pro Rata: O((L + 1) * B + M + D * R_best + A)

This is a cost decomposition of the code, not a different matching
algorithm. Pro Rata's O(K) output work is absorbed into O(M), since K <= M.
The formulas do not automatically hold for a custom policy that makes only
small progress on each call; that policy may revisit the same level.

Substitute the index costs to obtain:

.. list-table:: Full submission, expected/amortized hashing assumptions
   :header-rows: 1
   :widths: 30 35 35

   * - Index
     - FIFO
     - Pro Rata
   * - TreeMap
     - O(K + (L + 1) log P + A)
     - O(M + (L + 1) log P + A)
   * - Heap, OpenJDK 26 root-removal behavior
     - O(K + L + 1 + D log P + A)
     - O(M + L + 1 + D log P + A)
   * - Heap, conservative public removal bound
     - O(K + L + 1 + D P + A)
     - O(M + L + 1 + D P + A)

A is a same-side insertion cost, so use that side's number of levels when
substituting it. For the heap refinement, removal is of the current root;
arbitrary cancellations elsewhere still have linear deletion cost. The
implementation dependency is explained in :doc:`price-indexes`.

Useful special cases
--------------------

* **No trade:** L = K = M = D = 0. A noncrossing limit pays one best lookup
  and one insertion; a market order facing an empty book has no insertion.
* **A small FIFO fill:** one trade can satisfy the incoming order even if
  millions of other orders wait at that price. K = 1; that level's order
  count does not enter the allocation cost.
* **A small Pro Rata fill:** one unit may create one trade but scan all m
  orders. K = 1 does not imply O(1) matching.
* **Complete sweep:** all resting orders on the opposite side can be visited
  and filled. K (and M for Pro Rata) can reach N, so matching is not merely
  O(log P).

These are traces of the same snippets in :doc:`fifo` and :doc:`pro-rata`.
Always ask whether an interview question counts price levels, resting
orders, executed trades or numerical volume. The loops here do not iterate
once per unit of volume.

Space and output sensitivity
----------------------------

The retained book uses O(N + P_total) space. Each submission returns an
O(K) trade list; the book does not retain trade history after returning it.
FIFO adds O(1) working storage. Pro Rata uses O(max m) simultaneously live
temporary storage because levels are processed sequentially, though it
allocates O(M) temporary entries cumulatively during the sweep. Garbage
collection may delay reclaiming earlier arrays.

An algorithm that returns K distinct trade records must do at least
Omega(K) output work. That lower bound is why a matching engine cannot
promise constant-time processing for every order, even with constant-time
best-price access.
