$ErrorActionPreference = 'Stop'
function Invoke-JavaTool {
    param([string]$Tool, [string[]]$ToolArguments, [string]$LogPath)
    $previousPreference = $ErrorActionPreference
    try {
        # Windows PowerShell treats javac's stderr warnings as errors under Stop.
        $ErrorActionPreference = 'Continue'
        & $Tool @ToolArguments *> $LogPath
        $toolExitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousPreference
    }
    if ($toolExitCode -ne 0) {
        Get-Content -LiteralPath $LogPath -Tail 20
        throw "$Tool failed (exit $toolExitCode). See $LogPath"
    }
}

$projectPath = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Push-Location -LiteralPath $projectPath
try {
    # Saves are relative to class locations; each suite gets its own disposable copy.
    $verificationPath = Join-Path $projectPath ('bin/campaign-check-' + [Guid]::NewGuid().ToString('N').Substring(0, 8))
    $classPath = Join-Path $verificationPath 'classes'
    New-Item -ItemType Directory -Force -Path $classPath | Out-Null
    $productionSources = @(Get-ChildItem -LiteralPath src -Filter '*.java' -Recurse | ForEach-Object FullName)
    $compileArguments = @('-J-Duser.language=en', '--release', '17', '-encoding', 'UTF-8', '-d', $classPath)
    Invoke-JavaTool 'javac' ($compileArguments + $productionSources) (Join-Path $verificationPath 'production-compile.log')
    $testArguments = @('-J-Duser.language=en', '--release', '17', '-encoding', 'UTF-8', '-cp', "$classPath;res",
        '-d', $classPath, 'tests/CampaignMovementTest.java', 'tests/TwoHitEnemyTest.java')
    Invoke-JavaTool 'javac' $testArguments (Join-Path $verificationPath 'test-compile.log')
    foreach ($suite in @('CampaignMovementTest', 'TwoHitEnemyTest')) {
        $suitePath = Join-Path $verificationPath $suite
        New-Item -ItemType Directory -Force -Path $suitePath | Out-Null
        Copy-Item -LiteralPath $classPath -Destination $suitePath -Recurse
        $suiteClasses = Join-Path $suitePath 'classes'
        $suiteLog = Join-Path $verificationPath ($suite + '.log')
        $runArguments = @('-Duser.language=en', '-Djava.awt.headless=true', '-cp', "$suiteClasses;res", $suite)
        Invoke-JavaTool 'java' $runArguments $suiteLog
        Get-Content -LiteralPath $suiteLog | Select-String '^PASS:' | ForEach-Object { $_.Line }
    }
    Write-Host "Verified in: $verificationPath"
} finally {
    Pop-Location
}
