import {randomUUID} from "node:crypto";

// Negative targets are opt-in and have distinct native methods/receipts.
// Auth-resume acceptance is not inferred from session import.
export function createIosDeepLinkUi({channel,targetMode}) {
  if(targetMode!==undefined&&!["missing-thread","missing-message"].includes(targetMode))throw Error("deep_link_ios_ui_invalid");
  if(typeof channel?.observeChat!=="function")throw Error("deep_link_ios_ui_invalid");
  let started=false,unresolved=false;
  return {
    iosSessionChannel:channel,
    async run({target,body}) {
      if(started||typeof body!=="string"||!/^Deep link [0-9a-f-]{36}$/.test(body)||
          ![target?.threadId,target?.messageId].every(value=>/^[0-9]{1,16}$/.test(String(value)))||
          (targetMode==="missing-message"?(!/^[1-9][0-9]{0,15}$/.test(String(target.visibleMessageId))||
             String(target.visibleMessageId)===String(target.messageId)):target.visibleMessageId!==undefined)||
          (targetMode==="missing-thread"?(!/^[1-9][0-9]{0,15}$/.test(String(target.ownedThreadId))||
             String(target.ownedThreadId)===String(target.threadId)):target.ownedThreadId!==undefined))throw Error("deep_link_ios_ui_invalid");
      started=true;unresolved=true;
      const runId=body.slice("Deep link ".length),receipts=[];
      for(const mode of ["cold","warm"]) {
        receipts.push(await channel.observeChat({runId,stepId:randomUUID(),mode,
          threadId:String(target.threadId),messageId:String(target.messageId),body,...(targetMode?{targetMode}:{}),
          ...(targetMode==="missing-message"?{visibleMessageId:String(target.visibleMessageId)}:{})}));
      }
      unresolved=false;
      return {passed:true,receipts,scope:targetMode?`ios_external_${targetMode.replaceAll("-","_")}_cold_warm_and_back`:"ios_external_owned_message_cold_warm_and_back"};
    },
    async close() {
      // Each successful observation waits for terminal XCTest; the session clear
      // then shuts down the product host under the same simulator lease.
      if(unresolved)throw Error("deep_link_ios_ui_unresolved");
    },
  };
}
