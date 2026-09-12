import {randomUUID} from "node:crypto";
import {execFile} from "node:child_process";
import {promisify} from "node:util";
import {mkdir,writeFile,readFile} from "node:fs/promises";
import path from "node:path";
import {readAndroidRejectionWindowStart,readAndroidRefreshRejection} from './chat-deep-link-android-rejection.mjs';
const exec=promisify(execFile);

export function createAndroidDeepLinkUi({channel,adb,serial,evidenceDirectory,targetMode,nativeRenewalMode,nativeRejectionMode,execute=exec}) {
  if(nativeRejectionMode!==undefined&&(nativeRejectionMode!=='cold'||targetMode!==undefined||nativeRenewalMode!==undefined))throw Error('deep_link_android_ui_invalid');
  if(nativeRenewalMode!==undefined&&(nativeRenewalMode!=='cold'||targetMode!==undefined))throw Error('deep_link_android_ui_invalid');
  if((targetMode!==undefined&&!["missing-thread","missing-message"].includes(targetMode))||!/^emulator-\d+$/.test(serial)||!path.isAbsolute(evidenceDirectory))throw Error("deep_link_android_ui_invalid");
  let started=false,unresolved=false;
  const command=(args,timeout=15000)=>execute(adb,["-s",serial,...args],{windowsHide:true,timeout,maxBuffer:1024*1024});
  const pid=async()=>{
    try {return (await command(["shell","pidof","com.quata"])).stdout.trim();}
    catch(error) {if(error.code===1&&!error.stdout?.trim()&&!error.stderr?.trim())return "";throw Error("deep_link_android_pid_unresolved");}
  };
  return {
    androidSessionChannel:channel,
    ...(nativeRenewalMode?{nativeExpiryMode:nativeRenewalMode}:{}),
    ...(nativeRejectionMode?{nativeRejectionMode}:{}),
    async run({target,body}) {
      if(started||!/^Deep link [0-9a-f-]{36}$/.test(body)||
        ![target?.threadId,target?.messageId].every(value=>/^[1-9][0-9]{0,15}$/.test(String(value)))||
        (targetMode==="missing-message"?(!/^[1-9][0-9]{0,15}$/.test(String(target.visibleMessageId))||
          String(target.visibleMessageId)===String(target.messageId)):target.visibleMessageId!==undefined)||
        (targetMode==="missing-thread"?(!/^[1-9][0-9]{0,15}$/.test(String(target.ownedThreadId))||
          String(target.ownedThreadId)===String(target.threadId)):target.ownedThreadId!==undefined))throw Error("deep_link_android_ui_invalid");
      started=true;let lastPid;const receipts=[];
      const publicUrl=`https://egquata.com/#chat-sb%3A${target.threadId}?message=${target.messageId}`;
      for(const mode of nativeRenewalMode||nativeRejectionMode?['cold']:["cold","warm"]) {
        const before=await pid();
        if(mode==="cold"?before!=="":before!==lastPid)throw Error("deep_link_android_lifecycle_changed");
        const runId=`chat-${mode}-${randomUUID()}`,directory=path.join(evidenceDirectory,runId);
        await mkdir(directory);
        await writeFile(path.join(directory,"host.json"),JSON.stringify({runId,mode,beforePid:before||null,publicUrl,status:"prepared"}));
        unresolved=true;
        let result;
        const rejectionStartedAt=nativeRejectionMode?await readAndroidRejectionWindowStart({adb,serial,execute}):undefined;
        try {
          result=await command(["shell","am","instrument","-w","-r","-e","runId",runId,"-e","publicUrl",publicUrl,
            ...(nativeRejectionMode?["-e","anonymousAction","cancel"]:
              ["-e","expectedMessageId",String(target.messageId),"-e","expectedMarker",body.slice("Deep link ".length)]),
            ...(targetMode?["-e","targetMode",targetMode]:[]),
            ...(targetMode==="missing-message"?["-e","visibleMessageId",String(target.visibleMessageId)]:[]),
            "-e","class","com.quata.deeplinksender.PublicLinkTest#deliverPublicLink",
            "com.quata.deeplinksender.test/androidx.test.runner.AndroidJUnitRunner"],100000);
        } catch {throw Error("deep_link_android_delivery_unresolved");}
        // JUnit terminal (even failure) closes the sender. A timeout is not a terminal receipt.
        await writeFile(path.join(directory,"instrumentation.log"),result.stdout+result.stderr);
        if(!result.stdout.includes("INSTRUMENTATION_CODE:"))throw Error("deep_link_android_delivery_unresolved");
        unresolved=false;
        await command(["pull",`/sdcard/Android/data/com.quata.deeplinksender/files/${runId}`,path.join(directory,"device")]);
        if(!result.stdout.includes("OK (1 test)"))throw Error("deep_link_android_observation_failed");
        const report=JSON.parse(await readFile(path.join(directory,"device/report.json"),"utf8"));
        const after=await pid();
        const expectedStatus=nativeRejectionMode?'anonymous_passed_pending_visual_review':targetMode?`${targetMode.replaceAll("-","_")}_passed_pending_visual_review`:"chat_passed_pending_visual_review";
        if(!after||(mode==="warm"&&after!==before)||report.status!==expectedStatus||
          (!nativeRejectionMode&&(targetMode?report.missingMessageId:report.focusedMessageId)!==String(target.messageId))||report.url!==publicUrl||
          (nativeRejectionMode&&(report.runId!==runId||report.anonymousAction!=='cancel'||report.postExitObservationMs!==2000||
            report.focusedMessageId!==undefined||report.senderPackage!=='com.quata.deeplinksender'))||
          (targetMode==="missing-message"&&report.visibleMessageId!==String(target.visibleMessageId))||
          report.explicitPackage!==null||report.explicitComponent!==null||report.resolvedPackage!=="com.quata"||
          (mode==="warm"&&report.beforeForegroundPackage!=="com.quata"))throw Error("deep_link_android_observation_failed");
        lastPid=after;
        const rejection=nativeRejectionMode?await readAndroidRefreshRejection({adb,serial,pid:after,startedAt:rejectionStartedAt,execute}):undefined;
        const receipt={runId,mode,beforePid:before||null,afterPid:after,directory,
          ...(nativeRejectionMode?{rejection,targetThreadId:String(target.threadId),targetMessageId:String(target.messageId),anonymousAction:'cancel'}:
            targetMode?{targetMode,missingMessageId:String(target.messageId)}:{focusedMessageId:String(target.messageId)}),passed:true,
          scope:nativeRejectionMode?'External implicit resolver delivery, native HTTP rejection witness, public auth barrier, cancel and Feed; two-second exit observation':targetMode==="missing-message"?"External implicit resolver delivery, owned control visible, five-second no-focus observation, BACK; two-second exit observation":targetMode?"External implicit resolver delivery, read failure, no owned body/focus, BACK; two-second observation":"External implicit resolver delivery, focus, owned body, BACK; two-second observation"};
        await writeFile(path.join(directory,"closure.json"),JSON.stringify(receipt,null,2));receipts.push(receipt);
      }
      return {passed:true,receipts,scope:nativeRejectionMode?'android_external_owned_message_cold_native_rejection_barrier_cancel_and_feed; visual review pending':nativeRenewalMode?'android_external_owned_message_cold_with_expired_metadata_and_back; visual review pending':targetMode?`android_external_${targetMode.replaceAll("-","_")}_cold_warm_and_back; visual review pending`:"android_external_owned_message_cold_warm_and_back; visual review pending"};
    },
    async close() {if(unresolved)throw Error("deep_link_android_ui_unresolved");},
  };
}
