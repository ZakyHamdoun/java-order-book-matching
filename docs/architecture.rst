Architecture and design choices
===============================

Submission and state ownership
------------------------------

.. code-block:: text

   Client / Main
       |
       | submitOrder(incoming Order)
       v
   MatchingEngine ------------------------------> List<Trade> to caller
       |         |                                    ^
       |         +-- delegates one crossing level --+ | appends trades
       |                                            v |
       | best level / rest remainder        MatchingPolicy
       v                                    (FIFO or Pro Rata)
   OrderBook <--- fillOrder(node, quantity) ---------+
       |
       +-- bids: PriceLevelIndex ----+
       +-- asks: PriceLevelIndex ----+--> PriceLevel --> OrderNode <-> OrderNode
       +-- ordersById: HashMap --------------------------^

   Engine calls OrderBook.addOrder for an unfilled limit remainder.
   Client cancellation -> MatchingEngine.cancelOrder -> OrderBook.cancelOrder.

The engine selects **which price** can trade. The policy decides **how much
each order at that price receives**. The book coordinates **the resulting
state changes**. The policy mutates the incoming order directly because it
is not yet resting; it fills resting orders through ``OrderBook``.

Components and responsibilities
-------------------------------

.. list-table:: Production packages under ``src/main/java/org/example``
   :header-rows: 1
   :widths: 25 75

   * - Component
     - Responsibility
   * - ``Main``
     - Constructs the four combinations and logs an example lifecycle.
   * - ``order.Order``, ``OrderSide``
     - Order identity, side, immutable terms and mutable remaining volume.
   * - ``trade.Trade``
     - Immutable record containing buy ID, sell ID, execution price and volume.
   * - ``book.OrderBook``
     - Maintains the ID lookup and both price indexes together.
   * - ``book.PriceLevel``
     - Maintains one FIFO list, its size and total remaining volume.
   * - ``book.OrderNode``
     - Holds an order, its owning level and the two list links.
   * - ``book.index.PriceLevelIndex``
     - Price lookup, insertion, removal, best-price access and ordered traversal.
   * - ``matching.MatchingEngine``
     - Validates submissions, sweeps crossing levels and rests limit remainders.
   * - ``matching.policy.MatchingPolicy``
     - Allocates one incoming order against one opposing price level.

Patterns that are actually present
----------------------------------

**Strategy** is the central pattern. ``MatchingPolicy`` represents a
replaceable allocation algorithm. The engine receives an implementation,
so adding a policy does not require a policy-specific branch in the engine:

.. literalinclude:: ../src/main/java/org/example/matching/policy/MatchingPolicy.java
   :language: java
   :start-at: public interface

The price index is a second independent strategy: the book uses this
interface instead of committing to a particular collection:

.. literalinclude:: ../src/main/java/org/example/book/index/PriceLevelIndex.java
   :language: java
   :start-at: public interface

**Constructor dependency injection** supplies the book and policy to the
engine, and the two indexes to the book. No dependency-injection framework
is needed. Dependencies are explicit, non-null and easy to substitute in
tests. The engine's constructor shows this:

.. literalinclude:: ../src/main/java/org/example/matching/MatchingEngine.java
   :language: java
   :start-at:     public MatchingEngine(
   :end-before:     private PriceLevel bestOpposingPriceLevel
   :dedent: 4

This is **composition over inheritance**: final classes cooperate through
interfaces rather than inheriting variants of an engine. The TreeMap and
heap classes also act as small **adapters** from Java collection APIs to the
domain-specific index interface. ``OrderBook`` provides a coordinating
facade for updates spanning several structures.

``Trade`` is a **value object**, implemented as a Java record. Its immutable
primitive fields and generated equality make assertions straightforward:

.. literalinclude:: ../src/main/java/org/example/trade/Trade.java
   :language: java
   :start-at: public record

There is no event bus, persistence repository, Singleton or inheritance-based
Template Method here. Names for patterns should clarify the design, not add
concepts that the code does not implement.

Encapsulation and contracts
---------------------------

Node construction, node link setters and level mutations are package-private.
Outside ``org.example.book``, a policy can inspect nodes but cannot rearrange
the list. This concentrates structural changes in the book package.

There are deliberate limits to that protection: ``Order`` is mutable and
lookup returns the same object, while callers can retain supplied indexes.
The ownership discipline is therefore a contract as well as an access-control
choice. See :doc:`limitations` for the consequences.

Custom policies must consume only the supplied opposing level, fill through
the book, append accurate trades, and reduce incoming remaining volume when
eligible liquidity exists. The engine supplies price and side eligibility;
the policies do not independently verify those conditions. The progress
check catches a policy that does nothing, not every possible broken policy.

Extending the design
--------------------

To add an index, implement ``PriceLevelIndex`` and register it in the test
factory ``BookImplementation``. To add a policy, implement ``MatchingPolicy``
and register it in ``MatchingImplementation``, adding policy-specific expected
allocations where necessary. The engine orchestration should remain unchanged.

The current index contract is intentionally small, but its implementations
differ on duplicate ``put`` and whether ``levels()`` is a live view. Those
differences are documented in :doc:`price-indexes`; callers must not assume
stronger interchangeability than the book itself requires.
