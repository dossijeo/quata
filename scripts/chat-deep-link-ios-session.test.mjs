import test from "node:test";
import assert from "node:assert/strict";
import {randomUUID} from "node:crypto";
import {prepareIosDeepLinkSession} from "./e2e-fixtures/chat-deep-link-ios-session.mjs";

function fixture() {
  const record={runId:randomUUID(),profileId:randomUUID(),authUserId:randomUUID()};
  const ticket={...record,purpose:"deep_link",clientInstanceId:randomUUID(),authSessionId:randomUUID(),webSessionId:randomUUID()};
  const claims={sub:record.authUserId,session_id:ticket.authSessionId,exp:2000000000};
  const accessToken="synthetic."+Buffer.from(JSON.stringify(claims)).toString("base64url")+".synthetic";
  const session={profileId:record.profileId,accessToken,refreshToken:"private-refresh",expiresAt:claims.exp,webSessionToken:"private-web"};
  const body={profile:{id:record.profileId,auth_user_id:record.authUserId,display_name:"Deep link fixture"},
    user:{id:record.authUserId,email:"fixture@example.invalid"},session:{access_token:accessToken,refresh_token:session.refreshToken,expires_at:session.expiresAt},web_session:{token:session.webSessionToken}};
  const entry={...ticket,requestStarted:true,privateLoginResponse:{status:200,body}};
  const saved={...record,state:{sessions:[entry]}};
  const events=[];
  const args={record,ticket,session,journal:{read:async()=>structuredClone(saved)},backendUrl:"https://example.test",publicKey:"public",now:()=>1900000000,
    fetchImpl:async(url,options)=>{events.push("auth");assert.equal(url.pathname,"/auth/v1/user");assert.equal(options.method,undefined);
      assert.equal(options.headers.Authorization,"Bearer "+accessToken);return {ok:true,json:async()=>({id:record.authUserId})};},
    client:{query:async(sql,values)=>{events.push("db");assert.match(sql,/^select /);assert.match(sql,/quata_e2e/);assert.equal(values[0],ticket.authSessionId);
      assert.equal(values[3],ticket.webSessionId);assert.equal(values[6],record.runId);assert.equal(values[5].includes("private-web"),false);
      return {rowCount:1,rows:[{auth_session_id:ticket.authSessionId,web_session_id:ticket.webSessionId}]};}}};
  return {args,entry,saved,body,events};
}

test("exports private native input only after Auth and owned receipt verification, without login",async()=>{
  const f=fixture(), before=structuredClone(f.saved);
  const result=await prepareIosDeepLinkSession(f.args);
  assert.deepEqual(f.events,["auth","db"]);assert.deepEqual(f.saved,before);
  assert.equal(result.authSessionId,f.args.ticket.authSessionId);assert.equal(result.accessToken,f.args.session.accessToken);
  assert.equal(result.displayName,"Deep link fixture");assert.equal(result.stage,"install");assert.equal(result.isOfficial,false);
  assert.equal(result.webSessionToken,undefined);assert.equal(result.password,undefined);assert.match(result.stepId,/^[0-9a-f-]{36}$/);
});

test("rejects mixed identity, response, receipt and unresolved session states before network",async()=>{
  for(const change of [f=>f.saved.runId=randomUUID(),f=>f.args.ticket.authSessionId=randomUUID(),
    f=>f.saved.state.sessions.push(structuredClone(f.entry)),f=>f.entry.requestStarted=false,
    f=>f.entry.noSession=false,f=>f.entry.refreshAttempt={verified:true},f=>f.entry.revocation={verified:true},
    f=>f.body.user.id=randomUUID(),f=>f.body.profile.auth_user_id=randomUUID(),
    f=>f.args.session.refreshToken="other-private",f=>f.args.session.webSessionToken="other-private",
    f=>f.entry.privateLoginResponse.status=500,f=>f.args.ticket.kind="native",
    f=>f.args.ticket.ticketId=randomUUID(),f=>f.entry.ticketId=randomUUID(),
    f=>{delete f.entry.clientInstanceId;delete f.args.ticket.clientInstanceId;},
    f=>{f.args.session.accessToken+=".extra";f.body.session.access_token=f.args.session.accessToken;}]){
    const f=fixture();change(f);await assert.rejects(prepareIosDeepLinkSession(f.args),{message:"deep_link_ios_session_unverified"});
    assert.deepEqual(f.events,[]);
  }
});

test("requires install lifetime and verified token rather than decoded claims alone",async()=>{
  const expired=fixture();expired.args.now=()=>2000000000-900;
  await assert.rejects(prepareIosDeepLinkSession(expired.args));assert.deepEqual(expired.events,[]);
  const elapsed=fixture();let reads=0;elapsed.args.now=()=>++reads===1?1900000000:2000000000;
  await assert.rejects(prepareIosDeepLinkSession(elapsed.args));assert.deepEqual(elapsed.events,["auth","db"]);
  for(const response of [{ok:false},{ok:true,json:async()=>({id:randomUUID()})}]){
    const f=fixture();f.args.fetchImpl=async()=>response;
    await assert.rejects(prepareIosDeepLinkSession(f.args));assert.deepEqual(f.events,[]);
  }
});

test("rejects missing or different database receipts and hides private transport errors",async()=>{
  for(const query of [async()=>({rowCount:0,rows:[]}),async()=>({rowCount:1,rows:[{auth_session_id:randomUUID(),web_session_id:randomUUID()}]}),
    async()=>{throw Error("private-refresh private-web");}]){
    const f=fixture();f.args.client.query=query;
    await assert.rejects(prepareIosDeepLinkSession(f.args),{message:"deep_link_ios_session_unverified"});
  }
});
