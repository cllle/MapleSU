param(
    [Parameter(Mandatory)][string]$Java,
    [Parameter(Mandatory)][string]$CompilerClassPath,
    [Parameter(Mandatory)][string]$DependencyClassPath,
    [Parameter(Mandatory)][string]$ProductionClassPath,
    [Parameter(Mandatory)][string]$OutputDirectory,
    [string]$Node = 'node'
)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../../../../..')).Path
$source = Join-Path $root 'manager/src/main/java'
$output = [IO.Path]::GetFullPath($OutputDirectory)
if (Test-Path -LiteralPath $output) { throw "Use a fresh output directory: $output" }
New-Item -ItemType Directory -Path $output | Out-Null
$classes = Join-Path $output 'classes'
$attempts = [Collections.Generic.List[object]]::new()

function Write-JavaArgs([string]$Name, [string[]]$Arguments) {
    $path = Join-Path $output "$Name.args"
    $Arguments | ForEach-Object { '"' + $_.Replace('\', '\\').Replace('"', '\"') + '"' } |
        Set-Content -LiteralPath $path -Encoding utf8NoBOM
    return $path
}
function Invoke-Java([string]$Name, [string[]]$Arguments) {
    $argsFile = Write-JavaArgs $Name $Arguments
    & $Java "@$argsFile" *> (Join-Path $output "$Name.log")
    $code = $LASTEXITCODE
    $attempts.Add([ordered]@{name=$Name;exitCode=$code;argsFile=$argsFile})
    if ($code -ne 0) { Get-Content -LiteralPath (Join-Path $output "$Name.log"); throw "$Name exited $code" }
}
function Invoke-Node([string]$Name, [string[]]$Arguments) {
    & $Node @Arguments *> (Join-Path $output "$Name.log")
    $code = $LASTEXITCODE
    $attempts.Add([ordered]@{name=$Name;exitCode=$code;args=$Arguments})
    if ($code -ne 0) { Get-Content -LiteralPath (Join-Path $output "$Name.log"); throw "$Name exited $code" }
}

$web = Join-Path $source 'com/maplesu/core/ui/webui'
$lite = Join-Path $source 'dev/maplesu/manager/liteweb'
$production = @('WebViewInterface.kt','WebUIDownloadInterface.kt','WebUiSessionHost.kt',
    'WebUiBridgeDispatcher.kt','ExternalWebUiBridge.kt','RootWebUiApps.kt','BlobDownloadHandler.kt',
    'Download.kt','WebUIResourceCleanup.kt','NormalWebUiCompatibility.kt') | ForEach-Object { Join-Path $web $_ }
$production += Get-ChildItem -LiteralPath (Join-Path $web 'file') -Filter '*.kt' -File |
    Sort-Object Name | Select-Object -ExpandProperty FullName
$production += Join-Path $web 'pm/KsuPm.kt'
$production += @('LiteWebCore.kt','LiteWebHandler.kt','LiteWebModWeb.kt','LiteWebModuleBridge.kt',
    'LiteWebExtensions.kt','LiteWebPreview.kt','daemon/DaemonLiteWebBackend.kt','daemon/LiteWebDaemonMain.kt') |
    ForEach-Object { Join-Path $lite $_ }
$production += Join-Path $source 'dev/maplesu/manager/modules/webui/ModuleWebUiSessions.kt'
$sources = @($production) + @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'fixtures') -Filter '*.kt' -File |
    Sort-Object Name | Select-Object -ExpandProperty FullName) + @('BrowserBridgeFixtureServer.kt',
    'ExternalWebUiBridgeLifecycleTest.kt','RootWebUiAppsTest.kt' | ForEach-Object { Join-Path $PSScriptRoot $_ }) +
    @(Join-Path $root 'manager/src/test/java/dev/maplesu/manager/liteweb/LiteWebModuleBridgeTest.kt')
$inputs = [Collections.Generic.List[object]]::new()
$snapshots = for ($i=0; $i -lt $sources.Count; $i++) {
    $path=$sources[$i];$directory=Join-Path $output "sources/$i"
    New-Item -ItemType Directory -Path $directory | Out-Null
    $snapshot=Join-Path $directory ([IO.Path]::GetFileName($path))
    Copy-Item -LiteralPath $path -Destination $snapshot
    $inputs.Add([ordered]@{path=$path;sha256=(Get-FileHash -LiteralPath $snapshot -Algorithm SHA256).Hash.ToLowerInvariant()})
    $snapshot
}
@('manager/src/main/assets/liteweb/a/modbridge.js','third_party/kernelsu/upstream/js/index.js',
    'manager/src/test/resources/webui-browser-jvm/verify.cjs',
    'manager/src/test/resources/webui-browser-jvm/verify-event-order.cjs') | ForEach-Object {
    $path=Join-Path $root $_
    $inputs.Add([ordered]@{path=$path;sha256=(Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()})
}
$inputs | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $output 'INPUTS.json') -Encoding utf8NoBOM
$cp = "$DependencyClassPath;$ProductionClassPath"
$friends = @($ProductionClassPath -split ';' | Where-Object { Test-Path -LiteralPath $_ -PathType Container }) -join ','
try {
    Invoke-Java 'compile' (@('-Xmx768m','-XX:+UseSerialGC','-cp',$CompilerClassPath,
        'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-jvm-target','17',
        "-Xfriend-paths=$friends",'-classpath',$cp,'-d',$classes) + $snapshots)
    Invoke-Java 'junit' @('-cp',"$classes;$cp",'org.junit.runner.JUnitCore',
        'com.maplesu.core.ui.webui.ExternalWebUiBridgeLifecycleTest',
        'com.maplesu.core.ui.webui.RootWebUiAppsTest','dev.maplesu.manager.liteweb.LiteWebModuleBridgeTest')
    Invoke-Node 'event-order' @((Join-Path $PSScriptRoot 'verify-event-order.cjs'),$root)
    foreach ($mode in @('service','daemon')) {
        $serverArgs=@('-cp',"$classes;$cp",'com.maplesu.core.ui.webui.BrowserBridgeFixtureServer')
        if ($mode -eq 'daemon') { $serverArgs += '--daemon' }
        $argsFile=Write-JavaArgs "server-$mode" $serverArgs
        $start=[Diagnostics.ProcessStartInfo]::new($Java)
        $start.ArgumentList.Add("@$argsFile");$start.UseShellExecute=$false;$start.CreateNoWindow=$true
        $start.RedirectStandardOutput=$true;$start.RedirectStandardError=$true
        $server=[Diagnostics.Process]::Start($start)
        $stderr=$server.StandardError.ReadToEndAsync()
        $oldToken=$env:FIXTURE_TOKEN
        try {
            $lineTask=$server.StandardOutput.ReadLineAsync()
            if (-not $lineTask.Wait(15000)) { throw 'Fixture server did not start in 15 seconds' }
            $line=$lineTask.Result
            if ($line -notmatch '^PORT=(\d+)$') { throw "Fixture server did not report its port: $line" }
            $port=$Matches[1]
            $env:FIXTURE_TOKEN=if ($mode -eq 'daemon') {'FixtureLegacyToken12345'} else {$null}
            Invoke-Node "wire-$mode" @((Join-Path $PSScriptRoot 'verify.cjs'),$port,$root)
        } finally {
            $env:FIXTURE_TOKEN=$oldToken
            if (-not $server.HasExited) { $server.Kill();$server.WaitForExit() }
            $stderr.Result | Set-Content -LiteralPath (Join-Path $output "server-$mode.log") -Encoding utf8NoBOM
            $server.Dispose()
        }
    }
} finally {
    $stable=@($inputs | Where-Object {(Get-FileHash -LiteralPath $_.path -Algorithm SHA256).Hash.ToLowerInvariant() -ne $_.sha256}).Count -eq 0
    [ordered]@{recordedUtc=[DateTime]::UtcNow.ToString('o');attempts=$attempts;inputsStable=$stable;
        scope='Current production HTTP/session/dispatcher/IO/PM/download/daemon forwarding; explicit Android/libsu/DOM/binder fixtures. No APK, Android browser, MediaStore, real app_process, root provider or device acceptance.'} |
        ConvertTo-Json -Depth 7 | Set-Content -LiteralPath (Join-Path $output 'RESULT.json') -Encoding utf8NoBOM
    if (-not $stable) { throw 'Inputs changed during regression; repeat against a stable snapshot' }
}
