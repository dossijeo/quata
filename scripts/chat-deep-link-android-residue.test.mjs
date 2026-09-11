import test from 'node:test';
import assert from 'node:assert/strict';
import {retireAndroidDeepLinkResidue} from './e2e-fixtures/chat-deep-link-android-residue.mjs';
const ids=['11111111-1111-4111-8111-111111111111','22222222-2222-4222-8222-222222222222','33333333-3333-4333-8333-333333333333','44444444-4444-4444-8444-444444444444'];
function fixture() {
 const record={runId:ids[0],profileId:ids[1],authUserId:ids[2]},identity={...record,authSessionId:ids[3]};
 const input={...identity,accessToken:'synthetic',refreshToken:'synthetic',expiresAt:2000000000,email:'test@example.invalid',displayName:'Fixture',isOfficial:false};
 let state={...record,state:{profileCreated:true,sessions:[{...identity,androidSession:{
   install:{started:true,verified:true,input:{...input,stage:'install',stepId:ids[0]}},
   clear:{started:true,verified:true,input:{...input,stage:'clear',stepId:ids[1]}}}}]}};
 const calls=[],push={id:ids[3],user_id:record.profileId,auth_user_id:record.authUserId,platform:'android',token:'private-token'};
 const journal={read:async()=>structuredClone(state),checkpoint:async value=>{state.state=structuredClone(value);calls.push('checkpoint');}};
 const client={query:async sql=>{
   calls.push(sql);
   if(sql.includes('select p.id'))return {rowCount:1};
   if(sql.includes('to_jsonb(t)')&&sql.includes('push_tokens'))return {rows:[{row:push}]};
   if(sql.includes('to_jsonb(t)'))return {rows:[{row:{user_id:record.profileId,platform:'android'}}]};
   if(sql.includes('from pg_constraint'))return {rows:[{child:'push_delivery_log',parent:'push_tokens',definition:'FOREIGN KEY (push_token_id) REFERENCES push_tokens(id) ON DELETE CASCADE'}]};
   if(sql.includes('count(*)'))return {rows:[{count:'0'}]};
   if(sql.startsWith('delete '))return {rowCount:1};
   if(sql.includes('not exists'))return {rows:[{push:true,release:true}]};
   return {};
 }};
 return {args:{client,journal,record,operationsSettled:async()=>true},calls,push,state:()=>state};
}
test('owned Android metadata is snapshotted before exact deletes and verified',async()=>{
 const f=fixture();assert.deepEqual(await retireAndroidDeepLinkResidue(f.args),{removed:true});
 assert.ok(f.calls.indexOf('checkpoint')<f.calls.findIndex(s=>s.startsWith('delete ')));
 assert.equal(f.state().state.androidResidueCleanup.pushRows[0].token,'private-token');
 assert.equal(f.state().state.androidResidueCleanup.verified,true);
});
for(const scenario of ['foreign-owner','foreign-token','uncleared-session','delivery-log','unknown-fk','snapshot-failure'])test(`reject ${scenario} before deleting`,async()=>{
 const f=fixture(),query=f.args.client.query;
 if(scenario==='foreign-token')f.push.auth_user_id=ids[0];
 if(scenario==='uncleared-session')f.state().state.sessions[0].androidSession.clear.verified=false;
 if(scenario==='snapshot-failure')f.args.journal.checkpoint=async()=>{throw Error('disk');};
 f.args.client.query=async(sql,...args)=>{
  if(scenario==='foreign-owner'&&sql.includes('select p.id'))return {rowCount:0};
  if(scenario==='delivery-log'&&sql.includes('count(*)'))return {rows:[{count:'1'}]};
  if(scenario==='unknown-fk'&&sql.includes('from pg_constraint'))return {rows:[]};
  return query(sql,...args);
 };
 await assert.rejects(retireAndroidDeepLinkResidue(f.args));assert.equal(f.calls.some(s=>s.startsWith('delete ')),false);
});
