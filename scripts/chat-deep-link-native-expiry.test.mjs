import test from 'node:test';
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {prepareNativeDeepLinkExpiry, classifyNativeDeepLinkExpirySnapshot} from './e2e-fixtures/chat-deep-link-native-expiry.mjs';
import {iosDeepLinkCustodySettled,androidDeepLinkCustodySettled} from './e2e-fixtures/chat-deep-link-ios-custody.mjs';

function token(authUserId,authSessionId,exp) {
  return 'synthetic.'+Buffer.from(JSON.stringify({sub:authUserId,session_id:authSessionId,exp})).toString('base64url')+'.synthetic';
}
function fixture() {
  const record={runId:randomUUID(),profileId:randomUUID(),authUserId:randomUUID()};
  const ticket={...record,purpose:'deep_link',clientInstanceId:randomUUID(),authSessionId:randomUUID(),webSessionId:randomUUID()};
  const session={profileId:record.profileId,accessToken:token(record.authUserId,ticket.authSessionId,2000000000),
    refreshToken:'synthetic-refresh',expiresAt:2000000000,webSessionToken:'synthetic-web'};
  const body={profile:{id:record.profileId,auth_user_id:record.authUserId,display_name:'Fixture'},
    user:{id:record.authUserId,email:'fixture@example.invalid'},session:{access_token:session.accessToken,
      refresh_token:session.refreshToken,expires_at:session.expiresAt},web_session:{token:session.webSessionToken}};
  const saved={...record,state:{sessions:[{...ticket,requestStarted:true,privateLoginResponse:{status:200,body}}]}};
  const events=[];
  const journal={read:async()=>structuredClone(saved),checkpoint:async state=>{events.push('checkpoint');saved.state=structuredClone(state);}};
  const args={record,ticket,session,journal,backendUrl:'https://example.test',publicKey:'public',now:()=>1900000000,
    fetchImpl:async()=>{events.push('auth');return {ok:true,json:async()=>({id:record.authUserId})};},
    client:{query:async()=>{events.push('db');return {rowCount:1,rows:[{auth_session_id:ticket.authSessionId,web_session_id:ticket.webSessionId}]};}}};
  const input={...record,stage:'read-owned',stepId:randomUUID()};
  const receipt=privateSession=>({runId:record.runId,stepId:input.stepId,stage:input.stage,verified:true,privateSession});
  return {args,saved,events,input,receipt};
}

test('separates local expiry from original verified credentials and prevents existing cleanup',async()=>{
  for(const platform of ['ios','android']) {
    const f=fixture(),original=structuredClone(f.saved.state.sessions[0]);
    const renewal=await prepareNativeDeepLinkExpiry({...f.args,platform});
    assert.deepEqual(f.events,['auth','db','checkpoint']);
    assert.equal(renewal.expired.expiresAt,1899999999);
    assert.deepEqual(renewal.expired,{...renewal.original,expiresAt:1899999999});
    assert.deepEqual(f.saved.state.sessions[0].privateLoginResponse,original.privateLoginResponse);
    assert.equal(iosDeepLinkCustodySettled(f.saved.state.sessions[0]),false);
    assert.equal(androidDeepLinkCustodySettled(f.saved.state.sessions[0]),false);
    renewal.original.refreshToken='changed';
    assert.equal(f.saved.state.sessions[0].nativeSessionRenewal.original.refreshToken,'synthetic-refresh');
    await assert.rejects(prepareNativeDeepLinkExpiry(f.args));
    assert.deepEqual(f.events,['auth','db','checkpoint']);
  }
});

test('refuses prior custody, multiple sessions and unresolved renewal before transport',async()=>{
  for(const change of [e=>e.iosSession={},e=>e.androidSession={},e=>e.iosNativeLogin={},
    e=>e.androidNativeLogin={},e=>e.nativeSessionRenewal={},e=>e.refreshAttempt={},e=>e.revocation={}]) {
    const f=fixture();change(f.saved.state.sessions[0]);
    await assert.rejects(prepareNativeDeepLinkExpiry(f.args),{message:'deep_link_native_expiry_preparation_unverified'});
    assert.deepEqual(f.events,[]);
  }
  const f=fixture();f.saved.state.sessions.push(structuredClone(f.saved.state.sessions[0]));
  await assert.rejects(prepareNativeDeepLinkExpiry(f.args));assert.deepEqual(f.events,[]);
});

test('lost Auth response, concurrent journal change and failed persistence do not authorize installation',async()=>{
  const lost=fixture();lost.args.fetchImpl=async()=>{throw Error('synthetic-private');};
  await assert.rejects(prepareNativeDeepLinkExpiry(lost.args),{message:'deep_link_native_expiry_preparation_unverified'});
  assert.equal(lost.saved.state.sessions[0].nativeSessionRenewal,undefined);
  const concurrent=fixture(),query=concurrent.args.client.query;
  concurrent.args.client.query=async()=>{const result=await query();concurrent.saved.state.changed=true;return result;};
  await assert.rejects(prepareNativeDeepLinkExpiry(concurrent.args));assert.equal(concurrent.events.includes('checkpoint'),false);
  const disk=fixture();disk.args.journal.checkpoint=async()=>{throw Error('disk');};
  await assert.rejects(prepareNativeDeepLinkExpiry(disk.args));
  const unreadable=fixture(),read=unreadable.args.journal.read;
  unreadable.args.journal.read=async()=>{if(unreadable.events.includes('checkpoint'))throw Error('read');return read();};
  await assert.rejects(prepareNativeDeepLinkExpiry(unreadable.args));
  assert.equal(unreadable.saved.state.sessions[0].nativeSessionRenewal.phase,'prepared');
  unreadable.args.journal.read=read;
  await assert.rejects(prepareNativeDeepLinkExpiry(unreadable.args));
});

test('classifies snapshots without accepting a refresh or changing the original',async()=>{
  const f=fixture(),renewal=await prepareNativeDeepLinkExpiry(f.args),before=structuredClone(renewal);
  const classify=snapshot=>classifyNativeDeepLinkExpirySnapshot({renewal,input:f.input,receipt:f.receipt(snapshot)});
  assert.equal(classify(renewal.expired),'expired_snapshot_unchanged');
  assert.equal(classify(renewal.original),'original_snapshot');
  const renewed={...renewal.original,accessToken:token(f.args.record.authUserId,f.args.ticket.authSessionId,2000003600),
    refreshToken:'rotated-synthetic',expiresAt:2000003600};
  assert.equal(classify(renewed),'renewed_snapshot_unverified');
  for(const actual of [{...renewed,authSessionId:randomUUID()},{...renewed,profileId:randomUUID()},
    {...renewed,expiresAt:1899999999},{...renewed,refreshToken:renewal.original.refreshToken},
    {...renewed,email:'other@example.invalid'},{...renewed,extra:'unexpected'}])
    assert.throws(()=>classify(actual),{message:'deep_link_native_expiry_snapshot_unverified'});
  assert.deepEqual(renewal,before);
  assert.throws(()=>classifyNativeDeepLinkExpirySnapshot({renewal,input:f.input,
    receipt:{...f.receipt(renewal.expired),stepId:randomUUID()}}));
});
