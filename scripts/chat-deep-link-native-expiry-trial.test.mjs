// Real expiry state machine;
// simulated profile/backend/device boundaries, no remote resources or credentials.
import test,{mock} from 'node:test';
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {mkdtemp,rm} from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
if (!process.execArgv.includes('--experimental-test-module-mocks')) {
  test('native expiry lifecycle in an isolated module-mock process',()=>{
    const env={...process.env};delete env.NODE_TEST_CONTEXT;
    const child=spawnSync(process.execPath,['--experimental-test-module-mocks','--test',fileURLToPath(import.meta.url)],
      {env,encoding:'utf8',timeout:120000});
    assert.ifError(child.error);
    assert.equal(child.status,0,child.stdout+'\n'+child.stderr);
  });
} else {
let state;
const jwt=(actor,id,exp)=>'synthetic.'+Buffer.from(JSON.stringify({sub:actor,session_id:id,exp})).toString('base64url')+'.synthetic';
mock.module('./e2e-fixtures/recovery-private-journal.mjs',{namedExports:{createRecoveryJournal:async({record})=>{
  let saved=structuredClone(record);state.records.push(()=>saved);
  return {read:async()=>structuredClone(saved),checkpoint:async value=>{saved.state=structuredClone(value);},
    removeAfterVerification:async verify=>{await verify();state.events.push('journal-remove');}};
}}});
mock.module('./e2e-fixtures/chat-deep-link-profile.mjs',{namedExports:{createDeepLinkProfile:async({journal})=>{
  const saved=await journal.read();saved.state.profileCreated=true;saved.state.profileCreationStarted=true;await journal.checkpoint(saved.state);
},retireDeepLinkProfile:async({operationsSettled})=>{assert.equal(await operationsSettled(),true);state.events.push('retire-profile');}}});
mock.module('./e2e-fixtures/chat-deep-link-thread.mjs',{namedExports:{seedDeepLinkThread:async({journal})=>{
  const saved=await journal.read();saved.state.threadStarted=true;await journal.checkpoint(saved.state);return {threadId:'123',messageId:'456'};
},removeDeepLinkThread:async({operationsSettled})=>{assert.equal(await operationsSettled(),true);state.events.push('retire-thread');}}});
mock.module('./e2e-fixtures/chat-deep-link-session.mjs',{namedExports:{revokeDeepLinkSessions:async()=>{throw Error('unexpected');},
  loginDeepLinkSession:async({record,ticket,journal})=>{
    Object.assign(ticket,{authSessionId:randomUUID(),webSessionId:randomUUID()});
    const exp=Math.floor(Date.now()/1000)+3600;
    const session={profileId:record.profileId,accessToken:jwt(record.authUserId,ticket.authSessionId,exp),
      refreshToken:'synthetic-refresh',expiresAt:exp,webSessionToken:'synthetic-web'};
    const saved=await journal.read();Object.assign(saved.state.sessions[0],ticket,{requestStarted:true,privateLoginResponse:{status:200,body:{
      profile:{id:record.profileId,auth_user_id:record.authUserId,display_name:'Fixture'},user:{id:record.authUserId,email:record.email},
      session:{access_token:session.accessToken,refresh_token:session.refreshToken,expires_at:exp},web_session:{token:session.webSessionToken}}}});
    await journal.checkpoint(saved.state);return session;
  }}});
const {runDeepLinkChatTrial}=await import('./flow-deep-links-chat-trial.mjs');
const {createIosDeepLinkUi}=await import('./e2e-fixtures/chat-deep-link-ios.mjs');

for(const mode of ['cold','warm'])for(const failure of [undefined,'install-expired','observe','read-owned','ack','clear'])test(`native expiry coordinator ${mode} ${failure??'complete'} preserves lifecycle ordering`,async()=>{
  state={events:[],records:[]};let installed,closed=false,renewed;
  const dir=await mkdtemp(path.join(os.tmpdir(),'quata-expiry-trial-'));
  const channel={settled:()=>closed,abort:()=>state.events.push('abort'),close:async()=>{
    assert.equal(installed,undefined);closed=true;state.events.push('channel-close');
  },sessionStep:async input=>{
    state.events.push(input.stage);if(failure===input.stage)throw Error('synthetic-uncertain');
    if(input.stage==='install-expired')installed=structuredClone(input);
    if(input.stage==='read-owned') {
      assert.ok(installed);
      const exp=installed.originalExpiresAt+3600;
      renewed=Object.fromEntries(['profileId','authUserId','authSessionId','accessToken','refreshToken','expiresAt','email','displayName','isOfficial'].map(k=>[k,installed[k]]));
      Object.assign(renewed,{expiresAt:exp,accessToken:jwt(installed.authUserId,installed.authSessionId,exp),refreshToken:'synthetic-rotated'});
      return {runId:input.runId,stepId:input.stepId,stage:input.stage,verified:true,privateSession:renewed};
    }
    if(input.stage==='clear') {assert.deepEqual(input,{runId:input.runId,stepId:input.stepId,stage:'clear',...renewed});installed=undefined;}
    return {runId:input.runId,stepId:input.stepId,stage:input.stage,verified:true};
  },observeChat:async input=>{
    state.events.push('observe');assert.equal(input.mode,mode);assert.equal(input.renewalPrelude,mode==='warm'?true:undefined);assert.ok(installed);
    if(failure==='observe')throw Error('synthetic-uncertain');return {passed:true};
  },acknowledgeOwnedRead:async input=>{
    state.events.push('ack');assert.equal(state.records[0]().state.sessions[0].nativeSessionRenewal.remoteIdentity.verified,true);
    if(failure==='ack')throw Error('synthetic-uncertain');return {...input,acknowledged:true};
  }};
  const client={query:async(sql,values)=>{
    if(sql.includes(' as auth_count'))return {rowCount:1,rows:[{auth_session_id:values[0],auth_count:1}]};
    if(sql.includes('select s.id as auth_session_id'))return {rowCount:1,rows:[{auth_session_id:values[0],web_session_id:values[3]}]};
    if(sql.includes('not exists(select 1 from auth.users'))return {rows:[{auth:true,profile:true,sessions:true,web_sessions:true}]};
    throw Error('unexpected_sql');
  }};
  try {
    const report=await runDeepLinkChatTrial({client,privateDirectory:dir,backendUrl:'https://example.test',publicKey:'public',
      preflight:async()=>true,transportSettled:async()=>true,sessionMode:`native-refresh-${mode}`,
      ui:createIosDeepLinkUi({channel,nativeRenewalMode:mode}),fetchImpl:async(url,options)=>{
        assert.equal(url.pathname,'/auth/v1/user');const claims=JSON.parse(Buffer.from(options.headers.Authorization.split('.')[1],'base64url'));
        return {ok:true,json:async()=>({id:claims.sub})};
      }});
    if(failure) {
      assert.equal(report.status,'failed_cleanup_pending');assert.equal(report.cleanupComplete,false);
      assert.equal(state.events.some(e=>e.startsWith('retire-')||e==='journal-remove'),false);
    }else {
      assert.equal(report.status,'passed');assert.equal(report.cleanupComplete,true);
      assert.deepEqual(state.events,['install-expired','observe','read-owned','ack','clear','channel-close','retire-thread',
        'retire-profile','retire-profile','journal-remove','journal-remove']);
    }
  }finally{assert.equal(path.dirname(dir),os.tmpdir());await rm(dir,{recursive:true,force:true});}
});
}
