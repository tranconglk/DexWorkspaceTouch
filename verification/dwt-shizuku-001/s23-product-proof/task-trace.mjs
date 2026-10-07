import {execFileSync,spawn} from 'node:child_process';
import {writeFileSync} from 'node:fs';
import {parseTasks} from '../s22-product-proof/task-trace.mjs';
const adb='C:/Users/tranc/AppData/Local/Android/Sdk/platform-tools/adb.exe';
const serial='192.168.1.106:5555';
const [label,ms,x,y]=process.argv.slice(2);const duration=Number(ms??'0');
if(!/^[a-z0-9-]+$/.test(label)||!Number.isFinite(duration)||duration<0||duration>30000)throw new Error('Invalid trace parameters');
const samples=[],starts=[];
const snapshot=()=>({atUtc:new Date().toISOString(),tasks:parseTasks(execFileSync(adb,['-s',serial,'shell','dumpsys','activity','activities'],{windowsHide:true,maxBuffer:5000000}).toString())});
const log=spawn(adb,['-s',serial,'logcat','-v','epoch','-T','1','ActivityTaskManager:I','*:S'],{windowsHide:true,stdio:['ignore','pipe','ignore']});
let pending='';log.stdout.on('data',bytes=>{pending+=bytes.toString();const lines=pending.split(/\r?\n/);pending=lines.pop()??'';for(const line of lines){if(!line.includes('START u'))continue;const component=line.match(/\bcmp=([^\s}]+)/)?.[1];if(component)starts.push({at:line.match(/^\s*([\d.]+)/)?.[1]??null,component});}});
const begin=Date.now();samples.push(snapshot());console.log('TASK TRACE READY '+label);
if(x!==undefined){if(!/^\d+$/.test(x)||!/^\d+$/.test(y))throw new Error('Invalid UI click');execFileSync(adb,['-s',serial,'shell','input','-d','6','tap',x,y],{windowsHide:true});}
while(Date.now()-begin<duration){samples.push(snapshot());await new Promise(r=>setTimeout(r,125));}
log.kill();
const result={label,displayId:6,durationMs:Date.now()-begin,samples,activityStarts:starts,input:x===undefined?null:{kind:'real UI tap',displayId:6,x:Number(x),y:Number(y)}};
writeFileSync(new URL(label+'-task-trace.json',import.meta.url),JSON.stringify(result,null,2));
const target=t=>t.component?.startsWith('com.sec.android.app.popupcalculator/')||t.component?.startsWith('com.sec.android.app.clockpackage/');
console.log(JSON.stringify({label,sampleCount:samples.length,first:samples[0].tasks.filter(target),last:samples.at(-1).tasks.filter(target),shizukuAppStarts:starts.filter(s=>s.component.startsWith('moe.shizuku.privileged.api/')).length}));
