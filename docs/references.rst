Source map and further reading
==============================

Project source map
------------------

Paths below are relative to the repository root. Algorithm excerpts are
embedded in their explanatory chapters, so opening source is optional.

.. list-table:: Find the implementation behind a topic
   :header-rows: 1
   :widths: 30 70

   * - Topic
     - Source location
   * - Domain quantities and sides
     - ``src/main/java/org/example/order/``
   * - Book coordination and list nodes
     - ``src/main/java/org/example/book/``
   * - Price data structures
     - ``src/main/java/org/example/book/index/``
   * - Submission orchestration
     - ``src/main/java/org/example/matching/MatchingEngine.java``
   * - Allocation rules
     - ``src/main/java/org/example/matching/policy/``
   * - Execution record
     - ``src/main/java/org/example/trade/Trade.java``
   * - Demonstration
     - ``src/main/java/org/example/Main.java``
   * - Tests and implementation factories
     - ``src/test/java/org/example/``
   * - Build, dependency and coverage configuration
     - ``pom.xml``
   * - Documentation build and publishing
     - ``docs/conf.py``, ``docs/requirements.txt``, ``.github/workflows/docs.yml``

Primary references
------------------

The allocation rules described in this guide come from this project's
source. Other trading venues may use different priority, rounding and order
type rules; "Pro Rata" alone does not specify all those details.

For the Java collection guarantees, read the JDK 26
`HashMap <https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/util/HashMap.html>`_,
`TreeMap <https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/util/TreeMap.html>`_
and
`PriorityQueue <https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/util/PriorityQueue.html>`_
API documentation. The tighter best-entry and root-removal bounds are
implementation-derived, as distinguished in :doc:`price-indexes`.

For documentation maintenance, see Sphinx's
`literalinclude directive <https://www.sphinx-doc.org/en/master/usage/restructuredtext/directives.html#directive-literalinclude>`_
and
`build command <https://www.sphinx-doc.org/en/master/man/sphinx-build.html>`_.
For hosting, :doc:`publishing` links the relevant GitHub Pages and token
documentation alongside the setup that depends on it.
