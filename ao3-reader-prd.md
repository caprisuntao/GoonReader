# PRD: AO3 Reader (working title)

**Version:** 0.2 (draft) | **Audience:** AI coding agent | **Platform:** Native Android

---

## 1. Overview

A native Android app for reading fanfiction from Archive of Our Own (AO3). It combines two things:

- **Mihon-style app shell:** simple bottom-nav layout (Library, Updates, History, Browse, More), a persistent library, update tracking, optional downloads.
- **Moon Reader-style reading experience:** a highly customizable reader (tap zones, themes, fonts, gestures, TTS, page modes).

**Core problem being solved:** existing workflows require re-downloading a fic (e.g. an EPUB) every time it updates. This app tracks fics in a library, detects new chapters, and fetches only the new chapters. It is an unofficial client and has no backend of its own.

## 2. Goals and non-goals

### Goals
1. Browse and search AO3 inside the app with AO3's own filters.
2. Maintain a persistent library of works with reading progress.
3. Detect updates and fetch only new chapters.
4. Read through a short-lived disk cache by default, so storage stays small.
5. Offer optional per-chapter / per-work downloads for offline reading.
6. Provide a reader with Moon Reader-level customization.
7. Keep the app UI simple, fast, and visually close to Mihon.
8. Let users read chapter comments (read-only) without leaving the app.

### Non-goals (do NOT build)
- Manga/image reading, multiple sources, extension/plugin system (Mihon has these; we do not).
- Posting works, posting comments or replies, leaving kudos (the app is read-only in v1; *reading* comments is in scope).
- Any backend, accounts, cloud sync, analytics, ads, or telemetry.
- Scraping AO3 in bulk or crawling the site.

## 3. Platform and constraints

- Android only. **Min SDK 26 (Android 8.0)**, target the latest stable SDK.
- Kotlin only. UI in Jetpack Compose (Material 3).
- Works offline for downloaded chapters; browsing and uncached chapters need internet.
- No data collection. No required permissions beyond INTERNET, network state, foreground service (downloads/updates), and notifications.

## 4. Library vs. cache vs. downloads (critical concept)

| Layer | What it stores | Lifetime | Storage |
|---|---|---|---|
| **Library** | Work metadata, chapter list, read state, progress, categories | Permanent until user removes it | Room DB (small) |
| **Chapter cache** | Sanitized chapter text for recently opened chapters | Auto-evicted (LRU) | Disk LRU cache, size-capped |
| **Downloads** | Sanitized chapter text the user explicitly downloaded | Permanent until user deletes | App files dir (gzip HTML), tracked in DB |

Rules:
- Opening any chapter (library or not) goes through the cache. Nothing is permanently stored just because it was read.
- Adding a work to the library stores **metadata and the chapter list only**, not chapter text.
- Downloaded chapters are never evicted by the cache.
- The reader must work from a `Chapter` object and not care whether content came from cache, download, or network.

## 5. Tech stack

| Concern | Choice |
|---|---|
| Language / UI | Kotlin, Jetpack Compose, Material 3 |
| Architecture | MVVM, unidirectional state (StateFlow), repository pattern |
| DI | Hilt (Koin acceptable) |
| Networking | OkHttp (`HttpUrl.Builder` for URL building) |
| HTML parsing | Jsoup |
| Database | Room |
| Chapter cache | DiskLruCache (or equivalent LRU file cache) |
| Background work | WorkManager (library updates, downloads) |
| Async | Kotlin Coroutines + Flow |
| Paging | Paging 3 (search/browse results) |
| Reader (v1) | WebView behind a `ReaderEngine` interface |
| TTS | Android `TextToSpeech` |
| Preferences | DataStore |
| Testing | JUnit, Turbine, Compose UI tests, Jsoup parser tests on saved HTML fixtures |

## 6. Architecture

Single Gradle module to start, with clear package layering:

```
data/
  network/    AO3Client (OkHttp), RateLimiter, UrlBuilder
  parser/     SearchParser, WorkParser, ChapterParser  (Jsoup)
  db/         Room entities, DAOs, migrations
  cache/      ChapterCache, DownloadStore
  repo/       WorkRepository, LibraryRepository, UpdateRepository, HistoryRepository
domain/       models, use cases (AddToLibrary, CheckUpdates, GetChapterContent, ...)
ui/
  library/ updates/ history/ browse/ details/ more/ settings/ reader/
  theme/ components/
work/         UpdateWorker, DownloadWorker
```

Key interfaces:
- `ChapterContentSource.getChapter(workId, chapterId): ChapterContent` resolves download, then cache, then network.
- `ReaderEngine` abstracts rendering (WebView now, custom paginated engine later). The Reader UI must only talk to this interface.

## 7. AO3 integration (scraping)

AO3 has no official API. All data comes from fetching and parsing HTML. **Verify every URL pattern and CSS selector against the live site while building, and keep all of them in one place** so they are easy to fix when AO3 changes markup.

### 7.1 URL patterns (verify before relying on them)
- **Search:** `https://archiveofourown.org/works/search?work_search[query]=...&work_search[sort_column]=...&work_search[sort_direction]=...&work_search[fandom_names]=...&work_search[rating_ids]=...&work_search[word_count]=...&page=N`
- **Browse a tag:** `https://archiveofourown.org/works?tag_id=<tag>&work_search[sort_column]=revised_at&page=N`
- **Work:** `/works/{workId}`; **chapter:** `/works/{workId}/chapters/{chapterId}`; **chapter index:** `/works/{workId}/navigate`
- **Adult-content interstitial:** handle it (e.g. `view_adult=true`) so mature works load.
- **Tag encoding gotcha:** AO3 encodes special characters in tag URLs (e.g. `/` becomes `*s*`, `&` becomes `*a*`, others exist). **Do not hand-encode tags.** Take the tag link `href` straight from parsed AO3 pages whenever possible.
- A results page returns 20 works.
- **Chapter comments:** a chapter page can include its comments via a query param (believed to be `show_comments=true`, with a `page` param for comment pagination). Verify the exact params and the comment HTML structure on the live site.

### 7.2 Data to parse
- **Work blurb (search/browse results):** work id, title, author(s), fandoms, rating, warnings, categories, relationships, characters, additional tags, summary, language, word count, chapters (`x/y`, `y` may be `?`), complete flag, updated date, kudos, hits, bookmarks, comments.
- **Work page / chapter index:** full chapter list (chapter id, number, title, date).
- **Chapter page:** chapter title, chapter summary, start notes, body, end notes.
- **Comments (per chapter):** comment id, author name (or guest), posted date, body (sanitized HTML), parent comment / nesting depth, and whether replies exist. Comments are threaded: top-level comments contain nested replies. Also parse comment page count / total if available.
- **Sanitize before storing or caching:** keep only the content we render; strip scripts, trackers, and site chrome.

### 7.3 Rate limiting and politeness (mandatory)
- Single shared `RateLimiter`: serialize requests and enforce a minimum delay between requests (default 2s for user-initiated, 5s for background batches; configurable in Advanced settings).
- Send a descriptive `User-Agent` including app name and version.
- Handle HTTP 429 and "Retry later" pages with exponential backoff, honoring `Retry-After`.
- **Never** fire a request per keystroke. Search only on submit.
- Background update checks: cap per run, skip works checked recently, require network constraints, and never sweep the full library more often than the user-configured interval (default 12h, with a manual option).
- Prefetch only the next chapter of the work currently being read.
- Comments are heavier than chapter text: load them only when the user opens the comments view, one page at a time. Never prefetch them and never include them in background update checks.

### 7.4 Update detection
- Compare AO3's current published chapter count (and updated date) with the stored value.
- If it increased, fetch the chapter list and fetch only the new chapters' metadata; add them to the DB and the Updates feed. Chapter text is cached/downloaded on demand (or auto-downloaded if the user enabled "download new chapters").
- Provide a **"Refresh work"** action that re-fetches all chapters of that work, because authors can edit existing chapters without changing the chapter count. Do not auto-recheck old chapters.

## 8. Data model (Room)

- **Work:** id (AO3 id, PK), title, authors, fandoms, rating, warnings, categories, relationships, characters, tags, summary, language, wordCount, chaptersPublished, chaptersTotal (nullable), isComplete, publishedAt, updatedAt, inLibrary, addedAt, lastCheckedAt.
- **Chapter:** id (AO3 chapter id, PK), workId (FK), number, title, publishedAt, fetchedAt, isRead, progress (0.0 to 1.0), bookmarked, isDownloaded.
- **Category / WorkCategory:** user-defined library categories (many-to-many).
- **History:** workId, chapterId, lastReadAt, readDurationMs.
- **SavedSearch (P1):** name, serialized filter state.
- **ReaderBookmark / Highlight (P1):** chapterId, position, note, color.
- **Comments are not stored in the DB.** They live only in the short-lived cache (same LRU cache, separate key space) and are re-fetched on demand.

Updates feed = chapters in library works ordered by `fetchedAt` (new chapters detected by update checks), grouped by day.

## 9. App UI (Mihon-style)

### 9.1 Global style
- Material 3, **dark theme by default** (also Light, System, AMOLED black). Warm dark-brown surfaces with a peach/orange accent, matching the reference screenshots (approximate: near-black brown background, brown pill for the selected nav item, salmon/peach accent text and icons). Derive the palette from an orange-brown M3 seed color; do not hardcode pixel colors.
- Large top app bars with the screen title on the left and action icons on the right. Thin dividers between sections. Minimal chrome, no clutter.
- **Bottom navigation, 5 tabs, in this order:** Library, Updates, History, Browse, More. Selected tab uses a filled pill indicator.

### 9.2 Library
- List of works in the library (AO3 has no cover art, so **do not use a cover grid**). Each row shows: title, author, fandom (first one), progress as `read/total` chapters, unread-count badge, rating and complete/ongoing icon.
- Optional compact grid mode using generated color tiles (first letter), P2.
- Category tabs across the top (hidden if only the default category exists).
- Actions: search within library, filter (unread, downloaded, completed, ongoing, rating), sort (title, last read, last updated, date added, unread count), multi-select (download, mark read/unread, change category, remove).
- Tap opens Work details; "Continue reading" shortcut per row (P1).

### 9.3 Updates
- Header text: **"Library last updated: X minutes ago"**.
- Entries grouped by day (Today, 3 days ago, ...), each showing work title and new chapter label (e.g. `Ch. 12 - Title`), an unread dot, and a **download icon** on the right (shows progress or a check when downloaded).
- Top bar actions: filter, calendar (jump to date), refresh (run update check now).
- Tap an entry to open that chapter in the reader; long-press for multi-select (mark read, download).

### 9.4 History
- Entries grouped by day, each showing work title, `Ch. N - time read`, and a delete icon.
- Top bar: search, clear-all history.
- Tap resumes reading at the saved position.
- Respect Incognito mode (no history written while it is on).

### 9.5 Browse
Replaces Mihon's Sources/Extensions/Migrate (we have one source: AO3).
- Top: search field and a filter icon.
- Below: quick entry points: **Latest** (sort by updated), **Most kudos**, **Browse by fandom/tag** (type a tag, pick from results), **Saved searches** (P1).
- Filter sheet maps to AO3 search params: any-field query, title, author, fandom, rating, warnings, categories, relationships, characters, additional tags, complete/ongoing, word count range, language, date updated, sort column/direction, crossovers.
- Results: infinite-scroll list via Paging 3, 20 per page, each result shows the blurb fields from 7.2. Works already in the library are marked.
- Tap opens Work details. Preview must not write anything permanent.
- Show clear empty, loading, offline, and rate-limited states.

### 9.6 Work details
- Header: title, author(s), rating/warnings/category icons, stats (words, chapters, kudos, hits), updated date, status.
- Collapsible sections: summary, relationships, characters, tags, series info.
- Buttons: **Add to library / In library**, **Download** (menu: all, unread, next N, delete downloaded), **Refresh work**, **Open in browser**, Share link.
- **Chapter list:** chapter number, title, date, read state (dimmed when read), bookmark flag, per-chapter download indicator. Sorting asc/desc. Long-press for multi-select (mark read, download).
- Floating "Continue reading" / "Start reading" button.

### 9.7 More
Mirrors the reference screen:
- **Downloaded only** toggle (filters library to downloaded content and limits Browse to offline state).
- **Incognito mode** toggle (pauses reading history).
- Download queue, Categories, Statistics (P2), Data and storage, Settings, About, Help.
- Omit "Support Us".

### 9.8 Settings
- **Appearance:** theme (System/Light/Dark/AMOLED), accent color.
- **Reader:** defaults for everything in section 10.
- **Library:** update interval (manual, 6h, 12h, daily, weekly), constraints (Wi-Fi only, charging only), auto-download new chapters toggle, default category.
- **Downloads:** delete after reading toggle, download-ahead count.
- **Advanced:** chapter cache size limit (default 100 MB, options 50/100/200/500 MB), clear cache, network delay, user-agent suffix, adult-content handling.
- **Data and storage:** storage used (library, cache, downloads) with clear buttons, **export/import library backup** as JSON (P1).

### 9.9 Download queue
List of queued/in-progress/failed chapter downloads with pause, resume, cancel, retry. Runs as a foreground service via WorkManager with a progress notification.

### 9.10 Chapter comments (P1, read-only)
- **Entry points:** a Comments action on each chapter (Work details) and a comments icon in the reader's bottom bar; show the count if known.
- Opens as a bottom sheet or full-screen view for that chapter.
- **Threaded list:** top-level comments with nested replies indented (cap visual indentation at about 3 levels, then flatten). Each comment shows author, date, and body. Replies can be collapsed/expanded per thread.
- Load one page at a time with infinite scroll or a "Load more" button; show a skeleton loading state; cache briefly.
- **States:** no comments yet, comments disabled or restricted, rate-limited, and offline. Comments always need internet, even for downloaded chapters; say so clearly.
- No reply, post, edit, or kudos actions. Offer **Open in browser** on the view for anything beyond reading.
- Must go through the shared rate limiter from 7.3.

## 10. Reader (Moon Reader-inspired)

The reader is the product's main differentiator. Behavior should feel like Moon Reader: full-screen, distraction-free, and heavily customizable.

### 10.1 Reading modes
- **Scroll mode (P0):** continuous vertical scroll through a chapter.
- **Paged mode (P1):** horizontal pages (WebView CSS multi-column or custom engine).
- **Page-turn animations (P1/P2):** none, slide (P1), fade (P1), curl (P2, requires the custom engine).
- Chapters flow into each other: reaching the end offers/loads the next chapter, and the next chapter is prefetched.

### 10.2 Tap zones (P0)
- The screen is a configurable **3x3 grid**; each zone maps to an action.
- Actions: next page/scroll down, previous page/scroll up, open menu, next chapter, previous chapter, toggle TTS, none.
- **Defaults:** left column = previous, right column = next, center = open menu. In scroll mode, top row scrolls up and bottom row scrolls down.
- A settings screen lets the user remap each zone, with a preview grid.

### 10.3 Gestures
- Vertical swipe on the left edge adjusts **screen brightness** (P0), with an on-screen indicator.
- Pinch to change font size (P1).
- Optional volume-key page turning (P1).
- Long-press text for selection (copy, highlight, dictionary lookup) (P1/P2).

### 10.4 Appearance (P0 unless noted)
- **Themes:** Day, Night, Sepia, AMOLED black, plus custom background and text colors (custom is P1).
- **Font:** system fonts plus user-imported font files (TTF/OTF) (import is P1).
- Font size, line spacing, paragraph spacing, text alignment (left/justify), side margins and top/bottom margins.
- Toggle: show/hide AO3 author notes and chapter summary.
- Per-work override of reader settings (P2).

### 10.5 Reader chrome
- Immersive full-screen by default; tapping the menu zone toggles a top bar (work title, chapter title, back, bookmark) and a bottom bar (chapter slider, previous/next chapter, settings, TTS, brightness, comments (P1)).
- Optional status overlay: chapter title, progress %, clock, battery (toggleable) (P1).
- Orientation lock option and "keep screen on" toggle (P0).
- Chapter list sheet to jump between chapters.

### 10.6 Progress
- Save scroll/page position per chapter continuously (debounced) and on exit; restore exactly on reopen.
- Mark a chapter read when the user reaches the end (threshold configurable, default 95%).
- Write History entries (unless Incognito is on).

### 10.7 Text-to-speech (P1)
- Play/pause, next/previous paragraph, speed and voice selection, sleep timer.
- Runs as a foreground media-style service with notification controls; highlights the currently spoken paragraph.
- Continues into the next chapter automatically.

### 10.8 Auto-scroll (P1)
Adjustable speed, tap to pause/resume.

### 10.9 Bookmarks and highlights (P1/P2)
Bookmark any position, highlight selected text with a color, optional note, and a list screen per work.

### 10.10 Engine requirement
Everything above must be implemented against `ReaderEngine` so a custom paginated renderer can replace the WebView later **without changing the UI layer**.

## 11. Non-functional requirements

- **Performance:** cold start under 2s on mid-range hardware; chapter opens from cache in under 300ms; smooth 60fps scrolling in lists.
- **Offline:** downloaded chapters fully readable with no network; graceful offline states elsewhere.
- **Resilience:** the parser must fail gracefully (show "couldn't parse this page" and an Open in browser fallback), never crash. Parsing failures are logged locally.
- **Privacy:** no analytics, no ads, no third-party network calls except AO3. Cookies/login (if ever added) stay on device.
- **Accessibility:** respects system font scale and dark mode, content descriptions on icons, 48dp touch targets.
- **Testing:** parsers are tested against saved AO3 HTML fixtures (never hit live AO3 in automated tests); repository and update-detection logic are unit tested; core screens have Compose UI tests.

## 12. Legal and licensing

- The app is **unofficial** and must state it is not affiliated with or endorsed by AO3 or the Organization for Transformative Works. Include this in About.
- Respect AO3's Terms of Service and the rate-limiting rules in 7.3.
- **Licensing of reused code:** Mihon is Apache-2.0 (reuse allowed with attribution and notices). CO3 and other AO3 clients may be GPL-3.0; **do not copy code from GPL-licensed projects** unless this app is also released under GPL-3.0. When unsure, write it from scratch and use other projects as references only.

## 13. Milestones (suggested build order)

1. **M0, Scaffold:** project, theme, 5-tab navigation with empty screens, DI, DB skeleton.
2. **M1, AO3 client:** `AO3Client`, `RateLimiter`, parsers for search results, work, chapter list, chapter body, with fixture tests.
3. **M2, Library:** Room schema, add/remove work, Work details screen, chapter list.
4. **M3, Reader MVP:** WebView `ReaderEngine`, scroll mode, tap zones, themes, fonts/size/spacing, brightness gesture, progress saving, chapter cache, next-chapter prefetch.
5. **M4, Browse:** search, filter sheet, tag browse, Paging 3 results.
6. **M5, Updates and downloads:** update worker and detection, Updates tab, download queue, per-chapter/work downloads, Refresh work.
7. **M6, History, More, Settings:** history, incognito, downloaded-only, categories, storage screens, settings, read-only chapter comments (section 9.10).
8. **M7, Reader P1:** paged mode, slide/fade animations, volume keys, pinch zoom, status overlay, TTS, auto-scroll, bookmarks.
9. **M8, Polish:** backup/export, statistics, custom fonts/colors, curl animation (custom engine), error states, accessibility pass.

## 14. Definition of done (v1 = M0 to M6 plus reader P0)

- A user can search AO3, open a work, add it to the library, and read it with chapters loaded on demand.
- Re-opening the app shows library, progress, and history intact.
- An update check finds a newly published chapter and fetches only that chapter.
- A downloaded chapter reads fully offline and is never evicted.
- The chapter cache stays under its configured limit.
- Request rate never exceeds the configured limits, and 429s are handled with backoff.
- All P0 reader features work; no crashes on malformed or unexpected AO3 pages.
- (P1) Chapter comments open on demand as a threaded, paginated, read-only list, and never load during background updates.

## 15. Instructions for the implementing agent

- Build milestone by milestone; ensure the app compiles and runs after each one.
- Keep all AO3 URLs and CSS selectors in a single module/file with comments.
- Ask for clarification only when a requirement is genuinely ambiguous; otherwise follow this document and note assumptions in code comments or a `DECISIONS.md`.
- Do not add features listed under non-goals.
- Prefer simple, readable code over clever abstractions; keep the `ReaderEngine` boundary clean.

## 16. Open questions

- Final app name and package id.
- Release license for the app (affects what code can be reused).
- AO3 login (for bookmarks/subscriptions/restricted works): out of scope for v1, revisit after M6.
- Whether to add a compact cover-less grid mode for the Library in v1 or later.
