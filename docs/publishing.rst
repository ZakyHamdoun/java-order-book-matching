Documentation maintenance and publishing
========================================

The intended URL
----------------

The repository remote identifies the account as ``ZakyHamdoun``. The guide's
configured address is:

`https://zakyhamdoun.github.io/java/order-book-matching-engine/ <https://zakyhamdoun.github.io/java/order-book-matching-engine/>`_

GitHub serves websites on ``github.io``; ``github.com/ZakyHamdoun`` is the
account's repository/profile interface. A normal Pages deployment from
``java-order-book-matching`` would include that repository name in its URL.
To obtain the requested path directly under the account website, the
workflow publishes into the **user-site repository**
``ZakyHamdoun/ZakyHamdoun.github.io``. See GitHub's
`site naming rules <https://docs.github.com/en/pages/getting-started-with-github-pages/what-is-github-pages>`_.

This repository contains the workflow and guide sources. A successful local
build does not make the public address live: the hosting repository, publishing
credential and Pages settings below must exist, and a workflow run must
publish successfully.

One-time hosting setup
----------------------

1. Create ``ZakyHamdoun/ZakyHamdoun.github.io`` if it does not already exist.
   Use a public repository for GitHub Free, or check that your plan supports
   Pages for the selected visibility. Initialize it with a README so a
   default branch exists.
2. Create a ``gh-pages`` branch in that repository. It is the **static output
   branch**: keep generated site files here, separate from any site sources.
   If an existing website already uses a different publishing process,
   integrate this output into that process before switching its Pages source.
3. In the **user-site repository**, select **Settings → Pages → Build and
   deployment → Source: Deploy from a branch**, then ``gh-pages`` and
   ``/ (root)``. This workflow pushes static HTML to a branch; it does not
   use the Actions artifact deployment mode in the source repository.
4. Create a fine-grained personal access token owned by ``ZakyHamdoun``,
   restricted to the user-site repository, with **Contents: Read and write**.
   Store it as ``PAGES_DEPLOY_TOKEN`` in the source repository's Actions
   secrets, or in its ``documentation`` environment. Do not commit the token.
5. Ensure branch rules allow this publishing identity to push to the target
   ``gh-pages`` branch. If desired, restrict the source repository's
   ``documentation`` environment to ``main``.
6. Commit and push the project and documentation changes to ``main``, or run
   **Actions → Documentation → Run workflow** on ``main``. Inspect both this
   workflow and the target repository's Pages build/deployment status.

The source repository's automatic ``GITHUB_TOKEN`` is limited to that
repository; it cannot push this content into a different repository.
Additionally, GitHub documents that pushes with ``GITHUB_TOKEN`` do not
trigger branch-based Pages builds. The dedicated token addresses both
requirements. See
`publishing-source configuration <https://docs.github.com/en/pages/getting-started-with-github-pages/configuring-a-publishing-source-for-your-github-pages-site>`_
and `token permissions <https://docs.github.com/en/actions/security-for-github-actions/security-guides/automatic-token-authentication>`_.

The workflow adds a root ``.nojekyll`` to serve the branch as static content,
including Sphinx's ``_static`` directory. This applies to the entire target
branch: that branch should contain ready-to-serve HTML rather than Jekyll
sources. It preserves other directories and an existing root ``CNAME``;
a custom domain configured there may change the final host, in which case
update ``html_baseurl``, the workflow environment URL and the README link.

Workflow behavior
-----------------

``.github/workflows/docs.yml`` builds on relevant pull requests, pushes to
``main`` and manual dispatch. It checks out the full project so source
excerpts resolve, installs the Sphinx version from ``docs/requirements.txt``,
and fails on documentation warnings. Source changes also trigger the build.

Pull requests only build and upload an HTML artifact for review. Publishing
requires a successful build, the original repository, and ``main``. The
publishing job downloads that exact artifact and synchronizes only
``java/order-book-matching-engine/`` in the target checkout; stale files
inside that directory are removed. It commits only that path and the
root ``.nojekyll`` marker, then pushes without force. If another writer
advances the target branch concurrently, a rejected push should be retried
by rerunning the workflow after checking the target history.

Repeated identical output produces no new commit. Concurrency groups prevent
overlapping documentation runs on the same source ref. The source workflow
does not claim that the target Pages build has finished when its push completes.

Editing this guide
------------------

Each topic has one canonical chapter. Link to it with ``:doc:`` rather than
duplicating an explanation. Use ``literalinclude`` for Java algorithms so
the rendered snippet follows the source. Prefer stable method-name boundaries
to line numbers; update boundaries if methods are renamed. The build catches
missing files, unmatched excerpt markers and unresolved internal references.

For example, the cancellation excerpt is maintained as:

.. code-block:: rst

   .. literalinclude:: ../src/main/java/org/example/book/OrderBook.java
      :language: java
      :start-at:     public void cancelOrder(
      :end-before:     public void fillOrder(
      :dedent: 4

Run the strict build and preview commands in :doc:`getting-started` before
pushing. Review the rendered code boundaries as well as the text: a build
can validate syntax and references but cannot verify a complexity argument.

External links can be checked separately once publishing is available:

.. code-block:: console

   python -m sphinx -b linkcheck docs docs/_build/linkcheck

Use the documentation environment's Python. External availability is not a
required CI gate because rate limits and an unpublished site can fail that
check independently of the documentation source.

Troubleshooting
---------------

* **Local compiler rejects release 26:** check ``mvn -version`` and
  ``JAVA_HOME``, not just the IDE's project SDK.
* **Sphinx cannot include Java files:** build from a complete checkout;
  copying only ``docs/`` omits the referenced source.
* **Publishing credential missing or target branch not found:** complete the
  one-time setup; do not change the URL to hide a failed deployment.
* **404 after a successful push:** check Pages settings in the user-site
  repository and wait for its deployment. The source workflow only pushes
  the generated content.
* **Styles or search assets missing:** confirm the root ``.nojekyll`` exists
  and the ``_static`` directory was published with the HTML.
