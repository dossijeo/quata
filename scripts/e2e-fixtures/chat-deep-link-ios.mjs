import {randomUUID} from "node:crypto";

// Valid, owned message only. Missing-target and auth-resume modes need their own
// observations; do not infer them from these two externally delivered links.
export function createIosDeepLinkUi({channel}) {
  if(typeof channel?.observeChat!=="function")throw Error("deep_link_ios_ui_invalid");
  let started=false,unresolved=false;
  return {
    iosSessionChannel:channel,
    async run({target,body}) {
      if(started||typeof body!=="string"||!/^Deep link [0-9a-f-]{36}$/.test(body)||
          ![target?.threadId,target?.messageId].every(value=>/^[0-9]{1,16}$/.test(String(value)))||
          target.visibleMessageId!==undefined||target.ownedThreadId!==undefined)throw Error("deep_link_ios_ui_invalid");
      started=true;unresolved=true;
      const runId=body.slice("Deep link ".length),receipts=[];
      for(const mode of ["cold","warm"]) {
        receipts.push(await channel.observeChat({runId,stepId:randomUUID(),mode,
          threadId:String(target.threadId),messageId:String(target.messageId),body}));
      }
      unresolved=false;
      return {passed:true,receipts,scope:"ios_external_owned_message_cold_warm_and_back"};
    },
    async close() {
      // Each successful observation waits for terminal XCTest; the session clear
      // then shuts down the product host under the same simulator lease.
      if(unresolved)throw Error("deep_link_ios_ui_unresolved");
    },
  };
}
