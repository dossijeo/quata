import test from 'node:test';
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {validateIosNativeRejectionInput,validateIosNativeRejectionReceipt} from './e2e-fixtures/chat-deep-link-ios-rejection.mjs';
const runId=randomUUID(),input={runId,stepId:randomUUID(),mode:'cold',threadId:'123',messageId:'456',body:`Deep link ${runId}`};
const receipt={runId,stepId:input.stepId,mode:'cold',passed:true,cancelled:true,rejection:{observed:true,pid:1234,status:400,
  timestampNs:'1800000000123456789',startedAtNs:'1800000000123456788',endedAtNs:'1800000000123456790'}};
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
