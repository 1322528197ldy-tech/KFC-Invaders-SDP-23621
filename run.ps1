param([switch]$Test)
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    New-Item -ItemType Directory -Force bin | Out-Null
    $sources = (Get-ChildItem src -Recurse -Filter *.java).FullName
    if ($Test) { $sources += (Get-ChildItem tests -Recurse -Filter *.java).FullName }
    & javac -encoding UTF-8 -Xlint:all -d bin $sources
    if ($LASTEXITCODE -ne 0) { throw 'Compilation failed' }
    if ($Test) { & java -ea '-Djava.awt.headless=true' -cp 'bin;res' engine.EndlessRunTest }
    else { & java -cp 'bin;res' engine.Core }
    if ($LASTEXITCODE -ne 0) { throw 'Java exited with an error' }
} finally { Pop-Location }
