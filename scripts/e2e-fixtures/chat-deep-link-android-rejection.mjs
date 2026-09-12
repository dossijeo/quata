import {execFile} from 'node:child_process';
import {promisify} from 'node:util';
const exec=promisify(execFile);
const failure=()=>Error('deep_link_android_rejection_unverified');
function epoch(value) {
  if(typeof value!=='string'||!/^\d{10,12}\.\d{1,9}$/.test(value))throw failure();
  const [seconds,fraction]=value.split('.');
  return BigInt(seconds)*1000000000n+BigInt(fraction.padEnd(9,'0'));
}
function configuration(serial,pid) {
  if(!/^emulator-\d+$/.test(serial)||(pid!==undefined&&!/^[1-9]\d{0,9}$/.test(pid)))throw failure();
}

// Capture immediately before external delivery; no log buffer is cleared.
export async function readAndroidRejectionWindowStart({adb,serial,execute=exec}) {
  try {
    configuration(serial);
    const value=(await execute(adb,['-s',serial,'shell','date','+%s.%N'],{windowsHide:true,timeout:10000,maxBuffer:1024})).stdout.trim();
    epoch(value);return value;
  }catch{throw failure();}
}

// Only the product's fixed numeric-status message is eligible for public evidence.
// Everything else (including exception bodies) is discarded, never returned/logged.
export function selectAndroidRefreshRejection({text,pid,startedAt,endedAt}) {
  configuration('emulator-0',pid);
  const start=epoch(startedAt),end=epoch(endedAt);
  if(end<start||typeof text!=='string'||Buffer.byteLength(text)>1024*1024)throw failure();
  for(const line of text.split(/\r?\n/)) {
    const match=/^\s*(\d{10,12}\.\d{1,9})\s+(\d+)\s+(\d+)\s+W\s+SupabaseHttpClient\s*:\s*Supabase session refresh failed with status=(400|401)\s*$/.exec(line);
    if(!match||match[2]!==pid)continue;
    const timestamp=epoch(match[1]);
    if(timestamp>=start&&timestamp<=end)return {observed:true,pid,status:Number(match[4]),timestamp:match[1],startedAt,endedAt};
  }
  throw failure();
}

// Read-only, bounded logcat query. The PID must remain the delivered app's PID.
// A matching response proves one rejection witness, not an exhaustive request count.
export async function readAndroidRefreshRejection({adb,serial,pid,startedAt,execute=exec}) {
  try {
    configuration(serial,pid);epoch(startedAt);
    const options={windowsHide:true,timeout:10000,maxBuffer:1024*1024};
    const currentPid=async()=>(await execute(adb,['-s',serial,'shell','pidof','com.quata'],options)).stdout.trim();
    if(await currentPid()!==pid)throw failure();
    const endedAt=await readAndroidRejectionWindowStart({adb,serial,execute});
    const output=await execute(adb,['-s',serial,'logcat','-d','-v','epoch',`--pid=${pid}`,'-T',startedAt,
      '-s','SupabaseHttpClient:W','*:S'],options);
    if(await currentPid()!==pid)throw failure();
    return selectAndroidRefreshRejection({text:output.stdout,pid,startedAt,endedAt});
  }catch{throw failure();}
}
