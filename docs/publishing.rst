Documentation maintenance and publishing
========================================

The documentation URL
---------------------

Documentation is deployed directly from ``ZakyHamdoun/java-order-book-matching``
to its GitHub Pages project site:

`https://zakyhamdoun.github.io/java-order-book-matching/ <https://zakyhamdoun.github.io/java-order-book-matching/>`_

The repository name determines the default project-site path. The originally
requested ``/java/order-book-matching-engine/`` path would require a separate
user-site repository or other hosting configuration. This workflow uses the
existing project repository. See GitHub's
`site naming rules <https://docs.github.com/en/pages/getting-started-with-github-pages/what-is-github-pages>`_.

One-time hosting setup
----------------------

1. Open ``ZakyHamdoun/java-order-book-matching`` on GitHub.
2. Select **Settings > Pages > Build and deployment > Source: GitHub Actions**.
3. Commit and push the updated workflow and documentation to ``main``, or
   select **Actions > Documentation > Run workflow** on ``main`` after the
   updated workflow is on that branch.
4. Check that the build and publish jobs succeed. The publish job's
   ``github-pages`` environment links to the deployed site.

No separate repository, ``gh-pages`` branch or personal access token is
required. Authentication uses the automatic ``GITHUB_TOKEN`` and a GitHub
OIDC token. If you created ``PAGES_DEPLOY_TOKEN`` for the earlier workflow,
you can remove that secret and revoke its personal access token if nothing
else uses it.

See GitHub's
`publishing-source configuration <https://docs.github.com/en/pages/getting-started-with-github-pages/configuring-a-publishing-source-for-your-github-pages-site>`_
and
`custom workflow instructions <https://docs.github.com/en/pages/getting-started-with-github-pages/using-custom-workflows-with-github-pages>`_.

Workflow behavior
-----------------

``.github/workflows/docs.yml`` builds on relevant pull requests, pushes to
``main`` and manual dispatch. It checks out the full project so source
excerpts resolve, installs the Sphinx version from ``docs/requirements.txt``,
and fails on documentation warnings. Source changes also trigger the build.

The build job packages the HTML with ``actions/upload-pages-artifact``.
Pull requests build and upload the artifact but never deploy. Publishing
requires a successful build, the original repository, and ``main``.
The publish job uses ``actions/configure-pages`` and ``actions/deploy-pages``
to deploy that artifact directly, without pushing generated files to Git.

Only the publish job receives ``pages: write`` and ``id-token: write``
permissions. The build job has read-only repository access. The
``github-pages`` environment can restrict deployment to ``main``;
concurrency groups prevent overlapping runs on the same source ref.

Sphinx creates a ``.nojekyll`` marker, and the artifact includes its
``_static`` assets. No Jekyll processing is needed. If you configure a
custom domain in Pages settings, update ``html_baseurl`` and the README
link accordingly. The workflow's environment URL comes from the deployment
output.

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
* **Repository not found while fetching a separate hosting repository:**
  the run is using the old workflow. Push the updated ``docs.yml`` to
  ``main`` and start a new run; rerunning an old commit uses its old workflow.
* **Pages configuration fails:** enable Pages with **GitHub Actions** as
  the source in this repository's settings, and check that the repository's
  visibility and account plan support Pages.
* **Deployment is blocked:** check the ``github-pages`` environment's
  branch restrictions and required approvals. The workflow deploys ``main``.
* **404 at the site URL:** check that the publish job succeeded and use
  the project URL above, including ``/java-order-book-matching/``.
* **Styles or search assets missing:** confirm the ``_static`` directory
  was included with the HTML in the Pages artifact.
