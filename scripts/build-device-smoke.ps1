[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Set-Location $projectRoot

$expectedPackage = 'com.trancong.dexworkspacetouch'
$expectedSigner = '19ac0ea99125361b3c2083aaa44c8745ebad9c55fa967642c2b9e5a1086a45e7'
$expectedLicenseFingerprint = 'ae8c0a5376ceef82fa3f0123e2788c21cc218bb19fb866aed83813745d392e74'
$productionUrl = 'https://dexworkspacetouch-license-production.dex-backend.workers.dev'
$expectedKid = 'license-signing-v1'

function Assert-PublicHttpsUrl([string]$Value, [string]$Name) {
    if ([string]::IsNullOrWhiteSpace($Value)) { throw "$Name is required." }
    $parsed = $null
    if (-not [Uri]::TryCreate($Value, [UriKind]::Absolute, [ref]$parsed) -or
        $parsed.Scheme -ne 'https' -or [string]::IsNullOrWhiteSpace($parsed.Host) -or
        $parsed.Host -in @('localhost', '10.0.2.2')) {
        throw "$Name must be a non-local HTTPS URL."
    }
}

Assert-PublicHttpsUrl $productionUrl 'Production license API URL'

$config = Get-Content -Raw 'license-backend/wrangler.jsonc' | ConvertFrom-Json
$activeKid = [string]$config.env.production.vars.LICENSE_SIGNING_KEY_ID
$trustedJson = [string]$config.env.production.vars.LICENSE_TRUSTED_PUBLIC_KEYS_JSON
$trusted = @($trustedJson | ConvertFrom-Json)
if ($activeKid -ne $expectedKid -or $trusted.Count -ne 1 -or $trusted[0].kid -ne $activeKid) {
    throw 'Production license signing registry does not match the approved LIC-014 state.'
}
$sha256 = [Security.Cryptography.SHA256]::Create()
try {
    $licenseFingerprint = ([BitConverter]::ToString(
        $sha256.ComputeHash([Convert]::FromBase64String([string]$trusted[0].spkiBase64))
    )).Replace('-', '').ToLowerInvariant()
} finally {
    $sha256.Dispose()
}
if ($licenseFingerprint -ne $expectedLicenseFingerprint) {
    throw 'License-token public-key fingerprint mismatch.'
}

$sdkLine = (Get-Content -Raw 'local.properties') -split "`r?`n" |
    Where-Object { $_ -like 'sdk.dir=*' } | Select-Object -First 1
if ([string]::IsNullOrWhiteSpace($sdkLine)) { throw 'sdk.dir is missing from local.properties.' }
$sdkDir = $sdkLine.Substring(8).Replace('\:', ':').Replace('\\', '\')
$buildTools = Get-ChildItem (Join-Path $sdkDir 'build-tools') -Directory |
    Sort-Object Name -Descending | Select-Object -First 1
if ($null -eq $buildTools) { throw 'Android build-tools not found.' }
$apksigner = Join-Path $buildTools.FullName 'apksigner.bat'
$aapt2 = Join-Path $buildTools.FullName 'aapt2.exe'

$env:DWT_LICENSE_API_BASE_URL = $productionUrl
$env:DWT_LICENSE_TRUSTED_PUBLIC_KEYS_JSON = $trustedJson
$env:DWT_LICENSE_SIGNING_KEY_ID = $activeKid
$env:DWT_REQUIRE_RELEASE_SIGNING = 'true'
$env:DWT_BUILD_COMMIT = (git rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0) { throw 'Unable to resolve the current Git HEAD.' }
$env:DWT_BUILD_DATE_UTC = [DateTime]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ssZ')

& '.\gradlew.bat' assembleRelease
if ($LASTEXITCODE -ne 0) { throw 'Device-smoke release build failed.' }

$apk = (Resolve-Path 'app/build/outputs/apk/release/app-release.apk').Path
& $apksigner verify --verbose --print-certs $apk | Tee-Object -Variable signerOutput | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
$signerLine = $signerOutput |
    Where-Object { $_ -match 'certificate SHA-256 digest:\s*([0-9a-fA-F]+)' } |
    Select-Object -First 1
if ($null -eq $signerLine) { throw 'APK signer fingerprint was not reported.' }
$null = $signerLine -match 'certificate SHA-256 digest:\s*([0-9a-fA-F]+)'
$actualSigner = $matches[1].ToLowerInvariant()
if ($actualSigner -ne $expectedSigner) { throw 'STOP SMOKE: APK signer does not match production.' }

$badging = & $aapt2 dump badging $apk
if ($LASTEXITCODE -ne 0) { throw 'Unable to inspect APK metadata.' }
$packageLine = $badging | Where-Object { $_ -like 'package:*' } | Select-Object -First 1
if ($packageLine -notmatch "name='([^']+)'\s+versionCode='([^']+)'\s+versionName='([^']+)'") {
    throw 'Unable to parse APK package/version metadata.'
}
$applicationId, $versionCode, $versionName = $matches[1], [int]$matches[2], $matches[3]
if ($applicationId -ne $expectedPackage) { throw 'STOP SMOKE: APK applicationId mismatch.' }

$buildConfigPath = Join-Path $projectRoot 'app/build/generated/source/buildConfig/release/com/trancong/dexworkspacetouch/BuildConfig.java'
if (-not (Test-Path -LiteralPath $buildConfigPath)) { throw 'Generated release BuildConfig was not found.' }
$buildConfig = Get-Content -Raw -LiteralPath $buildConfigPath
$escapedUrl = [Regex]::Escape('public static final String LICENSE_API_BASE_URL = "' + $productionUrl + '";')
$escapedKid = [Regex]::Escape('public static final String LICENSE_SIGNING_KEY_ID = "' + $expectedKid + '";')
if ($buildConfig -notmatch $escapedUrl -or $buildConfig -notmatch $escapedKid) {
    throw 'Generated release BuildConfig does not contain the approved production license configuration.'
}
if ($buildConfig -match 'LICENSE_API_BASE_URL\s*=\s*"(?:"|http://|[^";]*(?:localhost|10\.0\.2\.2))') {
    throw 'Generated release BuildConfig contains a forbidden license API URL.'
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::OpenRead($apk)
try {
    $dexText = [Text.StringBuilder]::new()
    foreach ($entry in $archive.Entries | Where-Object { $_.FullName -match '^classes\d*\.dex$' }) {
        $stream = $entry.Open()
        try {
            $memory = [IO.MemoryStream]::new()
            try {
                $stream.CopyTo($memory)
                $null = $dexText.Append([Text.Encoding]::UTF8.GetString($memory.ToArray()))
            } finally {
                $memory.Dispose()
            }
        } finally {
            $stream.Dispose()
        }
    }
    if ($dexText.ToString().IndexOf($productionUrl, [StringComparison]::Ordinal) -lt 0 -or
        $dexText.ToString().IndexOf($expectedKid, [StringComparison]::Ordinal) -lt 0) {
        throw 'Packaged DEX does not contain the approved production license configuration.'
    }
} finally {
    $archive.Dispose()
}

$outputDir = Join-Path $projectRoot 'smoke-output'
New-Item -ItemType Directory -Force $outputDir | Out-Null
$finalApk = Join-Path $outputDir 'DWT-device-smoke.apk'
Copy-Item -LiteralPath $apk -Destination $finalApk -Force
$apkHash = (Get-FileHash -LiteralPath $finalApk -Algorithm SHA256).Hash.ToLowerInvariant()

Write-Host 'DEVICE SMOKE BUILD: PASS'
Write-Host "APK: $finalApk"
Write-Host "applicationId: $applicationId"
Write-Host "versionCode: $versionCode"
Write-Host "versionName: $versionName"
Write-Host "APK SHA-256: $apkHash"
Write-Host "Signer SHA-256: $actualSigner"
Write-Host "Production license URL: $productionUrl"
Write-Host "License signing kid: $activeKid"
