Matching engine: orchestrating a submission
===========================================

Eligibility and execution price
-------------------------------

An incoming buy inspects the best ask; an incoming sell inspects the best
bid. A buy limit crosses when it is at least the ask, while a sell limit
crosses when it is at most the bid. A market order crosses any opposing
level:

.. literalinclude:: ../src/main/java/org/example/matching/MatchingEngine.java
   :language: java
   :start-at:     private PriceLevel bestOpposingPriceLevel(
   :end-before:     public List<Trade> submitOrder(
   :dedent: 4

Crossing is O(1) once the level is known. Finding that level has the selected
index's ``best`` cost. Both policies then execute at the resting price; a
buy limit of 105 matched with an ask of 101 trades at 101.

The submission loop
-------------------

.. literalinclude:: ../src/main/java/org/example/matching/MatchingEngine.java
   :language: java
   :start-at:     public List<Trade> submitOrder(
   :end-before:     public void cancelOrder(
   :dedent: 4

Read the loop in five steps:

1. Reject an already-resting ID and a zero-remaining-volume incoming order.
2. Look up the best opposing level. Stop if it does not exist or does not cross.
3. Delegate allocation at that one price to the supplied policy.
4. Require progress, then re-read the best opposing level because a full
   fill may have removed the previous one.
5. After matching stops, store a positive limit remainder; leave a market
   remainder outside the book.

For either supplied policy, one call consumes all incoming volume or all
volume at that level. Thus each visited level is processed once. The
progress guard prevents an infinite loop for a policy that leaves incoming
volume unchanged or increases it. It does not roll back changes made by an
invalid policy.

Notice that submission mutates the incoming object. It returns a new list
of trades, not a replacement order or a success/failure status object. A
market order may return an empty list without raising an exception.

A sweep across prices
---------------------

Suppose asks contain 101:6 and 102:5, with one order per level. Submit a buy
limit of 102 for quantity 13:

.. list-table:: Trace of the loop above
   :header-rows: 1
   :widths: 15 30 25 30

   * - Step
     - Action
     - Incoming remainder
     - Book consequence
   * - Start
     - Buy 13 at limit 102
     - 13
     - Asks at 101 and 102
   * - First call
     - Trade 6 at 101
     - 7
     - Remove ask level 101
   * - Second call
     - Trade 5 at 102
     - 2
     - Remove ask level 102
   * - Stop
     - No opposing level
     - 2
     - Rest bid quantity 2 at 102

With a market buy, the same trades execute but the final quantity 2 never
rests. With a limit of 101, only the first level trades and quantity 7 rests
at 101. The policies produce the same trace here because there is only one
order at each price.

Work done by the engine versus the policy
-----------------------------------------

The engine pays for best-price lookups, policy calls and possibly one final
insertion. The policy pays for the resting orders it visits, the trades it
creates and the book updates. Keeping those costs separate prevents the
incorrect conclusion that a loop over price levels is the entire complexity.

Let L be visited price levels, K produced trades, and M the total number of
resting orders present at those levels when visited. FIFO visits only the
orders it fills; Pro Rata scans all M, including orders receiving zero units.
See :doc:`complexity` for complete formulas with index cleanup included.

Both policies return trades in best-price order; within a price, their
trade records follow oldest-to-newest traversal. Neither policy sorts by ID,
``priority``, original volume or incoming order price.
