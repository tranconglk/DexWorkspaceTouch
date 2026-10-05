[CmdletBinding()]
param([string]$Serial, [string]$SdkDir = "$env:LOCALAPPDATA/Android/Sdk",
    [string]$ApkPath, [string]$TestApkPath, [switch]$VerifyArtifactsOnly, [int]$DisplayId = 0,
    [ValidateSet("Controlled", "Capture", "Real")][string]$CaseSet = "Controlled", [string]$OnlyMethod, [string]$IndependentRunId)
$ErrorActionPreference = 'Stop'
if ($CaseSet -eq 'Real' -and -not $VerifyArtifactsOnly) {
    if (-not $IndependentRunId -or $IndependentRunId -notmatch '^vdm010-independent-[a-f0-9-]{36}$' -or -not $OnlyMethod) {
        throw 'STOP: Real requires a labelled independent run and exactly one allowlisted method'
    }
}
Set-StrictMode -Version Latest
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$mainPackage = 'com.trancong.dexworkspacetouch.vdm010harness'
$testPackage = "$mainPackage.test"
$apk = if ($ApkPath) { $ApkPath } else { Join-Path $repo 'app/build/outputs/apk/debug/app-debug.apk' }
$testApk = if ($TestApkPath) { $TestApkPath } else { Join-Path $repo 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk' }
$evidence = Join-Path $repo $(if ($CaseSet -eq 'Real') { 'verification/dwt-vdm-010/android-real' } else { 'verification/dwt-vdm-010/android-controlled' })
$evidence = Join-Path $evidence ([DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfffZ'))
$adb = Join-Path $SdkDir 'platform-tools/adb.exe'
$tools = Join-Path $SdkDir 'build-tools/37.0.0'
$aapt = Join-Path $tools 'aapt2.exe'
$signer = Join-Path $tools 'apksigner.bat'
function Metadata([string]$Path) {
    $lines = & $aapt dump badging $Path
    if ($LASTEXITCODE -ne 0) { throw "Cannot inspect APK: $Path" }
    $line = $lines | Where-Object { $_ -like 'package:*' } | Select-Object -First 1
    if ($line -notmatch "name='([^']+)'\s+versionCode='([^']*)'\s+versionName='([^']*)'") { throw 'Bad APK metadata' }
    return @{ Package = $matches[1]; VersionCode = $matches[2]; VersionName = $matches[3] }
}
function Certificate([string]$Path) {
    $lines = & $signer verify --print-certs $Path
    if ($LASTEXITCODE -ne 0) { throw "Invalid APK signature: $Path" }
    $line = $lines | Where-Object { $_ -match 'certificate SHA-256 digest:\s*([0-9a-fA-F]+)' } | Select-Object -First 1
    if (-not $line -or $line -notmatch 'certificate SHA-256 digest:\s*([0-9a-fA-F]+)') { throw 'Missing certificate digest' }
    return $matches[1].ToLowerInvariant()
}
$main = Metadata $apk
$test = Metadata $testApk
if ($main.Package -ne $mainPackage -or $test.Package -ne $testPackage) {
    throw 'STOP: build with isolated-app-id.init.gradle. Production-package installation is forbidden.'
}
if ($main.VersionCode -ne '9' -or $main.VersionName -ne '1.0.0-beta.8') { throw 'Unexpected version; do not bump version for this harness' }
$xml = & $aapt dump xmltree --file AndroidManifest.xml $testApk
if ($LASTEXITCODE -ne 0 -or ($xml -join "`n") -notmatch ('targetPackage.*="' + [regex]::Escape($mainPackage) + '"')) {
    throw 'STOP: instrumentation must target isolated harness package'
}
$mainCertificate = Certificate $apk
if ((Certificate $testApk) -ne $mainCertificate) { throw 'STOP: test and target signer differ' }
$identity = [pscustomobject]@{ Package=$main.Package; TestPackage=$test.Package; VersionCode=$main.VersionCode;
    VersionName=$main.VersionName; SignerSha256=$mainCertificate; TargetPackage=$mainPackage;
    ApkSha256=(Get-FileHash -Algorithm SHA256 -LiteralPath $apk).Hash;
    TestApkSha256=(Get-FileHash -Algorithm SHA256 -LiteralPath $testApk).Hash }
if ($VerifyArtifactsOnly) {
    $identity | ConvertTo-Json
    return
}
$devices = & $adb devices
if ($LASTEXITCODE -ne 0) { throw 'ADB availability check failed' }
$available = @($devices | Where-Object { $_ -match '^(\S+)\s+device$' } | ForEach-Object { ($_ -split '\s+')[0] })
if ($Serial) {
    if ($Serial -notin $available) { throw 'Selected device is not ready' }
} elseif ($available.Count -eq 1) { $Serial = $available[0] }
else { throw 'Select one ready device with -Serial; no install performed' }
New-Item -ItemType Directory -Path $evidence -Force | Out-Null
$identity | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $evidence 'artifact-identity.json') -Encoding UTF8
[pscustomobject]@{Serial=$Serial; DisplayId=$DisplayId; CaseSet=$CaseSet; OnlyMethod=$OnlyMethod;
        IndependentRunId=$IndependentRunId; NewIndependentTestbed=($CaseSet -eq 'Real');
        HistoricalSession='0bf0ecec-9e2c-4196-8edb-a076b688edb1'; HistoricalAuthoritativeRelease='NOT VERIFIED'} |
    ConvertTo-Json | Set-Content -LiteralPath (Join-Path $evidence 'run-manifest.json') -Encoding UTF8
$matchingInstalled010 = @{}
foreach ($package in @($mainPackage, $testPackage)) {
    $listed = & $adb -s $Serial shell pm list packages --user 0 $package
    if ($LASTEXITCODE -ne 0) { throw "Cannot inspect installed testbed package $package" }
    if (@($listed | Where-Object { $_.Trim() -eq "package:$package" }).Count -eq 0) { continue }
    $installed = & $adb -s $Serial shell pm path $package
    if ($LASTEXITCODE -ne 0) { throw "Cannot read installed testbed APK path $package" }
    $base = $installed | Where-Object { $_ -match '^package:.*base\.apk$' } | Select-Object -First 1
    if ($base) {
        $readback = Join-Path $evidence "$package-before.apk"
        & $adb -s $Serial pull $base.Substring(8).Trim() $readback | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'Installed harness signer readback failed' }
        if ((Certificate $readback) -ne $mainCertificate) { throw 'STOP: existing isolated package has incompatible signer; no uninstall/data clear allowed' }
        $expected010 = if ($package -eq $mainPackage) { $apk } else { $testApk }
        $matchingInstalled010[$package] = (Get-FileHash -LiteralPath $readback).Hash -eq (Get-FileHash -LiteralPath $expected010).Hash
    }
}
# Only exact allowlisted isolated packages can reach install. Never uninstall/clear data.
foreach ($path in @($apk, $testApk)) {
    $package010 = if ($path -eq $apk) { $mainPackage } else { $testPackage }
    if ($matchingInstalled010[$package010]) { continue }
    & $adb -s $Serial install -r $path
    if ($LASTEXITCODE -ne 0) { throw 'Isolated APK installation failed; do not replace production package' }
}
$methods = @(
    'recreateJoinsCleanupOldCallbackCannotPopNewHostAndFreshStartIsExplicit',
    'syntheticIncompleteSurvivesRecreationNavigationAndReadinessRefresh',
    'syntheticUncertainSurvivesRecreationNavigationAndReadinessRefresh',
    'actualSurfaceLossAndViewportChangesDoNotResurrectRun'
)
$class = 'com.trancong.dexworkspacetouch.qualification.EmbeddedHostLifecycleDeviceTest'
$remoteDirectory = 'dwt-vdm-010'
if ($CaseSet -eq 'Real') {
    $class = 'com.trancong.dexworkspacetouch.qualification.EmbeddedRealProductLifecycleDeviceTest'
    $methods = @('realCalculatorRecreationFreshStartAndSurfaceLoss', 'realCalculatorWazeRecreationFreshStartAndSurfaceLoss')
    $remoteDirectory = 'dwt-vdm-010-real'
}
if ($CaseSet -eq 'Capture') {
    $class = 'com.trancong.dexworkspacetouch.qualification.EmbeddedDiagnosticCaptureDeviceTest'
    $methods = @('preservesRawFailureAndExactOwnedValuesBeforeProductProjection')
    $remoteDirectory = 'dwt-vdm-010-capture'
}
if ($OnlyMethod) {
    if ($OnlyMethod -notin $methods) { throw 'Method is not allowlisted for this case set' }
    $methods = @($OnlyMethod)
}
if ($CaseSet -eq 'Real') {
    $overlayManifest = Join-Path $repo 'experiments/dwt-vdm-010-host-harness/diagnostic-overlay-manifest.json'
    if (-not (Test-Path -LiteralPath $overlayManifest)) { throw 'Missing diagnostic overlay manifest' }
    Copy-Item -LiteralPath $overlayManifest -Destination (Join-Path $evidence 'diagnostic-overlay-manifest.json')
}
foreach ($method in $methods) {
    # Separate invocation gives a fresh isolated process; inside each scenario
    # Activity recreation must preserve PID. Never reset a CLEANUP_BLOCKED gate.
    $independentArguments = if ($CaseSet -eq 'Real') { @('-e', 'independentRunId', $IndependentRunId) } else { @() }
    $output = & $adb -s $Serial shell am instrument -w -r @independentArguments -e displayId $DisplayId -e class "$class#$method" "$testPackage/androidx.test.runner.AndroidJUnitRunner"
    $result = $LASTEXITCODE
    $output | Set-Content -LiteralPath (Join-Path $evidence "$method.txt") -Encoding UTF8
    $passed = $result -eq 0 -and ($output -join "`n") -match 'OK \(1 test\)'
    & $adb -s $Serial pull "/sdcard/Android/data/$mainPackage/files/$remoteDirectory" $evidence | Out-Null
    if ($CaseSet -eq 'Real') {
        $diagnosticLog = & $adb -s $Serial logcat -d -b main -v threadtime -s 'DWT-010-DIAG:I' 'TaskViewLab.VDMSurface:I'
        if ($LASTEXITCODE -ne 0) { throw 'Diagnostic log read failed; no retry allowed' }
        $diagnosticLog | Set-Content -LiteralPath (Join-Path $evidence 'diagnostic-remote-log.txt') -Encoding UTF8
    }
    if (-not $passed) { throw "Harness case failed or not run: $method; STOP, no additional Start" }
    if ($LASTEXITCODE -ne 0) { throw 'Missing host/PID/gate/Surface evidence; do not report PASS' }
}
Write-Output "$CaseSet case set: $($methods.Count) selected cases PASS; evidence categories remain separate."
