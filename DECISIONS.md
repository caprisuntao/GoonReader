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
- No destructive migration, ever. Version 1 ships with no migrations yet.

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

Known gap: all pages come from one fandom (Harry Potter). Fandoms with unusual characters in
tag names, series pages, collection pages and multi-author blurbs are not represented yet.

## Cloudflare

The app sends browser-like headers and persists cookies on device, and surfaces a distinct
"challenge required" state with *Open in browser*. A WebView-backed fetcher is a deliberate
non-goal until the challenge is observed in the wild.

## Deliberately absent in M0

No search, no library writes, no reader. The five tabs exist and the database initialises,
which is the whole M0 definition.
