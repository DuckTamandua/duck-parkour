# Builds EndlessParkour-<version>.jar and runs the tests (Windows PowerShell).
# Needs a JDK 25 and HytaleServer.jar from your Hytale server to compile against (it is not in this repo):
#   ./build.ps1 -Jdk <JDK 25 folder> -ServerJar <path to HytaleServer.jar>
# Without -Jdk: $env:JDK, else the JDK 25 Prism Launcher downloads, else $env:JAVA_HOME.
# Without -ServerJar: $env:SERVER_JAR, else HytaleServer.jar next to this script, else ..\hytale-testserver\Server\.
param(
    [string]$Jdk = $env:JDK,
    [string]$ServerJar = $env:SERVER_JAR,
    [switch]$SkipTests
)
# 'Continue': Windows PowerShell treats compiler warnings on stderr as errors otherwise.
$ErrorActionPreference = 'Continue'
$root = $PSScriptRoot
if (-not $Jdk) {
    $prism = if ($env:APPDATA) { Join-Path $env:APPDATA 'PrismLauncher\java\java-runtime-epsilon' } else { '' }
    $Jdk = if ($prism -and (Test-Path (Join-Path $prism 'bin\javac.exe'))) { $prism } else { $env:JAVA_HOME }
}
if (-not $Jdk) { throw 'No JDK found: pass -Jdk <JDK 25 folder> or set JDK or JAVA_HOME' }
if (-not $ServerJar) {
    $nextToScript = Join-Path $root 'HytaleServer.jar'
    $ServerJar = if (Test-Path $nextToScript) { $nextToScript } else { Join-Path $root '..\hytale-testserver\Server\HytaleServer.jar' }
}
$out = Join-Path $root 'build'
$classes = Join-Path $out 'classes'
$testClasses = Join-Path $out 'test-classes'
$javac = Join-Path $Jdk 'bin\javac.exe'
$java = Join-Path $Jdk 'bin\java.exe'
$jarTool = Join-Path $Jdk 'bin\jar.exe'
if (-not (Test-Path $javac)) { throw "No javac at $javac (set -Jdk to a JDK 25)" }
if (-not (Test-Path $ServerJar)) { throw "No HytaleServer.jar at $ServerJar (set -ServerJar)" }
Write-Host "JDK: $Jdk"
Write-Host "Server jar: $ServerJar"

$manifest = Get-Content (Join-Path $root 'src\main\resources\manifest.json') -Raw | ConvertFrom-Json
$version = $manifest.Version

Remove-Item -Recurse -Force $classes, $testClasses -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $classes, $testClasses | Out-Null

$sources = Get-ChildItem -Recurse (Join-Path $root 'src\main\java') -Filter *.java | ForEach-Object { '"' + ($_.FullName -replace '\\', '/') + '"' }
$sourceList = Join-Path $out 'sources.txt'
[IO.File]::WriteAllLines($sourceList, $sources)
& $javac --release 25 -encoding UTF-8 -Xlint:all -Xlint:-serial -Xlint:-processing -cp $ServerJar -d $classes "@$sourceList"
if ($LASTEXITCODE -ne 0) { throw 'javac failed' }
Copy-Item (Join-Path $root 'src\main\resources\manifest.json') $classes

$jar = Join-Path $out "EndlessParkour-$version.jar"
Remove-Item $jar -ErrorAction SilentlyContinue
& $jarTool --create --file $jar -C $classes .
if ($LASTEXITCODE -ne 0) { throw 'jar failed' }
Write-Host "Built $jar"

if (-not $SkipTests) {
    $tests = Get-ChildItem -Recurse (Join-Path $root 'src\test\java') -Filter *.java | ForEach-Object { '"' + ($_.FullName -replace '\\', '/') + '"' }
    $testList = Join-Path $out 'tests.txt'
    [IO.File]::WriteAllLines($testList, $tests)
    & $javac --release 25 -encoding UTF-8 -cp "$classes;$ServerJar" -d $testClasses "@$testList"
    if ($LASTEXITCODE -ne 0) { throw 'javac (tests) failed' }
    foreach ($test in 'dev.endlessparkour.course.GeneratorTest', 'dev.endlessparkour.LeaderboardTest', 'dev.endlessparkour.IslandMoverTest', 'dev.endlessparkour.TintMarginsTest') {
        & $java -cp "$testClasses;$classes;$ServerJar" $test 2>&1 | Out-Host
        if ($LASTEXITCODE -ne 0) { throw "$test failed" }
    }
}
