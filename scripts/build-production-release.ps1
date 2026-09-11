[CmdletBinding()]
param(
    [switch]$AcknowledgeDirtyWorktree,
    [switch]$SkipTests,
    [switch]$CreateUpdateManifest,
    [string]$UpdateManifestUrl,
    [string]$ApkUrl,
    [string]$ReleaseNotes = ''
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Set-Location $projectRoot
$expectedPackage = 'com.trancong.dexworkspacetouch'
$expectedSigner = '19ac0ea99125361b3c2083aaa44c8745ebad9c55fa967642c2b9e5a1086a45e7'
$expectedLicenseFingerprint = 'ae8c0a5376ceef82fa3f0123e2788c21cc218bb19fb866aed83813745d392e74'
$productionUrl = 'https://dexworkspacetouch-license-production.dex-backend.workers.dev'
$workerVersion = 'e96efb6c-57b0-42da-b32d-bb93a959350f'
$d1Migration = '0004_license_refresh.sql'

function Assert-PublicHttpsUrl([string]$Value, [string]$Name) {
    if ([string]::IsNullOrWhiteSpace($Value)) { throw "$Name is required." }
    $parsed = $null
    if (-not [Uri]::TryCreate($Value, [UriKind]::Absolute, [ref]$parsed) -or
        $parsed.Scheme -ne 'https' -or [string]::IsNullOrWhiteSpace($parsed.Host) -or
        $parsed.Host -in @('localhost', '10.0.2.2')) {
        throw "$Name must be a non-local HTTPS URL."
    }
}

if ($CreateUpdateManifest) {
    Assert-PublicHttpsUrl $UpdateManifestUrl 'UpdateManifestUrl'
    Assert-PublicHttpsUrl $ApkUrl 'ApkUrl'
}

$dirty = [bool](git status --porcelain)
if ($LASTEXITCODE -ne 0) { throw 'Unable to read Git state.' }
if ($dirty -and -not $AcknowledgeDirtyWorktree) {
    throw 'Worktree is dirty. Review it, then rerun with -AcknowledgeDirtyWorktree.'
}

$config = Get-Content -Raw 'license-backend/wrangler.jsonc' | ConvertFrom-Json
$activeKid = [string]$config.env.production.vars.LICENSE_SIGNING_KEY_ID
$trustedJson = [string]$config.env.production.vars.LICENSE_TRUSTED_PUBLIC_KEYS_JSON
$trusted = @($trustedJson | ConvertFrom-Json)
if ($activeKid -ne 'license-signing-v1' -or $trusted.Count -ne 1 -or $trusted[0].kid -ne $activeKid) {
    throw 'Production license signing registry does not match the approved LIC-014 state.'
}
$sha256 = [Security.Cryptography.SHA256]::Create()
$licenseFingerprint = ([BitConverter]::ToString(
    $sha256.ComputeHash([Convert]::FromBase64String([string]$trusted[0].spkiBase64))
)).Replace('-', '').ToLowerInvariant()
$sha256.Dispose()
if ($licenseFingerprint -ne $expectedLicenseFingerprint) { throw 'License-token public-key fingerprint mismatch.' }

$sdkDir = ((Get-Content -Raw 'local.properties') -split "`r?`n" |
    Where-Object { $_ -like 'sdk.dir=*' } | Select-Object -First 1).Substring(8).Replace('\:', ':').Replace('\\', '\')
$buildTools = Get-ChildItem (Join-Path $sdkDir 'build-tools') -Directory | Sort-Object Name -Descending | Select-Object -First 1
if ($null -eq $buildTools) { throw 'Android build-tools not found.' }
$apksigner = Join-Path $buildTools.FullName 'apksigner.bat'
$aapt2 = Join-Path $buildTools.FullName 'aapt2.exe'

$env:DWT_LICENSE_API_BASE_URL = $productionUrl
$env:DWT_LICENSE_TRUSTED_PUBLIC_KEYS_JSON = $trustedJson
$env:DWT_LICENSE_SIGNING_KEY_ID = $activeKid
$env:DWT_REQUIRE_RELEASE_SIGNING = 'true'
$env:DWT_BUILD_COMMIT = (git rev-parse HEAD).Trim()
$env:DWT_BUILD_DATE_UTC = [DateTime]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ssZ')
if ($CreateUpdateManifest) { $env:DWT_UPDATE_MANIFEST_URL = $UpdateManifestUrl }

[string[]]$tasks = if ($SkipTests) { @('assembleRelease') } else {
    @('testDebugUnitTest', 'lintDebug', 'assembleDebug', 'assembleDebugAndroidTest', 'assembleRelease')
}
& '.\gradlew.bat' @tasks
if ($LASTEXITCODE -ne 0) { throw 'Gradle release gate failed.' }

$apk = (Resolve-Path 'app/build/outputs/apk/release/app-release.apk').Path
& $apksigner verify --verbose --print-certs $apk | Tee-Object -Variable signerOutput | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
$signerLine = $signerOutput | Where-Object { $_ -match 'certificate SHA-256 digest:\s*([0-9a-fA-F]+)' } | Select-Object -First 1
if ($null -eq $signerLine) { throw 'APK signer fingerprint was not reported.' }
$null = $signerLine -match 'certificate SHA-256 digest:\s*([0-9a-fA-F]+)'
$actualSigner = $matches[1].ToLowerInvariant()
if ($actualSigner -ne $expectedSigner) { throw 'STOP RELEASE: APK signer does not match production.' }

$badging = & $aapt2 dump badging $apk
if ($LASTEXITCODE -ne 0) { throw 'Unable to inspect APK metadata.' }
$packageLine = $badging | Where-Object { $_ -like 'package:*' } | Select-Object -First 1
if ($packageLine -notmatch "name='([^']+)'\s+versionCode='([^']+)'\s+versionName='([^']+)'") {
    throw 'Unable to parse APK package/version metadata.'
}
$applicationId, $versionCode, $versionName = $matches[1], [int]$matches[2], $matches[3]
if ($applicationId -ne $expectedPackage) { throw 'STOP RELEASE: APK applicationId mismatch.' }

$outputDir = Join-Path $projectRoot 'release-output'
New-Item -ItemType Directory -Force $outputDir | Out-Null
$fileName = "DexWorkspaceTouch-$versionName-$versionCode.apk"
$finalApk = Join-Path $outputDir $fileName
Copy-Item -LiteralPath $apk -Destination $finalApk -Force
$apkHash = (Get-FileHash -LiteralPath $finalApk -Algorithm SHA256).Hash.ToLowerInvariant()
$apkSize = (Get-Item -LiteralPath $finalApk).Length

$manifest = [ordered]@{
    schemaVersion = 1; applicationId = $applicationId; versionName = $versionName; versionCode = $versionCode
    buildTimestampUtc = $env:DWT_BUILD_DATE_UTC; gitCommit = $env:DWT_BUILD_COMMIT; dirty = $dirty
    apkFilename = $fileName; apkSizeBytes = $apkSize; apkSha256 = $apkHash
    apkSigningCertificateSha256 = $actualSigner; productionBackendUrl = $productionUrl
    activeLicenseSigningKid = $activeKid; trustedLicenseSigningKids = @($trusted | ForEach-Object { $_.kid })
    licenseSigningPublicKeyFingerprint = $licenseFingerprint; workerVersion = $workerVersion
    d1MigrationVersion = $d1Migration
}
$manifestPath = Join-Path $outputDir 'release-manifest.json'
$manifest | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $manifestPath -Encoding utf8
"$apkHash  $fileName" | Set-Content -LiteralPath (Join-Path $outputDir 'SHA256SUMS.txt') -Encoding ascii

$roundTrip = Get-Content -Raw $manifestPath | ConvertFrom-Json
if ($roundTrip.applicationId -ne $expectedPackage -or $roundTrip.apkSha256 -ne (Get-FileHash $finalApk -Algorithm SHA256).Hash.ToLowerInvariant() -or
    $roundTrip.apkSigningCertificateSha256 -ne $expectedSigner -or $roundTrip.productionBackendUrl -ne $productionUrl -or
    $roundTrip.activeLicenseSigningKid -ne 'license-signing-v1') { throw 'Generated release manifest validation failed.' }
$serialized = Get-Content -Raw $manifestPath
if ($serialized -match 'PRIVATE KEY|LICENSE_KEY_PEPPER|LICENSE_ADMIN_TOKEN|keystorePassword|keyPassword|Bearer\s') {
    throw 'Secret-like material detected in release manifest.'
}

if ($CreateUpdateManifest) {
    $updateManifest = [ordered]@{
        schemaVersion = 1
        applicationId = $applicationId
        versionName = $versionName
        versionCode = $versionCode
        apkUrl = $ApkUrl
        apkSha256 = $apkHash
        apkSize = $apkSize
        signingCertificateSha256 = $actualSigner
        publishedAt = $env:DWT_BUILD_DATE_UTC
        releaseNotes = $ReleaseNotes
    }
    $updateManifestPath = Join-Path $outputDir 'update-manifest.json'
    $updateManifestJson = $updateManifest | ConvertTo-Json -Depth 3
    [IO.File]::WriteAllText($updateManifestPath, $updateManifestJson, [Text.UTF8Encoding]::new($false))
    $verifiedUpdateManifest = Get-Content -Raw $updateManifestPath | ConvertFrom-Json
    if ($verifiedUpdateManifest.applicationId -ne $expectedPackage -or
        $verifiedUpdateManifest.versionCode -ne $versionCode -or
        $verifiedUpdateManifest.apkSha256 -ne $apkHash -or
        $verifiedUpdateManifest.apkSize -ne $apkSize -or
        $verifiedUpdateManifest.signingCertificateSha256 -ne $expectedSigner -or
        $verifiedUpdateManifest.apkUrl -ne $ApkUrl) {
        throw 'Generated update manifest validation failed.'
    }
    Write-Host "Update manifest: $updateManifestPath"
} else {
    Write-Host 'Update manifest not generated: no publishable hosting URLs were requested.'
}

Write-Host "RELEASE READY: $finalApk"
Write-Host "SHA-256: $apkHash"
Write-Host "Signer SHA-256: $actualSigner"
