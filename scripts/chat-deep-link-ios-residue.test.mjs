import test from 'node:test';
import assert from 'node:assert/strict';
import {verifyIosDeepLinkResidueAbsent} from './e2e-fixtures/chat-deep-link-ios-residue.mjs';
const id=n=>`00000000-0000-4000-8000-${String(n).padStart(12,'0')}`;
function fixture() {
 const record={runId:id(1),profileId:id(2),authUserId:id(3)};
 const session={profileId:id(2),authUserId:id(3),authSessionId:id(4),accessToken:'synthetic',refreshToken:'synthetic',
  expiresAt:2000000000,email:'fixture@example.invalid',displayName:'Fixture',isOfficial:false};
 const native={observationVerified:true,
  read:{started:true,verified:true,input:{...record,stepId:id(5),stage:'read-owned'},privateSession:session,
   acknowledgment:{started:true,verified:true,runId:record.runId,stepId:id(5)}},
  clear:{started:true,verified:true,input:{...session,runId:record.runId,stepId:id(6),stage:'clear'}}};
 const saved={...record,state:{profileCreated:true,sessions:[{...record,authSessionId:id(4),kind:'native',requestStarted:true,iosNativeLogin:native}]}};
 const rows=[{owned:true,push:true,release:true}],calls=[];
 return {saved,native,rows,calls,args:{record,journal:{read:async()=>saved},operationsSettled:async()=>true,
  client:{query:async(sql,values)=>{assert.match(sql,/^select/);assert.deepEqual(values,[record.profileId,record.authUserId,record.runId]);calls.push(sql);return {rows};}}}};
}
test('iOS verifies no remote registration residue without writes',async()=>{
 const f=fixture();assert.deepEqual(await verifyIosDeepLinkResidueAbsent(f.args),{verifiedAbsent:true});assert.equal(f.calls.length,1);
});
for(const key of ['owned','push','release'])test(`iOS refuses unexpected ${key} result`,async()=>{
 const f=fixture();f.rows[0][key]=false;await assert.rejects(verifyIosDeepLinkResidueAbsent(f.args),{message:'deep_link_ios_residue_unresolved'});
});
test('iOS does not query residue before exact device closure and matching journal',async()=>{
 for(const mutate of [f=>f.native.clear.verified=false,f=>f.native.read.acknowledgment.verified=false,
  f=>f.saved.runId=id(9),f=>f.args.operationsSettled=async()=>false]) {
  const f=fixture();mutate(f);await assert.rejects(verifyIosDeepLinkResidueAbsent(f.args),{message:'deep_link_ios_residue_not_ready'});
  assert.equal(f.calls.length,0);
 }
});
