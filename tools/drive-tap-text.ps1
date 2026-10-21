<#
  drive-tap-text.ps1

  Taps the first UI node whose text (or content description) matches. Used for manual
  end-to-end checks; not part of the build.

  It refuses to tap when the expected text is not on screen, which is what keeps a stale
  uiautomator dump from turning into a blind tap on whatever happens to be foreground.

  Usage:
    powershell -File tools/drive-tap-text.ps1 -Text "Chapter 1" [-Desc] [-Regex]
    powershell -File tools/drive-tap-text.ps1 -Text "Reader settings" -Desc
#>

param(
    [string]$Adb = "C:\Users\bernat woe\AppData\Local\Android\Sdk\platform-tools\adb.exe",
    [string]$Serial = "",
    [Parameter(Mandatory = $true)][string]$Text,
    [switch]$Desc,
    [switch]$Regex,
    [string]$Screenshot = "",
    [int]$SettleSeconds = 2
)

# adb writes normal output to stderr, so Stop would abort on success messages.
$ErrorActionPreference = "Continue"
if (-not $Serial) {
    $Serial = (& $Adb devices | Select-String -Pattern "^\S+\s+device$" | Select-Object -First 1).Line.Split("`t")[0]
}
if (-not $Serial) { throw "No authorised device. Enable USB debugging and re-run." }

$attribute = if ($Desc) { "content-desc" } else { "text" }
$dump = ""

for ($attempt = 1; $attempt -le 3; $attempt++) {
    & $Adb -s $Serial shell uiautomator dump /sdcard/ui.xml 2>&1 | Out-Null
    & $Adb -s $Serial pull /sdcard/ui.xml "$env:TEMP\dsh-ui.xml" 2>&1 | Out-Null
    if (Test-Path "$env:TEMP\dsh-ui.xml") {
        $dump = [System.IO.File]::ReadAllText("$env:TEMP\dsh-ui.xml", [System.Text.Encoding]::UTF8)
        if ($dump.Length -gt 200 -and $dump -match $attribute) { break }
    }
    Start-Sleep -Milliseconds 800
}

if (-not $dump) { throw "Could not read the UI hierarchy." }

$tap = $null
foreach ($node in [regex]::Matches($dump, '<node[^>]+>')) {
    $value = [regex]::Match($node.Value, "$attribute=`"([^`"]*)`"")
    if (-not $value.Success) { continue }
    $actual = $value.Groups[1].Value
    $hit = if ($Regex) { $actual -match $Text } else { $actual -eq $Text }
    if (-not $hit) { continue }

    $bounds = [regex]::Match($node.Value, 'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
    if (-not $bounds.Success) { continue }

    $tap = @{
        X = [int](([int]$bounds.Groups[1].Value + [int]$bounds.Groups[3].Value) / 2)
        Y = [int](([int]$bounds.Groups[2].Value + [int]$bounds.Groups[4].Value) / 2)
    }
    break
}

if (-not $tap) { throw "'$Text' is not on screen; nothing tapped." }

& $Adb -s $Serial shell input tap $tap.X $tap.Y 2>&1 | Out-Null
Write-Host "Tapped '$Text' at $($tap.X),$($tap.Y)"
Start-Sleep -Seconds $SettleSeconds

if ($Screenshot) {
    & $Adb -s $Serial shell screencap -p /sdcard/goon_tap.png 2>&1 | Out-Null
    & $Adb -s $Serial pull /sdcard/goon_tap.png $Screenshot 2>&1 | Out-Null
    Write-Host "Screenshot: $Screenshot"
}
