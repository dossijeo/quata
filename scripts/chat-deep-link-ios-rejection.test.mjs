import test from 'node:test';
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {validateIosNativeRejectionInput,validateIosNativeRejectionReceipt} from './e2e-fixtures/chat-deep-link-ios-rejection.mjs';
import {createIosDeepLinkUi} from './e2e-fixtures/chat-deep-link-ios.mjs';
const runId=randomUUID(),input={runId,stepId:randomUUID(),mode:'cold',threadId:'123',messageId:'456',body:`Deep link ${runId}`};
const receipt={runId,stepId:input.stepId,mode:'cold',passed:true,cancelled:true,rejection:{observed:true,pid:1234,status:400,
  timestampNs:'1800000000123456789',startedAtNs:'1800000000123456788',endedAtNs:'1800000000123456790'}};
test('iOS rejection adapter accepts only its cold receipt and never runs ordinary chat observation',async()=>{
  for(const outcome of ['complete','lost','foreign','no-cancel']) {
    let deliveries=0;
    const channel={observeChat:()=>assert.fail('ordinary observer forbidden'),nativeRejection:async command=>{
      deliveries++;assert.equal(command.mode,'cold');assert.equal(command.body,input.body);
      if(outcome==='lost')throw Error('synthetic');
      return {...receipt,stepId:outcome==='foreign'?input.stepId:command.stepId,cancelled:outcome!=='no-cancel'};
    }};
    const ui=createIosDeepLinkUi({channel,nativeRejectionMode:'cold'});
    const action=ui.run({target:{threadId:'123',messageId:'456'},body:input.body});
    if(outcome==='complete') {
      const result=await action;assert.equal(result.receipts.length,1);
      assert.equal(result.scope,'ios_external_owned_message_cold_native_rejection_barrier_cancel_and_feed');
      await ui.close();
    }else {await assert.rejects(action);await assert.rejects(ui.close());}
    await assert.rejects(ui.run({target:{threadId:'123',messageId:'456'},body:input.body}));
    assert.equal(deliveries,1);
  }
});
test('iOS rejection mode cannot mix with renewal or negative target configuration',()=>{
  const channel={observeChat(){},nativeRejection(){}};
  for(const extra of [{nativeRejectionMode:'warm'},{nativeRejectionMode:'cold',nativeRenewalMode:'cold'},
    {nativeRejectionMode:'cold',targetMode:'missing-thread'},{nativeRejectionMode:'cold',channel:{observeChat(){}}}])
    assert.throws(()=>createIosDeepLinkUi({channel,...extra}));
});
test('exact iOS rejection receipt preserves nanosecond boundaries and transport metadata only',()=>{
  assert.deepEqual(validateIosNativeRejectionReceipt({input,receipt}),receipt);
  for(const status of [400,401])for(const timestampNs of [receipt.rejection.startedAtNs,receipt.rejection.endedAtNs])
    assert.equal(validateIosNativeRejectionReceipt({input,receipt:{...receipt,rejection:{...receipt.rejection,status,timestampNs}}}).rejection.status,status);
});
test('rejects mixed or extended iOS inputs before channel delivery',()=>{
  for(const extra of [{mode:'warm'},{body:'private'},{extra:true},{threadId:123},{runId:randomUUID()},{messageId:'0'}])
    assert.throws(()=>validateIosNativeRejectionInput({...input,...extra}));
});
test('rejects foreign receipt and nonexact rejection evidence',()=>{
  for(const extra of [{runId:randomUUID()},{stepId:randomUUID()},{passed:false},{cancelled:false},{extra:'private'},{mode:'warm'}])
    assert.throws(()=>validateIosNativeRejectionReceipt({input,receipt:{...receipt,...extra}}));
  for(const extra of [{status:500},{status:'400'},{pid:'1234'},{pid:true},{observed:false},{extra:'private'},
    {timestampNs:'1800000000123456787'},{timestampNs:'1800000000123456791'},
    {startedAtNs:1800000000123456788},{timestampNs:'01800000000123456789'}])
    assert.throws(()=>validateIosNativeRejectionReceipt({input,receipt:{...receipt,rejection:{...receipt.rejection,...extra}}}));
});
