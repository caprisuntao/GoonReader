# Fixtures to save by hand

Cloudflare challenges this machine's egress (managed challenge, 503 + `challenge-platform`),
and headless Edge is challenged too, so `tools/capture-fixtures.ps1` cannot run here right now.
Saving the pages from your own browser produces equally good fixtures.

## Two facts that shape this list

1. **`/works/<id>` is not a stable "work page".** For a non-explicit work AO3 redirects it to
   `/works/<id>/chapters/<chapter-id>`, i.e. the work header and chapter 1 arrive in one
   document. It only stops at `/works/<id>` when the work is adult/explicit and shows the
   warning interstitial. Consequence: `WorkParser` and `ChapterParser` must both be able to
   parse the same document, and chapter 2 is captured separately because it is a different
   shape (prev/next nav, no "chapter 1 of N" framing).
2. **Tag URLs are never built by hand.** AO3 encodes characters inside tag names (`/` → `*s*`,
   `&` → `*a*`, and others), and the obvious `/tags/<name>/works` form does not resolve for
   every tag. The app therefore copies tag `href`s out of pages it has already parsed. The
   fixture must come from a real link for the same reason.

## How

1. Open each URL in a **private/incognito window** and stay logged out. AO3 renders a
   `Hi, <username>!` header and an account menu into every page when logged in; staying logged
   out keeps your account name out of a file that will be committed to git.
2. Save with **Ctrl+S → "Web page, HTML only"** (not "Webpage, complete" — that creates a
   folder of assets we do not need).
3. Name the file exactly as listed and drop it in this folder.
4. For groups 2 and 3, pick **one** multi-chapter work from `01-search-results.html` and use the
   same work throughout.

## The pages

| # | Save as | URL | Why we need it |
|---|---|---|---|
| 1 | `01-search-results.html` | `https://archiveofourown.org/works/search?work_search%5Bquery%5D=time%20travel&work_search%5Bsort_column%5D=kudos_count&work_search%5Bsort_direction%5D=desc` | Canonical blurb list; main `SearchParser` target |
| 2 | `02-search-results-page2.html` | the same URL with `&page=2` appended | Pagination; results 21–40 |
| 3 | `03-search-results-empty.html` | `https://archiveofourown.org/works/search?work_search%5Bquery%5D=zzqqxx` | "No results" state |
| 4 | `04-tag-browse.html` | open a work page first, then **click a fandom tag** and save the page it lands on | Real tag `href` shape; what the app copies instead of encoding |
| 5 | `05-work-chapter1.html` | `https://archiveofourown.org/works/<id>` — save **after** the redirect, i.e. the `/chapters/<chapter-id>` page it lands on | Work header + tags + stats + chapter 1 in one document |
| 6 | `06-work-navigate.html` | `https://archiveofourown.org/works/<id>/navigate` | Authoritative chapter list: id, number, title, date |
| 7 | `07-chapter-later.html` | `https://archiveofourown.org/works/<id>/chapters/<chapter-2-id>` (chapter 2 from the chapter list) | Chapter N>1 shape: prev/next nav, different title block |
| 8 | `08-chapter-comments.html` | item 5 or 7's URL with `?show_comments=true` | Threaded comments: nesting, depth, author, dates, pagination |
| 9 | `09-adult-interstitial.html` | an **Explicit** work's plain `https://archiveofourown.org/works/<id>` | The warning page, unconfirmed — the one case that does not redirect |
| 10 | `10-work-mature.html` | the same URL plus `?view_adult=true` | Mature work past the interstitial (also redirects, so this is a second chapter-1 sample) |
| 11 | `11-work-not-found.html` | `https://archiveofourown.org/works/1` | 404 page, so the parser fails softly |
| 12 | `12-work-single-chapter.html` | any work showing `1/1` in the search results, at `/works/<id>` | No chapter list at all — the edge case that breaks naive chapter loops |

Item 12 is optional but cheap: single-chapter works have no `#chapters` index table, which is the
easiest way for chapter-list parsing to throw.

## Minimum useful set

If saving all twelve is tedious, `01`, `04`, `05`, `06` and `08` let me write and prove
`SearchParser`, `TagParser`, `WorkParser`, `ChapterListParser`, `ChapterParser` and
`CommentsParser`. Item `07` is the one I would add next.

## Notes

- A `503`/`525` while saving means Cloudflare or AO3's origin is unhappy; reload until the tab
  title shows the real page (e.g. "Works Matching 'time travel'") instead of an error page.
- Do not hand-edit these files. When AO3 changes markup, re-capture — the git diff then tells us
  exactly which selectors broke.
- Once populated, `tools/capture-fixtures.ps1` becomes the refresh path; it writes its own
  `README.md` index of URLs, dates and byte counts.

## M4 pages (captured automatically)

`13`, `14` and `15` were captured by the app itself rather than by hand: a temporary intent hook
in `MainActivity` fetched each URL through `Ao3Client` (OkHttp first, the hidden WebView fallback
when Cloudflare challenges) and wrote the returned HTML to the app cache, which `adb exec-out
run-as` then copied out. The hook is gone; if these pages ever need recapturing, restore it and
run:

| # | Save as | URL |
|---|---|---|
| 13 | `13-search-form.html` | `/works/search?edit_search=true` |
| 14 | `14-tag-search.html` | `/tags/search?tag_search[name]=time travel` |
| 15 | `15-tag-works.html` | `/tags/Harry Potter - J*d* K*d* Rowling/works` |

`15` only resolves with AO3's own escapes in it. The unescaped form
(`/tags/Harry%20Potter%20-%20J.%20K.%20Rowling/works`) returns a 404, which is the whole reason the
app copies tag `href`s out of parsed pages instead of building them.
