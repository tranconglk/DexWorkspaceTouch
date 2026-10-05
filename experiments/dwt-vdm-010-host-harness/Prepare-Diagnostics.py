"""Explicit qualification-only source mirror with reversible observer insertions."""
import hashlib,json,re
from pathlib import Path
root=Path(__file__).resolve().parents[2]
source=root/'app/src/main/java'
q='com.trancong.dexworkspacetouch.qualification.QualificationDiagnostics'
patches={}
def add(name,anchor,code,position='after'):
    patches.setdefault(name,[]).append((anchor,code,position))
add('RemoteSessionRuntime.kt','    private var association: SessionAssociation? = null',
    '    private val qualificationSessionId = sessionId')
add('RemoteSessionRuntime.kt','    val inputDeviceName = virtualInputDeviceName(sessionId)',
    '    init { '+q+'.registerInput(qualificationSessionId, inputDeviceName) }')
add('RemoteSessionRuntime.kt','        return try {',
    '        var qualificationStage = "entry"\n        '+q+'.surface("remote_start_entry", qualificationSessionId, surface)','before')
add('RemoteSessionRuntime.kt','            association = AssociationShell.create().also { hasLiveResources = true }',
    '            qualificationStage = "display_create"')
add('RemoteSessionRuntime.kt','            val input = vdm.prepareTouchscreen()','            qualificationStage = "touchscreen_prepare"','before')
add('RemoteSessionRuntime.kt','            waitForStableFingerConfig(display.getInt("displayId"))',
    '            qualificationStage = "input_stability"\n            '+q+'.surface("input_wait_begin", qualificationSessionId, surface)','before')
add('RemoteSessionRuntime.kt','            waitForStableFingerConfig(display.getInt("displayId"))',
    '            '+q+'.surface("input_wait_return", qualificationSessionId, surface)\n            qualificationStage = "target_launch"')
add('RemoteSessionRuntime.kt','        } catch (error: Throwable) {',
    '            '+q+'.remoteFailure(qualificationSessionId, qualificationStage, error, surface)')
add('RemoteSessionRuntime.kt','            check(section.isNotEmpty())',
    '            '+q+'.inputSample(qualificationSessionId, displayId, section, previous, equal, end)','before')
add('EmbeddedAppVdm.kt','        check(receivedSurface?.isValid == true) { "Received Surface became invalid" }',
    '        '+q+'.launchSurface(expectedInputDeviceName, receivedSurface)','before')
add('EmbeddedAppSession.kt','    fun startSession(surface: Surface) {',
    '        '+q+'.surface("local_start_entry", sessionId.value, surface)')
add('EmbeddedAppSession.kt','    private fun onStartCompleted(value: StartCompletion) {',
    '        '+q+'.completion(sessionId.value, value.success, value.error, value.operation)')
add('EmbeddedWorkspaceRunner.kt','        ownedItems[sourceCellId] = item',
    '        '+q+'.register(sourceCellId, handle.sessionId.value, prepared.executionSurface)')
add('EmbeddedWorkspaceRunner.kt','        item.latestSnapshot = event.snapshot',
    '        '+q+'.snapshot(event.sourceCellId, item.handle.sessionId.value, event.snapshot)')
add('EmbeddedWorkspaceRunner.kt','            startReply?.complete(EmbeddedWorkspaceRunResult.Started(activeReceipts()))',
    '            '+q+'.result(EmbeddedWorkspaceRunResult.Started(activeReceipts()))','before')
add('EmbeddedWorkspaceRunner.kt','    private fun terminate(result: EmbeddedWorkspaceRunResult) {',
    '        '+q+'.result(result)')
add('EmbeddedWorkspaceProductScreen.kt','    var loaded by remember(workspaceId) { mutableStateOf<EmbeddedEligibilityResult?>(null) }',
    '    androidx.compose.runtime.SideEffect { '+q+'.eligibility(loaded) }')
add('EmbeddedWorkspaceProductScreen.kt','    val readiness by refresh.state.collectAsState()',
    '    androidx.compose.runtime.SideEffect { '+q+'.readiness("observation", readiness) }')
add('EmbeddedWorkspaceProductScreen.kt','    val readinessSnapshot by refresh.state.collectAsState()',
    '    androidx.compose.runtime.SideEffect { '+q+'.readiness("execution", readinessSnapshot) }')
files=sorted(p for p in source.rglob('*') if p.is_file())
digest=hashlib.sha256()
for p in files: digest.update(str(p.relative_to(source)).encode()); digest.update(p.read_bytes())
digest.update(json.dumps(patches,sort_keys=True).encode())
identity=digest.hexdigest()
dest=root/'app/build/generated/vdm010-diagnostic-main'/identity[:16]
entries=[]
for p in files:
    original=p.read_bytes()
    updated=original.decode('utf-8') if p.name in patches else None
    if updated is not None:
        newline='\r\n' if '\r\n' in updated else '\n'
        for i,(anchor,code,position) in enumerate(patches[p.name]):
            assert updated.count(anchor)==1,(p.name,anchor,updated.count(anchor))
            block=newline.join(['// VDM010_DIAGNOSTIC_BEGIN '+str(i)]+code.splitlines()+['// VDM010_DIAGNOSTIC_END '+str(i)])+newline
            updated=updated.replace(anchor,block+anchor if position=='before' else anchor+newline+block.rstrip('\r\n'))
        stripped=re.sub(r'// VDM010_DIAGNOSTIC_BEGIN [^\r\n]*\r?\n.*?// VDM010_DIAGNOSTIC_END [^\r\n]*\r?\n','',updated,flags=re.S)
        assert stripped.encode('utf-8')==original,p.name
        result=updated.encode('utf-8')
        entries.append({'path':str(p.relative_to(source)).replace('\\','/'),'original_sha256':hashlib.sha256(original).hexdigest(),
            'overlay_sha256':hashlib.sha256(result).hexdigest(),'strip_observers_equals_original_bytes':True,'insertions':len(patches[p.name])})
    else: result=original
    out=dest/p.relative_to(source); out.parent.mkdir(parents=True,exist_ok=True); out.write_bytes(result)
manifest={'classification':'NEW_INDEPENDENT_QUALIFICATION_ONLY','identity':identity,'source_dir':str(dest),
 'production_source_not_modified':True,'copied_files':len(files),'patched_copies':entries}
(root/'experiments/dwt-vdm-010-host-harness/diagnostic-overlay-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
print(json.dumps(manifest,indent=2))
