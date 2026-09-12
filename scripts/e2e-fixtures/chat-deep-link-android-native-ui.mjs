import {execFile} from 'node:child_process';
import {promisify} from 'node:util';
import {mkdir,readFile,writeFile} from 'node:fs/promises';
import {randomUUID} from 'node:crypto';
import path from 'node:path';
import {runAndroidDeepLinkLoginStep} from './chat-deep-link-android-login-step.mjs';
const exec=promisify(execFile);

export function createAndroidNativeDeepLinkUi({adb,serial,evidenceDirectory}) {
  if(!/^emulator-\d+$/.test(serial)||!path.isAbsolute(evidenceDirectory))throw Error('deep_link_native_ui_invalid');
  let delivered=false,loginStarted=false,unresolved=false,deliveryPid,deliveryTarget,deliveryMode,deliveryRunId;
  const command=(args,timeout=15000)=>exec(adb,['-s',serial,...args],{windowsHide:true,timeout,maxBuffer:1024*1024});
  const pid=async()=>{
    try{return (await command(['shell','pidof','com.quata'])).stdout.trim();}
    catch(error){if(error.code===1&&!error.stdout?.trim()&&!error.stderr?.trim())return '';throw Error('deep_link_native_pid_unresolved');}
  };
  const deliver=async(publicUrl,label)=>{
    const run=`${label}-${randomUUID()}`,directory=path.join(evidenceDirectory,run);await mkdir(directory);
    unresolved=true;
    let result;
    try{result=await command(['shell','am','instrument','-w','-r','-e','runId',run,'-e','publicUrl',publicUrl,
      '-e','class','com.quata.deeplinksender.PublicLinkTest#deliverPublicLink',
      'com.quata.deeplinksender.test/androidx.test.runner.AndroidJUnitRunner'],100000);}
    catch{throw Error('deep_link_native_delivery_unresolved');}
    await writeFile(path.join(directory,'instrumentation.log'),result.stdout+result.stderr);
    if(!result.stdout.includes('INSTRUMENTATION_CODE:'))throw Error('deep_link_native_delivery_unresolved');
    unresolved=false;
    await command(['pull',`/sdcard/Android/data/com.quata.deeplinksender/files/${run}`,path.join(directory,'device')]);
    const report=JSON.parse(await readFile(path.join(directory,'device/report.json'),'utf8'));
    if(!result.stdout.includes('OK (1 test)')||report.status!=='delivered_pending_destination_review'||report.url!==publicUrl||
      report.resolvedPackage!=='com.quata'||report.resolvedActivity!=='com.quata.MainActivity'||
      report.explicitPackage!==null||report.explicitComponent!==null)throw Error('deep_link_native_delivery_invalid');
    return {directory,publicUrl};
  };
  return {
    async deliver({runId,target,mode}) {
      if(delivered||!['cold','warm'].includes(mode)||! /^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/.test(runId)||
        ![target?.threadId,target?.messageId].every(value=>/^[1-9]\d{0,15}$/.test(String(value))))throw Error('deep_link_native_delivery_invalid');
      delivered=true;deliveryMode=mode;deliveryTarget=target;deliveryRunId=runId;
      const initialPid=await pid();
      if(mode==='cold'&&initialPid!=='')throw Error('deep_link_native_cold_process_present');
      let prelude;
      if(mode==='warm')prelude=await deliver('https://egquata.com/#post-e3aa9c1e-a458-4d3b-a35e-4cbd3b4e858b','native-warm-feed');
      const before=await pid();if(mode==='warm'?!before:before!=='')throw Error('deep_link_native_lifecycle_invalid');
      if(mode==='warm'&&initialPid&&before!==initialPid)throw Error('deep_link_native_lifecycle_invalid');
      const evidence=await deliver(`https://egquata.com/#chat-sb%3A${target.threadId}?message=${target.messageId}`,'native-anonymous-chat');
      deliveryPid=await pid();
      if(!deliveryPid||(mode==='warm'&&deliveryPid!==before))throw Error('deep_link_native_lifecycle_invalid');
      const report={runId,mode,...evidence,prelude,initialPid:initialPid||null,beforePid:before||null,afterPid:deliveryPid};
      await writeFile(path.join(evidenceDirectory,'delivery.json'),JSON.stringify(report,null,2));return report;
    },
    async login(input) {
      if(!delivered||loginStarted||input.runId!==deliveryRunId||input.messageId!==String(deliveryTarget.messageId)||await pid()!==deliveryPid)
        throw Error('deep_link_native_login_lifecycle_invalid');
      loginStarted=true;unresolved=true;
      const directory=path.join(evidenceDirectory,`native-login-${input.stepId}`);
      const receipt=await runAndroidDeepLinkLoginStep({adb,serial,input,evidenceDirectory:directory});
      unresolved=false;
      const after=await pid(),report=JSON.parse(await readFile(path.join(directory,'device/report.json'),'utf8'));
      if(after!==deliveryPid||report.runId!==input.runId||report.stepId!==input.stepId||report.messageId!==input.messageId||
        report.status!=='passed_pending_visual_review'||report.phase!=='complete'||report.submitCount!==1||receipt.verified!==true)
        throw Error('deep_link_native_login_observation_invalid');
      return {passed:true,directory,mode:deliveryMode,beforePid:deliveryPid,afterPid:after,messageId:input.messageId,
        scope:'One native Login Submit after external anonymous delivery, exact focus and Back; visual review pending'};
    },
    async close(){if(unresolved)throw Error('deep_link_native_ui_unresolved');},
  };
}
