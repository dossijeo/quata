import test from 'node:test';
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {prepareNativeDeepLinkExpiry, classifyNativeDeepLinkExpirySnapshot,installNativeDeepLinkExpiry,readNativeDeepLinkExpiry,verifyNativeDeepLinkExpiryIdentity,acknowledgeNativeDeepLinkExpiryRead} from './e2e-fixtures/chat-deep-link-native-expiry.mjs';
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

async function installFixture() {
  const f=fixture();await prepareNativeDeepLinkExpiry(f.args);
  f.events.length=0;
  const args={journal:f.args.journal,record:f.args.record,stepId:randomUUID(),now:f.args.now,
    execute:async input=>{
      f.events.push('execute');
      assert.deepEqual(f.saved.state.sessions[0].nativeSessionRenewal.install,{input,started:true,verified:false});
      assert.equal(input.expiresAt,1899999999);assert.equal(input.originalExpiresAt,2000000000);
      return {runId:input.runId,stepId:input.stepId,stage:input.stage,verified:true};
    }};
  return {...f,installArgs:args};
}

test('expiry install persists intent before one private command, then verifies receipt and remains unclosed',async()=>{
  const f=await installFixture();
  await installNativeDeepLinkExpiry(f.installArgs);
  assert.deepEqual(f.events,['checkpoint','execute','checkpoint']);
  const entry=f.saved.state.sessions[0];
  assert.equal(entry.nativeSessionRenewal.phase,'installed');
  assert.equal(entry.nativeSessionRenewal.install.verified,true);
  assert.equal(iosDeepLinkCustodySettled(entry),false);assert.equal(androidDeepLinkCustodySettled(entry),false);
  await assert.rejects(installNativeDeepLinkExpiry({...f.installArgs,stepId:randomUUID()}));
  assert.equal(f.events.filter(x=>x==='execute').length,1);
});

test('full fixture record does not leak unrelated private fields into the native command',async()=>{
  const f=await installFixture(),execute=f.installArgs.execute;
  f.installArgs.record={...f.installArgs.record,email:'private@example.invalid',phone:'synthetic-phone',
    password:'synthetic-private-password',state:{profileCreated:true}};
  f.installArgs.execute=async input=>{
    assert.equal(input.password,undefined);assert.equal(input.phone,undefined);assert.equal(input.state,undefined);
    assert.equal(input.email,'fixture@example.invalid');
    return execute(input);
  };
  await installNativeDeepLinkExpiry(f.installArgs);
  assert.deepEqual(f.events,['checkpoint','execute','checkpoint']);
});

test('lost or malformed install receipt retains intent and forbids retries even with a new step',async()=>{
  for(const execute of [async()=>{throw Error('private');},async()=>({verified:true}),
    async input=>({runId:input.runId,stepId:input.stepId,stage:input.stage,verified:true,private:'unexpected'})]) {
    const f=await installFixture();
    await assert.rejects(installNativeDeepLinkExpiry({...f.installArgs,execute}),{message:'deep_link_native_expiry_install_unresolved'});
    assert.equal(f.saved.state.sessions[0].nativeSessionRenewal.install.verified,false);
    await assert.rejects(installNativeDeepLinkExpiry({...f.installArgs,stepId:randomUUID()}));
    assert.equal(f.events.includes('execute'),false);
  }
});

test('failed intent durability prevents dispatch and failed result durability retains uncertainty',async()=>{
  for(const failureAt of [1,2]) {
    const f=await installFixture(),write=f.args.journal.checkpoint;let writes=0;
    f.args.journal.checkpoint=async state=>{if(++writes===failureAt)throw Error('disk');return write(state);};
    await assert.rejects(installNativeDeepLinkExpiry(f.installArgs));
    assert.equal(f.events.includes('execute'),failureAt===2);
    if(failureAt===2) {
      assert.equal(f.saved.state.sessions[0].nativeSessionRenewal.install.verified,false);
      await assert.rejects(installNativeDeepLinkExpiry(f.installArgs));
    }
  }
  const f=await installFixture(),read=f.args.journal.read;
  f.args.journal.read=async()=>{if(f.events.includes('checkpoint'))throw Error('unreadable');return read();};
  await assert.rejects(installNativeDeepLinkExpiry(f.installArgs));
  assert.equal(f.events.includes('execute'),false);
});

test('stale original, altered identity or premature transport activity cannot authorize expiry install',async()=>{
  for(const change of [f=>f.saved.state.sessions[0].revocation={},
    f=>f.saved.state.sessions[0].refreshAttempt={},
    f=>f.installArgs.now=()=>2000000000-900,
    f=>f.saved.state.sessions[0].nativeSessionRenewal.expired.refreshToken='different',
    f=>f.saved.state.sessions[0].authSessionId=randomUUID(),
    f=>f.saved.state.sessions[0].privateLoginResponse.body.session.refresh_token='rotated']) {
    const f=await installFixture();change(f);
    await assert.rejects(installNativeDeepLinkExpiry(f.installArgs));assert.deepEqual(f.events,[]);
  }
});

test('post-expiry native read persists returned secrets before structural verification and never accepts refresh',async()=>{
  const f=await installFixture();await installNativeDeepLinkExpiry(f.installArgs);f.events.length=0;
  const original=f.saved.state.sessions[0].nativeSessionRenewal.original;
  const session={...original,expiresAt:2000003600,accessToken:token(original.authUserId,original.authSessionId,2000003600),refreshToken:'rotated-synthetic'};
  const args={journal:f.args.journal,record:f.args.record,stepId:randomUUID(),execute:async input=>{
    f.events.push('read');
    assert.equal(f.saved.state.sessions[0].nativeSessionRenewal.snapshotRead.started,true);
    return {runId:input.runId,stepId:input.stepId,stage:input.stage,verified:true,privateSession:session};
  }};
  assert.deepEqual(await readNativeDeepLinkExpiry(args),{classification:'renewed_snapshot_unverified',remoteVerified:false});
  assert.deepEqual(f.events,['checkpoint','read','checkpoint','checkpoint']);
  assert.deepEqual(f.saved.state.sessions[0].nativeSessionRenewal.snapshotRead.privateReceipt.privateSession,session);
  assert.equal(iosDeepLinkCustodySettled(f.saved.state.sessions[0]),false);
  await assert.rejects(readNativeDeepLinkExpiry({...args,stepId:randomUUID()}));
});

test('lost or foreign native snapshot keeps private uncertainty and cannot be reread automatically',async()=>{
  for(const foreign of [false,true]) {
    const f=await installFixture();await installNativeDeepLinkExpiry(f.installArgs);
    const args={journal:f.args.journal,record:f.args.record,stepId:randomUUID(),execute:async input=>{
      if(!foreign)throw Error('private');
      return {runId:input.runId,stepId:input.stepId,stage:input.stage,verified:true,
        privateSession:{...f.saved.state.sessions[0].nativeSessionRenewal.original,profileId:randomUUID()}};
    }};
    await assert.rejects(readNativeDeepLinkExpiry(args),{message:'deep_link_native_expiry_read_unresolved'});
    const read=f.saved.state.sessions[0].nativeSessionRenewal.snapshotRead;
    assert.equal(read.structurallyVerified,false);
    assert.equal(read.privateReceipt!==undefined,foreign);
    await assert.rejects(readNativeDeepLinkExpiry({...args,stepId:randomUUID()}));
  }
});

async function identityFixture() {
  const f=await installFixture();await installNativeDeepLinkExpiry(f.installArgs);
  const original=f.saved.state.sessions[0].nativeSessionRenewal.original;
  const snapshot={...original,expiresAt:2000003600,accessToken:token(original.authUserId,original.authSessionId,2000003600),refreshToken:'rotated-synthetic'};
  await readNativeDeepLinkExpiry({journal:f.args.journal,record:f.args.record,stepId:randomUUID(),execute:async input=>({
    runId:input.runId,stepId:input.stepId,stage:input.stage,verified:true,privateSession:snapshot})});
  f.events.length=0;
  const args={...f.args,fetchImpl:async(url,options)=>{
    f.events.push('verify-auth');assert.equal(url.pathname,'/auth/v1/user');assert.equal(options.method,'GET');
    assert.equal(options.redirect,'error');assert.equal(options.headers.Authorization,`Bearer ${snapshot.accessToken}`);
    assert.equal(f.saved.state.sessions[0].nativeSessionRenewal.remoteIdentity.started,true);
    return {ok:true,json:async()=>({id:original.authUserId})};
  },client:{query:async(sql,values)=>{
    f.events.push('verify-db');assert.match(sql,/quata_e2e/);assert.match(sql,/auth_count/);
    assert.deepEqual(values,[original.authSessionId,original.authUserId,original.profileId,f.args.record.runId]);
    return {rowCount:1,rows:[{auth_session_id:original.authSessionId,auth_count:1}]};
  }}};
  return {...f,identityArgs:args};
}

test('remote identity preserves original receipt and does not claim observed refresh or settled custody',async()=>{
  const f=await identityFixture(),before=structuredClone(f.saved.state.sessions[0].privateLoginResponse);
  assert.deepEqual(await verifyNativeDeepLinkExpiryIdentity(f.identityArgs),{identityVerified:true,refreshObserved:false});
  assert.deepEqual(f.events,['checkpoint','verify-auth','verify-db','checkpoint']);
  assert.deepEqual(f.saved.state.sessions[0].privateLoginResponse,before);
  assert.equal(iosDeepLinkCustodySettled(f.saved.state.sessions[0]),false);
  await assert.rejects(verifyNativeDeepLinkExpiryIdentity(f.identityArgs));
});

test('remote verification rejects wrong actor, additional Auth sessions, missing receipt and lost response',async()=>{
  for(const modify of [
    f=>f.identityArgs.fetchImpl=async()=>({ok:true,json:async()=>({id:randomUUID()})}),
    f=>f.identityArgs.fetchImpl=async()=>{throw Error('synthetic-private');},
    f=>f.identityArgs.client.query=async()=>({rowCount:0,rows:[]}),
    f=>f.identityArgs.client.query=async()=>({rowCount:1,rows:[{auth_session_id:f.args.ticket.authSessionId,auth_count:2}]}),
    f=>f.identityArgs.client.query=async()=>({rowCount:1,rows:[{auth_session_id:randomUUID(),auth_count:1}]})]) {
    const f=await identityFixture();modify(f);
    await assert.rejects(verifyNativeDeepLinkExpiryIdentity(f.identityArgs),{message:'deep_link_native_expiry_identity_unverified'});
    assert.equal(f.saved.state.sessions[0].nativeSessionRenewal.remoteIdentity.verified,false);
    await assert.rejects(verifyNativeDeepLinkExpiryIdentity(f.identityArgs));
  }
});

test('remote verification refuses stale token and failed intent persistence before network',async()=>{
  const stale=await identityFixture();stale.identityArgs.now=()=>2000003600;
  await assert.rejects(verifyNativeDeepLinkExpiryIdentity(stale.identityArgs));assert.deepEqual(stale.events,[]);
  const disk=await identityFixture();disk.args.journal.checkpoint=async()=>{throw Error('disk');};
  await assert.rejects(verifyNativeDeepLinkExpiryIdentity(disk.identityArgs));assert.deepEqual(disk.events,[]);
});

test('iOS ACK is durable, bound to the verified read and still cannot settle device custody',async()=>{
  const f=await identityFixture();await verifyNativeDeepLinkExpiryIdentity(f.identityArgs);f.events.length=0;
  const args={journal:f.args.journal,record:f.args.record,acknowledge:async input=>{
    const read=f.saved.state.sessions[0].nativeSessionRenewal.snapshotRead;
    assert.deepEqual(input,{runId:f.args.record.runId,stepId:read.input.stepId});
    assert.equal(read.acknowledgment.started,true);f.events.push('ack');
    return {...input,acknowledged:true};
  }};
  assert.deepEqual(await acknowledgeNativeDeepLinkExpiryRead(args),{acknowledged:true});
  assert.deepEqual(f.events,['checkpoint','ack','checkpoint']);
  assert.equal(iosDeepLinkCustodySettled(f.saved.state.sessions[0]),false);
  await assert.rejects(acknowledgeNativeDeepLinkExpiryRead(args));
});

test('lost or incorrect ACK response preserves uncertainty and cannot be replayed',async()=>{
  for(const acknowledge of [async()=>{throw Error('private');},async input=>({...input,acknowledged:true,extra:true}),
    async input=>({...input,stepId:randomUUID(),acknowledged:true})]) {
    const f=await identityFixture();await verifyNativeDeepLinkExpiryIdentity(f.identityArgs);
    const args={journal:f.args.journal,record:f.args.record,acknowledge};
    await assert.rejects(acknowledgeNativeDeepLinkExpiryRead(args),{message:'deep_link_native_expiry_ack_unresolved'});
    assert.equal(f.saved.state.sessions[0].nativeSessionRenewal.snapshotRead.acknowledgment.verified,false);
    await assert.rejects(acknowledgeNativeDeepLinkExpiryRead(args));
  }
});

test('unverified identity or failed ACK intent checkpoint prevents dispatch',async()=>{
  const f=await identityFixture();let calls=0;
  const args={journal:f.args.journal,record:f.args.record,acknowledge:async()=>{calls++;}};
  await assert.rejects(acknowledgeNativeDeepLinkExpiryRead(args));assert.equal(calls,0);
  await verifyNativeDeepLinkExpiryIdentity(f.identityArgs);
  f.args.journal.checkpoint=async()=>{throw Error('disk');};
  await assert.rejects(acknowledgeNativeDeepLinkExpiryRead(args));assert.equal(calls,0);
});
