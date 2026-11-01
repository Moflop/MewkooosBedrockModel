param(
    [ValidateSet('vanilla', 'ar', 'sodium', 'sodium-ar', 'embeddium', 'embeddium-ar', 'sodium-ar-iris')]
    [string[]] $Profile = @('vanilla', 'ar', 'sodium', 'sodium-ar', 'embeddium', 'embeddium-ar', 'sodium-ar-iris'),
    [ValidateSet('environment', 'transitions', 'gpu', 'render', 'reload', 'all', 'collision', 'anchor', 'lifecycle', 'unload')]
    [string] $Scenario = 'environment',
    [ValidateRange(60, 3600)]
    [int] $TimeoutSeconds = 600,
    [string] $SourceWorld = '',
    [switch] $FreshWorld,
    [switch] $EngineControl,
    [switch] $EngineUnloadRequeue,
    [switch] $EngineUnloadFix,
    [ValidateRange(0, 1200)]
    [int] $TransitionSettleTicks = 60,
    [ValidateRange(1, 10)]
    [int] $TransitionCycles = 1,
    [ValidateRange(0, 29000000)]
    [int] $TransitionOrigin = 0,
    [switch] $ContinueOnFailure
)

$ErrorActionPreference = 'Stop'
if ($EngineUnloadRequeue) { $EngineControl = $true }
$repoRoot = Split-Path -Parent $PSScriptRoot
if (!$SourceWorld) { $SourceWorld = Join-Path $repoRoot 'run\saves\新的世界' }
$SourceWorld = (Resolve-Path -LiteralPath $SourceWorld).Path
if (!(Test-Path -LiteralPath (Join-Path $SourceWorld 'level.dat'))) {
    throw "No level.dat in source world: $SourceWorld"
}

$testRoot = Join-Path $repoRoot 'build\client-environment-tests'
$reportRoot = Join-Path $testRoot ('reports\' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
New-Item -ItemType Directory -Force -Path $reportRoot | Out-Null
$workerPath = Join-Path $reportRoot 'run-gradle.ps1'
# Values travel in a JSON file rather than through shell interpolation.
@'
$ErrorActionPreference = 'Stop'
$config = Get-Content -Raw -LiteralPath $env:SBM_CLIENT_RUN_CONFIG | ConvertFrom-Json
Set-Location -LiteralPath $config.repoRoot
$gradleArguments = @('--no-daemon', "-PclientTestProfile=$($config.profile)", "-PclientTestWorld=$($config.worldName)")
if ($config.engineControl) { $gradleArguments += '-PclientTestControl=true' }
if ($config.engineUnloadFix) { $gradleArguments += '-PclientTestEngineFix=true' }
& (Join-Path $config.repoRoot 'gradlew.bat') @gradleArguments runClient
exit $LASTEXITCODE
'@ | Set-Content -LiteralPath $workerPath -Encoding utf8

$savedEnvironment = @{}
foreach ($name in @('SBM_CLIENT_TEST', 'SBM_CLIENT_PROFILE', 'SBM_CLIENT_RUN_ID', 'SBM_CLIENT_RUN_CONFIG',
        'SBM_TRANSITION_SETTLE_TICKS', 'SBM_TRANSITION_CYCLES', 'SBM_TRANSITION_ORIGIN', 'SBM_CLIENT_ENGINE_FIX')) {
    $savedEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}

$results = [System.Collections.Generic.List[object]]::new()
try {
    foreach ($selectedProfile in $Profile) {
        $runId = [Guid]::NewGuid().ToString('N')
        $directoryName = if ($EngineControl) { 'control-' + $selectedProfile } else { $selectedProfile }
        if ($EngineUnloadFix) { $directoryName += '-engine-fix' }
        $selectedScenario = if ($EngineUnloadRequeue) { 'control-unload-requeue' }
            elseif ($EngineControl) { 'control-transitions' } else { $Scenario }
        $profileRoot = Join-Path $testRoot $directoryName
        $gameDir = Join-Path $profileRoot 'game'
        $worldName = if ($FreshWorld) { 'sbm-client-test-' + $runId } else { 'sbm-client-test' }
        $worldDir = Join-Path $gameDir ('saves\' + $worldName)
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $worldDir) | Out-Null
        if (!(Test-Path -LiteralPath (Join-Path $worldDir 'level.dat'))) {
            Copy-Item -LiteralPath $SourceWorld -Destination $worldDir -Recurse
        }
        # Each profile starts with only the mods selected by Gradle. User saves,
        # mod folders and shader/resource packs are never modified or imported.
        $optionsPath = Join-Path $gameDir 'options.txt'
        if (!(Test-Path -LiteralPath $optionsPath)) {
            @('fullscreen:false', 'pauseOnLostFocus:false', 'renderDistance:4',
              'simulationDistance:5', 'maxFps:60', 'enableVsync:false', 'onboardAccessibility:false') |
                Set-Content -LiteralPath $optionsPath -Encoding utf8
        }
        $existingOptions = Get-Content -LiteralPath $optionsPath
        if ($existingOptions -contains 'simulationDistance:4') {
            $existingOptions -replace '^simulationDistance:4$', 'simulationDistance:5' |
                Set-Content -LiteralPath $optionsPath -Encoding utf8
        }

        $profileReport = Join-Path $reportRoot $directoryName
        New-Item -ItemType Directory -Force -Path $profileReport | Out-Null
        $configPath = Join-Path $profileReport 'run.json'
        $started = [DateTime]::UtcNow
        $config = [ordered]@{
            runId = $runId
            profile = $selectedProfile
            scenario = $selectedScenario
            engineControl = [bool]$EngineControl
            injectedUnloadRequeue = [bool]$EngineUnloadRequeue
            engineUnloadFix = [bool]$EngineUnloadFix
            freshWorld = [bool]$FreshWorld
            worldName = $worldName
            transitionSettleTicks = $TransitionSettleTicks
            transitionCycles = $TransitionCycles
            transitionOrigin = $TransitionOrigin
            repoRoot = $repoRoot
            sourceWorld = $SourceWorld
            gameDirectory = $gameDir
            startedUtc = $started.ToString('o')
            timeoutSeconds = $TimeoutSeconds
        }
        $config | ConvertTo-Json | Set-Content -LiteralPath $configPath -Encoding utf8
        $env:SBM_CLIENT_TEST = $selectedScenario
        $env:SBM_CLIENT_PROFILE = $selectedProfile
        $env:SBM_CLIENT_RUN_ID = $runId
        $env:SBM_CLIENT_RUN_CONFIG = $configPath
        $env:SBM_TRANSITION_SETTLE_TICKS = [string]$TransitionSettleTicks
        $env:SBM_TRANSITION_CYCLES = [string]$TransitionCycles
        $env:SBM_TRANSITION_ORIGIN = [string]$TransitionOrigin
        $env:SBM_CLIENT_ENGINE_FIX = if ($EngineUnloadFix) { 'true' } else { 'false' }
        Write-Output "Running client environment profile '$selectedProfile'; evidence: $profileReport"
        $process = Start-Process -FilePath 'powershell.exe' -WindowStyle Hidden -PassThru `
            -ArgumentList @('-NoLogo', '-NoProfile', '-NonInteractive', '-File', ('"' + $workerPath + '"')) `
            -WorkingDirectory $repoRoot `
            -RedirectStandardOutput (Join-Path $profileReport 'gradle.stdout.log') `
            -RedirectStandardError (Join-Path $profileReport 'gradle.stderr.log')
        # Keep a handle while it is alive so Windows retains the exit status.
        $null = $process.Handle
        $timedOut = $false
        while (!$process.WaitForExit(1000)) {
            if (([DateTime]::UtcNow - $started).TotalSeconds -gt $TimeoutSeconds) {
                $timedOut = $true
                # Stop only the process tree created for this exact profile.
                & taskkill.exe /PID $process.Id /T /F | Out-Null
                $process.WaitForExit()
                break
            }
        }
        # Refresh() on Windows PowerShell discards the cached handle and can
        # turn a completed process's ExitCode into null; read it directly.
        $exitCode = $process.ExitCode
        $latestLog = Join-Path $gameDir 'logs\latest.log'
        $freshLog = (Test-Path -LiteralPath $latestLog) -and
            ((Get-Item -LiteralPath $latestLog).LastWriteTimeUtc -ge $started.AddSeconds(-1))
        $text = ''
        if ($freshLog) {
            Copy-Item -LiteralPath $latestLog -Destination (Join-Path $profileReport 'client.log')
            $text = Get-Content -Raw -LiteralPath $latestLog
        }
        $hasRunId = $text.Contains($runId)
        $passed = !$timedOut -and ($exitCode -eq 0) -and $freshLog -and $hasRunId -and
            ($text -match ('\[SBM_CLIENT_TEST\] PASS ' + [Regex]::Escape($selectedScenario) + '(?:\r?\n|$)')) -and
            ($text -notmatch '\[SBM_CLIENT_TEST\] FAIL')

        foreach ($name in @('clientRunVmArgs.txt', 'clientRunProgramArgs.txt', 'clientLegacyClasspath.txt')) {
            $source = Join-Path $repoRoot ('build\moddev\' + $name)
            if (Test-Path -LiteralPath $source) { Copy-Item -LiteralPath $source -Destination $profileReport }
        }
        $runtimeReport = Join-Path $profileRoot 'resolved-runtime.json'
        if (Test-Path -LiteralPath $runtimeReport) {
            Copy-Item -LiteralPath $runtimeReport -Destination $profileReport
        }
        $result = [ordered]@{
            profile = $selectedProfile
            scenario = $selectedScenario
            runId = $runId
            passed = [bool]$passed
            timedOut = $timedOut
            exitCode = $exitCode
            freshClientLog = [bool]$freshLog
            matchingRunId = $hasRunId
            startedUtc = $started.ToString('o')
            finishedUtc = [DateTime]::UtcNow.ToString('o')
            evidenceDirectory = $profileReport
        }
        $result | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $profileReport 'result.json') -Encoding utf8
        $results.Add([pscustomobject]$result)
        $results | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $reportRoot 'results.json') -Encoding utf8
        Select-String -LiteralPath (Join-Path $profileReport 'gradle.stdout.log') -Pattern '\[SBM_CLIENT_TEST\]' |
            ForEach-Object { Write-Output $_.Line }
        if (!$passed) {
            Write-Warning "Profile '$selectedProfile' failed: timeout=$timedOut exit=$exitCode freshLog=$freshLog matchingRunId=$hasRunId"
            if (!$ContinueOnFailure) { break }
        }
    }
} finally {
    foreach ($name in $savedEnvironment.Keys) {
        [Environment]::SetEnvironmentVariable($name, $savedEnvironment[$name], 'Process')
    }
}
$results | Format-Table profile, passed, timedOut, exitCode, evidenceDirectory -AutoSize
if (($results.Count -ne $Profile.Count) -or ($results | Where-Object { !$_.passed })) { exit 1 }
exit 0
