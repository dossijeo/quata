import {randomUUID} from 'node:crypto';
const uuid=/^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/;
const numeric=value=>typeof value==='string'&&/^[1-9][0-9]{0,15}$/.test(value);
const failure=()=>Error('deep_link_ios_native_ui_unresolved');

export function validateIosNativeLoginInput(input) {
  const expected='authUserId,countryCode,messageId,password,phone,profileId,runId,stepId,ticketId'+(input?.variant==='cancel-then-feed'?',variant':'');
  if(Object.keys(input??{}).sort().join(',')!==expected||
    ['runId','stepId','ticketId','profileId','authUserId'].some(key=>!uuid.test(input[key]))||input.countryCode!=='240'||
    typeof input.phone!=='string'||!/^\d{8,15}$/.test(input.phone)||typeof input.password!=='string'||
    input.password.length<12||input.password.length>128||!numeric(input.messageId))throw failure();
}

// The Mac worker binds both observers to the delivered target and the same PID.
// A failed dispatch remains uncertain: no automatic Login replay or successful close.
export function createIosNativeLoginUi({channel}) {
  if(typeof channel?.nativeGate!=='function'||typeof channel?.nativeLogin!=='function')throw failure();
  let state='new',delivery;
  return {
    async deliver({runId,target,mode}) {
      if(state!=='new'||!uuid.test(runId)||!['cold','warm'].includes(mode)||
        !numeric(String(target?.threadId))||!numeric(String(target?.messageId)))throw failure();
      state='delivering';
      delivery={runId,stepId:randomUUID(),threadId:String(target.threadId),messageId:String(target.messageId),mode};
      const receipt=await channel.nativeGate(delivery);
      if(receipt?.passed!==true||['runId','stepId','mode'].some(key=>receipt[key]!==delivery[key]))throw failure();
      state='delivered';return receipt;
    },
    async login(input) {
      validateIosNativeLoginInput(input);
      if(state!=='delivered'||input.runId!==delivery.runId||input.messageId!==delivery.messageId||input.stepId===delivery.stepId)throw failure();
      state='authenticating';
      const receipt=await channel.nativeLogin(input);
      if(receipt?.passed!==true||receipt.runId!==input.runId||receipt.stepId!==input.stepId||receipt.variant!==input.variant)throw failure();
      state='observed';return receipt;
    },
    async close() {
      if(!['new','delivered','observed','closed'].includes(state))throw failure();
      state='closed';
    },
  };
}
