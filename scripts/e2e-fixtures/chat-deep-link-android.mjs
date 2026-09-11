import {randomUUID} from "node:crypto";
import {execFile} from "node:child_process";
import {promisify} from "node:util";
import {mkdir,writeFile,readFile} from "node:fs/promises";
import path from "node:path";
const exec=promisify(execFile);

export function createAndroidDeepLinkUi({channel,adb,serial,evidenceDirectory}) {
  if(!/^emulator-\d+$/.test(serial)||!path.isAbsolute(evidenceDirectory))throw Error("deep_link_android_ui_invalid");
  let started=false,unresolved=false;
  const command=(args,timeout=15000)=>exec(adb,["-s",serial,...args],{windowsHide:true,timeout,maxBuffer:1024*1024});
  const pid=async()=>{
    try {return (await command(["shell","pidof","com.quata"])).stdout.trim();}
    catch(error) {if(error.code===1&&!error.stdout?.trim()&&!error.stderr?.trim())return "";throw Error("deep_link_android_pid_unresolved");}
  };
  return {
    androidSessionChannel:channel,
    async run({target,body}) {
      if(started||!/^Deep link [0-9a-f-]{36}$/.test(body)||
        ![target?.threadId,target?.messageId].every(value=>/^[1-9][0-9]{0,15}$/.test(String(value)))||
        target.visibleMessageId!==undefined||target.ownedThreadId!==undefined)throw Error("deep_link_android_ui_invalid");
      started=true;let lastPid;const receipts=[];
      const publicUrl=`https://egquata.com/#chat-sb%3A${target.threadId}?message=${target.messageId}`;
      for(const mode of ["cold","warm"]) {
        const before=await pid();
        if(mode==="cold"?before!=="":before!==lastPid)throw Error("deep_link_android_lifecycle_changed");
        const runId=`chat-${mode}-${randomUUID()}`,directory=path.join(evidenceDirectory,runId);
        await mkdir(directory);
        await writeFile(path.join(directory,"host.json"),JSON.stringify({runId,mode,beforePid:before||null,publicUrl,status:"prepared"}));
        unresolved=true;
        let result;
        try {
          result=await command(["shell","am","instrument","-w","-r","-e","runId",runId,"-e","publicUrl",publicUrl,
            "-e","expectedMessageId",String(target.messageId),"-e","expectedMarker",body.slice("Deep link ".length),
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
        if(!after||(mode==="warm"&&after!==before)||report.status!=="chat_passed_pending_visual_review"||
          report.focusedMessageId!==String(target.messageId)||report.url!==publicUrl||
          report.explicitPackage!==null||report.explicitComponent!==null||report.resolvedPackage!=="com.quata"||
          (mode==="warm"&&report.beforeForegroundPackage!=="com.quata"))throw Error("deep_link_android_observation_failed");
        lastPid=after;
        const receipt={runId,mode,beforePid:before||null,afterPid:after,directory,
          focusedMessageId:String(target.messageId),passed:true,scope:"External implicit resolver delivery, focus, owned body, BACK; two-second observation"};
        await writeFile(path.join(directory,"closure.json"),JSON.stringify(receipt,null,2));receipts.push(receipt);
      }
      return {passed:true,receipts,scope:"android_external_owned_message_cold_warm_and_back; visual review pending"};
    },
    async close() {if(unresolved)throw Error("deep_link_android_ui_unresolved");},
  };
}
