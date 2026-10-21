<#
  drive-add-work.ps1

  Opens the app's "Open a work" dialog, types a work id, and taps Open.
  Used for manual end-to-end checks against the live site; not part of the build.

  Requires: adb on PATH or -Adb, an authorised device, and the app installed.

  Usage:
    pwsh -File tools/drive-add-work.ps1 -WorkId 4701869 [-Screenshot path.png] [-WaitSeconds 12]
#>

param(
    [string]$Adb = "C:\Users\bernat woe\AppData\Local\Android\Sdk\platform-tools\adb.exe",
    [string]$Serial = "",
    [string]$WorkId = "4701869",
    [string]$Screenshot = "",
    [int]$WaitSeconds = 12
)

# adb writes normal output to stderr, so Stop would abort on success messages.
$ErrorActionPreference = "Continue"
if (-not $Serial) {
    $Serial = (& $Adb devices | Select-String -Pattern "^\S+\s+device$" | Select-Object -First 1).Line.Split("`t")[0]
}
if (-not $Serial) { throw "No authorised device. Enable USB debugging and re-run." }

$script:ui = ""

function Dump-Ui {
    & $Adb -s $Serial shell uiautomator dump /sdcard/ui.xml 2>&1 | Out-Null
    & $Adb -s $Serial pull /sdcard/ui.xml "$env:TEMP\dsh-ui.xml" 2>&1 | Out-Null
    $script:ui = [System.IO.File]::ReadAllText("$env:TEMP\dsh-ui.xml", [System.Text.Encoding]::UTF8)
}

function Find-Center([string]$pattern) {
    foreach ($node in [regex]::Matches($script:ui, '<node[^>]+>')) {
        if ($node.Value -match $pattern -and $node.Value -match 'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"') {
            return @(
                [int](([int]$matches[1] + [int]$matches[3]) / 2),
                [int](([int]$matches[2] + [int]$matches[4]) / 2)
            )
        }
    }
    return $null
}

function Tap-Node([string]$pattern, [string]$what) {
    $point = Find-Center $pattern
    if (-not $point) { throw "Could not find $what on screen." }
    & $Adb -s $Serial shell input tap $point[0] $point[1] 2>&1 | Out-Null
}

& $Adb -s $Serial shell am force-stop app.goonreader 2>&1 | Out-Null
# CLEAR_TASK: otherwise the app restores its last route, which may not be the library.
& $Adb -s $Serial shell am start -n "app.goonreader/.MainActivity" -f 0x00008000 2>&1 | Out-Null
Start-Sleep -Seconds 3

Dump-Ui
Tap-Node 'content-desc="Open a work"' "+ button"
Start-Sleep -Milliseconds 1500

Dump-Ui
Tap-Node 'class="android.widget.EditText"' "work id field"
Start-Sleep -Milliseconds 1200

& $Adb -s $Serial shell input text $WorkId 2>&1 | Out-Null
Start-Sleep -Milliseconds 1500

Dump-Ui
Tap-Node 'text="Open"' "Open button"
Write-Host "Requested work $WorkId; waiting ${WaitSeconds}s"
Start-Sleep -Seconds $WaitSeconds

if ($Screenshot) {
    & $Adb -s $Serial shell screencap -p /sdcard/goon_drive.png 2>&1 | Out-Null
    & $Adb -s $Serial pull /sdcard/goon_drive.png $Screenshot 2>&1 | Out-Null
    Write-Host "Screenshot: $Screenshot"
}
