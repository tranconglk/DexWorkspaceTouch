$ErrorActionPreference='Stop'
$adb='C:/Users/tranc/AppData/Local/Android/Sdk/platform-tools/adb.exe'
$serial='192.168.1.106:5555'
$package='com.trancong.dexworkspacetouch'
$artifact=Join-Path $PSScriptRoot '../artifact-license-unblock/DWT-SHIZUKU-001-product-qualification.apk'
$expectedHash='708e4df09c1aea4e232e5655b981ef457b96d3b5d85fb36397fee9e1ccd14b68'
$expectedSigner='19ac0ea99125361b3c2083aaa44c8745ebad9c55fa967642c2b9e5a1086a45e7'
$env:JAVA_HOME='C:/Program Files/Android/Android Studio/jbr'
$signerTool='C:/Users/tranc/AppData/Local/Android/Sdk/build-tools/37.0.0/apksigner.bat'
$aapt='C:/Users/tranc/AppData/Local/Android/Sdk/build-tools/37.0.0/aapt2.exe'
function Device([string[]]$Arguments) {
 $out=(& $adb -s $serial @Arguments | Out-String).Trim()
 if($LASTEXITCODE -ne 0){throw 'ADB operation failed'}
 return $out
}
function Certificate([string]$Apk,[string]$EvidenceName) {
 $out=(& $signerTool verify --print-certs $Apk | Out-String)
 if($LASTEXITCODE -ne 0){throw 'APK signature verification failed'}
 $out | Set-Content -LiteralPath (Join-Path $PSScriptRoot $EvidenceName) -Encoding UTF8
 $certs=@([regex]::Matches($out,'(?:Signer #\d+|V\d+ Signer):? certificate SHA-256 digest: ([a-fA-F0-9]{64})') | ForEach-Object {$_.Groups[1].Value.ToLowerInvariant()} | Select-Object -Unique)
 if($certs.Count -ne 1){throw 'Expected exactly one APK signer'}
 return $certs[0]
}
function Metadata([string]$Apk) {
 $out=(& $aapt dump badging $Apk | Out-String)
 if($LASTEXITCODE -ne 0){throw 'APK manifest verification failed'}
 $m=[regex]::Match($out,"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'")
 if(!$m.Success){throw 'APK package/version metadata unavailable'}
 return [ordered]@{Package=$m.Groups[1].Value;VersionCode=[int]$m.Groups[2].Value;VersionName=$m.Groups[3].Value}
}
$model=Device @('shell','getprop','ro.product.model')
if($model -ne 'SM-S918B'){throw 'S23 target model differs from accepted device'}
if((Get-FileHash -LiteralPath $artifact -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expectedHash){throw 'Frozen qualification artifact changed'}
$paths=@((Device @('shell','pm','path',$package)) -split '\r?\n' | Where-Object {$_})
if($paths.Count -ne 1 -or !$paths[0].StartsWith('package:')){throw 'Installed product missing or split-package update requires further review'}
$remote=$paths[0].Substring(8).Trim()
$before=Device @('shell','dumpsys','package',$package)
$apkBefore=Join-Path $PSScriptRoot 'installed-before.apk'
$remoteHash=[regex]::Match((Device @('shell','sha256sum',$remote)),'^[a-fA-F0-9]{64}').Value
if(!$remoteHash){throw 'Installed APK hash unavailable'}
if(!(Test-Path -LiteralPath $apkBefore) -or (Get-FileHash -LiteralPath $apkBefore -Algorithm SHA256).Hash -ne $remoteHash){
 Device @('pull',$remote,$apkBefore) | Out-Null
}
$oldCert=Certificate $apkBefore 'installed-before-signer.txt'
$newCert=Certificate $artifact 'qualification-signer-recheck.txt'
$old=Metadata $apkBefore
$new=Metadata $artifact
if($old.Package -ne $package -or $new.Package -ne $package){throw 'Wrong package identity'}
if($oldCert -ne $expectedSigner -or $newCert -ne $oldCert){throw 'Signer incompatibility: stop before install'}
if($old.VersionCode -ne 9 -or $new.VersionCode -ne $old.VersionCode -or $old.VersionName -ne '1.0.0-beta.8' -or $new.VersionName -ne $old.VersionName){throw 'Version differs: this continuation does not authorize version or downgrade work'}
$uid=[regex]::Match($before,'(?m)^\s*(?:userId|appId)=(\d+)').Groups[1].Value
$first=[regex]::Match($before,'firstInstallTime=([^\r\n]+)').Groups[1].Value.Trim()
$dataDir=[regex]::Match($before,'(?m)^\s*dataDir=([^\r\n]+)').Groups[1].Value.Trim()
if(!$uid -or !$first -or $dataDir -ne '/data/user/0/com.trancong.dexworkspacetouch'){throw 'Data-preservation metadata unavailable'}
$proof=[ordered]@{
 Device='S23';Serial=$serial;Model=$model;Package=$package
 AndroidVersion=(Device @('shell','getprop','ro.build.version.release'));SDK=(Device @('shell','getprop','ro.build.version.sdk'))
 OneUI=(Device @('shell','getprop','ro.build.version.oneui'));BuildFingerprint=(Device @('shell','getprop','ro.build.fingerprint'))
 InstalledVersion=$old;QualificationVersion=$new;InstalledSignerSHA256=$oldCert;QualificationSignerSHA256=$newCert
 InstalledApkSHA256=(Get-FileHash -LiteralPath $apkBefore -Algorithm SHA256).Hash.ToLowerInvariant()
 QualificationApkSHA256=$expectedHash;SameSigner=$true;SameVersion=$true;UpdateInPlaceCompatible=$true
 UserId=$uid;FirstInstallTime=$first;DataDir=$dataDir
 UpdateAttempted=$false;Uninstalled=$false;DataCleared=$false;RecordedAtUtc=[DateTime]::UtcNow.ToString('o')
}
$proof | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $PSScriptRoot 'precheck.json') -Encoding UTF8
Write-Output ($proof | ConvertTo-Json -Compress -Depth 5)
