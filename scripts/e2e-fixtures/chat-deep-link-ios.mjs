import {randomUUID} from "node:crypto";

// Missing-thread observations are opt-in and have a distinct native method/receipt.
// Auth-resume and missing-message acceptance are not inferred from either mode.
export function createIosDeepLinkUi({channel,targetMode}) {
  if(targetMode!==undefined&&targetMode!=="missing-thread")throw Error("deep_link_ios_ui_invalid");
  if(typeof channel?.observeChat!=="function")throw Error("deep_link_ios_ui_invalid");
  let started=false,unresolved=false;
  return {
    iosSessionChannel:channel,
    async run({target,body}) {
      if(started||typeof body!=="string"||!/^Deep link [0-9a-f-]{36}$/.test(body)||
          ![target?.threadId,target?.messageId].every(value=>/^[0-9]{1,16}$/.test(String(value)))||
          target.visibleMessageId!==undefined||
          (targetMode==="missing-thread"?(!/^[1-9][0-9]{0,15}$/.test(String(target.ownedThreadId))||
             String(target.ownedThreadId)===String(target.threadId)):target.ownedThreadId!==undefined))throw Error("deep_link_ios_ui_invalid");
      started=true;unresolved=true;
      const runId=body.slice("Deep link ".length),receipts=[];
      for(const mode of ["cold","warm"]) {
        receipts.push(await channel.observeChat({runId,stepId:randomUUID(),mode,
          threadId:String(target.threadId),messageId:String(target.messageId),body,...(targetMode?{targetMode}:{})}));
      }
      unresolved=false;
      return {passed:true,receipts,scope:targetMode==="missing-thread"?"ios_external_missing_thread_cold_warm_and_back":"ios_external_owned_message_cold_warm_and_back"};
    },
    async close() {
      // Each successful observation waits for terminal XCTest; the session clear
      // then shuts down the product host under the same simulator lease.
      if(unresolved)throw Error("deep_link_ios_ui_unresolved");
    },
  };
}
