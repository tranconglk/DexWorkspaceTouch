import assert from 'node:assert/strict';
import {readFileSync,writeFileSync} from 'node:fs';
import {createHash} from 'node:crypto';
const root=new URL('../',import.meta.url),project=new URL('../../',root);
const json=url=>JSON.parse(readFileSync(url,'utf8').replace(/^\uFEFF/,''));
const load=name=>json(new URL(name,import.meta.url));
const hash=url=>createHash('sha256').update(readFileSync(url)).digest('hex');
const pre=load('precheck.json'),update=load('update-result.json'),ui=load('assessment-ui-observation.json');
assert(pre.UpdateInPlaceCompatible&&pre.SameSigner&&pre.SameVersion,'update compatibility not proved');
assert.equal(update.UpdateInPlace,'PASS');
assert.equal(update.Uninstalled,false);assert.equal(update.DataCleared,false);
assert.equal(update.AppId,pre.UserId);assert.equal(update.FirstInstallTime,pre.FirstInstallTime);assert.equal(update.DataDir,pre.DataDir);
for(const stage of ['before','after']){
 const rows=load(stage+'-update-ui.json');assert(rows.some(r=>r.Text==='Workspace Library'),stage+': normal licensed UI absent');
}
const before=load('library-before.dwtbundle'),after=load('library-after.dwtbundle');
assert.equal(before.workspaceCount,13);assert.equal(after.workspaceCount,13);assert.deepEqual(before.workspaces,after.workspaces,'user workspace data changed during update');
const artifact=new URL('artifact-license-unblock/DWT-SHIZUKU-001-product-qualification.apk',root);
const artifactHash=hash(artifact);assert.equal(artifactHash,'708e4df09c1aea4e232e5655b981ef457b96d3b5d85fb36397fee9e1ccd14b68');
assert.equal(update.InstalledApkSHA256,artifactHash);assert.equal(pre.QualificationApkSHA256,artifactHash);
assert.equal(update.SignerSHA256,'19ac0ea99125361b3c2083aaa44c8745ebad9c55fa967642c2b9e5a1086a45e7');
assert.equal(update.VersionCode,9);assert.equal(update.VersionName,'1.0.0-beta.8');
assert.equal(ui.mode,'AUTOMATIC');assert.equal(ui.assessmentStatus,'LAYOUT_CORRECT');assert.equal(ui.dockDetail,'Layout already correct');
assert.equal(ui.manualRepairClicked,false);assert.equal(ui.layoutPreparedWithShellResize,false);assert.equal(ui.isolatedApplicationUsed,false);
const trace=load('automatic-correct-classic-task-trace.json');
assert(trace.durationMs>=20000&&trace.samples.length>=40,'observation window insufficient');
assert.deepEqual(trace.input,{kind:'real UI tap',displayId:6,x:354,y:508});
const expected=new Map([
 ['com.sec.android.app.popupcalculator',[8,8,952,1136]],
 ['com.sec.android.app.clockpackage',[968,8,1912,1136]],
]);
const cells=[],fixtureIds=new Set();let bothVisibleSince=null;
for(const [pkg,bounds] of expected){
 const observations=trace.samples.flatMap(s=>s.tasks.filter(t=>t.component?.startsWith(pkg+'/')));
 const ids=[...new Set(observations.map(t=>t.id))];assert.equal(ids.length,1,'fixture identity changed');
 fixtureIds.add(ids[0]);
 assert(trace.samples.every(s=>s.tasks.filter(t=>t.component?.startsWith(pkg+'/')).length<=1),'ambiguous fixture task');
 assert(observations.length>0);
 assert(observations.every(t=>t.displayId===6&&t.userId===0&&t.freeform&&t.visible),'fixture context or visibility changed');
 assert(observations.every(t=>JSON.stringify(t.bounds)===JSON.stringify(bounds)),'Classic bounds changed or deviated');
 const final=trace.samples.at(-1).tasks.filter(t=>t.component?.startsWith(pkg+'/'));assert.equal(final.length,1);
 const firstSample=trace.samples.find(s=>s.tasks.some(t=>t.id===ids[0]));
 cells.push({package:pkg,taskId:ids[0],expectedBounds:bounds,firstObservedBounds:observations[0].bounds,finalBounds:final[0].bounds,maxDeviationPx:0,boundsChanges:0,firstSeenUtc:firstSample.atUtc});
}
for(const s of trace.samples){if([...fixtureIds].every(id=>s.tasks.some(t=>t.id===id))){bothVisibleSince=s.atUtc;break;}}
assert(bothVisibleSince);
const stableSamples=trace.samples.filter(s=>s.atUtc>=bothVisibleSince);
assert(stableSamples.every(s=>[...fixtureIds].every(id=>s.tasks.some(t=>t.id===id))),'task disappeared in observation window');
const durationStable=Date.parse(trace.samples.at(-1).atUtc)-Date.parse(bothVisibleSince);assert(durationStable>=15000);
const base=trace.samples[0].tasks.filter(t=>!fixtureIds.has(t.id));
const identity=t=>({id:t.id,displayId:t.displayId,userId:t.userId,component:t.component,bounds:t.bounds,freeform:t.freeform});
const original=new Map(base.map(t=>[t.id,identity(t)]));
for(const sample of trace.samples){
 const unrelated=sample.tasks.filter(t=>!fixtureIds.has(t.id));assert.equal(unrelated.length,original.size,'unrelated task created or removed');
 for(const t of unrelated)assert.deepEqual(identity(t),original.get(t.id),'unrelated task mutation');
}
assert.equal(trace.activityStarts.filter(s=>s.component.startsWith('moe.shizuku.privileged.api/')).length,0,'Shizuku app launch');
assert(trace.activityStarts.every(s=>[...expected.keys()].some(pkg=>s.component.startsWith(pkg+'/'))),'unexpected activity launch in proof');
const policyXml=readFileSync(new URL('unit-results/TEST-com.trancong.dexworkspacetouch.feature.car.overlay.WorkspaceRepairPolicyTest.xml',root),'utf8');
assert(policyXml.includes('failures="0"')&&policyXml.includes('errors="0"')&&policyXml.includes('name="automaticCorrectLayoutOnlyReadsOnceAndDoesNotInvokeRepair"'),'accepted zero-attempt policy evidence absent');
const session=readFileSync(new URL('app/src/main/java/com/trancong/dexworkspacetouch/feature/car/overlay/WorkspaceRepairSession.kt',project),'utf8');
assert(session.includes('report.status == WorkspaceAssessmentStatus.REPAIR_AVAILABLE'),'automatic admission policy changed');
const frozen=json(new URL('device-continuation/frozen-source-snapshot.json',root));
for(const entry of frozen)assert.equal(hash(new URL(entry.File,project)).toUpperCase(),entry.SHA256.toUpperCase(),'frozen source changed: '+entry.File);
const s22=json(new URL('s22-product-proof/s22-qualification-summary.json',root));assert.equal(s22.status,'PASS - S22 real product qualification');assert.equal(s22.artifactSha256,artifactHash);
const result={status:'PASS - PRODUCT INTEGRATION VIABLE / READY FOR COMMIT REVIEW',
 deviceStatus:{S22:'PASS retained',S23:'PASS real product'},environment:pre,
 updateCompatibility:'PASS - same official signer/package/version, adb install -r',
 dataPreservation:{originalWorkspaceCount:13,originalWorkspaceContentsIdentical:true,appIdUnchanged:true,firstInstallTimeUnchanged:true,normalLicensedStartupBeforeAndAfter:true,reactivationRequired:false,licenseBypass:false,uninstall:false,clearData:false},
 mode:'AUTOMATIC',assessment:'LAYOUT_CORRECT',cells,
 observation:{durationMs:trace.durationMs,sampleCount:trace.samples.length,bothTasksStableForMs:durationStable,taskIdsStable:true,targetBoundsChanges:0,unrelatedTaskMutations:0,shizukuAppStarts:0,automaticRepairAttempts:0,taskResizes:0},
 zeroAttemptEvidence:'Runtime real-dock LAYOUT_CORRECT plus unchanged REPAIR_AVAILABLE-only automatic admission and accepted automaticCorrectLayoutOnlyReadsOnceAndDoesNotInvokeRepair regression; no Manual Repair click',
 counterInstrumentation:'No internal RPC counter injected into frozen non-debuggable artifact. Zero resize/attempt conclusion combines runtime assessment, exact stable task traces and verified unchanged policy.',
 sourceFreeze:{files:frozen.length,changed:0},artifactSha256:artifactHash,versionCode:9,versionName:'1.0.0-beta.8',newBuildPerformed:false,commit:false,push:false,tag:false,recordedAtUtc:new Date().toISOString()};
writeFileSync(new URL('s23-qualification-summary.json',import.meta.url),JSON.stringify(result,null,2));
console.log(JSON.stringify({status:result.status,taskIds:cells.map(c=>c.taskId),expectedActual:cells.map(c=>({taskId:c.taskId,expected:c.expectedBounds,actual:c.finalBounds,deviationPx:c.maxDeviationPx})),observation:result.observation,sourceFilesUnchanged:frozen.length,originalWorkspacesPreserved:13}));
