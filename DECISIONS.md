# Decisions

Assumptions and trade-offs made while building. The PRD asks for these to be written down
rather than buried in chat.

## Toolchain (verified against the installed toolchain, not assumed)

| Thing | Value | Why |
|---|---|---|
| AGP / Gradle | 9.4.1 / 9.6.0 | What Android Studio 2026.2 generated; first build passed untouched |
| Kotlin | 2.2.10 | **Bundled with AGP 9** (built-in Kotlin). The Kotlin plugin is deliberately *not* applied |
| KSP | 2.2.10-2.0.2 | Must match the bundled Kotlin exactly |
| Compose compiler plugin | 2.2.10 | Same reason |
| Compose BOM | 2026.09.00 | Latest stable at time of setup |
| compileSdk / targetSdk / minSdk | 37 / 37 / 26 | PRD minSdk 26; SDK has platform 37 only |
| Java | 17 source/target, JVM 17 | Compose and current AndroidX require it |

`android.disallowKotlinSourceSets=false` in `gradle.properties` is required: KSP adds its
generated sources through `kotlin.sourceSets`, which built-in Kotlin forbids by default.
The message explicitly names this flag as the escape hatch. Revisit if KSP adds first-class
AGP 9 support.

Version numbers were read from Google Maven / Maven Central `maven-metadata.xml` at setup
time rather than guessed, because the toolchain is far newer than any model priors.

## App identity

- `namespace` and `applicationId`: `app.goonreader` (user's choice).
- Display name: GoonReader.
- License: Apache-2.0, code written from scratch. GPL-licensed AO3 clients (CO3 and similar)
  are not consulted; no code is copied from them.

## Theme

- One seed (`SeedOrangeBrown`) and palette role arrays per mode. The PRD says derive rather
  than hardcode, so the roles live in one place and are consumed through M3 `ColorScheme`.
- Runtime seed → full palette generation (needed for the accent-colour setting) is deferred
  to M6. Until then, changing the accent means editing the seed and the role arrays together.
- AMOLED is the dark palette with surfaces forced to true black; hues are unchanged.
- Dark is the default mode regardless of the system setting, per PRD 9.1.

## Bottom navigation

Hand-rolled `GoonBottomBar` instead of Material 3 `NavigationBar`, because the reference design
needs a pill behind the **icon only**, labels always visible, and a hairline divider rather than
an elevation shadow. Nothing else in the shell is custom.

## Database

- Multi-valued AO3 fields (authors, fandoms, tags, relationships) are stored **newline-separated**
  in single columns. AO3 tags cannot contain newlines, so this is lossless for real data and keeps
  library reads to one row per work.
  `shortcut: no per-tag queries; split into tag tables if library filtering needs them in M6.`
- `exportSchema = true`; schema JSON is committed under `app/schemas`.
- No destructive migration, ever. Version 1 was the initial schema; version 2 (M7) adds
  `bookmarks` through `MIGRATION_1_2`.

## Fixtures

Twelve real AO3 pages in `app/src/test/resources/fixtures`, captured logged-out by the user
(Cloudflare challenges this machine's egress, so scripted capture was not possible):

| File | Covers |
|---|---|
| `01-search-results.html`, `02-...page2.html` | blurbs, pagination |
| `03-search-results-empty.html` | no-results state |
| `04-tag-browse.html` | tag landing page, real tag hrefs |
| `05-work-chapter1.html` | work header + chapter 1 in one document |
| `06-work-navigate.html` | authoritative chapter list |
| `07-chapter-later.html` | chapter N>1 shape |
| `08-chapter-comments.html` | threaded comments + comment pagination |
| `09-adult-interstitial.html` / `10-work-mature.html` | adult gate and `view_adult=true` |
| `11-work-not-found.html` | 404 |
| `12-work-single-chapter.html` | work with no chapter list |

Three more (M4) were captured through the app's own transport via a temporary hook, so they are
exactly what the parsers see:

| File | Covers |
|---|---|
| `13-search-form.html` | every `work_search[...]` field, rating/warning/category ids, sort and language options |
| `14-tag-search.html` | tag search results: type label, href, work count |
| `15-tag-works.html` | a tag's works page: 20 blurbs, numbered pagination, filter sidebar |

Known gap: all pages come from one fandom (Harry Potter). Fandoms with unusual characters in
tag names, series pages, collection pages and multi-author blurbs are not represented yet.

## Cloudflare (resolved by observation, not assumption)

Chrome on the same phone loads AO3; OkHttp did not, on the same IP, with the same URI. Tested in
order: honest UA, then a Chrome UA with matching `sec-ch-ua` client hints, `Sec-Fetch-*`, a
referer, and HTTP/1.1. All still got the managed challenge. So the trigger is the TLS/HTTP2
fingerprint (and the `cf_clearance` cookie Chrome already holds), neither of which OkHttp can
fake cheaply.

Decision: **OkHttp stays the default path; a challenged request is replayed once through a hidden
`WebViewTransport`** — a real browser engine on the same IP, with its User-Agent forced to the
app's UA so any `cf_clearance` it earns stays valid. Confirmed working on device: details loaded
end to end.

Cost: a challenged page costs ~6–8 s instead of ~0.3 s. **Cookie handoff:** after a successful
fallback fetch, `WebViewTransport` copies the WebView's cookies into the OkHttp jar, so the
`cf_clearance` just earned is reused and later requests stay on the fast path.
`CookieManager.getCookie` returns only `name=value`, so they are re-created host-only with path
`/` and a session lifetime — correct for `cf_clearance`, which is bound to host, IP and UA. A later
`Set-Cookie` from AO3 replaces them with its real scope.

The in-app "challenge required" state stays for the case where even the WebView fails.

Observed while testing: Cloudflare's answer varies by IP and minute. The same request returned
403-challenge, then a 200 challenge page, then a plain 200 with no challenge at all, and Chrome on
the same phone stalled where the app succeeded. So the handoff could not be forced on demand: unit
tests pin the header conversion and the jar, and `Log.d("GoonReader", "fallback ok: … cookies")`
records the count whenever a challenge does appear.

## Deliberately absent in M0

No search, no library writes, no reader. The five tabs exist and the database initialises,
which is the whole M0 definition.

## M1: AO3 client and parsers

- **One `Work` model** serves both a search blurb and a full work page; blurbs simply leave
  fields null. Two near-identical types would have meant a mapping layer for no benefit.
- **`WorkParser` and `ChapterParser` take ids as parameters.** AO3 does not render the work id
  into a canonical link, so deriving it from the page would mean guessing from a random href.
  Callers always know which work/chapter they fetched.
- **Chapter body is selected by tag name.** The chapter summary is a `blockquote.userstuff`, so
  a plain `.userstuff` lookup returns the summary instead of the text. Only the body is a `div`.
- **Chapter-level notes and summaries are parsed but untested**: the saved chapter pages have no
  chapter notes (that author used work-level notes).
  `shortcut: chapter notes stay unverified until a fixture containing them exists.`
- **Sanitiser is a second boundary, not the only one.** It drops scriptable elements and
  `javascript:` links; the reader will also run the WebView with JavaScript disabled.
- **`Ao3Transport` exists so retry/challenge logic is testable** without a server. Six tests
  drive it with a fake; no MockWebServer dependency, no network in tests.
- **User-Agent is honest** (`GoonReader/<version> (Android; unofficial AO3 reader)`) with
  browser-like `Accept` headers. If Cloudflare rejects it in the wild, the fix is evidence-led
  and belongs in `OkHttpTransport`, not scattered through parsers.
- **Cookie jar is in-memory.** `cf_clearance` and adult-content consent survive within a session
  only. `shortcut: cookies die with the process; persist in M6.`
- **Rate limiter is shared and injected**, 2 s interactive / 5 s background, serialised by a
  mutex. Tests use 0 ms so retry logic stays fast; a separate test verifies the real gap.
- **Retry policy**: 429/5xx retried twice (three attempts total) with `Retry-After` honoured
  and exponential backoff otherwise; 404 and challenges are never retried.
- **Not yet proven against the live site.** Every parser is verified against saved HTML, but no
  request has left the app yet — first real traffic will be the M2 search screen.

## M2: library

- **One `WorkRepository`, no use-case layer yet.** The PRD lists `domain/use cases`; at this size
  a second layer would only forward calls. Use cases appear when logic is shared between
  callers (update detection in M5 is the first real candidate).
- **Preview reads do not write.** `fetchWork` is read-only and `addToLibrary` persists the bundle
  parsed when the screen opened, so opening a work never leaves a row behind (PRD 4).
- **One-shot works are synthesised.** A single-chapter work has no `/navigate` index, so the
  chapter list falls back to one `ChapterRef` whose id is the work id — which is what AO3 uses.
  A multi-chapter work whose index fails to load becomes a `Failed`, not a fake single chapter.
- **Refresh preserves user state.** Read flags, progress, bookmarks and downloads survive a
  refresh; only chapters absent before are stamped `fetchedAt`, because the Updates feed reads
  that field. A unit test pins this — its first version was wrong and would have flooded Updates
  with every chapter on the first refresh.
- **Removing a work deletes the row** (chapters cascade). A hidden `inLibrary = 0` row would
  accumulate forever and its read state is not worth keeping for a work the user removed.
- **Stats columns added to `works`** (`kudos`, `hits`, `bookmarks`, `commentCount`). PRD 8 omits
  them from the data model but PRD 9.6 renders them in the header. The schema is still version 1;
  this was a dev reinstall, not a migration. Real migrations start at first release.
- **The "+ open by link" dialog is not in the PRD.** It exists because M2 has no way to reach a
  work until Browse lands in M4. It stays afterwards — pasting a link is the normal way to add a
  work you already have open in a browser.
- **Bottom bar hides on detail routes** rather than living inside a nested graph. Cheaper, and
  the reader will need the same behaviour.
- **Unread count and progress are computed in SQL** (`LibraryRow`), so the list never loads a
  work's chapters to render a row.
- **Chapter notes are not persisted**; the work row has no notes column in PRD 8. Work-level
  notes still reach the details screen live but are lost from the database. Revisit when the
  reader needs to show them from cache.

## M3: reader

- **The cache stores HTML, not JSON.** `ChapterCodec` writes the same fragments the reader
  renders into one `<article>`, gzipped, keyed `w<workId>c<chapterId>`. The payload already is
  HTML, it compresses well, and decoding is one Jsoup parse with no extra serialisation
  dependency. Notes and summary travel with the body, so toggling them never refetches.
- **`DiskLruCache` is a dependency** (`com.jakewharton:disklrucache:2.0.2`, Apache-2.0). Writing
  journaling plus correct LRU eviction from scratch would be more code than the feature it
  serves; the PRD explicitly allows "DiskLruCache or equivalent". Cap is 40 MB and eviction is
  the library's job — a unit test proves an entry larger than the cap does not survive.
- **One interface, one engine.** `ReaderEngine` (`show`, `scrollBy`, `scrollToProgress`,
  `progress`, `destroy`) is all the screen knows; `WebViewReaderEngine` implements it over a
  `ReaderWebView` subclass, because `computeVerticalScrollRange` and friends are `protected` on
  `WebView` and deriving them from `contentHeight * scale` depends on density semantics that are
  easy to get wrong.
- **JavaScript stays off in the reader, so appearance reloads the document.** Restyling in place
  would need script to touch the DOM; instead each change re-renders the same string and reloads
  it, and the screen restores the scroll fraction it was at. The system font scale is applied to
  the font size; CSS px are density-independent, so no other conversion is involved.
- **The reader owns every gesture.** WebView touch is off; one `awaitEachGesture` loop decides
  tap vs drag, so tap zones and the left-edge brightness swipe get first refusal. A drag scrolls
  the engine directly and hands off to an exponential decay for momentum. The WebView's own
  scrolling/fling would have been free, but it cannot coexist with tap zones: the view consumes
  the `UP` event a zone tap needs.
- **Tap zones are the PRD 10.2 grid, defaulted for scroll mode.** Top row scrolls up, middle row
  opens the menu, bottom row scrolls down; the eight actions are persisted by name and a broken
  or partial stored grid falls back to the defaults. The settings dialog doubles as the preview:
  each of the nine cells shows what it will do and tapping it swaps the action.
- **Progress is written when a gesture settles and on pause**, not per frame: a DB write per
  scroll frame is pointless work. Rotation keeps the position because the screen seeds its
  restore point from the view model, and a chapter reopened later restores the stored fraction.
- **Mark-read at 95% (configurable in the settings sheet from the model, not exposed yet).**
  Reaching the end also reveals a "Next chapter" pill (PRD 10.1), which is cheaper than
  auto-advancing and leaves the choice to the reader.
- **Only the next chapter is prefetched** (PRD 7.3) and prefetch failures are swallowed: the user
  never asked for that chapter yet, so an error there must not interrupt reading.
- **Preview works are readable.** A work that is not in the library has no chapter rows, so the
  reader re-fetches the work bundle once and keeps the list in memory. Reading still writes
  nothing to the library (PRD 4); only progress/read flags are stored, and only for chapters that
  exist as rows — a preview's progress stays at zero in the database.
- **In-text links are inert.** With script off and gestures owned by the reader, a link inside
  chapter text cannot be tapped. "Open in browser" on the work details screen is the escape
  hatch, and link handling can come back with selection/highlights (P1).
- **History rows are not written yet.** PRD 10.6 asks for them, but the History tab is M6; adding
  half a feature now would leave rows nothing reads. Deferred with the tab.
- **Brightness is a window override** (`screenBrightness`), reset to the system value when the
  reader closes; the toggled "keep screen on" flag follows the same lifecycle.

## M4: browse

- **Three endpoints, all verified on the live site.** Works search is `/works/search`, tag search
  is `/tags/search`, and browsing a tag is the tag's own page plus `/works`. `work_search[...]`
  parameter names are AO3's, copied from the "Work Search" form; the captured form is fixture
  `13-search-form.html`, and `SearchFormFixtureTest` fails if AO3 renumbers or renames anything
  the app hard-codes (ratings 9–13, warnings 14–20, categories 21–24/116/2246, sort values).
- **Rating is sent as `work_search[rating_ids][]=…` although the form uses a scalar `rating_ids`.**
  Both are accepted; the array form is the one proven end-to-end (a filtered capture came back
  with all 20 results at the requested rating and echoed the array back in its own pagination).
- **No "exclude tags" filter.** The work search form has no such field — only the tag-page facet
  form does, and that posts to a different endpoint with a `tag_id`. Rather than send an
  unverified parameter, the filter sheet omits it.
- **Dates are relative because AO3's are.** `work_search[revised_at]` takes "x days/weeks/months
  ago" syntax, so the filter is a set of periods (`<7 days` for the past week) rather than a date
  picker. Word count keeps AO3's numeric syntax (`1000-5000`, `>1000`, `<5000`).
- **Languages are a curated 26 of AO3's 168**, labelled in English. The option values are AO3's
  codes, which are not always ISO 639-1: Portuguese has no `pt`, it has `ptBR` and `ptPT`. The
  fixture test asserts every id the app ships is still an option.
- **Tags are never built by hand.** The tag picker searches `/tags/search` and then uses the
  `href` from each result verbatim (`/tags/...` + `/works`). That matters: AO3 escapes `/` as
  `*s*`, `.` as `*d*`, `#` as `*h*`, `&` as `*a*` inside tag names — a hand-built path for
  "Harry Potter - J. K. Rowling" 404s (tested). Synonym tags also redirect to their canonical
  tag; OkHttp follows that, so paging keeps working.
- **Paging is Paging 3 over a plain `PagingSource`** (no Room, no RemoteMediator): 20 results a
  page, `prefetchDistance = 3`. "More pages" is inferred from a full page because the two result
  layouts disagree on markup — search pages have a `rel=next` link, tag pages only number their
  links. An empty page ends the list.
- **One request per page, paced by the shared `RateLimiter`.** Device logs show consecutive page
  fetches exactly 2 s apart, which is the interactive delay, not a bug.
- **A new search resets the list.** The results pane is keyed by a search id, otherwise Compose
  kept the previous scroll offset and the new query opened mid-list.
- **Preview stays read-only.** Browse never writes; the "In library" pill is driven by an id-only
  `Flow` from the work table, so adding a work elsewhere updates results without a refetch.
- **Cloudflare detection was narrowed.** It used to treat any body containing `challenge-platform`
  as a challenge; ordinary AO3 pages carry Cloudflare's beacon script too, so every good 200 was
  being replayed through the (slower) WebView. It now keys on 403 or the interstitial's own text.
  The saved fixtures that contain the beacon stopped tripping the detector as a side effect.
- **Fixtures 13–15 were captured through the app itself.** A temporary intent hook in
  `MainActivity` ran the URL through `Ao3Client` (OkHttp, then the WebView fallback) and wrote the
  HTML to the app cache; the hook was removed before the milestone shipped. This is the only way
  to capture pages from this machine, and it keeps the fixtures byte-identical to what the app
  actually parses.

## M5: updates and downloads

- **Update detection costs one request when nothing changed.** A check fetches the work page and
  compares AO3's published chapter count and updated date with the stored values; the `/navigate`
  request (and the chapter writes) only happen when one of them moved. Stats and metadata are
  upserted on every check, so kudos counts and tags stay fresh for free.
- **`lastCheckedAt` is the throttle, not a "last seen" log.** It is written on add (the work was
  just fetched), on manual refresh and on every successful check. A *failed* check leaves it
  alone, so an offline library is retried on the next run instead of being skipped for 12 h.
- **Automatic checks are capped at 10 works per run, manual checks are not.** At 5 s per request
  a background run stays inside WorkManager's execution window; a manual "check now" is the user
  asking, so it walks the whole library at the interactive 2 s gap. Interval is 12 h, hardcoded
  until the Settings screen exists in M6 (`shortcut:` in the code).
- **WorkManager owns the schedule, not the check.** `UpdateScheduler` enqueues one unique
  periodic request with `KEEP` (relaunching the app must not move the schedule); every piece of
  policy lives in `UpdateChecker`, which the Updates tab's manual refresh calls directly.
- **The app provides WorkManager's `Configuration`.** `GoonReaderApp` implements
  `Configuration.Provider` with the Hilt worker factory and the manifest removes the default
  startup initializer. Without both, `@HiltWorker` constructs a worker with no dependencies.
- **The rate limiter has two gaps, and one instance.** `awaitTurn(background)` picks 2 s or 5 s
  from the same shared serialised queue, so a background batch and an open chapter cannot stack
  requests. `Ao3Client` also holds a mutex across the request itself: the limiter only spaces out
  starts, and a WebView challenge replay can take seconds.
- **A check in flight cannot resurrect a removed work.** Both refresh and check re-test
  `inLibrary` inside the write transaction. A check takes seconds; removing the work in that
  window must win, not be undone by the worker's upsert.
- **Downloads live under `filesDir/downloads`, not the LRU cache.** Plain gzipped files with the
  same `ChapterCodec` payload as the cache, written to a temp name and renamed. The cache may
  evict whatever it likes without touching them, which is the "downloads are never evicted"
  promise; `ChapterRepository` resolves download → cache → network so a downloaded chapter needs
  no network at all.
- **The download queue is one serial in-app batch.** A chapter already in the store is adopted
  (flag set from the file) rather than refetched. Progress is a `StateFlow` with the current
  chapter, a count, failures and cancel/retry. The file is the source of truth; `isDownloaded`
  only exists so lists can draw a tick.
  `shortcut: the queue dies with the app process; a foreground service + notification (PRD 9.9)
  is deferred until downloads can outlive the screen.`
- **Downloads require library membership.** A preview's chapters have no rows, so there is
  nowhere to record a flag; the download menu is hidden until the work is added.
- **Removing a work deletes its downloads and cached text.** The rows cascade; without deleting
  the files the app would keep invisible copies, and re-adding the same work would serve old text
  as current. A download batch for that work is cancelled before the rows go.
- **"Refresh work" now drops cached chapter text.** PRD 7.4 exists because an author can edit a
  chapter without changing the count, and a cache hit would hide that edit. Downloads are the
  user's copy of a chapter and are deliberately left alone; the feed only stamps chapters that
  were absent before.
- **The Updates feed is a query, not a table.** `chapters.fetchedAt` already marked "new on a
  check"; the tab joins the work title and groups by `fetchedAt` day in the device's time zone
  (epoch millis, not the date-only conversion used for AO3 published dates). It is capped at 200
  rows: a recent-activity feed, not an archive.
- **Not built in M5, deliberately:** the Updates calendar jump and filter, long-press
  multi-select, the standalone download-queue screen, download notifications, auto-download of
  new chapters and every setting that tunes the above (all M6/M8, and none is in the v1
  definition of done except offline reading, which works).

## M6: history, More, settings, categories, comments

- **App settings get their own DataStore file.** Reader appearance already had one; mixing the
  update interval into it would rewrite every reader key on a theme change.
- **The default accent keeps the hand-tuned palette.** The other accents derive roles 0..7 (the
  accent family) from their seed and leave the warm surfaces alone, so the app still looks like
  the reference screenshots. `shortcut: full seed-to-scheme generation, surfaces included, is M8.`
- **One `SettingsApplier` owns the settings that live outside Compose:** the cache cap, the
  interactive request gap and the update job. It starts in `Application.onCreate`, so persisted
  settings are in force before the first screen draws.
- **Only the update config is compared before rescheduling.** Theme, incognito and download
  toggles emit the same settings flow; without the comparison every one of them would reset the
  periodic check's timer. App start applies the persisted config with `KEEP` and a user change
  uses `UPDATE`, so relaunching never pushes the next check back.
- **Manual interval cancels the periodic job.** "Manual only" is a real setting, not a 999-hour
  interval; the Updates tab's refresh still runs a full check on demand.
- **History is one row per reading session, not per chapter.** The reader opens a row when a
  chapter loads and keeps adding the time between pauses to it, so a chapter read across three
  sittings still reads as one entry with the right duration.
- **History is written for library works only.** The row has a foreign key to `works`, and a
  preview work deliberately has no row (PRD 4: previewing writes nothing). A preview's reading
  session therefore appears in neither History nor progress, which is the same promise.
- **Incognito is checked when a session starts.** Turning it on mid-chapter leaves the row that
  was already opened; turning it off does not retroactively record anything.
- **Removing a work deletes its history rows** through the existing cascade. The History screen
  therefore never links to a work that is gone.
- **Categories: tabs are hidden when the user has none**, matching PRD 9.2's "hidden if only the
  default category exists". Assignment happens from a work's details, creation from More. There
  is no default category yet, so adding a work assigns nothing.
- **Downloaded only is one SQL predicate, not a second list.** The library query takes an
  optional category id and the flag together; Browse replaces its panes with an offline notice
  instead of pretending search works without a network.
- **Delete after reading acts on the threshold crossing**, the same moment the chapter flips to
  read. It refuses while a batch is running: deleting a file under the writer would race.
- **Auto-download of new chapters runs inside the update check.** When the setting is on, the
  chapters the check just added are queued immediately; the queue is the same in-process one, so
  it stops if the process dies.
- **Comments are the chapter page with `show_comments=true&page=N`.** AO3 normally loads them
  over an AJAX endpoint, but the full page (fixture `08-chapter-comments.html`) carries the same
  threads with working pagination and the total, so one request per page serves the whole view.
  They go through the shared client, so the limiter applies; nothing is prefetched.
- **The comment count rides with the chapter.** `Comments (741)` is on every chapter page and is
  parsed into `ChapterContent`, so the reader can badge its icon without a second request. It is
  cached with the chapter; entries cached before M6 simply show no badge.
- **Comments are not stored or cached.** They always need the network (PRD 9.10 says so), so a
  reopened view re-fetches page 1. Pages already loaded stay in memory while the screen lives.
- **The comment list shows text, not HTML.** One WebView per comment would be the only way to
  keep formatting, so paragraphs are preserved and inline markup is flattened. Indentation is
  capped at three levels per PRD 9.10; reply collapse/expand is not built yet.
- **Settings screen covers appearance, update policy, downloads and the advanced knobs.** The
  reader defaults moved to More → Reader settings, which reuses the reader's own sheet instead of
  keeping two copies of the same sliders.
- **Not built in M6, deliberately:** default category, reply collapse, user-agent suffix and the
  adult-content toggle (the app already sends `view_adult=true` everywhere), export/import
  backup, statistics, a separate Help screen, and comment caching.

## M7: reader P1

- **Paged mode is CSS multi-column, not a second renderer.** One column is one viewport wide, so
  a page is a viewport of horizontal scroll and `ReaderEngine` only has to know which axis it
  scrolls. Side margins become the column gap (`column-gap: 2 * margin`) with the same margin as
  container padding, which keeps every page's left and right margin identical. This is the
  "WebView CSS multi-column" option PRD 10.1 allows; the custom engine stays deferred.
  `shortcut: WebView text selection and per-page layout control still need the custom engine.`
- **A page turn jumps first and animates the incoming page only.** One WebView cannot paint two
  pages at once, so "slide" translates the new page in and "fade" fades it in. Doing the scroll
  synchronously also means progress is already correct when the animation starts, so the stored
  position never lags the animation.
- **Script is on in the reader now, for one reason: the TTS highlight.** The text being read has
  to be marked in the DOM, and there is no script-free way to do that. Chapter HTML is still
  sanitised before it reaches the view (scripts, event handlers, `javascript:` URLs stripped),
  no `JavascriptInterface` is registered, and every navigation is blocked. This reverses the M3
  choice and is the one security trade-off in M7; revisit if the sanitiser ever loosens.
- **TTS lives in a controller, not in the reader screen.** It owns the engine, the paragraph
  queue and the chapter fetch, so playback and "continue into the next chapter" survive the
  screen being backgrounded or destroyed. The foreground service only mirrors that state into
  the notification and stops itself when playback ends.
- **Pausing stops the engine and remembers the paragraph.** `TextToSpeech` has no pause, so
  resuming re-reads the current paragraph from its start.
- **Paragraph boundaries are produced once and shared.** `ReaderText.annotate` numbers the
  chapter blocks (`data-p="0"…`) and returns the same blocks' text, so the spoken paragraph and
  the highlighted element cannot drift. Blocks without text are skipped without shifting the
  numbering. One utterance longer than the engine's cap is truncated.
- **The highlight follows the controller, the reader does not fight the user.** The screen
  scrolls to a new TTS chapter only while it was in sync with the one just finished; opening a
  different chapter by hand stops the following, not the playback.
- **Pinch changes the font size on release, with a live scale preview.** Reloading the document
  on every pinch frame would re-lay-out the chapter dozens of times per gesture, so the WebView
  is scaled while the fingers move and the size is committed once.
- **Volume keys are routed through the activity.** Key events do not reach Compose reliably, so
  a reader that enabled the option registers a small handler that `MainActivity` consults; with
  no reader on screen the keys keep adjusting volume. Off by default.
- **Auto-scroll is a scroll-mode feature.** It moves a few dp per frame, reports position a few
  times a second (not every frame), stops at the end of the chapter, and any touch pauses it.
  The pill is the way back to running; the settings sheet is where it starts.
- **The status overlay hides when the chrome is up**, because the top bar already shows the
  chapter title and clock/battery over a title bar is noise.
- **Bookmarks are a table (schema v2), not a flag on the chapter.** A position is a chapter plus
  a fraction, and a chapter can be bookmarked more than once. `bookmarks` joins the database
  with a real `MIGRATION_1_2`; chapter number and title are copied in so the list reads without
  a join. Two bookmarks within 2% of each other count as the same spot, which is what makes the
  reader's bookmark icon a toggle.
- **The reader route gained an optional progress argument.** Opening a bookmark has to land on
  the saved spot, not the chapter's stored progress; every other entry point omits it.
- **Not built in M7, deliberately:** page-curl animation (P2, needs the custom engine), text
  highlights and notes (P2), per-work reader overrides (P2), imported fonts and custom colours
  (M8), and TTS word-level highlighting (paragraph-level only).

## M8: polish

- **The launcher icon is one adaptive layer.** The supplied art becomes a beige square with the
  portrait inside it, and the adaptive icon declares it as the *background* only. Any mask
  (circle, squircle, rounded square) therefore cuts background, never the face, and there is no
  foreground/background seam. There is no monochrome layer, so Android 13 themed icons fall back
  to the normal icon rather than showing a grey blob.
- **Custom reader colours are two swatch rows, not a colour wheel.** Background and text are
  each picked from ten options; the muted colour and rules are derived by blending text toward
  the background, and dark/light is decided from Rec. 601 luminance. That is the whole of PRD
  10.4's "custom background and text colours" without shipping an HSV picker.
- **Imported fonts travel as a `data:` URI inside the chapter document.** The WebView cannot
  read `file://` fonts (file access is off and the page's base URL is AO3), and there is no
  runtime asset pipeline. The file is copied into app storage, capped at 1.5 MB, and its base64
  is cached; the trade-off is that every appearance change re-embeds it.
  `shortcut: a big font adds ~2 MB to each render; move to a served asset if that ever shows.`
- **Backups are JSON written through the system file picker.** The platform `org.json` does the
  encoding (a test-only `org.json` dependency makes it JVM-testable; Android does not need it).
  Export covers the library: works, chapters (progress, read state), categories, membership and
  bookmarks. History is left out — it is activity, not library data, and re-importing it would
  duplicate sessions.
- **Import merges and never deletes.** Rows from the file are upserted, categories are matched
  by name because their ids are local, bookmarks within 2% of an existing one are skipped, and a
  chapter is marked downloaded only if its file is still on the device. Importing an old backup
  therefore cannot drop a newer library.
- **Statistics are one SQL row.** Reading time is `SUM(history.readDurationMs)`, so Incognito
  time is not counted; chapters read and downloads are counted over library works. No new tables,
  no tracking of its own.
- **Error states added in M8:** the reader's failure screen offers "Open on AO3" (PRD 11's
  fallback for a page the parser cannot read), chapter failures are logged with `Log.w`, and
  backup export/import report success or the actual failure in the storage screen.
- **Accessibility pass:** the reading surface exposes TalkBack custom actions (next/previous
  page, next/previous chapter, menu) because the 3x3 tap grid is unusable with a screen reader;
  the new colour swatches sit in 48dp targets; every new icon has a content description. Known
  gap: the swatches are colour-only, so they read as "colour" to a screen reader.
- **Not built in M8, deliberately:** the page-curl animation (P2, needs the custom renderer),
  text highlights and notes (P2), per-work reader overrides (P2), a Help screen (PRD 9.7 lists it
  but no milestone owns it), and user-agent suffix / adult-content toggle (still always
  `view_adult=true`).

## Motion and high refresh

- **Android 15's adaptive refresh rate treats a window as "Normal" (usually 60 Hz) unless it asks
  otherwise**, even on a 120 Hz panel. The shell requests `FrameRateCategory.High` once on the
  `Scaffold`, the reader's WebView requests `View.REQUESTED_FRAME_RATE_CATEGORY_HIGH`, and
  `MainActivity` enables `Window.setFrameRateBoostOnTouchEnabled` for touches. These are
  preferences for the rate used when a frame is drawn, not a pinned mode: a still screen draws
  nothing, and LTPO panels can still idle down. The cost is power while something is actually
  moving. Both the platform calls are API 35+ and guarded.
- **Motion is centralised in `ui/theme/Motion.kt`**: 140 ms for small changes, 200 ms for a
  surface, 280 ms for the full-screen reader, decelerating in and accelerating out. One rhythm
  instead of per-screen guesses.
- **Tabs cross-fade in place; pushed screens slide a sixth of their width** while the screen
  underneath drifts the other way, which gives depth without the old instant swap
  (`EnterTransition.None` was a 60 Hz workaround, not a design choice). The reader fades in
  because it is full screen and edge to edge.
- **System back uses the same animations as the app's own back arrow.** The manifest opts into
  predictive back, and navigation resolves predictive pops from `predictivePopEnterTransition` /
  `predictivePopExitTransition`, silently falling back to a fade/scale default instead of the
  pop specs, so those two mirror the pop transitions exactly. Per-destination overrides cannot be
  expressed for the predictive path (`composable` has no such parameters), so the reader's
  fade-only rules are repeated there.
- **Lists use `animateItem` through `animateListItem()`**: library, updates, history, bookmarks,
  categories, comments, chapters, browse results/tags and the chapter sheet. A row's divider is
  part of the animated root, otherwise the line detaches while the row moves.
- **The bottom bar pill, icon and label animate**; the reader chrome keeps its slide with
  explicit 200/140 ms specs. Page turns are 210 ms decelerating and reset alpha/translation
  before every turn, so a slide interrupted by None/Fade cannot leave the page offset.
- **Reader scrolling stays manual but is no longer per-event work.** Deltas accumulate in a float
  and only whole pixels are applied, so a slow drag is not rounded to zero every frame. Position
  is published at most every 100 ms while a gesture is live instead of on every touch event; the
  exact position is still written when the gesture settles. Auto-scroll checks the scroll extent
  five times a second rather than asking for it on every frame.
- **The TTS highlight cross-fades** with a CSS transition, turned off by
  `prefers-reduced-motion`. Compose and `ViewPropertyAnimator` already follow the system animator
  scale.
- **Not changed:** the page-curl animation still needs the custom renderer (PRD 10.1 P2).
