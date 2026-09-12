// Structural ownership only. Caller must persist the private result, verify Auth
// remotely, and clear the exact native snapshot before retiring the fixture.
export function validateOwnedNativeSessionReceipt({input,receipt}) {
  try {
    const uuid=/^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/;
    if(Object.keys(input??{}).sort().join(',')!=='authUserId,profileId,runId,stage,stepId'||
      ['runId','stepId','profileId','authUserId'].some(key=>!uuid.test(input[key]))||input.stage!=='read-owned'||
      Object.keys(receipt??{}).sort().join(',')!=='privateSession,runId,stage,stepId,verified'||receipt.verified!==true||
      ['runId','stepId','stage'].some(key=>receipt[key]!==input[key]))throw Error();
    const session=receipt.privateSession;
    if(Object.keys(session??{}).sort().join(',')!=='accessToken,authSessionId,authUserId,displayName,email,expiresAt,isOfficial,profileId,refreshToken'||
      ['profileId','authUserId'].some(key=>session[key]!==input[key])||!uuid.test(session.authSessionId)||
      ['accessToken','refreshToken','email','displayName'].some(key=>typeof session[key]!=='string'||!session[key])||
      !Number.isSafeInteger(session.expiresAt)||session.expiresAt<=0||typeof session.isOfficial!=='boolean')throw Error();
    const parts=session.accessToken.split('.');if(parts.length!==3)throw Error();
    const claims=JSON.parse(Buffer.from(parts[1],'base64url').toString('utf8'));
    if(claims.sub!==session.authUserId||claims.session_id!==session.authSessionId||claims.exp!==session.expiresAt)throw Error();
    return true;
  }catch{throw Error('deep_link_owned_session_receipt_invalid');}
}
