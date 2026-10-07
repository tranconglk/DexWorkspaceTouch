import {execFileSync} from 'node:child_process';
import {writeFileSync} from 'node:fs';
const [filename,display]=process.argv.slice(2);
if(!/^[a-z0-9-]+\.png$/.test(filename)||!/^\d+$/.test(display))throw new Error('Invalid capture parameters');
const data=execFileSync('C:/Users/tranc/AppData/Local/Android/Sdk/platform-tools/adb.exe',['-s','192.168.1.106:5555','exec-out','screencap','-d',display,'-p'],{windowsHide:true,maxBuffer:18000000});
if(data.readUInt32BE(0)!==0x89504e47)throw new Error('Capture is not PNG');
writeFileSync(new URL(filename,import.meta.url),data);
console.log(filename);
