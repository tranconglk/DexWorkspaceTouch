import assert from 'node:assert/strict';
import {readFileSync,writeFileSync} from 'node:fs';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';

const root=new URL('../',import.meta.url);
const json=url=>JSON.parse(readFileSync(url,'utf8').replace(/^\uFEFF/,''));
const load=name=>json(new URL(name,import.meta.url));
const expected=new Map([
 ['com.android.chrome',[8,8,472,1016]],
 ['com.sec.android.app.sbrowser',[488,8,952,1016]],
 ['com.samsung.android.app.tips',[968,8,1912,1016]],
]);
const same=(a,b)=>JSON.stringify(a)===JSON.stringify(b);
const fixture=t=>[...expected.keys()].some(p=>t.component?.startsWith(p+'/'));
function inspect(label,{repaired=false,hold=false}={}){
 const trace=load(label+'-task-trace.json');
 assert(trace.samples.length>=20,label+': insufficient observation');
 const wrongIndex=trace.samples.findIndex(s=>[...expected].every(([pkg,bounds])=>s.tasks.some(t=>t.component?.startsWith(pkg+'/')&&t.visible&&!same(t.bounds,bounds))));
 assert(wrongIndex>=0,label+': never observed all three wrong together');
 const classic=trace.samples[wrongIndex];
 const ids=new Set();const cells=[];
 for(const [pkg,bounds] of expected){
  const observations=trace.samples.flatMap(s=>s.tasks.filter(t=>t.component?.startsWith(pkg+'/')));
  const taskIds=[...new Set(observations.map(t=>t.id))];
  assert.equal(taskIds.length,1,label+': task identity changed for '+pkg);
  ids.add(taskIds[0]);
  assert(observations.every(t=>t.displayId===14&&t.userId===0&&t.freeform),label+': fixture outside target context');
  assert(trace.samples.every(s=>s.tasks.filter(t=>t.component?.startsWith(pkg+'/')).length<=1),label+': ambiguous task');
  const classicTask=classic.tasks.find(t=>t.component?.startsWith(pkg+'/'));
  const final=trace.samples.at(-1).tasks.filter(t=>t.component?.startsWith(pkg+'/'));
  assert.equal(final.length,1,label+': final task missing');
  assert(final[0].visible,label+': final task not visible');
  if(repaired)assert.deepEqual(final[0].bounds,bounds,label+': final bounds wrong');
  else {
   const settled=trace.samples.slice(wrongIndex).flatMap(s=>s.tasks.filter(t=>t.component?.startsWith(pkg+'/')));
   assert(settled.every(t=>same(t.bounds,classicTask.bounds)),label+': mutation after Classic settled, before click');
   if(hold)assert(trace.samples.every(s=>s.tasks.some(t=>t.id===taskIds[0]&&t.visible)),label+': task disappeared during hold');
  }
  cells.push({package:pkg,taskId:taskIds[0],classicBounds:classicTask.bounds,finalBounds:final[0].bounds});
 }
 const unrelatedIds=new Set(trace.samples.flatMap(s=>s.tasks.filter(t=>!ids.has(t.id)).map(t=>t.id)));
 for(const id of unrelatedIds){
  const observations=trace.samples.flatMap(s=>s.tasks.filter(t=>t.id===id));
  assert(observations.every(t=>same(t.bounds,observations[0].bounds)),label+': unrelated bounds changed for '+id);
 }
 const shizuku=trace.activityStarts.filter(s=>s.component.startsWith('moe.shizuku.privileged.api/'));
 assert.equal(shizuku.length,0,label+': Shizuku app launched');
 return {label,durationMs:trace.durationMs,sampleCount:trace.samples.length,cells,unrelatedBoundsChanges:0,shizukuAppStarts:0,taskIdentityPreserved:true};
}
const automatic=inspect('automatic-home-stable',{repaired:true});
const suggestClassic=inspect('suggest-classic-no-click');
const suggestHold=inspect('suggest-stable-hold',{hold:true});
const suggestRepair=inspect('suggest-real-dock-repair',{repaired:true});
const offClassic=inspect('off-classic-no-click');
const offHold=inspect('off-stable-hold',{hold:true});
const offRepair=inspect('off-real-dock-repair',{repaired:true});
for(const [before,hold,after] of [[suggestClassic,suggestHold,suggestRepair],[offClassic,offHold,offRepair]]){
 for(let i=0;i<3;i++){
  assert.equal(before.cells[i].taskId,hold.cells[i].taskId,'task changed before hold');
  assert.equal(before.cells[i].taskId,after.cells[i].taskId,'task changed before Repair');
  assert.deepEqual(hold.cells[i].finalBounds,after.cells[i].classicBounds,'bounds changed between hold and Repair');
 }
}
const frozen=json(new URL('device-continuation/frozen-source-snapshot.json',root));
const project=new URL('../../',root);
for(const entry of frozen){
 const actual=createHash('sha256').update(readFileSync(new URL(entry.File,project))).digest('hex').toUpperCase();
 assert.equal(actual,entry.SHA256.toUpperCase(),'frozen source changed: '+entry.File);
}
const policyXml=readFileSync(new URL('unit-results/TEST-com.trancong.dexworkspacetouch.feature.car.overlay.WorkspaceRepairPolicyTest.xml',root),'utf8');
assert(policyXml.includes('failures="0"')&&policyXml.includes('errors="0"'),'accepted policy regression failed');
assert(policyXml.includes('name="offDoesNotAssessAndManualRepairStillUsesExistingEngine"'),'missing accepted OFF command-count proof');
const license=load('license-startup-proof.json');
assert(license.UserActivatedThroughNormalLicenseGate&&license.ProductUiObserved&&license.ColdStartupProductUi,'normal licensing not confirmed');
assert.equal(license.LicenseBypass,false);
const artifact=new URL('artifact-license-unblock/DWT-SHIZUKU-001-product-qualification.apk',root);
const artifactHash=createHash('sha256').update(readFileSync(artifact)).digest('hex');
assert.equal(artifactHash,'708e4df09c1aea4e232e5655b981ef457b96d3b5d85fb36397fee9e1ccd14b68');
const result={
 recordedAtUtc:new Date().toISOString(),device:'S22 / SM-S908E / One UI 8',package:'com.trancong.dexworkspacetouch',
 status:'PASS - S22 real product qualification',milestoneStatus:'PARTIAL - S23 real product proof deferred',
 artifactSha256:artifactHash,normalLicenseAndColdStartup:'PASS',
 automatic,suggest:{classic:suggestClassic,hold:suggestHold,repair:suggestRepair},off:{classic:offClassic,hold:offHold,repair:offRepair},
 assessmentEvidence:{automatic:'Frozen policy permits automatic ExistingWorkspaceRepair only after REPAIR_AVAILABLE; real UI showed 3 repaired, 0 correct without Repair click',
  suggest:'Real dock showed Repair available; wrong bounds unchanged before the actual dock Repair click',
  off:'Frozen OFF early return plus accepted offDoesNotAssessAndManualRepairStillUsesExistingEngine zero-command assertion; device mode OFF and unchanged bounds before click',
  internalRpcCounters:'NOT DIRECTLY INSTRUMENTED - signed qualification artifact is non-debuggable; no product instrumentation or source alteration'},
 sourceFreeze:{files:frozen.length,changed:0,acceptedRegressionReused:true},
 observationMethod:'ADB activity-task snapshots and ActivityTaskManager START events; screenshots checked visually; no shell resize, fake callback, license extraction, or isolated application used during proof',
};
writeFileSync(new URL('s22-qualification-summary.json',import.meta.url),JSON.stringify(result,null,2));
console.log(JSON.stringify({status:result.status,sourceFilesUnchanged:frozen.length,automaticTaskIds:automatic.cells.map(c=>c.taskId),suggestTaskIds:suggestRepair.cells.map(c=>c.taskId),offTaskIds:offRepair.cells.map(c=>c.taskId),unrelatedBoundsChanges:0,shizukuAppStarts:0}));
