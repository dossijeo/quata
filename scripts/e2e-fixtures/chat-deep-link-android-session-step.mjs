import {spawn,execFile} from "node:child_process";
import {promisify} from "node:util";
import {randomUUID} from "node:crypto";
import {connect} from "node:net";
import {writeFile,open,mkdir} from "node:fs/promises";
import {unlinkSync} from "node:fs";
import path from "node:path";
import {isDeepStrictEqual} from 'node:util';
import {validateOwnedNativeSessionReceipt} from './chat-deep-link-owned-session.mjs';
const exec = promisify(execFile);
const pause = ms => new Promise(resolve => setTimeout(resolve,ms));

export async function retireAndroidDeepLinkForward({adb,serial,port,execute=exec}) {
  try {
    await execute(adb,["-s",serial,"forward","--remove",`tcp:${port}`],{windowsHide:true,timeout:10000});
    const {stdout}=await execute(adb,["forward","--list"],{windowsHide:true,timeout:10000});
    if(stdout.split(/\r?\n/).some(line=>{
      const fields=line.trim().split(/\s+/);
      return fields[0]===serial&&fields[1]===`tcp:${port}`;
    }))throw Error();
  } catch {throw Error("deep_link_android_forward_cleanup_unresolved");}
}

// Caller owns the dedicated AVD and durable private custody journal before install/clear.
// Only a random socket name enters argv. Private input goes directly to the local socket.
// One request, no replay. Success requires exact receipt AND terminal JUnit success.
export async function runAndroidDeepLinkSessionStep({adb,serial,input,logPath}) {
  if(!/^emulator-\d+$/.test(serial)||!["probe-empty","install","clear","install-expired","clear-expired","read-owned"].includes(input?.stage))
    throw Error("deep_link_android_step_configuration_invalid");
  if(input.stage==="read-owned"&&(Object.keys(input).sort().join(",")!=="authUserId,profileId,runId,stage,stepId"||
    ["runId","stepId","profileId","authUserId"].some(key=>!/^([0-9a-f]{8})(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/.test(input[key]))))
    throw Error("deep_link_android_step_configuration_invalid");
  const name=`quata-deeplink-${randomUUID()}`;
  let port,child,socket,timer,output="",finished=false,exitCode,receipt;
  try {
    port=(await exec(adb,["-s",serial,"forward","tcp:0",`localabstract:${name}`],{windowsHide:true,timeout:10000})).stdout.trim();
    if(!/^\d+$/.test(port))throw Error();
    // Non-secret ownership receipt survives an interrupted/failed forwarding cleanup.
    await writeFile(`${logPath}.forward.json`,JSON.stringify({serial,port,socket:name,stage:"allocated"}),{flag:"wx"});
    child=spawn(adb,["-s",serial,"shell","am","instrument","-w","-r","-e","custodySocket",name,
      "-e","class","com.quata.core.navigation.DeepLinkSessionCustodyInstrumentedTest#onePrivateSessionStep",
      "com.quata.test/com.quata.core.navigation.DeepLinkSessionCustodyRunner"],{windowsHide:true,stdio:["ignore","pipe","pipe"]});
    const terminal=new Promise(resolve=>{
      child.once("error",()=>{finished=true;exitCode=-1;resolve();});
      child.once("close",code=>{finished=true;exitCode=code;resolve();});
    });
    for(const stream of [child.stdout,child.stderr])stream.on("data",bytes=>{if(output.length<100000)output+=bytes.toString();});
    timer=setTimeout(()=>{socket?.destroy();child.kill();},100000);
    const deadline=Date.now()+60000;
    // adb forward accepts TCP before its abstract target exists. Wait for the test's
    // bound-socket receipt first, then make exactly one connection and one request.
    while(Date.now()<deadline&&!finished&&!output.includes(`deepLinkCustodySocketReady=${name}`))await pause(100);
    if(!output.includes(`deepLinkCustodySocketReady=${name}`)||finished)throw Error();
    socket=await new Promise(resolve=>{
      const candidate=connect({host:"127.0.0.1",port:Number(port)});
      const deadline=setTimeout(()=>{candidate.destroy();resolve(null);},5000);
      candidate.once("connect",()=>{clearTimeout(deadline);resolve(candidate);});
      candidate.once("error",()=>{clearTimeout(deadline);candidate.destroy();resolve(null);});
    });
    if(!socket)throw Error();
    receipt=await new Promise((resolve,reject)=>{
      let response="",settled=false;
      const fail=()=>{if(!settled){settled=true;reject(Error());}};
      socket.setTimeout(35000,fail);
      socket.on("error",fail);socket.on("close",fail);socket.on("end",()=>{if(!response.includes("\n"))fail();});
      socket.on("data",bytes=>{
        response+=bytes.toString();
        if(response.length>(input.stage==="read-owned"?32768:4096))return fail();
        if(response.includes("\n")&&!settled) {
          try {const value=JSON.parse(response.trim());settled=true;resolve(value);}catch{fail();}
        }
      });
      socket.write(JSON.stringify(input)+"\n");
    });
    socket.destroy();
    await terminal;
    await writeFile(logPath,output,{flag:"wx"});
    if(exitCode!==0||!output.includes("OK (1 test)")||!receipt||
      Object.keys(receipt).sort().join(",")!==(input.stage==="read-owned"?"privateSession,runId,stage,stepId,verified":"runId,stage,stepId,verified")||receipt.verified!==true||
      ["runId","stepId","stage"].some(key=>receipt[key]!==input[key]))throw Error();
    if(input.stage==="read-owned")validateAndroidOwnedSessionReceipt({input,receipt});
    let pid;
    try {pid=(await exec(adb,["-s",serial,"shell","pidof","com.quata"],{windowsHide:true,timeout:10000})).stdout.trim();}
    catch(error) {
      if(error.code===1&&!error.stdout?.trim()&&!error.stderr?.trim())pid="";
      else throw Error();
    }
    if(pid)throw Error(); // Terminal adb alone is not evidence that the passive host ended.
    return receipt;
  } catch {
    throw Error("deep_link_android_session_step_unresolved");
  } finally {
    clearTimeout(timer);socket?.destroy();
    if(child&&!finished)child.kill();
    if(output)await writeFile(logPath,output,{flag:"wx"}).catch(()=>{});
    if(port&&/^\d+$/.test(port)) {
      await retireAndroidDeepLinkForward({adb,serial,port});
      await writeFile(`${logPath}.forward.json`,JSON.stringify({serial,port,socket:name,stage:"retired_verified"}));
    }
  }
}

// Structural ownership check only. Caller verifies Auth remotely and persists this
// private return value before cleanup; do not include it in public reports or logs.
export function validateAndroidOwnedSessionReceipt({input,receipt}) {
  try {
    return validateOwnedNativeSessionReceipt({input,receipt});
  } catch {throw Error("deep_link_android_owned_session_receipt_invalid");}
}

/** A single exclusively leased Android custody lifecycle; uncertainty retains the lease. */
export async function openAndroidDeepLinkSessionChannel({adb,serial,leasePath,evidenceDirectory,stepImpl=runAndroidDeepLinkSessionStep}) {
  if(!path.isAbsolute(leasePath)||!path.isAbsolute(evidenceDirectory))throw Error("deep_link_android_lease_path_invalid");
  const lease=await open(leasePath,"wx",0o600),runId=randomUUID();
  let phase="ready",uncertain=false,closed=false,aborted=false,expiryInput,renewedSnapshot;
  const expirySteps=new Set();
  const step=async input=>stepImpl({adb,serial,input,
    logPath:path.join(evidenceDirectory,`session-${input.stepId}.log`)});
  const probe=()=>step({runId,stepId:randomUUID(),stage:"probe-empty"});
  try {
    await lease.writeFile(JSON.stringify({runId,serial,pid:process.pid,purpose:"FLOW-DEEP-LINKS session custody"}));
    await lease.sync();await mkdir(evidenceDirectory,{recursive:true});await probe();
  } catch {await lease.close();throw Error("deep_link_android_preflight_unresolved");}
  return {
    async sessionStep(input) {
      if(aborted||uncertain||closed||!((phase==="ready"&&['install','install-expired'].includes(input.stage))||
        (phase==="installed"&&input.stage==="clear")||(phase==='expired-installed'&&input.stage==='read-owned')||
        (phase==='renewed-read'&&input.stage==='clear')))
        throw Error("deep_link_android_custody_order_invalid");
      if(input.stage==='install-expired'||expiryInput) {
        if(typeof input.stepId!=='string'||!input.stepId||expirySteps.has(input.stepId))throw Error('deep_link_android_custody_order_invalid');
        if(input.stage==='read-owned'&&!isDeepStrictEqual(input,{runId:expiryInput.runId,stepId:input.stepId,
          stage:'read-owned',profileId:expiryInput.profileId,authUserId:expiryInput.authUserId}))throw Error('deep_link_android_custody_order_invalid');
        if(input.stage==='clear'&&!isDeepStrictEqual(input,{runId:expiryInput.runId,stepId:input.stepId,
          stage:'clear',...renewedSnapshot}))throw Error('deep_link_android_custody_order_invalid');
        expirySteps.add(input.stepId);
      }
      uncertain=true;
      const receipt=await step(input);
      if(aborted)throw Error("deep_link_android_custody_aborted");
      if(input.stage==='install-expired') {
        if(!isDeepStrictEqual(receipt,{runId:input.runId,stepId:input.stepId,stage:input.stage,verified:true}))throw Error('deep_link_android_custody_receipt_invalid');
        expiryInput=structuredClone(input);phase='expired-installed';
      } else if(input.stage==='read-owned') {
        validateAndroidOwnedSessionReceipt({input,receipt});
        const snapshot=receipt.privateSession;
        if(['profileId','authUserId','authSessionId','email','displayName','isOfficial'].some(key=>snapshot[key]!==expiryInput[key])||
          snapshot.accessToken===expiryInput.accessToken||snapshot.refreshToken===expiryInput.refreshToken)
          throw Error('deep_link_android_custody_receipt_invalid');
        renewedSnapshot=structuredClone(snapshot);phase='renewed-read';
      } else {
        if(expiryInput&&!isDeepStrictEqual(receipt,{runId:input.runId,stepId:input.stepId,stage:'clear',verified:true}))throw Error('deep_link_android_custody_receipt_invalid');
        phase=input.stage==="install"?"installed":"cleared";
      }
      uncertain=false;
      return receipt;
    },
    async close() {
      if(aborted||closed||uncertain||!['ready','cleared'].includes(phase))throw Error("deep_link_android_close_unresolved");
      uncertain=true;
      await probe();
      if(aborted)throw Error("deep_link_android_custody_aborted");
      await lease.close();
      if(aborted)throw Error("deep_link_android_custody_aborted");
      // No await between the final abort check, retiring the lease, and marking closed.
      unlinkSync(leasePath);
      closed=true;uncertain=false;
    },
    settled:()=>closed&&!uncertain&&!aborted,
    abort() {if(closed)return;aborted=true;uncertain=true;lease.close().catch(()=>{});},
  };
}
