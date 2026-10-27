Tests and evidence
==================

Commands and reports
--------------------

Run these commands from the repository root with Maven using JDK 26:

.. code-block:: console

   mvn test
   mvn clean verify
   mvn "-Dtest=MatchingEngineTest" test
   mvn "-Dit.test=MatchingEngineIT" verify

Surefire runs ``*Test`` unit tests. Failsafe runs ``*IT`` functional integration
tests during the integration-test/verify phases. Prefer ``verify`` over
invoking ``integration-test`` alone, because the verify goal evaluates the
Failsafe result. A targeted run may fail aggregate coverage thresholds
because it intentionally omits other tests; the full clean verification is
the acceptance command.

.. list-table:: Generated evidence
   :header-rows: 1

   * - Location
     - Contents
   * - ``target/surefire-reports/``
     - Unit-test text and XML reports.
   * - ``target/failsafe-reports/``
     - Functional integration-test reports.
   * - ``target/site/jacoco/index.html``
     - Coverage report after verification.

``pom.xml`` configures JaCoCo to enforce at least **98% line** and **95% branch**
coverage at bundle level, excluding ``Main``. This is aggregate coverage, not
a requirement that every individual class reach those percentages. JUnit
Jupiter is supplied by the JUnit 6.0.3 dependency.

What the test layers establish
------------------------------

.. list-table:: Test responsibilities
   :header-rows: 1
   :widths: 32 68

   * - Tests
     - Evidence
   * - ``order/*Test``
     - Construction, side representation, valid fills and rejection of invalid quantities.
   * - ``PriceLevelTest``, ``OrderNodeTest``
     - Endpoint/middle removal, FIFO links, total volume, detachment and overflow rejection.
   * - ``BookAccessTest``
     - Structural mutations and node construction remain package-private.
   * - ``PriceLevelIndexTest``
     - Ordering, removal, implementation-specific duplicate handling and seeded reference comparisons.
   * - ``OrderBookTest``
     - Live-ID rules, membership checks, cancellation/fill cleanup and independent sides.
   * - ``MatchingPolicyTest``
     - Remaining-volume allocation, rounding, empty inputs and single-level behavior.
   * - ``MatchingEngineTest``
     - Crossing, sweeps, market/limit remainders and the progress guard.
   * - ``OrderBookIT``
     - Mixed lifecycles and seeded operations compared with an independent reference model.
   * - ``MatchingEngineIT``
     - Exact trade sequences and volume conservation across mixed submissions/cancellations.

Shared implementations
----------------------

The tests construct both index implementations through a small enum factory:

.. literalinclude:: ../src/test/java/org/example/support/BookImplementation.java
   :language: java
   :start-at: public enum

An equivalent ``MatchingImplementation`` factory provides the two policies.
Parameterized tests use these to cover the two-by-two matrix, reducing the
risk of one variant silently losing coverage. The factories select an
implementation; they are test infrastructure rather than production runtime
configuration.

Seeded randomized checks are reproducible: a reported seed and operation
step identify the failing lifecycle. They compare independent expected state
after operations, which can detect interactions that a single hand-picked
example misses. They do not prove correctness for every input.

How to validate an extension
----------------------------

Preserve the invariant tests first, then add cases that distinguish the new
behavior. For a new policy, assert exact trade IDs, prices and volumes as
well as the surviving orders; checking only the total execution could miss
an incorrect priority rule. Exercise partial and full fills, no liquidity,
price boundaries, cancellation of the final node, and integer edge cases.

Coverage tells you which lines and branches executed, not whether the
assertions establish fairness, arithmetic safety or concurrency correctness.
The limits in :doc:`limitations` remain even when the build is green.
