# Order Book & Matching Engine | Java

A simple, in-memory Java order book and matching engine with FIFO and Pro Rata matching, interchangeable price indexes, and automated tests.

This is an **educational project**: its goal is to demonstrate a complete order lifecycle, a structured architecture, and how different data structures support the same behavior. It is intended for learning and experimentation, with a single-threaded design and no persistence or network API.

## Documentation

The [Sphinx interview guide](https://zakyhamdoun.github.io/java/order-book-matching-engine/) covers trading concepts, architecture, annotated algorithms, data structures, complexity derivations, FIFO versus Pro Rata, and implementation limits.

The sources are in [docs/](docs/index.rst), with [local build instructions](docs/getting-started.rst) and [GitHub Pages setup](docs/publishing.rst). The public URL becomes available after the user-site repository and deployment secret are configured and the `Documentation` workflow publishes successfully.

## Features

- **Buy and sell orders:** submit limit and market orders, with partial and full fills.
- **Price priority:** match against the best opposing price first and sweep multiple price levels when necessary. Trades execute at the resting order's price.
- **FIFO matching:** fill orders at the same price in arrival order.
- **Pro Rata matching:** allocate fills proportionally to remaining order volumes at a price level, distributing integer rounding leftovers in FIFO order.
- **Interchangeable price indexes:** choose a `TreeMap` or a binary heap (`PriorityQueue` with a price lookup map), independently of the matching policy.
- **Book management:** cancel and look up resting orders by ID, inspect the best bid and ask, and track order counts and total remaining volume at each price level.
- **Trade records:** each submission returns the executed trades, including buy/sell order IDs, price, and volume.
- **Console demo and automated tests:** exercise both price indexes and both matching policies.

Prices and quantities use integer `long` values. A price of `0` identifies a market order; a positive price identifies a limit order. Unfilled limit volume rests in the book, while unfilled market volume does not. FIFO follows insertion order; the order's `priority` field is metadata.

## Architecture

`MatchingEngine` handles submissions and price crossing, then delegates allocation within a price level to a `MatchingPolicy`. `OrderBook` manages resting orders through a hash map keyed by order ID, price indexes, and a doubly linked list of orders at each price level.

```text
src/
├── main/java/org/example/
│   ├── Main.java           # Console demonstration
│   ├── order/              # Order model and buy/sell side
│   ├── book/               # Order book, price levels, and linked nodes
│   │   └── index/          # PriceLevelIndex: TreeMap and binary heap
│   ├── matching/           # Matching engine
│   │   └── policy/         # MatchingPolicy: FIFO and Pro Rata
│   └── trade/              # Immutable trade record
└── test/java/org/example/
    ├── book/, order/, matching/  # Unit and shared implementation tests
    ├── functional/              # Order book and engine lifecycle tests
    └── support/                 # Factories for implementation combinations
```

The `PriceLevelIndex` and `MatchingPolicy` interfaces let you experiment with alternative implementations without changing the engine's orchestration.

## Getting started

### Requirements

- **JDK 26**, matching the Java version configured in `pom.xml`.
- **Apache Maven** installed and available as `mvn` (verified with Maven 3.9.9).

From the repository root, check that both Java and Maven use JDK 26:

```sh
java -version
mvn -version
```

Set `JAVA_HOME` to your JDK 26 installation and ensure its `bin` directory is on `PATH` if necessary.

### Build and run the demo

Compile the project and copy its runtime dependencies:

```sh
mvn compile dependency:copy-dependencies "-DincludeScope=runtime"
```

On Windows (PowerShell or Command Prompt):

```powershell
java -cp "target/classes;target/dependency/*" org.example.Main
```

On macOS or Linux:

```sh
java -cp "target/classes:target/dependency/*" org.example.Main
```

The demo logs the same scenario for all four combinations of price index and matching policy: resting orders, FIFO versus proportional allocation, execution across price levels, market orders, and cancellations.

You can also import `pom.xml` into a Java IDE, select JDK 26, and run `org.example.Main`.

### Use the engine

For example, inside a Java method:

```java
import org.example.book.OrderBook;
import org.example.book.index.TreeMapPriceLevelIndex;
import org.example.matching.MatchingEngine;
import org.example.matching.policy.FifoMatchingPolicy;
import org.example.order.Order;
import org.example.order.OrderSide;

var book = new OrderBook(
        new TreeMapPriceLevelIndex(true),    // Bids: highest price first
        new TreeMapPriceLevelIndex(false)); // Asks: lowest price first
var engine = new MatchingEngine(book, new FifoMatchingPolicy());

// Constructor: id, side, price, original volume, remaining volume, priority.
engine.submitOrder(new Order(1, OrderSide.SELL, 100, 10, 10, 1));
var trades = engine.submitOrder(new Order(2, OrderSide.BUY, 100, 4, 4, 2));
// One trade at price 100 for volume 4; sell order 1 has volume 6 remaining.

engine.cancelOrder(1);
```

Use `ProRataMatchingPolicy` to change allocation, or replace both indexes with `BinaryHeapPriceLevelIndex` to try the heap implementation.

## Tests and coverage

Run unit tests:

```sh
mvn test
```

Run a clean build with unit tests, functional integration tests, and coverage checks:

```sh
mvn clean verify
```

Tests use JUnit Jupiter and cover order validation, book operations, allocation rules, and complete order lifecycles. Shared tests exercise both price indexes and matching policies; seeded randomized tests compare book and index behavior against reference models.

Surefire runs `*Test` classes, and Failsafe runs `*IT` classes during `verify`. Reports are written to `target/surefire-reports/` and `target/failsafe-reports/`. The JaCoCo HTML coverage report is available at `target/site/jacoco/index.html` after verification.

The build enforces at least **98% line coverage** and **95% branch coverage**, excluding the console demo (`Main`).

## Contributing

Bug reports, improvements, and alternative implementations are welcome.

1. Open an issue to describe a bug or discuss a larger change.
2. Fork the repository and create a branch for your change.
3. Keep changes focused and follow the existing package structure and code style.
4. Add or update tests for behavior changes. New indexes or matching policies should participate in the shared tests through the factories in `src/test/java/org/example/support/`.
5. Run `mvn clean verify` and ensure the coverage checks pass.
6. Open a pull request explaining the change and how you validated it.

Keep the educational scope in mind: favor clear implementations and explain data-structure or algorithm tradeoffs when introducing them.
