Price indexes: tree versus heap
===============================

P denotes the number of active price levels on the side being indexed, not
the number of orders. A million orders at ten prices means P = 10.
Logarithmic bounds below use ``log(P + 1)`` conceptually so empty and
single-level cases still have constant cost.

TreeMap: keep all prices ordered
--------------------------------

``TreeMapPriceLevelIndex`` wraps a ``NavigableMap<Long, PriceLevel>`` backed
by a red-black tree. It reverses the comparator for bids, so the first entry
is always the best price on either side:

.. literalinclude:: ../src/main/java/org/example/book/index/TreeMapPriceLevelIndex.java
   :language: java
   :start-at: public final class

A balanced search tree has O(log P) height. Search follows one root-to-leaf
path; insertion and deletion preserve balance. An unbalanced binary search
tree could degenerate into a chain with O(P) height, which is why "binary
tree" alone does not justify logarithmic bounds. Java implements the balancing;
this project does not implement rotations.

``get``, ``put`` and ``remove`` have guaranteed O(log P) costs according to
the `Java TreeMap contract <https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/util/TreeMap.html>`_.
The implementation's ``firstEntry`` walks to the first node rather than
caching it, giving ``best`` O(log P) worst-case time. That last bound is
derived from the `OpenJDK 26 TreeMap implementation <https://github.com/openjdk/jdk/blob/jdk-26-ga/src/java.base/share/classes/java/util/TreeMap.java>`_.

``levels()`` returns a live values view in comparator order. Obtaining the
view is O(1); traversing all its elements is O(P), with no sorted copy.
Do not modify the view: removal would bypass the book's ID map and break
its invariants.

Binary heap: keep the best price at the root
--------------------------------------------

``BinaryHeapPriceLevelIndex`` pairs two collections:

* ``PriorityQueue<PriceLevel>`` orders levels so the best is at the root.
* ``HashMap<Long, PriceLevel>`` supports lookup by price without scanning the heap.

.. literalinclude:: ../src/main/java/org/example/book/index/BinaryHeapPriceLevelIndex.java
   :language: java
   :start-at: public final class

A binary heap is a complete tree represented by an array. A parent precedes
its children according to the comparator, but siblings and entire subtrees
are not globally sorted. New entries move toward the root as needed;
deletion repairs the heap along a path of logarithmic height. This explains
the O(1) ``peek`` and O(log P) heap-maintenance cost. Occasional backing-array
growth copies O(P) entries, so insertion's logarithmic bound is amortized
when accounting for allocation.

The price map points to **objects**, not their array positions. It makes
``get`` expected O(1), but it does not make arbitrary heap deletion
logarithmic. ``heap.remove(level)`` calls ``remove(Object)``, which searches
for the object before repairing the heap. Its public bound is O(P).
These operation bounds and unordered iteration are documented in the
`PriorityQueue API <https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/util/PriorityQueue.html>`_.

.. important::

   Deleting the **best** level is a useful special case. In OpenJDK 26,
   ``remove(Object)`` searches the array starting at index zero. If the
   object is the current root, the search succeeds immediately, leaving
   O(log P) heap repair. Both supplied policies empty only the best opposing
   level. Arbitrary cancellation does not have that property.

   This tighter bound depends on the
   `OpenJDK 26 PriorityQueue implementation <https://github.com/openjdk/jdk/blob/jdk-26-ga/src/java.base/share/classes/java/util/PriorityQueue.java>`_
   (``indexOf`` and ``removeAt``); the general API guarantee remains O(P).

The heap's ``levels()`` sorts a copy for display: O(P log P) time and O(P)
extra storage. A raw heap iterator would not give best-to-worst order.
The resulting list is unmodifiable, but its ``PriceLevel`` elements are the
live objects, so it is not a deep snapshot of book state.

Operation comparison
--------------------

.. list-table:: Costs for one side
   :header-rows: 1
   :widths: 40 30 30

   * - Operation
     - TreeMap
     - Binary heap + hash map
   * - Lookup price
     - O(log P)
     - Expected O(1)
   * - Insert a new level
     - O(log P)
     - Amortized O(log P), expected hashing
   * - Read best level
     - O(log P)
     - O(1)
   * - Delete an arbitrary level
     - O(log P)
     - O(P)
   * - Delete current best (OpenJDK 26)
     - O(log P)
     - O(log P)
   * - Return levels and visit all in order
     - O(P)
     - O(P log P)
   * - Extra ordered-traversal storage
     - O(1) view/iterator
     - O(P) copied/sorted references
   * - Retained index storage
     - O(P)
     - O(P), with a second lookup structure

Hash-map costs assume well-distributed hashes and amortize resizing; they
are not a promise of constant latency on every call. The heap and map must
always describe the same set of levels.

Observable interface differences
--------------------------------

Putting a duplicate price **replaces** the level in TreeMap and **throws** in
the heap index. ``OrderBook.addOrder`` first looks up the price, so it does
not invoke that mismatch during normal use. Both indexes ignore removal of
a missing price and return null when empty. Callers must construct separate
correctly oriented bid and ask indexes; the book constructor cannot infer
whether the supplied comparators are right.

Choosing and discussing improvements
------------------------------------

TreeMap is a natural baseline when arbitrary cancellation and ordered depth
inspection matter. The heap has cheap best-price access and existing-price
lookup, at the cost of linear arbitrary deletion and sorted-copy inspection.
The workload determines which tradeoff is useful; asymptotic analysis alone
does not establish which is faster for small books.

Possible extensions include a position-aware heap, cached best entries in
a tree wrapper, or a specialized best-level removal method. None is present
in this code. Each introduces another invariant to maintain, so justify it
with a measured workload before increasing implementation complexity.
