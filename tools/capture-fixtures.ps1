<#
  capture-fixtures.ps1

  One-off, rate-limited capture of real AO3 pages, saved as parser test fixtures.
  Parser tests NEVER hit the network; they read these files. Re-run this script
  by hand when AO3 changes markup, then review the resulting git diff.

  Politeness: single sequential pass, fixed delay between every request,
  descriptive User-Agent, no crawling (we never follow a page's links except to
  pick two work ids from one search page).

  Usage:
    pwsh -File tools/capture-fixtures.ps1 [-OutDir <path>] [-DelaySec <int>]

  NOTE: needs outbound network. In DSH this requires an approved
  danger-full-access command, because the normal sandbox blocks egress.
#>

param(
    [string]$OutDir = "app/src/test/resources/fixtures",
    [int]$DelaySec = 3
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

$Base = "https://archiveofourown.org"
# Identify ourselves honestly. This is not the app's runtime UA; it is a
# fixture-capture UA so AO3 sysadmins can attribute the traffic.
$UA = "GoonReader-fixture-capture/0.1 (parser test fixtures; single run; unofficial reader)"

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$Cookies = Join-Path $env:TEMP ("goonreader-fixtures-" + [guid]::NewGuid().ToString("N") + ".cookies")
$Index = [System.Collections.Generic.List[object]]::new()
$Count = 0

function Fetch {
    param(
        [Parameter(Mandatory)][string]$Url,
        [Parameter(Mandatory)][string]$Name,
        [string]$Note = "",
        [switch]$UseCookies
    )

    $script:Count++
    if ($script:Count -gt 1) { Start-Sleep -Seconds $DelaySec }

    $path = Join-Path $script:OutDir $Name
    $curlArgs = @("-sS", "-L", "--compressed", "--max-time", "45", "-A", $script:UA,
                  "-H", "Accept-Language: en-US,en;q=0.9", "-o", $path)
    if ($UseCookies) { $curlArgs += @("-b", $script:Cookies, "-c", $script:Cookies) }

    $err = & curl.exe @curlArgs $Url 2>&1
    $bytes = if (Test-Path $path) { (Get-Item $path).Length } else { 0 }
    $status = "ok"
    if ($err) { $status = "curl: $err" }

    $script:Index.Add([pscustomobject]@{
        file  = $Name
        url   = $Url
        bytes = $bytes
        note  = if ($Note) { $Note } else { "" }
        state = $status
    })

    Write-Host ("[{0,3}] {1,-34} {2,9:N0} B  {3}" -f $script:Count, $Name, $bytes, $status)
    if (Test-Path $path) { return Get-Content -Raw -LiteralPath $path }
    return ""
}

function Find-WorkIds {
    param([string]$Html)
    # Blurbs link to works as /works/<id> or /works/<id>/chapters/<id>.
    $ids = [regex]::Matches($Html, '/works/(\d+)(?=["/?#])') |
        ForEach-Object { $_.Groups[1].Value } |
        Select-Object -Unique
    return @($ids)
}

function Save-Index {
    $lines = @(
        "# AO3 parser fixtures",
        "",
        'Captured from the live site by tools/capture-fixtures.ps1.',
        "Do not hand-edit: re-run the script instead, so fixtures stay representative.",
        "",
        "Captured: $(Get-Date -Format 'yyyy-MM-dd HH:mm zzz')",
        "",
        "| File | Bytes | URL | Notes |",
        "|---|---:|---|---|"
    )
    foreach ($row in $script:Index) {
        $lines += ("| ``{0}`` | {1} | {2} | {3} |" -f $row.file, $row.bytes, $row.url, $row.note)
    }
    $lines | Set-Content -Path (Join-Path $script:OutDir "README.md") -Encoding utf8
}

try {
    Write-Host "Capturing into $OutDir (delay ${DelaySec}s)" -ForegroundColor Cyan

    # 1. Search results, sorted by kudos: the canonical blurb-list fixture.
    $search = Fetch -Url "$Base/works/search?work_search%5Bquery%5D=time%20travel&work_search%5Bsort_column%5D=kudos_count&work_search%5Bsort_direction%5D=desc" `
                    -Name "search-results-kudos.html" -Note "20 blurbs, kudos sort; source of work ids below"

    # 2. Same search, page 2: pagination parsing.
    Fetch -Url "$Base/works/search?work_search%5Bquery%5D=time%20travel&work_search%5Bsort_column%5D=kudos_count&work_search%5Bsort_direction%5D=desc&page=2" `
          -Name "search-results-page2.html" -Note "pagination; page N link shapes" | Out-Null

    # 3. A search with no hits: empty state parsing.
    Fetch -Url "$Base/works/search?work_search%5Bquery%5D=zzqqxx%20no%20such%20text%20anywhere" `
          -Name "search-results-empty.html" -Note "no results; 'no results' message markup" | Out-Null

    # 4. Tag browse page (tag landing page shape, not search).
    Fetch -Url "$Base/tags/Harry%20Potter%20-%20J.%20K.%20Rowling/works" `
          -Name "tag-browse.html" -Note "tag works listing; tag link href shape" | Out-Null

    $ids = Find-WorkIds -Html $search
    Write-Host ("Found {0} work ids on the search page: {1}" -f $ids.Count, ($ids -join ", "))
    if ($ids.Count -lt 2) { throw "Search page yielded too few work ids; AO3 markup likely changed." }

    # 5. Multi-chapter work page (metadata, tags, chapter list on work page).
    $workUrl = "$Base/works/$($ids[0])?view_adult=true"
    $work = Fetch -Url $workUrl -Name "work.html" -Note "work id $($ids[0]); header, tags, stats, chapter index" -UseCookies

    # 6. Chapter index page: the authoritative chapter list.
    Fetch -Url "$Base/works/$($ids[0])/navigate" -Name "work-navigate.html" -Note "full chapter list: id, number, title, date" -UseCookies | Out-Null

    # 7. A chapter page: title, summary, notes, body.
    $chapterIds = [regex]::Matches($work, "/works/$($ids[0])/chapters/(\d+)") |
        ForEach-Object { $_.Groups[1].Value } | Select-Object -Unique
    if ($chapterIds.Count -eq 0) { throw "No chapter link found on work page; cannot fetch chapter fixture." }
    $chapterId = $chapterIds[0]
    $chapterUrl = "$Base/works/$($ids[0])/chapters/$chapterId?view_adult=true"
    Fetch -Url $chapterUrl -Name "chapter.html" -Note "chapter $chapterId; summary, start notes, body, end notes" -UseCookies | Out-Null

    # 8. Same chapter with comments requested: threaded comment markup.
    Fetch -Url "$chapterUrl&show_comments=true" -Name "chapter-comments.html" `
          -Note "comments: threading, depth, author, date, pagination" -UseCookies | Out-Null

    # 9. Mature work behind the adult-content interstitial. Deliberately uses a
    #    fresh cookie jar so the warning page itself is captured.
    $explicit = [regex]::Match($search, 'rating-explicit[\s\S]{0,4000}?href="/works/(\d+)')
    if ($explicit.Success) {
        Fetch -Url "$Base/works/$($explicit.Groups[1].Value)" -Name "adult-interstitial.html" `
              -Note "adult content warning page (no view_adult cookie)" | Out-Null
        Fetch -Url "$Base/works/$($explicit.Groups[1].Value)?view_adult=true" -Name "work-mature.html" `
              -Note "explicit work via view_adult=true" | Out-Null
    } else {
        Write-Host "!! no explicit-rated work found in search page; skipping interstitial fixtures" -ForegroundColor Yellow
    }

    # 10. A single-chapter work, if one is visible in the first few results.
    foreach ($id in $ids[1..([Math]::Min(5, $ids.Count - 1))]) {
        $candidate = Fetch -Url "$Base/works/$id?view_adult=true" -Name "work-single-chapter.html" `
                            -Note "single-chapter work $id; no chapter index table" -UseCookies
        if ($candidate -match 'id="chapters"\s+class="chapter') { break }
    }

    # 11. Error page: work id that does not exist.
    Fetch -Url "$Base/works/1" -Name "work-not-found.html" -Note "404/'This work could not be found'" | Out-Null

    # 12. Rate-limit / retry page is not reproducible on demand; recorded here so
    #     the parser has a fixture when one is captured by hand.
    Write-Host "Note: no rate-limit fixture captured (429 only happens under load)." -ForegroundColor DarkGray

    Save-Index
    Write-Host ""
    Write-Host "Done. $(($Index | Where-Object { $_.state -eq 'ok' -and $_.bytes -gt 0 }).Count) non-empty files in $OutDir" -ForegroundColor Green
}
finally {
    if (Test-Path $Cookies) { Remove-Item $Cookies -Force -ErrorAction SilentlyContinue }
}
