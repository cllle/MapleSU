param(
    [string]$SourcePath,
    [string]$OutputDirectory,
    [switch]$OwnerOnly
)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '../../../../..')).Path
if (!$SourcePath) { $SourcePath = Join-Path $root 'manager/src/main/java/com/maplesu/core/ui/util/SELinuxChecker.kt' }
if (!$OutputDirectory) { $OutputDirectory = Join-Path $root '.tmp/selinux-owner-scope' }
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$cache = Join-Path $env:USERPROFILE '.gradle/caches/modules-2/files-2.1'
function Jar($module, $version) {
    $found = @(Get-ChildItem -LiteralPath (Join-Path $cache "$module/$version") -Filter '*.jar' -Recurse)
    if ($found.Count -ne 1) { throw "Expected one jar: $module/$version" }
    return $found[0].FullName
}
$compiler = Jar 'org.jetbrains.kotlin/kotlin-compiler-embeddable' '2.4.20'
$stdlib = Jar 'org.jetbrains.kotlin/kotlin-stdlib' '2.4.20'
$annotations = Jar 'org.jetbrains/annotations' '13.0'
$coroutines = Jar 'org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm' '1.11.0'
$junit = Jar 'junit/junit' '4.13.2'
$hamcrest = Jar 'org.hamcrest/hamcrest-core' '1.3'
$java = (Get-Command java).Source
$sources = @($SourcePath, (Join-Path $root 'manager/src/test/java/com/maplesu/core/ui/util/SELinuxCheckerTest.kt')) +
    @(Get-ChildItem -LiteralPath $PSScriptRoot -Filter '*.kt' | ForEach-Object FullName)
$classes = Join-Path $OutputDirectory 'classes'
$compile = @('-Xmx512m', '-cp', ($compiler,$stdlib,$annotations,$coroutines -join ';'),
    'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect', '-nowarn', '-jvm-target', '17',
    '-classpath', ($stdlib,$annotations,$junit,$hamcrest -join ';'), '-d', $classes) + $sources
& $java @compile 2>&1 | Tee-Object -FilePath (Join-Path $OutputDirectory 'compile.log')
$compileExit = $LASTEXITCODE
if ($compileExit -ne 0) { exit $compileExit }
$runArgs = @('-cp', ($classes,$stdlib -join ';'), 'com.maplesu.core.ui.util.OwnerStatusHarnessKt')
if ($OwnerOnly) { $runArgs += '--owner-only' }
& $java @runArgs 2>&1 | Tee-Object -FilePath (Join-Path $OutputDirectory 'behavior.log')
$testExit = $LASTEXITCODE
$junitExit = $null
if (!$OwnerOnly -and $testExit -eq 0) {
    & $java '-cp' ($classes,$stdlib,$junit,$hamcrest -join ';') 'org.junit.runner.JUnitCore' `
        'com.maplesu.core.ui.util.SELinuxCheckerTest' 2>&1 | Tee-Object -FilePath (Join-Path $OutputDirectory 'junit.log')
    $junitExit = $LASTEXITCODE
}
[ordered]@{
    compileExit = $compileExit
    testExit = $testExit
    existingJUnitExit = $junitExit
    sourcePath = (Resolve-Path -LiteralPath $SourcePath).Path
    sourceSha256 = (Get-FileHash -LiteralPath $SourcePath -Algorithm SHA256).Hash.ToLowerInvariant()
    boundaryScope = 'Full production checker; SDK, signed snapshot, Hook, clock and Android process are fixtures. Local-file cases use actual temp files. No Android/SELinux/device execution.'
} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $OutputDirectory 'RESULT.json') -Encoding utf8
if ($junitExit -and $junitExit -ne 0) { exit $junitExit }
exit $testExit
