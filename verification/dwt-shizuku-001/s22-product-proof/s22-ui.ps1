param([ValidateSet('observe','click','back','swipeUp','swipeDown')][string]$Action='observe',
    [string]$Text='', [string]$Description='', [switch]$Contains, [int]$DisplayId=0)
$ErrorActionPreference='Stop'
$adb='C:/Users/tranc/AppData/Local/Android/Sdk/platform-tools/adb.exe'
$serial='192.168.1.183:5555'
function Read-Ui {
    & $adb -s $serial shell uiautomator dump /sdcard/dwt-qualification-ui.xml | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'UI observation failed' }
    $raw=(& $adb -s $serial shell cat /sdcard/dwt-qualification-ui.xml | Out-String)
    & $adb -s $serial shell rm /sdcard/dwt-qualification-ui.xml
    if(!$raw.Contains('<hierarchy')){throw 'UI hierarchy not ready; reobserve before another action'}
    $xml=[xml]$raw
    return $xml
}
if($Action -eq 'click') {
    $doc=Read-Ui
    $nodes=@($doc.SelectNodes('//node') | Where-Object {
        if($Description){if($Contains){$_.GetAttribute('content-desc').Contains($Description)}else{$_.GetAttribute('content-desc') -eq $Description}}
        else{if($Contains){([string]$_.text).Contains($Text)}else{$_.text -eq $Text}}
    })
    $nodes=@($nodes | Group-Object -Property bounds,class,text | ForEach-Object {$_.Group[0]})
    if($nodes.Count -ne 1){throw "Expected exactly one matching UI node, found $($nodes.Count)"}
    $target=$nodes[0]
    while($target -and $target.clickable -ne 'true'){$target=$target.ParentNode}
    if(!$target -or $target.enabled -ne 'true'){throw 'UI target unavailable'}
    if($target.bounds -notmatch '\[(\d+),(\d+)\]\[(\d+),(\d+)\]'){throw 'UI bounds unavailable'}
    if([int]$matches[3] -le [int]$matches[1] -or [int]$matches[4] -le [int]$matches[2]){throw 'UI target bounds have no area'}
    $x=([int]$matches[1]+[int]$matches[3])/2;$y=([int]$matches[2]+[int]$matches[4])/2
    & $adb -s $serial shell input -d $DisplayId tap ([int]$x) ([int]$y)
} elseif($Action -eq 'back'){& $adb -s $serial shell input -d $DisplayId keyevent 4}
elseif($Action -eq 'swipeUp'){
    if($DisplayId -eq 14){& $adb -s $serial shell input -d $DisplayId swipe 800 790 800 270 650}
    else{& $adb -s $serial shell input -d $DisplayId swipe 540 1950 540 550 650}
}elseif($Action -eq 'swipeDown'){
    if($DisplayId -eq 14){& $adb -s $serial shell input -d $DisplayId swipe 800 270 800 790 650}
    else{& $adb -s $serial shell input -d $DisplayId swipe 540 550 540 1950 650}
}
$doc=Read-Ui
$doc.SelectNodes('//node') | Where-Object {$_.text -or $_.GetAttribute('content-desc') -or $_.class -eq 'android.widget.EditText'} | ForEach-Object {
    [pscustomobject]@{Text=if($_.class -eq 'android.widget.EditText'){'[INPUT]'}else{[regex]::Replace([string]$_.text,'[A-Z][A-Z0-9]{1,15}(?:-[A-Z0-9]{4}){3}','[REDACTED]')};Description=$_.GetAttribute('content-desc');Bounds=$_.bounds;Clickable=$_.clickable;Enabled=$_.enabled}
} | ConvertTo-Json -Compress -Depth 3
