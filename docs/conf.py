"""Sphinx configuration for the Java order book interview guide."""

project = "Order Book & Matching Engine"
author = "Zaky Hamdoun"
copyright = "2026, Zaky Hamdoun"
release = "1.0"

extensions = ["sphinx.ext.githubpages"]
root_doc = "index"
exclude_patterns = ["_build", "Thumbs.db", ".DS_Store"]
language = "en"
highlight_language = "java"
nitpicky = True

html_theme = "alabaster"
html_title = "Java Order Book & Matching Engine"
html_baseurl = "https://zakyhamdoun.github.io/java/order-book-matching-engine/"
html_static_path = ["_static"]
html_css_files = ["guide.css"]
html_theme_options = {
    "description": "An implementation-first interview guide",
    "github_user": "ZakyHamdoun",
    "github_repo": "java-order-book-matching",
    "github_button": False,
    "fixed_sidebar": True,
    "page_width": "1120px",
    "body_max_width": "850px",
}
html_sidebars = {"**": ["about.html", "navigation.html", "searchbox.html"]}
