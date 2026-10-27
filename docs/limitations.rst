Boundaries of this educational implementation
=============================================

This chapter distinguishes implemented guarantees from ideas an interviewer
may ask you to add. These are design boundaries, not hidden features.

Mutable state and thread ownership
----------------------------------

The engine is single-threaded. A submission updates order quantities,
linked-list nodes, aggregates, the ID map, price indexes and the trade list.
Replacing ``HashMap`` with ``ConcurrentHashMap`` would not make that compound
operation atomic or establish deterministic arrival order.

A production design needs a clear sequencing and ownership model for each
instrument, with synchronization across the entire state transition if
multiple threads can enter it. Network arrival timestamps alone do not
resolve concurrent mutation. Persistence and replay would also need to
preserve the accepted event sequence.

Public ``Order.fill`` and ``getOrder`` expose a mutation hazard: filling a
resting order directly changes its remainder without updating its level's
total. Similarly, retaining and modifying a supplied index can bypass the
book. Treat book-owned objects as read-only outside book operations.
Read-only order views and tighter ownership could strengthen that boundary.

Numeric limits and exception behavior
-------------------------------------

The book checks aggregate-volume addition with ``Math.addExact`` before
linking a new order. Pro Rata's unchecked product is a separate limit,
explained with an example in :doc:`pro-rata`. Fixed-width arithmetic counts
as O(1) in the complexity tables; switching to arbitrary precision would
require considering operand bit lengths too.

Submission is **not transactional**. If an error occurs after some trades
have executed, those earlier updates are not undone. For example, an
incoming limit may consume opposing liquidity and later fail when adding
its remainder would overflow an existing same-side level. An invalid custom
policy can mutate state before the progress guard throws. An exception
therefore does not always mean "the book was unchanged."

The trade record constructor itself performs no validation. The supplied
policies produce positive quantities and resting prices when their
preconditions and numeric assumptions hold; arbitrary callers can construct
other record values.

Scope of order identity and lifecycle
-------------------------------------

Only currently resting IDs are checked for duplicates. Once canceled or
fully filled, an ID can be reused. Filled incoming IDs are not retained in
a permanent registry. A market order with an unfilled remainder is not
registered either. There is no durable deduplication or retry/idempotency
contract, and returned trades have no globally unique trade ID.

Canceling an unknown ID throws instead of being silently idempotent. There
is no amend or cancel-replace API. Constructing another order with the same
live ID is rejected; callers cannot modify immutable price or original
volume fields in place.

Features outside this project
-----------------------------

There is no network API, persistence, recovery log, authentication, risk or
balance checks, market-data feed, participant identity, self-trade prevention,
auction, stop/iceberg order support or explicit time-in-force field. Market
remainders do not rest, but there is no separate cancellation acknowledgment.
Books have no instrument field or tick-size rules.

The console output is a demonstration, not a throughput benchmark. JVM
warmup, allocations, object layout, cache behavior and workload shape matter
in performance work. Big-O explains growth; it does not predict a particular
microsecond latency or establish that one policy/index is always faster.
