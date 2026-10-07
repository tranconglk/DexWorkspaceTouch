import {readFileSync,writeFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
const expected=new Map([
 ['com.android.chrome',[8,8,472,1016]],
 ['com.sec.android.app.sbrowser',[488,8,952,1016]],
 ['com.samsung.android.app.tips',[968,8,1912,1016]],
]);
const target=t=>[...expected.keys()].some(p=>t.component?.startsWith(p+'/'));
const equal=(a,b)=>JSON.stringify(a)===JSON.stringify(b);
for(const label of process.argv.slice(2)){
 const path=fileURLToPath(new URL('./'+label+'-task-trace.json',import.meta.url));
 const trace=JSON.parse(readFileSync(path));const final=trace.samples.at(-1).tasks;
 const cells=[];
 for(const [pkg,want] of expected){
  let previous='';const transitions=[];const ids=new Set();let duplicates=false;
  for(const s of trace.samples){const ts=s.tasks.filter(t=>t.component?.startsWith(pkg+'/'));ts.forEach(t=>ids.add(t.id));duplicates ||=ts.length>1;
   const key=JSON.stringify(ts.map(t=>({id:t.id,bounds:t.bounds,displayId:t.displayId,visible:t.visible})).sort((a,b)=>a.id-b.id));
   if(key!==previous){transitions.push({atUtc:s.atUtc,tasks:JSON.parse(key)});previous=key;}
  }
  const last=final.filter(t=>t.component?.startsWith(pkg+'/'));
  cells.push({package:pkg,expected:want,taskIds:[...ids],uniqueTaskIdentity:ids.size===1&&!duplicates,finalCorrect:last.length===1&&last[0].displayId===14&&last[0].visible&&equal(last[0].bounds,want),wrongBoundsObserved:transitions.some(s=>s.tasks.some(t=>t.bounds&&!equal(t.bounds,want))),transitions});
 }
 const changes=[];const base=trace.samples[0].tasks.filter(t=>!target(t));
 for(const t of base){const observed=new Set(trace.samples.map(s=>s.tasks.find(a=>a.id===t.id)).filter(Boolean).map(a=>JSON.stringify(a.bounds)));if(observed.size>1)changes.push({taskId:t.id,component:t.component,bounds:[...observed].map(JSON.parse)});}
 const result={label,sampleCount:trace.samples.length,durationMs:trace.durationMs,cells,unrelatedBoundsChanges:changes,shizukuAppStarts:trace.activityStarts.filter(s=>s.component.startsWith('moe.shizuku.privileged.api/')),activityStarts:trace.activityStarts};
 writeFileSync(fileURLToPath(new URL('./'+label+'-analysis.json',import.meta.url)),JSON.stringify(result,null,2));
 console.log(JSON.stringify({label,sampleCount:result.sampleCount,cells:cells.map(({transitions,...c})=>c),unrelatedBoundsChanges:changes,shizukuAppStarts:result.shizukuAppStarts.length}));
}
