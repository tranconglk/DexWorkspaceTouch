import {execFileSync,spawn} from 'node:child_process';
import {writeFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
const adb='C:/Users/tranc/AppData/Local/Android/Sdk/platform-tools/adb.exe';
const serial='192.168.1.183:5555';
export function parseTasks(dump){
 const result=[];let display=-1,task=null;
 const finish=()=>{if(task)result.push(task);task=null;};
 for(const line of dump.split(/\r?\n/)){
  if(line.startsWith('ActivityTaskSupervisor state:'))break;
  let m=line.match(/^Display #(\d+)/);if(m){finish();display=Number(m[1]);continue;}
  m=line.match(/^\s*\* Task\{[^#]*#(\d+)\b.*/);
  if(m){finish();task={id:Number(m[1]),displayId:display,userId:Number(line.match(/\bU=(\d+)\b/)?.[1]??-1),component:null,bounds:null,freeform:line.includes('mode=freeform'),visible:/\bvisible=true\b/.test(line)};continue;}
  if(!task)continue;
  const trim=line.trim();m=trim.match(/^mBounds=Rect\((\d+), (\d+) - (\d+), (\d+)\)/);
  if(m)task.bounds=m.slice(1).map(Number);
  if(trim.startsWith('Intent {')&&!task.component){m=trim.match(/\bcmp=([^\s}]+)/);if(m){let[p,a]=m[1].split('/');if(a.startsWith('.'))a=p+a;task.component=p+'/'+a;}}
 }finish();return result;
}
export function snapshot(){return {atUtc:new Date().toISOString(),tasks:parseTasks(execFileSync(adb,['-s',serial,'shell','dumpsys','activity','activities'],{windowsHide:true,maxBuffer:4000000}).toString())};}
if(process.argv[1]===fileURLToPath(import.meta.url)){
 const label=process.argv[2];const duration=Number(process.argv[3]??'8000');
 if(!/^[a-z0-9-]+$/.test(label)||duration>30000||duration<0)throw new Error('Invalid trace parameters');
 const samples=[];const starts=[];const events=spawn(adb,['-s',serial,'logcat','-v','epoch','-T','1','ActivityTaskManager:I','*:S'],{windowsHide:true,stdio:['ignore','pipe','ignore']});
 let buf='';events.stdout.on('data',c=>{buf+=c.toString();const lines=buf.split(/\r?\n/);buf=lines.pop()??'';for(const line of lines){if(!line.includes('START u'))continue;const cmp=line.match(/\bcmp=([^\s}]+)/)?.[1];if(cmp)starts.push({at:line.match(/^\s*([\d.]+)/)?.[1]??null,component:cmp});}});
 const begin=Date.now();samples.push(snapshot());console.log('TASK TRACE READY '+label);
 if(process.argv[4])execFileSync(adb,['-s',serial,'shell','input','-d','14','tap',process.argv[4],process.argv[5]],{windowsHide:true});
 while(Date.now()-begin<duration){samples.push(snapshot());await new Promise(r=>setTimeout(r,125));}
 events.kill();
 const target=fileURLToPath(new URL('./'+label+'-task-trace.json',import.meta.url));
 writeFileSync(target,JSON.stringify({label,durationMs:Date.now()-begin,samples,activityStarts:starts},null,2));
 const packages=['com.android.chrome','com.sec.android.app.sbrowser','com.samsung.android.app.tips'];
 console.log(JSON.stringify({label,samples:samples.length,first: samples[0].tasks.filter(t=>packages.some(p=>t.component?.startsWith(p+'/'))),last:samples.at(-1).tasks.filter(t=>packages.some(p=>t.component?.startsWith(p+'/'))),shizukuAppStarts:starts.filter(s=>s.component.startsWith('moe.shizuku.privileged.api/')).length}));
}
