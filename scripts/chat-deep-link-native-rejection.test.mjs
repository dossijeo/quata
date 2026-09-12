import test from 'node:test';
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {prepareAndroidNativeDeepLinkRejection} from './e2e-fixtures/chat-deep-link-native-rejection.mjs';
import {androidDeepLinkCustodySettled} from './e2e-fixtures/chat-deep-link-ios-custody.mjs';

function fixture() {
  const record={runId:randomUUID(),profileId:randomUUID(),authUserId:randomUUID()};
  const ticket={...record,purpose:'deep_link',authSessionId:randomUUID(),webSessionId:randomUUID(),clientInstanceId:randomUUID()};
  const accessToken='synthetic.'+Buffer.from(JSON.stringify({sub:record.authUserId,session_id:ticket.authSessionId,exp:2000000000})).toString('base64url')+'.synthetic';
  const original={profileId:record.profileId,authUserId:record.authUserId,authSessionId:ticket.authSessionId,
    accessToken,refreshToken:'synthetic-refresh',expiresAt:2000000000,email:'fixture@example.invalid',displayName:'Fixture',isOfficial:false};
  const expired={...original,expiresAt:1899999999},stepId=randomUUID();
  const renewal={platform:'android',phase:'installed',preparedAt:1900000000,original,expired,
    install:{started:true,verified:true,input:{runId:record.runId,stepId,stage:'install-expired',...expired,originalExpiresAt:original.expiresAt}}};
  const session={profileId:record.profileId,accessToken,refreshToken:original.refreshToken,expiresAt:original.expiresAt,webSessionToken:'synthetic-web'};
  const saved={...record,state:{sessions:[{...ticket,requestStarted:true,nativeSessionRenewal:renewal,
    privateLoginResponse:{status:200,body:{profile:{id:record.profileId},session:{access_token:accessToken,
      refresh_token:session.refreshToken,expires_at:session.expiresAt},web_session:{token:session.webSessionToken}}}}]}};
  const events=[];let auth=true,web=true;
  const args={record,ticket,session,backendUrl:'https://example.test',publicKey:'public',operationsSettled:async()=>true,
    journal:{read:async()=>structuredClone(saved),checkpoint:async state=>{events.push('checkpoint');saved.state=structuredClone(state);}},
    client:{query:async(sql,params)=>{
      if(sql.includes('as owned'))return {rowCount:1,rows:[{owned:true,auth_count:auth?1:0,exact_auth:auth,web_count:web?1:0,exact_web:params[7]===!web}]};
      if(sql.startsWith('update public.web_client_sessions')) {
        assert.equal(saved.state.sessions[0].nativeSessionRejection.phase,'revocation-started');
        assert.deepEqual(saved.state.sessions[0].revocation,{started:true});
        assert.equal(params[0],ticket.webSessionId);web=false;events.push('revoke-web');
      }
      if(sql.startsWith('delete from auth.sessions')) {
        assert.equal(params[0],ticket.authSessionId);assert.equal(params[1],record.authUserId);auth=false;events.push('revoke-auth');
      }
      if(sql.includes('as auth_count'))return {rows:[{auth_count:auth?1:0,web_count:web?1:0}]};
      return {rowCount:0,rows:[]};
    }},
  };
  return {args,saved,events};
}

test('owned native revocation follows durable install and intent, without claiming native rejection or cleanup',async()=>{
  const f=fixture(),original=structuredClone(f.saved.state.sessions[0].nativeSessionRenewal);
  assert.deepEqual(await prepareAndroidNativeDeepLinkRejection(f.args),{revoked:true,nativeRejectionObserved:false});
  const entry=f.saved.state.sessions[0];
  assert.deepEqual(entry.nativeSessionRenewal,original);
  assert.equal(entry.nativeSessionRejection.phase,'revoked');
  assert.deepEqual(entry.revocation,{started:true,verified:true});
  assert.equal(androidDeepLinkCustodySettled(entry),false);
  assert.deepEqual(f.events,['checkpoint','checkpoint','revoke-web','revoke-auth','checkpoint','checkpoint']);
  await assert.rejects(prepareAndroidNativeDeepLinkRejection(f.args));
  assert.equal(f.events.filter(x=>x==='revoke-auth').length,1);
});

for(const variant of ['wrong-platform','not-installed','unverified-install','changed-expiry','changed-install','read-started','identity-started',
  'mixed-session','mixed-ticket','prior-rejection','prior-revocation','extra-entry','operations-live'])
test(`refuses ${variant} before SQL mutation`,async()=>{
  const f=fixture(),e=f.saved.state.sessions[0],r=e.nativeSessionRenewal;
  if(variant==='wrong-platform')r.platform='ios';
  if(variant==='not-installed')r.phase='prepared';
  if(variant==='unverified-install')r.install.verified=false;
  if(variant==='changed-expiry')r.expired.refreshToken='foreign';
  if(variant==='changed-install')r.install.input.authSessionId=randomUUID();
  if(variant==='read-started')r.snapshotRead={};
  if(variant==='identity-started')r.remoteIdentity={};
  if(variant==='mixed-session')f.args.session.accessToken='foreign';
  if(variant==='mixed-ticket')f.args.ticket.authSessionId=randomUUID();
  if(variant==='prior-rejection')e.nativeSessionRejection={};
  if(variant==='prior-revocation')e.revocation={};
  if(variant==='extra-entry')f.saved.state.sessions.push(structuredClone(e));
  if(variant==='operations-live')f.args.operationsSettled=async()=>false;
  await assert.rejects(prepareAndroidNativeDeepLinkRejection(f.args),{message:'deep_link_native_rejection_preparation_unresolved'});
  assert.equal(f.events.some(x=>x.startsWith('revoke-')),false);
});

for(const write of [1,2,3,4])for(const mode of ['throw','lost-readback'])
test(`checkpoint ${write} ${mode} retains uncertainty and prevents revocation replay`,async()=>{
  const f=fixture(),checkpoint=f.args.journal.checkpoint;let count=0;
  f.args.journal.checkpoint=async state=>{
    if(++count===write) {
      if(mode==='throw')throw Error('private persistence failure');
      return;
    }
    await checkpoint(state);
  };
  await assert.rejects(prepareAndroidNativeDeepLinkRejection(f.args),{message:'deep_link_native_rejection_preparation_unresolved'});
  assert.equal(f.events.filter(x=>x==='revoke-auth').length,write<=2?0:1);
  assert.equal(androidDeepLinkCustodySettled(f.saved.state.sessions[0]),false);
  if(write>1) {
    f.args.journal.checkpoint=checkpoint;
    await assert.rejects(prepareAndroidNativeDeepLinkRejection(f.args));
    assert.equal(f.events.filter(x=>x==='revoke-auth').length,write<=2?0:1);
  }
});

test('concurrent change during ownership audit prevents SQL and retains native intent',async()=>{
  const f=fixture(),query=f.args.client.query;
  f.args.client.query=async(sql,params)=>{
    const result=await query(sql,params);
    if(sql.includes('as owned'))f.saved.state.changed=true;
    return result;
  };
  await assert.rejects(prepareAndroidNativeDeepLinkRejection(f.args));
  assert.equal(f.events.some(x=>x.startsWith('revoke-')),false);
  assert.equal(f.saved.state.sessions[0].nativeSessionRejection.phase,'revocation-started');
});

test('uncertain remote revocation is not retried and private SQL errors do not escape',async()=>{
  const f=fixture(),query=f.args.client.query;
  f.args.client.query=async(sql,params)=>{
    if(sql.startsWith('delete from auth.sessions'))throw Error('private SQL detail');
    return query(sql,params);
  };
  await assert.rejects(prepareAndroidNativeDeepLinkRejection(f.args),{message:'deep_link_native_rejection_preparation_unresolved'});
  await assert.rejects(prepareAndroidNativeDeepLinkRejection(f.args));
  assert.equal(f.events.filter(x=>x==='revoke-web').length,1);
  assert.equal(androidDeepLinkCustodySettled(f.saved.state.sessions[0]),false);
});
