param([string]$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path)
$ErrorActionPreference = 'Stop'
$failureList = [System.Collections.Generic.List[string]]::new()
$main = Join-Path $ProjectRoot 'app/src/main/java/com/trancong/dexworkspacetouch'
$control = Join-Path $main 'platform/launch/shizuku'
$required = @('WorkspaceTaskCorrelation.kt', 'ExistingWorkspaceRepair.kt', 'AndroidExistingWorkspaceRepair.kt',
    'AndroidShizukuCommandTransport.kt', 'ShizukuCommandTransport.kt', 'WorkspaceCommandUserService.kt',
    'WorkspaceCommandDispatcher.kt', 'WorkspaceCommandDeadline.kt', 'WorkspaceCommandProcess.kt')
foreach ($file in $required) {
    if (!(Test-Path -LiteralPath (Join-Path $control $file))) { $failureList.Add("Missing product component: $file") }
}
$sources = @(Get-ChildItem -LiteralPath $control -Filter '*.kt')
foreach ($source in $sources) {
    $content = Get-Content -LiteralPath $source.FullName -Raw -Encoding UTF8
    if ($content -match 'ShizukuWorkspacePrototype|PrototypeProcessTransport|PrototypeShell|PrototypeTask|newProcess|sw001|swc00|verification/') {
        $failureList.Add("Prototype/evidence dependency: $($source.Name)")
    }
    if ($content -match '"start"|"sh"|"bash"|"force-stop"|selectNew|launchCommand') {
        $failureList.Add("Launch/arbitrary shell on product control path: $($source.Name)")
    }
    if ($content -match 'workspace.execution.embedded|feature.embeddedapp|EmbeddedWorkspaceRunner|EmbeddedAppService') {
        $failureList.Add("Embedded runtime dependency: $($source.Name)")
    }
}
$coordinator = Get-Content -LiteralPath (Join-Path $main 'feature/car/overlay/CarFloatingDockCoordinator.kt') -Raw -Encoding UTF8
if ($coordinator -match 'BuildConfig.DEBUG') { $failureList.Add('Product dock Repair is DEBUG-gated') }
$navigation = Get-Content -LiteralPath (Join-Path $main 'navigation/TouchNavigation.kt') -Raw -Encoding UTF8
if ($navigation -match 'ShizukuWorkspacePrototype|SW-001|workspaceRepairMode\s*=\s*if\s*\(BuildConfig.DEBUG\)') {
    $failureList.Add('Prototype route or DEBUG-gated product settings')
}
foreach ($symbol in @('onClassicLaunchStarted', 'onClassicLaunchCompleted', 'onWorkspaceRepairModeChanged')) {
    if ($navigation -notmatch $symbol) { $failureList.Add("Missing product navigation integration: $symbol") }
}
$protected = @('app/build.gradle.kts', 'gradle', 'app/src/main/AndroidManifest.xml',
    'app/src/main/java/com/trancong/dexworkspacetouch/license',
    'app/src/main/java/com/trancong/dexworkspacetouch/workspace/persistence',
    'app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedworkspace',
    'app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedapp',
    'app/src/main/java/com/trancong/dexworkspacetouch/ui/screens/HomeScreen.kt')
Push-Location $ProjectRoot
try {
    $protectedChanges = & git diff --name-only b04288e69a0f8c2057e185b68a1df9ae8fcd0b80 -- @protected
    if ($LASTEXITCODE -ne 0) { throw 'Unable to verify baseline' }
    foreach ($file in $protectedChanges) { $failureList.Add("Protected baseline changed: $file") }
} finally { Pop-Location }
if ($failureList.Count) { $failureList | Write-Output; exit 1 }
Write-Output 'PASS: production control closure, UI wiring, no prototype/launch/Embedded runtime dependency; protected release baseline unchanged.'
