param(
    [string]$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path,
    [string]$ExistingSigningProperties = 'D:/AndroidStudioProjects/DexWorkspaceTouch/local.properties',
    [string]$Gradle = 'D:/AndroidStudioProjects/DexWorkspaceTouch/gradle/wrapper/dists/gradle-9.4.1-bin/arn2x92ynaizyzdaamcbpbhtj/gradle-9.4.1/bin/gradle.bat'
)
$ErrorActionPreference = 'Stop'
Push-Location $ProjectRoot
try {
    $expectedPackage = 'com.trancong.dexworkspacetouch'
    $expectedSigner = '19ac0ea99125361b3c2083aaa44c8745ebad9c55fa967642c2b9e5a1086a45e7'
    $expectedKeyFingerprint = 'ae8c0a5376ceef82fa3f0123e2788c21cc218bb19fb866aed83813745d392e74'
    $url = 'https://dexworkspacetouch-license-production.dex-backend.workers.dev'
    $config = Get-Content -LiteralPath 'license-backend/wrangler.jsonc' -Raw -Encoding UTF8 | ConvertFrom-Json
    $vars = $config.env.production.vars
    $registryJson = [string]$vars.LICENSE_TRUSTED_PUBLIC_KEYS_JSON
    $registry = @($registryJson | ConvertFrom-Json)
    $kid = [string]$vars.LICENSE_SIGNING_KEY_ID
    if ($kid -ne 'license-signing-v1' -or $registry.Count -ne 1 -or
        $registry[0].kid -ne $kid -or $registry[0].algorithm -ne 'RS256' -or
        $vars.APP_PACKAGE_NAME -ne $expectedPackage -or
        $vars.ANDROID_ALLOWED_SIGNING_CERT_SHA256 -ne $expectedSigner) {
        throw 'Approved production package/key/signer configuration mismatch.'
    }
    $sha = [Security.Cryptography.SHA256]::Create()
    try {
        $fingerprint = ([BitConverter]::ToString($sha.ComputeHash(
            [Convert]::FromBase64String([string]$registry[0].spkiBase64)))).Replace('-','').ToLowerInvariant()
    } finally { $sha.Dispose() }
    if ($fingerprint -ne $expectedKeyFingerprint) { throw 'Approved public-key fingerprint mismatch.' }
    $keys = @('DWT_RELEASE_STORE_FILE','DWT_RELEASE_STORE_PASSWORD','DWT_RELEASE_KEY_ALIAS','DWT_RELEASE_KEY_PASSWORD')
    $signing = @{}
    foreach ($line in Get-Content -LiteralPath $ExistingSigningProperties) {
        $pair = $line -split '=',2
        if ($pair.Count -eq 2 -and $keys -contains $pair[0]) {
            $signing[$pair[0]] = $pair[1].Replace('\:',':').Replace('\\','\')
        }
    }
    foreach ($name in $keys) {
        if (!$signing[$name]) { throw "Missing existing signing setting: $name" }
        [Environment]::SetEnvironmentVariable($name, $signing[$name], 'Process')
    }
    if (!(Test-Path -LiteralPath $signing['DWT_RELEASE_STORE_FILE'])) { throw 'Approved signing keystore unavailable.' }
    $env:DWT_LICENSE_API_BASE_URL = $url
    $env:DWT_LICENSE_TRUSTED_PUBLIC_KEYS_JSON = $registryJson
    $env:DWT_LICENSE_SIGNING_KEY_ID = $kid
    $env:DWT_REQUIRE_RELEASE_SIGNING = 'true'
    $env:DWT_BUILD_COMMIT = (& git -c safe.directory=$ProjectRoot rev-parse HEAD).Trim()
    $env:DWT_BUILD_DATE_UTC = [DateTime]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ssZ')
    $env:GRADLE_OPTS = '-Dorg.gradle.daemon=false'
    $snapshot = Get-Content -LiteralPath 'verification/dwt-shizuku-001/device-continuation/frozen-source-snapshot.json' -Raw | ConvertFrom-Json
    foreach ($row in $snapshot) {
        if ((Get-FileHash -LiteralPath $row.File -Algorithm SHA256).Hash -ne $row.SHA256) {
            throw "Frozen integration source changed: $($row.File)"
        }
    }
    & $Gradle ':app:assembleRelease' '--offline' '--console=plain'
    if ($LASTEXITCODE -ne 0) { throw 'Qualification build failed; production guard was preserved.' }
    $buildConfig = Get-Content -LiteralPath 'app/build/generated/source/buildConfig/release/com/trancong/dexworkspacetouch/BuildConfig.java' -Raw
    foreach ($expected in @($url, $kid)) {
        if (!$buildConfig.Contains($expected)) { throw 'Generated qualification licensing config mismatch.' }
    }
    if ($buildConfig -match 'LICENSE_TRUSTED_PUBLIC_KEYS_JSON = "\[\]"') { throw 'Qualification trusted registry is empty.' }
    $tools = 'C:/Users/tranc/AppData/Local/Android/Sdk/build-tools/37.0.0'
    $sourceApk = 'app/build/outputs/apk/release/app-release.apk'
    $certificate = & (Join-Path $tools 'apksigner.bat') verify --verbose --print-certs $sourceApk
    if ($LASTEXITCODE -ne 0) { throw 'Qualification APK signature verification failed.' }
    $certificate | Set-Content -LiteralPath (Join-Path $PSScriptRoot 'qualification-signer.txt') -Encoding UTF8
    $signerLine = [string]($certificate | Where-Object { $_ -match 'certificate SHA-256 digest:' } | Select-Object -First 1)
    $actualSigner = [regex]::Match($signerLine, 'digest:\s*([0-9a-fA-F]{64})').Groups[1].Value.ToLowerInvariant()
    if ($actualSigner -ne $expectedSigner) { throw 'Qualification APK signer mismatch.' }
    $badging = & (Join-Path $tools 'aapt2.exe') dump badging $sourceApk
    if ($LASTEXITCODE -ne 0) { throw 'Qualification APK package inspection failed.' }
    $packageLine = [string]($badging | Where-Object { $_ -like 'package:*' } | Select-Object -First 1)
    if ($packageLine -notmatch "name='([^']+)'\s+versionCode='([^']+)'\s+versionName='([^']+)'") { throw 'Invalid APK package metadata.' }
    $package, $versionCode, $versionName = $matches[1], [int]$matches[2], $matches[3]
    if ($package -ne $expectedPackage -or $versionCode -ne 9 -or $versionName -ne '1.0.0-beta.8') { throw 'Qualification changed product package/version.' }
    $finalApk = Join-Path $PSScriptRoot 'DWT-SHIZUKU-001-product-qualification.apk'
    Copy-Item -LiteralPath $sourceApk -Destination $finalApk -Force
    foreach ($row in $snapshot) {
        if ((Get-FileHash -LiteralPath $row.File -Algorithm SHA256).Hash -ne $row.SHA256) { throw 'Build changed frozen integration source.' }
    }
    [ordered]@{
        Purpose='DWT-SHIZUKU-001 device qualification only';Package=$package;VersionCode=$versionCode;VersionName=$versionName
        ApkSHA256=(Get-FileHash -LiteralPath $finalApk -Algorithm SHA256).Hash.ToLowerInvariant()
        ApkSignerSHA256=$actualSigner;LicenseApiBaseUrl=$url;ActiveKid=$kid;TrustedKeyRegistry=$registry
        LicensePublicKeyFingerprint=$fingerprint;SourceSnapshot='36 unchanged files';BuildVariant='release'
        ProductionConfigSource='scripts/build-device-smoke.ps1 + license-backend/wrangler.jsonc'
        SigningSource='Existing approved DWT keystore/configuration';Published=$false;Installed=$false
    } | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $PSScriptRoot 'qualification-manifest.json') -Encoding UTF8
    Write-Output "QUALIFICATION BUILD PASS: $finalApk"
    Write-Output "Package=$package; version=$versionName/$versionCode; signer=$actualSigner"
} finally {
    foreach ($name in @('DWT_RELEASE_STORE_FILE','DWT_RELEASE_STORE_PASSWORD','DWT_RELEASE_KEY_ALIAS','DWT_RELEASE_KEY_PASSWORD')) {
        [Environment]::SetEnvironmentVariable($name, $null, 'Process')
    }
    Pop-Location
}
