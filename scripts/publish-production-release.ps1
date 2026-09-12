[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$BucketName,
    [Parameter(Mandatory)][string]$WorkerBaseUrl,
    [Parameter(Mandatory)][string]$ApkPath,
    [string]$ManifestPath = 'release-output/update-manifest.json',
    [string]$WranglerPath = 'update-delivery/node_modules/.bin/wrangler.cmd'
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Set-Location $projectRoot
$expectedPackage = 'com.trancong.dexworkspacetouch'
$expectedSigner = '19ac0ea99125361b3c2083aaa44c8745ebad9c55fa967642c2b9e5a1086a45e7'

function Assert-PublicHttpsUrl([string]$Value, [string]$Name) {
    $uri = $null
    if (-not [Uri]::TryCreate($Value, [UriKind]::Absolute, [ref]$uri) -or $uri.Scheme -ne 'https' -or
        [string]::IsNullOrWhiteSpace($uri.Host) -or $uri.Host -in @('localhost', '10.0.2.2')) {
        throw "$Name must be a non-local HTTPS URL."
    }
}

Assert-PublicHttpsUrl $WorkerBaseUrl 'WorkerBaseUrl'
$apk = (Resolve-Path $ApkPath).Path
$manifestFile = (Resolve-Path $ManifestPath).Path
$manifestBytes = [IO.File]::ReadAllBytes($manifestFile)
if ($manifestBytes.Length -ge 3 -and $manifestBytes[0] -eq 0xEF -and
    $manifestBytes[1] -eq 0xBB -and $manifestBytes[2] -eq 0xBF) {
    throw 'Refusing to publish an update manifest containing a UTF-8 BOM.'
}
$manifest = Get-Content -Raw -LiteralPath $manifestFile | ConvertFrom-Json
if ($manifest.applicationId -ne $expectedPackage -or $manifest.signingCertificateSha256 -ne $expectedSigner) {
    throw 'Update manifest package or production signer mismatch.'
}
$localHash = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
$localSize = (Get-Item -LiteralPath $apk).Length
if ($manifest.apkSha256 -ne $localHash -or $manifest.apkSize -ne $localSize) {
    throw 'Update manifest does not match the selected APK bytes.'
}

$base = $WorkerBaseUrl.TrimEnd('/')
$apkUri = [Uri]$manifest.apkUrl
Assert-PublicHttpsUrl $apkUri.AbsoluteUri 'manifest.apkUrl'
if ($apkUri.GetLeftPart([UriPartial]::Authority) -ne ([Uri]$base).GetLeftPart([UriPartial]::Authority)) {
    throw 'APK URL is not served by the selected delivery Worker origin.'
}
$objectKey = $apkUri.AbsolutePath.TrimStart('/')
if ($objectKey -notmatch '^releases/[^/]+/DexWorkspaceTouch-[^/]+\.apk$') { throw 'Unsafe versioned APK object path.' }
$manifestUrl = "$base/update-manifest.json"

$tempDir = Join-Path ([IO.Path]::GetTempPath()) ("dwt-publish-" + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $tempDir | Out-Null
try {
    $existingResponse = $null
    try { $existingResponse = Invoke-WebRequest -Method Head -Uri $apkUri.AbsoluteUri -UseBasicParsing }
    catch { if ($_.Exception.Response.StatusCode.value__ -ne 404) { throw } }
    if ($null -ne $existingResponse) {
        $existingCopy = Join-Path $tempDir 'existing.apk'
        Invoke-WebRequest -Uri $apkUri.AbsoluteUri -OutFile $existingCopy -UseBasicParsing
        if ((Get-FileHash $existingCopy -Algorithm SHA256).Hash.ToLowerInvariant() -ne $localHash) {
            throw 'STOP: immutable APK path already exists with different bytes.'
        }
        Write-Host 'Versioned APK already exists with identical SHA-256; upload skipped.'
    } else {
        & $WranglerPath r2 object put "$BucketName/$objectKey" --remote --file $apk `
            --content-type 'application/vnd.android.package-archive' `
            --content-disposition "attachment; filename=`"$($apkUri.Segments[-1])`"" `
            --cache-control 'public, max-age=31536000, immutable'
        if ($LASTEXITCODE -ne 0) { throw 'APK upload failed; manifest was not changed.' }
    }

    $head = Invoke-WebRequest -Method Head -Uri $apkUri.AbsoluteUri -UseBasicParsing
    if ($head.StatusCode -ne 200 -or [long]$head.Headers.'Content-Length' -ne $localSize -or
        $head.Headers.'Content-Type' -ne 'application/vnd.android.package-archive') {
        throw 'Remote APK HEAD verification failed; manifest was not changed.'
    }
    $remoteCopy = Join-Path $tempDir 'remote.apk'
    Invoke-WebRequest -Uri $apkUri.AbsoluteUri -OutFile $remoteCopy -UseBasicParsing
    $remoteHash = (Get-FileHash $remoteCopy -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($remoteHash -ne $localHash) { throw 'Remote APK SHA-256 mismatch; manifest was not changed.' }
    Add-Type -AssemblyName System.Net.Http
    $rangeClient = [Net.Http.HttpClient]::new()
    $rangeRequest = [Net.Http.HttpRequestMessage]::new([Net.Http.HttpMethod]::Get, $apkUri.AbsoluteUri)
    $rangeRequest.Headers.Range = [Net.Http.Headers.RangeHeaderValue]::new(0, 1023)
    try {
        $rangeResponse = $rangeClient.SendAsync($rangeRequest).GetAwaiter().GetResult()
        $rangeBytes = $rangeResponse.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult()
        if ([int]$rangeResponse.StatusCode -ne 206 -or $rangeBytes.Length -ne 1024) {
            throw 'Remote APK range verification failed; manifest was not changed.'
        }
    } finally {
        $rangeRequest.Dispose()
        $rangeClient.Dispose()
    }

    $backupPath = Join-Path (Split-Path $manifestFile) 'previous-update-manifest.json'
    try { Invoke-WebRequest -Uri $manifestUrl -OutFile $backupPath -UseBasicParsing }
    catch { if ($_.Exception.Response.StatusCode.value__ -ne 404) { throw } }
    & $WranglerPath r2 object put "$BucketName/update-manifest.json" --remote --file $manifestFile `
        --content-type 'application/json; charset=utf-8' --cache-control 'no-cache'
    if ($LASTEXITCODE -ne 0) { throw 'Manifest upload failed; the versioned APK remains safely undiscoverable.' }

    $publishedManifestJson = (Invoke-WebRequest -Uri $manifestUrl -UseBasicParsing).Content.TrimStart([char]0xFEFF)
    $publishedManifest = $publishedManifestJson | ConvertFrom-Json
    if ($publishedManifest.apkSha256 -ne $localHash -or $publishedManifest.apkUrl -ne $apkUri.AbsoluteUri -or
        $publishedManifest.versionCode -ne $manifest.versionCode) {
        throw "PUBLISH FAILED: production manifest validation failed. Restore $backupPath if it exists."
    }
    Write-Host "PUBLISH COMPLETE: APK first, manifest last"
    Write-Host "APK URL: $($apkUri.AbsoluteUri)"
    Write-Host "Manifest URL: $manifestUrl"
    Write-Host "Local/remote SHA-256: $localHash"
} finally {
    if (Test-Path -LiteralPath $tempDir) { Remove-Item -LiteralPath $tempDir -Recurse -Force }
}
