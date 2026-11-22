<#
    build-audit-archive.ps1

    Collects nd-audit-YYYYMMDD.zip with facts about the project for an external review.
    Read-only over the sources: nothing is edited, deleted or renamed.

    Usage:
        powershell -ExecutionPolicy Bypass -File .\tools\build-audit-archive.ps1
        powershell -ExecutionPolicy Bypass -File .\tools\build-audit-archive.ps1 -SkipGradle
        powershell -ExecutionPolicy Bypass -File .\tools\build-audit-archive.ps1 -SkipLint
        powershell -ExecutionPolicy Bypass -File .\tools\build-audit-archive.ps1 -KeepStage

    Output: <project root>\nd-audit-<YYYYMMDD>.zip

    NOTE: console messages are ASCII on purpose. Windows PowerShell 5.1 reads a .ps1
    saved without a BOM as ANSI, so Cyrillic literals break the parser.
#>

[CmdletBinding()]
param(
    [switch]$SkipGradle,
    [switch]$SkipLint,
    [switch]$KeepStage
)

$ErrorActionPreference = 'Stop'

# ---------------------------------------------------------------- location

$ProjectRoot = Split-Path -Parent $PSScriptRoot
if (-not (Test-Path (Join-Path $ProjectRoot 'settings.gradle.kts'))) {
    $ProjectRoot = (Get-Location).Path
}
if (-not (Test-Path (Join-Path $ProjectRoot 'settings.gradle.kts'))) {
    throw 'settings.gradle.kts not found. Run this script from the netoverdose project root.'
}

$Stamp   = Get-Date -Format 'yyyyMMdd'
$Stage   = Join-Path ([System.IO.Path]::GetTempPath()) "nd-audit-stage-$Stamp"
$Reports = Join-Path $Stage 'reports'
$ZipPath = Join-Path $ProjectRoot "nd-audit-$Stamp.zip"

Write-Host "Project root : $ProjectRoot"
Write-Host "Staging      : $Stage"
Write-Host "Archive      : $ZipPath"
Write-Host ''

if (Test-Path $Stage) { Remove-Item $Stage -Recurse -Force }
New-Item -ItemType Directory -Force -Path $Reports | Out-Null

$Missing  = New-Object System.Collections.Generic.List[string]
$Included = New-Object System.Collections.Generic.List[string]
$Commands = New-Object System.Collections.Generic.List[string]

# ---------------------------------------------------------------- helpers

function Copy-Tree {
    param(
        [Parameter(Mandatory)][string]$Source,
        [Parameter(Mandatory)][string]$Destination,
        [string[]]$Include = @('*'),
        [string[]]$Exclude = @()
    )
    if (-not (Test-Path $Source)) {
        $script:Missing.Add($Source)
        return
    }
    $files = @(Get-ChildItem -Path $Source -Recurse -File -Include $Include -ErrorAction SilentlyContinue)
    if ($Exclude.Count -gt 0) {
        $files = @($files | Where-Object {
            $skip = $false
            foreach ($pattern in $Exclude) { if ($_.Name -like $pattern) { $skip = $true; break } }
            -not $skip
        })
    }
    foreach ($file in $files) {
        $relative = $file.FullName.Substring($Source.Length).TrimStart('\')
        $target   = Join-Path $Destination $relative
        $dir      = Split-Path -Parent $target
        if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
        Copy-Item $file.FullName $target -Force
    }
    $script:Included.Add(("{0} ({1} files)" -f $Source, $files.Count))
}

function Copy-Single {
    param([Parameter(Mandatory)][string]$Relative)
    $source = Join-Path $ProjectRoot $Relative
    if (-not (Test-Path $source)) { $script:Missing.Add($Relative); return }
    $target = Join-Path $Stage $Relative
    $dir = Split-Path -Parent $target
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
    Copy-Item $source $target -Force
    $script:Included.Add($Relative)
}

function Write-Report {
    param(
        [Parameter(Mandatory)][string]$Name,
        [AllowEmptyCollection()][object[]]$Lines
    )
    $path = Join-Path $Reports $Name
    if ($null -eq $Lines -or $Lines.Count -eq 0) {
        Set-Content -Path $path -Value '__NO MATCHES__' -Encoding UTF8
    } else {
        Set-Content -Path $path -Value $Lines -Encoding UTF8
    }
    $count = if ($null -eq $Lines) { 0 } else { $Lines.Count }
    Write-Host ("  report {0,-22} {1} lines" -f $Name, $count)
}

function Invoke-Captured {
    param(
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][string]$Command
    )
    $path = Join-Path $Reports "cmd-$Name.txt"
    Write-Host "  command: $Command"
    $code = -1
    $output = ''
    # Native commands may write to stderr (git warnings, gradle progress). Under
    # ErrorActionPreference=Stop that surfaces as a terminating NativeCommandError,
    # which would lose the output. Capture with Continue instead.
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = (Invoke-Expression "$Command 2>&1" | Out-String)
        $code = $LASTEXITCODE
    } catch {
        $output = "DID NOT RUN: $($_.Exception.Message)"
        $code = -1
    } finally {
        $ErrorActionPreference = $previous
    }
    if ($null -eq $code) { $code = -1 }
    $header = @(
        "COMMAND: $Command",
        "EXIT CODE: $code",
        ("TIME: " + (Get-Date -Format 'yyyy-MM-dd HH:mm:ss')),
        ('-' * 70)
    )
    Set-Content -Path $path -Value ($header + $output) -Encoding UTF8
    $script:Commands.Add("$Name -> exit $code")
    Write-Host "    exit code: $code"
}

# ---------------------------------------------------------------- 1. sources

Write-Host '[1/6] Copying sources'
Copy-Tree -Source (Join-Path $ProjectRoot 'app\src')    -Destination (Join-Path $Stage 'app\src')    -Include @('*.kt')
Copy-Tree -Source (Join-Path $ProjectRoot 'domain\src') -Destination (Join-Path $Stage 'domain\src') -Include @('*.kt')

# ---------------------------------------------------------------- 2. resources and build

Write-Host '[2/6] Copying resources and build files'
Copy-Single 'app\src\main\AndroidManifest.xml'

$resDir = Join-Path $ProjectRoot 'app\src\main\res'
if (Test-Path $resDir) {
    Get-ChildItem -Path $resDir -Directory -Filter 'values*' -ErrorAction SilentlyContinue |
        ForEach-Object {
            Copy-Tree -Source $_.FullName `
                      -Destination (Join-Path $Stage "app\src\main\res\$($_.Name)") `
                      -Include @('*.xml')
        }
} else {
    $Missing.Add('app\src\main\res')
}

Copy-Tree -Source (Join-Path $ProjectRoot 'app\src\main\res\xml') -Destination (Join-Path $Stage 'app\src\main\res\xml') -Include @('*.xml')

Copy-Single 'settings.gradle.kts'
Copy-Single 'build.gradle.kts'
Copy-Single 'gradle.properties'
Copy-Single 'app\build.gradle.kts'
Copy-Single 'domain\build.gradle.kts'
Copy-Single 'gradle\libs.versions.toml'
Copy-Single 'gradle\wrapper\gradle-wrapper.properties'
Copy-Single 'app\proguard-rules.pro'

Copy-Tree -Source (Join-Path $ProjectRoot 'app\schemas') -Destination (Join-Path $Stage 'app\schemas') -Include @('*.json')

$binaryLines = New-Object System.Collections.Generic.List[string]
foreach ($dir in @('font', 'drawable')) {
    $full = Join-Path $resDir $dir
    if (-not (Test-Path $full)) { $Missing.Add("app\src\main\res\$dir"); continue }
    Get-ChildItem $full -Recurse -File | ForEach-Object {
        $binaryLines.Add(("{0} : {1} bytes" -f $_.FullName.Substring($ProjectRoot.Length + 1), $_.Length))
    }
}
if (Test-Path $resDir) {
    Get-ChildItem $resDir -Directory -Filter 'mipmap*' -ErrorAction SilentlyContinue |
        ForEach-Object {
            Get-ChildItem $_.FullName -Recurse -File | ForEach-Object {
                $binaryLines.Add(("{0} : {1} bytes" -f $_.FullName.Substring($ProjectRoot.Length + 1), $_.Length))
            }
        }
}
Write-Report -Name 'binaries.txt' -Lines $binaryLines.ToArray()

# ---------------------------------------------------------------- 3. reports

Write-Host '[3/6] Building reports'

$sourceRoots = @(
    (Join-Path $ProjectRoot 'app\src'),
    (Join-Path $ProjectRoot 'domain\src')
) | Where-Object { Test-Path $_ }

$ktFiles = @()
foreach ($root in $sourceRoots) {
    $ktFiles += @(Get-ChildItem $root -Recurse -File -Filter '*.kt' -ErrorAction SilentlyContinue)
}
if ($ktFiles.Count -eq 0) { throw 'No .kt files found. Check the project root.' }
$ktPaths = $ktFiles.FullName

function Get-RelativePath {
    param([string]$Full)
    return $Full.Substring($ProjectRoot.Length + 1)
}

function Select-Source {
    param([Parameter(Mandatory)][string]$Pattern)
    # @() and the explicit cast keep the result a string[] so List.AddRange accepts it.
    return [string[]]@(Select-String -Path $ktPaths -Pattern $Pattern -AllMatches -ErrorAction SilentlyContinue |
        ForEach-Object { "{0}:{1}: {2}" -f (Get-RelativePath $_.Path), $_.LineNumber, $_.Line.Trim() })
}

# --- tree.txt
$treeLines = $ktFiles |
    ForEach-Object { [pscustomobject]@{ Path = $_.FullName; Lines = @(Get-Content $_.FullName).Count } } |
    Sort-Object Lines -Descending |
    ForEach-Object {
        $mark = if ($_.Lines -gt 500) { '  <<< MORE THAN 500 LINES' } else { '' }
        "{0}:{1}: {2}{3}" -f (Get-RelativePath $_.Path), $_.Lines, (Get-RelativePath $_.Path), $mark
    }
Write-Report -Name 'tree.txt' -Lines @($treeLines)

# --- expected.txt
$expectedNames = @(
    'AppRoot', 'MainActivity', 'NetoverdoseApp', 'Database', 'TaskRepository',
    'BackupRepository', 'ExportJson', 'ExportWriter', 'ExportReader', 'Design', 'Color',
    'Type', 'Theme', 'Components', 'StageComponents', 'MainScreen', 'TaskCardScreen',
    'IntakeSheet', 'HorizonScreen', 'StatsScreen', 'MoreScreen', 'LabScreen',
    'IdeasScreen', 'JournalScreen', 'DaySheet', 'StubScreen', 'CalendarMonthView',
    'Reminders', 'TaskRules', 'WindowProgress', 'TimeText', 'DeadlineOptions',
    'Progress', 'AutoDifficulty', 'CalendarGrid'
)
$expectedLines = foreach ($name in $expectedNames) {
    $hit = $ktFiles | Where-Object { $_.BaseName -eq $name } | Select-Object -First 1
    if ($hit) {
        "{0} : PRESENT : {1} lines : {2}" -f $name, @(Get-Content $hit.FullName).Count, (Get-RelativePath $hit.FullName)
    } else {
        "$name : ABSENT"
    }
}
Write-Report -Name 'expected.txt' -Lines @($expectedLines)

Write-Report -Name 'nav.txt' -Lines @(Select-Source 'popUpTo|navigate\(|NavHost|BackHandler|onBackPressed|NAVDEBUG|launchSingleTop|restoreState|saveState')
Write-Report -Name 'db-export.txt' -Lines @(Select-Source '@Database|version\s*=|SCHEMA_VERSION|schemaVersion|MIGRATION_|fallbackToDestructive|orderIndex|sprintStartedAt|sprintEndsAt')
Write-Report -Name 'deadcode.txt' -Lines @(Select-Source 'resetDemoData|seedDemo|writeDemoAttachment|DemoOpen|DemoClosed|demo_|StubScreen|backup_rules|data_extraction_rules')

$fontLines = New-Object System.Collections.Generic.List[string]
$fontLines.AddRange([string[]]@(Select-Source 'R\.font|getIdentifier|FontFamily|fontKey'))
$fontLines.Add('')
$fontDir = Join-Path $resDir 'font'
if (Test-Path $fontDir) {
    $fontLines.Add('--- res/font listing ---')
    Get-ChildItem $fontDir -File | ForEach-Object { $fontLines.Add(("{0} : {1} bytes" -f $_.Name, $_.Length)) }
} else {
    $fontLines.Add('--- res/font : ABSENT ---')
}
Write-Report -Name 'fonts.txt' -Lines $fontLines.ToArray()

# --- touch.txt
$touchLines = New-Object System.Collections.Generic.List[string]
foreach ($file in $ktFiles) {
    $number = 0
    foreach ($line in (Get-Content $file.FullName)) {
        $number++
        $outer = [regex]::Matches($line, '(height|width|size|heightIn|widthIn|sizeIn|defaultMinSize|minHeight|minWidth)\s*\(([^)]*)\)')
        $found = $false
        foreach ($match in $outer) {
            foreach ($dp in [regex]::Matches($match.Groups[2].Value, '(\d+(?:\.\d+)?)\.dp')) {
                if ([double]$dp.Groups[1].Value -lt 44) {
                    $touchLines.Add(("{0}:{1}: {2}" -f (Get-RelativePath $file.FullName), $number, $line.Trim()))
                    $found = $true
                    break
                }
            }
            if ($found) { break }
        }
    }
}
Write-Report -Name 'touch.txt' -Lines $touchLines.ToArray()

# --- fontsize.txt
$sizeLines = New-Object System.Collections.Generic.List[string]
foreach ($file in $ktFiles) {
    $number = 0
    foreach ($line in (Get-Content $file.FullName)) {
        $number++
        $matches11 = [regex]::Matches($line, 'fontSize\s*=\s*(\d+(?:\.\d+)?)\.sp')
        foreach ($match in $matches11) {
            if ([double]$match.Groups[1].Value -lt 11) {
                $sizeLines.Add(("{0}:{1}: {2}" -f (Get-RelativePath $file.FullName), $number, $line.Trim()))
                break
            }
        }
    }
}
Write-Report -Name 'fontsize.txt' -Lines $sizeLines.ToArray()

# --- strings-in-code.txt (Cyrillic literals)
$cyrillicPattern = '"[^"]*[\u0400-\u04FF][^"]*"'
$cyrillicLines = New-Object System.Collections.Generic.List[string]
foreach ($file in $ktFiles) {
    $number = 0
    foreach ($line in (Get-Content $file.FullName)) {
        $number++
        if ($line -match $cyrillicPattern) {
            $cyrillicLines.Add(("{0}:{1}: {2}" -f (Get-RelativePath $file.FullName), $number, $line.Trim()))
        }
    }
}
Write-Report -Name 'strings-in-code.txt' -Lines $cyrillicLines.ToArray()

# --- eventtype.txt
$eventLines = New-Object System.Collections.Generic.List[string]
$rulesFile = $ktFiles | Where-Object { $_.Name -eq 'TaskRules.kt' } | Select-Object -First 1
if ($rulesFile) {
    $eventLines.Add("--- enum from $(Get-RelativePath $rulesFile.FullName) ---")
    $eventLines.AddRange([string[]]@(Get-Content $rulesFile.FullName | Where-Object { $_ -match 'enum class EventType' }))
} else {
    $eventLines.Add('--- TaskRules.kt : ABSENT ---')
}
$eventLines.Add('')
$eventLines.Add('--- EventType. occurrences ---')
$eventLines.AddRange([string[]]@(Select-Source 'EventType\.'))
Write-Report -Name 'eventtype.txt' -Lines $eventLines.ToArray()

# --- design.txt
$designLines = New-Object System.Collections.Generic.List[string]
$designFile = $ktFiles | Where-Object { $_.Name -eq 'Design.kt' } | Select-Object -First 1
if ($designFile) {
    $all = @(Get-Content $designFile.FullName)
    $blocks = @(
        @{ Name = 'data class DesignTokens'; Pattern = '^data class DesignTokens' },
        @{ Name = 'object DesignPresets';    Pattern = '^object DesignPresets' },
        @{ Name = 'fun urgency()';           Pattern = 'fun urgency\(' }
    )
    foreach ($block in $blocks) {
        $designLines.Add("--- $($block.Name) ---")
        $index = 0
        while ($index -lt $all.Count -and $all[$index] -notmatch $block.Pattern) { $index++ }
        if ($index -ge $all.Count) {
            $designLines.Add('NOT FOUND')
            $designLines.Add('')
            continue
        }
        $depth = 0
        $started = $false
        for ($i = $index; $i -lt $all.Count; $i++) {
            $text = $all[$i]
            $designLines.Add($text)
            foreach ($ch in $text.ToCharArray()) {
                if ($ch -eq '(' -or $ch -eq '{') { $depth++; $started = $true }
                elseif ($ch -eq ')' -or $ch -eq '}') { $depth-- }
            }
            if ($started -and $depth -le 0) { break }
        }
        $designLines.Add('')
    }
} else {
    $designLines.Add('Design.kt : ABSENT')
}
Write-Report -Name 'design.txt' -Lines $designLines.ToArray()

# ---------------------------------------------------------------- 4. commands

Write-Host '[4/6] Running commands'
Push-Location $ProjectRoot
$headHash = 'NOT A GIT REPO'
$headShort = 'n/a'
try {
    if (-not $SkipGradle) {
        $gradlew = if (Test-Path '.\gradlew.bat') { '.\gradlew.bat' } else { 'gradlew' }
        Invoke-Captured -Name 'gradlew-v' -Command "$gradlew -v"
        Invoke-Captured -Name 'tests'     -Command "$gradlew :domain:test :app:testDebugUnitTest --console=plain"
        Invoke-Captured -Name 'assemble'  -Command "$gradlew :app:assembleDebug --console=plain"
        if ($SkipLint) {
            $Commands.Add('lint -> SKIPPED by -SkipLint')
        } else {
            Invoke-Captured -Name 'lint' -Command "$gradlew :app:lintDebug --console=plain"
        }
    } else {
        $Commands.Add('gradle commands -> SKIPPED by -SkipGradle')
    }

    Invoke-Captured -Name 'git-status'    -Command 'git status --porcelain'
    Invoke-Captured -Name 'git-log'       -Command 'git log --oneline -20'
    Invoke-Captured -Name 'git-diff-stat' -Command 'git diff --stat'
    Invoke-Captured -Name 'git-diff'      -Command 'git diff'
    Invoke-Captured -Name 'git-show-head' -Command 'git show HEAD'

    $hash = git rev-parse HEAD 2>$null
    if ($LASTEXITCODE -eq 0 -and $hash) {
        $headHash = $hash
        $headShort = git rev-parse --short HEAD 2>$null
    }
} finally {
    Pop-Location
}

# ---------------------------------------------------------------- 5. manifest

Write-Host '[5/6] Writing MANIFEST.txt'

$manifest = New-Object System.Collections.Generic.List[string]
$manifest.Add('NETOVERDOSE - ARCHIVE FOR EXTERNAL REVIEW')
$manifest.Add(('=' * 60))
$manifest.Add("Project root : $ProjectRoot")
$manifest.Add("Built at     : $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')")
$manifest.Add("git HEAD     : $headHash ($headShort)")
$manifest.Add("Archive      : nd-audit-$Stamp.zip")
$manifest.Add('')
$manifest.Add('--- INCLUDED ---')
foreach ($item in $Included) { $manifest.Add("  + $item") }
$manifest.Add('')
$manifest.Add('--- NOT FOUND / ABSENT ---')
if ($Missing.Count -eq 0) {
    $manifest.Add('  (all expected paths found)')
} else {
    foreach ($item in $Missing) { $manifest.Add("  - $item : ABSENT") }
}
$manifest.Add('')
$manifest.Add('--- COMMANDS ---')
foreach ($item in $Commands) { $manifest.Add("  $item") }
$manifest.Add('')
$manifest.Add('--- FILTER RULES ---')
$manifest.Add('  Excluded: local.properties, keystore.properties, *.jks, *.keystore,')
$manifest.Add('            build/, .gradle/, .git/, .idea/, captures/,')
$manifest.Add('            *.png, *.webp, *.jpg, *.ttf, *.otf, *.jar, *.so, *.apk')
$manifest.Add('  Binaries under res/ are listed in reports/binaries.txt (name and size only).')
$manifest.Add('  Sources were not modified: the archive was built by reading only.')
Set-Content -Path (Join-Path $Stage 'MANIFEST.txt') -Value $manifest -Encoding UTF8

# ---------------------------------------------------------------- 6. package

Write-Host '[6/6] Packaging'

$problemPath = Join-Path $Stage 'PROBLEM.txt'
if (-not (Test-Path $problemPath)) {
    @(
        'FILL IN MANUALLY: what does not work right now.',
        '',
        'Format: exact error text or reproduction steps.',
        'Facts only, no hypotheses, no fix proposals.',
        '',
        'Known defect at the time of the build:',
        '  System Back from a sub-screen (More -> Fonts) closes the app instead of',
        '  returning to More. Reproduced on emulator and on device.'
    ) | Set-Content -Path $problemPath -Encoding UTF8
}

$forbidden = Get-ChildItem $Stage -Recurse -File -ErrorAction SilentlyContinue | Where-Object {
    $_.Name -match '^(local\.properties|keystore\.properties)$' -or
    $_.Extension -in @('.jks', '.keystore', '.png', '.webp', '.jpg', '.ttf', '.otf', '.jar', '.so', '.apk')
}
if ($forbidden) {
    Write-Host 'STOP: forbidden files reached the staging folder:' -ForegroundColor Red
    $forbidden | ForEach-Object { Write-Host "  $($_.FullName)" }
    throw 'Fix the filters and run again. Archive was not created.'
}

if (Test-Path $ZipPath) { Remove-Item $ZipPath -Force }
Compress-Archive -Path (Join-Path $Stage '*') -DestinationPath $ZipPath -CompressionLevel Optimal

$reportCount = @(Get-ChildItem $Reports -File).Count
if (-not $KeepStage) {
    Remove-Item $Stage -Recurse -Force -ErrorAction SilentlyContinue
    Write-Host 'Staging removed.'
} else {
    Write-Host "Staging kept: $Stage"
}

$sizeMb = [math]::Round((Get-Item $ZipPath).Length / 1MB, 2)
$failed = @($Commands | Where-Object { $_ -match 'exit -?\d+$' -and $_ -notmatch 'exit 0$' }).Count
$skipped = @($Commands | Where-Object { $_ -match 'SKIPPED' }).Count

Write-Host ''
Write-Host "DONE: $ZipPath ($sizeMb MB)" -ForegroundColor Green
Write-Host "Reports: $reportCount"
Write-Host "Commands with non-zero exit: $failed"
Write-Host "Commands skipped: $skipped"
Write-Host ''
Write-Host 'Next: run without -SkipGradle to include build output, then fill PROBLEM.txt.'
