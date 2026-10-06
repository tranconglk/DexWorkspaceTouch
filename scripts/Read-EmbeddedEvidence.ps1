param([Parameter(Mandatory=$true)][string[]]$Artifacts)
$ErrorActionPreference = 'Stop'
$dwtRepo = Split-Path -Parent $PSScriptRoot
$dwtFiles = @($Artifacts | ForEach-Object { Get-Item -LiteralPath $_ })
if ($dwtFiles.Count -gt 8 -or ($dwtFiles | Measure-Object Length -Sum).Sum -gt 1048576) { throw 'Evidence reader input exceeds quota' }
$dwtClasspath = & "$dwtRepo/gradlew.bat" -p $dwtRepo -I "$PSScriptRoot/embedded-evidence-reader.init.gradle" :app:embeddedEvidenceReaderClasspath --offline --console=plain -q
if ($LASTEXITCODE -ne 0) { throw 'Reader classpath unavailable; build local unit-test classes first' }
$dwtClasspath = @($dwtClasspath | Where-Object { $_.Contains([System.IO.Path]::PathSeparator.ToString()) -and $_ -match 'compileDebugUnitTestKotlin|kotlin-classes' })[-1]
if ([string]::IsNullOrWhiteSpace($dwtClasspath)) { throw 'Reader classpath missing' }
& java -cp $dwtClasspath com.trancong.dexworkspacetouch.diagnostics.embedded.offline.EvidenceReader @($dwtFiles.FullName)
if ($LASTEXITCODE -ne 0) { throw 'Offline reader failed' }
