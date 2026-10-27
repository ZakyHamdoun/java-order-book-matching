Trading concepts and project rules
==================================

The vocabulary
--------------

An **order** is an instruction to buy or sell a quantity. A **bid** is a buy
order; an **ask** (or offer) is a sell order. **Volume** and **quantity** mean
the same thing here.

An **order book** stores unexecuted orders that are waiting to trade. Such
orders are called **resting orders**. It groups orders by side and price;
each group is a **price level**. The **best bid** is the highest buy price,
and the **best ask** is the lowest sell price. Their difference, best ask
minus best bid, is the **spread**, when both sides exist.

A **matching engine** processes an incoming order, determines which resting
orders are eligible, allocates execution quantities, updates the book and
returns **trades**. A trade records an execution between one buy and one sell.
The book is the stored state; the engine applies the trading rules to it.

A **limit order** sets the maximum price a buyer will pay or the minimum a
seller will accept. A **market order** accepts any available opposing price.
Neither guarantees a complete fill: there may be too little opposing
quantity. **Liquidity** here means the resting quantity available to trade.

Price priority, then allocation priority
----------------------------------------

Both policies always use the best eligible opposing price first. Only the
allocation **within that price level** differs:

* **FIFO** (first in, first out), also called price-time priority in this
  setting, fills the oldest resting order before moving to the next.
* **Pro Rata** divides an execution in proportion to each resting order's
  remaining quantity. This implementation rounds down to whole units and
  gives remaining units to older orders first.

An earlier order at a worse price cannot jump ahead of a better price under
either policy. FIFO refers to the order of insertion into a level, not a
sort on the ``priority`` field.

Read a small book
-----------------

.. list-table:: Orders are listed oldest to newest at each price
   :header-rows: 1
   :widths: 15 15 45 25

   * - Side
     - Price
     - Orders (ID: remaining volume)
     - Total volume
   * - Ask
     - 102
     - C: 5
     - 5
   * - Ask
     - 101
     - A: 6, B: 4
     - 10
   * - Bid
     - 99
     - D: 8
     - 8

The best ask is 101, best bid 99 and spread 2. An incoming buy limit at
101 can consume A and B but cannot consume C. A buy limit at 102 may reach C
after consuming the 101 level. A buy limit at 100 rests without trading.
See :doc:`matching-engine` for the executable price-crossing rule and
:doc:`fifo` / :doc:`pro-rata` for allocation traces.

Exact conventions in this repository
------------------------------------

.. list-table:: Domain contract
   :header-rows: 1
   :widths: 30 70

   * - Field or behavior
     - Meaning
   * - ``price``
     - A nonnegative ``long``. Zero means market; a positive value means
       limit. The application chooses the tick or currency scale.
   * - ``originalVolume``
     - A positive ``long`` fixed when the order is constructed.
   * - ``remainingVolume``
     - A mutable ``long`` between zero and original volume. New orders
       normally start with the two volumes equal; partial-state construction
       is allowed.
   * - ``id``
     - A ``long`` unique among currently resting orders. No positive-only or
       permanent global uniqueness constraint is enforced.
   * - ``priority``
     - Immutable metadata. Neither matching policy reads it.
   * - Execution price
     - The resting order's price, even if the incoming limit is more generous.
   * - Unfilled limit quantity
     - Added to the incoming side of the book at its limit price.
   * - Unfilled market quantity
     - Remains on the incoming object but is not stored in the book.
   * - Cancellation
     - Removes all remaining quantity of a resting order; produces no trade.

Integer prices avoid binary floating-point comparison errors, but the project
does not validate instrument-specific tick sizes or currency units. It has no
symbol field, so use a separate engine/book for each instrument.

The ``Order`` constructor and fill operation enforce the numeric contract:

.. literalinclude:: ../src/main/java/org/example/order/Order.java
   :language: java
   :start-at:     public Order(
   :end-before:     // Getters.
   :dedent: 4

``fill`` is constant time: validate a positive quantity no greater than the
remainder, then subtract it. A zero remainder is allowed by the constructor
but rejected when submitting to the engine. Once an order rests in a book,
use book operations to fill it so aggregate state stays consistent.
