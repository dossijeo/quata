import {spawn,execFile} from 'node:child_process';
import {promisify} from 'node:util';
import {connect} from 'node:net';
import {randomUUID} from 'node:crypto';
import {writeFile,mkdir} from 'node:fs/promises';
import path from 'node:path';
import {retireAndroidDeepLinkForward} from './chat-deep-link-android-session-step.mjs';
const exec=promisify(execFile),pause=ms=>new Promise(resolve=>setTimeout(resolve,ms));
const uuid=/^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/;

export function validateAndroidDeepLinkLoginInput(input) {
  if(!input||Object.keys(input).sort().join(',')!=='countryCode,messageId,password,phone,runId,stepId'||
    !uuid.test(input.runId)||!uuid.test(input.stepId)||input.countryCode!=='240'||
    typeof input.phone!=='string'||!/^\d{8,15}$/.test(input.phone)||
    typeof input.password!=='string'||input.password.length<12||input.password.length>128||
    typeof input.messageId!=='string'||! /^[1-9]\d{0,15}$/.test(input.messageId))
    throw Error('deep_link_android_login_input_invalid');
}

// Caller already owns the AVD lease and has durably journaled requestStarted for
// its disposable native login ticket. Never retry an unresolved operation. This
// test does not deliver an Intent; deliverPublicLink must have completed first.
export async function runAndroidDeepLinkLoginStep({adb,serial,input,evidenceDirectory}) {
  validateAndroidDeepLinkLoginInput(input);
  if(!/^emulator-\d+$/.test(serial)||!path.isAbsolute(evidenceDirectory))throw Error('deep_link_android_login_configuration_invalid');
  await mkdir(evidenceDirectory,{recursive:false});
  const name=`quata-native-login-${randomUUID()}`,logPath=path.join(evidenceDirectory,'instrumentation.log');
  let port,child,socket,timer,finished=false,exitCode,output='',receipt;
  try {
    port=(await exec(adb,['-s',serial,'forward','tcp:0',`localabstract:${name}`],{windowsHide:true,timeout:10000})).stdout.trim();
    if(!/^\d+$/.test(port))throw Error();
    await writeFile(logPath+'.forward.json',JSON.stringify({serial,port,socket:name,stage:'allocated'}),{flag:'wx'});
    child=spawn(adb,['-s',serial,'shell','am','instrument','-w','-r','-e','loginSocket',name,
      '-e','class','com.quata.deeplinksender.NativeLoginTest#resumeDeliveredLink',
      'com.quata.deeplinksender.test/androidx.test.runner.AndroidJUnitRunner'],
      {windowsHide:true,stdio:['ignore','pipe','pipe']});
    const terminal=new Promise(resolve=>{
      child.once('error',()=>{finished=true;exitCode=-1;resolve();});
      child.once('close',code=>{finished=true;exitCode=code;resolve();});
    });
    for(const stream of [child.stdout,child.stderr])stream.on('data',bytes=>{if(output.length<100000)output+=bytes.toString();});
    timer=setTimeout(()=>{socket?.destroy();child.kill();},140000);
    const deadline=Date.now()+30000;
    while(Date.now()<deadline&&!finished&&!output.includes(`nativeLoginSocketReady=${name}`))await pause(100);
    if(finished||!output.includes(`nativeLoginSocketReady=${name}`))throw Error();
    socket=await new Promise((resolve,reject)=>{
      const candidate=connect({host:'127.0.0.1',port:Number(port)});
      const timeout=setTimeout(()=>{candidate.destroy();reject(Error());},5000);
      candidate.once('connect',()=>{clearTimeout(timeout);resolve(candidate);});
      candidate.once('error',()=>{clearTimeout(timeout);reject(Error());});
    });
    receipt=await new Promise((resolve,reject)=>{
      let response='',settled=false;
      const fail=()=>{if(!settled){settled=true;reject(Error());}};
      socket.setTimeout(110000,fail);socket.on('error',fail);socket.on('end',fail);socket.on('close',fail);
      socket.on('data',bytes=>{
        response+=bytes.toString();if(response.length>4096)return fail();
        if(response.includes('\n')&&!settled)try{const value=JSON.parse(response.trim());settled=true;resolve(value);}catch{fail();}
      });
      socket.write(JSON.stringify(input)+'\n');
    });
    socket.destroy();await terminal;
    if(exitCode!==0||!output.includes('OK (1 test)')||!output.includes('INSTRUMENTATION_CODE:')||
      Object.keys(receipt??{}).sort().join(',')!=='runId,stepId,verified'||receipt.verified!==true||
      receipt.runId!==input.runId||receipt.stepId!==input.stepId)throw Error();
    await exec(adb,['-s',serial,'pull',`/sdcard/Android/data/com.quata.deeplinksender/files/native-login-${input.stepId}`,
      path.join(evidenceDirectory,'device')],{windowsHide:true,timeout:15000,maxBuffer:1024*1024});
    return receipt;
  } catch {throw Error('deep_link_android_login_step_unresolved');}
  finally {
    clearTimeout(timer);socket?.destroy();if(child&&!finished)child.kill();
    await writeFile(logPath,output,{flag:'wx'});
    await writeFile(path.join(evidenceDirectory,'process.json'),JSON.stringify({terminalObserved:finished,exitCode:exitCode??null}),{flag:'wx'});
    if(port&&/^\d+$/.test(port)) {
      await retireAndroidDeepLinkForward({adb,serial,port});
      await writeFile(logPath+'.forward.json',JSON.stringify({serial,port,socket:name,stage:'retired_verified'}));
    }
  }
}
