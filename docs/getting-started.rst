Getting started
===============

Requirements
------------

Install **JDK 26** and **Apache Maven** (the project has been verified with
Maven 3.9.9). The source and target versions are set to 26 in ``pom.xml``;
``Main`` also uses the modern no-argument entry-point syntax. Python is needed
only to build this documentation, not to run the engine.

Clone the project and check your tools:

.. code-block:: console

   git clone https://github.com/ZakyHamdoun/java-order-book-matching.git
   cd java-order-book-matching
   java -version
   mvn -version

Both version commands should report JDK 26. Maven uses ``JAVA_HOME``, which
can differ from the ``java`` found on ``PATH``. For a PowerShell session,
substitute your installed JDK directory:

.. code-block:: powershell

   $env:JAVA_HOME = 'C:\path\to\jdk-26'
   $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

On macOS or Linux:

.. code-block:: sh

   export JAVA_HOME=/path/to/jdk-26
   export PATH="$JAVA_HOME/bin:$PATH"

Compile and run
---------------

From the repository root:

.. code-block:: console

   mvn compile dependency:copy-dependencies "-DincludeScope=runtime"

Then on Windows:

.. code-block:: powershell

   java -cp "target/classes;target/dependency/*" org.example.Main

On macOS or Linux:

.. code-block:: sh

   java -cp "target/classes:target/dependency/*" org.example.Main

The classpath contains both the compiled classes and the SLF4J/Logback runtime
dependencies. The JAR is not an executable, dependency-bundled application.
You can also import ``pom.xml`` into an IDE, select JDK 26 for the project and
Maven runner, and run ``org.example.Main``.

The demo prints submissions, trades and book state for all four combinations
of index and policy. Look for different allocations at the same price while
the best-price ordering stays the same.

A minimal client
----------------

This complete class can be placed at
``src/main/java/org/example/QuickStart.java`` and run with the same classpath,
replacing ``org.example.Main`` with ``org.example.QuickStart``. Remove the
scratch class after experimenting.

.. code-block:: java

   package org.example;

   import org.example.book.OrderBook;
   import org.example.book.index.TreeMapPriceLevelIndex;
   import org.example.matching.MatchingEngine;
   import org.example.matching.policy.FifoMatchingPolicy;
   import org.example.order.Order;
   import org.example.order.OrderSide;

   public class QuickStart {
       public static void main(String[] args) {
           var book = new OrderBook(
                   new TreeMapPriceLevelIndex(true),
                   new TreeMapPriceLevelIndex(false));
           var engine = new MatchingEngine(book, new FifoMatchingPolicy());

           // id, side, price, original volume, remaining volume, priority
           engine.submitOrder(new Order(1, OrderSide.SELL, 100, 10, 10, 1));
           var trades = engine.submitOrder(
                   new Order(2, OrderSide.BUY, 100, 4, 4, 2));

           System.out.println(trades);
           System.out.println(book.getOrder(1).getRemainingVolume()); // 6
           engine.cancelOrder(1);
           System.out.println(book.isEmpty()); // true
       }
   }

The trade is ``Trade[buyOrderId=2, sellOrderId=1, price=100, volume=4]``.
The incoming buy is fully filled and never becomes a resting order.
Replace ``FifoMatchingPolicy`` with ``ProRataMatchingPolicy`` to change the
allocation rule, or use ``BinaryHeapPriceLevelIndex`` for both sides to change
the price data structure. The constructor flags remain ``true`` for bids and
``false`` for asks.

Run the tests
-------------

.. code-block:: console

   mvn test
   mvn clean verify

The first command runs unit tests. The second also runs functional integration
tests and enforces coverage thresholds. See :doc:`testing` for reports,
individual test selection and what the tests establish.

Build and preview the documentation
-----------------------------------

Use **Python 3.13** for the same environment as CI. Create an isolated
environment from the repository root. Activation is unnecessary.

Windows:

.. code-block:: powershell

   py -3.13 -m venv .venv-docs
   .\.venv-docs\Scripts\python.exe -m pip install -r docs/requirements.txt
   .\.venv-docs\Scripts\python.exe -m sphinx -n -W -b html docs docs/_build/html
   .\.venv-docs\Scripts\python.exe -m http.server 8000 --bind 127.0.0.1 --directory docs/_build/html

macOS or Linux:

.. code-block:: sh

   python3.13 -m venv .venv-docs
   .venv-docs/bin/python -m pip install -r docs/requirements.txt
   .venv-docs/bin/python -m sphinx -n -W -b html docs docs/_build/html
   .venv-docs/bin/python -m http.server 8000 --bind 127.0.0.1 --directory docs/_build/html

Open ``http://localhost:8000``; stop the server with ``Ctrl+C``. You can also
open ``docs/_build/html/index.html`` directly. The build treats warnings and
missing internal references as errors. Build from a full checkout because
the code excerpts refer to ``src/``. No Java compilation is required for the
documentation build. Hosting instructions are in :doc:`publishing`.
