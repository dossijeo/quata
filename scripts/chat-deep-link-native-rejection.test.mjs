import test from 'node:test';
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {awaitNativeWarmDeepLinkRejectionExpiry,prepareAndroidNativeDeepLinkRejection,
  prepareIosNativeDeepLinkRejection} from './e2e-fixtures/chat-deep-link-native-rejection.mjs';
import {androidDeepLinkCustodySettled,iosDeepLinkCustodySettled} from './e2e-fixtures/chat-deep-link-ios-custody.mjs';
import {observeAndroidNativeDeepLinkRejection,confirmAndroidNativeDeepLinkRejectionAbsence,
  androidNativeDeepLinkRejectionCustodySettled,observeIosNativeDeepLinkRejection,clearIosNativeDeepLinkRejection,
  iosNativeDeepLinkRejectionCustodySettled} from './e2e-fixtures/chat-deep-link-native-rejection-observation.mjs';

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
      if(sql.includes('from auth.refresh_tokens'))return {rowCount:1,rows:[{total:3,revoked:2,active:1}]};
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

test('iOS preparation reuses owned revocation while retaining separate platform custody',async()=>{
  const f=fixture();f.saved.state.sessions[0].nativeSessionRenewal.platform='ios';
  await assert.rejects(prepareAndroidNativeDeepLinkRejection(f.args));
  assert.deepEqual(f.events,[]);
  assert.deepEqual(await prepareIosNativeDeepLinkRejection(f.args),{revoked:true,nativeRejectionObserved:false});
  const entry=f.saved.state.sessions[0];
  assert.equal(entry.nativeSessionRejection.platform,'ios');
  assert.equal(entry.nativeSessionRejection.phase,'revoked');
  assert.equal(androidDeepLinkCustodySettled(entry),false);
  assert.equal(iosDeepLinkCustodySettled(entry),false);
  await assert.rejects(prepareIosNativeDeepLinkRejection(f.args));
  assert.equal(f.events.filter(x=>x==='revoke-auth').length,1);
  const android=fixture();await assert.rejects(prepareIosNativeDeepLinkRejection(android.args));
  assert.deepEqual(android.events,[]);
});

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

async function revokedFixture(platform='android') {
  const f=fixture();f.saved.state.sessions[0].nativeSessionRenewal.platform=platform;
  await (platform==='ios'?prepareIosNativeDeepLinkRejection:prepareAndroidNativeDeepLinkRejection)(f.args);
  f.args.target={threadId:'123',messageId:'456'};
  f.saved.state.threadReceipt=structuredClone(f.args.target);f.saved.state.threadStarted=true;
  f.saved.state.threadPlan={runId:f.args.record.runId,ownerId:f.args.record.profileId};
  f.result={passed:true,scope:'android_external_owned_message_cold_native_rejection_barrier_cancel_and_feed; visual review pending',
    receipts:[{passed:true,runId:`chat-cold-${randomUUID()}`,mode:'cold',beforePid:null,afterPid:'1234',anonymousAction:'cancel',
      targetThreadId:'123',targetMessageId:'456',rejection:{observed:true,pid:'1234',status:400,
        timestamp:'1800000001.1',startedAt:'1800000000.1',endedAt:'1800000002.1'}}]};
  if(platform==='ios')f.result={passed:true,scope:'ios_external_owned_message_cold_native_rejection_barrier_cancel_and_feed',
    receipts:[{runId:f.args.record.runId,stepId:randomUUID(),mode:'cold',passed:true,cancelled:true,
      rejection:{observed:true,pid:1234,status:400,timestampNs:'1800000001000000000',
        startedAtNs:'1800000000000000000',endedAtNs:'1800000002000000000'}}]};
  f.args.execute=async()=>{
    assert.equal(f.saved.state.sessions[0].nativeSessionRejection.observation.started,true);
    f.events.push('ui');return structuredClone(f.result);
  };
  f.events.length=0;return f;
}
function warmExpiryFixture(platform='android') {
  const f=fixture(),entry=f.saved.state.sessions[0],original=entry.nativeSessionRenewal.original,stepId=randomUUID();
  delete entry.nativeSessionRenewal;
  const custodyKey=platform==='android'?'androidSession':'iosSession';
  entry[custodyKey]={install:{started:true,verified:true,input:{runId:f.args.record.runId,stepId,stage:'install',...original}}};
  const target={threadId:'123',messageId:'456'};
  const prelude=platform==='android'?{platform,mode:'warm',pidPreserved:true,suspended:true,target,receipt:{
    runId:`chat-cold-${randomUUID()}`,mode:'cold',beforePid:null,afterPid:'1234',focusedMessageId:'456',passed:true}}:
    {platform,mode:'warm',pidPreserved:true,suspended:true,target,receipt:{runId:f.args.record.runId,stepId:randomUUID(),mode:'cold',passed:true}};
  return {f,entry,original,target,prelude,clock:{value:original.expiresAt-1}};
}
async function warmRevokedFixture(platform='android') {
  const {f,original,target,prelude,clock}=warmExpiryFixture(platform);
  await awaitNativeWarmDeepLinkRejectionExpiry({...f.args,platform,prelude,target,now:()=>clock.value,
    sleep:async milliseconds=>{assert.equal(milliseconds,3000);clock.value=original.expiresAt+2;},
    fetchImpl:async()=>({ok:false,status:401})});
  await (platform==='ios'?prepareIosNativeDeepLinkRejection:prepareAndroidNativeDeepLinkRejection)({...f.args,mode:'warm',prelude,target});
  f.args.target=target;
  f.saved.state.threadReceipt=structuredClone(f.args.target);f.saved.state.threadStarted=true;
  f.saved.state.threadPlan={runId:f.args.record.runId,ownerId:f.args.record.profileId};
  f.result=platform==='android'?{passed:true,scope:'android_external_owned_message_warm_native_rejection_barrier_cancel_and_feed; visual review pending',
    receipts:[{passed:true,runId:`chat-warm-${randomUUID()}`,mode:'warm',beforePid:'1234',afterPid:'1234',anonymousAction:'cancel',
      targetThreadId:'123',targetMessageId:'456',rejection:{observed:true,pid:'1234',status:401,
        timestamp:'1800000001.1',startedAt:'1800000000.1',endedAt:'1800000002.1'}}]}:
    {passed:true,scope:'ios_external_owned_message_warm_native_rejection_barrier_cancel_and_feed',receipts:[{
      runId:f.args.record.runId,stepId:randomUUID(),mode:'warm',passed:true,cancelled:true,rejection:{observed:true,pid:1234,status:401,
        timestampNs:'1800000001000000000',startedAtNs:'1800000000000000000',endedAtNs:'1800000002000000000'}}]};
  f.args.execute=async()=>structuredClone(f.result);f.events.length=0;return f;
}
for(const variant of ['wrong-target','invalid-baseline','accepted-jwt','rotated-chain','concurrent-journal','wait-too-long'])
test(`warm expiry ${variant} cannot authorize owned revocation or replay`,async()=>{
  const {f,original,target,prelude,clock}=warmExpiryFixture(),query=f.args.client.query;
  let refreshReads=0;
  f.args.client.query=async(sql,params)=>{
    if(sql.includes('from auth.refresh_tokens')) {
      refreshReads++;
      if(variant==='invalid-baseline')return {rowCount:1,rows:[{total:3,revoked:1,active:2}]};
      if(variant==='rotated-chain'&&refreshReads===2)return {rowCount:1,rows:[{total:4,revoked:3,active:1}]};
    }
    return query(sql,params);
  };
  const args={...f.args,platform:'android',prelude,target:variant==='wrong-target'?{...target,messageId:'999'}:target,
    now:()=>clock.value,maxWaitSeconds:variant==='wait-too-long'?1:7200,
    sleep:async()=>{
      clock.value=original.expiresAt+2;
      if(variant==='concurrent-journal')f.saved.state.concurrent=true;
    },fetchImpl:async()=>({ok:variant==='accepted-jwt',status:variant==='accepted-jwt'?200:401})};
  await assert.rejects(awaitNativeWarmDeepLinkRejectionExpiry(args),
    {message:'deep_link_native_rejection_preparation_unresolved'});
  assert.equal(f.saved.state.sessions[0].revocation,undefined);
  if(['accepted-jwt','rotated-chain'].includes(variant)) {
    assert.equal(f.saved.state.sessions[0].nativeSessionRejection.phase,'expiry-wait');
    await assert.rejects(awaitNativeWarmDeepLinkRejectionExpiry(args));
  }
});
for(const platform of ['android','ios'])test(`${platform} warm rejection stays in one authenticated product process and settles specialized custody`,async()=>{
  const f=await warmRevokedFixture(platform);
  await (platform==='android'?observeAndroidNativeDeepLinkRejection:observeIosNativeDeepLinkRejection)(f.args);
  if(platform==='android')await confirmAndroidNativeDeepLinkRejectionAbsence({...f.args,stepId:randomUUID(),
    execute:async input=>({...input,verified:true})});
  else await clearIosNativeDeepLinkRejection({...f.args,stepId:randomUUID(),execute:async input=>{
    assert.equal(input.stage,'clear');assert.equal(input.accessToken,f.saved.state.sessions[0].iosSession.install.input.accessToken);
    return {runId:input.runId,stepId:input.stepId,stage:input.stage,verified:true};
  }});
  const entry=f.saved.state.sessions[0];
  assert.equal(platform==='android'?androidDeepLinkCustodySettled(entry):iosDeepLinkCustodySettled(entry),true);
  assert.equal(entry.nativeSessionRejection.mode,'warm');assert.equal(entry.nativeSessionRejection.prelude.receipt.mode,'cold');
});
test('warm Android observation rejects a changed product PID after revocation',async()=>{
  const f=await warmRevokedFixture('android');f.result.receipts[0].beforePid='9999';
  await assert.rejects(observeAndroidNativeDeepLinkRejection(f.args),{message:'deep_link_native_rejection_observation_unresolved'});
  assert.equal(androidDeepLinkCustodySettled(f.saved.state.sessions[0]),false);
});
for(const platform of ['android','ios'])test(`${platform} legacy cold rejection journal remains exactly recoverable`,async()=>{
  const f=await revokedFixture(platform),entry=f.saved.state.sessions[0],rejection=entry.nativeSessionRejection;
  delete rejection.mode;delete rejection.custodyKind;
  await (platform==='android'?observeAndroidNativeDeepLinkRejection:observeIosNativeDeepLinkRejection)(f.args);
  delete f.saved.state.sessions[0].nativeSessionRejection.observation.mode;
  if(platform==='android')await confirmAndroidNativeDeepLinkRejectionAbsence({...f.args,stepId:randomUUID(),
    execute:async input=>({...input,verified:true})});
  else await clearIosNativeDeepLinkRejection({...f.args,stepId:randomUUID(),execute:async input=>({
    runId:input.runId,stepId:input.stepId,stage:input.stage,verified:true})});
  const settledEntry=f.saved.state.sessions[0];
  assert.equal(platform==='android'?androidNativeDeepLinkRejectionCustodySettled(settledEntry):iosNativeDeepLinkRejectionCustodySettled(settledEntry),true);
});
test('iOS rejection closes only with the original expired snapshot and a new exact clear receipt',async()=>{
  const f=await revokedFixture('ios');await observeIosNativeDeepLinkRejection(f.args);
  assert.equal(iosDeepLinkCustodySettled(f.saved.state.sessions[0]),false);
  let commands=0;
  const args={...f.args,stepId:randomUUID(),execute:async input=>{
    commands++;const entry=f.saved.state.sessions[0];
    assert.deepEqual(input,{...entry.nativeSessionRenewal.install.input,stage:'clear-expired',stepId:args.stepId});
    assert.deepEqual(entry.nativeSessionRejection.clear,{started:true,verified:false,input});
    return {runId:input.runId,stepId:input.stepId,stage:input.stage,verified:true};
  }};
  await clearIosNativeDeepLinkRejection(args);
  assert.equal(commands,1);assert.equal(iosDeepLinkCustodySettled(f.saved.state.sessions[0]),true);
  assert.equal(androidDeepLinkCustodySettled(f.saved.state.sessions[0]),false);
  await assert.rejects(clearIosNativeDeepLinkRejection({...args,stepId:randomUUID()}));
  for(const mutate of [e=>e.nativeSessionRejection.clear.input.refreshToken='foreign',
    e=>e.nativeSessionRejection.clear.input.originalExpiresAt++,e=>e.nativeSessionRejection.observation.verified=false,
    e=>e.nativeSessionRejection.clear.input.stepId=e.nativeSessionRejection.observation.result.receipts[0].stepId]) {
    const copy=structuredClone(f.saved.state.sessions[0]);mutate(copy);
    assert.equal(iosNativeDeepLinkRejectionCustodySettled(copy),false);
  }
});
for(const failure of ['foreign-observation','lost-observation','clear-lost','clear-foreign','clear-write-lost','clear-readback-lost','reused-step','operations-live'])
test(`iOS rejection ${failure} preserves custody and blocks replay`,async()=>{
  const f=await revokedFixture('ios');let commands=0;
  if(failure==='foreign-observation')f.result.receipts[0].runId=randomUUID();
  if(failure==='lost-observation')f.args.execute=async()=>{throw Error('private');};
  if(failure.endsWith('observation')) {
    await assert.rejects(observeIosNativeDeepLinkRejection(f.args));
    await assert.rejects(observeIosNativeDeepLinkRejection(f.args));
  }else await observeIosNativeDeepLinkRejection(f.args);
  const checkpoint=f.args.journal.checkpoint;let writes=0;
  f.args.journal.checkpoint=async state=>{
    writes++;if(failure==='clear-write-lost'||failure==='clear-readback-lost'&&writes===2)return;
    await checkpoint(state);
  };
  const args={...f.args,stepId:failure==='reused-step'?f.result.receipts[0].stepId:randomUUID(),
    operationsSettled:async()=>failure!=='operations-live',execute:async input=>{
      commands++;if(failure==='clear-lost')throw Error('private');
      return {runId:failure==='clear-foreign'?randomUUID():input.runId,stepId:input.stepId,stage:input.stage,verified:true};
    }};
  await assert.rejects(clearIosNativeDeepLinkRejection(args));
  assert.equal(iosNativeDeepLinkRejectionCustodySettled(f.saved.state.sessions[0]),false);
  if(commands) {await assert.rejects(clearIosNativeDeepLinkRejection({...args,stepId:randomUUID()}));assert.equal(commands,1);}
});
test('native rejection closes only after exact observed result and a durable passive absence probe',async()=>{
  const f=await revokedFixture();await observeAndroidNativeDeepLinkRejection(f.args);
  const entry=f.saved.state.sessions[0];
  assert.equal(androidNativeDeepLinkRejectionCustodySettled(entry),false);
  await assert.rejects(observeAndroidNativeDeepLinkRejection(f.args));
  await confirmAndroidNativeDeepLinkRejectionAbsence({...f.args,stepId:randomUUID(),execute:async input=>{
    assert.deepEqual(f.saved.state.sessions[0].nativeSessionRejection.absence,{started:true,verified:false,input});
    assert.equal(input.stage,'probe-empty');return {...input,verified:true};
  }});
  assert.equal(androidNativeDeepLinkRejectionCustodySettled(f.saved.state.sessions[0]),true);
  assert.equal(androidDeepLinkCustodySettled(f.saved.state.sessions[0]),true);
  assert.equal(iosDeepLinkCustodySettled(f.saved.state.sessions[0]),false);
  await assert.rejects(confirmAndroidNativeDeepLinkRejectionAbsence({...f.args,stepId:randomUUID()}));
  for(const corrupt of [e=>e.revocation.verified=false,e=>e.nativeSessionRejection.observation.verified=false,
    e=>e.nativeSessionRejection.absence.input.runId=randomUUID(),e=>e.nativeSessionRejection.installStepId=randomUUID(),
    e=>e.nativeSessionRenewal.original.refreshToken='foreign',e=>delete e.nativeSessionRejection.mode,
    e=>delete e.nativeSessionRejection.custodyKind,e=>delete e.nativeSessionRejection.observation.mode,
    e=>e.nativeSessionRejection.observation.result.receipts[0].rejection.status=500]) {
    const copy=structuredClone(f.saved.state.sessions[0]);corrupt(copy);
    assert.equal(androidNativeDeepLinkRejectionCustodySettled(copy),false);
    assert.equal(androidDeepLinkCustodySettled(copy),false);
  }
});
for(const variant of ['lost-ui','wrong-target','warm','foreign-pid','no-http','future-http','extra-receipt','wrong-scope','new-remote-session'])
test(`native rejection ${variant} cannot authorize absence or replay`,async()=>{
  const f=await revokedFixture(),r=f.result.receipts[0];
  if(variant==='lost-ui')f.args.execute=async()=>{f.events.push('ui');throw Error('private error');};
  if(variant==='wrong-target')r.targetMessageId='999';
  if(variant==='warm')r.beforePid='1234';
  if(variant==='foreign-pid')r.rejection.pid='9999';
  if(variant==='no-http')r.rejection.status=500;
  if(variant==='future-http')r.rejection.timestamp='1800000003.1';
  if(variant==='extra-receipt')f.result.receipts.push(structuredClone(r));
  if(variant==='wrong-scope')f.result.scope='different';
  if(variant==='new-remote-session') {
    const query=f.args.client.query;let audits=0;
    f.args.client.query=async(sql,params)=>sql.includes('as owned')&&++audits===2?
      {rowCount:1,rows:[{owned:true,auth_count:1,exact_auth:true,web_count:0,exact_web:true}]}:query(sql,params);
  }
  await assert.rejects(observeAndroidNativeDeepLinkRejection(f.args),{message:'deep_link_native_rejection_observation_unresolved'});
  await assert.rejects(observeAndroidNativeDeepLinkRejection(f.args));
  await assert.rejects(confirmAndroidNativeDeepLinkRejectionAbsence({...f.args,stepId:randomUUID()}));
  assert.equal(f.events.filter(x=>x==='ui').length,1);
  assert.equal(androidNativeDeepLinkRejectionCustodySettled(f.saved.state.sessions[0]),false);
});
for(const phase of ['observe','absence'])for(const write of [1,2,...(phase==='observe'?[3]:[])])
test(`${phase} checkpoint ${write} read-back failure cannot authorize custody`,async()=>{
  const f=await revokedFixture();if(phase==='absence')await observeAndroidNativeDeepLinkRejection(f.args);
  const checkpoint=f.args.journal.checkpoint;let writes=0,probes=0;
  f.args.journal.checkpoint=async state=>{if(++writes!==write)await checkpoint(state);};
  const action=()=>phase==='observe'?observeAndroidNativeDeepLinkRejection(f.args):
    confirmAndroidNativeDeepLinkRejectionAbsence({...f.args,stepId:randomUUID(),execute:async input=>{probes++;return {...input,verified:true};}});
  await assert.rejects(action());
  assert.equal(androidNativeDeepLinkRejectionCustodySettled(f.saved.state.sessions[0]),false);
  if(write===1)assert.equal(phase==='observe'?f.events.filter(x=>x==='ui').length:probes,0);
  else {f.args.journal.checkpoint=checkpoint;await assert.rejects(action());}
});
for(const variant of ['lost','foreign-receipt','live-operations','reused-step'])
test(`absence ${variant} preserves custody`,async()=>{
  const f=await revokedFixture();await observeAndroidNativeDeepLinkRejection(f.args);let probes=0;
  const args={...f.args,stepId:variant==='reused-step'?f.saved.state.sessions[0].nativeSessionRejection.installStepId:randomUUID(),
    operationsSettled:async()=>variant!=='live-operations',execute:async input=>{
      probes++;if(variant==='lost')throw Error('private');return {...input,runId:randomUUID(),verified:true};
    }};
  await assert.rejects(confirmAndroidNativeDeepLinkRejectionAbsence(args));
  assert.equal(probes,['live-operations','reused-step'].includes(variant)?0:1);
  assert.equal(androidNativeDeepLinkRejectionCustodySettled(f.saved.state.sessions[0]),false);
  if(probes)await assert.rejects(confirmAndroidNativeDeepLinkRejectionAbsence({...args,stepId:randomUUID()}));
});
