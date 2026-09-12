const uuid=/^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/;
const keys=(value,names)=>value&&Object.keys(value).sort().join(',')===names.split(',').sort().join(',');
const failure=()=>Error('deep_link_ios_rejection_receipt_invalid');

export function validateIosNativeRejectionInput(input) {
  if(!keys(input,'runId,stepId,mode,threadId,messageId,body')||!uuid.test(input.runId)||!uuid.test(input.stepId)||
    input.mode!=='cold'||input.body!==`Deep link ${input.runId}`||
    !['threadId','messageId'].every(k=>typeof input[k]==='string'&&/^[1-9][0-9]{0,15}$/.test(input[k])))throw failure();
}

// Structural receipt check only. The owning journal must bind revocation, target
// and one delivery before this may authorize an exact native clear.
export function validateIosNativeRejectionReceipt({input,receipt}) {
  try {
    validateIosNativeRejectionInput(input);
    if(!keys(receipt,'runId,stepId,mode,passed,cancelled,rejection')||
      ['runId','stepId','mode'].some(k=>receipt[k]!==input[k])||receipt.passed!==true||receipt.cancelled!==true)throw failure();
    const witness=receipt.rejection;
    if(!keys(witness,'observed,pid,status,timestampNs,startedAtNs,endedAtNs')||witness.observed!==true||
      !Number.isSafeInteger(witness.pid)||witness.pid<=0||witness.pid>2147483647||![400,401].includes(witness.status)||
      !['timestampNs','startedAtNs','endedAtNs'].every(k=>typeof witness[k]==='string'&&/^[1-9][0-9]{0,20}$/.test(witness[k])))throw failure();
    if(BigInt(witness.startedAtNs)>BigInt(witness.timestampNs)||BigInt(witness.timestampNs)>BigInt(witness.endedAtNs))throw failure();
    return receipt;
  }catch{throw failure();}
}
