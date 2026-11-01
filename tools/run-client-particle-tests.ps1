param(
    [ValidateSet('all', 'collision', 'anchor', 'lifecycle', 'unload', 'transitions', 'gpu', 'render', 'reload')]
    [string] $Scenario = 'all'
)

$repoRoot = Split-Path -Parent $PSScriptRoot
$sourceGameDir = Join-Path $repoRoot 'run'
$testGameDir = Join-Path $repoRoot 'build\client-particle-test'
$testSavesDir = Join-Path $testGameDir 'saves'
$worldName = 'sbm-client-test'
$sourceWorld = Join-Path $sourceGameDir 'saves\新的世界'
$testWorld = Join-Path $testSavesDir $worldName
$sourceArgs = Join-Path $repoRoot 'build\moddev\clientRunProgramArgs.txt'
$testArgs = Join-Path $testGameDir 'clientRunProgramArgs.txt'

if (!(Test-Path (Join-Path $sourceWorld 'level.dat'))) {
    throw "Source client save not found: $sourceWorld"
}
if (!(Test-Path $sourceArgs)) {
    throw "NeoForge client arguments not found. Run a Gradle configuration task first: $sourceArgs"
}

New-Item -ItemType Directory -Force -Path $testSavesDir | Out-Null
if (!(Test-Path (Join-Path $testWorld 'level.dat'))) {
    Copy-Item -LiteralPath $sourceWorld -Destination $testWorld -Recurse
}

foreach ($folder in @('mods', 'config', 'resourcepacks', 'shaderpacks')) {
    $source = Join-Path $sourceGameDir $folder
    $destination = Join-Path $testGameDir $folder
    if ((Test-Path $source) -and !(Test-Path $destination)) {
        Copy-Item -LiteralPath $source -Destination $destination -Recurse
    }
}

$argumentLines = Get-Content -LiteralPath $sourceArgs
for ($index = 0; $index -lt $argumentLines.Count - 1; $index++) {
    if ($argumentLines[$index] -eq '--gameDir') {
        $argumentLines[$index + 1] = $testGameDir
        break
    }
}
Set-Content -LiteralPath $testArgs -Value $argumentLines -Encoding ascii

$sourceOptions = Join-Path $sourceGameDir 'options.txt'
$testOptions = Join-Path $testGameDir 'options.txt'
if (!(Test-Path $testOptions) -and (Test-Path $sourceOptions)) {
    $options = Get-Content -LiteralPath $sourceOptions
    $options = $options -replace '^fullscreen:true$', 'fullscreen:false'
    Set-Content -LiteralPath $testOptions -Value $options -Encoding utf8
}

$previousScenario = $env:SBM_CLIENT_TEST
$previousRunId = $env:SBM_CLIENT_RUN_ID
$runId = [Guid]::NewGuid().ToString('N')
$started = [DateTime]::UtcNow
$env:SBM_CLIENT_TEST = $Scenario
$env:SBM_CLIENT_RUN_ID = $runId
try {
    $argsFileArgument = '@' + $testArgs
    $gameArgs = "$argsFileArgument --quickPlaySingleplayer $worldName"
    & (Join-Path $repoRoot 'gradlew.bat') --no-daemon runClient "--args=$gameArgs"
    $gradleExitCode = $LASTEXITCODE
} finally {
    $env:SBM_CLIENT_TEST = $previousScenario
    $env:SBM_CLIENT_RUN_ID = $previousRunId
}

$logCandidates = @(
    (Join-Path $testGameDir 'logs\latest.log'),
    (Join-Path $sourceGameDir 'logs\latest.log')
) | Where-Object { (Test-Path $_) -and ((Get-Item $_).LastWriteTimeUtc -ge $started.AddSeconds(-1)) } |
    Sort-Object { (Get-Item $_).LastWriteTime } -Descending
$logCandidates = @($logCandidates)
if (!$logCandidates) {
    Write-Error 'No client log was produced.'
    exit 1
}
if (!(Select-String -LiteralPath $logCandidates[0] -SimpleMatch $runId)) {
    Write-Error 'The client log does not belong to this test run.'
    exit 1
}

$testMarkers = Select-String -LiteralPath $logCandidates[0] -Pattern '\[SBM_CLIENT_TEST\] (PASS|FAIL)'
$testMarkers | ForEach-Object { Write-Output $_.Line }
if ($testMarkers | Where-Object { $_.Line -match '\[SBM_CLIENT_TEST\] FAIL' }) {
    exit 1
}
if (!($testMarkers | Where-Object { $_.Line -match "\[SBM_CLIENT_TEST\] PASS $Scenario$" })) {
    Write-Error "Client exited without reporting PASS for '$Scenario'."
    exit 1
}

exit $gradleExitCode
