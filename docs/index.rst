Order Book & Matching Engine
============================

This guide explains a small, single-threaded Java matching engine from the
perspective of a software engineering interview: what state must be stored,
which rules change that state, and how to derive the time and space costs from
the implementation.

Start with :doc:`getting-started` to run the project, then read the chapters in
order. For revision, use :doc:`complexity` and :doc:`interview`. Java excerpts
are included directly from the repository at documentation build time, so the
algorithms you read are the algorithms the project runs.

The project offers two independent choices: **TreeMap or binary heap** for
price indexing, and **FIFO or Pro Rata** for allocation at a price. It models
one instrument per book; it does not implement an exchange server.

.. toctree::
   :maxdepth: 2
   :caption: Learn and run

   getting-started
   concepts
   architecture

.. toctree::
   :maxdepth: 2
   :caption: Read the implementation

   order-book
   price-indexes
   matching-engine
   fifo
   pro-rata

.. toctree::
   :maxdepth: 2
   :caption: Prepare and maintain

   complexity
   testing
   limitations
   interview
   publishing
   references
