$ErrorActionPreference='Stop'
$adb='C:/Users/tranc/AppData/Local/Android/Sdk/platform-tools/adb.exe'
$serial='192.168.1.106:5555'
$package='com.trancong.dexworkspacetouch'
$pre=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'precheck.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$ui=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'before-update-ui.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$library=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'library-before.dwtbundle') -Raw -Encoding UTF8 | ConvertFrom-Json
if(!($ui | Where-Object {$_.Text -eq 'Workspace Library'})){throw 'Licensed product UI not confirmed before update'}
if($library.format -ne 'dex-workspace-touch-library' -or $library.workspaceCount -le 0){throw 'User library backup not confirmed'}
function Device([string[]]$Arguments){
 $out=(& $adb -s $serial @Arguments | Out-String).Trim()
 if($LASTEXITCODE -ne 0){throw 'ADB operation failed; do not uninstall or clear data'}
 return $out
}
$artifact=Join-Path $PSScriptRoot '../artifact-license-unblock/DWT-SHIZUKU-001-product-qualification.apk'
$hash=(Get-FileHash -LiteralPath $artifact -Algorithm SHA256).Hash.ToLowerInvariant()
if($hash -ne $pre.QualificationApkSHA256 -or !$pre.UpdateInPlaceCompatible -or $pre.InstalledSignerSHA256 -ne $pre.QualificationSignerSHA256){throw 'Qualification artifact or signer compatibility changed'}
if((Device @('shell','getprop','ro.product.model')) -ne $pre.Model){throw 'Target device changed'}
$path=(Device @('shell','pm','path',$package)).Substring(8).Trim()
$oldHash=[regex]::Match((Device @('shell','sha256sum',$path)),'^[a-fA-F0-9]{64}').Value.ToLowerInvariant()
if($oldHash -ne $pre.InstalledApkSHA256){throw 'Installed APK changed since precheck: stop before update'}
$install=Device @('install','-r',$artifact)
if($install -notmatch '(?m)^Success\s*$'){throw 'Update-in-place failed: stop without uninstall or clear data'}
$dump=Device @('shell','dumpsys','package',$package)
$uid=[regex]::Match($dump,'(?m)^\s*(?:userId|appId)=(\d+)').Groups[1].Value
$first=[regex]::Match($dump,'firstInstallTime=([^\r\n]+)').Groups[1].Value.Trim()
$dir=[regex]::Match($dump,'(?m)^\s*dataDir=([^\r\n]+)').Groups[1].Value.Trim()
if($uid -ne $pre.UserId -or $first -ne $pre.FirstInstallTime -or $dir -ne $pre.DataDir){throw 'Data-preservation metadata changed: stop device proof'}
if($dump -notmatch 'versionCode=9\b' -or $dump -notmatch 'versionName=1\.0\.0-beta\.8\b'){throw 'Unexpected installed version'}
$path=(Device @('shell','pm','path',$package)).Substring(8).Trim()
$installedHash=[regex]::Match((Device @('shell','sha256sum',$path)),'^[a-fA-F0-9]{64}').Value.ToLowerInvariant()
if($installedHash -ne $hash){throw 'Installed APK bytes differ from frozen qualification artifact'}
$result=[ordered]@{Device='S23';Package=$package;UpdateInPlace='PASS';InstallCommand='adb install -r <frozen qualification APK>'
 InstalledApkSHA256=$installedHash;SignerSHA256=$pre.QualificationSignerSHA256;VersionCode=9;VersionName='1.0.0-beta.8'
 AppId=$uid;FirstInstallTime=$first;DataDir=$dir;DataPreservationMetadataUnchanged=$true;Uninstalled=$false;DataCleared=$false
 LicenseBeforeUpdate='Normal product UI observed';LibraryBeforeCount=$library.workspaceCount;LicenseAfterUpdate='NOT VERIFIED - awaiting normal startup'
 RecordedAtUtc=[DateTime]::UtcNow.ToString('o')}
$result | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $PSScriptRoot 'update-result.json') -Encoding UTF8
Device @('shell','am','start','--user','0','--display','6','--windowingMode','5','-n',"$package/.MainActivity") | Out-Null
Write-Output ($result | ConvertTo-Json -Compress -Depth 5)
